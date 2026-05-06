package com.example.gitissuewidget.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gitissuewidget.domain.Issue
import com.example.gitissuewidget.domain.IssueState
import com.example.gitissuewidget.domain.Label
import com.example.gitissuewidget.domain.RepoRef
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.issueCacheDataStore: DataStore<Preferences> by preferencesDataStore(name = "issue_cache")

class IssueCacheStore(context: Context) {

    private val dataStore = context.applicationContext.issueCacheDataStore
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun save(appWidgetId: Int, issues: List<Issue>) {
        val envelope = CachedEnvelope(
            savedAtMillis = System.currentTimeMillis(),
            issues = issues.map(::toCached),
        )
        val encoded = json.encodeToString(CachedEnvelope.serializer(), envelope)
        dataStore.edit { it[envelopeKey(appWidgetId)] = encoded }
    }

    suspend fun load(appWidgetId: Int): CachedIssues? {
        val raw = dataStore.data.first()[envelopeKey(appWidgetId)] ?: return null
        return runCatching {
            val env = json.decodeFromString(CachedEnvelope.serializer(), raw)
            CachedIssues(
                savedAtMillis = env.savedAtMillis,
                issues = env.issues.map(::toDomain),
            )
        }.getOrNull()
    }

    suspend fun clear(appWidgetId: Int) {
        dataStore.edit { it.remove(envelopeKey(appWidgetId)) }
    }

    private fun envelopeKey(id: Int) = stringPreferencesKey("widget_${id}_cache")

    @Serializable
    private data class CachedEnvelope(
        val savedAtMillis: Long,
        val issues: List<CachedIssue>,
    )

    @Serializable
    private data class CachedIssue(
        val number: Int,
        val title: String,
        val htmlUrl: String,
        val state: String,
        val labels: List<CachedLabel>,
        val updatedAt: String,
        val createdAt: String = "",
        val commentsCount: Int = 0,
        val repoOwner: String,
        val repoName: String,
        val nodeId: String = "",
    )

    @Serializable
    private data class CachedLabel(val name: String, val color: String)

    private fun toCached(issue: Issue) = CachedIssue(
        number = issue.number,
        title = issue.title,
        htmlUrl = issue.htmlUrl,
        state = if (issue.state == IssueState.CLOSED) "closed" else "open",
        labels = issue.labels.map { CachedLabel(it.name, it.colorHex) },
        updatedAt = issue.updatedAt,
        createdAt = issue.createdAt,
        commentsCount = issue.commentsCount,
        repoOwner = issue.repoRef.owner,
        repoName = issue.repoRef.name,
        nodeId = issue.nodeId,
    )

    private fun toDomain(c: CachedIssue) = Issue(
        number = c.number,
        title = c.title,
        htmlUrl = c.htmlUrl,
        state = IssueState.fromApi(c.state),
        labels = c.labels.map { Label(it.name, it.color) },
        updatedAt = c.updatedAt,
        createdAt = c.createdAt,
        commentsCount = c.commentsCount,
        repoRef = RepoRef(c.repoOwner, c.repoName),
        nodeId = c.nodeId,
    )
}

data class CachedIssues(
    val savedAtMillis: Long,
    val issues: List<Issue>,
)
