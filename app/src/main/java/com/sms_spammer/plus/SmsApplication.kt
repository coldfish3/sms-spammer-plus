package com.sms_spammer.plus

import android.app.Application
import timber.log.Timber

class SmsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
} 