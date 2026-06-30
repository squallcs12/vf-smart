package com.daotranbang.vfsmart.data.network

import com.daotranbang.vfsmart.data.local.SecurePreferences
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * OkHttp interceptor that dynamically sets the base URL for each request
 * based on the device IP stored in SecurePreferences.
 *
 * This allows the app to test connections with new IP addresses
 * without recreating the Retrofit singleton.
 */
class DynamicBaseUrlInterceptor @Inject constructor(
    private val securePreferences: SecurePreferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Get current device IP from preferences (or use default)
        val deviceIp = securePreferences.getDeviceIp() ?: "192.168.4.1"

        // Build new URL with current device IP
        val newUrl = originalRequest.url.newBuilder()
            .scheme("http")
            .host(deviceIp)
            .port(80)
            .build()

        // Create new request with updated URL
        val newRequest = originalRequest.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(newRequest)
    }
}
