package me.bgregos.foreground.util

import androidx.lifecycle.MutableLiveData

/**
 * Triggers a LiveData update. This is useful for when a list's contents have changed.
 */
fun MutableLiveData<out MutableCollection<out Any>>.contentsChanged() {
    this.value = this.value
}

