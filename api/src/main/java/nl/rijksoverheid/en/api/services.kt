/*
 * Copyright (c) 2020 De Staat der Nederlanden, Ministerie van Volksgezondheid, Welzijn en Sport.
 *  Licensed under the EUROPEAN UNION PUBLIC LICENCE v. 1.2
 *
 *  SPDX-License-Identifier: EUPL-1.2
 */
package nl.rijksoverheid.en.api

import android.content.Context
import android.net.Uri
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cache
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.io.File

private const val SSL_PIN = "sha256/QiOJQAOogcXfa6sWPbI1wiGhjVS/dZlFgg5nDaguPzk="

private var okHttpClient: OkHttpClient? = null

internal fun createMoshi() =
    Moshi.Builder().add(Base64Adapter()).add(KotlinJsonAdapterFactory()).build()

internal fun createOkHttpClient(context: Context): OkHttpClient {
    return okHttpClient ?: OkHttpClient.Builder()
        // enable cache for config and resource bundles
        .followRedirects(false)
        .apply {
            val cache = Cache(File(context.cacheDir, "http"), 32 * 1024 * 1024)
            cache(cache)
            addNetworkInterceptor(CacheOverrideInterceptor())
            addNetworkInterceptor(SignedResponseInterceptor())
            addInterceptor(PaddedRequestInterceptor())
            addInterceptor(SignedBodyInterceptor())
            if (Timber.forest().isNotEmpty()) {
                addInterceptor(HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
                    override fun log(message: String) {
                        Timber.tag("OkHttpClient").d(message)
                    }
                }).setLevel(HttpLoggingInterceptor.Level.BODY))
            }
            addInterceptor(CorruptedCacheInterceptor(cache))
            if (BuildConfig.FEATURE_SSL_PINNING) {
                connectionSpecs(
                    listOf(
                        ConnectionSpec.MODERN_TLS
                    )
                )
                certificatePinner(
                    CertificatePinner.Builder()
                        .add(Uri.parse(BuildConfig.CDN_BASE_URL).host!!, SSL_PIN)
                        .add(Uri.parse(BuildConfig.API_BASE_URL).host!!, SSL_PIN)
                        .build()
                )
            }
        }.build().also { okHttpClient = it }
}
