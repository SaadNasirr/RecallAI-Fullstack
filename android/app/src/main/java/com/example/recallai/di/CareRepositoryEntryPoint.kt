package com.example.recallai.di

import com.example.recallai.data.CareRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CareRepositoryEntryPoint {
    fun careRepository(): CareRepository
}
