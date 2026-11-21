package com.example.agenticrag.data

import com.example.agenticrag.BuildConfig
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface ApiService {
    @POST("api/query")
    suspend fun query(@Body request: QueryRequest): QueryResponse

    @GET("api/traces")
    suspend fun traces(@Query("session_id") sessionId: String): List<QueryResponse>

    @GET("api/memory")
    suspend fun memory(@Query("session_id") sessionId: String): MemoryResponse

    @POST("api/reset-memory")
    suspend fun resetMemory(@Body request: ResetRequest): ResetResponse

    @GET("api/corpus")
    suspend fun corpus(): CorpusResponse

    @GET("api/scenarios")
    suspend fun scenarios(): List<Scenario>
}

object ApiClient {
    private val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = GsonBuilder().setPrettyPrinting().create()

    val service: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }
}
