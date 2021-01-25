package me.bgregos.foreground.util

import androidx.lifecycle.LiveData

/**
 * A wrapper around MutableLiveData that enforces non-nullability of its data
 */
@Suppress("UNCHECKED_CAST")
class NonNullableMutableLiveData<T>(value: T) : LiveData<T>(value) {
    override fun getValue(): T = super.getValue() as T
    public override fun setValue(value: T) = super.setValue(value)
    public override fun postValue(value: T) = super.postValue(value)
}

