package com.example

import android.content.Context
import android.accounts.Account
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.auth.GoogleAuthUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class BackupPayload(
    val products: List<ProductEntity>,
    val settings: List<SettingEntity>
)

class DriveBackupHelper(private val context: Context) {
    private val client = OkHttpClient()
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(BackupPayload::class.java)

    fun getSignInClient() = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
            .build()
    )

    fun getSignedInAccount(): GoogleSignInAccount? {
        return try {
            GoogleSignIn.getLastSignedInAccount(context)
        } catch (e: Throwable) {
            android.util.Log.e("DriveBackupHelper", "Error getting getLastSignedInAccount", e)
            null
        }
    }

    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val account = getSignedInAccount() ?: return@withContext null
        try {
            // Using drive.file scope matching requested scopes
            val scopeStr = "oauth2:https://www.googleapis.com/auth/drive.file"
            val androidAccount = account.account ?: Account(account.email ?: "", "com.google")
            GoogleAuthUtil.getToken(context, androidAccount, scopeStr)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun clearToken() = withContext(Dispatchers.IO) {
        val account = getSignedInAccount() ?: return@withContext
        try {
            val scopeStr = "oauth2:https://www.googleapis.com/auth/drive.file"
            val androidAccount = account.account ?: Account(account.email ?: "", "com.google")
            val token = GoogleAuthUtil.getToken(context, androidAccount, scopeStr)
            GoogleAuthUtil.clearToken(context, token)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun searchBackupFile(accessToken: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?q=name='kasir_pos_backup.json' and trashed=false&fields=files(id)")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    val filesArray = json.optJSONArray("files")
                    if (filesArray != null && filesArray.length() > 0) {
                        return@withContext filesArray.getJSONObject(0).getString("id")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    suspend fun uploadBackup(accessToken: String, payload: BackupPayload): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonString = adapter.toJson(payload)
            val fileId = searchBackupFile(accessToken)

            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = RequestBody.create(mediaType, jsonString)

            if (fileId != null) {
                // Update existing file
                val request = Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
                    .header("Authorization", "Bearer $accessToken")
                    .patch(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    return@withContext response.isSuccessful
                }
            } else {
                // Create new file metadata first
                val metaJson = JSONObject()
                metaJson.put("name", "kasir_pos_backup.json")
                metaJson.put("mimeType", "application/json")

                val metaBody = RequestBody.create(mediaType, metaJson.toString())
                val metaRequest = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files")
                    .header("Authorization", "Bearer $accessToken")
                    .post(metaBody)
                    .build()

                var newId: String? = null
                client.newCall(metaRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val respJson = JSONObject(response.body?.string() ?: "")
                        newId = respJson.getString("id")
                    }
                }

                val currentNewId = newId ?: return@withContext false

                // Upload content to the newly created file ID
                val contentRequest = Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files/$currentNewId?uploadType=media")
                    .header("Authorization", "Bearer $accessToken")
                    .patch(requestBody)
                    .build()

                client.newCall(contentRequest).execute().use { response ->
                    return@withContext response.isSuccessful
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun downloadBackup(accessToken: String): BackupPayload? = withContext(Dispatchers.IO) {
        try {
            val fileId = searchBackupFile(accessToken) ?: return@withContext null

            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val rawJson = response.body?.string() ?: ""
                    return@withContext adapter.fromJson(rawJson)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }
}
