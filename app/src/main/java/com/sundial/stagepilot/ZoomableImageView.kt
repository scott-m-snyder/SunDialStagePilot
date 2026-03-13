package com.sundial.stagepilot

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView

class ZoomableImageView(context: Context, attrs: AttributeSet?) : ImageView(context, attrs) {

    private var mMatrix = Matrix()
    private var mode = NONE

    private var last = PointF()
    private var start = PointF()
    private var minScale = 0.5f
    private var maxScale = 20f
    private var m: FloatArray = FloatArray(9)

    private var viewWidth = 0
    private var viewHeight = 0
    private var saveScale = 1f // Reset to 1x (fit-to-width)
    private var origWidth = 0f
    private var origHeight = 0f

    private var mScaleDetector: ScaleGestureDetector
    private var mGestureDetector: GestureDetector

    companion object {
        const val NONE = 0
        const val DRAG = 1
        const val ZOOM = 2
    }

    init {
        super.setClickable(true)
        mScaleDetector = ScaleGestureDetector(context, ScaleListener())
        mGestureDetector = GestureDetector(context, GestureListener())
        mMatrix = Matrix()
        imageMatrix = mMatrix
        scaleType = ScaleType.MATRIX
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        mScaleDetector.onTouchEvent(event)
        mGestureDetector.onTouchEvent(event)
        
        val curr = PointF(event.x, event.y)

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                last.set(curr)
                start.set(last)
                mode = DRAG
            }
            MotionEvent.ACTION_MOVE -> if (mode == DRAG) {
                // Horizontal scroll is locked: deltaX is forced to 0
                val deltaY = curr.y - last.y
                mMatrix.postTranslate(0f, deltaY)
                fixTrans()
                last.set(curr.x, curr.y)
            }
            MotionEvent.ACTION_UP -> {
                mode = NONE
            }
            MotionEvent.ACTION_POINTER_UP -> mode = NONE
        }

        imageMatrix = mMatrix
        invalidate()
        return true
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            mode = ZOOM
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            var mScaleFactor = detector.scaleFactor
            val origScale = saveScale
            saveScale *= mScaleFactor
            if (saveScale > maxScale) {
                saveScale = maxScale
                mScaleFactor = maxScale / origScale
            } else if (saveScale < minScale) {
                saveScale = minScale
                mScaleFactor = minScale / origScale
            }

            if (origWidth * getFullScale() <= viewWidth || origHeight * getFullScale() <= viewHeight) {
                mMatrix.postScale(mScaleFactor, mScaleFactor, viewWidth / 2f, viewHeight / 2f)
            } else {
                mMatrix.postScale(mScaleFactor, mScaleFactor, detector.focusX, detector.focusY)
            }

            fixTrans()
            return true
        }
    }

    private fun getFullScale(): Float {
        return saveScale * (viewWidth.toFloat() / origWidth)
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val targetScale = if (saveScale > 1f) 1f else 2.5f
            val factor = targetScale / saveScale
            saveScale = targetScale
            mMatrix.postScale(factor, factor, e.x, e.y)
            fixTrans()
            imageMatrix = mMatrix
            invalidate()
            return true
        }
    }

    private fun fixTrans() {
        mMatrix.getValues(m)
        val transX = m[Matrix.MTRANS_X]
        val transY = m[Matrix.MTRANS_Y]
        
        val fullScale = getFullScale()
        val contentWidth = origWidth * fullScale
        val contentHeight = origHeight * fullScale
        
        // Horizontal fix: always center horizontally if horizontal scroll is locked
        val fixTransX = (viewWidth - contentWidth) / 2f - transX
        
        val fixTransY = getFixTrans(transY, viewHeight.toFloat(), contentHeight)
        
        if (fixTransX != 0f || fixTransY != 0f) {
            mMatrix.postTranslate(fixTransX, fixTransY)
        }
    }

    private fun getFixTrans(trans: Float, viewSize: Float, contentSize: Float): Float {
        val minTrans: Float
        val maxTrans: Float
        if (contentSize <= viewSize) {
            minTrans = 0f
            maxTrans = viewSize - contentSize
        } else {
            minTrans = viewSize - contentSize
            maxTrans = 0f
        }
        if (trans < minTrans) return -trans + minTrans
        if (trans > maxTrans) return -trans + maxTrans
        return 0f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        viewWidth = MeasureSpec.getSize(widthMeasureSpec)
        viewHeight = MeasureSpec.getSize(heightMeasureSpec)
        
        if (origWidth == 0f || origHeight == 0f) return
        
        mMatrix.reset()
        val baseScale = viewWidth.toFloat() / origWidth
        val currentScale = baseScale * saveScale
        mMatrix.setScale(currentScale, currentScale)

        val redundantXSpace = (viewWidth.toFloat() - currentScale * origWidth) / 2f
        mMatrix.postTranslate(redundantXSpace, 0f)
        fixTrans()
        imageMatrix = mMatrix
    }

    fun setBitmapSize(width: Int, height: Int) {
        origWidth = width.toFloat()
        origHeight = height.toFloat()
        saveScale = 1f // Reset to 1x fit-to-width
        requestLayout()
        invalidate()
    }
}