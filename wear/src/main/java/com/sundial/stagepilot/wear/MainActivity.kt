package com.sundial.stagepilot.wear

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable

class MainActivity : Activity(), MessageClient.OnMessageReceivedListener {

    private lateinit var tvChartContent: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var touchOverlay: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvChartContent = findViewById(R.id.tv_chart_content)
        scrollView = findViewById(R.id.scroll_view)
        touchOverlay = findViewById(R.id.touch_overlay)

        // Implement touch zones on the watch face
        // Tapping the bottom half scrolls down, tapping the top half scrolls up!
        touchOverlay.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val screenHeight = resources.displayMetrics.heightPixels
                if (event.rawY > screenHeight / 2) {
                    // Touched bottom half -> Scroll Down one full page
                    scrollView.smoothScrollBy(0, screenHeight - 64)
                } else {
                    // Touched top half -> Scroll Up one full page
                    scrollView.smoothScrollBy(0, -(screenHeight - 64))
                }
            }
            true // Consume the event so it doesn't trigger other things
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
            "/stagepilot/remote_scroll_down" -> {
                // The phone told US to scroll down automatically
                runOnUiThread {
                    val screenHeight = resources.displayMetrics.heightPixels
                    scrollView.smoothScrollBy(0, screenHeight - 64)
                }
            }
        }
    }
}