package com.sms.edgenode.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Simple HTTP client using HttpURLConnection.
 * No external dependencies required.
 */
object HttpClient {
    
    val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var onUnauthorized: (suspend () -> Boolean)? = null
    
    data class Response(
        val code: Int,
        val body: String,
        val success: Boolean = code in 200..299
    )
    
    /**
     * POST request with JSON body.
     */
    suspend fun post(
        endpoint: String,
        body: Map<String, String>,
        token: String? = null
    ): Response = request("POST", endpoint, json.encodeToString(body), token)

    /**
     * POST request with raw JSON string.
     */
    suspend fun postJson(
        endpoint: String,
        jsonBody: String,
        token: String? = null
    ): Response = request("POST", endpoint, jsonBody, token)
    
    /**
     * GET request.
     */
    suspend fun get(
        endpoint: String,
        token: String? = null
    ): Response = request("GET", endpoint, null, token)
    
    /**
     * PUT request with JSON body.
     */
    suspend fun put(
        endpoint: String,
        body: Map<String, String>,
        token: String? = null
    ): Response = request("PUT", endpoint, json.encodeToString(body), token)

    /**
     * Generic HTTP request.
     */
    private var isRefreshing = java.util.concurrent.atomic.AtomicBoolean(false)

    private suspend fun request(
        method: String,
        endpoint: String,
        body: String?,
        token: String?,
        isRetry: Boolean = false
    ): Response {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var currentToken = token
            var connection: HttpURLConnection? = null
            try {
                val url = URL("${ApiConfig.baseUrl}$endpoint")
                connection = url.openConnection() as HttpURLConnection
                
                connection.apply {
                    requestMethod = method
                    connectTimeout = ApiConfig.CONNECT_TIMEOUT_MS
                    readTimeout = ApiConfig.READ_TIMEOUT_MS
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    
                    currentToken?.let {
                        setRequestProperty("Authorization", "Bearer $it")
                    }
                    
                    if (body != null && method != "GET") {
                        doOutput = true
                        OutputStreamWriter(outputStream).use { writer ->
                            writer.write(body)
                            writer.flush()
                        }
                    }
                }
                
                var responseCode = connection.responseCode
                
                // Handle 401 Unauthorized with auto-refresh
                // Guard: don't retry auth endpoints, don't recurse, and use atomic lock
                val isAuthEndpoint = endpoint.contains("/auth/")
                if (responseCode == 401 && !isRetry && !isAuthEndpoint && onUnauthorized != null) {
                    if (isRefreshing.compareAndSet(false, true)) {
                        try {
                            android.util.Log.w("HttpClient", "401 Unauthorized for $endpoint. Attempting refresh...")
                            if (onUnauthorized?.invoke() == true) {
                                android.util.Log.i("HttpClient", "Refresh successful. Retrying $endpoint...")
                                connection.disconnect()
                                // Retry with isRetry=true to prevent further recursion
                                // Pass token=null so the caller can re-read the refreshed token
                                return@withContext request(method, endpoint, body, token, isRetry = true)
                            }
                        } finally {
                            isRefreshing.set(false)
                        }
                    } else {
                        android.util.Log.d("HttpClient", "Refresh already in progress, skipping retry for $endpoint")
                    }
                }

                val responseBody = try {
                    if (responseCode in 200..299) {
                        connection.inputStream.bufferedReader().use(BufferedReader::readText)
                    } else {
                        connection.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HttpClient", "Error reading stream", e)
                    ""
                }
                
                android.util.Log.d("HttpClient", "$method $endpoint -> $responseCode")
                if (responseCode !in 200..299) {
                    android.util.Log.e("HttpClient", "Response error: $responseBody")
                }
                
                Response(responseCode, responseBody)
            } catch (e: Exception) {
                android.util.Log.e("HttpClient", "Network request failed: $endpoint", e)
                Response(-1, e.message ?: "Network error", false)
            } finally {
                connection?.disconnect()
            }
        }
    }
    
    /**
     * Parse JSON response to data class.
     */
    inline fun <reified T> parseResponse(response: Response): T? {
        return if (response.success && response.body.isNotBlank()) {
            try {
                json.decodeFromString<T>(response.body)
            } catch (e: Exception) {
                null
            }
        } else null
    }
}
