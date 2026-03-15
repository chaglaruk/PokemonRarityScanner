package com.pokerarity.scanner.di

import android.content.Context
import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.data.repository.PokemonRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun providePokemonRepository(database: AppDatabase): PokemonRepository {
        return PokemonRepository(database)
    }
}