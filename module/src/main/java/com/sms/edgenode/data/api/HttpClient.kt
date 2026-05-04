package com.sms.edgenode.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.HostnameVerifier

/**
 * HTTP client using HttpURLConnection.
 * Supports the V2 API response envelope: { status_code, result: { data: T }, meta }
 */
object HttpClient {
    
    val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var onUnauthorized: (suspend () -> Boolean)? = null
    var tokenProvider: (() -> String?)? = null
    
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
     * DELETE request.
     */
    suspend fun delete(
        endpoint: String,
        token: String? = null
    ): Response = request("DELETE", endpoint, null, token)

    /**
     * Generic HTTP request with auto-refresh on 401.
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
                val url = URL(if (endpoint.startsWith("http")) endpoint else ApiConfig.BASE_URL + endpoint)
                connection = url.openConnection() as HttpURLConnection
                
                // Use default SSL hostname verification — TLS handles trust.
                // No custom HostnameVerifier needed for production.

                connection.apply {
                    requestMethod = method
                    connectTimeout = ApiConfig.CONNECT_TIMEOUT_MS
                    readTimeout = ApiConfig.READ_TIMEOUT_MS
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    
                    val resolvedToken = token ?: tokenProvider?.invoke()
                    resolvedToken?.let {
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
                val isAuthEndpoint = endpoint.startsWith("/mobile/")
                if (responseCode == 401 && !isRetry && !isAuthEndpoint && onUnauthorized != null) {
                    if (isRefreshing.compareAndSet(false, true)) {
                        try {
                            android.util.Log.w("HttpClient", "401 Unauthorized for $endpoint. Attempting refresh...")
                            if (onUnauthorized?.invoke() == true) {
                                android.util.Log.i("HttpClient", "Refresh successful. Retrying $endpoint with fresh token...")
                                connection.disconnect()
                                // Retry with isRetry=true to prevent further recursion.
                                // Pass null as token so it gets re-fetched via tokenProvider.
                                return@withContext request(method, endpoint, body, null, isRetry = true)
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
     * Parse a V2 API response envelope.
     *
     * V2 envelope shape:
     * ```json
     * { "status_code": 200, "result": { "data": <T> }, "meta": { ... } }
     * ```
     *
     * This method extracts `result.data` and deserializes it as T.
     */
    inline fun <reified T> parseV2Response(response: Response): T? {
        if (!response.success || response.body.isBlank()) return null
        return try {
            val root = json.parseToJsonElement(response.body).jsonObject
            val result = root["result"]?.jsonObject ?: return null
            val data = result["data"] ?: return null
            json.decodeFromString<T>(data.toString())
        } catch (e: Exception) {
            android.util.Log.e("HttpClient", "V2 parse failed, falling back to direct parse", e)
            // Fallback: try parsing the body directly (for endpoints that may not wrap)
            try {
                json.decodeFromString<T>(response.body)
            } catch (e2: Exception) {
                null
            }
        }
    }
    
    /**
     * Parse JSON response body directly to data class (legacy V1 compat / simple payloads).
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
