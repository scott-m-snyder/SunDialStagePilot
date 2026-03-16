package com.sundial.stagepilot

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class LogActivity : Activity() {

    private lateinit var logTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        logTextView = findViewById(R.id.log_text_view)

        loadLogcat()
    }

    private fun loadLogcat() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val process = Runtime.getRuntime().exec("logcat -d")
                val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
                val log = StringBuilder()
                var line: String?
                while (bufferedReader.readLine().also { line = it } != null) {
                    log.append(line).append("
")
                }
                withContext(Dispatchers.Main) {
                    logTextView.text = log.toString()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    logTextView.text = "Error loading logcat: ${e.message}"
                }
            }
        }
    }
}
