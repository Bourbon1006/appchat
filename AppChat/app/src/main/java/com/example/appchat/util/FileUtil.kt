package com.example.appchat.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.app.Activity

object FileUtil {
    const val STORAGE_PERMISSION_CODE = 1001

    private fun checkStoragePermissions(activity: Activity): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用新的权限
            val readImages = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.READ_MEDIA_IMAGES
            )
            val readVideos = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.READ_MEDIA_VIDEO
            )

            if (readImages != PackageManager.PERMISSION_GRANTED ||
                readVideos != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                    ),
                    STORAGE_PERMISSION_CODE
                )
                false
            } else {
                true
            }
        } else {
            // Android 13 以下版本使用旧的权限
            val readPermission = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            val writePermission = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )

            if (readPermission != PackageManager.PERMISSION_GRANTED ||
                writePermission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    STORAGE_PERMISSION_CODE
                )
                false
            } else {
                true
            }
        }
    }

    fun saveFile(activity: Activity, sourceFile: File, destinationPath: String): File? {
        if (!checkStoragePermissions(activity)) {
            return null
        }

        try {
            val destinationFile = File(destinationPath)
            sourceFile.copyTo(destinationFile, overwrite = true)
            return destinationFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun getFileFromUri(context: Context, uri: Uri): File? {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val fileName = getFileName(context, uri)
            // 使用应用私有目录而不是外部存储
            val tempFile = File(context.cacheDir, fileName)
            copyInputStreamToFile(inputStream, tempFile)
            return tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var fileName = "temp_file"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        return fileName
    }

    private fun copyInputStreamToFile(inputStream: InputStream, file: File) {
        FileOutputStream(file).use { outputStream ->
            val buffer = ByteArray(4 * 1024)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
            }
            outputStream.flush()
        }
    }
}