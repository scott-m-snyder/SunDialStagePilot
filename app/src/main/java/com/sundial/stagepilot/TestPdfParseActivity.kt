package com.sundial.stagepilot

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TestPdfParseActivity : ComponentActivity() {

    private lateinit var tvParsedLog: TextView
    private lateinit var btnSelectPdf: Button

    private val selectPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            parsePdf(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_pdf_parse)

        // Initialize PDFBox for Android internals
        PDFBoxResourceLoader.init(applicationContext)

        tvParsedLog = findViewById(R.id.tv_parsed_log)
        btnSelectPdf = findViewById(R.id.btn_select_pdf)

        btnSelectPdf.setOnClickListener {
            // Launch the file picker to grab a PDF
            selectPdfLauncher.launch("application/pdf")
        }
    }

    private fun parsePdf(uri: Uri) {
        tvParsedLog.text = "Loading and Parsing PDF...\n(This might take a second)"

        // Run parsing on a background thread so we don't freeze the UI!
        CoroutineScope(Dispatchers.IO).launch {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val document = PDDocument.load(inputStream)
                    val textStripper = PDFTextStripper()
                    val parsedText = textStripper.getText(document)
                    val pageCount = document.numberOfPages
                    document.close()

                    val rawLines = parsedText.split('\n', '\r')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    val formattedLog = StringBuilder()
                    formattedLog.append("=== PDF PARSE SUCCESS ===\n")
                    formattedLog.append("Total Pages: $pageCount\n")
                    formattedLog.append("=========================\n\n")

                    // A highly robust, industry-standard Regular Expression to detect all musical chord notations.
                    // Supports:
                    // - Roots: A-G
                    // - Accidentals: b, #, and true unicode ♭, ♯
                    // - Qualities: m, M, min, maj, dim, aug, sus, add, +, -, Δ, °, ø
                    // - Intervals: 2, 4, 5, 6, 6/9, 7, 9, 11, 13
                    // - Alterations: (b5), #9, add9, sus4, etc. (including parentheses)
                    // - Slash/Bass chords: /F#, /Bb
                    // - "No Chord" markers: N.C.
                    val chordRegex = "^([A-G][b#♭♯]?(m|M|min|maj|dim|aug|sus|add|\\+|-|Δ|°|ø)?(2|4|5|6|6/9|7|9|11|13)?(\\(?([b#♭♯]|add|sus|maj|min|\\+|-)?\\d+\\)?)*(/[A-G][b#♭♯]?)?|N\\.?C\\.?)$".toRegex()

                    for (line in rawLines) {
                        val wordsOrChords = line.split("\\s+".toRegex()).filter { it.isNotBlank() }
                        
                        for (word in wordsOrChords) {
                            // Strip any pre-existing brackets in case the PDF already formats them like [C]
                            val cleanWord = word.trim('[', ']')
                            
                            if (cleanWord.matches(chordRegex)) {
                                // It IS a chord! Wrap it cleanly in brackets.
                                formattedLog.append(" [$cleanWord] \n")
                            } else {
                                // It's just a regular lyric word. Wrap it in spaces.
                                formattedLog.append(" $word \n")
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        tvParsedLog.text = formattedLog.toString()
                    }
                } ?: run {
                    withContext(Dispatchers.Main) {
                        tvParsedLog.text = "Error: Could not open the file."
                    }
                }
            } catch (e: Exception) {
                Log.e("PdfParse", "Error parsing PDF", e)
                withContext(Dispatchers.Main) {
                    tvParsedLog.text = "=== ERROR PARSING ===\n${e.message}\n\nCheck Logcat for more details."
                }
            }
        }
    }
}
