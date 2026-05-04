package com.example.buswatch

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import com.example.buswatch.common.R as CommonR

class ParentMainActivity : AppCompatActivity() {

    private lateinit var ivNavAccount: ImageView
    private lateinit var tvNavAccount: TextView
    private lateinit var indicatorAccount: View

    private lateinit var ivNavHome: ImageView
    private lateinit var tvNavHome: TextView
    private lateinit var indicatorHome: View

    private lateinit var ivNavSettings: ImageView
    private lateinit var tvNavSettings: TextView
    private lateinit var indicatorSettings: View

    private val activeColor = "#FEBE1E".toColorInt()
    private val inactiveColor = "#A99E9E".toColorInt()
    
    private var currentTabTag: String = "HOME"
    
    private var notificationListener: ListenerRegistration? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        Log.d("NotificationPermission", "Granted: $isGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parent_main)

        ivNavAccount = findViewById(R.id.ivNavAccount)
        tvNavAccount = findViewById(R.id.tvNavAccount)
        indicatorAccount = findViewById(R.id.indicatorAccount)

        ivNavHome = findViewById(R.id.ivNavHome)
        tvNavHome = findViewById(R.id.tvNavHome)
        indicatorHome = findViewById(R.id.indicatorHome)

        ivNavSettings = findViewById(R.id.ivNavSettings)
        tvNavSettings = findViewById(R.id.tvNavSettings)
        indicatorSettings = findViewById(R.id.indicatorSettings)

        findViewById<View>(R.id.navAccount).setOnClickListener { switchToAccount() }
        findViewById<View>(R.id.navHome).setOnClickListener { switchToHome() }
        findViewById<View>(R.id.navSettings).setOnClickListener { switchToSettings() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentTabTag != "HOME") {
                    switchToHome()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (savedInstanceState != null) {
            currentTabTag = savedInstanceState.getString("selected_tab", "HOME")
            updateNavUI(currentTabTag)
        } else {
            switchToHome()
        }

        checkNotificationPermission()
        updateFCMToken()
    }

    override fun onStart() {
        super.onStart()
        startNotificationListener()
    }

    override fun onStop() {
        super.onStop()
        stopNotificationListener()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun updateFCMToken() {
        val uid = auth.currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                val updateMap = mapOf("fcmToken" to token)
                // Ensure parent record has the latest token
                db.collection("parents").document(uid).get().addOnSuccessListener { 
                    if (it.exists()) db.collection("parents").document(uid).update(updateMap)
                }
            }
        }
    }

    private fun stopNotificationListener() {
        notificationListener?.remove()
        notificationListener = null
    }

    private fun startNotificationListener() {
        val uid = auth.currentUser?.uid ?: return
        var isInitialSnapshot = true
        
        stopNotificationListener()

        notificationListener = db.collection("parents").document(uid)
            .collection("notifications")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) {
                    isInitialSnapshot = false
                    return@addSnapshotListener
                }
                
                for (dc in snapshots.documentChanges) {
                    if (dc.type == DocumentChange.Type.ADDED) {
                        // Skip notifications already in the database when the app starts
                        if (!isInitialSnapshot) {
                            val doc = dc.document
                            // Logic similar to Test Notification in Security
                            if (currentTabTag != "NOTIFICATION") {
                                val title = doc.getString("title") ?: "BusWatch Update"
                                val message = doc.getString("message") ?: ""
                                showSystemNotification(title, message)
                            }
                        }
                    }
                }
                isInitialSnapshot = false
            }
    }

    private fun showSystemNotification(title: String, message: String) {
        val intent = Intent(this, ParentMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, "BUSWATCH_NOTIF")
            .setSmallIcon(CommonR.drawable.logo)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
            }
        } else {
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopNotificationListener()
    }

    fun switchToAccount() { replaceFragment(ParentDetailsFragment(), "ACCOUNT") }
    fun switchToHome() { replaceFragment(HomeFragment(), "HOME") }
    fun switchToSettings() { replaceFragment(SettingsFragment(), "SETTINGS") }
    fun switchToNotifications() { replaceFragment(NotificationFragment(), "NOTIFICATION") }

    private fun replaceFragment(fragment: Fragment, tag: String) {
        val currentFragment = supportFragmentManager.findFragmentByTag(tag)
        if (currentFragment?.isVisible == true) return

        currentTabTag = tag
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.parentFragmentContainer, fragment, tag)
            .commit()
        updateNavUI(tag)
    }

    private fun updateNavUI(tag: String) {
        val inactiveList = ColorStateList.valueOf(inactiveColor)
        val activeList = ColorStateList.valueOf(activeColor)

        ivNavAccount.imageTintList = inactiveList
        tvNavAccount.setTextColor(inactiveColor)
        indicatorAccount.visibility = View.GONE

        ivNavHome.imageTintList = inactiveList
        tvNavHome.setTextColor(inactiveColor)
        indicatorHome.visibility = View.GONE

        ivNavSettings.imageTintList = inactiveList
        tvNavSettings.setTextColor(inactiveColor)
        indicatorSettings.visibility = View.GONE

        when (tag) {
            "ACCOUNT" -> {
                ivNavAccount.imageTintList = activeList
                tvNavAccount.setTextColor(activeColor)
                indicatorAccount.visibility = View.VISIBLE
            }
            "HOME", "NOTIFICATION" -> {
                ivNavHome.imageTintList = activeList
                tvNavHome.setTextColor(activeColor)
                indicatorHome.visibility = View.VISIBLE
            }
            "SETTINGS" -> {
                ivNavSettings.imageTintList = activeList
                tvNavSettings.setTextColor(activeColor)
                indicatorSettings.visibility = View.VISIBLE
            }
        }
    }
}
