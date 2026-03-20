package com.sundial.stagepilot

import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

open class TestParseAndBlocksActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {

    protected lateinit var btnMenu: Button
    protected lateinit var btnSave: Button
    protected lateinit var tvStatus: TextView
    protected lateinit var container: FrameLayout
    protected lateinit var scrollView: ScrollView

    protected val viewOrder = mutableListOf<View>()
    protected var screenWidth = 0
    protected var currentUri: Uri? = null
    
    // Remote scrolling sync state
    private var isReceivingRemoteScroll = false
    private var lastScrollSendTime = 0L

    // Match all chords
    private val chordRegex = "^(\\[[A-Za-z0-9\\s]+\\]|[A-G][b#♭♯]?(m|M|min|maj|dim|aug|sus|add|\\+|-|Δ|°|ø)?(2|4|5|6|6/9|7|9|11|13)?(\\(([b#♭♯]|add|sus|maj|min|\\+|-)?\\d+\\))?(/[A-G][b#♭♯]?)?|N\\.?C\\.?)$".toRegex()

    // Using OpenDocument to properly support persistable URI permissions
    protected val selectPdfLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                Log.e("PdfToBlocks", "Failed to take persistable URI permission", e)
            }

            val prefs = getSharedPreferences("StagePilotPrefs", MODE_PRIVATE)
            prefs.edit().putString("last_pdf_uri", uri.toString()).apply()

            loadOrParsePdf(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getLayoutResourceId())

        PDFBoxResourceLoader.init(applicationContext)

        btnMenu = findViewById(R.id.btn_menu)
        btnSave = findViewById(R.id.btn_save) ?: Button(this)
        tvStatus = findViewById(R.id.tv_status)
        container = findViewById(R.id.draggableContainer)
        scrollView = findViewById(R.id.main_scroll_view)

        screenWidth = resources.displayMetrics.widthPixels

        btnMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 0, 0, "Open PDF")
            popup.menu.add(0, 1, 0, "Home (Parse & Blocks)")
            popup.menu.add(0, 2, 0, "Log")
            popup.menu.add(0, 3, 0, "Settings")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    0 -> selectPdfLauncher.launch(arrayOf("application/pdf"))
                    1 -> startActivity(Intent(this, TestParseAndBlocksActivity::class.java))
                    2 -> startActivity(Intent(this, LogActivity::class.java))
                    3 -> startActivity(Intent(this, SettingsActivity::class.java))
                }
                true
            }
            popup.show()
        }

        btnSave.setOnClickListener {
            saveCurrentLayout()
        }
        
        // Listen to scroll changes and proactively sync the percentage to the watch
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            if (!isReceivingRemoteScroll) {
                val maxScroll = container.height - scrollView.height
                if (maxScroll > 0) {
                    val percentage = scrollView.scrollY.toFloat() / maxScroll.toFloat()
                    val now = System.currentTimeMillis()
                    // Throttle updates to ~20fps to avoid flooding the watch
                    if (now - lastScrollSendTime > 50) {
                        lastScrollSendTime = now
                        sendScrollSyncToWatch(percentage)
                    }
                }
            }
        }

        // --- Auto-Load Last PDF ---
        val prefs = getSharedPreferences("StagePilotPrefs", MODE_PRIVATE)
        val lastUriStr = prefs.getString("last_pdf_uri", null)
        if (lastUriStr != null) {
            try {
                loadOrParsePdf(Uri.parse(lastUriStr))
            } catch (e: Exception) {
                Log.e("PdfToBlocks", "Error parsing saved URI on startup", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Register to listen to the watch!
        Wearable.getMessageClient(this).addListener(this)

        // Refresh stage mode when returning from settings
        refreshStageMode()
    }

    override fun onPause() {
        super.onPause()
        Wearable.getMessageClient(this).removeListener(this)
    }

    // Helper for device-independent pixels
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
    
    private fun sendScrollSyncToWatch(percentage: Float) {
        val messageClient = Wearable.getMessageClient(this)
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                messageClient.sendMessage(node.id, "/stagepilot/sync_scroll", percentage.toString().toByteArray())
            }
        }
    }

    // --- WEAR OS COMMUNICATION HUB ---
    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            "/stagepilot/request_chart" -> {
                Log.d("StagePilot", "Watch requested the current chart. Sending it now...")
                pushCurrentChartToWatch()
            }
            "/stagepilot/sync_scroll" -> {
                val percentage = String(messageEvent.data).toFloatOrNull() ?: 0f
                runOnUiThread {
                    isReceivingRemoteScroll = true
                    val maxScroll = container.height - scrollView.height
                    if (maxScroll > 0) {
                        val targetY = (percentage * maxScroll).toInt()
                        // Native scroll jump to exactly match the watch!
                        scrollView.scrollTo(0, targetY)
                    }
                    // Reset flag after a tiny delay so the listener doesn't bounce it back
                    scrollView.postDelayed({ isReceivingRemoteScroll = false }, 50)
                }
            }
            // Keeping the old paths as fallbacks, though they're no longer strictly needed
            // since the watch now handles scrolling natively and syncs the percentage!
            "/stagepilot/remote_scroll_down" -> {
                runOnUiThread {
                    val scrollAmount = (scrollView.height * 0.8f).toInt()
                    scrollView.smoothScrollBy(0, scrollAmount)
                }
            }
            "/stagepilot/remote_scroll_up" -> {
                runOnUiThread {
                    val scrollAmount = (scrollView.height * 0.8f).toInt()
                    scrollView.smoothScrollBy(0, -scrollAmount)
                }
            }
        }
    }

    protected fun pushCurrentChartToWatch() {
        val payload = buildStringFromBlocks()
        if (payload.isBlank()) return // Don't send empty data

        val messageClient = Wearable.getMessageClient(this)
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                messageClient.sendMessage(node.id, "/stagepilot/load_chart", payload.toByteArray())
                    .addOnSuccessListener { Log.d("StagePilot", "Pushed chart to watch!") }
                    .addOnFailureListener { Log.e("StagePilot", "Failed to push chart to watch") }
            }
        }
    }

    // Builds a completely raw text version of the currently arranged blocks for the watch
    private fun buildStringFromBlocks(): String {
        val builder = StringBuilder()
        var currentY = -1f

        for (view in viewOrder) {
            val block = view as TextView
            val tagY = block.getTag(R.id.tag_target_y) as? Float ?: 0f
            val isBlank = block.getTag(R.id.tag_is_blank) as? Boolean ?: false

            if (isBlank) {
                // Instead of a full gap, just inject a double-line break for readability on the watch
                builder.append("\n\n")
                currentY = tagY // update Y position
                continue
            }

            // If the Y dropped significantly, we are on a new line!
            if (currentY != -1f && tagY > currentY + 10f) {
                builder.append("\n")
            }

            val text = block.text.toString()
            val isChord = block.getTag(R.id.tag_is_chord) as? Boolean ?: false

            if (isChord) {
                builder.append("[$text] ")
            } else {
                builder.append("$text ")
            }

            currentY = tagY
        }
        return builder.toString()
    }

    // ------------------------------------
    protected open fun getLayoutResourceId(): Int {
        return R.layout.activity_test_parse_and_blocks
    }

    private fun saveCurrentLayout() {
        if (currentUri == null) {
            Toast.makeText(this, "No PDF loaded to save!", Toast.LENGTH_SHORT).show()
            return
        }
        val jsonArray = JSONArray()

        for (view in viewOrder) {
            val tv = view as TextView
            val obj = JSONObject()

            val isBlank = tv.getTag(R.id.tag_is_blank) as? Boolean ?: false
            val isChord = tv.getTag(R.id.tag_is_chord) as? Boolean ?: false

            obj.put("text", tv.text.toString())
            obj.put("isBlank", isBlank)
            obj.put("isChord", isChord)
            jsonArray.put(obj)
        }

        val prefs = getSharedPreferences("StagePilotPrefs", MODE_PRIVATE)
        prefs.edit().putString("blocks_${currentUri.toString()}", jsonArray.toString()).apply()
        Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()

        // Push the newly saved layout to the watch!
        pushCurrentChartToWatch()
    }

    protected fun loadOrParsePdf(uri: Uri) {
        currentUri = uri

        val prefs = getSharedPreferences("StagePilotPrefs", MODE_PRIVATE)
        val savedJson = prefs.getString("blocks_$uri", null)

        if (savedJson != null) {
            restoreBlocksFromJson(savedJson)
        } else {
            parsePdfToBlocks(uri)
        }
    }

    private fun restoreBlocksFromJson(jsonStr: String) {
        tvStatus.text = "Loading saved layout..."

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val jsonArray = JSONArray(jsonStr)

                withContext(Dispatchers.Main) {
                    container.removeAllViews()
                    viewOrder.clear()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val text = obj.getString("text")
                        val isBlank = obj.getBoolean("isBlank")
                        val isChord = obj.getBoolean("isChord")

                        val block = if (isBlank) {
                            createBlankBlock()
                        } else {
                            createTextBlock(text, isChord)
                        }

                        viewOrder.add(block)
                        container.addView(block)
                    }

                    tvStatus.text = "Loaded ${viewOrder.size} saved blocks."
                    container.post {
                        layoutBlocks(null)
                        pushCurrentChartToWatch() // Send newly loaded chart
                    }
                }
            } catch (e: Exception) {
                Log.e("PdfToBlocks", "Error restoring JSON", e)
                withContext(Dispatchers.Main) { tvStatus.text = "Error loading save." }
            }
        }
    }

    protected fun parsePdfToBlocks(uri: Uri) {
        tvStatus.text = "Parsing PDF..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val document = PDDocument.load(inputStream)
                    val textStripper = PDFTextStripper()

                    textStripper.sortByPosition = true

                    val parsedText = textStripper.getText(document)
                    document.close()

                    // We DO NOT filter out empty lines here anymore, because an empty line represents
                    // a paragraph break in the song, which we need to render as a "Blank Row" block!
                    val rawLines = parsedText.split('\n', '\r').map { it.trim() }

                    val parsedElements = mutableListOf<Pair<String, Boolean>>() // Pair(text, isChord)

                    for (line in rawLines) {
                        if (line.isEmpty()) {
                            // If the line is purely whitespace/empty, insert a blank row placeholder!
                            parsedElements.add(Pair("", false))
                            continue
                        }

                        val tokens = line.split("\\s+".toRegex()).filter { it.isNotBlank() }

                        var inBracket = false
                        var currentBracketContent = ""

                        for (word in tokens) {
                            if (word.startsWith("[") && word.endsWith("]") && word.length > 1) {
                                val content = word.substring(1, word.length - 1).trim()
                                if (content.matches(chordRegex) || content.matches("^[A-G].*".toRegex())) {
                                    parsedElements.add(Pair("[$content]", true))
                                } else {
                                    parsedElements.add(Pair("$content:", false))
                                }
                                continue
                            }

                            if (word == "[") {
                                inBracket = true
                                currentBracketContent = ""
                                continue
                            } else if (word.startsWith("[")) {
                                inBracket = true
                                currentBracketContent = word.substring(1) + " "
                                continue
                            } else if (inBracket && word == "]") {
                                inBracket = false
                                val content = currentBracketContent.trim()
                                if (content.matches(chordRegex) || content.matches("^[A-G].*".toRegex())) {
                                    parsedElements.add(Pair("[$content]", true))
                                } else {
                                    parsedElements.add(Pair("$content:", false))
                                }
                                continue
                            } else if (inBracket && word.endsWith("]")) {
                                inBracket = false
                                currentBracketContent += word.substring(0, word.length - 1)
                                val content = currentBracketContent.trim()
                                if (content.matches(chordRegex) || content.matches("^[A-G].*".toRegex())) {
                                    parsedElements.add(Pair("[$content]", true))
                                } else {
                                    parsedElements.add(Pair("$content:", false))
                                }
                                continue
                            } else if (inBracket) {
                                currentBracketContent += "$word "
                                continue
                            }

                            val cleanWord = word.trim('[', ']')
                            if (cleanWord.isBlank()) continue
                            if (cleanWord.matches("^[|:!/\\\\\\\\]+\$".toRegex())) continue

                            if (cleanWord.matches(chordRegex)) {
                                parsedElements.add(Pair("[$cleanWord]", true))
                            } else {
                                parsedElements.add(Pair(word, false))
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        tvStatus.text = "Generating ${parsedElements.size} Blocks..."

                        container.removeAllViews()
                        viewOrder.clear()

                        for ((textStr, isChord) in parsedElements) {
                            if (textStr.isEmpty()) {
                                // Add a completely blank/invisible spacer row to separate sections!
                                val blankBlock = createBlankBlock()
                                viewOrder.add(blankBlock)
                                container.addView(blankBlock)
                            } else {
                                val wordBlock = createTextBlock(textStr, isChord)
                                viewOrder.add(wordBlock)
                                container.addView(wordBlock)
                            }
                        }

                        tvStatus.text = "Loaded ${parsedElements.size} blocks. (End of Chart)"

                        container.post {
                            layoutBlocks(null)
                            pushCurrentChartToWatch() // Send newly generated chart
                        }
                    }
                } ?: run {
                    withContext(Dispatchers.Main) { tvStatus.text = "Error: Could not open file." }
                }
            } catch (e: Exception) {
                Log.e("PdfToBlocks", "Error", e)
                withContext(Dispatchers.Main) { tvStatus.text = "Parse Error!" }
            }
        }
    }

    private fun refreshStageMode() {
        val prefs = getSharedPreferences("StagePilotPrefs", MODE_PRIVATE)
        val stageModeEnabled = prefs.getBoolean("stage_mode_enabled", false)

        scrollView.setBackgroundColor(if (stageModeEnabled) Color.WHITE else Color.parseColor("#222222"))
        container.setBackgroundColor(Color.TRANSPARENT)

        for (view in viewOrder) {
            val tv = view as TextView
            val isBlank = tv.getTag(R.id.tag_is_blank) as? Boolean ?: false
            val isChord = tv.getTag(R.id.tag_is_chord) as? Boolean ?: false

            applyStyleToBlock(tv, isBlank, isChord, stageModeEnabled)

            // Re-measure text blocks (blank blocks are measured inside applyStyleToBlock)
            if (!isBlank) {
                tv.measure(
                    View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                tv.layoutParams = FrameLayout.LayoutParams(tv.measuredWidth, tv.measuredHeight)
            }
        }
        layoutBlocks(null)
    }

    private fun applyStyleToBlock(tv: TextView, isBlank: Boolean, isChord: Boolean, stageModeEnabled: Boolean) {
        if (isBlank) {
            val newHeight = if (stageModeEnabled) dpToPx(16) else dpToPx(32)
            val fixedWidth = screenWidth - dpToPx(32)
            tv.measure(
                View.MeasureSpec.makeMeasureSpec(fixedWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(newHeight, View.MeasureSpec.EXACTLY)
            )
            tv.layoutParams = FrameLayout.LayoutParams(fixedWidth, newHeight)
        } else {
            val bg = ContextCompat.getDrawable(this, R.drawable.rounded_box)?.mutate() as? GradientDrawable
            if (stageModeEnabled) {
                tv.setTextColor(Color.BLACK)
                bg?.setColor(Color.WHITE)
                tv.typeface = android.graphics.Typeface.DEFAULT
            } else {
                tv.setTextColor(Color.WHITE)
                if (isChord) {
                    val hue = tv.getTag(R.id.tag_target_x) as? Float ?: (Random.nextFloat() * 60f + 200f) // Keep stable colors if possible
                    bg?.setColor(Color.HSVToColor(floatArrayOf(hue, 0.9f, 0.9f)))
                    tv.typeface = android.graphics.Typeface.DEFAULT_BOLD
                } else {
                    bg?.setColor(Color.HSVToColor(floatArrayOf(0f, 0f, 0.2f))) // Dark gray for lyrics
                    tv.typeface = android.graphics.Typeface.DEFAULT
                }
            }
            tv.background = bg
            tv.setPadding(24, 16, 24, 16)
        }
    }

    // --- Block Factory Helpers ---
    private fun createTextBlock(textStr: String, isChord: Boolean): TextView {
        return TextView(this@TestParseAndBlocksActivity).apply {
            text = textStr
            textSize = 14f

            val prefs = context.getSharedPreferences("StagePilotPrefs", MODE_PRIVATE)
            val stageModeEnabled = prefs.getBoolean("stage_mode_enabled", false)

            setTag(R.id.tag_is_blank, false)
            setTag(R.id.tag_is_chord, isChord)
            // Generate initial random hue to keep non-stage mode colors stable when toggling
            if (isChord) setTag(R.id.tag_target_x, Random.nextFloat() * 60f + 200f)

            applyStyleToBlock(this, false, isChord, stageModeEnabled)

            measure(
                View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )

            layoutParams = FrameLayout.LayoutParams(measuredWidth, measuredHeight)
            setOnClickListener { showBlockMenu(this) }
        }
    }

    private fun createBlankBlock(): TextView {
        return TextView(this@TestParseAndBlocksActivity).apply {
            text = " "
            textSize = 1f
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)

            val prefs = context.getSharedPreferences("StagePilotPrefs", MODE_PRIVATE)
            val stageModeEnabled = prefs.getBoolean("stage_mode_enabled", false)

            setTag(R.id.tag_is_blank, true)
            setTag(R.id.tag_is_chord, false)

            applyStyleToBlock(this, true, false, stageModeEnabled)
            setOnClickListener { showBlockMenu(this) }
        }
    }

    // --- Interaction Menu Logic ---
    protected fun showBlockMenu(block: TextView) {
        val popup = PopupMenu(this, block)
        popup.menu.add(0, 1, 0, "Drag Block")
        popup.menu.add(0, 2, 0, "Edit Block")
        popup.menu.add(0, 3, 0, "Delete Block")
        popup.menu.add(0, 4, 0, "Add Blank Row")
        popup.menu.add(0, 5, 0, "Add Text Block") // New feature!
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> enableDragMode(block)
                2 -> showEditDialog(block)
                3 -> deleteBlock(block)
                4 -> addBlankRow(block)
                5 -> showAddTextDialog(block)
            }
            true
        }
        popup.show()
    }

    protected fun enableDragMode(block: TextView) {
        block.bringToFront()
        block.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .alpha(0.8f)
            .setDuration(150)
            .start()

        block.setOnTouchListener(DragTouchListener())
        block.setOnClickListener(null)
    }

    protected fun deleteBlock(block: TextView) {
        viewOrder.remove(block)
        container.removeView(block)
        layoutBlocks(null)
    }

    protected fun showEditDialog(block: TextView) {
        val input = EditText(this)
        input.setText(block.text)

        AlertDialog.Builder(this)
            .setTitle("Edit Block")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newText = input.text.toString().trim()
                block.text = newText

                val isChord = newText.matches(chordRegex) || newText.startsWith("[")
                block.setTag(R.id.tag_is_chord, isChord)

                val prefs = getSharedPreferences("StagePilotPrefs", MODE_PRIVATE)
                val stageModeEnabled = prefs.getBoolean("stage_mode_enabled", false)

                applyStyleToBlock(block, false, isChord, stageModeEnabled)

                block.measure(
                    View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                block.layoutParams = FrameLayout.LayoutParams(block.measuredWidth, block.measuredHeight)

                layoutBlocks(null)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    protected fun showAddTextDialog(afterBlock: TextView) {
        val input = EditText(this)
        input.hint = "Enter word or [Chord]"

        AlertDialog.Builder(this)
            .setTitle("Add New Block")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    val isChord = text.matches(chordRegex) || text.startsWith("[")
                    val newBlock = createTextBlock(text, isChord)

                    val index = viewOrder.indexOf(afterBlock)
                    if (index != -1) {
                        viewOrder.add(index + 1, newBlock)
                        container.addView(newBlock)
                        layoutBlocks(null)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    protected fun addBlankRow(afterBlock: TextView) {
        val blankBlock = createBlankBlock()
        val index = viewOrder.indexOf(afterBlock)
        if (index != -1) {
            viewOrder.add(index + 1, blankBlock)
            container.addView(blankBlock)
            layoutBlocks(null)
        }
    }

    // --- Layout Math ---
    protected fun layoutBlocks(draggingView: View?) {
        val startPaddingX = dpToPx(16).toFloat()
        var currentX = startPaddingX
        var currentY = dpToPx(16).toFloat()
        val horizontalSpacing = dpToPx(8).toFloat()
        val verticalSpacing = dpToPx(12).toFloat()
        var maxLineHeight = 0

        val maxAllowedX = screenWidth - dpToPx(16)

        for (view in viewOrder) {
            val isBlank = view.getTag(R.id.tag_is_blank) as? Boolean ?: false

            if (isBlank) {
                // FORCE a line break BEFORE the blank row if we are not at the start of a line
                if (currentX > startPaddingX) {
                    currentX = startPaddingX
                    currentY += maxLineHeight + verticalSpacing
                    maxLineHeight = 0
                }

                // Place the blank row block itself
                view.setTag(R.id.tag_target_x, currentX)
                view.setTag(R.id.tag_target_y, currentY)

                if (view != draggingView) {
                    view.animate()
                        .x(currentX)
                        .y(currentY)
                        .setDuration(200)
                        .start()
                }

                // FORCE a line break AFTER the blank row block, so the next word goes on a new line!
                currentY += view.measuredHeight + verticalSpacing
                currentX = startPaddingX
                maxLineHeight = 0
                continue
            }

            // Normal text/chord block placement
            val width = view.measuredWidth
            val height = view.measuredHeight

            // Carriage return if it exceeds the screen width
            if (currentX + width > maxAllowedX) {
                currentX = startPaddingX
                currentY += maxLineHeight + verticalSpacing
                maxLineHeight = 0 // Reset max height for the new line!
            }

            if (height > maxLineHeight) maxLineHeight = height

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

        // Dynamically update the container height so the ScrollView can accurately scroll!
        // Since we position children via translationX/Y (animate().x().y()), the FrameLayout
        // doesn't know the real content bounds. We must set the height explicitly.
        val requiredHeight = (currentY + maxLineHeight + dpToPx(150)).toInt()
        Log.d("StagePilot", "layoutBlocks: requiredHeight=$requiredHeight scrollViewHeight=${scrollView.height} blocks=${viewOrder.size}")
        val lp = container.layoutParams
        lp.height = requiredHeight
        container.layoutParams = lp
        container.minimumHeight = requiredHeight
        // Force a full measure/layout pass so ScrollView recalculates scroll range
        container.requestLayout()
        scrollView.post { scrollView.requestLayout() }
    }

    protected fun updateOrderWhileDragging(draggedView: View) {
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

                    view.setOnTouchListener(null)
                    view.setOnClickListener { showBlockMenu(view as TextView) }
                    return true
                }
                else -> return false
            }
        }
    }
}