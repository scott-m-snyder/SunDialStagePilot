package com.sundial.stagepilot

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
    
    protected val viewOrder = mutableListOf<View>()
    protected var screenWidth = 0
    protected var currentUri: Uri? = null

    // Match all chords
    private val chordRegex = "^([A-G][b#♭♯]?(m|M|min|maj|dim|aug|sus|add|\\+|-|Δ|°|ø)?(2|4|5|6|6/9|7|9|11|13)?(\\(?([b#♭♯]|add|sus|maj|min|\\+|-)?\\d+\\)?)*(/[A-G][b#♭♯]?)?|N\\.?C\\.?)$".toRegex()

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
        
        screenWidth = resources.displayMetrics.widthPixels

        btnMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 0, 0, "Select PDF")
            popup.menu.add(0, 1, 0, "Home (Parse & Blocks)")
            popup.menu.add(0, 2, 0, "Test Feature 1")
            popup.menu.add(0, 3, 0, "Test Feature 2")
            popup.menu.add(0, 4, 0, "Test Feature 3")
            popup.menu.add(0, 5, 0, "Test Feature 4")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    0 -> selectPdfLauncher.launch(arrayOf("application/pdf"))
                    1 -> startActivity(Intent(this, TestParseAndBlocksActivity::class.java))
                    2 -> startActivity(Intent(this, TestFeature1Activity::class.java))
                    3 -> startActivity(Intent(this, TestFeature2Activity::class.java))
                    4 -> startActivity(Intent(this, TestFeature3Activity::class.java))
                    5 -> startActivity(Intent(this, TestFeature4Activity::class.java))
                }
                true
            }
            popup.show()
        }
        
        btnSave.setOnClickListener {
            saveCurrentLayout()
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
    }

    override fun onPause() {
        super.onPause()
        Wearable.getMessageClient(this).removeListener(this)
    }

    // --- WEAR OS COMMUNICATION HUB ---

    override fun onMessageReceived(messageEvent: MessageEvent) {
        // The watch just woke up and wants to know what's on the screen
        if (messageEvent.path == "/stagepilot/request_chart") {
            Log.d("StagePilot", "Watch requested the current chart. Sending it now...")
            pushCurrentChartToWatch()
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

                    val rawLines = parsedText.split('\n', '\r')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    val parsedElements = mutableListOf<Pair<String, Boolean>>() // Pair(text, isChord)

                    for (line in rawLines) {
                        val wordsOrChords = line.split("\\s+".toRegex()).filter { it.isNotBlank() }
                        
                        for (word in wordsOrChords) {
                            val cleanWord = word.trim('[', ']')
                            if (cleanWord.isBlank()) continue
                            if (cleanWord.matches("^[|:!/\\\\]+$".toRegex())) continue

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

                        val elementsToRender = parsedElements.take(500) 

                        for ((textStr, isChord) in elementsToRender) {
                            val wordBlock = createTextBlock(textStr, isChord)
                            viewOrder.add(wordBlock)
                            container.addView(wordBlock)
                        }

                        if (parsedElements.size > 500) {
                            tvStatus.text = "Loaded (Capped at 500 blocks for performance test)"
                        } else {
                            tvStatus.text = "Loaded ${parsedElements.size} blocks."
                        }

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

    // --- Block Factory Helpers ---

    private fun createTextBlock(textStr: String, isChord: Boolean): TextView {
        return TextView(this@TestParseAndBlocksActivity).apply {
            text = textStr
            textSize = 14f
            setTextColor(Color.WHITE)
            
            setTag(R.id.tag_is_blank, false)
            setTag(R.id.tag_is_chord, isChord)
            
            val bg = ContextCompat.getDrawable(context, R.drawable.rounded_box)?.mutate() as? GradientDrawable
            
            if (isChord) {
                val hsv = floatArrayOf(Random.nextFloat() * 60f + 200f, 0.9f, 0.9f)
                bg?.setColor(Color.HSVToColor(hsv))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
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
            setOnClickListener { showBlockMenu(this) }
        }
    }

    private fun createBlankBlock(): TextView {
        return TextView(this@TestParseAndBlocksActivity).apply {
            text = " "
            textSize = 1f
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
            
            setTag(R.id.tag_is_blank, true)
            setTag(R.id.tag_is_chord, false)

            val fixedWidth = screenWidth - 32
            measure(
                View.MeasureSpec.makeMeasureSpec(fixedWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(32, View.MeasureSpec.EXACTLY)
            )
            layoutParams = FrameLayout.LayoutParams(fixedWidth, 32)
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
                
                if (isChord) {
                    val bg = ContextCompat.getDrawable(this, R.drawable.rounded_box)?.mutate() as? GradientDrawable
                    bg?.setColor(Color.HSVToColor(floatArrayOf(Random.nextFloat() * 60f + 200f, 0.9f, 0.9f)))
                    block.background = bg
                    block.typeface = android.graphics.Typeface.DEFAULT_BOLD
                } else {
                    val bg = ContextCompat.getDrawable(this, R.drawable.rounded_box)?.mutate() as? GradientDrawable
                    bg?.setColor(Color.HSVToColor(floatArrayOf(0f, 0f, Random.nextFloat() * 0.2f + 0.3f)))
                    block.background = bg
                    block.typeface = android.graphics.Typeface.DEFAULT
                }

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
