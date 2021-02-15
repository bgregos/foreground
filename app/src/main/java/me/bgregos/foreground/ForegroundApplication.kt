package me.bgregos.foreground

import android.app.Application
import android.content.Context
import me.bgregos.foreground.di.ApplicationComponent
import me.bgregos.foreground.di.DaggerApplicationComponent
import me.bgregos.foreground.di.DaggerApplicationComponent.builder


class ForegroundApplication: Application() {
    val appComponent = DaggerApplicationComponent.builder().application(this).build()

    override fun onCreate() {
        super.onCreate()
        appComponent.localTasksRepository.init()
    }

    fun Context.getApplicationComponent(): ApplicationComponent = appComponent
}