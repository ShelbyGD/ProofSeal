package de.proofseal.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProofRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ProofDatabase : RoomDatabase() {

    abstract fun proofRecordDao(): ProofRecordDao

    companion object {
        @Volatile
        private var INSTANCE: ProofDatabase? = null

        fun getInstance(context: Context): ProofDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ProofDatabase::class.java,
                    "proofseal_records.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
