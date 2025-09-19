package com.example.ecotrack.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.ecotrack.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder

class FileLuService(private val context: Context) {
    
    companion object {
        private const val TAG = "FileLuService"
        private const val API_KEY = "42392ix8ebpn54bgalgek"
        private const val BASE_URL = "https://filelu.com/api"
        private const val UPLOAD_URL = "https://filelu.com/api/upload/server"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    /**
     * Uploads an image file to FileLu and returns the file URL
     * @param imageFile The image file to upload
     * @return The FileLu URL if successful, null otherwise
     */
    suspend fun uploadImageFile(imageFile: File): String? = withContext(Dispatchers.IO) {
        try {
            // Step 1: Get upload server URL and session ID
            val uploadServerInfo = getUploadServerInfo() ?: return@withContext null
            
            // Step 2: Upload file to the server
            val fileCode = uploadFileToServer(imageFile, uploadServerInfo.first, uploadServerInfo.second)
            fileCode?.let { 
                // Get the proper image display URL
                getImageDisplayUrl(it, imageFile.name)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading image file", e)
            null
        }
    }
    
    /**
     * Uploads an image from URI to FileLu and returns the file URL
     * @param imageUri The image URI to upload
     * @param fileName Optional custom filename
     * @return The FileLu URL if successful, null otherwise
     */
    suspend fun uploadImageUri(imageUri: Uri, fileName: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(imageUri) ?: "image/jpeg"
            val extension = when (mimeType.substringAfter('/').lowercase()) {
                "jpeg", "jpg" -> ".jpg"
                "png" -> ".png"
                "webp" -> ".webp"
                "gif" -> ".gif"
                else -> ".jpg"
            }
            
            val finalFileName = fileName ?: "image_${System.currentTimeMillis()}${extension}"
            
            val inputStream = contentResolver.openInputStream(imageUri) ?: return@withContext null
            val bytes = inputStream.readBytes()
            inputStream.close()
            
            // Create temporary file
            val tempFile = File(context.cacheDir, finalFileName)
            tempFile.writeBytes(bytes)
            
            try {
                // Step 1: Get upload server URL and session ID
                val uploadServerInfo = getUploadServerInfo() ?: return@withContext null
                
                // Step 2: Upload file to the server
                val fileCode = uploadFileToServer(tempFile, uploadServerInfo.first, uploadServerInfo.second)
                fileCode?.let { 
                    // Get the proper image display URL
                    getImageDisplayUrl(it, finalFileName)
                }
            } finally {
                // Clean up temp file
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading image URI", e)
            null
        }
    }
    
    /**
     * Gets the upload server URL and session ID from FileLu API
     * @return Pair of (uploadServerUrl, sessionId) or null if failed
     */
    private suspend fun getUploadServerInfo(): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val url = UPLOAD_URL
            Log.d(TAG, "Requesting upload server URL: $url")
            
            val requestBody = "key=$API_KEY".toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Response code: ${response.code}")
                Log.d(TAG, "Response message: ${response.message}")
                
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to get upload server URL: ${response.code} ${response.message}")
                    return@withContext null
                }
                
                val responseBody = response.body?.string()
                Log.d(TAG, "Response body: $responseBody")
                
                if (responseBody.isNullOrBlank()) {
                    Log.e(TAG, "Empty response from upload server URL")
                    return@withContext null
                }
                
                try {
                    val json = JSONObject(responseBody)
                    Log.d(TAG, "Parsed JSON: $json")
                    
                    if (json.getInt("status") == 200) {
                        val result = json.getString("result")
                        val sessId = json.getString("sess_id")
                        Log.d(TAG, "Upload server URL: $result")
                        Log.d(TAG, "Session ID: $sessId")
                        Pair(result, sessId)
                    } else {
                        Log.e(TAG, "API error: ${json.getString("msg")}")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing upload server response: $responseBody", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting upload server URL", e)
            null
        }
    }
    
