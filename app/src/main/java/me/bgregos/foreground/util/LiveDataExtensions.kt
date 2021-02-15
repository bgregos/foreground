package me.bgregos.foreground.util

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData

/**
 * A wrapper around MutableLiveData that enforces non-nullability of its data
 */
@Suppress("UNCHECKED_CAST")
class NonNullableMutableLiveData<T>(value: T) : MutableLiveData<T>(value) {
    override fun getValue(): T = super.getValue() as T
    override fun setValue(value: T) = super.setValue(value)
    override fun postValue(value: T) = super.postValue(value)
}

/**
 * A wrapper around LiveData that enforces non-nullability of its data
 */
@Suppress("UNCHECKED_CAST")
class NonNullableLiveData<T>(value: T) : LiveData<T>(value) {
    override fun getValue(): T = super.getValue() as T
}

/**
 * A wrapper around MediatorLiveData that enforces non-nullability of its data
 */
@Suppress("UNCHECKED_CAST")
class NonNullableMediatorLiveData<T>(initalValue: T) : MediatorLiveData<T>() {
    init{
        this.value = initalValue
    }
    override fun getValue(): T = super.getValue() as T
}

/**
 * Triggers a LiveData update. This is useful for when a list's contents have changed.
 */
fun MutableLiveData<out MutableCollection<out Any>>.contentsChanged() {
    this.value = this.value
}

