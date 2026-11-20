package com.example.smarthomeai.data

import android.app.Activity
import android.content.Context
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.credentials.*
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.*

class AuthRepository(private val context: Context) {
    val configured get() = FirebaseApp.getApps(context).isNotEmpty()
    private val auth get() = FirebaseAuth.getInstance()
    fun isSignedIn() = configured && auth.currentUser != null
    val currentEmail get() = if(configured) auth.currentUser?.let { it.email ?: it.displayName } else null
    fun login(email: String, password: String, done: (String?) -> Unit) {
        auth.signInWithEmailAndPassword(email,password).addOnCompleteListener { done(it.exception?.let(::describe)) }
    }
    fun register(email: String, password: String, done: (String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email,password).addOnCompleteListener { done(it.exception?.let(::describe)) }
    }
    fun signInWithGoogle(activity: Activity, done: (String?) -> Unit) {
        val id = context.resources.getIdentifier("default_web_client_id","string",context.packageName)
        if(id == 0) { done("Chưa cấu hình Google Sign-In trong Firebase."); return }
        val option = GetGoogleIdOption.Builder().setServerClientId(context.getString(id)).setFilterByAuthorizedAccounts(false).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        CredentialManager.create(activity).getCredentialAsync(activity,request,CancellationSignal(),ContextCompat.getMainExecutor(activity),
            object : CredentialManagerCallback<GetCredentialResponse,GetCredentialException> {
                override fun onResult(result: GetCredentialResponse) {
                    val credential = result.credential
                    if(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val token = runCatching { GoogleIdTokenCredential.createFrom(credential.data).idToken }.getOrNull()
                        if(token == null) { done("Không đọc được thông tin tài khoản Google."); return }
                        auth.signInWithCredential(GoogleAuthProvider.getCredential(token,null))
                            .addOnCompleteListener { done(it.exception?.let(::describe)) }
                    } else done("Không lấy được tài khoản Google.")
                }
                override fun onError(e: GetCredentialException) {
                    done(if(e is GetCredentialCancellationException) "Đã hủy đăng nhập Google." else "Đăng nhập Google thất bại. Kiểm tra kết nối và cấu hình tài khoản.")
                }
            })
    }
    fun signOut() { if(configured) auth.signOut() }
    private fun describe(e: Exception): String = when(e) {
        is FirebaseAuthInvalidUserException, is FirebaseAuthInvalidCredentialsException -> "Email hoặc mật khẩu không đúng."
        is FirebaseAuthUserCollisionException -> "Email này đã được đăng ký."
        is FirebaseAuthWeakPasswordException -> "Mật khẩu cần ít nhất 6 ký tự."
        is FirebaseNetworkException -> "Không có kết nối Internet. Hãy thử lại."
        else -> "Không thể xác thực. Kiểm tra thông tin và cấu hình Firebase."
    }
}
