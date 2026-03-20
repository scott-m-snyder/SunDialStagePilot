package com.sundial.stagepilot.wear

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable

class MainActivity : FragmentActivity(), MessageClient.OnMessageReceivedListener, AmbientModeSupport.AmbientCallbackProvider {

    private lateinit var tvChartContent: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var btnShutdown: Button
    private lateinit var ambientController: AmbientModeSupport.AmbientController
    
    private var isReceivingRemoteScroll = false
    private var lastScrollSendTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Keep the screen on while the app is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Enable Ambient Mode (Always-On)
        ambientController = AmbientModeSupport.attach(this)

        tvChartContent = findViewById(R.id.tv_chart_content)
        scrollView = findViewById(R.id.scroll_view)
        btnShutdown = findViewById(R.id.btn_shutdown)

        btnShutdown.setOnClickListener {
            // Unhook from the watch's Significant Motion Sensor
            stopService(Intent(this, MotionService::class.java))
            // Immediately close the app and return to the watch face
            finishAffinity()
        }

        // Use a GestureDetector to pick up simple taps on the scrollview without
        // overriding its ability to scroll normally or the button's ability to be clicked!
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val screenHeight = resources.displayMetrics.heightPixels
                if (e.rawY > screenHeight / 2) {
                    // Touched bottom half -> Scroll Down one full page
                    scrollView.smoothScrollBy(0, screenHeight - 64)
                } else {
                    // Touched top half -> Scroll Up one full page
                    scrollView.smoothScrollBy(0, -(screenHeight - 64))
                }
                return true
            }
        })

        // Attach it to the ScrollView
        scrollView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            // Returning false lets the ScrollView and Buttons process the event natively as well!
            false
        }
        
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            if (!isReceivingRemoteScroll) {
                val child = scrollView.getChildAt(0)
                if (child != null) {
                    val maxScroll = child.height - scrollView.height
                    if (maxScroll > 0) {
                        val percentage = scrollView.scrollY.toFloat() / maxScroll.toFloat()
                        val now = System.currentTimeMillis()
                        // Throttle sending sync events to ~20 updates per second to avoid flooding Bluetooth
                        if (now - lastScrollSendTime > 50) {
                            lastScrollSendTime = now
                            sendScrollSyncToPhone(percentage)
                        }
                    }
                }
            }
        }
    }

    private fun sendScrollSyncToPhone(percentage: Float) {
        val messageClient = Wearable.getMessageClient(this)
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                messageClient.sendMessage(node.id, "/stagepilot/sync_scroll", percentage.toString().toByteArray())
                    .addOnSuccessListener { Log.d("Wear", "Sent scroll sync: $percentage") }
            }
        }
    }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback {
        return MyAmbientCallback()
    }

    private inner class MyAmbientCallback : AmbientModeSupport.AmbientCallback() {
        override fun onEnterAmbient(ambientDetails: Bundle?) {
            super.onEnterAmbient(ambientDetails)
            // Watch dropped: Dim screen to save battery
            tvChartContent.setTextColor(Color.LTGRAY)
            // Optional: disable anti-aliasing on text for burn-in protection, etc.
        }

        override fun onExitAmbient() {
            super.onExitAmbient()
            // Wrist raised: Restore full color and brightness
            tvChartContent.setTextColor(Color.WHITE)
        }

        override fun onUpdateAmbient() {
            super.onUpdateAmbient()
            // Optional: Update screen periodically while in ambient mode
        }
    }

    override fun onResume() {
        super.onResume()
        // Check for Google Play Services
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val connectionResult = googleApiAvailability.isGooglePlayServicesAvailable(this)
        if (connectionResult != ConnectionResult.SUCCESS) {
            tvChartContent.text = "Google Play Services not available."
            return
        }

        // Register the watch to listen for incoming charts from the phone
        Wearable.getMessageClient(this).addListener(this)
        
        // Actively ping the phone the moment the watch app opens to ask "What is currently on your screen?"
        requestCurrentChartFromPhone()
    }

    override fun onPause() {
        super.onPause()
        // Stop listening to save battery when the watch app is backgrounded
        Wearable.getMessageClient(this).removeListener(this)
    }

    private fun requestCurrentChartFromPhone() {
        val nodeClient = Wearable.getNodeClient(this)
        val messageClient = Wearable.getMessageClient(this)

        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    tvChartContent.text = "No phone connected.\nOpen StagePilot on your phone."
                    return@addOnSuccessListener
                }
                
                tvChartContent.text = "Syncing with phone..."
                
                for (node in nodes) {
                    // Send a tiny empty payload to the specific "request_chart" path
                    messageClient.sendMessage(node.id, "/stagepilot/request_chart", ByteArray(0))
                        .addOnSuccessListener {
                            Log.d("Wear", "Requested chart from ${node.displayName}")
                        }
                        .addOnFailureListener { e ->
                            Log.e("Wear", "Failed to request chart", e)
                            tvChartContent.text = "Failed to reach phone."
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("Wear", "Failed to get connected nodes", e)
                tvChartContent.text = "Error finding phone."
            }
    }

    // This triggers automatically whenever the Z Fold sends a message over Bluetooth/WiFi
    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            "/stagepilot/load_chart" -> {
                // The phone has sent us a raw string of the entire parsed song chart!
                val chartText = String(messageEvent.data)
                
                runOnUiThread {
                    tvChartContent.text = chartText
                    
                    // Reset the scroll back to the very top for the new song!
                    scrollView.scrollTo(0, 0)
                }
                
                Log.d("Wear", "Successfully loaded new chart from Z Fold.")
            }
            "/stagepilot/sync_scroll" -> {
                val percentage = String(messageEvent.data).toFloatOrNull() ?: 0f
                runOnUiThread {
                    isReceivingRemoteScroll = true
                    val child = scrollView.getChildAt(0)
                    if (child != null) {
                        val maxScroll = child.height - scrollView.height
                        if (maxScroll > 0) {
                            val targetY = (percentage * maxScroll).toInt()
                            scrollView.scrollTo(0, targetY)
                        }
                    }
                    // Release the lock slightly after to prevent echo loops
                    scrollView.postDelayed({ isReceivingRemoteScroll = false }, 50)
                }
            }
        }
    }
}