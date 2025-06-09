package com.mykolashvets.diplomawork.domain.repo

import android.graphics.Bitmap
import com.mykolashvets.diplomawork.domain.entity.MushroomResult

interface MushroomRepository {
    suspend fun classify(bitmap: Bitmap): MushroomResult
    fun saveToHistory(label: String)
    fun getHistory(): List<String>
}