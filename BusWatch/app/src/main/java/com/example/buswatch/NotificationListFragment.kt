package com.example.buswatch

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class NotificationListFragment : Fragment() {
    private var filterType: String = "ALL" // "ALL", "UNREAD", "READ"
    private lateinit var rvNotifications: RecyclerView
    private lateinit var pbNotifications: ProgressBar
    private lateinit var tvEmpty: TextView
    lateinit var adapter: NotificationAdapter
    
    private var onSelectionChangedListener: ((Int) -> Unit)? = null
    private var pendingSelectionMode: Boolean = false

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val notifications = mutableListOf<NotificationItem>()

    companion object {
        private const val ARG_FILTER_TYPE = "filter_type"
        fun newInstance(filterType: String): NotificationListFragment {
            val fragment = NotificationListFragment()
            val args = Bundle()
            args.putString(ARG_FILTER_TYPE, filterType)
            fragment.arguments = args
            return fragment
        }
    }

    fun setOnSelectionChangedListener(listener: (Int) -> Unit) {
        onSelectionChangedListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filterType = arguments?.getString(ARG_FILTER_TYPE) ?: "ALL"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_notification_list, container, false)
        rvNotifications = view.findViewById(R.id.rvNotifications)
        pbNotifications = view.findViewById(R.id.pbNotifications)
        tvEmpty = view.findViewById(R.id.tvEmptyNotifications)
        
        setupRecyclerView()
        
        if (pendingSelectionMode) {
            adapter.isSelectionMode = true
            pendingSelectionMode = false
        }
        
        fetchNotifications()
        return view
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter(
            notifications = emptyList(),
            onSelectionChanged = { count ->
                onSelectionChangedListener?.invoke(count)
            },
            onNotificationClick = { item ->
                if (!item.isRead) {
                    markAsRead(item.id)
                }
            }
        )
        rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        rvNotifications.adapter = adapter
    }

    fun enterSelectionMode() {
        if (::adapter.isInitialized) {
            adapter.isSelectionMode = true
        } else {
            pendingSelectionMode = true
        }
    }

    fun exitSelectionMode() {
        if (::adapter.isInitialized) {
            adapter.isSelectionMode = false
        } else {
            pendingSelectionMode = false
        }
    }

    fun getSelectedIds(): List<String> = if (::adapter.isInitialized) adapter.selectedIds.toList() else emptyList()

    private fun fetchNotifications() {
        val userId = auth.currentUser?.uid ?: return
        pbNotifications.visibility = View.VISIBLE
        
        val query = db.collection("parents").document(userId)
            .collection("notifications")

        query.addSnapshotListener { snapshots, e ->
            if (!isAdded) return@addSnapshotListener
            pbNotifications.visibility = View.GONE
            
            if (e != null) {
                Log.e("NotificationList", "Listen failed.", e)
                return@addSnapshotListener
            }
            
            notifications.clear()
            if (snapshots != null) {
                for (doc in snapshots) {
                    try {
                        val isRead = doc.getBoolean("isRead") ?: false
                        
                        // Filter logic
                        val matchesFilter = when(filterType) {
                            "UNREAD" -> !isRead
                            "READ" -> isRead
                            else -> true // "ALL"
                        }
                        
                        if (matchesFilter) {
                            val item = NotificationItem(
                                id = doc.id,
                                title = doc.getString("title") ?: "",
                                message = doc.getString("message") ?: "",
                                timestamp = doc.getTimestamp("timestamp"),
                                isRead = isRead,
                                type = doc.getString("type") ?: ""
                            )
                            notifications.add(item)
                        }
                    } catch (ex: Exception) {
                        Log.e("NotificationList", "Error parsing doc ${doc.id}", ex)
                    }
                }
            }
            
            notifications.sortByDescending { it.timestamp?.seconds ?: 0L }
            if (::adapter.isInitialized) {
                adapter.updateList(notifications)
            }
            tvEmpty.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun markAsRead(id: String) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("parents").document(userId)
            .collection("notifications").document(id)
            .update("isRead", true)
    }
}
