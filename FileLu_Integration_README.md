# FileLu API Integration for EcoTrack Mobile

This document provides comprehensive documentation for the FileLu API integration in the EcoTrack Mobile application.

## Overview

The FileLu integration provides cloud storage capabilities for the EcoTrack Mobile app, allowing users to upload, manage, and share files through the FileLu service. The integration includes full support for all FileLu API endpoints as documented in their [official API documentation](https://filelu.com/pages/api/).

## Features

### ✅ Account Management
- Get account information (email, storage usage, premium status)
- Monitor storage usage and limits

### ✅ File Upload
- Upload files from local storage
- Upload images from Android URI
- Upload files from remote URLs
- Progress tracking for URL uploads

### ✅ File Management
- List files with pagination
- Get detailed file information
- Rename files
- Clone files
- Move files between folders
- Set file visibility (public/private)
- Password protection for files
- Remove and restore files
- Get list of deleted files

### ✅ Folder Management
- Create folders and subfolders
- List folders with pagination
- Rename folders
- Move folders
- Copy folders
- Delete and restore folders
- Set folder passwords
- Configure folder settings (filedrop, public access)

### ✅ Security Features
- File password protection
- Folder password protection
- File visibility controls
- Secure API key management

## File Structure

```
app/src/main/java/com/example/ecotrack/
├── models/
│   └── FileLuModels.kt          # Data models for FileLu API responses
├── utils/
│   ├── FileLuService.kt         # Main service class with all API methods
│   └── FileLuServiceExample.kt  # Usage examples and demonstrations
└── test/java/com/example/ecotrack/utils/
    └── FileLuServiceTest.kt     # Unit tests for FileLu service
```

## Quick Start

### 1. Initialize the Service

```kotlin
val fileLuService = FileLuService(context)
```

### 2. Upload a File

```kotlin
// Upload from file
val file = File("path/to/your/file.jpg")
val fileUrl = fileLuService.uploadImageFile(file)

// Upload from URI (common in Android)
val imageUri: Uri = // Get from image picker
val fileUrl = fileLuService.uploadImageUri(imageUri, "my_image.jpg")

// Upload from URL
val fileCode = fileLuService.uploadFromUrl("https://example.com/image.jpg")
```

### 3. Manage Files

```kotlin
// Get file list
val files = fileLuService.getFileList()

// Get file information
val fileInfo = fileLuService.getFileInfo("file_code")

// Rename a file
val renamed = fileLuService.renameFile("file_code", "new_name.jpg")

// Move file to folder
val moved = fileLuService.moveFile("file_code", folderId)

// Set file password
val passwordSet = fileLuService.setFilePassword("file_code", "mypassword")
```

### 4. Manage Folders

```kotlin
// Create folder
val folderId = fileLuService.createFolder(name = "My Folder")

// Create subfolder
val subFolderId = fileLuService.createFolder(parentId = folderId, name = "Subfolder")

// List folders
val folderList = fileLuService.getFolderList()

// Rename folder
val renamed = fileLuService.renameFolder(folderId, "New Folder Name")
```

## API Reference

### Account Management

#### `getAccountInfo(): FileLuAccountInfo?`
Gets account information including email, storage usage, and premium status.

**Returns:** `FileLuAccountInfo` object or `null` if failed

**Example:**
```kotlin
val accountInfo = fileLuService.getAccountInfo()
accountInfo?.let {
    println("Email: ${it.email}")
    println("Storage Used: ${it.storageUsed}")
    println("Storage Left: ${it.storageLeft}")
}
```

### File Upload

#### `uploadImageFile(file: File): String?`
Uploads a file from local storage.

**Parameters:**
- `file`: The file to upload

**Returns:** FileLu URL or `null` if failed

#### `uploadImageUri(uri: Uri, fileName: String?): String?`
Uploads an image from Android URI.

**Parameters:**
- `uri`: The image URI
- `fileName`: Optional custom filename

**Returns:** FileLu URL or `null` if failed

#### `uploadFromUrl(url: String, folderId: Int = 0): String?`
Uploads a file from a remote URL.

**Parameters:**
- `url`: The remote URL to upload from
- `folderId`: Optional folder ID (default: 0 for root)

**Returns:** File code or `null` if failed

### File Management

#### `getFileList(page: Int = 1, perPage: Int = 25, folderId: Int = 0): List<FileLuFileItem>?`
Gets a paginated list of files.

**Parameters:**
- `page`: Page number (default: 1)
- `perPage`: Items per page (default: 25)
- `folderId`: Folder ID to list files from (default: 0 for root)

**Returns:** List of `FileLuFileItem` objects or `null` if failed

#### `getFileInfo(fileCode: String): FileInfo?`
Gets detailed information about a file.

**Parameters:**
- `fileCode`: The file code from FileLu

**Returns:** `FileInfo` object or `null` if failed

#### `renameFile(fileCode: String, newName: String): Boolean`
Renames a file.

**Parameters:**
- `fileCode`: The file code to rename
- `newName`: The new name for the file

**Returns:** `true` if successful, `false` otherwise

#### `cloneFile(fileCode: String): FileLuFileCloneResult?`
Creates a copy of a file.

**Parameters:**
- `fileCode`: The file code to clone

**Returns:** `FileLuFileCloneResult` with new file details or `null` if failed

#### `moveFile(fileCode: String, folderId: Int): Boolean`
Moves a file to a different folder.

**Parameters:**
- `fileCode`: The file code to move
- `folderId`: The destination folder ID

**Returns:** `true` if successful, `false` otherwise

#### `setFileVisibility(fileCode: String, onlyMe: Int): Boolean`
Sets file visibility (public or private).

**Parameters:**
- `fileCode`: The file code
- `onlyMe`: 0 for public sharing, 1 for private

**Returns:** `true` if successful, `false` otherwise

#### `setFilePassword(fileCode: String, password: String): Boolean`
Sets or removes file password protection.

**Parameters:**
- `fileCode`: The file code
- `password`: The password (empty string to remove)

**Returns:** `true` if successful, `false` otherwise

#### `removeOrRestoreFile(fileCode: String, remove: Boolean): Boolean`
Removes or restores a file.

**Parameters:**
- `fileCode`: The file code
- `remove`: `true` to remove, `false` to restore

**Returns:** `true` if successful, `false` otherwise

#### `getDeletedFiles(): List<FileLuDeletedFile>?`
Gets list of deleted files.

**Returns:** List of `FileLuDeletedFile` objects or `null` if failed

### Folder Management

#### `getFolderList(page: Int = 1, perPage: Int = 25, folderId: Int = 0): FileLuFolderListResult?`
Gets a paginated list of folders and files.

**Parameters:**
- `page`: Page number (default: 1)
- `perPage`: Items per page (default: 25)
- `folderId`: Parent folder ID (default: 0 for root)

**Returns:** `FileLuFolderListResult` object or `null` if failed

#### `createFolder(parentId: Int = 0, name: String): Int?`
Creates a new folder.

**Parameters:**
- `parentId`: Parent folder ID (default: 0 for root)
- `name`: Folder name

**Returns:** Created folder ID or `null` if failed

#### `renameFolder(folderId: Int, newName: String): Boolean`
Renames a folder.

**Parameters:**
- `folderId`: The folder ID to rename
- `newName`: The new name for the folder

**Returns:** `true` if successful, `false` otherwise

#### `moveFolder(folderId: Int, destFolderId: Int): Boolean`
Moves a folder to a different parent.

**Parameters:**
- `folderId`: The folder ID to move
- `destFolderId`: The destination parent folder ID

**Returns:** `true` if successful, `false` otherwise

#### `copyFolder(folderId: Int): FileLuFolderCopyResult?`
Creates a copy of a folder.

**Parameters:**
- `folderId`: The folder ID to copy

**Returns:** `FileLuFolderCopyResult` with new folder details or `null` if failed

#### `deleteFolder(folderId: Int): Boolean`
Deletes a folder.

**Parameters:**
- `folderId`: The folder ID to delete

**Returns:** `true` if successful, `false` otherwise

#### `restoreFolder(folderId: Int): Boolean`
Restores a deleted folder.

**Parameters:**
- `folderId`: The folder ID to restore

**Returns:** `true` if successful, `false` otherwise

#### `setFolderPassword(folderToken: String, password: String): Boolean`
Sets or removes folder password protection.

**Parameters:**
- `folderToken`: The folder token
- `password`: The password (empty string to remove)

**Returns:** `true` if successful, `false` otherwise

#### `setFolderSettings(folderId: Int, filedrop: Int, fldPublic: Int): Boolean`
Sets folder settings.

**Parameters:**
- `folderId`: The folder ID
- `filedrop`: 0 for OFF, 1 for ON
- `fldPublic`: 0 for OFF, 1 for ON

**Returns:** `true` if successful, `false` otherwise

## Data Models

### FileLuAccountInfo
```kotlin
data class FileLuAccountInfo(
    val email: String,
    val storageUsed: String?,
    val premiumExpire: String?,
    val storageLeft: String
)
```

### FileLuFileItem
```kotlin
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
```

### FileLuFolderItem
```kotlin
data class FileLuFolderItem(
    val fldId: Int,
    val code: String?,
    val name: String
)
```

## Error Handling

All methods return `null` or `false` on failure and log detailed error information. Check the Android logs for specific error messages.

## Configuration

The FileLu API key is currently hardcoded in the service. In a production environment, consider:

1. Moving the API key to a secure configuration file
2. Using environment variables
3. Implementing proper key management

## Testing

Run the unit tests with:
```bash
./gradlew test
```

The test suite includes basic functionality tests for all major operations.

## Usage Examples

See `FileLuServiceExample.kt` for comprehensive usage examples including:

- Complete file management workflows
- Batch operations
- Security management
- Folder organization
- File recovery

## API Limits

- Free accounts have bandwidth limitations
- Premium accounts have higher limits
- Direct links consume bandwidth allowance
- Use regular links instead of direct links for sharing

## Security Considerations

- API key should be kept secure
- File passwords are set on the FileLu service
- Private files are only accessible with proper authentication
- Consider implementing additional encryption for sensitive files

## Support

For FileLu API issues, refer to the [official FileLu API documentation](https://filelu.com/pages/api/).

For integration issues, check the Android logs for detailed error messages from the `FileLuService` class.
