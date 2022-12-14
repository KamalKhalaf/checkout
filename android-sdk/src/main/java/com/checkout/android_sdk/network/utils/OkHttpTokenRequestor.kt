package com.checkout.android_sdk.network.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.checkout.android_sdk.BuildConfig
import com.checkout.android_sdk.CheckoutAPIClient
import com.checkout.android_sdk.FramesLogger
import com.checkout.android_sdk.Response.TokenisationResponse
import com.checkout.android_sdk.Utils.Environment
import com.checkout.android_sdk.network.InternalCardTokenGeneratedListener
import com.checkout.android_sdk.network.InternalGooglePayTokenGeneratedListener
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

internal class OkHttpTokenRequestor(
    private val environment: Environment,
    private val key: String,
    private val gson: Gson,
    private val logger: FramesLogger
) : TokenRequestor {

    private val okHttpClient by lazy { newOkHttpClient() }

    /**
     * Use a handler with main Looper to trigger the result callback on the main thread.
     *
     * This is required to maintain backward compatibility with the contract offered by Volley.
     */
    private fun responseHandler(): Handler {
        return Handler(Looper.getMainLooper())
    }

    override fun requestCardToken(
        correlationID: String?,
        requestBody: String,
        listener: CheckoutAPIClient.OnTokenGenerated
    ) {
        val internalListener = InternalCardTokenGeneratedListener(
            listener,
            responseHandler(),
            logger
        )
        val callback = CardTokenCallback(
            internalListener,
            gson
        )

        executeOkHttpRequest(
            url = environment.token,
            correlationID = correlationID,
            requestBody = requestBody,
            callback = callback
        )
    }

    override fun requestGooglePayToken(
        correlationID: String?,
        requestBody: String,
        listener: CheckoutAPIClient.OnGooglePayTokenGenerated
    ) {
        val internalListener = InternalGooglePayTokenGeneratedListener(
            listener,
            responseHandler(),
            logger
        )
        val callback = GooglePayTokenCallback(
            internalListener,
            gson
        )

        executeOkHttpRequest(
            url = environment.googlePay,
            correlationID = correlationID,
            requestBody = requestBody,
            callback = callback
        )
    }

    private inline fun <reified T : TokenisationResponse> executeOkHttpRequest(
        url: String,
        correlationID: String?,
        requestBody: String,
        callback: OkHttpTokenCallback<T>
    ) {

        val tokenRequest = Request.Builder()
            .url(url)
            .addHeader(HEADER_AUTHORIZATION, key)
            .addHeader(HEADER_USER_AGENT_NAME, HEADER_USER_AGENT_VALUE)
            .addHeaderIfNotEmpty(HEADER_CKO_CORRELATION_ID, correlationID)
            .post(requestBody.toRequestBody(jsonMediaType))
            .build()

        okHttpClient
            .newCall(tokenRequest)
            .enqueue(callback)
    }

    companion object {
        private val LOGGING_ENABLED = BuildConfig.DEBUG

        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val HEADER_USER_AGENT_NAME = "User-Agent"
        private const val HEADER_USER_AGENT_VALUE = "checkout-sdk-frames-android/${BuildConfig.PRODUCT_VERSION}"
        private const val HEADER_CKO_CORRELATION_ID = "Cko-Correlation-Id"

        private const val CALL_TIMEOUT_MS = 10000L

        private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

        private fun newOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .callTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .addLoggingInterceptor()
            .cache(null)
            .build()

        private fun Request.Builder.addHeaderIfNotEmpty(
            name: String,
            value: String?
        ): Request.Builder {

            if (!value.isNullOrBlank()) {
                addHeader(name, value)
            }

            return this
        }

        private fun OkHttpClient.Builder.addLoggingInterceptor(): OkHttpClient.Builder {
            if (LOGGING_ENABLED) {
                val loggingInterceptor = HttpLoggingInterceptor { message ->
                    Log.d("[okHttp]", message)
                }
                loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
                addInterceptor(loggingInterceptor)
            }

            return this
        }
    }
}
