package com.example.myapplication.activites

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.myapplication.R
import java.io.File
import java.net.URLEncoder

class WebCoverPickerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var textTitle: TextView
    private lateinit var btnBack: ImageView
    private var isDownloading = false

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_cover_picker)
        enableEdgeToEdge()

        webView = findViewById(R.id.webViewCover)
        textTitle = findViewById(R.id.textWebTitle)
        btnBack = findViewById(R.id.btnBackWeb)

        btnBack.setOnClickListener { finish() }

        val searchQuery = intent.getStringExtra("search_query") ?: "album cover"
        textTitle.text = searchQuery

        val encodedQuery = try {
            URLEncoder.encode(searchQuery, "UTF-8")
        } catch (e: Exception) {
            searchQuery
        }

        val searchUrl = "https://www.google.com/search?tbm=isch&q=$encodedQuery"

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        webView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP && !isDownloading) {
                val hitTestResult = webView.hitTestResult
                if (hitTestResult.type == WebView.HitTestResult.IMAGE_TYPE ||
                    hitTestResult.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                ) {
                    val imageUrl = hitTestResult.extra
                    if (!imageUrl.isNullOrEmpty()) {
                        downloadAndSelectImage(imageUrl)
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }

        webView.loadUrl(searchUrl)
    }

    private fun downloadAndSelectImage(imageUrl: String) {
        isDownloading = true
        Toast.makeText(this, "Downloading cover image...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val bitmap: Bitmap = Glide.with(applicationContext)
                    .asBitmap()
                    .load(imageUrl)
                    .submit()
                    .get()

                val tempFile = File(cacheDir, "web_cover_${System.currentTimeMillis()}.jpg")
                tempFile.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                val resultUri = Uri.fromFile(tempFile)

                runOnUiThread {
                    val intent = Intent().apply {
                        putExtra("web_image_uri", resultUri.toString())
                    }
                    setResult(RESULT_OK, intent)
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    isDownloading = false
                    Toast.makeText(this@WebCoverPickerActivity, "Could not load image, try another one", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
