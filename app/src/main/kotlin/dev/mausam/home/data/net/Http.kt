package dev.mausam.home.data.net

import android.content.Context
import dev.mausam.home.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/** One OkHttp client, one Json, one Retrofit factory. Every source builds on these. */
object Http {
    /** Identifies the app to upstreams that require it (MET Norway) and is polite everywhere. */
    const val USER_AGENT = "MausamHome/${BuildConfig.VERSION_NAME} (SIH26076; Android)"

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun client(context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, "http")
        val ua = Interceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).header("Accept", "application/json, */*").build())
        }
        val builder = OkHttpClient.Builder()
            .cache(Cache(cacheDir, 10L * 1024 * 1024))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .addInterceptor(ua)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        return builder.build()
    }

    fun retrofit(client: OkHttpClient, baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
