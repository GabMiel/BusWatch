package com.example.buswatch.common

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

object NotificationSender {
    private const val ONESIGNAL_APP_ID = "7dff4619-d475-4e8b-9a8e-31f2e7f40c19"
    private const val REST_API_KEY = "os_v2_app_px7umgouovhixguoghzop5amdgcesbmtnxie66ndlrm6fuaztastrc6h4napmxz73yjjv4iqsbpyznggbc5qeilm3lsh6uqrbhvtpua"

    private val client = OkHttpClient()

    fun sendNotification(targetUserId: String, title: String, message: String) {
        sendNotification(listOf(targetUserId), title, message)
    }

    fun sendNotification(targetUserIds: List<String>, title: String, message: String) {
        if (targetUserIds.isEmpty()) return

        val jsonBody = JSONObject().apply {
            put("app_id", ONESIGNAL_APP_ID)
            put("include_external_user_ids", JSONArray(targetUserIds))
            put("headings", JSONObject().put("en", title))
            put("contents", JSONObject().put("en", message))
            put("android_channel_id", "BUSWATCH_NOTIF")
            put("priority", 10)
        }

        val request = Request.Builder()
            .url("https://api.onesignal.com/api/v1/notifications")
            .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .addHeader("Authorization", "Basic $REST_API_KEY")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("NotificationSender", "Network Error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful) {
                    Log.d("NotificationSender", "Successfully sent to ${targetUserIds.size} users")
                } else {
                    Log.e("NotificationSender", "OneSignal error: ${response.code} | $body")
                }
            }
        })
    }
}
