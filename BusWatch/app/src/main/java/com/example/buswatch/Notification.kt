package com.example.buswatch

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class Notification : AppCompatActivity() {
    private lateinit var btnAll: Button
    private lateinit var btnUnread: Button
    private lateinit var rvNotifications: RecyclerView
    private lateinit var pbNotifications: ProgressBar
    private lateinit var tvEmpty: TextView
    
    private lateinit var adapter: NotificationAdapter
    private val notifications = mutableListOf<NotificationItem>()
    private var isFilterAll = true
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.notification)

        btnAll = findViewById(R.id.btnNotificationAll)
        btnUnread = findViewById(R.id.btnNotificationUnread)
        rvNotifications = findViewById(R.id.rvNotifications)
        pbNotifications = findViewById(R.id.pbNotifications)
        tvEmpty = findViewById(R.id.tvEmptyNotifications)

        val backButton = findViewById<ImageButton>(R.id.btnNotificationBack)
        backButton.setOnClickListener {
            finish()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, CommonR.anim.stay, CommonR.anim.slide_out_bottom)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(CommonR.anim.stay, CommonR.anim.slide_out_bottom)
            }
        }

        setupRecyclerView()
        
        btnAll.setOnClickListener {
            if (!isFilterAll) {
                isFilterAll = true
                updateFilterUI()
                fetchNotifications()
            }
        }

        btnUnread.setOnClickListener {
            if (isFilterAll) {
                isFilterAll = false
                updateFilterUI()
                fetchNotifications()
            }
        }
        
        fetchNotifications()
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter(notifications) { item ->
            if (!item.isRead) {
                markAsRead(item.id)
            }
        }
        rvNotifications.layoutManager = LinearLayoutManager(this)
        rvNotifications.adapter = adapter
    }

    private fun fetchNotifications() {
        val userId = auth.currentUser?.uid ?: return
        pbNotifications.visibility = View.VISIBLE
        
        var query = db.collection("parents").document(userId)
            .collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            
        if (!isFilterAll) {
            query = query.whereEqualTo("isRead", false)
        }

        query.addSnapshotListener { snapshots, e ->
            pbNotifications.visibility = View.GONE
            if (e != null) return@addSnapshotListener
            
            notifications.clear()
            if (snapshots != null) {
                for (doc in snapshots) {
                    val item = doc.toObject(NotificationItem::class.java).copy(id = doc.id)
                    notifications.add(item)
                }
            }
            
            adapter.updateList(notifications)
            tvEmpty.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun markAsRead(id: String) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("parents").document(userId)
            .collection("notifications").document(id)
            .update("isRead", true)
    }

    private fun updateFilterUI() {
        if (isFilterAll) {
            btnAll.setBackgroundResource(CommonR.drawable.rectangle_shape)
            btnAll.backgroundTintList = ContextCompat.getColorStateList(this, CommonR.color.yellow_primary)
            btnAll.setTextColor(Color.BLACK)

            btnUnread.setBackgroundColor(Color.TRANSPARENT)
            btnUnread.setTextColor("#666666".toColorInt())
        } else {
            btnAll.setBackgroundColor(Color.TRANSPARENT)
            btnAll.setTextColor("#666666".toColorInt())

            btnUnread.setBackgroundResource(CommonR.drawable.rectangle_shape)
            btnUnread.backgroundTintList = ContextCompat.getColorStateList(this, CommonR.color.yellow_primary)
            btnUnread.setTextColor(Color.BLACK)
        }
    }
}
