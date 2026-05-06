package com.example.gitissuewidget.data.local

import com.example.gitissuewidget.data.remote.GitHubGraphQlClient
import com.example.gitissuewidget.domain.ProjectMeta
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * viewer (PAT 所有者) の Projects v2 メタ情報を **アプリプロセス内メモリ** にキャッシュする。
 *
 * 設計意図:
 * - GraphQL `viewer.projectsV2` を叩く回数を抑え、Project タイトル → [ProjectMeta] の高速ルックアップを提供する
 * - プロセス再起動でキャッシュは失われるが、初回アクセス時に再フェッチされるので問題なし
 * - GitHub 側で Project / カラムが追加された場合は [refresh] で明示的に取り直す（設定画面の「プロジェクト情報を更新」ボタンから）
 *
 * スレッドセーフ性: [Mutex] により listViewerProjects への並列呼び出しを 1 度に絞る。
 */
class ProjectMetaCache(private val graphQl: GitHubGraphQlClient) {

    private val mutex = Mutex()

    @Volatile
    private var snapshot: List<ProjectMeta>? = null

    /** キャッシュが空なら GraphQL から取得して保存する。常に最新一覧を返す。 */
    suspend fun list(): List<ProjectMeta> = mutex.withLock {
        snapshot ?: graphQl.listViewerProjects().also { snapshot = it }
    }

    /** 強制再取得。GitHub 側で Project / カラムを編集した直後に呼ぶ。 */
    suspend fun refresh(): List<ProjectMeta> = mutex.withLock {
        graphQl.listViewerProjects().also { snapshot = it }
    }

    /** Project title で検索（大小文字区別なし）。見つからなければ null。 */
    suspend fun findByTitle(title: String): ProjectMeta? {
        if (title.isBlank()) return null
        return list().firstOrNull { it.project.title.equals(title, ignoreCase = true) }
    }

    /** キャッシュを破棄。次回アクセスで再取得される。 */
    fun invalidate() {
        snapshot = null
    }
}
