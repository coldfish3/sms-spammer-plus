package com.sms_spammer.plus

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.widget.Toast
import timber.log.Timber

class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getIntExtra("message_id", -1)
        Timber.d("SMS sent: message_id=$messageId")
        when (resultCode) {
            Activity.RESULT_OK -> {
                Timber.d("SMS sent successfully")
            }
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                Timber.e("Generic failure sending SMS")
                Toast.makeText(context, "Failed to send SMS", Toast.LENGTH_SHORT).show()
            }
            SmsManager.RESULT_ERROR_NO_SERVICE -> {
                Timber.e("No service available")
                Toast.makeText(context, "No service available", Toast.LENGTH_SHORT).show()
            }
            SmsManager.RESULT_ERROR_NULL_PDU -> {
                Timber.e("Null PDU")
                Toast.makeText(context, "Null PDU", Toast.LENGTH_SHORT).show()
            }
            SmsManager.RESULT_ERROR_RADIO_OFF -> {
                Timber.e("Radio off")
                Toast.makeText(context, "Radio off", Toast.LENGTH_SHORT).show()
            }
        }
    }
} 