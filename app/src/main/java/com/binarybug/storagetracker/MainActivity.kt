package com.binarybug.storagetracker

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.binarybug.storagetracker.data.Prefs
import com.binarybug.storagetracker.data.SnapshotStore
import com.binarybug.storagetracker.scan.AlertTarget
import com.binarybug.storagetracker.scan.Forecast
import com.binarybug.storagetracker.scan.Report
import com.binarybug.storagetracker.scan.ScanController
import com.binarybug.storagetracker.ui.Actions
import com.binarybug.storagetracker.ui.AppsScreen
import com.binarybug.storagetracker.ui.BrowseScreen
import com.binarybug.storagetracker.ui.ChangesScreen
import com.binarybug.storagetracker.ui.Fmt
import com.binarybug.storagetracker.ui.MATCH
import com.binarybug.storagetracker.ui.OverviewScreen
import com.binarybug.storagetracker.ui.Palette
import com.binarybug.storagetracker.ui.Screen
import com.binarybug.storagetracker.ui.StatusScreen
import com.binarybug.storagetracker.ui.WRAP
import com.binarybug.storagetracker.ui.dp
import com.binarybug.storagetracker.ui.hbox
import com.binarybug.storagetracker.ui.label
import com.binarybug.storagetracker.ui.lp
import com.binarybug.storagetracker.ui.vbox
import com.binarybug.storagetracker.work.Notifier
import com.binarybug.storagetracker.work.ScanTileService
import com.binarybug.storagetracker.work.Scheduler

/** Hosts the four screens (bottom navigation), the scan button at the top right, and the live progress banner. */
class MainActivity : Activity(), Actions, ScanController.Listener {
    private lateinit var p: Palette
    private lateinit var frame: FrameLayout
    private lateinit var banner: LinearLayout
    private lateinit var bannerText: TextView
    private lateinit var screens: Array<Screen>
    private lateinit var browse: BrowseScreen
    private val navItems = ArrayList<LinearLayout>()
    private val titles = listOf("Overview", "Apps", "Changes", "Browse", "Status")
    private val icons = listOf(R.drawable.ic_overview, R.drawable.ic_apps, R.drawable.ic_changes, R.drawable.ic_browse, R.drawable.ic_status)
    private var tab = 0
    private var wasRunning = false
    companion object {
        /** Notification taps put an [AlertTarget] (as text) here so the app opens the screen the alert is about. */
        const val EXTRA_TARGET = "alert_target"
        private const val REQ_EXPORT = 21
        private const val REQ_IMPORT = 22
    }

    /** A Browse folder to open once the snapshots have loaded (a notification can start the app cold). */
    private var pendingTarget: AlertTarget? = null
    private val modelListener: () -> Unit = {
        pendingTarget?.let { if (Model.comparison != null) { pendingTarget = null; open(it) } }
        screens.forEach { it.refresh() }
        updateBanner()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Palette(this)
        tab = savedInstanceState?.getInt("tab") ?: 0
        browse = BrowseScreen(this, p, this)
        screens = arrayOf(OverviewScreen(this, p, this), AppsScreen(this, p, this), ChangesScreen(this, p, this), browse, StatusScreen(this, p, this))

        val root = vbox().apply { setBackgroundColor(p.bg) }
        banner = vbox().apply { setBackgroundColor(p.surface); visibility = View.GONE; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        bannerText = label("", 14f, p.text, true)
        banner.addView(bannerText)
        banner.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate = true }, lp(MATCH, WRAP, t = dp(6)))
        root.addView(banner, lp())
        frame = FrameLayout(this)
        root.addView(frame, lp(MATCH, 0, 1f))
        root.addView(View(this).apply { setBackgroundColor(p.divider) }, lp(MATCH, 1))
        root.addView(buildNav(), lp())
        setContentView(root)

