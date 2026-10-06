package com.chochocho.homephotoclient

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.chochocho.homephotoclient.data.SettingsRepository
import com.chochocho.homephotoclient.data.serverRouting
import com.chochocho.homephotoclient.data.trackServerRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class HomePhotoApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        val repository = SettingsRepository(this)
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(serverRouting(this) { runBlocking { repository.settings.first() } })
            .addNetworkInterceptor(trackServerRequest)
            .build()
        return ImageLoader.Builder(this).okHttpClient(client).build()
    }
}
