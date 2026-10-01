package com.identixia.facerecognitionsdk.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.identixia.facerecognitionsdk.R
import com.identixia.facerecognitionsdk.kit.LicenseStatus
import com.google.android.material.appbar.MaterialToolbar

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val openSite = {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.company_website_url))))
        }
        findViewById<ImageView>(R.id.imgAboutLogo).setOnClickListener { openSite() }
        findViewById<TextView>(R.id.txtAboutWebsite).setOnClickListener { openSite() }

        val txtLicense = findViewById<TextView>(R.id.txtAboutLicense)
        val txtAppId = findViewById<TextView>(R.id.txtMachineCode)
        val appId = packageName
        txtAppId.text = appId
        txtLicense.text = getString(R.string.about_license_fmt, LicenseStatus.current().label)

        findViewById<TextView>(R.id.btnCopyMachine).apply {
            setText(R.string.copy_application_id)
            setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("applicationId", appId))
                Toast.makeText(this@AboutActivity, R.string.copied, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
