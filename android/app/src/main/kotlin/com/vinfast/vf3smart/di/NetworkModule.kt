package com.vinfast.vf3smart.di

import com.google.gson.Gson
import com.vinfast.vf3smart.BuildConfig
import com.vinfast.vf3smart.data.local.SecurePreferences
import com.vinfast.vf3smart.data.network.AuthInterceptor
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
    fun provideAuthInterceptor(
        securePreferences: SecurePreferences
    ): AuthInterceptor {
        return AuthInterceptor(securePreferences)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
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
        gson: Gson,
        securePreferences: SecurePreferences
    ): Retrofit {
        // Use configured device IP or default
        val baseUrl = securePreferences.getDeviceIp()?.let { "http://$it/" }
            ?: "http://192.168.4.1/"

        return Retrofit.Builder()
            .baseUrl(baseUrl)
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
