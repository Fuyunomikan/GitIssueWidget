package com.example.gitissuewidget.data.remote

import com.example.gitissuewidget.data.remote.dto.GraphQlRequest
import com.example.gitissuewidget.domain.Issue
import com.example.gitissuewidget.domain.IssueState
import com.example.gitissuewidget.domain.Label
import com.example.gitissuewidget.domain.Project
import com.example.gitissuewidget.domain.ProjectColumn
import com.example.gitissuewidget.domain.ProjectMeta
import com.example.gitissuewidget.domain.RepoRef
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * GitHub GraphQL API v4 を叩くクライアント。
 * Projects v2 関連の操作（プロジェクト一覧/Status 更新/Issue→Project 追加/Project items 列挙）を提供する。
 *
 * 各メソッドは GraphQL レスポンスの `errors` を検査し、非空なら [GraphQlException] を throw する。
 */
class GitHubGraphQlClient(private val api: GitHubGraphQlApi) {

    /**
     * PAT 所有者 (viewer) のすべての Projects v2 を、Status (SingleSelect) フィールドの
     * メタ情報込みで取得する。Status フィールドが無い Project はスキップされる。
     */
    suspend fun listViewerProjects(): List<ProjectMeta> {
        val response = api.execute(GraphQlRequest(query = QUERY_LIST_PROJECTS))
        response.throwIfErrors()
        val nodes = response.data
            ?.get("viewer")?.jsonObject
            ?.get("projectsV2")?.jsonObject
            ?.get("nodes")?.jsonArray
            ?: return emptyList()
        return nodes.mapNotNull { it.parseProjectMeta() }
    }

    /**
     * 指定 Issue が指定 Project に既に追加されている場合、そのプロジェクトアイテム ID を返す。
     * 追加されていなければ null。
     */
    suspend fun findProjectItemId(projectNodeId: String, issueNodeId: String): String? {
        val variables = buildJsonObject { put("issueId", issueNodeId) }
        val response = api.execute(GraphQlRequest(QUERY_FIND_ITEM, variables))
        response.throwIfErrors()
        val items = response.data
            ?.get("node")?.jsonObject
            ?.get("projectItems")?.jsonObject
            ?.get("nodes")?.jsonArray
            ?: return null
        return items.firstOrNull { node ->
            node.jsonObject["project"]?.jsonObject?.get("id")?.jsonPrimitive?.content == projectNodeId
        }?.jsonObject?.get("id")?.jsonPrimitive?.content
    }

    /**
     * Issue を Project に追加し、生成されたアイテム ID を返す。既に追加済みでも GitHub 側は
     * 既存アイテムを返す。
     */
    suspend fun addItemToProject(projectNodeId: String, issueNodeId: String): String {
        val variables = buildJsonObject {
            put("projectId", projectNodeId)
            put("contentId", issueNodeId)
        }
        val response = api.execute(GraphQlRequest(MUTATION_ADD_ITEM, variables))
        response.throwIfErrors()
        return response.data
            ?.get("addProjectV2ItemById")?.jsonObject
            ?.get("item")?.jsonObject
            ?.get("id")?.jsonPrimitive?.content
            ?: throw GraphQlException("addProjectV2ItemById のレスポンスに item.id が含まれていません")
    }

    /**
     * 指定アイテムの Status フィールドを `optionId` のオプションに更新する。
     */
    suspend fun updateItemStatus(
        projectNodeId: String,
        itemId: String,
        statusFieldId: String,
        optionId: String,
    ) {
        val variables = buildJsonObject {
            put("projectId", projectNodeId)
            put("itemId", itemId)
            put("fieldId", statusFieldId)
            put("optionId", optionId)
        }
        val response = api.execute(GraphQlRequest(MUTATION_UPDATE_STATUS, variables))
        response.throwIfErrors()
    }

    /**
     * Project に紐づくアイテム（Issue のみ）を最大 [first] 件取得する。
     * DraftIssue / PullRequest は除外。各アイテムには Status の現在値 (`statusOptionName`) が含まれる。
     */
    suspend fun listProjectItems(projectNodeId: String, first: Int = 100): List<ProjectItem> {
        val variables = buildJsonObject {
            put("projectId", projectNodeId)
            put("first", first)
        }
        val response = api.execute(GraphQlRequest(QUERY_LIST_ITEMS, variables))
        response.throwIfErrors()
        val items = response.data
            ?.get("node")?.jsonObject
            ?.get("items")?.jsonObject
            ?.get("nodes")?.jsonArray
            ?: return emptyList()
        return items.mapNotNull { it.jsonObject.parseProjectItem() }
    }

    private fun com.example.gitissuewidget.data.remote.dto.GraphQlResponse.throwIfErrors() {
        val errs = errors
        if (!errs.isNullOrEmpty()) {
            throw GraphQlException(errs.joinToString("; ") { it.message })
        }
    }

    private fun JsonElement.parseProjectMeta(): ProjectMeta? {
        val obj = jsonObject
        val id = obj["id"]?.jsonPrimitive?.content ?: return null
        val number = obj["number"]?.jsonPrimitive?.int ?: return null
        val title = obj["title"]?.jsonPrimitive?.content ?: return null
        val statusField = obj["statusField"]?.jsonObject ?: return null
        if (statusField.isEmpty()) return null
        val statusFieldId = statusField["id"]?.jsonPrimitive?.content ?: return null
        val options = statusField["options"]?.jsonArray ?: return null
        val columns = options.mapNotNull { opt ->
            val o = opt.jsonObject
            val optId = o["id"]?.jsonPrimitive?.content
            val optName = o["name"]?.jsonPrimitive?.content
            if (optId != null && optName != null) ProjectColumn(optId, optName) else null
        }
        return ProjectMeta(
            project = Project(nodeId = id, number = number, title = title),
            statusFieldId = statusFieldId,
            columns = columns,
        )
    }

