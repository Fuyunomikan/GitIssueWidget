package com.example.gitissuewidget.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateIssueRequest(
    val title: String,
    val body: String? = null,
    val labels: List<String> = emptyList(),
)
