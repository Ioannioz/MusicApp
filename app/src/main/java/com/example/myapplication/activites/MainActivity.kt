package com.example.myapplication.activites

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.example.myapplication.util.ToastHelper
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.myapplication.R
import com.example.myapplication.adapter.MainPagerAdapter
import com.example.myapplication.adapter.PlaylistAdapter
import com.example.myapplication.adapter.SongAdapter
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.model.Playlist
import com.example.myapplication.model.PlaylistManager
import com.example.myapplication.model.Song
import com.example.myapplication.service.AudioService
import com.google.android.exoplayer2.Player
import org.json.JSONArray
import java.io.File
import java.util.Collections
import java.util.Locale

class MainActivity : AppCompatActivity(), SongAdapter.OnItemClickListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var pagerAdapter: MainPagerAdapter
    private var songList: List<Song> = mutableListOf()

    private var audioService: AudioService? = null
    private var isBound = false
    private var activeQueueToast: Toast? = null
    private val handle = Handler(Looper.getMainLooper())
    private var isUserScrubbingMini = false

    fun getAudioService(): AudioService? = audioService

    private var tempSelectedImageUri: Uri? = null
    private var activeDialogCoverPreview: ImageView? = null

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            val srv = audioService
            val player = srv?.player
            if (srv != null && player != null) {
                val currentPosition = player.currentPosition
                val duration = player.duration
                if (duration > 0 && !isUserScrubbingMini) {
                    val progressRatio = currentPosition.toFloat() / duration.toFloat()
                    binding.miniSeekBar.progress = (progressRatio * 1000).toInt()

                    val seconds = (currentPosition / 1000).toInt()
                    val minutes = seconds / 60
                    val secs = seconds % 60
                    binding.miniTextElapsed.text = String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
                }
                if (player.isPlaying) {
                    handle.postDelayed(this, 100)
                }
            }
        }
    }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                tempSelectedImageUri = uri
                activeDialogCoverPreview?.let { imgView ->
                    Glide.with(this)
                        .load(uri)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .centerCrop()
                        .into(imgView)
                }
            }
        }

    private val webCoverLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uriString = result.data?.getStringExtra("web_image_uri")
                if (!uriString.isNullOrEmpty()) {
                    val uri = Uri.parse(uriString)
                    tempSelectedImageUri = uri
                    activeDialogCoverPreview?.let { imgView ->
                        Glide.with(this)
                            .load(uri)
                            .skipMemoryCache(true)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .centerCrop()
                            .into(imgView)
                    }
                }
            }
        }

    private data class PendingEdit(
        val position: Int,
        val newTitle: String,
        val newArtist: String,
        val newImageUri: Uri?
    )
    private var pendingEditSong: PendingEdit? = null

    private val editMediaLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                pendingEditSong?.let { edit ->
                    applySongEditToApp(edit.position, edit.newTitle, edit.newArtist, edit.newImageUri)
                }
            } else {
                ToastHelper.showToast(this, "Edit cancelled")
            }
            pendingEditSong = null
        }

    private fun getWebClientId(): String {
        val resId = resources.getIdentifier("default_web_client_id", "string", packageName)
        return if (resId != 0) getString(resId) else "753319027179-webclient.apps.googleusercontent.com"
    }

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            var email = ""
            var name = ""
            var idToken = ""

            if (data != null) {
                try {
                    val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data)
                    val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                    if (account != null) {
                        email = account.email ?: "googleuser@gmail.com"
                        name = account.displayName ?: email.substringBefore("@")
                        idToken = account.idToken ?: ""
                    }
                } catch (e: com.google.android.gms.common.api.ApiException) {
                    if (e.statusCode == 10) {
                        ToastHelper.showToast(this, "Please add SHA-1 fingerprint in Firebase Console")
                    } else {
                        ToastHelper.showToast(this, "Google Sign-In Error (${e.statusCode}): ${e.localizedMessage}")
                    }
                } catch (e: Exception) {
                    ToastHelper.showToast(this, "Google Sign-In Error: ${e.localizedMessage}")
                }
            }

            if (email.isNotEmpty()) {
                val cleanEmail = email.lowercase(java.util.Locale.getDefault())
                val prefs = getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE)
                val existingUsername = prefs.getString("username_$cleanEmail", null)

                if (!existingUsername.isNullOrEmpty()) {
                    com.example.myapplication.model.FirebaseUserManager.signInWithGoogleAccount(this, idToken, email, existingUsername) { _, msg ->
                        ToastHelper.showToast(this, msg)
                        refreshDrawerAuthState()
                    }
                } else {
                    com.example.myapplication.model.FirebaseUserManager.findProfileByEmail(email) { existingProfile ->
                        runOnUiThread {
                            if (existingProfile != null && existingProfile.username.isNotEmpty()) {
                                prefs.edit().putString("username_$cleanEmail", existingProfile.username).apply()
                                com.example.myapplication.model.FirebaseUserManager.signInWithGoogleAccount(this, idToken, email, existingProfile.username) { _, msg ->
                                    ToastHelper.showToast(this, msg)
                                    refreshDrawerAuthState()
                                }
                            } else {
                                promptGoogleUserForUsername(idToken, email, name)
                            }
                        }
                    }
                }
            }
        }

    private fun promptGoogleUserForUsername(idToken: String, email: String, defaultName: String) {
        val dialog = AlertDialog.Builder(this).create()
        val view = layoutInflater.inflate(R.layout.dialog_input_text, null)

        val titleText = view.findViewById<TextView>(R.id.dialogInputTitle)
        val subMsgText = view.findViewById<TextView>(R.id.dialogInputSubMessage)
        val input = view.findViewById<EditText>(R.id.dialogInputField)
        val btnCancel = view.findViewById<Button>(R.id.btnInputCancel)
        val btnConfirm = view.findViewById<Button>(R.id.btnInputConfirm)

        titleText.text = "Choose Unique Username"
        subMsgText.visibility = View.VISIBLE
        subMsgText.text = "Welcome! Create a unique username for your profile:"
        input.hint = "Username"
        input.setText(defaultName)
        btnConfirm.text = "Save Username"

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            val chosenUsername = input.text.toString().trim()
            if (chosenUsername.isNotEmpty()) {
                com.example.myapplication.model.FirebaseUserManager.checkUsernameAvailability(this, chosenUsername) { available, msg ->
                    if (available) {
                        dialog.dismiss()
                        val cleanEmail = email.lowercase(java.util.Locale.getDefault())
                        getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putString("username_$cleanEmail", chosenUsername)
                            .apply()

                        com.example.myapplication.model.FirebaseUserManager.signInWithGoogleAccount(this, idToken, email, chosenUsername) { success, toastMsg ->
                            ToastHelper.showToast(this, toastMsg)
                            if (success) refreshDrawerAuthState()
                        }
                    } else {
                        ToastHelper.showToast(this, msg)
                    }
                }
            } else {
                ToastHelper.showToast(this, "Username cannot be empty")
            }
        }

        dialog.setView(view)
        com.example.myapplication.util.DialogThemeHelper.styleDialogView(view, this)
        com.example.myapplication.util.DialogThemeHelper.styleEditText(input, this)
        com.example.myapplication.util.DialogThemeHelper.stylePrimaryButton(btnConfirm, this)
        com.example.myapplication.util.DialogThemeHelper.styleSecondaryButton(btnCancel, this)
        com.example.myapplication.util.DialogThemeHelper.removeDialogWindowBorders(dialog)
        dialog.show()
    }

    private var tempSelectedProfileAvatarUri: Uri? = null
    private var activeEditAvatarPreview: ImageView? = null

    private val pickProfileAvatarLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                tempSelectedProfileAvatarUri = uri
                activeEditAvatarPreview?.let { imgView ->
                    Glide.with(this)
                        .load(uri)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .centerCrop()
                        .into(imgView)
                }
            }
        }

    private val stateListener = {
        runOnUiThread {
            updateMiniPlayerUI()
            updateUserOnlineStatus()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioService.AudioBinder
            val srv = binder.getService()
            audioService = srv
            isBound = true

            srv.addStateChangeListener(stateListener)
            updateMiniPlayerUI()
            updateUserOnlineStatus()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService?.removeStateChangeListener(stateListener)
            audioService = null
            isBound = false
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
            } else {
                permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
            }

            if (audioGranted) {
                loadSongs()
            } else {
                ToastHelper.showToast(this, "Permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { com.google.firebase.FirebaseApp.initializeApp(this) } catch (_: Exception) {}
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(binding.PlayerRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.tabHeaderLayout.updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomMargin = systemBars.bottom
            }
            binding.navDrawerProfile.navDrawerContainer.setPadding(
                binding.navDrawerProfile.navDrawerContainer.paddingLeft,
                systemBars.top + (16 * resources.displayMetrics.density).toInt(),
                binding.navDrawerProfile.navDrawerContainer.paddingRight,
                binding.navDrawerProfile.navDrawerContainer.paddingBottom
            )
            insets
        }

        checkPermissionAndLoadSong()
        setupMiniPlayerControls()
        setupTabHeaderNavigation()
        setupProfileDrawer()
        registerNetworkCallback()

        com.example.myapplication.model.ThemeManager.addThemeChangeListener(themeChangeListener)
        applyAppTheme(com.example.myapplication.model.ThemeManager.getSelectedTheme(this))
    }

    private fun selectTab(position: Int) {
        val theme = com.example.myapplication.model.ThemeManager.getSelectedTheme(this)
        val activeColor = android.graphics.Color.parseColor(theme.accentColorHex)
        val lightGray = getColor(R.color.light_gray)

        binding.iconHome.setColorFilter(if (position == 0) activeColor else lightGray)
        binding.textHome.setTextColor(if (position == 0) activeColor else lightGray)

        binding.iconPlaylist.setColorFilter(if (position == 1) activeColor else lightGray)
        binding.textPlaylist.setTextColor(if (position == 1) activeColor else lightGray)

        binding.iconEqualizer.setColorFilter(if (position == 2) activeColor else lightGray)
        binding.textEqualizer.setTextColor(if (position == 2) activeColor else lightGray)

        binding.iconStats.setColorFilter(if (position == 3) activeColor else lightGray)
        binding.textStats.setTextColor(if (position == 3) activeColor else lightGray)

        binding.iconThemes.setColorFilter(if (position == 4) activeColor else lightGray)
        binding.textThemes.setTextColor(if (position == 4) activeColor else lightGray)
    }

    fun openProfileDrawer() {
        refreshDrawerAuthState()
        binding.mainDrawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
    }

    private fun setupProfileDrawer() {
        val drawer = binding.navDrawerProfile

        drawer.btnCloseDrawer.setOnClickListener {
            binding.mainDrawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        }

        drawer.btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        drawer.btnSignIn.setOnClickListener {
            val email = drawer.inputAuthEmail.text.toString().trim()
            val pass = drawer.inputAuthPassword.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                ToastHelper.showToast(this, "Please fill in email and password")
                return@setOnClickListener
            }

            com.example.myapplication.model.FirebaseUserManager.signInWithEmail(this, email, pass) { success, msg ->
                ToastHelper.showToast(this, msg)
                if (success) {
                    refreshDrawerAuthState()
                }
            }
        }

        drawer.btnSignUp.setOnClickListener {
            val email = drawer.inputAuthEmail.text.toString().trim()
            val user = drawer.inputAuthUsername.text.toString().trim()
            val pass = drawer.inputAuthPassword.text.toString().trim()

            if (email.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                ToastHelper.showToast(this, "Please fill in email, username, and password")
                return@setOnClickListener
            }

            if (pass.length < 8) {
                ToastHelper.showToast(this, "Password must be at least 8 characters")
                return@setOnClickListener
            }

            com.example.myapplication.model.FirebaseUserManager.signUpWithEmail(this, email, user, pass) { success, msg ->
                ToastHelper.showToast(this, msg)
                if (success) {
                    refreshDrawerAuthState()
                }
            }
        }

        drawer.btnGoogleAuth.setOnClickListener {
            try {
                val gsoBuilder = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                    com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
                ).requestEmail()

                val webClientId = getWebClientId()
                if (webClientId.isNotEmpty()) {
                    try {
                        gsoBuilder.requestIdToken(webClientId)
                    } catch (_: Exception) {}
                }

                val gso = gsoBuilder.build()
                val googleClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso)

                // Sign out Google Client first so Google Account Chooser is shown every time!
                googleClient.signOut().addOnCompleteListener {
                    googleSignInLauncher.launch(googleClient.signInIntent)
                }
            } catch (e: Exception) {
                ToastHelper.showToast(this, "Google Sign-In error: ${e.message}")
            }
        }

        drawer.btnSignOut.setOnClickListener {
            showSignOutConfirmationDialog()
        }

        drawer.inputSearchFriend.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                drawer.navDrawerScrollView.postDelayed({
                    drawer.navDrawerScrollView.smoothScrollTo(0, drawer.inputSearchFriend.bottom + 300)
                }, 200)
            }
        }

        drawer.inputSearchFriend.setOnClickListener {
            drawer.navDrawerScrollView.postDelayed({
                drawer.navDrawerScrollView.smoothScrollTo(0, drawer.inputSearchFriend.bottom + 300)
            }, 200)
        }

        drawer.inputSearchFriend.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND ||
                actionId == android.view.inputmethod.EditorInfo.IME_NULL
            ) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(drawer.inputSearchFriend.windowToken, 0)
                drawer.inputSearchFriend.clearFocus()
                true
            } else {
                false
            }
        }

        drawer.btnAddFriend.setOnClickListener {
            val username = drawer.inputSearchFriend.text.toString().trim()
            if (username.isNotEmpty()) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(drawer.inputSearchFriend.windowToken, 0)
                drawer.inputSearchFriend.clearFocus()

                com.example.myapplication.model.FirebaseUserManager.sendFriendRequest(this, username) { success, msg ->
                    ToastHelper.showToast(this, msg)
                    if (success) {
                        drawer.inputSearchFriend.setText("")
                        refreshDrawerAuthState()
                    }
                }
            } else {
                ToastHelper.showToast(this, "Enter username")
            }
        }

        val initPrefs = getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE)
        drawer.switchBroadcastStatus.isChecked = initPrefs.getBoolean("broadcast_status_enabled", true)
        drawer.switchAllowFriendRequests.isChecked = initPrefs.getBoolean("allow_friend_requests", true)

        drawer.switchBroadcastStatus.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("broadcast_status_enabled", isChecked).apply()

            updateUserOnlineStatus()
            ToastHelper.showToast(this, if (isChecked) "Status set to Online" else "Status forced to Offline")
        }

        drawer.switchAllowFriendRequests.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("allow_friend_requests", isChecked).apply()
            com.example.myapplication.model.FirebaseUserManager.updateFriendRequestPreference(this, isChecked)
            ToastHelper.showToast(this, if (isChecked) "Friend requests allowed" else "Friend requests disabled")
        }

        refreshDrawerAuthState()
    }

    private fun showEditProfileDialog() {
        val dialog = AlertDialog.Builder(this).create()
        val view = layoutInflater.inflate(R.layout.dialog_edit_profile, null)

        val avatarPreview = view.findViewById<ImageView>(R.id.imgEditProfileAvatarPreview)
        val btnChangeAvatar = view.findViewById<Button>(R.id.btnChangeProfileAvatar)
        val inputUsername = view.findViewById<EditText>(R.id.inputEditProfileUsername)
        val btnCancel = view.findViewById<Button>(R.id.btnCancelEditProfile)
        val btnSave = view.findViewById<Button>(R.id.btnSaveEditProfile)

        activeEditAvatarPreview = avatarPreview
        tempSelectedProfileAvatarUri = null

        val currentProf = com.example.myapplication.model.FirebaseUserManager.getLocalProfile(this)
        inputUsername.setText(currentProf?.username ?: "User")

        val activeUid = com.example.myapplication.model.FirebaseUserManager.getActiveUid(this) ?: "user"
        val avatarFile = File(filesDir, "avatar_${activeUid}.jpg")

        avatarPreview.imageTintList = null
        avatarPreview.clearColorFilter()

        if (avatarFile.exists()) {
            Glide.with(this)
                .load(avatarFile)
                .signature(com.bumptech.glide.signature.ObjectKey(avatarFile.lastModified()))
                .centerCrop()
                .placeholder(R.drawable.baseline_account_circle_24)
                .into(avatarPreview)
        } else {
            avatarPreview.setImageResource(R.drawable.baseline_account_circle_24)
            com.example.myapplication.model.FirebaseUserManager.fetchCurrentProfile(this) { profile ->
                runOnUiThread {
                    val cloudAvatar = profile?.avatarUrl
                    if (!cloudAvatar.isNullOrEmpty()) {
                        val bitmap = com.example.myapplication.util.ImageUtils.decodeBase64ToBitmap(cloudAvatar)
                        if (bitmap != null) {
                            Glide.with(this@MainActivity)
                                .load(bitmap)
                                .centerCrop()
                                .placeholder(R.drawable.baseline_account_circle_24)
                                .into(avatarPreview)
                        }
                    }
                }
            }
        }

        btnChangeAvatar.setOnClickListener {
            pickProfileAvatarLauncher.launch("image/*")
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val newUsername = inputUsername.text.toString().trim()
            if (newUsername.isNotEmpty()) {
                val activeUid = com.example.myapplication.model.FirebaseUserManager.getActiveUid(this)
                com.example.myapplication.model.FirebaseUserManager.checkUsernameAvailability(this, newUsername, activeUid) { available, msg ->
                    if (available) {
                        dialog.dismiss()
                        saveProfileChanges(newUsername, tempSelectedProfileAvatarUri)
                    } else {
                        ToastHelper.showToast(this, msg)
                    }
                }
            } else {
                ToastHelper.showToast(this, "Username cannot be empty")
            }
        }

        dialog.setView(view)
        com.example.myapplication.util.DialogThemeHelper.styleDialogView(view, this)
        com.example.myapplication.util.DialogThemeHelper.styleEditText(inputUsername, this)
        com.example.myapplication.util.DialogThemeHelper.styleSecondaryButton(btnChangeAvatar, this)
        com.example.myapplication.util.DialogThemeHelper.styleSecondaryButton(btnCancel, this)
        com.example.myapplication.util.DialogThemeHelper.stylePrimaryButton(btnSave, this)
        com.example.myapplication.util.DialogThemeHelper.removeDialogWindowBorders(dialog)
        dialog.show()
    }

    private fun saveProfileChanges(newUsername: String, avatarUri: Uri?) {
        val activeUid = com.example.myapplication.model.FirebaseUserManager.getActiveUid(this) ?: "user"
        com.example.myapplication.model.FirebaseUserManager.updateUsername(this, newUsername)

        if (avatarUri != null) {
            try {
                val avatarFile = File(filesDir, "avatar_${activeUid}.jpg")
                contentResolver.openInputStream(avatarUri)?.use { input ->
                    avatarFile.outputStream().use { output -> input.copyTo(output) }
                }
                Glide.get(applicationContext).clearMemory()
                Thread { Glide.get(applicationContext).clearDiskCache() }.start()

                // Encode avatar to Base64 and sync to Cloud Firestore
                val base64Avatar = com.example.myapplication.util.ImageUtils.uriToBase64(this, avatarUri, targetSize = 140)
                if (!base64Avatar.isNullOrEmpty()) {
                    com.example.myapplication.model.FirebaseUserManager.updateUserAvatarUrl(this, base64Avatar)
                }
            } catch (_: Exception) {}
        }

        refreshDrawerAuthState()
        ToastHelper.showToast(this, "Profile updated!")
    }

    private fun showRemoveFriendConfirmationDialog(friend: com.example.myapplication.model.UserProfile) {
        val dialog = AlertDialog.Builder(this).create()
        val view = layoutInflater.inflate(R.layout.dialog_input_text, null)

        val titleText = view.findViewById<TextView>(R.id.dialogInputTitle)
        val subMsgText = view.findViewById<TextView>(R.id.dialogInputSubMessage)
        val inputField = view.findViewById<EditText>(R.id.dialogInputField)
        val btnCancel = view.findViewById<Button>(R.id.btnInputCancel)
        val btnConfirm = view.findViewById<Button>(R.id.btnInputConfirm)

        titleText.text = "Remove Friend"
        subMsgText.visibility = View.VISIBLE
        subMsgText.text = "Are you sure you want to remove ${friend.username} from your friends list?"
        inputField.visibility = View.GONE
        btnConfirm.text = "Remove"

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            com.example.myapplication.model.FirebaseUserManager.removeFriend(this, friend.uid, friend.username) { ok ->
                if (ok) {
                    ToastHelper.showToast(this, "Removed ${friend.username} from friends")
                    refreshDrawerAuthState()
                }
            }
        }

        dialog.setView(view)
        com.example.myapplication.util.DialogThemeHelper.styleDialogView(view, this)
        com.example.myapplication.util.DialogThemeHelper.stylePrimaryButton(btnConfirm, this)
        com.example.myapplication.util.DialogThemeHelper.styleSecondaryButton(btnCancel, this)
        com.example.myapplication.util.DialogThemeHelper.removeDialogWindowBorders(dialog)
        dialog.show()
    }

    private fun showSignOutConfirmationDialog() {
        val dialog = AlertDialog.Builder(this).create()
        val view = layoutInflater.inflate(R.layout.dialog_input_text, null)

        val titleText = view.findViewById<TextView>(R.id.dialogInputTitle)
        val subMsgText = view.findViewById<TextView>(R.id.dialogInputSubMessage)
        val inputField = view.findViewById<EditText>(R.id.dialogInputField)
        val btnCancel = view.findViewById<Button>(R.id.btnInputCancel)
        val btnConfirm = view.findViewById<Button>(R.id.btnInputConfirm)

        titleText.text = "Sign Out"
        subMsgText.visibility = View.VISIBLE
        subMsgText.text = "Are you sure you want to sign out of your account?"
        inputField.visibility = View.GONE
        btnConfirm.text = "Sign Out"

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            com.example.myapplication.model.FirebaseUserManager.signOut(this)
            ToastHelper.showToast(this, "Signed out")
            refreshDrawerAuthState()
        }

        dialog.setView(view)
        com.example.myapplication.util.DialogThemeHelper.styleDialogView(view, this)
        com.example.myapplication.util.DialogThemeHelper.stylePrimaryButton(btnConfirm, this)
        com.example.myapplication.util.DialogThemeHelper.styleSecondaryButton(btnCancel, this)
        com.example.myapplication.util.DialogThemeHelper.removeDialogWindowBorders(dialog)
        dialog.show()
    }

    fun refreshDrawerAuthState() {
        runOnUiThread {
            val drawer = binding.navDrawerProfile
            val isLoggedIn = com.example.myapplication.model.FirebaseUserManager.isUserLoggedIn(this)

            if (isLoggedIn) {
                com.example.myapplication.model.FirebaseUserManager.verifyUserInDatabase(this) { exists ->
                    runOnUiThread {
                        if (!exists) {
                            ToastHelper.showToast(this, "Account not found in database. Please sign in again.")
                            drawer.layoutLoggedOut.visibility = View.VISIBLE
                            drawer.layoutLoggedIn.visibility = View.GONE
                            return@runOnUiThread
                        }

                        drawer.layoutLoggedOut.visibility = View.GONE
                        drawer.layoutLoggedIn.visibility = View.VISIBLE

                        val syncPrefs = getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE)
                        val broadcastOn = syncPrefs.getBoolean("broadcast_status_enabled", true)
                        val requestsOn = syncPrefs.getBoolean("allow_friend_requests", true)
                        if (drawer.switchBroadcastStatus.isChecked != broadcastOn) {
                            drawer.switchBroadcastStatus.isChecked = broadcastOn
                        }
                        if (drawer.switchAllowFriendRequests.isChecked != requestsOn) {
                            drawer.switchAllowFriendRequests.isChecked = requestsOn
                        }

                        updateUserOnlineStatus()

                        val activeUid = com.example.myapplication.model.FirebaseUserManager.getActiveUid(this@MainActivity) ?: "user"
                        val avatarFile = File(filesDir, "avatar_${activeUid}.jpg")
                        if (avatarFile.exists()) {
                            drawer.imgProfileAvatar.imageTintList = null
                            drawer.imgProfileAvatar.clearColorFilter()
                            Glide.with(this@MainActivity)
                                .load(avatarFile)
                                .signature(com.bumptech.glide.signature.ObjectKey(avatarFile.lastModified()))
                                .centerCrop()
                                .placeholder(R.drawable.baseline_account_circle_24)
                                .into(drawer.imgProfileAvatar)
                        } else {
                            drawer.imgProfileAvatar.setImageResource(R.drawable.baseline_account_circle_24)
                        }

                        com.example.myapplication.model.FirebaseUserManager.fetchCurrentProfile(this@MainActivity) { profile ->
                            runOnUiThread {
                                val username = profile?.username ?: com.example.myapplication.model.FirebaseUserManager.getLocalProfile(this@MainActivity)?.username ?: "User"
                                drawer.textProfileUsername.text = username
                                drawer.textProfileStatus.text = if (drawer.switchBroadcastStatus.isChecked) "Online" else "Offline"
                                drawer.textProfileStatus.setTextColor(
                                    if (drawer.switchBroadcastStatus.isChecked) android.graphics.Color.parseColor("#1DB954") else android.graphics.Color.parseColor("#FF5252")
                                )

                                val cloudAvatar = profile?.avatarUrl
                                if (!cloudAvatar.isNullOrEmpty()) {
                                    drawer.imgProfileAvatar.imageTintList = null
                                    drawer.imgProfileAvatar.clearColorFilter()
                                    val bitmap = com.example.myapplication.util.ImageUtils.decodeBase64ToBitmap(cloudAvatar)
                                    if (bitmap != null) {
                                        Glide.with(this@MainActivity)
                                            .load(bitmap)
                                            .centerCrop()
                                            .placeholder(R.drawable.baseline_account_circle_24)
                                            .into(drawer.imgProfileAvatar)

                                        Thread {
                                            try {
                                                val fileOut = avatarFile.outputStream()
                                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, fileOut)
                                                fileOut.close()
                                            } catch (_: Exception) {}
                                        }.start()
                                    }
                                } else if (avatarFile.exists()) {
                                    // Sync existing local avatar to Cloud Firestore for friends to see
                                    val uri = Uri.fromFile(avatarFile)
                                    val base64 = com.example.myapplication.util.ImageUtils.uriToBase64(this@MainActivity, uri, targetSize = 140)
                                    if (!base64.isNullOrEmpty()) {
                                        com.example.myapplication.model.FirebaseUserManager.updateUserAvatarUrl(this@MainActivity, base64)
                                    }
                                }
                            }
                        }

                        val currentSong = audioService?.getCurrentSong()
                        if (currentSong != null) {
                            drawer.textDrawerNowPlayingTitle.text = currentSong.title ?: "Unknown Song"
                            drawer.textDrawerNowPlayingArtist.text = currentSong.artist ?: "Unknown Artist"
                            Glide.with(this@MainActivity)
                                .load(currentSong.getAlbumArtTarget(this@MainActivity))
                                .centerCrop()
                                .placeholder(R.drawable.baseline_music_note_24)
                                .into(drawer.imgDrawerNowPlayingArt)
                        } else {
                            drawer.textDrawerNowPlayingTitle.text = "Not Playing"
                            drawer.textDrawerNowPlayingArtist.text = "Select a song"
                            drawer.imgDrawerNowPlayingArt.setImageResource(R.drawable.baseline_music_note_24)
                        }

                        com.example.myapplication.model.FirebaseUserManager.listenToPendingRequests(this@MainActivity) { requests ->
                            runOnUiThread {
                                if (requests.isNotEmpty()) {
                                    drawer.layoutPendingRequests.visibility = View.VISIBLE
                                    drawer.containerPendingRequests.removeAllViews()
                                    requests.forEach { req ->
                                        val reqView = layoutInflater.inflate(R.layout.item_pending_request, null)
                                        val reqCardView = reqView.findViewById<androidx.cardview.widget.CardView>(R.id.cardPendingRequest)
                                        val currentTheme = com.example.myapplication.model.ThemeManager.getSelectedTheme(this@MainActivity)
                                        reqCardView?.setCardBackgroundColor(android.graphics.Color.parseColor(currentTheme.cardColorHex))

                                        val reqNameText = reqView.findViewById<TextView>(R.id.textReqName)
                                        reqNameText.text = "${req.senderUsername} wants to be friends"
                                        reqNameText.setTextColor(android.graphics.Color.WHITE)
                                        val btnAccept = reqView.findViewById<Button>(R.id.btnReqAccept)
                                        val btnDecline = reqView.findViewById<Button>(R.id.btnReqDecline)
                                        com.example.myapplication.util.DialogThemeHelper.stylePrimaryButton(btnAccept, this@MainActivity)
                                        com.example.myapplication.util.DialogThemeHelper.styleSecondaryButton(btnDecline, this@MainActivity)

                                        btnAccept.setOnClickListener {
                                            com.example.myapplication.model.FirebaseUserManager.acceptFriendRequest(this@MainActivity, req) { ok ->
                                                if (ok) ToastHelper.showToast(this@MainActivity, "Accepted friend request!")
                                            }
                                        }
                                        btnDecline.setOnClickListener {
                                            com.example.myapplication.model.FirebaseUserManager.declineFriendRequest(req.requestId) { _ -> }
                                        }
                                        drawer.containerPendingRequests.addView(reqView)
                                    }
                                } else {
                                    drawer.layoutPendingRequests.visibility = View.GONE
                                }
                            }
                        }

                        drawer.recyclerViewFriends.layoutManager = LinearLayoutManager(this@MainActivity)
                        com.example.myapplication.model.FirebaseUserManager.listenToFriendsList(this@MainActivity) { friends ->
                            runOnUiThread {
                                if (friends.isNotEmpty()) {
                                    drawer.textFriendsEmpty.visibility = View.GONE
                                    drawer.recyclerViewFriends.visibility = View.VISIBLE

                                    val currentAdapter = drawer.recyclerViewFriends.adapter as? com.example.myapplication.adapter.FriendAdapter
                                    if (currentAdapter != null) {
                                        currentAdapter.updateFriends(friends)
                                    } else {
                                        drawer.recyclerViewFriends.adapter = com.example.myapplication.adapter.FriendAdapter(friends) { friendToRemove ->
                                            showRemoveFriendConfirmationDialog(friendToRemove)
                                        }
                                    }
                                } else {
                                    drawer.textFriendsEmpty.visibility = View.VISIBLE
                                    drawer.recyclerViewFriends.visibility = View.GONE
                                    drawer.recyclerViewFriends.adapter = null
                                }
                            }
                        }
                    }
                }
            } else {
                drawer.layoutLoggedOut.visibility = View.VISIBLE
                drawer.layoutLoggedIn.visibility = View.GONE
            }

            drawer.navDrawerContainer.requestLayout()
            drawer.navDrawerScrollView.requestLayout()
            drawer.navDrawerContainer.invalidate()
            drawer.navDrawerScrollView.invalidate()
        }
    }

    private fun setupTabHeaderNavigation() {
        binding.viewPagerMain.isUserInputEnabled = true

        binding.tabHome.setOnClickListener {
            binding.viewPagerMain.currentItem = 0
        }

        binding.tabPlaylist.setOnClickListener {
            binding.viewPagerMain.currentItem = 1
        }

        binding.tabEqualizer.setOnClickListener {
            binding.viewPagerMain.currentItem = 2
        }

        binding.tabStats.setOnClickListener {
            binding.viewPagerMain.currentItem = 3
        }

        binding.tabThemes.setOnClickListener {
            binding.viewPagerMain.currentItem = 4
        }

        binding.viewPagerMain.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                selectTab(position)
            }
        })
    }

    private val themeChangeListener: (com.example.myapplication.model.AppTheme) -> Unit = { theme ->
        runOnUiThread {
            applyAppTheme(theme)
        }
    }

    private fun applyAppTheme(theme: com.example.myapplication.model.AppTheme) {
        val bgColor = android.graphics.Color.parseColor(theme.bgColorHex)
        val cardColor = android.graphics.Color.parseColor(theme.cardColorHex)
        val accentColor = android.graphics.Color.parseColor(theme.accentColorHex)

        binding.PlayerRoot.setBackgroundColor(bgColor)
        binding.miniPlayerLayout.setBackgroundColor(cardColor)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = bgColor
            window.navigationBarColor = bgColor
        }

        // Apply theme to Profile Drawer & Controls
        val drawer = binding.navDrawerProfile
        drawer.navDrawerScrollView.setBackgroundColor(bgColor)
        drawer.cardDrawerNowPlaying.setCardBackgroundColor(cardColor)
        drawer.cardAccountSettings.setCardBackgroundColor(cardColor)
        drawer.btnCloseDrawer.setColorFilter(accentColor)
        drawer.textPendingRequestsHeader.setTextColor(accentColor)

        val switchThumbStateList = android.content.res.ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                accentColor,
                android.graphics.Color.parseColor("#888888")
            )
        )

        val trackCheckedColor = androidx.core.graphics.ColorUtils.blendARGB(accentColor, android.graphics.Color.BLACK, 0.45f)
        val switchTrackStateList = android.content.res.ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                trackCheckedColor,
                android.graphics.Color.parseColor("#444444")
            )
        )

        drawer.switchBroadcastStatus.thumbTintList = switchThumbStateList
        drawer.switchBroadcastStatus.trackTintList = switchTrackStateList
        drawer.switchAllowFriendRequests.thumbTintList = switchThumbStateList
        drawer.switchAllowFriendRequests.trackTintList = switchTrackStateList

        com.example.myapplication.util.DialogThemeHelper.styleEditText(drawer.inputAuthEmail, this)
        com.example.myapplication.util.DialogThemeHelper.styleEditText(drawer.inputAuthUsername, this)
        com.example.myapplication.util.DialogThemeHelper.styleEditText(drawer.inputAuthPassword, this)
        com.example.myapplication.util.DialogThemeHelper.styleEditText(drawer.inputSearchFriend, this)

        com.example.myapplication.util.DialogThemeHelper.stylePrimaryButton(drawer.btnSignIn, this)
        com.example.myapplication.util.DialogThemeHelper.stylePrimaryButton(drawer.btnAddFriend, this)
        com.example.myapplication.util.DialogThemeHelper.styleSecondaryButton(drawer.btnSignUp, this)
        com.example.myapplication.util.DialogThemeHelper.styleSecondaryButton(drawer.btnGoogleAuth, this)

        selectTab(binding.viewPagerMain.currentItem)
    }

    override fun onStart() {
        super.onStart()
        val serviceIntent = Intent(this, AudioService::class.java)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    private fun registerNetworkCallback() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return
        networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                runOnUiThread {
                    updateUserOnlineStatus()
                }
            }

            override fun onLost(network: android.net.Network) {
                runOnUiThread {
                    updateUserOnlineStatus()
                }
            }
        }
        try {
            val request = android.net.NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, networkCallback!!)
        } catch (_: Exception) {}
    }

    fun updateUserOnlineStatus() {
        val isLoggedIn = com.example.myapplication.model.FirebaseUserManager.isUserLoggedIn(this)
        if (!isLoggedIn) return

        val isOnline = com.example.myapplication.model.FirebaseUserManager.isNetworkConnected(this)
        val currentSong = audioService?.getCurrentSong()
        val isPlaying = audioService?.player?.isPlaying == true
        val broadcastEnabled = getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE)
            .getBoolean("broadcast_status_enabled", true)

        val drawer = binding.navDrawerProfile

        // Forced Offline via slider or disconnected network
        if (!broadcastEnabled || !isOnline) {
            com.example.myapplication.model.FirebaseUserManager.updatePresence(this, isOnline = false, customStatus = "Offline")
            com.example.myapplication.model.FirebaseUserManager.updateNowPlaying(this, null, null, null)

            drawer.textProfileStatus.text = "Offline"
            drawer.textProfileStatus.setTextColor(android.graphics.Color.parseColor("#FF5252"))
            drawer.textDrawerNowPlayingTitle.text = "Not Playing"
            drawer.textDrawerNowPlayingArtist.text = "Select a song"
            drawer.imgDrawerNowPlayingArt.setImageResource(R.drawable.baseline_music_note_24)
            return
        }

        // User is ONLINE in Green (#1DB954)
        com.example.myapplication.model.FirebaseUserManager.updatePresence(this, isOnline = true, customStatus = "Online")

        drawer.textProfileStatus.text = "Online"
        drawer.textProfileStatus.setTextColor(android.graphics.Color.parseColor("#1DB954"))

        if (currentSong != null) {
            val artTarget = currentSong.getAlbumArtTarget(this)
            var artUrl = ""
            if (artTarget is File && artTarget.exists()) {
                val uri = Uri.fromFile(artTarget)
                artUrl = com.example.myapplication.util.ImageUtils.uriToBase64(this, uri, targetSize = 140) ?: ""
            } else if (artTarget is Uri) {
                artUrl = com.example.myapplication.util.ImageUtils.uriToBase64(this, artTarget, targetSize = 140) ?: ""
            }

            com.example.myapplication.model.FirebaseUserManager.updateNowPlaying(this, currentSong.title, currentSong.artist, artUrl)

            drawer.textDrawerNowPlayingTitle.text = currentSong.title ?: "Unknown Song"
            drawer.textDrawerNowPlayingArtist.text = currentSong.artist ?: "Unknown Artist"
            Glide.with(this)
                .load(currentSong.getAlbumArtTarget(this))
                .centerCrop()
                .placeholder(R.drawable.baseline_music_note_24)
                .into(drawer.imgDrawerNowPlayingArt)
        } else {
            com.example.myapplication.model.FirebaseUserManager.updateNowPlaying(this, null, null, null)
            drawer.textDrawerNowPlayingTitle.text = "Not Playing"
            drawer.textDrawerNowPlayingArtist.text = "Select a song"
            drawer.imgDrawerNowPlayingArt.setImageResource(R.drawable.baseline_music_note_24)
        }
    }

    private val presenceHandler = Handler(Looper.getMainLooper())
    private val presenceRunnable = object : Runnable {
        override fun run() {
            updateUserOnlineStatus()
            presenceHandler.postDelayed(this, 15000)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        presenceHandler.removeCallbacks(presenceRunnable)
        com.example.myapplication.model.FirebaseUserManager.updatePresence(this, isOnline = false, customStatus = "Offline")
        com.example.myapplication.model.FirebaseUserManager.updateNowPlaying(this, null, null, null)
        if (networkCallback != null) {
            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                cm?.unregisterNetworkCallback(networkCallback!!)
            } catch (_: Exception) {}
        }
        com.example.myapplication.model.ThemeManager.removeThemeChangeListener(themeChangeListener)
    }

    override fun onResume() {
        super.onResume()
        presenceHandler.removeCallbacks(presenceRunnable)
        presenceHandler.post(presenceRunnable)
        if (isBound) {
            audioService?.addStateChangeListener(stateListener)
            updateMiniPlayerUI()
        }
    }

    override fun onPause() {
        super.onPause()
        presenceHandler.removeCallbacks(presenceRunnable)
        activeQueueToast?.cancel()
        handle.removeCallbacks(updateProgressRunnable)
    }

    override fun onStop() {
        super.onStop()
        presenceHandler.removeCallbacks(presenceRunnable)
        if (isFinishing) {
            com.example.myapplication.model.FirebaseUserManager.updatePresence(this, isOnline = false, customStatus = "Offline")
            com.example.myapplication.model.FirebaseUserManager.updateNowPlaying(this, null, null, null)
        }
        activeQueueToast?.cancel()
        handle.removeCallbacks(updateProgressRunnable)
        if (isBound) {
            audioService?.removeStateChangeListener(stateListener)
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun setupMiniPlayerControls() {
        binding.miniPlayerLayout.setOnClickListener {
            val srv = audioService ?: return@setOnClickListener
            if (srv.getCurrentSong() == null) return@setOnClickListener
            val activeList = srv.getActiveList()
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("songList", ArrayList(activeList))
                putExtra("position", srv.currentIndex)
            }
            startActivity(intent)
        }

        binding.miniBtnPlay.setOnClickListener {
            audioService?.togglePlayPause()
            updateMiniPlayerUI()
        }

        binding.miniBtnPrev.setOnClickListener {
            audioService?.playPrevious()
            updateMiniPlayerUI()
        }

        binding.miniBtnNext.setOnClickListener {
            audioService?.playNext()
            updateMiniPlayerUI()
        }

        binding.miniSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val srv = audioService ?: return
                    val p = srv.player ?: return
                    val duration = p.duration
                    if (duration > 0) {
                        val seekMs = (progress.toFloat() / 1000f * duration).toLong()
                        val seconds = (seekMs / 1000).toInt()
                        val minutes = seconds / 60
                        val secs = seconds % 60
                        binding.miniTextElapsed.text = String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserScrubbingMini = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val srv = audioService ?: return
                val p = srv.player ?: return
                val duration = p.duration
                if (duration > 0 && seekBar != null) {
                    val targetMs = (seekBar.progress.toFloat() / 1000f * duration).toLong()
                    p.seekTo(targetMs)
                }
                isUserScrubbingMini = false
            }
        })
    }

    fun updateMiniPlayerUI() {
        val srv = audioService ?: run {
            binding.miniPlayerLayout.visibility = View.GONE
            if (::pagerAdapter.isInitialized && pagerAdapter.songsFragment.songAdapter != null) {
                pagerAdapter.songsFragment.songAdapter?.setPlayingSongId(null)
            }
            handle.removeCallbacks(updateProgressRunnable)
            return
        }

        val song = srv.getCurrentSong()
        if (song == null) {
            binding.miniPlayerLayout.visibility = View.GONE
            if (::pagerAdapter.isInitialized && pagerAdapter.songsFragment.songAdapter != null) {
                pagerAdapter.songsFragment.songAdapter?.setPlayingSongId(null)
            }
            handle.removeCallbacks(updateProgressRunnable)
            return
        }

        binding.miniPlayerLayout.visibility = View.VISIBLE
        if (::pagerAdapter.isInitialized && pagerAdapter.songsFragment.songAdapter != null) {
            pagerAdapter.songsFragment.songAdapter?.setPlayingSongId(song.id)
        }

        val newTitle = song.title ?: ""
        if (binding.miniTextTitle.text != newTitle) {
            binding.miniTextTitle.text = newTitle
            binding.miniTextTitle.isSelected = true
        }
        binding.miniTextArtist.text = song.artist ?: ""

        val isPlaying = srv.player?.let { it.isPlaying || (it.playWhenReady && it.playbackState != Player.STATE_ENDED) } == true
        binding.miniBtnPlay.setImageResource(
            if (isPlaying) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24
        )

        val albumArtTarget = song.getAlbumArtTarget(this)

        Glide.with(this)
            .asBitmap()
            .load(albumArtTarget)
            .centerCrop()
            .placeholder(R.drawable.baseline_music_note_24)
            .into(binding.miniAlbumArt)

        handle.removeCallbacks(updateProgressRunnable)
        handle.post(updateProgressRunnable)
    }

    private fun checkPermissionAndLoadSong() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionsToRequest = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }

            if (permissionsToRequest.isEmpty()) {
                loadSongs()
            } else {
                requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                loadSongs()
            } else {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
            }
        }
    }

    private fun loadSongs() {
        reloadSongs()
    }

    fun scanDeviceForNewSongs() {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val filesToScan = mutableListOf<String>()

        fun collectAudioFiles(dir: File?) {
            if (dir == null || !dir.exists()) return
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    collectAudioFiles(file)
                } else if (file.isFile) {
                    val ext = file.extension.lowercase(Locale.getDefault())
                    if (ext == "mp3" || ext == "m4a" || ext == "wav" || ext == "flac" || ext == "aac" || ext == "ogg") {
                        filesToScan.add(file.absolutePath)
                    }
                }
            }
        }

        try {
            collectAudioFiles(musicDir)
            collectAudioFiles(downloadDir)
        } catch (_: Exception) {}

        if (filesToScan.isNotEmpty()) {
            MediaScannerConnection.scanFile(
                this,
                filesToScan.toTypedArray(),
                null
            ) { _, _ ->
                runOnUiThread {
                    reloadSongs()
                }
            }
        } else {
            reloadSongs()
        }
    }

    fun reloadSongs() {
        songList = getSongs()
        if (::pagerAdapter.isInitialized) {
            pagerAdapter.songsFragment.refreshSongsList()
        } else {
            pagerAdapter = MainPagerAdapter(this)
            binding.viewPagerMain.adapter = pagerAdapter
        }
    }

    private fun saveSongMetadataToPrefs(songId: Long, title: String, artist: String) {
        val prefs = getSharedPreferences("custom_song_metadata", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("title_$songId", title)
            .putString("artist_$songId", artist)
            .apply()
    }

    private fun saveSongOrderToPrefs(songs: List<Song>) {
        val prefs = getSharedPreferences("custom_song_order", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        songs.forEach { jsonArray.put(it.id) }
        prefs.edit().putString("saved_song_id_order", jsonArray.toString()).apply()
    }

    private fun getSongs(): List<Song> {
        val rawSongsMap = mutableMapOf<Long, Song>()
        val rawSongsList = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        val prefsMetadata = getSharedPreferences("custom_song_metadata", Context.MODE_PRIVATE)

        contentResolver.query(uri, null, selection, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumns = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                var title = cursor.getString(titleColumn)
                var artist = cursor.getString(artistColumns)
                val data = cursor.getString(dataColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val duration = cursor.getLong(durationColumn)

                val savedTitle = prefsMetadata.getString("title_$id", null)
                val savedArtist = prefsMetadata.getString("artist_$id", null)

                if (!savedTitle.isNullOrEmpty()) {
                    title = savedTitle
                }
                if (!savedArtist.isNullOrEmpty()) {
                    artist = savedArtist
                }

                val song = Song(id, title, artist, data, albumId, duration)
                rawSongsMap[id] = song
                rawSongsList.add(song)
            }
        }

        val prefsOrder = getSharedPreferences("custom_song_order", Context.MODE_PRIVATE)
        val savedOrderJson = prefsOrder.getString("saved_song_id_order", null)

        if (!savedOrderJson.isNullOrEmpty()) {
            try {
                val orderedSongs = mutableListOf<Song>()
                val jsonArray = JSONArray(savedOrderJson)
                for (i in 0 until jsonArray.length()) {
                    val songId = jsonArray.getLong(i)
                    val song = rawSongsMap.remove(songId)
                    if (song != null) {
                        orderedSongs.add(song)
                    }
                }
                orderedSongs.addAll(rawSongsMap.values)
                return orderedSongs
            } catch (e: Exception) {
                // Ignore parse error
            }
        }

        return rawSongsList
    }

    fun getSongList(): List<Song> = songList

    fun showQueueNotification(message: String) {
        ToastHelper.showToast(this, message)
    }

    fun addSongToQueue(song: Song) {
        val srv = audioService
        if (srv != null) {
            srv.addToQueueEnd(song)
            showQueueNotification("Added \"${song.title ?: "Song"}\" to queue")
        }
    }

    fun addSongToQueueByPosition(position: Int) {
        if (position in songList.indices) {
            addSongToQueue(songList[position])
        }
    }

    fun onSongReordered(fromPos: Int, toPos: Int) {
        if (fromPos in songList.indices && toPos in songList.indices) {
            Collections.swap(songList, fromPos, toPos)
            saveSongOrderToPrefs(songList)
            val srv = audioService
            if (srv != null) {
                srv.songList = songList
                if (!srv.isShuffle) {
                    srv.shuffledList = songList.toMutableList()
                }
            }
        }
    }

    fun sortSongsByName() {
        val sorted = songList.sortedWith(compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { song -> song.title ?: "" })
        songList = sorted.toMutableList()
        saveSongOrderToPrefs(songList)
        getSharedPreferences("custom_song_order", Context.MODE_PRIVATE).edit().putString("current_sort_mode", "name").apply()
        val srv = audioService
        if (srv != null) {
            srv.songList = songList
            if (!srv.isShuffle) {
                srv.shuffledList = songList.toMutableList()
            }
        }
    }

    fun sortSongsByArtist() {
        val sorted = songList.sortedWith(
            compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { song -> song.artist ?: "" }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { song -> song.title ?: "" }
        )
        songList = sorted.toMutableList()
        saveSongOrderToPrefs(songList)
        getSharedPreferences("custom_song_order", Context.MODE_PRIVATE).edit().putString("current_sort_mode", "artist").apply()
        val srv = audioService
        if (srv != null) {
            srv.songList = songList
            if (!srv.isShuffle) {
                srv.shuffledList = songList.toMutableList()
            }
        }
    }

    override fun onItemClick(position: Int) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("songList", ArrayList(songList))
            putExtra("position", position)
        }
        startActivity(intent)
    }

    fun showSongOptionsMenu(song: Song, anchorView: View? = null) {
        val actualPosition = songList.indexOfFirst { it.id == song.id }

        val dialog = BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
        val sheetView = layoutInflater.inflate(R.layout.dialog_song_options_bottom_sheet, null)

        val sheetAlbumArt = sheetView.findViewById<ImageView>(R.id.sheetAlbumArt)
        val sheetTitle = sheetView.findViewById<TextView>(R.id.sheetSongTitle)
        val sheetArtist = sheetView.findViewById<TextView>(R.id.sheetSongArtist)

        val optionPlayNext = sheetView.findViewById<LinearLayout>(R.id.optionPlayNext)
        val optionAddToQueue = sheetView.findViewById<LinearLayout>(R.id.optionAddToQueue)
        val optionAddToPlaylist = sheetView.findViewById<LinearLayout>(R.id.optionAddToPlaylist)
        val optionEditInfo = sheetView.findViewById<LinearLayout>(R.id.optionEditInfo)

        sheetTitle.text = song.title ?: "Unknown Song"
        sheetArtist.text = song.artist ?: "Unknown Artist"

        Glide.with(this)
            .load(song.getAlbumArtTarget(this))
            .centerCrop()
            .placeholder(R.drawable.baseline_music_note_24)
            .into(sheetAlbumArt)

        optionPlayNext.setOnClickListener {
            dialog.dismiss()
            val srv = audioService
            if (srv != null) {
                srv.playNextSongInQueue(song)
                showQueueNotification("Added \"${song.title ?: "Song"}\" to play next")
            }
        }

        optionAddToQueue.setOnClickListener {
            dialog.dismiss()
            val srv = audioService
            if (srv != null) {
                srv.addToQueueEnd(song)
                showQueueNotification("Added \"${song.title ?: "Song"}\" to queue")
            }
        }

        optionAddToPlaylist.setOnClickListener {
            dialog.dismiss()
            showAddToPlaylistDialog(song)
        }

        optionEditInfo.setOnClickListener {
            dialog.dismiss()
            if (actualPosition != -1) {
                showEditSongDialog(actualPosition)
            } else {
                ToastHelper.showToast(this, "Song not found in library")
            }
        }

        val optionSetRingtone = sheetView.findViewById<LinearLayout>(R.id.optionSetRingtone)
        optionSetRingtone?.setOnClickListener {
            dialog.dismiss()
            setSongAsPhoneRingtone(song)
        }

        dialog.setContentView(sheetView)
        com.example.myapplication.util.DialogThemeHelper.styleBottomSheet(dialog, this)
        dialog.show()
    }

    fun showSongOptionsMenu(position: Int, anchorView: View) {
        if (position in songList.indices) {
            showSongOptionsMenu(songList[position], anchorView)
        }
    }

    private fun showAddToPlaylistDialog(song: Song) {
        val playlists = PlaylistManager.getPlaylists(this)

        if (playlists.isEmpty()) {
            val dialog = AlertDialog.Builder(this).create()
            val view = layoutInflater.inflate(R.layout.dialog_input_text, null)

            val titleText = view.findViewById<TextView>(R.id.dialogInputTitle)
            val subMsgText = view.findViewById<TextView>(R.id.dialogInputSubMessage)
            val input = view.findViewById<EditText>(R.id.dialogInputField)
            val btnCancel = view.findViewById<Button>(R.id.btnInputCancel)
            val btnConfirm = view.findViewById<Button>(R.id.btnInputConfirm)

            titleText.text = "No Playlists Found"
            subMsgText.visibility = View.VISIBLE
            subMsgText.text = "Create a new playlist to add this song:"
            input.hint = "Playlist Name"
            btnConfirm.text = "Create & Add"

            btnCancel.setOnClickListener { dialog.dismiss() }

            btnConfirm.setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    dialog.dismiss()
                    val newPlaylist = Playlist(
                        id = System.currentTimeMillis(),
                        name = name,
                        songIds = mutableListOf(song.id)
                    )
                    playlists.add(newPlaylist)
                    PlaylistManager.savePlaylists(this, playlists)
                    if (::pagerAdapter.isInitialized) {
                        pagerAdapter.playlistsFragment.refreshPlaylists()
                    }
                    ToastHelper.showToast(this, "Created \"$name\" and added song")
                } else {
                    ToastHelper.showToast(this, "Name cannot be empty")
                }
            }

            dialog.setView(view)
            com.example.myapplication.util.DialogThemeHelper.styleDialogView(view, this)
            com.example.myapplication.util.DialogThemeHelper.styleEditText(input, this)
            com.example.myapplication.util.DialogThemeHelper.stylePrimaryButton(btnConfirm, this)
            com.example.myapplication.util.DialogThemeHelper.styleSecondaryButton(btnCancel, this)
            com.example.myapplication.util.DialogThemeHelper.removeDialogWindowBorders(dialog)
            dialog.show()
        } else {
            val dialog = AlertDialog.Builder(this).create()
            val view = layoutInflater.inflate(R.layout.dialog_select_playlist, null)

            val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewSelectPlaylist)
            val btnCancel = view.findViewById<Button>(R.id.btnSelectPlaylistCancel)

            btnCancel.setOnClickListener { dialog.dismiss() }

            val optionsList = mutableListOf<Playlist>()
            val newPlaylistItem = Playlist(id = -1L, name = "+ New Playlist", songIds = mutableListOf())
            optionsList.add(newPlaylistItem)
            optionsList.addAll(playlists)

            recyclerView.layoutManager = LinearLayoutManager(this)
            val adapter = PlaylistAdapter(optionsList, object : PlaylistAdapter.OnPlaylistClickListener {
                override fun onPlaylistClick(position: Int) {
                    dialog.dismiss()
                    if (position == 0) {
                        val createDialog = AlertDialog.Builder(this@MainActivity).create()
                        val createView = layoutInflater.inflate(R.layout.dialog_input_text, null)

                        val titleText = createView.findViewById<TextView>(R.id.dialogInputTitle)
                        val input = createView.findViewById<EditText>(R.id.dialogInputField)
                        val btnCancelNew = createView.findViewById<Button>(R.id.btnInputCancel)
                        val btnConfirmNew = createView.findViewById<Button>(R.id.btnInputConfirm)

                        titleText.text = "New Playlist"
                        input.hint = "Playlist Name"
                        btnConfirmNew.text = "Create & Add"

                        btnCancelNew.setOnClickListener { createDialog.dismiss() }

                        btnConfirmNew.setOnClickListener {
                            val name = input.text.toString().trim()
                            if (name.isNotEmpty()) {
                                createDialog.dismiss()
                                val newPlaylist = Playlist(
                                    id = System.currentTimeMillis(),
                                    name = name,
                                    songIds = mutableListOf(song.id)
                                )
                                playlists.add(newPlaylist)
                                PlaylistManager.savePlaylists(this@MainActivity, playlists)
                                if (::pagerAdapter.isInitialized) {
                                    pagerAdapter.playlistsFragment.refreshPlaylists()
                                }
                                ToastHelper.showToast(this@MainActivity, "Created \"$name\" and added song")
                            } else {
                                ToastHelper.showToast(this@MainActivity, "Name cannot be empty")
                            }
                        }

                        createDialog.setView(createView)
                        createDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                        createDialog.show()
                    } else {
                        val selectedPlaylist = playlists[position - 1]
                        if (!selectedPlaylist.songIds.contains(song.id)) {
                            selectedPlaylist.songIds.add(song.id)
                            PlaylistManager.savePlaylists(this@MainActivity, playlists)
                            if (::pagerAdapter.isInitialized) {
                                pagerAdapter.playlistsFragment.refreshPlaylists()
                            }
                            ToastHelper.showToast(this@MainActivity, "Added to \"${selectedPlaylist.name}\"")
                        } else {
                            ToastHelper.showToast(this@MainActivity, "Song already in \"${selectedPlaylist.name}\"")
                        }
                    }
                }
            })
            recyclerView.adapter = adapter

            dialog.setView(view)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.show()
        }
    }

    fun showEditSongDialog(position: Int) {
        if (position !in songList.indices) return
        val song = songList[position]
        tempSelectedImageUri = null

        val dialog = AlertDialog.Builder(this).create()
        val view = layoutInflater.inflate(R.layout.dialog_edit_song, null)

        val coverPreview = view.findViewById<ImageView>(R.id.editSongCoverPreview)
        val btnChangeArt = view.findViewById<Button>(R.id.btnChangeCoverImage)
        val inputTitle = view.findViewById<EditText>(R.id.inputEditSongTitle)
        val inputArtist = view.findViewById<EditText>(R.id.inputEditSongArtist)
        val btnCancel = view.findViewById<Button>(R.id.btnEditSongCancel)
        val btnSave = view.findViewById<Button>(R.id.btnEditSongSave)

        activeDialogCoverPreview = coverPreview

        Glide.with(this)
            .load(song.getAlbumArtTarget(this))
            .placeholder(R.drawable.baseline_music_note_24)
            .into(coverPreview)

        inputTitle.setText(song.title)
        inputArtist.setText(song.artist)

        btnChangeArt.setOnClickListener {
            val sourceDialog = BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
            val sourceView = layoutInflater.inflate(R.layout.dialog_choose_image_source, null)

            val theme = com.example.myapplication.model.ThemeManager.getSelectedTheme(this)
            val cardColor = android.graphics.Color.parseColor(theme.cardColorHex)
            val accentColor = android.graphics.Color.parseColor(theme.accentColorHex)

            val cardDevice = sourceView.findViewById<CardView>(R.id.cardOptionDevice)
            val cardWeb = sourceView.findViewById<CardView>(R.id.cardOptionWeb)
            val iconDevice = sourceView.findViewById<ImageView>(R.id.iconOptionDevice)
            val iconWeb = sourceView.findViewById<ImageView>(R.id.iconOptionWeb)
            val btnCancelSource = sourceView.findViewById<Button>(R.id.btnCancelSource)

            cardDevice?.setCardBackgroundColor(cardColor)
            cardWeb?.setCardBackgroundColor(cardColor)
            iconDevice?.setColorFilter(accentColor)
            iconWeb?.setColorFilter(accentColor)

            if (btnCancelSource != null) {
                com.example.myapplication.util.DialogThemeHelper.styleSecondaryButton(btnCancelSource, this)
                btnCancelSource.setOnClickListener { sourceDialog.dismiss() }
            }

            cardDevice?.setOnClickListener {
                sourceDialog.dismiss()
                pickImageLauncher.launch("image/*")
            }

            cardWeb?.setOnClickListener {
                sourceDialog.dismiss()
                val currentTitle = inputTitle.text.toString().trim()
                val currentArtist = inputArtist.text.toString().trim()
                val query = "$currentTitle $currentArtist album cover".trim()
                val webIntent = Intent(this, WebCoverPickerActivity::class.java).apply {
                    putExtra("search_query", query)
                }
                webCoverLauncher.launch(webIntent)
            }

            sourceDialog.setContentView(sourceView)
            com.example.myapplication.util.DialogThemeHelper.styleBottomSheet(sourceDialog, this)
            sourceDialog.show()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val newTitle = inputTitle.text.toString().trim()
            val newArtist = inputArtist.text.toString().trim()
            val imageToSave = tempSelectedImageUri
            if (newTitle.isNotEmpty()) {
                dialog.dismiss()
                updateSongMetadata(position, newTitle, newArtist, imageToSave)
            } else {
                ToastHelper.showToast(this, "Title cannot be empty")
            }
        }

        dialog.setView(view)
        com.example.myapplication.util.DialogThemeHelper.styleDialogView(view, this)
        com.example.myapplication.util.DialogThemeHelper.styleEditText(inputTitle, this)
        com.example.myapplication.util.DialogThemeHelper.styleEditText(inputArtist, this)
        com.example.myapplication.util.DialogThemeHelper.styleSecondaryButton(btnChangeArt, this)
        com.example.myapplication.util.DialogThemeHelper.styleSecondaryButton(btnCancel, this)
        com.example.myapplication.util.DialogThemeHelper.stylePrimaryButton(btnSave, this)
        com.example.myapplication.util.DialogThemeHelper.removeDialogWindowBorders(dialog)
        dialog.show()
    }

    fun setSongAsPhoneRingtone(song: Song) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.System.canWrite(this)) {
                ToastHelper.showToast(this, "Please allow modifying system settings to set ringtone")
                try {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
                return
            }
        }

        try {
            val path = song.data
            if (path.isNullOrEmpty()) {
                ToastHelper.showToast(this, "Audio file path invalid")
                return
            }
            val file = File(path)
            if (!file.exists()) {
                ToastHelper.showToast(this, "Audio file not found on disk")
                return
            }

            val songUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
            android.media.RingtoneManager.setActualDefaultRingtoneUri(
                this,
                android.media.RingtoneManager.TYPE_RINGTONE,
                songUri
            )
            ToastHelper.showToast(this, "Set \"${song.title ?: "Song"}\" as call ringtone!")
        } catch (e: Exception) {
            ToastHelper.showToast(this, "Error setting ringtone: ${e.message}")
        }
    }

    private fun updateSongMetadata(position: Int, newTitle: String, newArtist: String, newImageUri: Uri?) {
        if (position !in songList.indices) return
        val song = songList[position]

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, newTitle)
            put(MediaStore.Audio.Media.ARTIST, newArtist)
        }
        val songUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)

        try {
            val updatedRows = contentResolver.update(songUri, values, null, null)
            if (updatedRows > 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                applySongEditToApp(position, newTitle, newArtist, newImageUri)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pendingEditSong = PendingEdit(position, newTitle, newArtist, newImageUri)
                val writeRequest = MediaStore.createWriteRequest(contentResolver, listOf(songUri))
                editMediaLauncher.launch(IntentSenderRequest.Builder(writeRequest).build())
            } else {
                applySongEditToApp(position, newTitle, newArtist, newImageUri)
            }
        } catch (securityException: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val recoverableSecurityException = securityException as? RecoverableSecurityException
                val intentSender = recoverableSecurityException?.userAction?.actionIntent?.intentSender
                if (intentSender != null) {
                    pendingEditSong = PendingEdit(position, newTitle, newArtist, newImageUri)
                    editMediaLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pendingEditSong = PendingEdit(position, newTitle, newArtist, newImageUri)
                    val writeRequest = MediaStore.createWriteRequest(contentResolver, listOf(songUri))
                    editMediaLauncher.launch(IntentSenderRequest.Builder(writeRequest).build())
                } else {
                    ToastHelper.showToast(this, "Permission required to edit song")
                }
            } else {
                ToastHelper.showToast(this, "Failed to edit song")
            }
        } catch (e: Exception) {
            ToastHelper.showToast(this, "Error updating song: ${e.message}")
        }
    }

    private fun saveCustomAlbumArt(songId: Long, albumId: Long, imageUri: Uri) {
        try {
            val oldSharedFile = File(filesDir, "albumart_$albumId.jpg")
            if (oldSharedFile.exists()) {
                oldSharedFile.delete()
            }

            val songFile = File(filesDir, "albumart_$songId.jpg")
            contentResolver.openInputStream(imageUri)?.use { input ->
                val bytes = input.readBytes()
                songFile.writeBytes(bytes)
            }
            Glide.get(applicationContext).clearMemory()
            Thread {
                Glide.get(applicationContext).clearDiskCache()
            }.start()
        } catch (e: Exception) {
            ToastHelper.showToast(this, "Failed to save cover image")
        }
    }

    private fun applySongEditToApp(position: Int, newTitle: String, newArtist: String, newImageUri: Uri?) {
        val oldSong = songList[position]

        saveSongMetadataToPrefs(oldSong.id, newTitle, newArtist)

        if (newImageUri != null) {
            saveCustomAlbumArt(oldSong.id, oldSong.albumId, newImageUri)
        }

        val updatedSong = Song(oldSong.id, newTitle, newArtist, oldSong.data, oldSong.albumId, oldSong.duration)
        (songList as? MutableList)?.set(position, updatedSong)
        if (::pagerAdapter.isInitialized) {
            val adapterSongs = pagerAdapter.songsFragment.songAdapter?.songs
            if (adapterSongs != null && position in adapterSongs.indices) {
                adapterSongs[position] = updatedSong
                pagerAdapter.songsFragment.songAdapter?.notifyItemChanged(position)
            }
        }

        val srv = audioService
        if (srv != null) {
            srv.updateActiveSongMetadata(updatedSong)
        }

        updateMiniPlayerUI()

        ToastHelper.showToast(this, "Song updated permanently")
    }
}
