package com.signa.app.data.repository

import com.signa.app.data.fake.FakeData
import com.signa.app.data.local.database.dao.SignDao
import com.signa.app.data.mapper.toDomain
import com.signa.app.data.mapper.toEntity
import com.signa.app.domain.model.TrafficSign
import com.signa.app.domain.repository.SignRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Sample cache supports map development; regional API refresh will supply verified positions later. */
class CachedSignRepository @Inject constructor(private val dao: SignDao) : SignRepository {
    private val seedMutex = Mutex()

    override fun observeSigns(): Flow<List<TrafficSign>> = dao.observeAll()
        .onStart { ensureSampleCache() }
        .map { rows -> rows.map { it.toDomain() } }

    override fun observeSign(id: String): Flow<TrafficSign?> = dao.observeById(id)
        .onStart { ensureSampleCache() }
        .map { it?.toDomain() }

    private suspend fun ensureSampleCache() = seedMutex.withLock {
        if (dao.count() == 0) dao.insertMissing(FakeData.signs.map { it.toEntity() })
        // Upgrade only reserved sample IDs lacking coordinates; do not replace cached content.
        FakeData.signs.forEach { sample ->
            sample.position?.let { dao.fillMissingSamplePosition(sample.id, sample.code, it.latitude, it.longitude) }
        }
    }
}
