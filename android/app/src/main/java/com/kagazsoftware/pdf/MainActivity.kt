package com.kagazsoftware.pdf

import android.app.DownloadManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * Hosts the Kagaz PDF web UI. TLS errors are never bypassed, and navigation to any other origin is
 * handed to the system browser so the WebView only ever shows the user's own server.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var errorView: View
    private lateinit var errorMessage: TextView
    private var filePicker: ValueCallback<Array<Uri>>? = null
    private lateinit var server: String
    private var loadFailed = false

    private val pick = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        val clip = data?.clipData
        filePicker?.onReceiveValue(
            when {
                r.resultCode != RESULT_OK -> null
                clip != null -> Array(clip.itemCount) { clip.getItemAt(it).uri }
                data?.data != null -> arrayOf(data.data!!)
                else -> null
            }
        )
        filePicker = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        server = getSharedPreferences(SetupActivity.PREFS, MODE_PRIVATE).getString(SetupActivity.KEY_SERVER, null)
            ?: run { openSetup(); finish(); return }
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.root).applySystemInsetsPadding()
        progress = findViewById(R.id.progress)
        refresh = findViewById(R.id.refresh)
        errorView = findViewById(R.id.errorView)
        errorMessage = findViewById(R.id.errorMessage)
        web = findViewById(R.id.web)

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            inflateMenu(R.menu.main)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_reload -> { reload(); true }
                    R.id.action_browser -> { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(server))); true }
                    R.id.action_change -> { openSetup(); true }
                    else -> false
                }
            }
        }
        findViewById<View>(R.id.retry).setOnClickListener { reload() }
        findViewById<View>(R.id.changeServer).setOnClickListener { openSetup() }
        refresh.setOnRefreshListener { reload() }
        refresh.setOnChildScrollUpCallback { _, _ -> web.scrollY > 0 }

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        CookieManager.getInstance().setAcceptCookie(true)

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest): Boolean {
                if (ServerUrl.sameOrigin(server, req.url.toString())) return false
                startActivity(Intent(Intent.ACTION_VIEW, req.url)); return true
            }
            override fun onPageStarted(v: WebView, url: String, favicon: Bitmap?) { loadFailed = false }
            override fun onPageFinished(v: WebView, url: String) {
                refresh.isRefreshing = false
                if (!loadFailed) errorView.visibility = View.GONE
            }
            override fun onReceivedError(v: WebView, req: WebResourceRequest, err: WebResourceError) {
                if (req.isForMainFrame) showError(err.description?.toString() ?: getString(R.string.err_unreachable))
            }
            override fun onReceivedSslError(v: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                showError(getString(R.string.load_error_cert))
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                progress.visibility = if (p < 100) View.VISIBLE else View.GONE
                progress.setProgressCompat(p, true)
            }
            override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                filePicker?.onReceiveValue(null); filePicker = cb
                val i = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, p.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
                }
                pick.launch(Intent.createChooser(i, null)); return true
            }
        }
        web.setDownloadListener { url, ua, cd, mime, _ ->
            if (!ServerUrl.sameOrigin(server, url)) return@setDownloadListener
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                addRequestHeader("User-Agent", ua)
                setMimeType(mime)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, cd, mime))
            }
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
        if (savedInstanceState == null) web.loadUrl(server) else web.restoreState(savedInstanceState)
    }

    private fun showError(message: String) {
        loadFailed = true
        refresh.isRefreshing = false
        errorMessage.text = message
        errorView.visibility = View.VISIBLE
    }

    private fun reload() {
        errorView.visibility = View.GONE
        if (web.url == null || loadFailed) web.loadUrl(server) else web.reload()
    }

    private fun openSetup() {
        startActivity(Intent(this, SetupActivity::class.java).putExtra(SetupActivity.EXTRA_CHANGE, true))
        finish()
    }

    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); if (::web.isInitialized) web.saveState(out) }
    override fun onDestroy() { if (::web.isInitialized) web.destroy(); super.onDestroy() }
}
