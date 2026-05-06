package com.example.gitissuewidget.data.remote

import com.example.gitissuewidget.data.remote.dto.CreateIssueRequest
import com.example.gitissuewidget.data.remote.dto.IssueDto
import com.example.gitissuewidget.data.remote.dto.LabelDto
import com.example.gitissuewidget.data.remote.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
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

    @POST("repos/{owner}/{repo}/issues")
    suspend fun createIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateIssueRequest,
    ): IssueDto

    @GET("repos/{owner}/{repo}/labels")
    suspend fun listLabels(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 100,
    ): List<LabelDto>

    companion object {
        const val BASE_URL = "https://api.github.com/"
    }
}
