package com.signa.sign.store;

import com.signa.common.error.ApiException;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
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
  private volatile boolean bucketReady;

  public CropStorage(
      @Value("${signa.minio.endpoint}") String endpoint,
      @Value("${signa.minio.public-endpoint}") String publicEndpoint,
      @Value("${signa.minio.access-key}") String accessKey,
      @Value("${signa.minio.secret-key}") String secretKey,
      @Value("${signa.minio.bucket}") String bucket) {
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
  }

  /** Thử lúc khởi động; nếu S3 chưa sẵn sàng thì thử lại ở lần cấp URL tải lên kế tiếp. */
  @EventListener(ApplicationReadyEvent.class)
  public void ensureBucket() {
    try {
      if (!internal.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
        internal.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
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

  public boolean exists(String key) {
    try {
      internal.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
      return true;
    } catch (ErrorResponseException e) {
      if ("NoSuchKey".equals(e.errorResponse().code())) {
        return false;
      }
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
    try {
      return presigner.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(method)
              .bucket(bucket)
              .object(key)
              .expiry(minutes, TimeUnit.MINUTES)
              .build());
    } catch (Exception e) {
      throw new IllegalStateException("Không tạo được presigned URL", e);
    }
  }
}
