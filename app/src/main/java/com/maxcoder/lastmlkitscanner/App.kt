package com.maxcoder.lastmlkitscanner

import android.app.Application
import com.maxcoder.lastmlkitscanner.data.analyzer.OpenCVPreprocessor
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        OpenCVPreprocessor.init()
    }
}