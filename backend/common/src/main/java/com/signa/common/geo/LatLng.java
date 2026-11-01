package com.signa.common.geo;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.io.Serializable;

/** Toạ độ WGS84. */
public record LatLng(
    @DecimalMin("-90") @DecimalMax("90") double lat,
    @DecimalMin("-180") @DecimalMax("180") double lng)
    implements Serializable {}
