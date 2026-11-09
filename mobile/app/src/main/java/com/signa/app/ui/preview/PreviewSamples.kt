package com.signa.app.ui.preview

import com.signa.app.data.fake.FakeData
import com.signa.app.ui.map.MapUiState
import com.signa.app.ui.sign.SignDetailUiState
import com.signa.app.ui.sign.SignLookupUiState

/** Design-time adapters only. Runtime features get their data from injected repositories. */
internal object PreviewSamples {
    val profile = FakeData.profile
    val map = MapUiState(signs = FakeData.signs)
    val lookup = SignLookupUiState(signs = FakeData.signs)
    val detail = SignDetailUiState(sign = FakeData.signs.first(), isLoading = false)
}
