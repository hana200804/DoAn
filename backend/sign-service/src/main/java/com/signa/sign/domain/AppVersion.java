package com.signa.sign.domain;

/** Numeric app versions, 1 to 3 components; never compares "1.10" lexicographically. */
public record AppVersion(int major, int minor, int patch) implements Comparable<AppVersion> {
  public static AppVersion parse(String value) {
    if (value == null || !value.matches("(0|[1-9][0-9]{0,8})(\\.(0|[1-9][0-9]{0,8})){0,2}")) {
      throw new IllegalArgumentException("App version must be major[.minor[.patch]]");
    }
    String[] parts = value.split("\\.");
    return new AppVersion(
        Integer.parseInt(parts[0]),
        parts.length > 1 ? Integer.parseInt(parts[1]) : 0,
        parts.length > 2 ? Integer.parseInt(parts[2]) : 0);
  }

  @Override
  public int compareTo(AppVersion other) {
    int result = Integer.compare(major, other.major);
    if (result == 0) result = Integer.compare(minor, other.minor);
    return result == 0 ? Integer.compare(patch, other.patch) : result;
  }
}
