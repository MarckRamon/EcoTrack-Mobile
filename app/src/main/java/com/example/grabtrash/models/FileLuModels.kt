package com.example.grabtrash.models

/**
 * Data models for FileLu API responses
 */

// Account Models
data class FileLuAccountInfo(
    val email: String,
    val storageUsed: String?,
    val premiumExpire: String?,
    val storageLeft: String
)

data class FileLuAccountResponse(
    val msg: String,
    val result: FileLuAccountInfo,
    val status: Int,
    val serverTime: String
)

// Upload Models
data class FileLuUploadServerResponse(
    val status: Int,
    val sessId: String,
    val result: String,
    val msg: String,
    val serverTime: String
)

data class FileLuFileUploadResponse(
    val fileCode: String,
    val fileStatus: String
)

data class FileLuUrlUploadResponse(
    val fileCode: String
)

data class FileLuUrlUploadStatusResponse(
    val status: Int,
    val serverTime: String,
    val msg: String,
    val result: FileLuUrlUploadStatus
)

data class FileLuUrlUploadStatus(
    val totalSize: String,
    val urlCount: Int,
    val percent: String,
    val speed: String
)

// File Models
data class FileLuFileInfo(
    val fileCode: String,
    val name: String,
    val status: Int,
    val size: Long,
    val uploaded: String,
    val thumbnail: String,
    val hash: String,
    val downloads: Int
)

data class FileLuFileInfoResponse(
    val status: Int,
    val serverTime: String,
    val result: List<FileLuFileInfo>,
    val msg: String
)

data class FileLuFileListResponse(
    val msg: String,
    val result: FileLuFileListResult,
    val status: Int,
    val serverTime: String
)

data class FileLuFileListResult(
    val files: List<FileLuFileItem>,
    val resultsTotal: Int,
    val results: Int
)

data class FileLuFileItem(
    val name: String,
    val fileCode: String,
    val downloads: Int,
    val thumbnail: String,
    val public: Int,
    val size: Long,
    val link: String,
    val fldId: Int,
    val hash: String,
    val uploaded: String
)

data class FileLuDirectLinkResponse(
    val status: Int,
    val serverTime: String,
    val result: FileLuDirectLink,
    val msg: String
)

data class FileLuDirectLink(
    val url: String,
    val size: Long
)

data class FileLuFileActionResponse(
    val status: Int,
    val result: String?,
    val msg: String,
    val serverTime: String
)

data class FileLuFileCloneResponse(
    val status: Int,
    val result: FileLuFileCloneResult,
    val msg: String,
    val serverTime: String
)

data class FileLuFileCloneResult(
    val url: String,
    val fileCode: String
)

data class FileLuDeletedFile(
    val deletedAgoSec: Int,
    val deleted: String,
    val fileCode: String,
    val name: String
)

data class FileLuDeletedFilesResponse(
    val status: Int,
    val msg: String,
    val result: List<FileLuDeletedFile>,
    val serverTime: String
)

// Folder Models
data class FileLuFolderListResponse(
    val status: Int,
    val msg: String,
    val result: FileLuFolderListResult,
    val serverTime: String
)

data class FileLuFolderListResult(
    val files: List<FileLuFileItem>,
    val folders: List<FileLuFolderItem>
)

data class FileLuFolderItem(
    val fldId: Int,
    val code: String?,
    val name: String
)

data class FileLuFolderCreateResponse(
    val status: Int,
    val msg: String,
    val result: FileLuFolderCreateResult,
    val serverTime: String
)

data class FileLuFolderCreateResult(
    val fldId: Int
)

data class FileLuFolderActionResponse(
    val status: Int,
    val msg: String,
    val result: String?,
    val serverTime: String
)

data class FileLuFolderCopyResponse(
    val status: Int,
    val msg: String,
    val result: FileLuFolderCopyResult,
    val serverTime: String
)

data class FileLuFolderCopyResult(
    val fldId: String,
    val name: String
)

// Password Models
data class FileLuPasswordResponse(
    val status: Int,
    val msg: String,
    val serverTime: String
)

// Error Models
data class FileLuError(
    val status: Int,
    val msg: String,
    val serverTime: String
)

// Request Models
data class FileLuUrlUploadRequest(
    val url: String,
    val fldId: Int = 0
)

data class FileLuFileRenameRequest(
    val fileCode: String,
    val name: String
)

data class FileLuFileMoveRequest(
    val fileCode: String,
    val fldId: Int
)

data class FileLuFilePasswordRequest(
    val fileCode: String,
    val filePassword: String
)

data class FileLuFileVisibilityRequest(
    val fileCode: String,
    val onlyMe: Int // 0 = Sharing, 1 = Only_Me
)

data class FileLuFileActionRequest(
    val fileCode: String,
    val action: String // "remove" or "restore"
)

data class FileLuFolderCreateRequest(
    val parentId: Int,
    val name: String
)

data class FileLuFolderMoveRequest(
    val fldId: Int,
    val destFldId: Int
)

data class FileLuFolderRenameRequest(
    val fldId: Int,
    val name: String
)

data class FileLuFolderPasswordRequest(
    val fldToken: String,
    val fldPassword: String
)

data class FileLuFolderSettingRequest(
    val fldId: Int,
    val filedrop: Int, // 0 = OFF, 1 = ON
    val fldPublic: Int // 0 = OFF, 1 = ON
)
