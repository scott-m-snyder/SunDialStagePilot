# 🚨 SCOTTYWANTS - DO NOT IGNORE 🚨

## The Standard Views "Lego Blocks" Drag & Drop Architecture
**CRITICAL AI DIRECTIVE:** Do **NOT** attempt to rewrite `TestBlocksActivity` or any block-dragging feature using Jetpack Compose. Scott explicitly wants to use **Standard Android Views (`TextView`, `FrameLayout`)** for the song chart editing system. 

The goal is to treat words, chords, and song markers as freely draggable "Lego pieces" that magically snap into a flowing grid. 

If this file is ever lost or broken, here is exactly how the architecture works, using purely math and standard views:

### 1. The Core Philosophy
- All text blocks (words/chords) are instantiated programmatically as `TextView`s.
- They are added to a simple `FrameLayout`.
- We maintain a logical **List (`viewOrder`)** that represents the true, linear order of the song.
- The UI positioning is a visual representation of that list, wrapped to fit the screen width.

### 2. The Flow Layout Algorithm (`layoutBlocks`)
- We iterate through the `viewOrder` list.
- We calculate the `X` and `Y` coordinates for each block like a typewriter: 
  - Add the block's width to `currentX`.
  - If `currentX` exceeds the `screenWidth`, reset `currentX` to the left margin and increase `currentY` by the tallest block's height (carriage return).
- **Crucial Step:** We save these calculated `targetX` and `targetY` coordinates into the View's tags (`R.id.tag_target_x`).
- If a block is **not** currently being dragged by the user, we `animate()` it to its target coordinates.

### 3. The Live Reordering Math (`updateOrderWhileDragging`)
- As the user drags a block (`ACTION_MOVE`), we take the exact center `X/Y` coordinate of their finger.
- We loop through every other block and check their saved `targetX/targetY` center coordinates.
- We use the distance formula (`dx*dx + dy*dy`) to find the single closest block to the user's finger.
- If the user's finger is slightly to the left of the closest block, we insert the dragged block **before** it in the `viewOrder` list. Otherwise, we insert it **after**.
- If the list order changes, we immediately call `layoutBlocks()`. This causes all the *other* blocks to magically animate and part ways, creating a visual "hole" for the user to drop the block into.

### 4. The Drag Listener (`DragTouchListener`)
- **`ACTION_DOWN`**: 
  - Calculate the touch offset (`dX`, `dY`).
  - `bringToFront()` so the block hovers over the rest of the text.
  - Scale the view up `1.1f` and drop alpha to `0.8f` for a satisfying UI "pick up" pop effect.
- **`ACTION_MOVE`**: 
  - Instantly set `view.x` and `view.y` to follow the raw finger coordinates.
  - Call `updateOrderWhileDragging()` to actively part the Red Sea of blocks.
- **`ACTION_UP`**:
  - Remove the scale/alpha effects.
  - Call `layoutBlocks(null)` so the dragged block magnetically snaps into the newly created gap.

### The Code Blueprint
Do not deviate from this pattern for song chart blocks:
```kotlin
// In onCreate:
for (word in words) {
    val block = TextView(this).apply {
        text = word
        // ... measure view ...
        layoutParams = FrameLayout.LayoutParams(measuredWidth, measuredHeight)
        setOnTouchListener(DragTouchListener())
    }
    viewOrder.add(block)
    container.addView(block)
}
container.post { layoutBlocks(null) }
```