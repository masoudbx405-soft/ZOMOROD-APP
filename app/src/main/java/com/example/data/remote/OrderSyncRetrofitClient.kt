package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Real connection to the web panel's server (step 4 of the app<->panel
 * coordination plan). Replaces the old fake `delay()` in
 * ZomorrodRepository.syncWithWebPanel().
 *
 * Both SERVER_BASE_URL and DRIVER_API_KEY come from BuildConfig (generated
 * from the local, gitignored `.env` file) — never hardcode them.
 * DRIVER_API_KEY must exactly match the same key in the panel's own .env.
 */
object OrderSyncRetrofitClient {

    private val authInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("x-driver-api-key", BuildConfig.DRIVER_API_KEY)
            .build()
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    }

    private val safeBaseUrl: String
        get() = if (BuildConfig.SERVER_BASE_URL.endsWith("/")) BuildConfig.SERVER_BASE_URL else "${BuildConfig.SERVER_BASE_URL}/"

    val api: OrderSyncApiService by lazy {
        Retrofit.Builder()
            .baseUrl(safeBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OrderSyncApiService::class.java)
    }
}
