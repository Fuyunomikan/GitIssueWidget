package com.example.gitissuewidget.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LabelDto(
    val name: String,
    val color: String,
)
