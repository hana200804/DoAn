package com.signa.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.signa.app.data.local.database.entity.FakeAccountEntity
import com.signa.app.data.local.database.entity.PasswordResetEntity

@Dao
interface FakeAccountDao {
    @Query("SELECT * FROM fake_accounts WHERE email = :email")
    suspend fun findByEmail(email: String): FakeAccountEntity?

    @Query("SELECT * FROM fake_accounts WHERE id = :id")
    suspend fun findById(id: String): FakeAccountEntity?

    @Insert
    suspend fun insert(account: FakeAccountEntity)

    @Query("UPDATE fake_accounts SET passwordSalt = :salt, passwordHash = :hash WHERE id = :id")
    suspend fun updatePassword(id: String, salt: String, hash: String)

    @Query("SELECT * FROM password_resets WHERE email = :email")
    suspend fun findReset(email: String): PasswordResetEntity?

    @Upsert
    suspend fun saveReset(reset: PasswordResetEntity)

    @Query("DELETE FROM password_resets WHERE email = :email")
    suspend fun deleteReset(email: String)
}
