package com.example.grabtrash.utils

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Example usage of FileLuService
 * This class demonstrates how to use all the FileLu API features
 */
class FileLuServiceExample(private val context: Context) {
    
    private val fileLuService = FileLuService(context)
    
    /**
     * Example: Complete file management workflow
     */
    fun demonstrateCompleteWorkflow() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 1. Get account information
                val accountInfo = fileLuService.getAccountInfo()
                accountInfo?.let {
                    println("Account Email: ${it.email}")
                    println("Storage Used: ${it.storageUsed}")
                    println("Storage Left: ${it.storageLeft}")
                    println("Premium Expires: ${it.premiumExpire}")
                }
                
                // 2. Create a folder for organization
                val folderId = fileLuService.createFolder(parentId = 0, name = "EcoTrack Images")
                folderId?.let { id ->
                    println("Created folder with ID: $id")
                    
                    // 3. Upload a file to the folder
                    val imageFile = File(context.cacheDir, "example_image.jpg")
                    // Note: In real usage, you would have an actual image file
                    val fileUrl = fileLuService.uploadImageFile(imageFile)
                    fileUrl?.let { url ->
                        println("File uploaded successfully: $url")
                        
                        // Extract file code from URL
                        val fileCode = url.substringAfterLast("/")
                        
                        // 4. Get file information
                        val fileInfo = fileLuService.getFileInfo(fileCode)
                        fileInfo?.let { info ->
                            println("File Name: ${info.name}")
                            println("File Size: ${info.size} bytes")
                            println("Upload Date: ${info.uploaded}")
                            println("Download Count: ${info.downloads}")
                        }
                        
                        // 5. Move file to the created folder
                        val moved = fileLuService.moveFile(fileCode, id)
                        if (moved) {
                            println("File moved to folder successfully")
                        }
                        
                        // 6. Set file password protection
                        val passwordSet = fileLuService.setFilePassword(fileCode, "mypassword123")
                        if (passwordSet) {
                            println("File password set successfully")
                        }
                        
                        // 7. Clone the file
                        val cloneResult = fileLuService.cloneFile(fileCode)
                        cloneResult?.let { clone ->
                            println("File cloned successfully")
                            println("Clone URL: ${clone.url}")
                            println("Clone File Code: ${clone.fileCode}")
                        }
                    }
                }
                
                // 8. List all files in root folder
                val fileList = fileLuService.getFileList(folderId = 0)
                fileList?.let { files ->
                    println("Files in root folder:")
                    files.forEach { file ->
                        println("- ${file.name} (${file.size} bytes)")
                    }
                }
                
