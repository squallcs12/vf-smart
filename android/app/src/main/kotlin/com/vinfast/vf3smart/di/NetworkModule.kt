package com.vinfast.vf3smart.di

import com.google.gson.Gson
import com.vinfast.vf3smart.BuildConfig
import com.vinfast.vf3smart.data.local.SecurePreferences
import com.vinfast.vf3smart.data.network.AuthInterceptor
import com.vinfast.vf3smart.data.network.DynamicBaseUrlInterceptor
import com.vinfast.vf3smart.data.network.UdpDiscoveryService
import com.vinfast.vf3smart.data.network.VF3ApiService
import com.vinfast.vf3smart.data.network.WebSocketManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module for network dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideDynamicBaseUrlInterceptor(
        securePreferences: SecurePreferences
    ): DynamicBaseUrlInterceptor {
        return DynamicBaseUrlInterceptor(securePreferences)
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        securePreferences: SecurePreferences
    ): AuthInterceptor {
        return AuthInterceptor(securePreferences)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor,
        authInterceptor: AuthInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(dynamicBaseUrlInterceptor)  // First: change host dynamically
            .addInterceptor(authInterceptor)            // Second: add API key
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                }
            )
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        // Note: The actual URL is set dynamically by DynamicBaseUrlInterceptor
        // This base URL is just a placeholder required by Retrofit
        return Retrofit.Builder()
            .baseUrl("http://192.168.4.1/")  // Placeholder, overridden by interceptor
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideVF3ApiService(retrofit: Retrofit): VF3ApiService {
        return retrofit.create(VF3ApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideWebSocketManager(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): WebSocketManager {
        return WebSocketManager(okHttpClient, gson)
    }

    @Provides
    @Singleton
    fun provideUdpDiscoveryService(
        gson: Gson
    ): UdpDiscoveryService {
        return UdpDiscoveryService(gson)
    }
}
