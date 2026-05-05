package com.example.gitissuewidget.data.remote

import com.example.gitissuewidget.data.remote.dto.IssueDto
import com.example.gitissuewidget.data.remote.dto.UserDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubApi {

    @GET("user")
    suspend fun getCurrentUser(): UserDto

    @GET("repos/{owner}/{repo}/issues")
    suspend fun listIssues(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("labels") labels: String? = null,
        @Query("assignee") assignee: String? = null,
        @Query("sort") sort: String = "updated",
        @Query("direction") direction: String = "desc",
        @Query("per_page") perPage: Int = 20,
    ): List<IssueDto>

    companion object {
        const val BASE_URL = "https://api.github.com/"
    }
}
