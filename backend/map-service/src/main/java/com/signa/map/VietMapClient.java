package com.signa.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.signa.common.error.ApiException;
import com.signa.common.geo.LatLng;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

/**
 * Gọi VietMap API (search v3, place v3, reverse v3, route). Định dạng phản hồi theo tài liệu
 * VietMap; cần kiểm tra lại với khoá thật.
 */
@Component
public class VietMapClient {

  private static final Logger log = LoggerFactory.getLogger(VietMapClient.class);

  public record Place(String refId, String name, String address, LatLng location)
      implements Serializable {}

  public record Route(double distanceM, double durationS, List<LatLng> polyline)
      implements Serializable {}

  private final RestClient http;
  private final String apiKey;

  public VietMapClient(
      RestClient.Builder builder,
      @Value("${signa.vietmap.base-url}") String baseUrl,
      @Value("${signa.vietmap.api-key}") String apiKey) {
    // Mặc định không có timeout: VietMap treo sẽ giữ luồng xử lý request mãi
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(5));
    factory.setReadTimeout(Duration.ofSeconds(10));
    this.http = builder.baseUrl(baseUrl).requestFactory(factory).build();
    this.apiKey = apiKey;
  }

  @Cacheable("vietmap-search")
  public List<Place> search(String text, Double lat, Double lng) {
    JsonNode body =
        get(
            u -> {
              u.path("/search/v3").queryParam("text", text);
              if (lat != null && lng != null) {
                u.queryParam("focus", lat + "," + lng);
              }
              return u;
            });
    List<Place> places = new ArrayList<>();
    body.forEach(n -> places.add(place(n)));
    return places;
  }

  @Cacheable("vietmap-place")
  public Place place(String refId) {
    return place(get(u -> u.path("/place/v3").queryParam("refid", refId)));
  }

  @Cacheable("vietmap-reverse")
  public Place reverse(double lat, double lng) {
    JsonNode body = get(u -> u.path("/reverse/v3").queryParam("lat", lat).queryParam("lng", lng));
    if (!body.isArray() || body.isEmpty()) {
      throw ApiException.notFound("Không tìm thấy địa chỉ tại toạ độ này");
    }
    return place(body.get(0));
  }

  @Cacheable("vietmap-route")
  public Route route(LatLng from, LatLng to, String vehicle) {
    return parseRoute(
        get(
            u ->
                u.path("/route")
                    .queryParam("api-version", "1.1")
                    .queryParam("point", from.lat() + "," + from.lng())
                    .queryParam("point", to.lat() + "," + to.lng())
                    .queryParam("vehicle", vehicle)
                    .queryParam("points_encoded", false)));
  }

  static Route parseRoute(JsonNode body) {
    JsonNode path = body.path("paths").path(0);
    if (path.isMissingNode()) {
      throw ApiException.notFound("Không tìm được tuyến đường");
    }
    List<LatLng> polyline = new ArrayList<>();
    // GeoJSON LineString: [lng, lat]
    path.path("points")
        .path("coordinates")
        .forEach(c -> polyline.add(new LatLng(c.get(1).asDouble(), c.get(0).asDouble())));
    return new Route(
        path.path("distance").asDouble(), path.path("time").asDouble() / 1000, polyline);
  }

  private static Place place(JsonNode n) {
    LatLng location =
        n.has("lat") ? new LatLng(n.path("lat").asDouble(), n.path("lng").asDouble()) : null;
    String address =
        n.hasNonNull("display") ? n.path("display").asText() : n.path("address").asText(null);
    return new Place(n.path("ref_id").asText(null), n.path("name").asText(null), address, location);
  }

  private JsonNode get(Function<UriBuilder, UriBuilder> uri) {
    if (apiKey == null || apiKey.isBlank()) {
      throw ApiException.upstream("Chưa cấu hình VIETMAP_API_KEY");
    }
    try {
      JsonNode body =
          http.get()
              .uri(u -> uri.apply(u).queryParam("apikey", apiKey).build())
              .retrieve()
              .body(JsonNode.class);
      if (body == null) {
        throw ApiException.upstream("VietMap không trả dữ liệu");
      }
      return body;
    } catch (RestClientException e) {
      // Thông báo của Spring có cả URL chứa apikey, không được trả cho client
      log.warn("Gọi VietMap lỗi: {}", e.getMostSpecificCause().toString());
      throw ApiException.upstream("Không gọi được VietMap");
    }
  }
}
