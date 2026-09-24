package com.smsforwarder.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.smsforwarder.app.databinding.ActivityMainBinding
import com.smsforwarder.app.databinding.ItemFeatureBinding
import com.smsforwarder.app.databinding.ItemSetupBinding
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.serverUrl.setText(Prefs.serverUrl(this))
        b.topic.setText(Prefs.topic(this))
        b.token.setText(Prefs.token(this))
        b.version.text = "Pocket Relay ${packageManager.getPackageInfo(packageName, 0).versionName}"

        b.smsSwitch.setOnCheckedChangeListener { _, on -> Prefs.setSmsEnabled(this, on) }
        b.callSwitch.setOnCheckedChangeListener { _, on -> Prefs.setCallAlertsEnabled(this, on) }
        b.panelSwitch.setOnCheckedChangeListener { _, on ->
            Prefs.setPanelEnabled(this, on)
            if (on) PanelService.start(this) else PanelService.stop(this)
            refresh()
        }

        b.newPin.setOnClickListener { Prefs.newPanelPin(this); refresh() }
        b.panelAddress.setOnClickListener { copy(b.panelAddress.text.toString().lineSequence().firstOrNull().orEmpty()) }
        b.save.setOnClickListener { save(); refresh() }
        b.test.setOnClickListener { sendTest() }
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.panelEnabled(this)) PanelService.start(this)
        refresh()
    }

    // ---- state ---------------------------------------------------------------------

    private fun has(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun requiredPermissions(): List<String> {
        val list = mutableListOf(
            Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS, Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS,
        )
        if (Prefs.panelEnabled(this) && PanelFeatures.enabled(this, "send")) list.add(Manifest.permission.SEND_SMS)
        return list
    }

    private fun permissionsOk() = requiredPermissions().all { has(it) }

    private fun feature(key: String) = Prefs.panelEnabled(this) && PanelFeatures.enabled(this, key)

    private fun musicAccessOk() = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun batteryOk() =
        (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun accessibilityOk() =
        (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .contains("$packageName/${RemoteAccessibilityService::class.java.name}")

    private fun dndOk() =
        (getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).isNotificationPolicyAccessGranted

    private fun refresh() {
        val perms = permissionsOk()
        val battery = batteryOk()
        val server = Prefs.isConfigured(this)
        val panelOn = Prefs.panelEnabled(this)
        val access = accessibilityOk()

        val ready = perms && server && battery
        b.statusDot.background.mutate().setTint(ContextCompat.getColor(this, if (ready) R.color.ok else R.color.warn))
        b.statusTitle.text = if (ready) "All set" else "Setup needed"
        b.statusLast.text = lastSentText()

        // Setup checklist: shown until everything is done.
        val dnd = dndOk()
        val musicAccess = musicAccessOk()
        val extrasMissing = (feature("toggles") && !access) || (feature("sound") && !dnd) || (feature("music") && !musicAccess)
        val needSetup = !ready || extrasMissing
        b.setupCard.visibility = if (needSetup) android.view.View.VISIBLE else android.view.View.GONE
        while (b.setupList.childCount > 1) b.setupList.removeViewAt(1)
        if (needSetup) {
            addSetupRow("Permissions", perms, "Grant") { requestPermissions() }
            addSetupRow("Battery unrestricted", battery, "Fix") { requestBattery() }
            addSetupRow("Server settings", server, "Below") { b.serverUrl.requestFocus() }
            if (feature("toggles")) addSetupRow("Accessibility (Wi-Fi and data switches)", access, "Open") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            if (feature("sound")) addSetupRow("Do Not Disturb access (sound controls)", dnd, "Open") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
            if (feature("music")) addSetupRow("Notification access (music player)", musicAccess, "Open") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        b.smsSwitch.isChecked = Prefs.smsEnabled(this)
        b.callSwitch.isChecked = Prefs.callAlertsEnabled(this)
        b.panelSwitch.isChecked = panelOn
        b.panelDetails.visibility = if (panelOn) android.view.View.VISIBLE else android.view.View.GONE
        b.panelOffHint.visibility = if (panelOn) android.view.View.GONE else android.view.View.VISIBLE
        buildFeatureSwitches()
        if (panelOn) {
            val addresses = panelAddresses()
            b.panelAddress.text = if (addresses.isEmpty()) "Turn on the hotspot or join Wi-Fi to get an address"
            else addresses.joinToString("\n")
            b.panelPin.text = Prefs.panelPin(this)
            b.panelHint.text = "Tap the address to copy it. Remembered devices: ${Prefs.trustedCount(this)}"
        }
    }

    private fun buildFeatureSwitches() {
        b.featureList.removeAllViews()
        PanelFeatures.all.forEach { f ->
            val row = ItemFeatureBinding.inflate(LayoutInflater.from(this), b.featureList, false)
            row.featureSwitch.text = f.label
            row.featureSwitch.isChecked = PanelFeatures.enabled(this, f.key)
            row.featureSwitch.setOnCheckedChangeListener { _, on ->
                Prefs.setPanelFeature(this, f.key, on)
                refresh()
            }
            b.featureList.addView(row.root)
        }
    }

    private fun lastSentText(): String {
        val at = Prefs.lastSentAt(this)
        if (at == 0L) return "Nothing sent yet"
        val ago = DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        val title = Prefs.lastSentTitle(this)
        val body = Prefs.lastSentBody(this)
        return "Last sent $ago\n$title: $body"
    }

    private fun addSetupRow(label: String, done: Boolean, action: String, onClick: () -> Unit) {
        val row = ItemSetupBinding.inflate(LayoutInflater.from(this), b.setupList, false)
        row.label.text = label
        row.icon.setImageResource(if (done) R.drawable.ic_done else R.drawable.ic_todo)
        row.icon.setColorFilter(ContextCompat.getColor(this, if (done) R.color.ok else R.color.muted))
        if (done) row.action.visibility = android.view.View.GONE
        else { row.action.text = action; row.action.setOnClickListener { onClick() } }
        b.setupList.addView(row.root)
    }

    private fun panelAddresses(): List<String> =
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.name.startsWith("rmnet") }
            .flatMap { it.inetAddresses.toList() }
            .filter { it is Inet4Address && it.isSiteLocalAddress }
            .map { "http://${it.hostAddress}:${Prefs.panelPort(this)}" }

    // ---- actions -------------------------------------------------------------------

    private fun requestPermissions() {
        val perms = requiredPermissions().toMutableList()
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun requestBattery() {
        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
    }

    private fun save() {
        Prefs.save(this, b.serverUrl.text.toString(), b.topic.text.toString(), b.token.text.toString())
        b.serverUrl.setText(Prefs.serverUrl(this))
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
    }

    private fun sendTest() {
        save()
        if (!Prefs.isConfigured(this)) {
            Toast.makeText(this, "Set the server URL and topic first", Toast.LENGTH_SHORT).show()
            return
        }
        val data = Data.Builder()
            .putString(SmsForwardWorker.KEY_SENDER, "Pocket Relay")
            .putString(SmsForwardWorker.KEY_BODY, "Test notification from your Android phone.")
            .build()
        WorkManager.getInstance(this).enqueue(OneTimeWorkRequestBuilder<SmsForwardWorker>().setInputData(data).build())
        Toast.makeText(this, "Test sent", Toast.LENGTH_SHORT).show()
        b.statusLast.postDelayed({ refresh() }, 2500)
    }

    private fun copy(text: String) {
        if (text.isBlank() || !text.startsWith("http")) return
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("address", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }
}
