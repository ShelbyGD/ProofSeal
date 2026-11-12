package de.proofseal.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProofRecordDao {

    @Query("SELECT * FROM proof_records ORDER BY createdAtEpochMillis DESC")
    fun getAllRecords(): Flow<List<ProofRecordEntity>>

    @Query(
        """
        SELECT * FROM proof_records
        WHERE title LIKE '%' || :query || '%'
           OR primaryOcrValue LIKE '%' || :query || '%'
           OR fullOcrSummary LIKE '%' || :query || '%'
           OR detectedBarcodesJoined LIKE '%' || :query || '%'
           OR protocolId LIKE '%' || :query || '%'
           OR createdAtFormatted LIKE '%' || :query || '%'
           OR witnessName LIKE '%' || :query || '%'
           OR userNotes LIKE '%' || :query || '%'
        ORDER BY createdAtEpochMillis DESC
        """
    )
    fun searchRecords(query: String): Flow<List<ProofRecordEntity>>

    @Query("SELECT * FROM proof_records WHERE protocolId = :protocolId LIMIT 1")
    suspend fun getRecordById(protocolId: String): ProofRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: ProofRecordEntity)

    @Delete
    suspend fun deleteRecord(record: ProofRecordEntity)
}
