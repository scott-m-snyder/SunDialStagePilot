package com.sundial.stagepilot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button

class HelpActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.backButton).setOnLongClickListener {
            startActivity(Intent(this, SelfTestActivity::class.java))
            true
        }
    }
}