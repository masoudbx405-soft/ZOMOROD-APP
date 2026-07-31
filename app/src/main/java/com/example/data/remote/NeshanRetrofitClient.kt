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
 * Single Retrofit instance for every Neshan API call in the app.
 *
 * The API key is NEVER hardcoded here: it comes from BuildConfig.NESHAN_API_KEY,
 * which the secrets-gradle-plugin generates from the local, gitignored `.env`
 * file (see /.env.example for the placeholder documentation).
 */
object NeshanRetrofitClient {

    private const val BASE_URL = "https://api.neshan.org/"

    val apiKey: String get() = BuildConfig.NESHAN_API_KEY

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    }

    val api: NeshanApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(NeshanApiService::class.java)
    }

    /**
     * Builds a real Neshan Static Map image URL (used to replace the old
     * hand-drawn fake "city block" Canvas preview with an actual map tile).
     */
    fun buildStaticMapUrl(
        lat: Double,
        lng: Double,
        widthPx: Int = 600,
        heightPx: Int = 300,
        zoom: Int = 16
    ): String {
        return "${BASE_URL}v4/static" +
            "?key=$apiKey" +
            "&type=standard-day" +
            "&center=$lat,$lng" +
            "&zoom=$zoom" +
            "&width=$widthPx" +
            "&height=$heightPx" +
            "&marker=$lat,$lng"
    }
}
