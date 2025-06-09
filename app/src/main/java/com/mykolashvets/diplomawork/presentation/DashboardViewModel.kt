package com.mykolashvets.diplomawork.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.mykolashvets.diplomawork.data.repoimpl.MushroomRepositoryImpl
import com.mykolashvets.diplomawork.domain.repo.MushroomRepository
import com.mykolashvets.diplomawork.domain.usecases.GetHistoryUseCase

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: MushroomRepository = MushroomRepositoryImpl(app)
    private val getHistory = GetHistoryUseCase(repo)
    val history: LiveData<List<String>> = liveData { emit(getHistory()) }
}