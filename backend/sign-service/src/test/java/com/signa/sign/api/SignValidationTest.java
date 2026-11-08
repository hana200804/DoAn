package com.signa.sign.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.signa.common.error.ApiException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SignValidationTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "NaN,21,105.9,21.1",
        "105,Infinity,105.1,21.1",
        "200,21,200.1,21.1",
        "105,91,105.1,91.1",
        "105,21,105.1,21.1,",
        "105,21,105,21.1"
      })
  void invalidBboxIsRejected(String bbox) {
    assertThatThrownBy(() -> SignController.parseBbox(bbox)).isInstanceOf(ApiException.class);
  }
}
