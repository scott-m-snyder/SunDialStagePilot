package com.sundial.stagepilot

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlin.random.Random

class TestBlocksActivity : Activity() {

    private lateinit var container: FrameLayout
    private val viewOrder = mutableListOf<View>()
    private var screenWidth = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_blocks)

        container = findViewById(R.id.draggableContainer)
        screenWidth = resources.displayMetrics.widthPixels

        // Generate 200 tiny words to completely fill the screen like a canvas
        val words = (1..200).map { listOf("C", "G", "Am", "F", "Verse", "Chorus", "Intro", "Solo").random() }

        for (word in words) {
            val wordBlock = TextView(this).apply {
                text = word
                textSize = 12f // Much smaller text
                setTextColor(Color.WHITE)
                
                // Random color for EVERY single block
                val bg = ContextCompat.getDrawable(context, R.drawable.rounded_box)?.mutate() as? GradientDrawable
                // Generate a random vibrant color using HSV
                val hsv = floatArrayOf(Random.nextFloat() * 360f, 0.8f, 0.9f)
                bg?.setColor(Color.HSVToColor(hsv))
                background = bg
                
                // Tighter padding for smaller blocks
                setPadding(20, 12, 20, 12)

                // Measure the block so we know its physical size before rendering it
                measure(
                    View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                
                // Wrap content using FrameLayout params
                layoutParams = FrameLayout.LayoutParams(measuredWidth, measuredHeight)

                setOnTouchListener(DragTouchListener())
            }

            // Track its logical order and add it to the UI
            viewOrder.add(wordBlock)
            container.addView(wordBlock)
        }

        // Layout the initial grid of blocks once the UI thread is ready
        container.post { layoutBlocks(null) }
    }

    /**
     * Iterates through the ordered list of views and calculates their physical X/Y grid coordinates,
     * wrapping to a new line if the block goes off the screen.
     */
    private fun layoutBlocks(draggingView: View?) {
        var currentX = 16f
        var currentY = 32f
        val horizontalSpacing = 12f // Tighter spacing
        val verticalSpacing = 16f   // Tighter spacing
        var maxLineHeight = 0

        for (view in viewOrder) {
            val width = view.measuredWidth
            val height = view.measuredHeight

            // Track the tallest block on this line so we know how far to drop the Y for the next line
            if (height > maxLineHeight) maxLineHeight = height

            // If this block exceeds the screen width, drop it to the next line (carriage return)
            if (currentX + width > screenWidth - 16f) {
                currentX = 16f
                currentY += maxLineHeight + verticalSpacing
                maxLineHeight = height // reset for the new line
            }

            // Save the target X/Y into the view's tags so we know where it *belongs* in the grid
            view.setTag(R.id.tag_target_x, currentX)
            view.setTag(R.id.tag_target_y, currentY)

            // If we are currently dragging this view, don't snap it! Let the finger control it.
            // But if it's NOT being dragged, animate it to its rightful grid position.
            if (view != draggingView) {
                view.animate()
                    .x(currentX)
                    .y(currentY)
                    .setDuration(200) // Smooth, satisfying magnetic snap
                    .start()
            }

            // Advance the cursor for the next block
            currentX += width + horizontalSpacing
        }
    }

    /**
     * Checks if the dragged block has moved close to another block's spot.
     * If so, it mathematically shifts the array and recalculates the layout!
     */
    private fun updateOrderWhileDragging(draggedView: View) {
        val centerX = draggedView.x + draggedView.width / 2
        val centerY = draggedView.y + draggedView.height / 2

        var bestIndex = -1
        var minDistance = Float.MAX_VALUE
        val currentIndex = viewOrder.indexOf(draggedView)

        // Find the absolute closest block to our dragged block
        for ((index, view) in viewOrder.withIndex()) {
            if (view == draggedView) continue

            val targetX = view.getTag(R.id.tag_target_x) as? Float ?: view.x
            val targetY = view.getTag(R.id.tag_target_y) as? Float ?: view.y
            val viewCenterX = targetX + view.width / 2
            val viewCenterY = targetY + view.height / 2

            val dx = centerX - viewCenterX
            val dy = centerY - viewCenterY
            // We use distance squared to avoid Math.sqrt overhead on every single touch move event
            val distance = dx * dx + dy * dy

            if (distance < minDistance) {
                minDistance = distance
                // If the dragged center is to the left of the closest block's center, 
                // we want to squeeze in *before* it. Otherwise, drop it *after* it.
                bestIndex = if (centerX < viewCenterX) index else index + 1
            }
        }

        // If we found a closer neighbor block, and the index is fundamentally different from our current index
        if (bestIndex != -1) {
            // Because removing the item from the list shifts all subsequent items down by 1...
            if (bestIndex > currentIndex) {
                bestIndex -= 1
            }

            // Only trigger a layout shift if our logical position in the flow actually changed
            if (bestIndex != currentIndex) {
                viewOrder.remove(draggedView)
                
                // Safety clamp
                if (bestIndex > viewOrder.size) bestIndex = viewOrder.size
                if (bestIndex < 0) bestIndex = 0
                
                viewOrder.add(bestIndex, draggedView)

                // Trigger the flow calculation! This makes the other blocks magically part ways.
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
                    
                    // Bring to front so it hovers OVER the other blocks
                    view.bringToFront()
                    
                    // Add a satisfying UI 'pop' effect when picking up the block
                    view.animate()
                        .scaleX(1.3f) // Exaggerate the scale a bit more for tiny blocks
                        .scaleY(1.3f)
                        .alpha(0.8f)
                        .setDuration(150)
                        .start()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Update the absolute position of the block to follow the finger
                    view.x = event.rawX + dX
                    view.y = event.rawY + dY
                    
                    // Check if we need to shift other blocks around out of the way!
                    updateOrderWhileDragging(view)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Pre-queue the scale/alpha properties to snap back to normal
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(1.0f)
                        // Do NOT call .start() here! layoutBlocks() will attach X/Y and call start() synchronously!
                        
                    // Passing null forces ALL blocks (including the one we just dropped) to snap to grid
                    // We use container.post to ensure layout happens safely at the start of the next frame
                    container.post { layoutBlocks(null) }
                    
                    view.performClick()
                    return true
                }
                else -> return false
            }
        }
    }
}