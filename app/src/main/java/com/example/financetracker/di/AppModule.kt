package com.example.financetracker.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * БД создаётся и открывается лениво через DbHolder после ввода
 * графического ключа (ключ шифрования = PBKDF2 от узора), поэтому
 * здесь нет провайдеров Room.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