                // 9. List folders
                val folderList = fileLuService.getFolderList(folderId = 0)
                folderList?.let { result ->
                    println("Folders in root:")
                    result.folders.forEach { folder ->
                        println("- ${folder.name} (ID: ${folder.fldId})")
                    }
                }
                
            } catch (e: Exception) {
                println("Error in workflow: ${e.message}")
            }
        }
    }
    
    /**
     * Example: Upload from URL
     */
    fun demonstrateUrlUpload() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val imageUrl = "https://example.com/image.jpg"
                val fileCode = fileLuService.uploadFromUrl(imageUrl)
                fileCode?.let { code ->
                    println("File uploaded from URL: https://filelu.com/$code")
                    
                    // Check upload status
                    val status = fileLuService.getUrlUploadStatus()
                    status?.let { uploadStatus ->
                        println("Upload Progress: ${uploadStatus.percent}")
                        println("Upload Speed: ${uploadStatus.speed}")
                        println("Total Size: ${uploadStatus.totalSize}")
                    }
                }
            } catch (e: Exception) {
                println("Error uploading from URL: ${e.message}")
            }
        }
    }
    
    /**
     * Example: File security management
     */
    fun demonstrateFileSecurity() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Assuming you have a file code
                val fileCode = "example_file_code"
                
                // Set file to private (only me)
                val visibilitySet = fileLuService.setFileVisibility(fileCode, 1)
                if (visibilitySet) {
                    println("File set to private")
                }
                
                // Set password protection
                val passwordSet = fileLuService.setFilePassword(fileCode, "secure_password")
                if (passwordSet) {
                    println("File password protection enabled")
                }
                
                // Later, remove password protection
                val passwordRemoved = fileLuService.setFilePassword(fileCode, "")
                if (passwordRemoved) {
                    println("File password protection removed")
                }
                
                // Set file back to public
                val publicSet = fileLuService.setFileVisibility(fileCode, 0)
                if (publicSet) {
                    println("File set to public")
                }
                
            } catch (e: Exception) {
                println("Error managing file security: ${e.message}")
            }
        }
    }
    
    /**
     * Example: Folder management
     */
    fun demonstrateFolderManagement() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Create a folder structure
                val parentFolderId = fileLuService.createFolder(name = "EcoTrack")
                parentFolderId?.let { parentId ->
                    println("Created parent folder: $parentId")
                    
                    // Create subfolder
                    val subFolderId = fileLuService.createFolder(parentId = parentId, name = "Images")
                    subFolderId?.let { subId ->
                        println("Created subfolder: $subId")
                        
                        // Rename the subfolder
                        val renamed = fileLuService.renameFolder(subId, "Processed Images")
                        if (renamed) {
                            println("Subfolder renamed successfully")
                        }
                        
                        // Set folder settings (enable filedrop, make public)
                        val settingsSet = fileLuService.setFolderSettings(subId, filedrop = 1, fldPublic = 1)
                        if (settingsSet) {
                            println("Folder settings updated")
                        }
                        
                        // Copy the folder
                        val copyResult = fileLuService.copyFolder(subId)
                        copyResult?.let { copy ->
                            println("Folder copied successfully")
                            println("Copy ID: ${copy.fldId}")
                            println("Copy Name: ${copy.name}")
                        }
                    }
                }
                
            } catch (e: Exception) {
                println("Error managing folders: ${e.message}")
            }
        }
    }
    
    /**
     * Example: File recovery and cleanup
     */
    fun demonstrateFileRecovery() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Get list of deleted files
                val deletedFiles = fileLuService.getDeletedFiles()
                deletedFiles?.let { files ->
                    println("Deleted files:")
                    files.forEach { file ->
                        println("- ${file.name} (deleted ${file.deletedAgoSec} seconds ago)")
                    }
                    
                    // Restore the first deleted file
                    if (files.isNotEmpty()) {
                        val firstFile = files.first()
                        val restored = fileLuService.removeOrRestoreFile(firstFile.fileCode, false)
                        if (restored) {
                            println("File restored successfully: ${firstFile.name}")
                        }
                    }
                }
                
            } catch (e: Exception) {
                println("Error in file recovery: ${e.message}")
            }
        }
    }
    
    /**
     * Example: Upload image from URI (common in Android apps)
     */
    fun demonstrateImageUpload(imageUri: Uri) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val fileUrl = fileLuService.uploadImageUri(imageUri, "profile_image.jpg")
                fileUrl?.let { url ->
                    println("Image uploaded successfully: $url")
                    
                    // Extract file code and get detailed info
                    val fileCode = url.substringAfterLast("/")
                    val fileInfo = fileLuService.getFileInfo(fileCode)
                    fileInfo?.let { info ->
                        println("Uploaded Image Details:")
                        println("- Name: ${info.name}")
                        println("- Size: ${info.size} bytes")
                        println("- Upload Date: ${info.uploaded}")
                        println("- Download Count: ${info.downloads}")
                        println("- URL: ${info.url}")
                    }
                }
            } catch (e: Exception) {
                println("Error uploading image: ${e.message}")
            }
        }
    }
    
    /**
     * Example: Batch file operations
     */
    fun demonstrateBatchOperations() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Create a batch folder
                val batchFolderId = fileLuService.createFolder(name = "Batch Operations")
                batchFolderId?.let { folderId ->
                    println("Created batch folder: $folderId")
                    
                    // Simulate uploading multiple files
                    val fileCodes = mutableListOf<String>()
                    for (i in 1..5) {
                        val tempFile = File(context.cacheDir, "batch_file_$i.txt")
                        tempFile.writeText("This is batch file $i")
                        
                        val fileUrl = fileLuService.uploadImageFile(tempFile)
                        fileUrl?.let { url ->
                            val fileCode = url.substringAfterLast("/")
                            fileCodes.add(fileCode)
                            
                            // Move each file to the batch folder
                            fileLuService.moveFile(fileCode, folderId)
                        }
                        
                        // Clean up temp file
                        tempFile.delete()
                    }
                    
                    println("Uploaded ${fileCodes.size} files to batch folder")
                    
                    // List files in the batch folder
                    val batchFiles = fileLuService.getFileList(folderId = folderId)
                    batchFiles?.let { files ->
                        println("Files in batch folder:")
                        files.forEach { file ->
                            println("- ${file.name}")
                        }
                    }
                    
                    // Set all files to private
                    fileCodes.forEach { fileCode ->
                        fileLuService.setFileVisibility(fileCode, 1)
                    }
                    println("All files set to private")
                }
                
            } catch (e: Exception) {
                println("Error in batch operations: ${e.message}")
            }
        }
    }
}
