package com.example.gitissuewidget

import android.app.Application
import com.example.gitissuewidget.widget.IssueRefreshWorker

class IssueWidgetApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        IssueRefreshWorker.schedule(this)
    }
}
