package com.signa.map;

import com.signa.common.error.ApiException;
import com.signa.common.geo.LatLng;
import com.signa.common.security.Authenticated;
import com.signa.map.VietMapClient.Place;
import com.signa.map.VietMapClient.Route;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tìm địa chỉ và chỉ đường (FR-09), docs/07-api.md §4. */
@RestController
@RequestMapping("/api/v1")
@Authenticated
@Validated
public class MapController {

  private final VietMapClient vietmap;

  public MapController(VietMapClient vietmap) {
    this.vietmap = vietmap;
  }

  @GetMapping("/places/search")
  public List<Place> search(
      @RequestParam @Size(min = 2, max = 200) String q,
      @RequestParam(required = false) Double lat,
      @RequestParam(required = false) Double lng) {
    return vietmap.search(q.trim(), lat, lng);
  }

  @GetMapping("/places/reverse")
  public Place reverse(@RequestParam double lat, @RequestParam double lng) {
    return vietmap.reverse(lat, lng);
  }

  @GetMapping("/places/{refId}")
  public Place place(@PathVariable String refId) {
    return vietmap.place(refId);
  }

  @GetMapping("/routes")
  public Route route(
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam(defaultValue = "car") String vehicle) {
    if (!List.of("car", "truck", "bus").contains(vehicle)) {
      throw ApiException.validation("vehicle", "chỉ nhận car, truck, bus");
    }
    return vietmap.route(point("from", from), point("to", to), vehicle);
  }

  private static LatLng point(String field, String value) {
    String[] p = value.split(",");
    try {
      LatLng ll = new LatLng(Double.parseDouble(p[0]), Double.parseDouble(p[1]));
      if (p.length != 2 || Math.abs(ll.lat()) > 90 || Math.abs(ll.lng()) > 180) {
        throw new NumberFormatException();
      }
      return ll;
    } catch (RuntimeException e) {
      throw ApiException.validation(field, "định dạng lat,lng");
    }
  }
}
