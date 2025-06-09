package com.mykolashvets.diplomawork.domain.usecases

import android.graphics.Bitmap
import com.mykolashvets.diplomawork.domain.repo.MushroomRepository

class ClassifyUseCase(private val repo: MushroomRepository) {
    suspend operator fun invoke(bitmap: Bitmap) = repo.classify(bitmap)
}
class SaveHistoryUseCase(private val repo: MushroomRepository) {
    operator fun invoke(label: String) = repo.saveToHistory(label)
}
class GetHistoryUseCase(private val repo: MushroomRepository) {
    operator fun invoke(): List<String> = repo.getHistory()
}