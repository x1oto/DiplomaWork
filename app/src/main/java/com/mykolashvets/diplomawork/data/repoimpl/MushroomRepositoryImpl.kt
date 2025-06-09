package com.mykolashvets.diplomawork.data.repoimpl

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import com.mykolashvets.diplomawork.data.classifier.TFLiteClassifier
import com.mykolashvets.diplomawork.data.sharedpref.SharedPrefsStorage
import com.mykolashvets.diplomawork.domain.entity.MushroomResult
import com.mykolashvets.diplomawork.domain.repo.MushroomRepository

class MushroomRepositoryImpl(app: Application) : MushroomRepository {
    private val prefs = app.getSharedPreferences("mushroom_prefs", Context.MODE_PRIVATE)
    private val storage = SharedPrefsStorage(prefs)
    private val classifier = TFLiteClassifier(app)
    override suspend fun classify(bitmap: Bitmap): MushroomResult = classifier.classify(bitmap)
    override fun saveToHistory(label: String) = storage.append(label)
    override fun getHistory(): List<String> = storage.fetch()
}