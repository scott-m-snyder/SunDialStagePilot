package com.sundial.stagepilot

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.TextView
import java.io.File

class SelfTestActivity : Activity() {

    private lateinit var testLog: TextView
    private lateinit var keyInputLog: TextView
    private val logBuilder = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_self_test)

        testLog = findViewById(R.id.testLog)
        keyInputLog = findViewById(R.id.keyInputLog)

        findViewById<Button>(R.id.runTestsButton).setOnClickListener {
            runFullDiagnostic()
        }

        findViewById<Button>(R.id.closeButton).setOnClickListener {
            finish()
        }
    }

    private fun log(message: String) {
        logBuilder.append("> $message\n")
        testLog.text = logBuilder.toString()
    }

    private fun runFullDiagnostic() {
        logBuilder.setLength(0)
        log("Starting system check...")

        // 1. Storage Check
        val cacheDir = cacheDir
        log("Cache Dir: ${cacheDir.absolutePath}")
        log("Writable: ${cacheDir.canWrite()}")
        
        val freeSpace = cacheDir.freeSpace / (1024 * 1024)
        log("Free Space: ${freeSpace}MB")

        // 2. Memory Check
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMem = runtime.maxMemory() / (1024 * 1024)
        log("Memory: ${usedMem}MB / ${maxMem}MB")

        // 3. Asset Check
        try {
            val assets = assets.list("") ?: arrayOf()
            log("Assets found: ${assets.joinToString(", ")}")
            if (assets.contains("document.pdf")) {
                log("SUCCESS: document.pdf found in assets")
            } else {
                log("WARNING: document.pdf missing from assets")
            }
        } catch (e: Exception) {
            log("Asset Error: ${e.message}")
        }

        // 4. File system test
        try {
            val testFile = File(cacheDir, "diag_test.tmp")
            testFile.writeText("test")
            log("FS Write: SUCCESS")
            testFile.delete()
            log("FS Delete: SUCCESS")
        } catch (e: Exception) {
            log("FS Test FAILED: ${e.message}")
        }

        log("\n--- TEST COMPLETE ---")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val keyName = KeyEvent.keyCodeToString(keyCode)
        keyInputLog.text = "Last Key: $keyName (Code: $keyCode)"
        
        // Log to the main log too for persistence
        log("Input detected: $keyName")
        
        return super.onKeyDown(keyCode, event)
    }
}