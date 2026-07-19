package com.example.pluck.data.network

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Url

interface StoryApi {
    @POST
    suspend fun post(@Url url: String, @HeaderMap headers: Map<String, String>, @Body body: RequestBody): Response<ResponseBody>
}
