package com.signa.app.data.fake

import com.signa.app.domain.model.TrafficSign
import com.signa.app.domain.repository.SignRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeSignRepository @Inject constructor() : SignRepository {
    override fun observeSigns(): Flow<List<TrafficSign>> = flowOf(FakeData.signs)

    override fun observeSign(id: String): Flow<TrafficSign?> =
        flowOf(FakeData.signs.firstOrNull { it.id == id })
}
