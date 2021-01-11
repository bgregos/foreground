package me.bgregos.foreground

import android.app.Application
import me.bgregos.foreground.di.DaggerApplicationComponent

class ForegroundApplication: Application() {
    val appComponent = DaggerApplicationComponent.create()
}