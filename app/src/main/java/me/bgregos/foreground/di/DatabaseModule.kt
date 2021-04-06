package me.bgregos.foreground.di

import android.content.Context
import dagger.Module
import dagger.Provides
import me.bgregos.foreground.data.ForegroundDatabase
import javax.inject.Singleton

@Module
abstract class DatabaseModule {
    companion object {

        @Singleton
        @Provides
        fun provideForegroundDatabase(context: Context): ForegroundDatabase {
            return ForegroundDatabase.buildDatabase(context)
        }
    }
}