package com.example.recallai.di

import com.example.recallai.face.FaceNetEmbedder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FaceNetEmbedderEntryPoint {
  fun faceNetEmbedder(): FaceNetEmbedder
}
