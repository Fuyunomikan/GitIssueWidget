package com.example.gitissuewidget.data.remote

import com.example.gitissuewidget.data.remote.dto.GraphQlRequest
import com.example.gitissuewidget.data.remote.dto.GraphQlResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * GitHub GraphQL API v4 の Retrofit インターフェイス。
 * 単一エンドポイント `POST /graphql` のみ。レスポンスの `data` フィールドは
 * クエリごとに形が違うため [GraphQlResponse] の `JsonObject` を呼び出し側で手動パースする。
 */
interface GitHubGraphQlApi {

    @POST("graphql")
    suspend fun execute(@Body request: GraphQlRequest): GraphQlResponse
}
