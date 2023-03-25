package me.bgregos.foreground.data.tasks

import java.io.File
import javax.inject.Inject
import javax.inject.Named

class TaskFileStorage @Inject constructor(
    @Named("InternalFiles") val internalFileDir: File,
    @Named("ExternalFiles") val eternalFileDir: File?
) {
    /*
    Implement save and load functions for each of internal files and external files, then have
    TaskRepository call the functions as appropriate based on user preference.
     */
}