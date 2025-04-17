package com.sms_spammer.plus.repository

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import android.os.Build
import androidx.core.content.ContextCompat
import com.sms_spammer.plus.SmsApplication
import android.content.IntentFilter

sealed class SmsResult {
    data class Success(val messageId: String) : SmsResult()
    data class Error(val message: String) : SmsResult()
}

class SmsRepository(private val context: Context) {
    private val smsManager: SmsManager by lazy {
        context.getSystemService(SmsManager::class.java)
    }

    private val subscriptionManager: SubscriptionManager by lazy {
        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
    }

    suspend fun sendSms(phoneNumber: String, message: String): SmsResult = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                sendSmsModern(phoneNumber, message)
            } else {
                sendSmsLegacy(phoneNumber, message)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error sending SMS: ${e.message}")
            SmsResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun sendSmsModern(phoneNumber: String, message: String): SmsResult {
        val subscriptionId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            subscriptionManager.activeSubscriptionInfoList.firstOrNull()?.subscriptionId
                ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
        } else {
            SubscriptionManager.getDefaultSubscriptionId()
        }

        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
            } else {
                context.getSystemService(SmsManager::class.java)
            }
        } else {
            context.getSystemService(SmsManager::class.java)
        }

        val parts = smsManager.divideMessage(message)
        
        val sentIntents = ArrayList<PendingIntent>()
        val deliveryIntents = ArrayList<PendingIntent>()

        parts.indices.forEach { index ->
            sentIntents.add(
                PendingIntent.getBroadcast(
                    context,
                    index,
                    Intent("SMS_SENT").apply {
                        putExtra("message_id", index)
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )

            deliveryIntents.add(
                PendingIntent.getBroadcast(
                    context,
                    index,
                    Intent("SMS_DELIVERED").apply {
                        putExtra("message_id", index)
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }

        smsManager.sendMultipartTextMessage(
            phoneNumber,
            null,
            parts,
            sentIntents,
            deliveryIntents
        )
        return SmsResult.Success("Message sent successfully")
    }

    private fun sendSmsLegacy(phoneNumber: String, message: String): SmsResult {
        val sentIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent("SMS_SENT"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val deliveredIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent("SMS_DELIVERED"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        smsManager.sendTextMessage(
            phoneNumber,
            null,
            message,
            sentIntent,
            deliveredIntent
        )
        return SmsResult.Success("Message sent successfully")
    }
} 