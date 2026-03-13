package com.sundial.stagepilot

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object DiagnosticTool {
    private const val TAG = "DiagnosticTool"

    fun runAllTests(context: Context, currentUri: Uri?): String {
        val sb = StringBuilder()
        sb.append("--- SELF TEST ---\n")
        
        // 1. Storage Test
        try {
            val testFile = File(context.cacheDir, "test_write.txt")
            testFile.writeText("test")
            sb.append("Storage: OK\n")
            testFile.delete()
        } catch (e: Exception) {
            sb.append("Storage: FAIL (${e.message})\n")
        }

        // 2. URI Test
        if (currentUri != null) {
            sb.append("Current URI: ${currentUri.scheme}\n")
            try {
                context.contentResolver.openFileDescriptor(currentUri, "r")?.use {
                    sb.append("URI Access: OK\n")
                }
            } catch (e: Exception) {
                sb.append("URI Access: FAIL (${e.message})\n")
            }
        } else {
            sb.append("Current URI: None\n")
        }

        // 3. Renderer Check
        sb.append("Memory: ${Runtime.getRuntime().freeMemory() / 1024 / 1024}MB free\n")
        
        Log.d(TAG, sb.toString())
        return sb.toString()
    }
}