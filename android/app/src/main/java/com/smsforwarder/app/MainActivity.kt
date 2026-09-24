package com.smsforwarder.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class MainActivity : AppCompatActivity() {

    private lateinit var serverUrlInput: EditText
    private lateinit var topicInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var statusLabel: TextView

    private val requestPermissionsLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) {
            updateStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        loadPrefs()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun buildUi(): ScrollView {
        val pad = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        statusLabel = TextView(this).apply { textSize = 14f }
        root.addView(statusLabel)
        root.addView(spacer())

        val grantButton = Button(this).apply {
            text = "Grant permissions (SMS, calls, contacts)"
            setOnClickListener { requestSmsPermissions() }
        }
        root.addView(grantButton)

        val batteryButton = Button(this).apply {
            text = "Disable battery optimization"
            setOnClickListener { requestIgnoreBatteryOptimizations() }
        }
        root.addView(batteryButton)
        root.addView(spacer())

        root.addView(label("ntfy server URL (e.g. https://ntfy.example.com)"))
        serverUrlInput = EditText(this)
        root.addView(serverUrlInput)

        root.addView(label("Topic"))
        topicInput = EditText(this)
        root.addView(topicInput)

        root.addView(label("Access token (from ntfy token add)"))
        tokenInput = EditText(this)
        root.addView(tokenInput)
        root.addView(spacer())

        val callSwitch = android.widget.Switch(this).apply {
            text = "Alert me when a call comes in"
            isChecked = Prefs.callAlertsEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked -> Prefs.setCallAlertsEnabled(this@MainActivity, checked) }
        }
        root.addView(callSwitch)
        root.addView(spacer())

        val saveButton = Button(this).apply {
            text = "Save settings"
            setOnClickListener { savePrefs() }
        }
        root.addView(saveButton)

        val testButton = Button(this).apply {
            text = "Send test notification"
            setOnClickListener { sendTest() }
        }
        root.addView(testButton)

        return ScrollView(this).apply { addView(root) }
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 4)
    }

    private fun spacer() = TextView(this).apply {
        setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
    }

    private fun loadPrefs() {
        serverUrlInput.setText(Prefs.serverUrl(this))
        topicInput.setText(Prefs.topic(this))
        tokenInput.setText(Prefs.token(this))
    }

    private fun savePrefs() {
        Prefs.save(this, serverUrlInput.text.toString(), topicInput.text.toString(), tokenInput.text.toString())
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun requestSmsPermissions() {
        requestPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS,
                Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS,
            )
        )
    }

    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:$packageName")
            })
        } else {
            Toast.makeText(this, "Already unrestricted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasSmsPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    private fun hasCallPermissions(): Boolean =
        listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS)
            .all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    private fun updateStatus() {
        val perms = if (hasSmsPermissions()) "granted" else "NOT granted"
        val callPerms = if (hasCallPermissions()) "granted" else "NOT granted"
        val configured = if (Prefs.isConfigured(this)) "configured" else "NOT configured"
        statusLabel.text = "SMS permissions: $perms\nCall alert permissions: $callPerms\nServer: $configured"
    }

    private fun sendTest() {
        savePrefs()
        if (!Prefs.isConfigured(this)) {
            Toast.makeText(this, "Set server URL and topic first", Toast.LENGTH_SHORT).show()
            return
        }
        val data = Data.Builder()
            .putString(SmsForwardWorker.KEY_SENDER, "SMS Forwarder")
            .putString(SmsForwardWorker.KEY_BODY, "Test notification from your Android phone.")
            .build()
        val request = OneTimeWorkRequestBuilder<SmsForwardWorker>().setInputData(data).build()
        WorkManager.getInstance(this).enqueue(request)
        Toast.makeText(this, "Test queued", Toast.LENGTH_SHORT).show()
    }
}
