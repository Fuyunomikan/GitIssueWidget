package com.example.gitissuewidget.domain

data class RepoRef(
    val owner: String,
    val name: String,
) {
    val fullName: String get() = "$owner/$name"

    companion object {
        fun parse(input: String): RepoRef? {
            val trimmed = input.trim().removePrefix("/").removeSuffix("/")
            val parts = trimmed.split("/")
            if (parts.size != 2) return null
            val (owner, name) = parts
            if (owner.isBlank() || name.isBlank()) return null
            return RepoRef(owner, name)
        }
    }
}
