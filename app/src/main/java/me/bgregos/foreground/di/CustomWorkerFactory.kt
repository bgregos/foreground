package me.bgregos.foreground.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters

interface CustomWorkerFactory {
    fun create(appContext: Context, params: WorkerParameters): ListenableWorker
}