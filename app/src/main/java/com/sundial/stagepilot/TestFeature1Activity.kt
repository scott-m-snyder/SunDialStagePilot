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
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
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
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// Completely standalone sandbox class for Feature 1 Testing
class TestFeature1Activity : ComponentActivity() {

    private lateinit var btnMenu: Button
    private lateinit var btnSave: Button
    private lateinit var btnPresentation: Button
    private lateinit var tvStatus: TextView
    private lateinit var container: FrameLayout
    
    private val viewOrder = mutableListOf<View>()
    private var screenWidth = 0
    private var currentUri: Uri? = null
    
    private var isPresentationMode = false

    private val chordRegex = "^([A-G][b#♭♯]?(m|M|min|maj|dim|aug|sus|add|\\+|-|Δ|°|ø)?(2|4|5|6|6/9|7|9|11|13)?(\\(?([b#♭♯]|add|sus|maj|min|\\+|-)?\\d+\\)?)*(/[A-G][b#♭♯]?)?|N\\.?C\\.?)$".toRegex()

    private val selectPdfLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                Log.e("TestFeature1", "Failed permission", e)
            }
            getSharedPreferences("StagePilotPrefs", MODE_PRIVATE).edit().putString("last_pdf_uri", uri.toString()).apply()
            loadOrParsePdf(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_feature_1)

        PDFBoxResourceLoader.init(applicationContext)

        btnMenu = findViewById(R.id.btn_menu)
        btnSave = findViewById(R.id.btn_save)
        btnPresentation = findViewById(R.id.btn_presentation)
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
        
        btnSave.setOnClickListener { saveCurrentLayout() }
        
        btnPresentation.setOnClickListener { togglePresentationMode() }

        val prefs = getSharedPreferences("StagePilotPrefs", MODE_PRIVATE)
        val lastUriStr = prefs.getString("last_pdf_uri", null)
        if (lastUriStr != null) {
            try {
                loadOrParsePdf(Uri.parse(lastUriStr))
            } catch (e: Exception) {
                Log.e("TestFeature1", "Error parsing URI", e)
            }
        }
    }

    private fun togglePresentationMode() {
        isPresentationMode = !isPresentationMode
        tvStatus.text = if (isPresentationMode) "Presentation Mode (Read-Only)" else "Edit Mode (Tap blocks to edit)"
        
        // Loop through all generated views and swap their visual styling instantly
        for (view in viewOrder) {
            val block = view as TextView
            applyBlockStyle(block)
        }
        
        // Re-run the flow layout math because padding changes the block width/height!
        layoutBlocks(null)
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
            
            obj.put("text", tv.text.toString())
            obj.put("isBlank", tv.getTag(R.id.tag_is_blank) as? Boolean ?: false)
            obj.put("isChord", tv.getTag(R.id.tag_is_chord) as? Boolean ?: false)
            obj.put("forceNewLine", tv.getTag(R.id.tag_force_new_line) as? Boolean ?: false)
            jsonArray.put(obj)
        }
        
        val prefs = getSharedPreferences("StagePilotPrefs", MODE_PRIVATE)
        prefs.edit().putString("feature1_blocks_${currentUri.toString()}", jsonArray.toString()).apply()
        Toast.makeText(this, "Saved Test Feature 1 Layout!", Toast.LENGTH_SHORT).show()
    }

    private fun loadOrParsePdf(uri: Uri) {
        currentUri = uri
        val prefs = getSharedPreferences("StagePilotPrefs", MODE_PRIVATE)
        val savedJson = prefs.getString("feature1_blocks_$uri", null)
        
        if (savedJson != null) {
            restoreBlocksFromJson(savedJson)
        } else {
            parsePdfToBlocks(uri)
        }
    }

    private fun restoreBlocksFromJson(jsonStr: String) {
        tvStatus.text = "Loading Feature 1 save..."
        
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
                        val forceNewLine = if (obj.has("forceNewLine")) obj.getBoolean("forceNewLine") else false
                        
                        val block = if (isBlank) {
                            createBlankBlock()
                        } else {
                            createTextBlock(text, isChord)
                        }
                        
                        block.setTag(R.id.tag_force_new_line, forceNewLine)
                        
                        viewOrder.add(block)
                        container.addView(block)
                    }
                    
                    tvStatus.text = "Loaded ${viewOrder.size} saved blocks."
                    container.post { layoutBlocks(null) }
                }
            } catch (e: Exception) {
                Log.e("TestFeature1", "Error restoring JSON", e)
                withContext(Dispatchers.Main) { tvStatus.text = "Error loading save." }
            }
        }
    }

    private fun parsePdfToBlocks(uri: Uri) {
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

                    val parsedElements = mutableListOf<Pair<String, Boolean>>() 

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
                        container.removeAllViews()
                        viewOrder.clear()

                        val elementsToRender = parsedElements.take(500) 

                        for ((textStr, isChord) in elementsToRender) {
                            val wordBlock = createTextBlock(textStr, isChord)
                            viewOrder.add(wordBlock)
                            container.addView(wordBlock)
                        }

                        tvStatus.text = if (parsedElements.size > 500) "Loaded (Capped at 500)" else "Loaded ${parsedElements.size} blocks."
                        container.post { layoutBlocks(null) }
                    }
                }
            } catch (e: Exception) {
                Log.e("TestFeature1", "Error", e)
            }
        }
    }

    private fun createTextBlock(textStr: String, isChord: Boolean): TextView {
        return TextView(this).apply {
            text = textStr
            setTag(R.id.tag_is_blank, false)
            setTag(R.id.tag_is_chord, isChord)
            setTag(R.id.tag_force_new_line, false) // Default
            
            applyBlockStyle(this)
        }
    }

    private fun createBlankBlock(): TextView {
        return TextView(this).apply {
            text = " "
            setTag(R.id.tag_is_blank, true)
            setTag(R.id.tag_is_chord, false)
            setTag(R.id.tag_force_new_line, false)
            
            applyBlockStyle(this)
        }
    }

    private fun applyBlockStyle(block: TextView) {
        val isBlank = block.getTag(R.id.tag_is_blank) as? Boolean ?: false
        val isChord = block.getTag(R.id.tag_is_chord) as? Boolean ?: false
        
        var widthSpec: Int
        var heightSpec: Int

        if (isBlank) {
            block.setBackgroundColor(Color.TRANSPARENT)
            block.setPadding(0, 0, 0, 0)
            
            widthSpec = View.MeasureSpec.makeMeasureSpec(screenWidth - 32, View.MeasureSpec.EXACTLY)
            heightSpec = View.MeasureSpec.makeMeasureSpec(if (isPresentationMode) 16 else 32, View.MeasureSpec.EXACTLY)
            
            if (isPresentationMode) {
                block.setOnClickListener(null)
            } else {
                block.setOnClickListener { showBlockMenu(block) }
            }
        } else {
            if (isPresentationMode) {
                // Clear the heavy background
                block.background = null
                block.setBackgroundColor(Color.TRANSPARENT)
                
                if (isChord) {
                    block.setTextColor(Color.parseColor("#1565C0")) // A nice readable dark blue for chords
                    block.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    block.textSize = 18f
                } else {
                    block.setTextColor(Color.BLACK)
                    block.typeface = android.graphics.Typeface.DEFAULT
                    block.textSize = 16f
                }
                // Very tight padding so it looks like printed text
                block.setPadding(8, 4, 8, 4)
                
                // Disable interactivity!
                block.setOnClickListener(null)
                block.setOnTouchListener(null)
                
            } else {
                // Edit Mode: Bring back the colorful rounded blocks!
                val bg = ContextCompat.getDrawable(this, R.drawable.rounded_box)?.mutate() as? GradientDrawable
                if (isChord) {
                    val hsv = floatArrayOf(Random.nextFloat() * 60f + 200f, 0.9f, 0.9f)
                    bg?.setColor(Color.HSVToColor(hsv))
                    block.typeface = android.graphics.Typeface.DEFAULT_BOLD
                } else {
                    val hsv = floatArrayOf(0f, 0f, Random.nextFloat() * 0.2f + 0.3f)
                    bg?.setColor(Color.HSVToColor(hsv))
                    block.typeface = android.graphics.Typeface.DEFAULT
                }
                block.background = bg
                block.setTextColor(Color.WHITE)
                block.textSize = 14f
                block.setPadding(24, 16, 24, 16)
                
                // Re-enable interactivity
                block.setOnClickListener { showBlockMenu(block) }
            }
            
            widthSpec = View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.AT_MOST)
            heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        }

        // Remeasure explicitly because padding/text size just changed!
        block.measure(widthSpec, heightSpec)
        
        // Either update existing layout params or create new ones
        var params = block.layoutParams as? FrameLayout.LayoutParams
        if (params == null) {
            params = FrameLayout.LayoutParams(block.measuredWidth, block.measuredHeight)
        } else {
            params.width = block.measuredWidth
            params.height = block.measuredHeight
        }
        block.layoutParams = params
    }

    private fun showBlockMenu(block: TextView) {
        val popup = PopupMenu(this, block)
        popup.menu.add(0, 1, 0, "Drag Block")
        popup.menu.add(0, 2, 0, "Edit Block")
        popup.menu.add(0, 3, 0, "Delete Block")
        popup.menu.add(0, 4, 0, "Add Blank Row")
        popup.menu.add(0, 5, 0, "Add Text Block")
        
        // NEW FEATURES!
        popup.menu.add(0, 6, 0, "Combine With Next Block")
        
        val isForcedNewline = block.getTag(R.id.tag_force_new_line) as? Boolean ?: false
        popup.menu.add(0, 7, 0, if (isForcedNewline) "Remove Snap to New Line" else "Snap to New Line (Row Start)")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> enableDragMode(block)
                2 -> showEditDialog(block)
                3 -> deleteBlock(block)
                4 -> addBlankRow(block)
                5 -> showAddTextDialog(block)
                6 -> combineWithNext(block)
                7 -> toggleForceNewLine(block, !isForcedNewline)
            }
            true
        }
        popup.show()
    }

    private fun combineWithNext(block: TextView) {
        val index = viewOrder.indexOf(block)
        // Ensure there is actually a block physically after this one
        if (index != -1 && index < viewOrder.size - 1) {
            val nextBlock = viewOrder[index + 1] as TextView
            val newText = block.text.toString() + " " + nextBlock.text.toString()
            
            block.text = newText
            
            // Just in case they combine two chord blocks, check if the combined string still passes regex
            val isChord = newText.matches(chordRegex) || newText.startsWith("[")
            block.setTag(R.id.tag_is_chord, isChord)
            
            // Re-apply style to fix up padding/colors/measurements
            applyBlockStyle(block)
            
            // Destroy the absorbed block
            viewOrder.remove(nextBlock)
            container.removeView(nextBlock)
            
            layoutBlocks(null) 
        } else {
            Toast.makeText(this, "No block available to combine with!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleForceNewLine(block: TextView, force: Boolean) {
        block.setTag(R.id.tag_force_new_line, force)
        layoutBlocks(null)
    }

    private fun enableDragMode(block: TextView) {
        block.bringToFront()
        block.animate().scaleX(1.2f).scaleY(1.2f).alpha(0.8f).setDuration(150).start()
        block.setOnTouchListener(DragTouchListener())
        block.setOnClickListener(null)
    }

    private fun deleteBlock(block: TextView) {
        viewOrder.remove(block)
        container.removeView(block)
        layoutBlocks(null) 
    }

    private fun showEditDialog(block: TextView) {
        val input = EditText(this)
        input.setText(block.text)
        AlertDialog.Builder(this).setTitle("Edit Block").setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newText = input.text.toString().trim()
                block.text = newText
                val isChord = newText.matches(chordRegex) || newText.startsWith("[")
                block.setTag(R.id.tag_is_chord, isChord)
                applyBlockStyle(block)
                layoutBlocks(null)
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showAddTextDialog(afterBlock: TextView) {
        val input = EditText(this)
        input.hint = "Enter word or [Chord]"
        AlertDialog.Builder(this).setTitle("Add New Block").setView(input)
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
            .setNegativeButton("Cancel", null).show()
    }

    private fun addBlankRow(afterBlock: TextView) {
        val blankBlock = createBlankBlock()
        val index = viewOrder.indexOf(afterBlock)
        if (index != -1) {
            viewOrder.add(index + 1, blankBlock)
            container.addView(blankBlock)
            layoutBlocks(null)
        }
    }

    private fun layoutBlocks(draggingView: View?) {
        // Tighten the global padding up when in presentation mode
        var currentX = if (isPresentationMode) 8f else 16f
        var currentY = if (isPresentationMode) 8f else 16f
        val horizontalSpacing = if (isPresentationMode) 8f else 16f
        val verticalSpacing = if (isPresentationMode) 12f else 24f
        var maxLineHeight = 0
        
        // Track the true start X so we know when to carriage return
        val startX = if (isPresentationMode) 8f else 16f

        // First pass: We actually need to figure out the maximum height for the frame layout!
        var totalContainerHeight = 0f

        for (view in viewOrder) {
            val width = view.measuredWidth
            val height = view.measuredHeight
            val forceNewLine = view.getTag(R.id.tag_force_new_line) as? Boolean ?: false

            if (height > maxLineHeight) maxLineHeight = height

            // MATHEMATICS UPDATE: Carriage return if we exceed screen width OR if this block was strictly flagged to force a new row start!
            // Note: We check `currentX > startX` so we don't accidentally drop down an extra line if we're *already* at the start!
            if ((forceNewLine && currentX > startX) || currentX + width > screenWidth - startX) {
                currentX = startX
                currentY += maxLineHeight + verticalSpacing
                maxLineHeight = height
            }

            view.setTag(R.id.tag_target_x, currentX)
            view.setTag(R.id.tag_target_y, currentY)

            if (view != draggingView) {
                view.animate().x(currentX).y(currentY).setDuration(200).start()
            }

            currentX += width + horizontalSpacing
            
            // Keep track of the lowest Y point reached to size the ScrollView container
            if (currentY + maxLineHeight > totalContainerHeight) {
                totalContainerHeight = currentY + maxLineHeight
            }
        }
        
        // Expand the physics FrameLayout so the ScrollView knows how far down to let the user scroll
        var params = container.layoutParams
        if (params != null) {
            params.height = (totalContainerHeight + 200f).toInt() // Add 200px of extra breathing room at the bottom
            container.layoutParams = params
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
            if (bestIndex > currentIndex) bestIndex -= 1
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
                    view.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f)
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