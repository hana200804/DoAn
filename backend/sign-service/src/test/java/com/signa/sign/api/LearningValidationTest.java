package com.signa.sign.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.signa.sign.api.LearningDtos.ConditionsRequest;
import com.signa.sign.api.LearningDtos.OcrReviewRequest;
import com.signa.sign.api.LearningDtos.TimeWindowRequest;
import com.signa.sign.api.LearningDtos.VehiclesRequest;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;

class LearningValidationTest {
  @Test
  void ocrReviewRejectsInvalidClocksVehiclesAndNumericConditions() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      var bad =
          List.of(
              new ConditionsRequest(
                  List.of(new TimeWindowRequest("25:00", "22:00")), null, null, null, null),
              new ConditionsRequest(
                  null, new VehiclesRequest(List.of("BOAT"), List.of()), null, null, null),
              new ConditionsRequest(null, null, 0, null, null),
              new ConditionsRequest(null, null, null, null, Double.NaN));
      for (var conditions : bad) {
        assertThat(validator.validate(new OcrReviewRequest("text", conditions))).isNotEmpty();
      }
    }
  }
}
