package com.example.recallai.di

import com.example.recallai.data.MemoryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MemoryRepositoryEntryPoint {
    fun memoryRepository(): MemoryRepository
}
