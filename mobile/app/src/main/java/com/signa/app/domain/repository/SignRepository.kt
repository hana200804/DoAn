package com.signa.app.domain.repository

import com.signa.app.domain.model.TrafficSign
import kotlinx.coroutines.flow.Flow

interface SignRepository {
    fun observeSigns(): Flow<List<TrafficSign>>
    fun observeSign(id: String): Flow<TrafficSign?>
}
