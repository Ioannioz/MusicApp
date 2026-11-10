package com.example.myapplication.activites

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import com.example.myapplication.util.ToastHelper
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.databinding.ActivitySplashBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
            } else {
                permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
            }

            if (audioGranted) {
                loadAppAndNavigate()
            } else {
                ToastHelper.showToast(this, "Permission required to load songs")
                navigateToMain()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissionsAndLoad()
    }

    private fun checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionsToRequest = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }

            if (permissionsToRequest.isEmpty()) {
                loadAppAndNavigate()
            } else {
                requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                loadAppAndNavigate()
            } else {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
            }
        }
    }

    private fun loadAppAndNavigate() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
                contentResolver.query(uri, null, selection, null, null)?.use { cursor ->
                    cursor.count
                }
            } catch (e: Exception) {
                // Ignore query error
            }

            withContext(Dispatchers.Main) {
                navigateToMain()
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this@SplashActivity, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
