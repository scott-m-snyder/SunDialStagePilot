package com.sundial.stagepilot

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.PopupMenu
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class SettingsActivity : AppCompatActivity() {

    private lateinit var btnMenu: Button
    private lateinit var btnBack: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        val toolbarTitle: TextView = findViewById(R.id.toolbar_title)
        toolbarTitle.text = "Settings"

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

        val stageModeSwitch: Switch = findViewById(R.id.stage_mode_switch)
        val prefs = getSharedPreferences("StagePilotPrefs", MODE_PRIVATE)
        
        stageModeSwitch.isChecked = prefs.getBoolean("stage_mode_enabled", false)

        stageModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("stage_mode_enabled", isChecked).apply()
            Toast.makeText(this, "Stage Mode: ${if (isChecked) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        }
    }
}