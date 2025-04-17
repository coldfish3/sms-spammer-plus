package com.sms_spammer.plus

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.sms_spammer.plus.databinding.ActivityMainBinding
import com.sms_spammer.plus.repository.SmsResult
import com.sms_spammer.plus.viewmodel.SmsViewModel
import com.sms_spammer.plus.viewmodel.SmsViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: SmsViewModel
    private lateinit var smsSender: SmsSender
    private var isSending = false
    private var currentJob: Job? = null
    private var messagesSentCount = 0
    private var targetMessageCount = 0
    private var permissionCheckJob: Job? = null
    private var permissionDialog: AlertDialog? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResult(permissions)
    }

    private val pickContact = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        handleContactPickResult(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize view model
        viewModel = ViewModelProvider(this, SmsViewModelFactory(this))[SmsViewModel::class.java]
        smsSender = SmsSender(viewModel) { sending ->
            isSending = sending
            updateUIState()
        }

        // Setup UI components
        setupContactPicker()
        setupStopButton()
        setupClickListeners()
        setupObservers()
        updateUIState()

        // Start periodic permission and limit checks
        startPeriodicChecks()

        // Check permissions last
        checkSmsPermissions()
    }

    private fun handlePermissionResult(permissions: Map<String, Boolean>) {
        val allGranted = permissions.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "Some permissions were denied. SMS functionality may not work properly.", Toast.LENGTH_LONG).show()
        }
        updatePermissionWarnings()
    }

    private fun checkSmsPermissions() {
        val permissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.POST_NOTIFICATIONS
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            if (shouldShowRequestPermissionRationale(permissionsToRequest[0])) {
                showSmsPermissionExplanationDialog(permissionsToRequest)
            } else {
                requestPermissionLauncher.launch(permissionsToRequest)
            }
        }
        updatePermissionWarnings()
    }

    private fun updatePermissionWarnings() {
        val smsPermissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )

        val hasSmsPermissions = smsPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

        // Hide Android 15+ warning if SMS permissions are granted
        binding.permissionWarningText.visibility = if (hasSmsPermissions) View.GONE else View.VISIBLE
    }

    private fun showSmsPermissionExplanationDialog(permissions: Array<String>) {
        permissionDialog = AlertDialog.Builder(this)
            .setTitle("SMS Permissions Required")
            .setMessage(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                getString(R.string.permission_warning)
            } else {
                "This app needs SMS permissions to send messages. Please grant the permissions to continue."
            })
            .setPositiveButton("OK") { _, _ ->
                requestPermissionLauncher.launch(permissions)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "SMS functionality will not work without permissions", Toast.LENGTH_LONG).show()
            }
            .create()
            .also { it.show() }
    }

    private fun handleContactPickResult(uri: Uri?) {
        uri?.let {
            val contentResolver = contentResolver
            val cursor = contentResolver.query(
                uri,
                arrayOf(ContactsContract.Contacts._ID),
                null,
                null,
                null
            )
            
            cursor?.use { contactCursor ->
                if (contactCursor.moveToFirst()) {
                    val contactId = contactCursor.getString(
                        contactCursor.getColumnIndex(ContactsContract.Contacts._ID)
                    )
                    
                    val phoneCursor = contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId),
                        null
                    )
                    
                    phoneCursor?.use { phoneNumberCursor ->
                        if (phoneNumberCursor.moveToFirst()) {
                            val number = phoneNumberCursor.getString(
                                phoneNumberCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            )
                            binding.phoneNumberEditText.setText(number)
                        } else {
                            Toast.makeText(this, "No phone number found for this contact", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun setupContactPicker() {
        binding.contactPickerButton.setOnClickListener { _: View ->
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    pickContact.launch(null)
                }
                shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) -> {
                    showContactPermissionExplanationDialog()
                }
                else -> {
                    requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                }
            }
        }
    }

    private fun setupStopButton() {
        binding.stopButton.setOnClickListener { _: View ->
            stopSending()
        }
    }

    private fun stopSending() {
        smsSender.stopSending()
        updateUIState()
    }

    private fun updateUIState() {
        binding.sendButton.isEnabled = !isSending
        binding.stopButton.isEnabled = isSending
        binding.phoneNumberEditText.isEnabled = !isSending
        binding.messageEditText.isEnabled = !isSending
        binding.messageCountEditText.isEnabled = !isSending
        binding.contactPickerButton.isEnabled = !isSending
        
        // Update messages count display
        val countText = if (targetMessageCount == 0 && binding.messageCountEditText.text.toString() == "0") {
            getString(R.string.messages_sent_infinite, messagesSentCount)
        } else {
            getString(R.string.messages_sent, messagesSentCount, targetMessageCount)
        }
        binding.txtMessages.text = countText
    }

    private fun setupClickListeners() {
        binding.sendButton.setOnClickListener { _: View ->
            handleSendButtonClick()
        }
    }

    private fun handleSendButtonClick() {
        val phoneNumber = binding.phoneNumberEditText.text.toString()
        val message = binding.messageEditText.text.toString()
        val countText = binding.messageCountEditText.text.toString()
        val count = if (countText.isBlank()) 1 else countText.toIntOrNull() ?: 1

        if (phoneNumber.isBlank() || message.isBlank()) {
            Toast.makeText(this, "Please enter both phone number and message", Toast.LENGTH_SHORT).show()
            return
        }

        // Reset counter when starting a new sending session
        messagesSentCount = 0
        targetMessageCount = count
        updateUIState()

        // Check SMS permissions before sending
        val smsPermissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )

        if (smsPermissions.all { permission ->
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            }) {
            smsSender.startSending(phoneNumber, message, count)
        } else {
            showSmsPermissionExplanationDialog(smsPermissions)
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.smsState.collectLatest { state ->
                binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                binding.sendButton.isEnabled = !state.isLoading
                
                state.error?.let { error ->
                    Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
                }
                
                if (state.success) {
                    messagesSentCount++
                    updateUIState()
                    Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startPeriodicChecks() {
        permissionCheckJob = lifecycleScope.launch {
            while (true) {
                updatePermissionWarnings()
                checkSmsLimit()
                
                // Check if all permissions are granted and dismiss dialog if they are
                val smsPermissions = arrayOf(
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
                )
                
                if (smsPermissions.all { permission ->
                        ContextCompat.checkSelfPermission(this@MainActivity, permission) == PackageManager.PERMISSION_GRANTED
                    }) {
                    permissionDialog?.dismiss()
                    permissionDialog = null
                }
                
                delay(100) // Check every 0.1 seconds
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        permissionCheckJob?.cancel()
        stopSending()
    }

    override fun onResume() {
        super.onResume()
        // Check SMS limit when app resumes
        checkSmsLimit()
        
        // Check SMS permissions and show warning if not granted
        val smsPermissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        
        if (!smsPermissions.all { permission ->
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            }) {
            showSmsPermissionExplanationDialog(smsPermissions)
        }
    }

    private fun checkSmsLimit() {
        try {
            val smsLimit = android.provider.Settings.Global.getInt(contentResolver, "sms_outgoing_check_max_count")
            binding.smsLimitWarningText.visibility = if (smsLimit >= 1000) View.GONE else View.VISIBLE
        } catch (e: Exception) {
            // If we can't read the setting, show the warning
            binding.smsLimitWarningText.visibility = View.VISIBLE
        }
    }

    private fun showContactPermissionExplanationDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Contact Permission Required")
            .setMessage("This app needs contact permission to let you select phone numbers from your contacts. The app will only use this permission to read the phone numbers you select.")
            .setPositiveButton("Grant Permission") { _, _ ->
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
} 