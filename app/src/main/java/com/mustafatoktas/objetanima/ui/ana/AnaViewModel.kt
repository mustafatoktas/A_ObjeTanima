package com.mustafatoktas.objetanima.ui.ana

import android.app.Application
import androidx.lifecycle.MutableLiveData
import com.mustafatoktas.objetanima.model.TaninanObjeler
import com.mustafatoktas.objetanima.util.BaseViewModel

class AnaViewModel (application: Application): BaseViewModel(application) {

    val taninanObjelerListesiLiveData = MutableLiveData<List<TaninanObjeler>>()
}