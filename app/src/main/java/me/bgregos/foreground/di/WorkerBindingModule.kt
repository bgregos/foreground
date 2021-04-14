package me.bgregos.foreground.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.multibindings.IntoMap
import me.bgregos.foreground.network.TaskwarriorSyncWorker
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass

@MapKey
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class WorkerKey(val value: KClass<out ListenableWorker>)

@Module
interface WorkerBindingModule {

    @Binds
    fun bindInjectableWorkerFactory(factory: InjectableWorkerFactory): WorkerFactory

    @Binds
    @IntoMap
    @WorkerKey(TaskwarriorSyncWorker::class)
    fun bindTaskwarriorSyncWorker(factory: TaskwarriorSyncWorker.Factory): CustomWorkerFactory

}

class InjectableWorkerFactory @Inject constructor(
        private val workerFactories: Map<Class<out ListenableWorker>, @JvmSuppressWildcards Provider<CustomWorkerFactory>>
) : WorkerFactory() {
    override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters
    ): ListenableWorker? {
        val tranformedName = transformClassIfRequired(workerClassName)
        val foundEntry =
                workerFactories.entries.find { Class.forName(tranformedName).isAssignableFrom(it.key) }
        val factoryProvider = foundEntry?.value
                ?: throw IllegalArgumentException("unknown worker class name: $workerClassName")
        return factoryProvider.get().create(appContext, workerParameters)
    }

    private fun transformClassIfRequired(className: String): String {
        return when(className){
            "me.bgregos.foreground.task.TaskwarriorSyncWorker" -> "me.bgregos.foreground.network.TaskwarriorSyncWorker"
            else -> className
        }
    }
}