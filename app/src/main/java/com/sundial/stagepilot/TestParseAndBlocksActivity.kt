package com.sundial.stagepilot

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class TestParseAndBlocksActivity : ComponentActivity() {

    private lateinit var btnSelectPdf: Button
    private lateinit var tvStatus: TextView
    private lateinit var container: FrameLayout
    
    private val viewOrder = mutableListOf<View>()
    private var screenWidth = 0

    private val selectPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            parsePdfToBlocks(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_parse_and_blocks)

        // Initialize PDFBox for Android internals
        PDFBoxResourceLoader.init(applicationContext)

        btnSelectPdf = findViewById(R.id.btn_select_pdf_for_blocks)
        tvStatus = findViewById(R.id.tv_status)
        container = findViewById(R.id.draggableContainer)
        
        screenWidth = resources.displayMetrics.widthPixels

        btnSelectPdf.setOnClickListener {
            selectPdfLauncher.launch("application/pdf")
        }
    }

    private fun parsePdfToBlocks(uri: Uri) {
        tvStatus.text = "Parsing PDF..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val document = PDDocument.load(inputStream)
                    val textStripper = PDFTextStripper()
                    val parsedText = textStripper.getText(document)
                    document.close()

                    val rawLines = parsedText.split('\n', '\r')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    val chordRegex = "^([A-G][b#♭♯]?(m|M|min|maj|dim|aug|sus|add|\\+|-|Δ|°|ø)?(2|4|5|6|6/9|7|9|11|13)?(\\(?([b#♭♯]|add|sus|maj|min|\\+|-)?\\d+\\)?)*(/[A-G][b#♭♯]?)?|N\\.?C\\.?)$".toRegex()

                    // Gather all the words and formatting first so we can quickly build views on Main thread
                    val parsedElements = mutableListOf<Pair<String, Boolean>>() // Pair(text, isChord)

                    for (line in rawLines) {
                        val wordsOrChords = line.split("\\s+".toRegex()).filter { it.isNotBlank() }
                        
                        for (word in wordsOrChords) {
                            val cleanWord = word.trim('[', ']')
                            if (cleanWord.matches(chordRegex)) {
                                parsedElements.add(Pair("[$cleanWord]", true))
                            } else {
                                parsedElements.add(Pair(word, false))
                            }
                        }
                    }

                    // Now jump to the Main Thread to generate all the UI views
                    withContext(Dispatchers.Main) {
                        tvStatus.text = "Generating ${parsedElements.size} Blocks..."
                        
                        // Clear any previous test
                        container.removeAllViews()
                        viewOrder.clear()

                        // Stop generating at max 500 blocks for testing to prevent total UI thread lockup on huge PDFs
                        val elementsToRender = parsedElements.take(500) 

                        for ((textStr, isChord) in elementsToRender) {
                            val wordBlock = TextView(this@TestParseAndBlocksActivity).apply {
                                text = textStr
                                textSize = 14f
                                setTextColor(Color.WHITE)
                                
                                val bg = ContextCompat.getDrawable(context, R.drawable.rounded_box)?.mutate() as? GradientDrawable
                                
                                if (isChord) {
                                    // Make chords highly distinct (e.g., bright vibrant blues/purples)
                                    val hsv = floatArrayOf(Random.nextFloat() * 60f + 200f, 0.9f, 0.9f)
                                    bg?.setColor(Color.HSVToColor(hsv))
                                    // Thicker font for chords
                                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                                } else {
                                    // Make lyrics darker/greyish
                                    val hsv = floatArrayOf(0f, 0f, Random.nextFloat() * 0.2f + 0.3f)
                                    bg?.setColor(Color.HSVToColor(hsv))
                                }
                                
                                background = bg
                                setPadding(24, 16, 24, 16)

                                measure(
                                    View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.AT_MOST),
                                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                                )
                                
                                layoutParams = FrameLayout.LayoutParams(measuredWidth, measuredHeight)
                                setOnTouchListener(DragTouchListener())
                            }

                            viewOrder.add(wordBlock)
                            container.addView(wordBlock)
                        }

                        if (parsedElements.size > 500) {
                            tvStatus.text = "Loaded (Capped at 500 blocks for performance test)"
                        } else {
                            tvStatus.text = "Loaded ${parsedElements.size} blocks."
                        }

                        // Trigger the flow calculation to arrange them!
                        container.post { layoutBlocks(null) }
                    }
                } ?: run {
                    withContext(Dispatchers.Main) {
                        tvStatus.text = "Error: Could not open file."
                    }
                }
            } catch (e: Exception) {
                Log.e("PdfToBlocks", "Error", e)
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Parse Error!"
                }
            }
        }
    }

    // --- The exact Layout & Reordering Logic from TestBlocksActivity ---

    private fun layoutBlocks(draggingView: View?) {
        var currentX = 16f
        var currentY = 16f
        val horizontalSpacing = 16f
        val verticalSpacing = 24f
        var maxLineHeight = 0

        for (view in viewOrder) {
            val width = view.measuredWidth
            val height = view.measuredHeight

            if (height > maxLineHeight) maxLineHeight = height

            // Carriage return if it exceeds the screen width
            if (currentX + width > screenWidth - 16f) {
                currentX = 16f
                currentY += maxLineHeight + verticalSpacing
                maxLineHeight = height
            }

            view.setTag(R.id.tag_target_x, currentX)
            view.setTag(R.id.tag_target_y, currentY)

            if (view != draggingView) {
                view.animate()
                    .x(currentX)
                    .y(currentY)
                    .setDuration(200)
                    .start()
            }

            currentX += width + horizontalSpacing
        }
    }

    private fun updateOrderWhileDragging(draggedView: View) {
        val centerX = draggedView.x + draggedView.width / 2
        val centerY = draggedView.y + draggedView.height / 2

        var bestIndex = -1
        var minDistance = Float.MAX_VALUE
        val currentIndex = viewOrder.indexOf(draggedView)

        for ((index, view) in viewOrder.withIndex()) {
            if (view == draggedView) continue

            val targetX = view.getTag(R.id.tag_target_x) as? Float ?: view.x
            val targetY = view.getTag(R.id.tag_target_y) as? Float ?: view.y
            val viewCenterX = targetX + view.width / 2
            val viewCenterY = targetY + view.height / 2

            val dx = centerX - viewCenterX
            val dy = centerY - viewCenterY
            val distance = dx * dx + dy * dy

            if (distance < minDistance) {
                minDistance = distance
                bestIndex = if (centerX < viewCenterX) index else index + 1
            }
        }

        if (bestIndex != -1) {
            if (bestIndex > currentIndex) {
                bestIndex -= 1
            }

            if (bestIndex != currentIndex) {
                viewOrder.remove(draggedView)
                if (bestIndex > viewOrder.size) bestIndex = viewOrder.size
                if (bestIndex < 0) bestIndex = 0
                viewOrder.add(bestIndex, draggedView)

                layoutBlocks(draggedView)
            }
        }
    }

    inner class DragTouchListener : View.OnTouchListener {
        private var dX = 0f
        private var dY = 0f

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    view.bringToFront()
                    
                    view.animate()
                        .scaleX(1.3f)
                        .scaleY(1.3f)
                        .alpha(0.8f)
                        .setDuration(150)
                        .start()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    view.x = event.rawX + dX
                    view.y = event.rawY + dY
                    updateOrderWhileDragging(view)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(1.0f)
                        
                    container.post { layoutBlocks(null) }
                    view.performClick()
                    return true
                }
                else -> return false
            }
        }
    }
}
