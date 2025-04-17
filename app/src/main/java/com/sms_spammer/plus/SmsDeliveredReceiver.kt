package com.sms_spammer.plus

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import timber.log.Timber

class SmsDeliveredReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getIntExtra("message_id", -1)
        Timber.d("SMS delivered: message_id=$messageId")
        when (resultCode) {
            Activity.RESULT_OK -> {
                Timber.d("SMS delivered successfully")
            }
            else -> {
                Timber.e("SMS delivery failed")
                Toast.makeText(context, "SMS delivery failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
} 