package com.andrew.lu.wordleonline

import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import com.google.firebase.auth.userProfileChangeRequest

// This is our abstract concept of a User, which is visible
// outside AuthUser.  That way, client code will not change
// if we use something other than Firebase for authentication
data class User (private val nullableName: String?,
                 private val nullableEmail: String?,
                 val uid: String) {
    val name: String = nullableName ?: "User logged out"
    val email: String = nullableEmail ?: "User logged out"
}
const val invalidUserUid = "-1"
// Extension function to determine if user is valid
fun User.isInvalid(): Boolean {
    return uid == invalidUserUid
}
val invalidUser = User(null, null,
    invalidUserUid)

class AuthUser(private val registry: ActivityResultRegistry) :
    DefaultLifecycleObserver,
    FirebaseAuth.AuthStateListener {
    companion object {
        private const val TAG = "AuthUser"
    }
    // https://developer.android.com/training/basics/intents/result#separate
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>
    // In lifecycle 2.9+, postValue before onStart permanently breaks that LiveData.
    // Use constructor to set the initial value instead of postValue.
    private var liveUser = MutableLiveData(invalidUser)
    private var pendingLogin = false
    init {
        // Listen to FirebaseAuth state
        // That way, if the server logs us out, we know it and change the view
        Firebase.auth.addAuthStateListener(this)
    }

    fun observeUser(): LiveData<User> {
        return liveUser
    }

    // Active live data upon a change of state for our FirebaseUser
    private fun postUserUpdate(firebaseUser: FirebaseUser?) {
        if(firebaseUser == null) {
            Log.d(TAG, "postUser login")
            liveUser.postValue(invalidUser)
            // No disconnected operation
            login()
        } else {
            val user = User(firebaseUser.displayName,
                firebaseUser.email, firebaseUser.uid)
            liveUser.postValue(user)
        }
    }
    // https://developer.android.com/training/basics/intents/result#separate
    // Called when lifecycle owner finishes onCreate
    override fun onCreate(owner: LifecycleOwner) {
        signInLauncher = registry.register("key", owner,
            FirebaseAuthUIActivityResultContract()) { result ->
            Log.d(TAG, "sign in result ${result.resultCode}")
            pendingLogin = false
        }
    }
    // This override makes us a valid FirebaseAuth.AuthStateListener
    override fun onAuthStateChanged(p0: FirebaseAuth) {
        Log.d(TAG, "onAuthStateChanged null? ${p0.currentUser == null}")
        // XXX Write me
        postUserUpdate(p0.currentUser)
    }
    private fun user(): FirebaseUser? {
        return Firebase.auth.currentUser
    }

    fun setDisplayName(displayName: String) {
        Log.d(TAG, "XXX profile change request")
        // If no user, no way to set display name, just return
        val user = user() ?: return
        // https://firebase.google.com/docs/auth/android/manage-users#update_a_users_profile
        // XXX Write me.
        val profileUpdate = userProfileChangeRequest {
            this.displayName = displayName
        }
        user.updateProfile(profileUpdate)
            .addOnCompleteListener { task ->
                if(task.isSuccessful){
                    Log.d(TAG, "User profile updated.")
                    postUserUpdate(user)
                }
                else{
                    Log.d(TAG, "User profile update failed.")
                }
            }
    }
    private fun login() {
        if (user() == null
            && !pendingLogin) {
            Log.d(TAG, "XXX user null")
            pendingLogin = true
            // Choose authentication providers
            val providers = arrayListOf(
                AuthUI.IdpConfig.EmailBuilder().build()
            )
            //TODO: make this not look like trash
            val signInIntent = AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setCredentialManagerEnabled(false)
                .setAvailableProviders(providers)
                .build()
            signInLauncher.launch(signInIntent)
        }
    }
    fun logout() {
        if(user() == null) return
        Firebase.auth.signOut()
    }
}