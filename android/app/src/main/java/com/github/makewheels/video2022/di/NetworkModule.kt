package com.github.makewheels.video2022.di

import com.github.makewheels.video2022.BuildConfig
import com.github.makewheels.video2022.data.api.*
import com.github.makewheels.video2022.util.TokenManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideOkHttpClient(tokenManager: TokenManager): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                tokenManager.getTokenSync()?.let { builder.addHeader("token", it) }
                tokenManager.getClientIdSync()?.let { builder.addHeader("clientId", it) }
                tokenManager.getSessionIdSync()?.let { builder.addHeader("sessionId", it) }
                chain.proceed(builder.build())
            }
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    @Named("main")
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    @Named("youtube")
    fun provideYouTubeRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.YOUTUBE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides @Singleton
    fun provideUserApi(@Named("main") retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)

    @Provides @Singleton
    fun provideVideoApi(@Named("main") retrofit: Retrofit): VideoApi = retrofit.create(VideoApi::class.java)

    @Provides @Singleton
    fun provideWatchApi(@Named("main") retrofit: Retrofit): WatchApi = retrofit.create(WatchApi::class.java)

    @Provides @Singleton
    fun provideCommentApi(@Named("main") retrofit: Retrofit): CommentApi = retrofit.create(CommentApi::class.java)

    @Provides @Singleton
    fun providePlaylistApi(@Named("main") retrofit: Retrofit): PlaylistApi = retrofit.create(PlaylistApi::class.java)

    @Provides @Singleton
    fun provideLikeApi(@Named("main") retrofit: Retrofit): LikeApi = retrofit.create(LikeApi::class.java)

    @Provides @Singleton
    fun provideYouTubeApi(@Named("youtube") retrofit: Retrofit): YouTubeApi = retrofit.create(YouTubeApi::class.java)

    @Provides @Singleton
    fun provideAppApi(@Named("main") retrofit: Retrofit): AppApi = retrofit.create(AppApi::class.java)
}
