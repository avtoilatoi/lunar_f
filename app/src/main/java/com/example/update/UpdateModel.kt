package com.example.update

import java.io.File

data class UpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val changelog: String,
    val downloadUrl: String,
    val releaseDate: String = "",
    val isForced: Boolean = false,
    val fileSizeFormatted: String = ""
)

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class UpToDate(val currentVersion: String) : UpdateState
    data class Downloading(
        val info: UpdateInfo,
        val progress: Float, // 0.0 to 1.0
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val apkFile: File) : UpdateState
    data class Error(val message: String) : UpdateState
}
