package com.signa.sign.domain;

import com.signa.common.geo.LatLng;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Phép tính địa lý trên khoảng cách ngắn (vài km), đủ chính xác cho bài toán biển báo. */
public final class Geo {

  private static final double EARTH_RADIUS_M = 6_371_000;

  private Geo() {}

  public static double distanceM(double lat1, double lng1, double lat2, double lng2) {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLng = Math.toRadians(lng2 - lng1);
    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2)
                * Math.sin(dLng / 2);
    return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
  }

  /** Độ lệch hướng nhỏ nhất giữa hai hướng, trong khoảng [0, 180]. */
  public static int headingDiff(int a, int b) {
    int d = Math.abs(((a - b) % 360 + 360) % 360);
    return Math.min(d, 360 - d);
  }

  /** Độ (vĩ độ) tương ứng với một khoảng cách theo phương bắc–nam. */
  public static double metersToLatDeg(double meters) {
    return Math.toDegrees(meters / EARTH_RADIUS_M);
  }

  /** Độ (kinh độ) tương ứng với một khoảng cách theo phương đông–tây tại vĩ độ lat. */
  public static double metersToLngDeg(double meters, double lat) {
    return metersToLatDeg(meters) / Math.cos(Math.toRadians(lat));
  }

  /** Khoảng cách từ điểm tới đường gấp khúc, chiếu lên mặt phẳng cục bộ quanh điểm. */
  public static double distanceToPolylineM(double lat, double lng, List<LatLng> polyline) {
    if (polyline.size() == 1) {
      return distanceM(lat, lng, polyline.get(0).lat(), polyline.get(0).lng());
    }
    double mPerLat = EARTH_RADIUS_M * Math.PI / 180;
    double mPerLng = mPerLat * Math.cos(Math.toRadians(lat));
    double best = Double.MAX_VALUE;
    for (int i = 0; i + 1 < polyline.size(); i++) {
      double ax = (polyline.get(i).lng() - lng) * mPerLng;
      double ay = (polyline.get(i).lat() - lat) * mPerLat;
      double bx = (polyline.get(i + 1).lng() - lng) * mPerLng;
      double by = (polyline.get(i + 1).lat() - lat) * mPerLat;
      double dx = bx - ax;
      double dy = by - ay;
      double len2 = dx * dx + dy * dy;
      double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
      best = Math.min(best, Math.hypot(ax + t * dx, ay + t * dy));
    }
    return best;
  }

  /** Trung vị có trọng số của một dãy giá trị; dùng riêng cho vĩ độ và kinh độ. */
  public static double weightedMedian(List<double[]> valueAndWeight) {
    List<double[]> sorted = new ArrayList<>(valueAndWeight);
    sorted.sort(Comparator.comparingDouble(v -> v[0]));
    double total = sorted.stream().mapToDouble(v -> v[1]).sum();
    if (total <= 0) {
      return sorted.get(sorted.size() / 2)[0];
    }
    double acc = 0;
    for (double[] v : sorted) {
      acc += v[1];
      if (acc >= total / 2) {
        return v[0];
      }
    }
    return sorted.get(sorted.size() - 1)[0];
  }
}