        Notifier.ensureChannel(this)
        Scheduler.ensure(this)
        showTab(tab)
        Model.reload(this)
        maybeAskNotifications()
        handleTarget(intent)
    }

    /** The app is already open and a notification was tapped. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTarget(intent)
    }

    private fun handleTarget(i: Intent?) {
        val target = AlertTarget.parse(i?.getStringExtra(EXTRA_TARGET)) ?: return
        i?.removeExtra(EXTRA_TARGET) // handled once: rotating the phone must not jump back
        if (target is AlertTarget.Folder && Model.comparison == null) pendingTarget = target else open(target)
    }

    private fun open(target: AlertTarget) = when (target) {
        AlertTarget.Overview -> showTab(0)
        AlertTarget.Apps -> showTab(1)
        AlertTarget.Changes -> showTab(2)
        is AlertTarget.Folder -> openInBrowse(target.path)
    }

    private fun buildNav(): View {
        val bar = hbox().apply { setBackgroundColor(p.surface) }
        titles.forEachIndexed { i, t ->
            val item = vbox().apply {
                gravity = Gravity.CENTER
                minimumHeight = dp(64)
                setPadding(0, dp(8), 0, dp(8))
                isClickable = true
                isFocusable = true
                setOnClickListener { showTab(i) }
            }
            item.addView(ImageView(this).apply { setImageResource(icons[i]); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, lp(dp(24), dp(24)))
            item.addView(label(t, 12f, p.muted).apply { gravity = Gravity.CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, lp(MATCH, WRAP, t = dp(2)))
            item.contentDescription = "$t, tab ${i + 1} of ${titles.size}"
            navItems.add(item)
            bar.addView(item, lp(0, WRAP, 1f))
        }
        return bar
    }

    private fun showTab(i: Int) {
        tab = i
        frame.removeAllViews()
        frame.addView(screens[i].root, FrameLayout.LayoutParams(MATCH, MATCH))
        screens[i].refresh()
        navItems.forEachIndexed { k, item ->
            val on = k == i
            val color = if (on) p.primary else p.muted
            (item.getChildAt(0) as ImageView).imageTintList = ColorStateList.valueOf(color)
            (item.getChildAt(1) as TextView).apply { setTextColor(color); setTypeface(null, if (on) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL) }
            item.stateDescription = if (on) "Selected" else null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab)
    }

    override fun onStart() {
        super.onStart()
        ScanController.listeners.add(this)
        Model.listeners.add(modelListener)
    }

    override fun onStop() {
        ticker.removeCallbacks(tick)
        ScanController.listeners.remove(this)
        Model.listeners.remove(modelListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        wasRunning = ScanController.running
        screens[tab].refresh()
        updateBanner()
    }

    // ---- scan state ----
    override fun onScanState() {
        val running = ScanController.running
        if (running != wasRunning) {
            wasRunning = running
            invalidateOptionsMenu()
            window.decorView.announceForAccessibility(if (running) "Scan started" else "Scan finished")
            if (!running) Model.reload(this)
        }
        updateBanner()
    }

    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (!ScanController.running) return
            updateBannerText()
            ticker.postDelayed(this, 1000)
        }
    }

    private fun updateBanner() {
        if (!ScanController.running) { banner.visibility = View.GONE; ticker.removeCallbacks(tick); return }
        banner.visibility = View.VISIBLE
        updateBannerText()
        ticker.removeCallbacks(tick)
        ticker.postDelayed(tick, 1000) // the clock advances every second, not only when the scanner reports progress
        if (tab == 4) screens[4].refresh()
    }

    /** Total elapsed time comes from the scan's own start time; the apps phase reports no progress, so it is named instead. */
    private fun updateBannerText() {
        val pr = ScanController.progress
        val elapsed = Fmt.dur(System.currentTimeMillis() - ScanController.startedMs)
        bannerText.text = when {
            pr == null -> "Starting scan… · $elapsed"
            ScanController.measuringApps -> "Measuring apps… · ${"%,d".format(pr.files)} files found · $elapsed"
            else -> "Scanning files… ${"%,d".format(pr.files)} files · ${formatBytes(pr.bytes)} · $elapsed"
        }
    }

    // ---- top-right play / stop button ----
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_scan)?.apply {
            if (ScanController.running) { setIcon(R.drawable.ic_stop); title = "Stop scan" } else { setIcon(R.drawable.ic_play); title = "Scan now" }
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_scan) {
            if (ScanController.running) ScanController.cancel() else requestScan()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @Deprecated("Back handling for drill-down in Browse")
    override fun onBackPressed() {
        if (tab == 3 && browse.canGoUp()) browse.goUp() else super.onBackPressed()
    }

    // ---- Actions ----
    override fun goToTab(index: Int) = showTab(index)

    override fun requestScan() {
        if (ScanController.running) { Toast.makeText(this, "A scan is already running", Toast.LENGTH_SHORT).show(); return }
        if (!Environment.isExternalStorageManager()) {
            AlertDialog.Builder(this)
                .setTitle("All files access needed")
                .setMessage("To scan your whole storage, Android needs you to switch on “All files access” for Storage Tracker.")
                .setPositiveButton("Open settings") { _, _ -> openAllFilesSettings() }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        ScanController.start(this, "manual")
    }

    override fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !Prefs.askedNotificationPermission(this)) {
            Prefs.setAskedNotificationPermission(this)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        } else {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        screens[tab].refresh()
    }

    private fun maybeAskNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(this) && !Prefs.askedNotificationPermission(this)) requestNotificationPermission()
    }

    override fun openAllFilesSettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
    }

    override fun openBatterySettings() {
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    override fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    override fun openAppInfo(pkg: String) {
        runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))) }
            .onFailure { Toast.makeText(this, "Cannot open app info for $pkg", Toast.LENGTH_SHORT).show() }
    }

    override fun openStorageSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
            .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    override fun addQuickTile() {
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(this, "Swipe down twice, tap the pencil, and drag “Scan storage” into your tiles.", Toast.LENGTH_LONG).show()
            return
        }
        getSystemService(android.app.StatusBarManager::class.java).requestAddTileService(
            android.content.ComponentName(this, ScanTileService::class.java), "Scan storage",
            android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_storage), mainExecutor,
        ) { result ->
            val msg = when (result) {
                android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Tile added"
                android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "The tile is already in your Quick Settings"
                else -> "Tile not added"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun exportSnapshots() {
        if (Model.metas.isEmpty()) { Toast.makeText(this, "No snapshots to export yet", Toast.LENGTH_SHORT).show(); return }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip")
            .putExtra(Intent.EXTRA_TITLE, "storage-tracker-snapshots-$stamp.zip")
        startActivityForResult(intent, REQ_EXPORT)
    }

    override fun importSnapshots() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        startActivityForResult(intent, REQ_IMPORT)
    }

    @Deprecated("The framework file pickers still return through onActivityResult; no AndroidX is used in this app")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        val app = applicationContext
        // file work happens off the main thread; the result is reported back with a toast
        Thread {
            val message = runCatching {
                when (requestCode) {
                    REQ_EXPORT -> {
                        val n = contentResolver.openOutputStream(uri)?.use { SnapshotStore.exportTo(app, it) } ?: 0
                        "Exported $n snapshot${if (n == 1) "" else "s"}"
                    }
                    REQ_IMPORT -> {
                        val r = contentResolver.openInputStream(uri)?.use { SnapshotStore.importFrom(app, it) }
                        if (r == null) "Could not open that file"
                        else "Imported ${r.added} new, ${r.skipped} already here" + if (r.invalid > 0) ", ${r.invalid} not valid" else ""
                    }
                    else -> null
                }
            }.getOrElse { "That did not work: ${it.message ?: it.javaClass.simpleName}" }
            if (message != null) runOnUiThread {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                if (requestCode == REQ_IMPORT) Model.reload(this)
            }
        }.start()
    }

    override fun shareReport() {
        val cmp = Model.comparison
        if (cmp == null) {
            Toast.makeText(this, "Run a scan first", Toast.LENGTH_SHORT).show()
            return
        }
        val text = Report.build(cmp, Forecast.of(Model.metas, System.currentTimeMillis()))
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Storage Tracker report")
            .putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, "Share report"))
    }

    override fun openInBrowse(path: String) {
        browse.open(path)
        showTab(3)
    }

    override fun openCategory(key: String?) {
        browse.openCategory(key)
        showTab(3)
    }

    override fun openUsageAccessSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName")))
    }
}
