package com.mustafatoktas.objetanima.util

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

abstract class BaseViewModel (application: Application) : AndroidViewModel(application) ,
    CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main  //önce işi yap sonra maintheraed'a dön

    override fun onCleared() {
        super.onCleared()
        job.cancel()
    }

}