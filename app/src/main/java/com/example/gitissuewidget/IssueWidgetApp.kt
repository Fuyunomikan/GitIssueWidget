package com.example.gitissuewidget

import android.app.Application

class IssueWidgetApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