    private fun JsonObject.parseProjectItem(): ProjectItem? {
        val itemId = this["id"]?.jsonPrimitive?.content ?: return null
        val content = this["content"]?.jsonObject ?: return null
        if (content.isEmpty()) return null  // DraftIssue / unsupported content
        val issueNodeId = content["id"]?.jsonPrimitive?.content ?: return null
        val number = content["number"]?.jsonPrimitive?.int ?: return null
        val title = content["title"]?.jsonPrimitive?.content ?: return null
        val url = content["url"]?.jsonPrimitive?.content ?: return null
        val state = content["state"]?.jsonPrimitive?.content ?: "open"
        val updatedAt = content["updatedAt"]?.jsonPrimitive?.content ?: ""
        val createdAt = content["createdAt"]?.jsonPrimitive?.content ?: ""
        val comments = content["comments"]?.jsonObject?.get("totalCount")?.jsonPrimitive?.int ?: 0
        val labels = content["labels"]?.jsonObject?.get("nodes")?.jsonArray
            ?.mapNotNull { node ->
                val o = node.jsonObject
                val name = o["name"]?.jsonPrimitive?.content
                val color = o["color"]?.jsonPrimitive?.content ?: "808080"
                if (name != null) Label(name, color) else null
            }
            ?: emptyList()
        val repoObj = content["repository"]?.jsonObject ?: return null
        val repoName = repoObj["name"]?.jsonPrimitive?.content ?: return null
        val repoOwner = repoObj["owner"]?.jsonObject
            ?.get("login")?.jsonPrimitive?.content ?: return null

        val statusOptionName = this["fieldValues"]?.jsonObject?.get("nodes")?.jsonArray
            ?.firstNotNullOfOrNull { node ->
                val o = node.jsonObject
                val fieldName = o["field"]?.jsonObject?.get("name")?.jsonPrimitive?.content
                if (fieldName.equals("Status", ignoreCase = true)) {
                    o["name"]?.jsonPrimitive?.content
                } else null
            }

        val issue = Issue(
            number = number,
            title = title,
            htmlUrl = url,
            state = IssueState.fromApi(state),
            labels = labels,
            updatedAt = updatedAt,
            createdAt = createdAt,
            commentsCount = comments,
            repoRef = RepoRef(repoOwner, repoName),
            nodeId = issueNodeId,
        )
        return ProjectItem(itemId = itemId, statusOptionName = statusOptionName, issue = issue)
    }

    companion object {
        private const val QUERY_LIST_PROJECTS = """
            query {
              viewer {
                projectsV2(first: 100) {
                  nodes {
                    id
                    number
                    title
                    statusField: field(name: "Status") {
                      ... on ProjectV2SingleSelectField {
                        id
                        options { id name }
                      }
                    }
                  }
                }
              }
            }
        """

        private const val QUERY_FIND_ITEM = """
            query FindItem(${'$'}issueId: ID!) {
              node(id: ${'$'}issueId) {
                ... on Issue {
                  projectItems(first: 50) {
                    nodes {
                      id
                      project { id }
                    }
                  }
                }
              }
            }
        """

        private const val MUTATION_ADD_ITEM = """
            mutation AddItem(${'$'}projectId: ID!, ${'$'}contentId: ID!) {
              addProjectV2ItemById(input: { projectId: ${'$'}projectId, contentId: ${'$'}contentId }) {
                item { id }
              }
            }
        """

        private const val MUTATION_UPDATE_STATUS = """
            mutation UpdateStatus(${'$'}projectId: ID!, ${'$'}itemId: ID!, ${'$'}fieldId: ID!, ${'$'}optionId: String!) {
              updateProjectV2ItemFieldValue(input: {
                projectId: ${'$'}projectId,
                itemId: ${'$'}itemId,
                fieldId: ${'$'}fieldId,
                value: { singleSelectOptionId: ${'$'}optionId }
              }) {
                projectV2Item { id }
              }
            }
        """

        private const val QUERY_LIST_ITEMS = """
            query ListItems(${'$'}projectId: ID!, ${'$'}first: Int!) {
              node(id: ${'$'}projectId) {
                ... on ProjectV2 {
                  items(first: ${'$'}first) {
                    nodes {
                      id
                      fieldValues(first: 20) {
                        nodes {
                          ... on ProjectV2ItemFieldSingleSelectValue {
                            name
                            field {
                              ... on ProjectV2SingleSelectField { id name }
                            }
                          }
                        }
                      }
                      content {
                        ... on Issue {
                          id
                          number
                          title
                          url
                          state
                          updatedAt
                          createdAt
                          comments { totalCount }
                          labels(first: 20) { nodes { name color } }
                          repository { name owner { login } }
                        }
                      }
                    }
                  }
                }
              }
            }
        """
    }
}

/**
 * Project の 1 アイテム。Project v2 への参照と元の Issue 情報を保持。
 * ウィジェット表示は [issue] を、カラム移動は [itemId] / [statusOptionName] を使用する。
 */
data class ProjectItem(
    val itemId: String,
    val statusOptionName: String?,
    val issue: Issue,
)

class GraphQlException(message: String) : RuntimeException(message)
