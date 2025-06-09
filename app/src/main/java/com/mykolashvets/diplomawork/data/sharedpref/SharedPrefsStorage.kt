package com.mykolashvets.diplomawork.data.sharedpref

import android.content.SharedPreferences

class SharedPrefsStorage(private val prefs: SharedPreferences) {
    companion object {
        private const val KEY = "history_set"
    }
    fun append(label: String) {
        val set = prefs.getStringSet(KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        set.add(label)
        prefs.edit().putStringSet(KEY, set).apply()
    }
    fun fetch(): List<String> = prefs.getStringSet(KEY, emptySet())?.toList() ?: emptyList()
}