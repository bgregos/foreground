package me.bgregos.foreground.di

import dagger.Component
import javax.inject.Singleton

@Component(modules= [
    ViewModelModule::class
] )
@Singleton
interface ApplicationComponent {

}
