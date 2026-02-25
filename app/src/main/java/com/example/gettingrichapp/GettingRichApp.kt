package com.example.gettingrichapp

import android.app.Application

class GettingRichApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(this)
    }
}
