package com.kagazsoftware.pdf

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputLayout
import java.util.concurrent.Executors

/** First-run screen: validates the address, probes the server, then stores it and opens the app. */
class SetupActivity : AppCompatActivity() {
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val changing = intent.getBooleanExtra(EXTRA_CHANGE, false)
        val builtIn = ServerUrl.builtIn(BuildConfig.DEFAULT_SERVER)
        if (prefs.getString(KEY_SERVER, null) == null && builtIn != null && !changing) {
            prefs.edit().putString(KEY_SERVER, builtIn).apply()
        }
        val saved = prefs.getString(KEY_SERVER, null)
        if (saved != null && !intent.getBooleanExtra(EXTRA_CHANGE, false)) {
            startActivity(Intent(this, MainActivity::class.java)); finish(); return
        }
        setContentView(R.layout.activity_setup)
        findViewById<View>(R.id.root).applySystemInsetsPadding()
        val layout = findViewById<TextInputLayout>(R.id.serverLayout)
        val input = findViewById<EditText>(R.id.serverUrl)
        val button = findViewById<MaterialButton>(R.id.connect)
        val checking = findViewById<LinearProgressIndicator>(R.id.checking)
        val linked = intent.data?.takeIf { it.scheme == "kagazpdf" }?.getQueryParameter("server")
        (linked ?: saved)?.let { input.setText(it) }

        fun connect() {
            layout.error = null
            val url = ServerUrl.normalize(input.text.toString())
            if (url == null) { layout.error = getString(R.string.bad_url); return }
            button.isEnabled = false; checking.visibility = View.VISIBLE
            worker.execute {
                val result = ServerCheck.probe(url)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    button.isEnabled = true; checking.visibility = View.INVISIBLE
                    when (result) {
                        ServerCheck.Result.OK -> {
                            prefs.edit().putString(KEY_SERVER, url).apply()
                            startActivity(Intent(this, MainActivity::class.java)); finish()
                        }
                        ServerCheck.Result.TLS_FAILED -> layout.error = getString(R.string.err_tls)
                        ServerCheck.Result.UNREACHABLE -> layout.error = getString(R.string.err_unreachable)
                    }
                }
            }
        }
        button.setOnClickListener { connect() }
        input.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_GO) { connect(); true } else false
        }
    }

    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }

    companion object {
        const val PREFS = "kagaz"
        const val KEY_SERVER = "server"
        const val EXTRA_CHANGE = "change"
    }
}
