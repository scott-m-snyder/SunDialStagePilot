package com.sundial.stagepilot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var appTitleTextView: TextView
    private lateinit var testBlocksButton: Button
    private lateinit var testPdfParseButton: Button
    private lateinit var testParseAndBlocksButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        appTitleTextView = findViewById(R.id.app_title)
        testBlocksButton = findViewById(R.id.test_blocks_button)
        testPdfParseButton = findViewById(R.id.test_pdf_parse_button)
        testParseAndBlocksButton = findViewById(R.id.test_parse_and_blocks_button)

        // Set OnClickListener for buttons to navigate to new activities
        testBlocksButton.setOnClickListener {
            Toast.makeText(this, "Navigating to Test Blocks screen...", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, TestBlocksActivity::class.java)
            startActivity(intent)
            Log.d("MainActivity", "Navigated to TestBlocksActivity")
        }

        testPdfParseButton.setOnClickListener {
            Toast.makeText(this, "Navigating to Test PDF Parse screen...", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, TestPdfParseActivity::class.java)
            startActivity(intent)
            Log.d("MainActivity", "Navigated to TestPdfParseActivity")
        }

        testParseAndBlocksButton.setOnClickListener {
            Toast.makeText(this, "Navigating to Test Parse & Blocks screen...", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, TestParseAndBlocksActivity::class.java)
            startActivity(intent)
            Log.d("MainActivity", "Navigated to TestParseAndBlocksActivity")
        }

        // Log the app title to confirm it's loaded
        Log.d("MainActivity", "App Title: ${appTitleTextView.text}")
    }

    override fun onDestroy() {
        super.onDestroy()
        // No specific resources to clean up in this simplified state.
    }
}
