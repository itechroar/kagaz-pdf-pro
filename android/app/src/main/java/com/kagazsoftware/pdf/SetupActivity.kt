package com.kagazsoftware.pdf

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("kagaz", MODE_PRIVATE)
        val saved = prefs.getString("server", null)
        if (saved != null && intent.getBooleanExtra("change", false).not()) {
            startActivity(Intent(this, MainActivity::class.java)); finish(); return
        }
        setContentView(R.layout.activity_setup)
        val input = findViewById<EditText>(R.id.serverUrl)
        val error = findViewById<TextView>(R.id.error)
        saved?.let { input.setText(it) }
        findViewById<Button>(R.id.connect).setOnClickListener {
            val url = ServerUrl.normalize(input.text.toString())
            if (url == null) {
                error.setText(R.string.bad_url); error.visibility = View.VISIBLE
            } else {
                prefs.edit().putString("server", url).apply()
                startActivity(Intent(this, MainActivity::class.java)); finish()
            }
        }
    }
}
