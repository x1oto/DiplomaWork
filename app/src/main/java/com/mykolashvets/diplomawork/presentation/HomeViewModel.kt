package com.mykolashvets.diplomawork.presentation

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mykolashvets.diplomawork.data.repoimpl.MushroomRepositoryImpl
import com.mykolashvets.diplomawork.domain.entity.MushroomResult
import com.mykolashvets.diplomawork.domain.repo.MushroomRepository
import com.mykolashvets.diplomawork.domain.usecases.ClassifyUseCase
import com.mykolashvets.diplomawork.domain.usecases.SaveHistoryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: MushroomRepository = MushroomRepositoryImpl(app)
    private val classify = ClassifyUseCase(repo)
    private val save = SaveHistoryUseCase(repo)
    private val _result = MutableLiveData<MushroomResult>()
    val result: LiveData<MushroomResult> = _result
    fun classifyBitmap(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            val r = classify(bitmap)
            save(r.label)
            _result.postValue(r)
        }
    }
}