    /**
     * Uploads a file to the FileLu server
     */
    private suspend fun uploadFileToServer(file: File, uploadServerUrl: String, sessionId: String): String? = withContext(Dispatchers.IO) {
        try {
            val mediaType = "image/jpeg".toMediaTypeOrNull()
            val fileBody = file.asRequestBody(mediaType)
            
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("sess_id", sessionId) // Use the session ID from the first request
                .addFormDataPart("utype", "prem") // Use premium upload type
                .addFormDataPart("fileToUpload", file.name, fileBody)
                .build()
            
            val request = Request.Builder()
                .url(uploadServerUrl)
                .post(requestBody)
                .build()
            
            Log.d(TAG, "Uploading file: ${file.name} to: $uploadServerUrl with session: $sessionId")
            
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Upload response code: ${response.code}")
                Log.d(TAG, "Upload response message: ${response.message}")
                
                if (!response.isSuccessful) {
                    Log.e(TAG, "File upload failed: ${response.code} ${response.message}")
                    return@withContext null
                }
                
                val responseBody = response.body?.string()
                Log.d(TAG, "Upload response body: $responseBody")
                
                if (responseBody.isNullOrBlank()) {
                    Log.e(TAG, "Empty response from file upload")
                    return@withContext null
                }
                
                try {
                    // FileLu returns JSON array with file_code
                    val jsonArray = org.json.JSONArray(responseBody)
                    if (jsonArray.length() > 0) {
                        val fileObject = jsonArray.getJSONObject(0)
                        val fileStatus = fileObject.optString("file_status", "UNKNOWN")
                        Log.d(TAG, "File upload status: $fileStatus")
                        
                        if (fileStatus == "OK") {
                            val fileCode = fileObject.getString("file_code")
                            Log.d(TAG, "File uploaded successfully with code: $fileCode")
                            fileCode
                        } else {
                            Log.e(TAG, "File upload status not OK: $fileStatus")
                            null
                        }
                    } else {
                        Log.e(TAG, "Empty JSON array in upload response")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing file upload response: $responseBody", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading file to server", e)
            null
        }
    }
    
    /**
     * Gets file information from FileLu
     * @param fileCode The file code from FileLu
     * @return File information or null if failed
     */
    suspend fun getFileInfo(fileCode: String): FileInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/file/info?file_code=$fileCode&key=$API_KEY")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to get file info: ${response.code}")
                    return@withContext null
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext null
                }
                
                try {
                    val json = JSONObject(responseBody)
                    if (json.getInt("status") == 200) {
                        val resultArray = json.getJSONArray("result")
                        if (resultArray.length() > 0) {
                            val fileData = resultArray.getJSONObject(0)
                            FileInfo(
                                fileCode = fileData.getString("filecode"),
                                name = fileData.getString("name"),
                                size = fileData.getLong("size"),
                                uploaded = fileData.getString("uploaded"),
                                downloads = fileData.getInt("downloads"),
                                url = "https://filelu.com/${fileData.getString("filecode")}"
                            )
                        } else null
                    } else null
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing file info response", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file info", e)
            null
        }
    }
    
    /**
     * Gets direct download link for a file
     * @param fileCode The file code from FileLu
     * @return Direct download URL or null if failed
     */
    suspend fun getDirectDownloadLink(fileCode: String): String? = withContext(Dispatchers.IO) {
        try {
            val requestBody = "file_code=$fileCode&key=$API_KEY".toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())
            
            val request = Request.Builder()
                .url("$BASE_URL/file/direct_link")
                .post(requestBody)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to get direct download link: ${response.code}")
                    return@withContext null
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext null
                }
                
                try {
                    val json = JSONObject(responseBody)
                    if (json.getInt("status") == 200) {
                        val result = json.getJSONObject("result")
                        result.getString("url")
                    } else null
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing direct link response", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting direct download link", e)
            null
        }
    }
    
    // ==================== ACCOUNT MANAGEMENT ====================
    
    /**
     * Gets account information from FileLu
     * @return Account information or null if failed
     */
    suspend fun getAccountInfo(): FileLuAccountInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/account/info?key=$API_KEY")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to get account info: ${response.code}")
                    return@withContext null
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext null
                }
                
                try {
                    val json = JSONObject(responseBody)
                    if (json.getInt("status") == 200) {
                        val result = json.getJSONObject("result")
                        FileLuAccountInfo(
                            email = result.getString("email"),
                            storageUsed = result.optString("storage_used", null),
                            premiumExpire = result.optString("premium_expire", null),
                            storageLeft = result.getString("storage_left")
                        )
                    } else {
                        Log.e(TAG, "API error: ${json.getString("msg")}")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing account info response", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting account info", e)
            null
        }
    }
    
    // ==================== ENHANCED UPLOAD FUNCTIONALITY ====================
    
    /**
     * Uploads a file from a remote URL to FileLu
     * @param url The remote URL to upload from
     * @param folderId Optional folder ID to upload to (default: 0 for root)
     * @return File code if successful, null otherwise
     */
    suspend fun uploadFromUrl(url: String, folderId: Int = 0): String? = withContext(Dispatchers.IO) {
        try {
            val encodedUrl = URLEncoder.encode(url, "UTF-8")
            val request = Request.Builder()
                .url("$BASE_URL/upload/url?key=$API_KEY&url=$encodedUrl&fld_id=$folderId")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to upload from URL: ${response.code}")
                    return@withContext null
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext null
                }
                
                try {
                    val jsonArray = JSONArray(responseBody)
                    if (jsonArray.length() > 0) {
                        val fileObject = jsonArray.getJSONObject(0)
                        fileObject.getString("file_code")
                    } else {
                        Log.e(TAG, "Empty JSON array in URL upload response")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing URL upload response", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading from URL", e)
            null
        }
    }
    
    /**
     * Checks the status of remote URL uploads
     * @return Upload status information or null if failed
     */
    suspend fun getUrlUploadStatus(): FileLuUrlUploadStatus? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/file/status?key=$API_KEY")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to get URL upload status: ${response.code}")
                    return@withContext null
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext null
                }
                
                try {
                    val json = JSONObject(responseBody)
                    if (json.getInt("status") == 200) {
                        val result = json.getJSONObject("result")
                        FileLuUrlUploadStatus(
                            totalSize = result.getString("total_size"),
                            urlCount = result.getInt("url_count"),
                            percent = result.getString("percent"),
                            speed = result.getString("speed")
                        )
                    } else {
                        Log.e(TAG, "API error: ${json.getString("msg")}")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing URL upload status response", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting URL upload status", e)
            null
        }
    }
    
    // ==================== FILE MANAGEMENT ====================
    
    /**
     * Gets a list of files from FileLu
     * @param page Page number (default: 1)
     * @param perPage Items per page (default: 25)
     * @param folderId Folder ID to list files from (default: 0 for root)
     * @return List of files or null if failed
     */
    suspend fun getFileList(page: Int = 1, perPage: Int = 25, folderId: Int = 0): List<FileLuFileItem>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/file/list?page=$page&per_page=$perPage&fld_id=$folderId&key=$API_KEY")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to get file list: ${response.code}")
                    return@withContext null
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext null
                }
                
                try {
                    val json = JSONObject(responseBody)
                    if (json.getInt("status") == 200) {
                        val result = json.getJSONObject("result")
                        val filesArray = result.getJSONArray("files")
                        val files = mutableListOf<FileLuFileItem>()
                        
                        for (i in 0 until filesArray.length()) {
                            val fileObj = filesArray.getJSONObject(i)
                            files.add(FileLuFileItem(
                                name = fileObj.getString("name"),
                                fileCode = fileObj.getString("file_code"),
                                downloads = fileObj.getInt("downloads"),
                                thumbnail = fileObj.getString("thumbnail"),
                                public = fileObj.getInt("public"),
                                size = fileObj.getLong("size"),
                                link = fileObj.getString("link"),
                                fldId = fileObj.getInt("fld_id"),
                                hash = fileObj.getString("hash"),
                                uploaded = fileObj.getString("uploaded")
                            ))
                        }
                        files
                    } else {
                        Log.e(TAG, "API error: ${json.getString("msg")}")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing file list response", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file list", e)
            null
        }
    }
    
    /**
     * Renames a file
     * @param fileCode The file code to rename
     * @param newName The new name for the file
     * @return True if successful, false otherwise
     */
    suspend fun renameFile(fileCode: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/file/rename?file_code=$fileCode&name=$newName&key=$API_KEY")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to rename file: ${response.code}")
                    return@withContext false
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext false
                }
                
                try {
                    val json = JSONObject(responseBody)
                    json.getInt("status") == 200 && json.getString("result") == "true"
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing rename file response", e)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error renaming file", e)
            false
        }
    }
    
    /**
     * Clones a file
     * @param fileCode The file code to clone
     * @return Clone result with new file code and URL, or null if failed
     */
    suspend fun cloneFile(fileCode: String): FileLuFileCloneResult? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/file/clone?file_code=$fileCode&key=$API_KEY")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to clone file: ${response.code}")
                    return@withContext null
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext null
                }
                
                try {
                    val json = JSONObject(responseBody)
                    if (json.getInt("status") == 200) {
                        val result = json.getJSONObject("result")
                        FileLuFileCloneResult(
                            url = result.getString("url"),
                            fileCode = result.getString("filecode")
                        )
                    } else {
                        Log.e(TAG, "API error: ${json.getString("msg")}")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing clone file response", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cloning file", e)
            null
        }
    }
    
    /**
     * Moves a file to a different folder
     * @param fileCode The file code to move
     * @param folderId The destination folder ID
     * @return True if successful, false otherwise
     */
    suspend fun moveFile(fileCode: String, folderId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/file/set_folder?file_code=$fileCode&fld_id=$folderId&key=$API_KEY")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to move file: ${response.code}")
                    return@withContext false
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext false
                }
                
                try {
                    val json = JSONObject(responseBody)
                    json.getInt("status") == 200
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing move file response", e)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error moving file", e)
            false
        }
    }
    
    /**
     * Sets file visibility (sharing or only me)
     * @param fileCode The file code
     * @param onlyMe 0 for sharing, 1 for only me
     * @return True if successful, false otherwise
     */
    suspend fun setFileVisibility(fileCode: String, onlyMe: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/file/only_me?file_code=$fileCode&only_me=$onlyMe&key=$API_KEY")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to set file visibility: ${response.code}")
                    return@withContext false
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext false
                }
                
                try {
                    val json = JSONObject(responseBody)
                    json.getInt("status") == 200
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing set file visibility response", e)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting file visibility", e)
            false
        }
    }
    
    /**
     * Sets or unsets file password
     * @param fileCode The file code
     * @param password The password (empty string to unset)
     * @return True if successful, false otherwise
     */
    suspend fun setFilePassword(fileCode: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/file/set_password?file_code=$fileCode&key=$API_KEY&file_password=$password")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to set file password: ${response.code}")
                    return@withContext false
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext false
                }
                
                try {
                    val json = JSONObject(responseBody)
                    json.getInt("status") == 200
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing set file password response", e)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting file password", e)
            false
        }
    }
    
    /**
     * Removes or restores a file
     * @param fileCode The file code
     * @param remove True to remove, false to restore
     * @return True if successful, false otherwise
     */
    suspend fun removeOrRestoreFile(fileCode: String, remove: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val action = if (remove) "remove" else "restore"
            val value = if (remove) 1 else 1
            val request = Request.Builder()
                .url("$BASE_URL/file/$action?file_code=$fileCode&$action=$value&key=$API_KEY")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to $action file: ${response.code}")
                    return@withContext false
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext false
                }
                
                try {
                    val json = JSONObject(responseBody)
                    json.getInt("status") == 200
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing $action file response", e)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ${if (remove) "removing" else "restoring"} file", e)
            false
        }
    }
    
    /**
     * Gets list of deleted files
     * @return List of deleted files or null if failed
     */
    suspend fun getDeletedFiles(): List<FileLuDeletedFile>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/files/deleted?key=$API_KEY")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to get deleted files: ${response.code}")
                    return@withContext null
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext null
                }
                
                try {
                    val json = JSONObject(responseBody)
                    if (json.getInt("status") == 200) {
                        val resultArray = json.getJSONArray("result")
                        val deletedFiles = mutableListOf<FileLuDeletedFile>()
                        
                        for (i in 0 until resultArray.length()) {
                            val fileObj = resultArray.getJSONObject(i)
                            deletedFiles.add(FileLuDeletedFile(
                                deletedAgoSec = fileObj.getInt("deleted_ago_sec"),
                                deleted = fileObj.getString("deleted"),
                                fileCode = fileObj.getString("file_code"),
                                name = fileObj.getString("name")
                            ))
                        }
                        deletedFiles
                    } else {
                        Log.e(TAG, "API error: ${json.getString("msg")}")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing deleted files response", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting deleted files", e)
            null
        }
    }
    
    // ==================== FOLDER MANAGEMENT ====================
    
    /**
     * Gets folder list
     * @param page Page number (default: 1)
     * @param perPage Items per page (default: 25)
     * @param folderId Parent folder ID (default: 0 for root)
     * @return Folder list result or null if failed
     */
    suspend fun getFolderList(page: Int = 1, perPage: Int = 25, folderId: Int = 0): FileLuFolderListResult? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/folder/list?page=$page&per_page=$perPage&fld_id=$folderId&key=$API_KEY")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to get folder list: ${response.code}")
                    return@withContext null
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext null
                }
                
                try {
                    val json = JSONObject(responseBody)
                    if (json.getInt("status") == 200) {
                        val result = json.getJSONObject("result")
                        val filesArray = result.getJSONArray("files")
                        val foldersArray = result.getJSONArray("folders")
                        
                        val files = mutableListOf<FileLuFileItem>()
                        for (i in 0 until filesArray.length()) {
                            val fileObj = filesArray.getJSONObject(i)
                            files.add(FileLuFileItem(
                                name = fileObj.getString("name"),
                                fileCode = fileObj.getString("file_code"),
                                downloads = fileObj.getInt("downloads"),
                                thumbnail = fileObj.getString("thumbnail"),
                                public = fileObj.getInt("public"),
                                size = fileObj.getLong("size"),
                                link = fileObj.getString("link"),
                                fldId = fileObj.getInt("fld_id"),
                                hash = fileObj.getString("hash"),
                                uploaded = fileObj.getString("uploaded")
                            ))
                        }
                        
                        val folders = mutableListOf<FileLuFolderItem>()
                        for (i in 0 until foldersArray.length()) {
                            val folderObj = foldersArray.getJSONObject(i)
                            folders.add(FileLuFolderItem(
                                fldId = folderObj.getInt("fld_id"),
                                code = folderObj.optString("code", null),
                                name = folderObj.getString("name")
                            ))
                        }
                        
                        FileLuFolderListResult(files = files, folders = folders)
                    } else {
                        Log.e(TAG, "API error: ${json.getString("msg")}")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing folder list response", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting folder list", e)
            null
        }
    }
    
    /**
     * Creates a new folder
     * @param parentId Parent folder ID (default: 0 for root)
     * @param name Folder name
     * @return Created folder ID or null if failed
     */
    suspend fun createFolder(parentId: Int = 0, name: String): Int? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/folder/create?parent_id=$parentId&name=$name&key=$API_KEY")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to create folder: ${response.code}")
                    return@withContext null
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext null
                }
                
                try {
                    val json = JSONObject(responseBody)
                    if (json.getInt("status") == 200) {
                        val result = json.getJSONObject("result")
                        result.getInt("fld_id")
                    } else {
                        Log.e(TAG, "API error: ${json.getString("msg")}")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing create folder response", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating folder", e)
            null
        }
    }
    
    /**
     * Moves a folder to a different parent
     * @param folderId The folder ID to move
     * @param destFolderId The destination parent folder ID
     * @return True if successful, false otherwise
     */
    suspend fun moveFolder(folderId: Int, destFolderId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/folder/move?fld_id=$folderId&dest_fld_id=$destFolderId&key=$API_KEY")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to move folder: ${response.code}")
                    return@withContext false
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext false
                }
                
                try {
                    val json = JSONObject(responseBody)
                    json.getInt("status") == 200
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing move folder response", e)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error moving folder", e)
            false
        }
    }
    
    /**
     * Copies a folder
     * @param folderId The folder ID to copy
     * @return Copy result with new folder ID and name, or null if failed
     */
    suspend fun copyFolder(folderId: Int): FileLuFolderCopyResult? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/folder/copy?fld_id=$folderId&key=$API_KEY")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to copy folder: ${response.code}")
                    return@withContext null
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext null
                }
                
                try {
                    val json = JSONObject(responseBody)
                    if (json.getInt("status") == 200) {
                        val result = json.getJSONObject("result")
                        FileLuFolderCopyResult(
                            fldId = result.getString("fld_id"),
                            name = result.getString("name")
                        )
                    } else {
                        Log.e(TAG, "API error: ${json.getString("msg")}")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing copy folder response", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying folder", e)
            null
        }
    }
    
    /**
     * Deletes a folder
     * @param folderId The folder ID to delete
     * @return True if successful, false otherwise
     */
    suspend fun deleteFolder(folderId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/folder/delete?fld_id=$folderId&key=$API_KEY")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to delete folder: ${response.code}")
                    return@withContext false
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext false
                }
                
                try {
                    val json = JSONObject(responseBody)
                    json.getInt("status") == 200
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing delete folder response", e)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting folder", e)
            false
        }
    }
    
    /**
     * Restores a deleted folder
     * @param folderId The folder ID to restore
     * @return True if successful, false otherwise
     */
    suspend fun restoreFolder(folderId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/folder/restore?fld_id=$folderId&key=$API_KEY")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to restore folder: ${response.code}")
                    return@withContext false
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext false
                }
                
                try {
                    val json = JSONObject(responseBody)
                    json.getInt("status") == 200
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing restore folder response", e)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring folder", e)
            false
        }
    }
    
    /**
     * Renames a folder
     * @param folderId The folder ID to rename
     * @param newName The new name for the folder
     * @return True if successful, false otherwise
     */
    suspend fun renameFolder(folderId: Int, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/folder/rename?fld_id=$folderId&name=$newName&key=$API_KEY")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to rename folder: ${response.code}")
                    return@withContext false
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext false
                }
                
                try {
                    val json = JSONObject(responseBody)
                    json.getInt("status") == 200
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing rename folder response", e)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error renaming folder", e)
            false
        }
    }
    
    /**
     * Sets or unsets folder password
     * @param folderToken The folder token
     * @param password The password (empty string to unset)
     * @return True if successful, false otherwise
     */
    suspend fun setFolderPassword(folderToken: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/folder/set_password?fld_token=$folderToken&key=$API_KEY&fld_password=$password")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to set folder password: ${response.code}")
                    return@withContext false
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext false
                }
                
                try {
                    val json = JSONObject(responseBody)
                    json.getInt("status") == 200
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing set folder password response", e)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting folder password", e)
            false
        }
    }
    
    /**
     * Sets folder settings (filedrop and public access)
     * @param folderId The folder ID
     * @param filedrop 0 for OFF, 1 for ON
     * @param fldPublic 0 for OFF, 1 for ON
     * @return True if successful, false otherwise
     */
    suspend fun setFolderSettings(folderId: Int, filedrop: Int, fldPublic: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/folder/setting?fld_id=$folderId&filedrop=$filedrop&fld_public=$fldPublic&key=$API_KEY")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to set folder settings: ${response.code}")
                    return@withContext false
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext false
                }
                
                try {
                    val json = JSONObject(responseBody)
                    json.getInt("status") == 200
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing set folder settings response", e)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting folder settings", e)
            false
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Gets the proper image URL format for display
     * @param fileCode The file code from FileLu
     * @param fileName The original filename
     * @return Properly formatted image URL
     */
    suspend fun getImageDisplayUrl(fileCode: String, fileName: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            // First try to get the direct download link
            val directLink = getDirectDownloadLink(fileCode)
            if (directLink != null) {
                Log.d(TAG, "Using direct download link: $directLink")
                return@withContext directLink
            }
            
            // Fallback to the standard FileLu URL format
            val fallbackUrl = if (fileName != null) {
                "https://filelu.com/$fileCode/$fileName"
            } else {
                "https://filelu.com/$fileCode"
            }
            
            Log.d(TAG, "Using fallback URL: $fallbackUrl")
            fallbackUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error getting image display URL", e)
            null
        }
    }
    
    /**
     * Converts an existing FileLu URL to a proper display URL
     * @param fileLuUrl The existing FileLu URL (e.g., https://filelu.com/nb88ecd129ag)
     * @return Properly formatted image URL for display
     */
    suspend fun convertToDisplayUrl(fileLuUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Converting FileLu URL to display URL: $fileLuUrl")
            
            // Extract file code from URL
            val fileCode = fileLuUrl.substringAfterLast("/")
            if (fileCode.isEmpty() || fileCode == fileLuUrl) {
                Log.e(TAG, "Could not extract file code from URL: $fileLuUrl")
                return@withContext null
            }
            
            Log.d(TAG, "Extracted file code: $fileCode")
            
            // Try to get the direct download link
            val directLink = getDirectDownloadLink(fileCode)
            if (directLink != null) {
                Log.d(TAG, "Converted to direct download link: $directLink")
                return@withContext directLink
            }
            
            // Fallback to the standard FileLu URL format
            val fallbackUrl = "https://filelu.com/$fileCode"
            Log.d(TAG, "Using fallback URL: $fallbackUrl")
            fallbackUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error converting FileLu URL to display URL", e)
            null
        }
    }
    
    // ==================== LEGACY SUPPORT ====================
    
    data class FileInfo(
        val fileCode: String,
        val name: String,
        val size: Long,
        val uploaded: String,
        val downloads: Int,
        val url: String
    )
}
