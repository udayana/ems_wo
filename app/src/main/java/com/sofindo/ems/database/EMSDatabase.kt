package com.sofindo.ems.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [PendingWorkOrder::class, PendingProject::class, PendingMaintenanceTask::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class EMSDatabase : RoomDatabase() {
    
    abstract fun pendingWorkOrderDao(): PendingWorkOrderDao
    abstract fun pendingProjectDao(): PendingProjectDao
    abstract fun pendingMaintenanceTaskDao(): PendingMaintenanceTaskDao
    
    companion object {
        @Volatile
        private var INSTANCE: EMSDatabase? = null
        
        fun getDatabase(context: Context): EMSDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EMSDatabase::class.java,
                    "ems_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

