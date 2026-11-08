package com.signa.sign.store;

import com.signa.common.error.ApiException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Ảnh cắt biển báo trên MinIO; app tải thẳng lên bằng presigned URL (ADR-0006). */
@Component
public class CropStorage {

  private static final Logger log = LoggerFactory.getLogger(CropStorage.class);
  private static final int UPLOAD_URL_MINUTES = 15;
  private static final int VIEW_URL_MINUTES = 60;

  private final MinioClient internal;
  private final MinioClient presigner;
  private final String bucket;
  private final String modelBucket;
  private volatile boolean bucketReady;

  public CropStorage(
      @Value("${signa.minio.endpoint}") String endpoint,
      @Value("${signa.minio.public-endpoint}") String publicEndpoint,
      @Value("${signa.minio.access-key}") String accessKey,
      @Value("${signa.minio.secret-key}") String secretKey,
      @Value("${signa.minio.bucket}") String bucket,
      @Value("${signa.minio.model-bucket:models}") String modelBucket) {
    this.internal =
        MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    // Ký URL bằng địa chỉ công khai; đặt region để không phải gọi mạng khi ký
    this.presigner =
        MinioClient.builder()
            .endpoint(publicEndpoint)
            .credentials(accessKey, secretKey)
            .region("us-east-1")
            .build();
    this.bucket = bucket;
    this.modelBucket = modelBucket;
  }

  /** Thử lúc khởi động; nếu S3 chưa sẵn sàng thì thử lại ở lần cấp URL tải lên kế tiếp. */
  @EventListener(ApplicationReadyEvent.class)
  public void ensureBucket() {
    try {
      for (String name : new java.util.HashSet<>(java.util.List.of(bucket, modelBucket))) {
        if (!internal.bucketExists(BucketExistsArgs.builder().bucket(name).build())) {
          internal.makeBucket(MakeBucketArgs.builder().bucket(name).build());
        }
      }
      bucketReady = true;
    } catch (Exception e) {
      log.warn("Chưa tạo được bucket {}: {}", bucket, e.getMessage());
    }
  }

  public String uploadUrl(String key) {
    if (!bucketReady) {
      ensureBucket();
      if (!bucketReady) {
        throw ApiException.upstream("Kho ảnh chưa sẵn sàng");
      }
    }
    return presign(Method.PUT, key, UPLOAD_URL_MINUTES);
  }

  public String viewUrl(String key) {
    return key == null ? null : presign(Method.GET, key, VIEW_URL_MINUTES);
  }

  public String artifactUrl(String key) {
    return key == null ? null : presign(Method.GET, key, VIEW_URL_MINUTES, modelBucket);
  }

  public String cropSha256(String key) {
    if (key == null || !key.startsWith("crops/"))
      throw ApiException.validation("crop", "thiếu ảnh cắt");
    try (var input =
        internal.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
      var digest = MessageDigest.getInstance("SHA-256");
      long count = 0;
      byte[] buffer = new byte[65536];
      int n;
      while ((n = input.read(buffer)) != -1) {
        count += n;
        if (count > 5 * 1024 * 1024) throw ApiException.validation("crop", "ảnh vượt 5MiB");
        digest.update(buffer, 0, n);
      }
      if (count == 0) throw ApiException.validation("crop", "ảnh rỗng");
      return HexFormat.of().formatHex(digest.digest());
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw storageDown(key, e);
    }
  }

  public boolean exists(String key) {
    try {
      var stat = internal.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
      if (stat.size() < 1 || stat.size() > 5 * 1024 * 1024) {
        throw ApiException.validation("crop", "ảnh cắt phải có kích thước từ 1 byte đến 5 MiB");
      }
      return true;
    } catch (ApiException e) {
      throw e;
    } catch (ErrorResponseException e) {
      if ("NoSuchKey".equals(e.errorResponse().code())) {
        return false;
      }
      throw storageDown(key, e);
    } catch (Exception e) {
      throw storageDown(key, e);
    }
  }

  /**
   * Verify bytes in storage before registration/activation; metadata alone is not authoritative.
   */
  public void verifyArtifact(String key, String sha256, long size) {
    if (key == null
        || !key.matches("[A-Za-z0-9][A-Za-z0-9_./-]*\\.tflite")
        || java.util.Arrays.stream(key.split("/", -1))
            .anyMatch(p -> p.isBlank() || p.equals(".") || p.equals(".."))) {
      throw ApiException.validation("objectKey", "khoá file TFLite không hợp lệ");
    }
    try {
      var stat =
          internal.statObject(StatObjectArgs.builder().bucket(modelBucket).object(key).build());
      if (stat.size() != size)
        throw ApiException.validation("sizeBytes", "không khớp file trong kho ảnh");
      var digest = MessageDigest.getInstance("SHA-256");
      try (var input =
          internal.getObject(GetObjectArgs.builder().bucket(modelBucket).object(key).build())) {
        byte[] buffer = new byte[65536];
        long count = 0;
        int n;
        while ((n = input.read(buffer)) != -1) {
          count += n;
          if (count > size) throw ApiException.validation("sizeBytes", "file đã thay đổi");
          digest.update(buffer, 0, n);
        }
        if (count != size || !HexFormat.of().formatHex(digest.digest()).equals(sha256)) {
          throw ApiException.validation("sha256", "không khớp file trong kho ảnh");
        }
      }
    } catch (ApiException e) {
      throw e;
    } catch (ErrorResponseException e) {
      if ("NoSuchKey".equals(e.errorResponse().code()))
        throw ApiException.validation("objectKey", "chưa tải file mô hình");
      throw storageDown(key, e);
    } catch (Exception e) {
      throw storageDown(key, e);
    }
  }

  // Lỗi hạ tầng phải làm request thất bại (rollback) để app gửi lại, không coi là ảnh không có
  private static ApiException storageDown(String key, Exception e) {
    log.warn("Không kiểm tra được ảnh {}: {}", key, e.getMessage());
    return ApiException.upstream("Kho ảnh tạm thời không truy cập được");
  }

  private String presign(Method method, String key, int minutes) {
    return presign(method, key, minutes, bucket);
  }

  private String presign(Method method, String key, int minutes, String targetBucket) {
    try {
      return presigner.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(method)
              .bucket(targetBucket)
              .object(key)
              .expiry(minutes, TimeUnit.MINUTES)
              .build());
    } catch (Exception e) {
      throw new IllegalStateException("Không tạo được presigned URL", e);
    }
  }
}
