package me.bgregos.foreground

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import me.bgregos.foreground.di.ApplicationComponent
import me.bgregos.foreground.di.DaggerApplicationComponent


class ForegroundApplication: Application() {
    val applicationComponent = DaggerApplicationComponent.builder().bindContext(this).build()

    override fun onCreate() {
        super.onCreate()
        WorkManager.initialize(this, Configuration.Builder().setWorkerFactory(applicationComponent.workerFactory).build())
    }
}

fun Context.getApplicationComponent(): ApplicationComponent = (this.applicationContext as ForegroundApplication).applicationComponent

