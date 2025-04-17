package com.sms_spammer.plus

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import com.sms_spammer.plus.viewmodel.SmsViewModel

class SmsSender(
    private val viewModel: SmsViewModel,
    private val onStateChange: (Boolean) -> Unit
) {
    private var job: Job? = null
    private var isSending = false

    fun startSending(phoneNumber: String, message: String, count: Int) {
        if (isSending) return
        
        isSending = true
        onStateChange(isSending)
        
        val scope = CoroutineScope(Dispatchers.IO)
        job = scope.launch {
            try {
                var messagesSent = 0
                while (isSending && (count == 0 || messagesSent < count)) {
                    withContext(Dispatchers.Main) {
                        viewModel.sendSms(phoneNumber, message)
                    }
                    messagesSent++
                    delay(1000) // 1 second delay between messages
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isSending = false
                    onStateChange(isSending)
                }
            }
        }
    }

    fun stopSending() {
        job?.cancel()
        isSending = false
        onStateChange(isSending)
    }
} 