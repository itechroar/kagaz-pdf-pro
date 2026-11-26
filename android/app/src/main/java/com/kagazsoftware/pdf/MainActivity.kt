package com.kagazsoftware.pdf

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Hosts the Kagaz PDF web UI. TLS errors are never bypassed; navigation outside the configured
 * server is handed to the system browser so the WebView only ever shows the user's own server.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private var filePicker: ValueCallback<Array<Uri>>? = null
    private lateinit var server: String

    private val pick = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        filePicker?.onReceiveValue(
            if (r.resultCode == RESULT_OK) {
                val d = r.data
                val clip = d?.clipData
                when {
                    clip != null -> Array(clip.itemCount) { clip.getItemAt(it).uri }
                    d?.data != null -> arrayOf(d.data!!)
                    else -> null
                }
            } else null
        )
        filePicker = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        server = getSharedPreferences("kagaz", MODE_PRIVATE).getString("server", null) ?: run {
            startActivity(Intent(this, SetupActivity::class.java)); finish(); return
        }
        setContentView(R.layout.activity_main)
        web = findViewById(R.id.web)
        progress = findViewById(R.id.progress)
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(true)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest): Boolean {
                if (req.url.toString().startsWith(server)) return false
                startActivity(Intent(Intent.ACTION_VIEW, req.url)); return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                progress.visibility = if (p < 100) View.VISIBLE else View.GONE
                progress.progress = p
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
            val name = URLUtil.guessFileName(url, cd, mime)
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                addRequestHeader("User-Agent", ua)
                setMimeType(mime)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            }
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack()
                else startActivity(Intent(this@MainActivity, SetupActivity::class.java).putExtra("change", true))
            }
        })
        if (savedInstanceState == null) web.loadUrl(server) else web.restoreState(savedInstanceState)
    }

    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); if (::web.isInitialized) web.saveState(out) }
    override fun onDestroy() { if (::web.isInitialized) web.destroy(); super.onDestroy() }
}
