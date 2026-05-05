package com.example.gitissuewidget.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class IssueDto(
    val number: Int,
    val title: String,
    @SerialName("html_url") val htmlUrl: String,
    val state: String,
    val labels: List<LabelDto> = emptyList(),
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("pull_request") val pullRequest: JsonElement? = null,
)
