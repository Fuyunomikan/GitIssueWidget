package com.example.gitissuewidget

import android.content.Context
import com.example.gitissuewidget.data.local.IssueCacheStore
import com.example.gitissuewidget.data.local.PreferenceStore
import com.example.gitissuewidget.data.local.ProjectMetaCache
import com.example.gitissuewidget.data.local.TokenStore
import com.example.gitissuewidget.data.local.WidgetConfigStore
import com.example.gitissuewidget.data.remote.GitHubApi
import com.example.gitissuewidget.data.remote.GitHubGraphQlApi
import com.example.gitissuewidget.data.remote.GitHubGraphQlClient
import com.example.gitissuewidget.data.remote.NetworkModule
import com.example.gitissuewidget.data.repo.IssueRepository
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    val tokenStore: TokenStore = TokenStore(context)
    val preferenceStore: PreferenceStore = PreferenceStore(context)
    val widgetConfigStore: WidgetConfigStore = WidgetConfigStore(context)
    val issueCacheStore: IssueCacheStore = IssueCacheStore(context)

    private val okHttpClient: OkHttpClient = NetworkModule.createOkHttp(tokenStore)
    val gitHubApi: GitHubApi = NetworkModule.createGitHubApi(okHttpClient)
    val gitHubGraphQlApi: GitHubGraphQlApi = NetworkModule.createGitHubGraphQlApi(okHttpClient)
    val gitHubGraphQlClient: GitHubGraphQlClient = GitHubGraphQlClient(gitHubGraphQlApi)
    val projectMetaCache: ProjectMetaCache = ProjectMetaCache(gitHubGraphQlClient)

    val issueRepository: IssueRepository = IssueRepository(
        api = gitHubApi,
        graphQl = gitHubGraphQlClient,
        projectMetaCache = projectMetaCache,
    )
}
