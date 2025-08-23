package com.sofindo.ems

import android.app.Application
import com.sofindo.ems.services.UserService

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        UserService.init(this)
    }
}
