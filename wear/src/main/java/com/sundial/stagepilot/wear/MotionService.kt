package com.sundial.stagepilot.wear

import android.app.Service
import android.content.Intent
import android.os.IBinder

class MotionService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unhooking from the significant motion sensor logic would go here.
    }
}