package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class NoaaMetadata(
  @Json(name = "id") val id: String? = null,
  @Json(name = "name") val name: String? = null,
  @Json(name = "lat") val lat: String? = null,
  @Json(name = "lon") val lon: String? = null
)

data class NoaaWaterLevelItem(
  @Json(name = "t") val time: String? = null,
  @Json(name = "v") val value: String? = null,
  @Json(name = "s") val standardDeviation: String? = null,
  @Json(name = "f") val flags: String? = null,
  @Json(name = "q") val quality: String? = null
)

data class NoaaWaterLevelResponse(
  @Json(name = "metadata") val metadata: NoaaMetadata? = null,
  @Json(name = "data") val data: List<NoaaWaterLevelItem>? = null,
  @Json(name = "error") val error: Map<String, Any>? = null
)

interface NoaaCoopsService {
  @GET("api/prod/datagetter")
  suspend fun getWaterLevel(
    @Query("station") stationId: String,
    @Query("date") date: String = "latest",
    @Query("product") product: String = "water_level",
    @Query("datum") datum: String = "MLLW",
    @Query("units") units: String = "metric",
    @Query("time_zone") timeZone: String = "gmt",
    @Query("application") application: String = "NavCast",
    @Query("format") format: String = "json"
  ): NoaaWaterLevelResponse
}

object NoaaApiClient {
  private const val BASE_URL = "https://api.tidesandcurrents.noaa.gov/"

  private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .build()

  private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

  val service: NoaaCoopsService by lazy {
    Retrofit.Builder()
      .baseUrl(BASE_URL)
      .client(okHttpClient)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(NoaaCoopsService::class.java)
  }
}
