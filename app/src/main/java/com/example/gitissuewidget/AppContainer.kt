package com.example.gitissuewidget

import android.content.Context
import com.example.gitissuewidget.data.local.PreferenceStore
import com.example.gitissuewidget.data.local.TokenStore
import com.example.gitissuewidget.data.remote.GitHubApi
import com.example.gitissuewidget.data.remote.NetworkModule
import com.example.gitissuewidget.data.repo.IssueRepository
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    val tokenStore: TokenStore = TokenStore(context)
    val preferenceStore: PreferenceStore = PreferenceStore(context)

    private val okHttpClient: OkHttpClient = NetworkModule.createOkHttp(tokenStore)
    val gitHubApi: GitHubApi = NetworkModule.createGitHubApi(okHttpClient)

    val issueRepository: IssueRepository = IssueRepository(gitHubApi)
}
