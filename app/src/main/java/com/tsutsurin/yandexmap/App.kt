package com.tsutsurin.yandexmap

import android.app.Application
import com.yandex.mapkit.MapKitFactory

class App: Application() {
    override fun onCreate() {
        super.onCreate()
        MapKitFactory.setApiKey("314f0af2-325c-4765-bb67-e8b0390cd71a")
    }
}