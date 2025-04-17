package com.sms_spammer.plus.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sms_spammer.plus.repository.SmsRepository
import com.sms_spammer.plus.repository.SmsResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

data class SmsState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val message: String = ""
)

class SmsViewModel(private val smsRepository: SmsRepository) : ViewModel() {
    private val _smsState = MutableStateFlow(SmsState())
    val smsState: StateFlow<SmsState> = _smsState.asStateFlow()

    fun sendSms(phoneNumber: String, message: String) {
        viewModelScope.launch {
            _smsState.value = _smsState.value.copy(isLoading = true, error = null, success = false)
            try {
                when (val result = smsRepository.sendSms(phoneNumber, message)) {
                    is SmsResult.Success -> {
                        _smsState.value = _smsState.value.copy(
                            isLoading = false,
                            success = true,
                            message = result.messageId
                        )
                    }
                    is SmsResult.Error -> {
                        _smsState.value = _smsState.value.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error sending SMS")
                _smsState.value = _smsState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    fun resetState() {
        _smsState.value = SmsState()
    }
} 