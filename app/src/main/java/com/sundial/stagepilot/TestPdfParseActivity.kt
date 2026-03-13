package com.sundial.stagepilot

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class TestPdfParseActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_pdf_parse)

        // Optionally, you can find and manipulate views in this new activity's layout
        // For example: val titleTextView = findViewById<TextView>(R.id.your_title_id_in_test_pdf_layout)
    }
}
