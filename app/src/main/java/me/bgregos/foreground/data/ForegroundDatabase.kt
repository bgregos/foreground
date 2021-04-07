package me.bgregos.foreground.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bgregos.foreground.data.taskfilter.TaskFilterDao
import me.bgregos.foreground.model.TaskFilter
import me.bgregos.foreground.model.TaskFilterType
import me.bgregos.foreground.model.TaskFiltersAvailable
import java.util.concurrent.Executors

@Database(entities = [TaskFilter::class], version = 1)
@TypeConverters(Converters::class)
abstract class ForegroundDatabase : RoomDatabase() {
    abstract fun taskFilterDao(): TaskFilterDao

    companion object {

        fun buildDatabase(context: Context): ForegroundDatabase {
            return Room.databaseBuilder(context, ForegroundDatabase::class.java, "ForegroundDatabase")
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            //pre-populate data
                            db.execSQL("INSERT INTO TaskFilter VALUES (null,'waiting',null,1,0) ")
                        }
                    })
                    .build()
        }
    }

}
