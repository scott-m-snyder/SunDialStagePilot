package com.sundial.stagepilot

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogActivity : AppCompatActivity() {

    private lateinit var logTextView: TextView
    private lateinit var btnMenu: Button
    private lateinit var btnBack: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        val toolbarTitle: TextView = findViewById(R.id.toolbar_title)
        toolbarTitle.text = "Log"

        logTextView = findViewById(R.id.log_text_view)
        btnMenu = findViewById(R.id.btn_menu)
        btnBack = findViewById(R.id.btn_back)

        btnBack.setOnClickListener {
            finish()
        }

        btnMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 0, 0, "Open PDF")
            popup.menu.add(0, 1, 0, "Home (Parse & Blocks)")
            popup.menu.add(0, 2, 0, "Log")
            popup.menu.add(0, 3, 0, "Settings")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    0 -> {
                        // In LogActivity, we don't have a direct PDF context,
                        // so we'll just go to the main screen to select a PDF.
                        val intent = Intent(this, TestParseAndBlocksActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                    }
                    1 -> startActivity(Intent(this, TestParseAndBlocksActivity::class.java))
                    2 -> startActivity(Intent(this, LogActivity::class.java))
                    3 -> startActivity(Intent(this, SettingsActivity::class.java))
                }
                true
            }
            popup.show()
        }

        loadLogcat()
    }

    private fun loadLogcat() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val process = Runtime.getRuntime().exec("logcat -d")
                val logText = process.inputStream.bufferedReader().readText()
                withContext(Dispatchers.Main) {
                    logTextView.text = logText
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