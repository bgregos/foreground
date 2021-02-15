package me.bgregos.foreground.di

import android.content.Context
import dagger.BindsInstance
import dagger.Component
import me.bgregos.foreground.tasklist.LocalTasksRepository
import me.bgregos.foreground.tasklist.MainActivity
import javax.inject.Singleton

@Component(modules= [
    ViewModelModule::class,
    TaskModule::class
] )
@Singleton
interface ApplicationComponent {

    var localTasksRepository: LocalTasksRepository

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(applicationContext: Context): Builder
        fun build(): ApplicationComponent
    }

    fun inject(activity: MainActivity)

    fun Context.getApplicationComponent(): ApplicationComponent = this@ApplicationComponent
}
