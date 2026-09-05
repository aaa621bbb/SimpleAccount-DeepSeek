package com.simpleaccount.app.auto

import com.simpleaccount.app.data.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoRecordEntryPoint {
    fun manager(): AutoRecordManager
    fun runtime(): AutoRecordRuntime
    fun settings(): SettingsRepository
}
