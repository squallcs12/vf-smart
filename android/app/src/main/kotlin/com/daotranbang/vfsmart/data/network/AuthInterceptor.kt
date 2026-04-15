package com.daotranbang.vfsmart.data.network

import com.daotranbang.vfsmart.data.local.SecurePreferences
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * OkHttp interceptor that adds API key authentication to requests
 *
 * Authentication method: X-API-Key header
 * Skips authentication for: GET /car/status (public endpoint)
 */
class AuthInterceptor @Inject constructor(
    private val securePreferences: SecurePreferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip auth for GET /car/status (public endpoint)
        if (originalRequest.url.encodedPath == "/car/status" &&
            originalRequest.method == "GET"
        ) {
            return chain.proceed(originalRequest)
        }

        // Add API key header for all other requests
        val apiKey = securePreferences.getApiKey() ?: ""
        val newRequest = originalRequest.newBuilder()
            .addHeader("X-API-Key", apiKey)
            .build()

        return chain.proceed(newRequest)
    }
}
