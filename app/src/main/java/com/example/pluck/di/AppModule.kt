package com.example.pluck.di

import android.content.Context
import androidx.room.Room
import com.example.pluck.data.database.PluckDatabase
import com.example.pluck.data.network.StoryApi
import com.example.pluck.data.provider.ClaudeStoryProvider
import com.example.pluck.data.provider.GeminiStoryProvider
import com.example.pluck.data.provider.GroqStoryProvider
import com.example.pluck.data.localai.GemmaLocalStoryProvider
import com.example.pluck.data.localai.LiteRtModelManager
import com.example.pluck.data.provider.OpenAiStoryProvider
import com.example.pluck.data.provider.OpenRouterStoryProvider
import com.example.pluck.data.provider.TogetherStoryProvider
import com.example.pluck.data.repository.RoomJourneyRepository
import com.example.pluck.data.repository.RoomStoryRepository
import com.example.pluck.data.repository.RoomNovellaRepository
import com.example.pluck.data.repository.SecureSettingsRepository
import com.example.pluck.domain.provider.StoryProvider
import com.example.pluck.domain.repository.JourneyRepository
import com.example.pluck.domain.repository.SettingsRepository
import com.example.pluck.domain.repository.StoryRepository
import com.example.pluck.domain.repository.LocalAiRepository
import com.example.pluck.domain.repository.NovellaRepository
import com.google.android.gms.location.LocationServices
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindJourneyRepository(repository: RoomJourneyRepository): JourneyRepository
    @Binds abstract fun bindStoryRepository(repository: RoomStoryRepository): StoryRepository
    @Binds abstract fun bindNovellaRepository(repository: RoomNovellaRepository): NovellaRepository
    @Binds abstract fun bindSettingsRepository(repository: SecureSettingsRepository): SettingsRepository
    @Binds abstract fun bindLocalAiRepository(repository: LiteRtModelManager): LocalAiRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun provideDatabase(@ApplicationContext context: Context): PluckDatabase =
        Room.databaseBuilder(context, PluckDatabase::class.java, "pluck.db")
            .addMigrations(
                PluckDatabase.MIGRATION_1_2,
                PluckDatabase.MIGRATION_2_3,
                PluckDatabase.MIGRATION_3_4
            )
            .build()
    @Provides fun provideJourneyDao(database: PluckDatabase) = database.journeyDao()
    @Provides fun providePhotoDao(database: PluckDatabase) = database.journeyPhotoDao()
    @Provides fun provideStoryDao(database: PluckDatabase) = database.storyDao()
    @Provides fun provideNovellaDao(database: PluckDatabase) = database.novellaDao()
    @Provides @Singleton fun provideHttpClient(): OkHttpClient = OkHttpClient.Builder().build()
    @Provides @Singleton fun provideStoryApi(client: OkHttpClient): StoryApi = Retrofit.Builder().baseUrl("https://localhost/").client(client).build().create(StoryApi::class.java)
    @Provides fun provideLocationClient(@ApplicationContext context: Context) = LocationServices.getFusedLocationProviderClient(context)
    @Provides @Singleton fun provideProviders(gemini: GeminiStoryProvider, openAi: OpenAiStoryProvider, claude: ClaudeStoryProvider, groq: GroqStoryProvider, together: TogetherStoryProvider, openRouter: OpenRouterStoryProvider, local: GemmaLocalStoryProvider): Set<@JvmSuppressWildcards StoryProvider> = setOf(gemini, openAi, claude, groq, together, openRouter, local)
}
