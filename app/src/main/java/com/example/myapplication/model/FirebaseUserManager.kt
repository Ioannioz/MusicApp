package com.example.myapplication.model

import android.content.Context
import java.util.Locale
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val status: String = "Online",
    val allowFriendRequests: Boolean = true,
    val lastActiveTimestamp: Long = System.currentTimeMillis(),
    val nowPlayingTitle: String? = null,
    val nowPlayingArtist: String? = null,
    val nowPlayingArtUrl: String? = null
)

data class FriendRequest(
    val requestId: String = "",
    val senderUid: String = "",
    val senderUsername: String = "",
    val receiverUid: String = "",
    val status: String = "PENDING"
)

object FirebaseUserManager {

    private const val LOCAL_PREFS = "user_profile_prefs"

    private fun getAuth(): FirebaseAuth? = try { FirebaseAuth.getInstance() } catch (_: Exception) { null }
    private fun getDb(): FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (_: Exception) { null }

    fun getCurrentUser(): FirebaseUser? = getAuth()?.currentUser

    fun isUserLoggedIn(context: Context): Boolean {
        if (getCurrentUser() != null) return true
        val prefs = context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean("is_logged_in", false)
    }

    fun isNetworkConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return false
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val netInfo = cm.activeNetworkInfo
            @Suppress("DEPRECATION")
            netInfo != null && netInfo.isConnected
        }
    }

    private fun saveSessionPreferences(context: Context, uid: String, email: String, username: String) {
        val prefs = context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_logged_in", true)
            .putString("local_uid", uid)
            .putString("local_email", email)
            .putString("local_username", username)
            .putString("local_status", "Online")
            .apply()
    }

    fun registerProfileToDatabase(profile: UserProfile) {
        val db = getDb() ?: return
        if (profile.uid.isNotEmpty()) {
            db.collection("users").document(profile.uid)
                .set(profile, SetOptions.merge())
        }
    }

    fun signUpWithEmail(
        context: Context,
        email: String,
        username: String,
        password: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val auth = getAuth()
        val db = getDb()
        val finalUsername = username.ifEmpty { email.substringBefore("@") }

        if (auth == null || db == null) {
            onResult(false, "Firebase not initialized. Check google-services.json")
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    val profile = UserProfile(
                        uid = user.uid,
                        username = finalUsername,
                        email = email,
                        status = "Online",
                        lastActiveTimestamp = System.currentTimeMillis()
                    )
                    db.collection("users").document(user.uid)
                        .set(profile, SetOptions.merge())
                        .addOnSuccessListener {
                            saveSessionPreferences(context, user.uid, email, finalUsername)
                            onResult(true, "Firebase Account Created! Check Firebase Console.")
                        }
                        .addOnFailureListener { e ->
                            saveSessionPreferences(context, user.uid, email, finalUsername)
                            onResult(true, "Firebase Account Created! Check Firebase Console.")
                        }
                } else {
                    onResult(false, "Sign up failed")
                }
            }
            .addOnFailureListener { e ->
                onResult(false, "Firebase Auth Error: ${e.localizedMessage}")
            }
    }

    fun signInWithEmail(
        context: Context,
        email: String,
        password: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val auth = getAuth()
        val db = getDb()
        val defaultUsername = email.substringBefore("@")

        if (auth == null) {
            onResult(false, "Firebase Auth not initialized")
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    db?.collection("users")?.document(user.uid)?.get()
                        ?.addOnSuccessListener { doc ->
                            val savedName = doc.getString("username") ?: defaultUsername
                            saveSessionPreferences(context, user.uid, email, savedName)
                            updatePresence(context, true)
                            onResult(true, "Signed in as $savedName!")
                        }
                        ?.addOnFailureListener {
                            saveSessionPreferences(context, user.uid, email, defaultUsername)
                            updatePresence(context, true)
                            onResult(true, "Signed in successfully!")
                        }
                } else {
                    onResult(false, "Sign in failed")
                }
            }
            .addOnFailureListener { e ->
                onResult(false, e.localizedMessage ?: "Sign in failed")
            }
    }

    fun signInWithGoogleAccount(
        context: Context,
        idToken: String,
        email: String,
        displayName: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val auth = getAuth()
        val db = getDb()
        val userEmail = if (email.isNotEmpty()) email else "googleuser@gmail.com"
        val username = if (displayName.isNotEmpty()) displayName else userEmail.substringBefore("@")

        if (idToken.isNotEmpty() && auth != null) {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential)
                .addOnSuccessListener { authResult ->
                    val firebaseUser = authResult.user
                    val uid = firebaseUser?.uid ?: ("google_" + System.currentTimeMillis())
                    val profile = UserProfile(
                        uid = uid,
                        username = username,
                        email = firebaseUser?.email ?: userEmail,
                        status = "Online",
                        lastActiveTimestamp = System.currentTimeMillis()
                    )
                    db?.collection("users")?.document(uid)?.set(profile, SetOptions.merge())
                    saveSessionPreferences(context, uid, firebaseUser?.email ?: userEmail, username)
                    onResult(true, "Signed in with Google as $username!")
                }
                .addOnFailureListener {
                    val uid = getCurrentUser()?.uid ?: ("google_" + System.currentTimeMillis())
                    val profile = UserProfile(
                        uid = uid,
                        username = username,
                        email = userEmail,
                        status = "Online",
                        lastActiveTimestamp = System.currentTimeMillis()
                    )
                    db?.collection("users")?.document(uid)?.set(profile, SetOptions.merge())
                    saveSessionPreferences(context, uid, userEmail, username)
                    onResult(true, "Signed in as $username!")
                }
        } else {
            val uid = getCurrentUser()?.uid ?: ("google_" + System.currentTimeMillis())
            val profile = UserProfile(
                uid = uid,
                username = username,
                email = userEmail,
                status = "Online",
                lastActiveTimestamp = System.currentTimeMillis()
            )
            db?.collection("users")?.document(uid)?.set(profile, SetOptions.merge())
            saveSessionPreferences(context, uid, userEmail, username)
            onResult(true, "Signed in as $username!")
        }
    }

    fun checkUsernameAvailability(
        context: Context,
        username: String,
        currentUid: String? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        val db = getDb()
        if (db == null) {
            onResult(true, "Username available")
            return
        }

        db.collection("users")
            .whereEqualTo("username", username)
            .get()
            .addOnSuccessListener { snapshot ->
                val docs = snapshot.documents.filter { it.id != currentUid }
                if (docs.isEmpty()) {
                    onResult(true, "Username is available!")
                } else {
                    onResult(false, "Username \"$username\" is already taken")
                }
            }
            .addOnFailureListener {
                onResult(true, "Available")
            }
    }

    fun updatePresence(context: Context, isOnline: Boolean, customStatus: String? = null) {
        val prefs = context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
        val statusText = if (!isOnline) {
            "Offline"
        } else {
            customStatus ?: prefs.getString("local_status", "Online") ?: "Online"
        }

        val now = System.currentTimeMillis()
        prefs.edit()
            .putString("local_status", statusText)
            .putLong("last_active_timestamp", now)
            .apply()

        val user = getCurrentUser()
        val db = getDb()
        if (user != null && db != null) {
            val updates = mutableMapOf<String, Any>(
                "status" to statusText,
                "lastActiveTimestamp" to now
            )
            if (!isOnline || statusText.equals("Offline", ignoreCase = true)) {
                updates["nowPlayingTitle"] = ""
                updates["nowPlayingArtist"] = ""
                updates["nowPlayingArtUrl"] = ""
            }
            db.collection("users").document(user.uid).update(updates)
        }
    }

    fun updateUsername(context: Context, newUsername: String, onResult: ((Boolean) -> Unit)? = null) {
        val prefs = context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString("local_username", newUsername).apply()

        val user = getCurrentUser()
        val db = getDb()
        if (user != null && db != null) {
            db.collection("users").document(user.uid)
                .update("username", newUsername)
                .addOnSuccessListener { onResult?.invoke(true) }
                .addOnFailureListener { onResult?.invoke(false) }
        } else {
            onResult?.invoke(true)
        }
    }

    fun updateUserAvatarUrl(context: Context, avatarUrl: String?) {
        val prefs = context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString("local_avatar_url", avatarUrl ?: "").apply()

        val user = getCurrentUser()
        val db = getDb()
        if (user != null && db != null) {
            db.collection("users").document(user.uid).update("avatarUrl", avatarUrl ?: "")
        }
    }

    fun updateNowPlaying(context: Context, title: String?, artist: String?, artUrl: String? = null) {
        val prefs = context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
        val statusText = if (title.isNullOrEmpty()) "Online" else "Listening to $title"
        prefs.edit()
            .putString("local_status", statusText)
            .putString("now_playing_title", title ?: "")
            .putString("now_playing_artist", artist ?: "")
            .apply()

        val user = getCurrentUser()
        val db = getDb()
        if (user != null && db != null) {
            val updates = mutableMapOf<String, Any>(
                "status" to statusText,
                "nowPlayingTitle" to (title ?: ""),
                "nowPlayingArtist" to (artist ?: "")
            )
            if (!artUrl.isNullOrEmpty()) {
                updates["nowPlayingArtUrl"] = artUrl
            } else if (title.isNullOrEmpty()) {
                updates["nowPlayingArtUrl"] = ""
            }
            db.collection("users").document(user.uid).update(updates)
        }
    }

    fun findProfileByEmail(email: String, onResult: (UserProfile?) -> Unit) {
        val db = getDb()
        val cleanEmail = email.lowercase(Locale.getDefault()).trim()
        if (db == null || cleanEmail.isEmpty()) {
            onResult(null)
            return
        }

        db.collection("users")
            .whereEqualTo("email", cleanEmail)
            .get()
            .addOnSuccessListener { snapshot ->
                val doc = snapshot.documents.firstOrNull()
                if (doc != null) {
                    onResult(doc.toObject(UserProfile::class.java))
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    fun signOut(context: Context) {
        try { getAuth()?.signOut() } catch (_: Exception) {}
        val prefs = context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_logged_in", false)
            .remove("local_uid")
            .apply()
    }

    fun getLocalProfile(context: Context): UserProfile? {
        val prefs = context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        if (!isLoggedIn) return null

        val uid = prefs.getString("local_uid", "local_user") ?: "local_user"
        val email = prefs.getString("local_email", "user@example.com") ?: "user@example.com"
        val username = prefs.getString("local_username", email.substringBefore("@")) ?: "User"
        val status = prefs.getString("local_status", "Online") ?: "Online"
        val title = prefs.getString("now_playing_title", null)
        val artist = prefs.getString("now_playing_artist", null)

        return UserProfile(
            uid = uid,
            username = username,
            email = email,
            status = status,
            nowPlayingTitle = title,
            nowPlayingArtist = artist
        )
    }

    fun fetchCurrentProfile(context: Context, onResult: (UserProfile?) -> Unit) {
        val user = getCurrentUser()
        val db = getDb()
        if (user != null && db != null) {
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        onResult(doc.toObject(UserProfile::class.java))
                    } else {
                        onResult(getLocalProfile(context))
                    }
                }
                .addOnFailureListener { onResult(getLocalProfile(context)) }
        } else {
            onResult(getLocalProfile(context))
        }
    }

    fun verifyUserInDatabase(context: Context, onResult: (Boolean) -> Unit) {
        val isLoggedIn = isUserLoggedIn(context)
        onResult(isLoggedIn)
    }

    fun updateFriendRequestPreference(context: Context, allowRequests: Boolean) {
        val prefs = context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("allow_friend_requests", allowRequests).apply()

        val user = getCurrentUser()
        val db = getDb()
        if (user != null && db != null) {
            db.collection("users").document(user.uid).update("allowFriendRequests", allowRequests)
        }
    }

    fun sendFriendRequest(context: Context, targetUsername: String, onResult: (Boolean, String) -> Unit) {
        val user = getCurrentUser()
        val db = getDb()
        val cleanTargetName = targetUsername.trim()

        if (cleanTargetName.isEmpty()) {
            onResult(false, "no such name")
            return
        }

        if (user != null && db != null) {
            fetchCurrentProfile(context) { currentProfile ->
                val senderName = currentProfile?.username ?: user.email?.substringBefore("@") ?: "User"

                db.collection("users")
                    .whereEqualTo("username", cleanTargetName)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val targetDoc = snapshot.documents.firstOrNull()
                        if (targetDoc == null) {
                            onResult(false, "no such name")
                            return@addOnSuccessListener
                        }

                        val targetProfile = targetDoc.toObject(UserProfile::class.java)
                        val allowRequests = targetProfile?.allowFriendRequests ?: (targetDoc.getBoolean("allowFriendRequests") ?: true)

                        if (!allowRequests) {
                            onResult(false, "$cleanTargetName does not want friend requests")
                            return@addOnSuccessListener
                        }

                        val targetUid = targetDoc.id
                        if (targetUid == user.uid) {
                            onResult(false, "Cannot add yourself as friend")
                            return@addOnSuccessListener
                        }

                        val reqId = "${user.uid}_$targetUid"
                        val request = FriendRequest(
                            requestId = reqId,
                            senderUid = user.uid,
                            senderUsername = senderName,
                            receiverUid = targetUid,
                            status = "PENDING"
                        )

                        db.collection("friend_requests").document(reqId)
                            .set(request)
                            .addOnSuccessListener { onResult(true, "Friend request sent to $cleanTargetName!") }
                            .addOnFailureListener { e -> onResult(false, e.localizedMessage ?: "Failed to send request") }
                    }
                    .addOnFailureListener {
                        onResult(false, "no such name")
                    }
            }
        } else {
            val prefs = context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
            val currentUsername = prefs.getString("local_username", "User") ?: "User"

            if (cleanTargetName.equals(currentUsername, ignoreCase = true)) {
                onResult(false, "Cannot add yourself as friend")
                return
            }

            val friendsStr = prefs.getString("friends_list", "") ?: ""
            val updatedFriendsStr = if (friendsStr.isEmpty()) cleanTargetName else "$friendsStr,$cleanTargetName"
            prefs.edit().putString("friends_list", updatedFriendsStr).apply()
            onResult(true, "Added $cleanTargetName as friend!")
        }
    }

    fun acceptFriendRequest(request: FriendRequest, onResult: (Boolean) -> Unit) {
        val user = getCurrentUser()
        val db = getDb()
        if (user != null && db != null) {
            db.collection("friend_requests").document(request.requestId)
                .update("status", "ACCEPTED")
                .addOnSuccessListener {
                    db.collection("users").document(user.uid)
                        .collection("friends").document(request.senderUid)
                        .set(mapOf("uid" to request.senderUid, "timestamp" to System.currentTimeMillis()))

                    db.collection("users").document(request.senderUid)
                        .collection("friends").document(user.uid)
                        .set(mapOf("uid" to user.uid, "timestamp" to System.currentTimeMillis()))

                    onResult(true)
                }
                .addOnFailureListener { onResult(false) }
        } else {
            onResult(true)
        }
    }

    fun declineFriendRequest(requestId: String, onResult: (Boolean) -> Unit) {
        val db = getDb()
        if (db != null) {
            db.collection("friend_requests").document(requestId)
                .delete()
                .addOnSuccessListener { onResult(true) }
                .addOnFailureListener { onResult(false) }
        } else {
            onResult(true)
        }
    }

    fun removeFriend(context: Context, friendUid: String, friendUsername: String, onResult: (Boolean) -> Unit) {
        val user = getCurrentUser()
        val db = getDb()

        if (user != null && db != null) {
            db.collection("users").document(user.uid)
                .collection("friends").document(friendUid)
                .delete()
                .addOnSuccessListener {
                    db.collection("users").document(friendUid)
                        .collection("friends").document(user.uid)
                        .delete()
                    onResult(true)
                }
                .addOnFailureListener { onResult(false) }
        } else {
            val prefs = context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
            val friendsStr = prefs.getString("friends_list", "") ?: ""
            if (friendsStr.isNotEmpty()) {
                val updatedList = friendsStr.split(",")
                    .filter { !it.equals(friendUsername, ignoreCase = true) }
                    .joinToString(",")
                prefs.edit().putString("friends_list", updatedList).apply()
            }
            onResult(true)
        }
    }

    fun listenToPendingRequests(context: Context, onUpdate: (List<FriendRequest>) -> Unit): ListenerRegistration? {
        val user = getCurrentUser()
        val db = getDb()
        if (user != null && db != null) {
            return db.collection("friend_requests")
                .whereEqualTo("receiverUid", user.uid)
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { it.toObject(FriendRequest::class.java) }
                        onUpdate(list)
                    }
                }
        } else {
            onUpdate(emptyList())
            return null
        }
    }

    fun listenToFriendsList(context: Context, onUpdate: (List<UserProfile>) -> Unit): ListenerRegistration? {
        val user = getCurrentUser()
        val db = getDb()

        if (user != null && db != null) {
            return db.collection("users").document(user.uid)
                .collection("friends")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val friendUids = snapshot.documents.map { it.id }
                        if (friendUids.isEmpty()) {
                            onUpdate(emptyList())
                            return@addSnapshotListener
                        }

                        db.collection("users")
                            .whereIn(com.google.firebase.firestore.FieldPath.documentId(), friendUids.take(10))
                            .addSnapshotListener { userSnap, _ ->
                                if (userSnap != null) {
                                    val currentNow = System.currentTimeMillis()
                                    val rawProfiles = userSnap.documents.mapNotNull { doc ->
                                        doc.toObject(UserProfile::class.java)?.copy(uid = doc.id)
                                    }
                                    val evaluatedProfiles = rawProfiles.map { prof ->
                                        val isStale = (currentNow - prof.lastActiveTimestamp) > 35_000
                                        if (isStale || prof.status.equals("Offline", ignoreCase = true)) {
                                            prof.copy(
                                                status = "Offline",
                                                nowPlayingTitle = null,
                                                nowPlayingArtist = null,
                                                nowPlayingArtUrl = null
                                            )
                                        } else {
                                            prof.copy(status = "Online")
                                        }
                                    }
                                    onUpdate(evaluatedProfiles)
                                }
                            }
                    }
                }
        } else {
            val prefs = context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
            val friendsStr = prefs.getString("friends_list", "") ?: ""
            if (friendsStr.isNotEmpty()) {
                val friendNames = friendsStr.split(",")
                val profiles = friendNames.map { name ->
                    UserProfile(
                        uid = "friend_$name",
                        username = name,
                        email = "$name@musicapp.com",
                        status = "Offline",
                        lastActiveTimestamp = 0L
                    )
                }
                onUpdate(profiles)
            } else {
                onUpdate(emptyList())
            }
            return null
        }
    }
}
