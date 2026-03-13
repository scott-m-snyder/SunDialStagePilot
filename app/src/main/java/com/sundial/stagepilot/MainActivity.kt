package com.sundial.stagepilot

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.util.Log

class MainActivity : Activity() {

    private lateinit var appTitleTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appTitleTextView = findViewById(R.id.app_title)

        // Log the app title to confirm it's loaded
        Log.d("MainActivity", "App Title: ${appTitleTextView.text}")
    }

    override fun onDestroy() {
        super.onDestroy()
        // No specific resources to clean up in this simplified state.
    }
}
