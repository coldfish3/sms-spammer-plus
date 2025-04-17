package com.sms_spammer.plus.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sms_spammer.plus.repository.SmsRepository

class SmsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SmsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SmsViewModel(SmsRepository(context)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 