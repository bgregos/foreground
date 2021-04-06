package me.bgregos.foreground.network

import me.bgregos.foreground.model.SyncResult
import me.bgregos.foreground.model.Task

interface RemoteTaskSource {
    var tasks: MutableList<Task>
    var localChanges: MutableList<Task>
    var syncEnabled: Boolean

    suspend fun taskwarriorInitSync(): SyncResult

    suspend fun taskwarriorSync(): SyncResult

    suspend fun resetSync()

    suspend fun save()

    suspend fun load()
}