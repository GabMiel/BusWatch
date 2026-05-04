package com.example.buswatch

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.Group
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class NotificationFragment : Fragment() {
    private lateinit var btnAll: Button
    private lateinit var btnUnread: Button
    private lateinit var btnRead: Button
    private var currentFilter: String = "ALL" // "ALL", "UNREAD", "READ"

    // Selection Mode UI
    private lateinit var groupNormalHeader: Group
    private lateinit var groupSelectionHeader: Group
    private lateinit var btnClearNotifications: ImageButton
    private lateinit var btnMarkAllRead: ImageButton
    private lateinit var btnCancelSelection: ImageButton
    private lateinit var tvSelectedCount: TextView
    private lateinit var btnSelectAll: TextView
    private lateinit var bottomDeleteBar: LinearLayout
    private lateinit var btnDeleteSelected: Button

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private var listFragment: NotificationListFragment? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.notification, container, false)
        
        btnAll = view.findViewById(R.id.btnNotificationAll)
        btnUnread = view.findViewById(R.id.btnNotificationUnread)
        btnRead = view.findViewById(R.id.btnNotificationRead)

        // Initialize Selection UI
        groupNormalHeader = view.findViewById(R.id.groupNormalHeader)
        groupSelectionHeader = view.findViewById(R.id.groupSelectionHeader)
        btnClearNotifications = view.findViewById(R.id.btnClearNotifications)
        btnMarkAllRead = view.findViewById(R.id.btnMarkAllRead)
        btnCancelSelection = view.findViewById(R.id.btnCancelSelection)
        tvSelectedCount = view.findViewById(R.id.tvSelectedCount)
        btnSelectAll = view.findViewById(R.id.btnSelectAll)
        bottomDeleteBar = view.findViewById(R.id.bottomDeleteBar)
        btnDeleteSelected = view.findViewById(R.id.btnDeleteSelected)

        // Show back button and handle navigation to Home
        val btnBack = view.findViewById<ImageButton>(R.id.btnNotificationBack)
        btnBack?.visibility = View.VISIBLE
        btnBack?.setOnClickListener {
            if (isSelectionModeActive()) {
                exitSelectionMode()
            } else {
                (activity as? ParentMainActivity)?.switchToHome()
            }
        }

        btnAll.setOnClickListener {
            if (currentFilter != "ALL") {
                if (isSelectionModeActive()) exitSelectionMode()
                currentFilter = "ALL"
                updateFilterUI()
                showListFragment("ALL")
            }
        }

        btnUnread.setOnClickListener {
            if (currentFilter != "UNREAD") {
                if (isSelectionModeActive()) exitSelectionMode()
                currentFilter = "UNREAD"
                updateFilterUI()
                showListFragment("UNREAD")
            }
        }

        btnRead.setOnClickListener {
            if (currentFilter != "READ") {
                if (isSelectionModeActive()) exitSelectionMode()
                currentFilter = "READ"
                updateFilterUI()
                showListFragment("READ")
            }
        }

        setupSelectionListeners()
        
        // Initial show
        showListFragment("ALL")
        
        return view
    }

    private fun isSelectionModeActive(): Boolean {
        return listFragment?.adapter?.isSelectionMode == true
    }

    private fun setupSelectionListeners() {
        btnClearNotifications.setOnClickListener {
            enterSelectionMode()
        }

        btnMarkAllRead.setOnClickListener {
            showMarkAllReadConfirmationDialog()
        }

        btnCancelSelection.setOnClickListener {
            exitSelectionMode()
        }

        btnSelectAll.setOnClickListener {
            val adapter = listFragment?.adapter
            if (adapter != null) {
                if (adapter.isAllSelected()) {
                    adapter.unselectAll()
                } else {
                    adapter.selectAll()
                }
            }
        }

        btnDeleteSelected.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun showMarkAllReadConfirmationDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_mark_all_read, null)
        val dialog = AlertDialog.Builder(requireContext(), CommonR.style.CustomDialog)
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            markAllNotificationsAsRead()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun markAllNotificationsAsRead() {
        val userId = auth.currentUser?.uid ?: return
        val notificationsRef = db.collection("parents").document(userId).collection("notifications")
        
        notificationsRef.whereEqualTo("isRead", false).get().addOnSuccessListener { snapshots ->
            if (snapshots.isEmpty) {
                Toast.makeText(requireContext(), "No unread notifications", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            
            val batch = db.batch()
            for (doc in snapshots) {
                batch.update(doc.reference, "isRead", true)
            }
            
            batch.commit().addOnCompleteListener { task ->
                if (isAdded) {
                    if (task.isSuccessful) {
                        Toast.makeText(requireContext(), "All marked as read", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to update: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun enterSelectionMode() {
        listFragment?.enterSelectionMode()
        groupNormalHeader.visibility = View.GONE
        groupSelectionHeader.visibility = View.VISIBLE
    }

    private fun exitSelectionMode() {
        listFragment?.exitSelectionMode()
        groupNormalHeader.visibility = View.VISIBLE
        groupSelectionHeader.visibility = View.GONE
        bottomDeleteBar.visibility = View.GONE
    }

    private fun showDeleteConfirmationDialog() {
        val selectedCount = listFragment?.getSelectedIds()?.size ?: 0
        if (selectedCount == 0) {
            Toast.makeText(requireContext(), "No notifications selected", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm_delete, null)
        val dialog = AlertDialog.Builder(requireContext(), CommonR.style.CustomDialog)
            .setView(dialogView)
            .create()
        
        val tvMsg = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        tvMsg.text = getString(CommonR.string.delete_notifications_confirm_msg, selectedCount)
        
        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<Button>(R.id.btnDelete).setOnClickListener {
            deleteSelectedNotifications()
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun deleteSelectedNotifications() {
        val userId = auth.currentUser?.uid ?: return
        val selectedIds = listFragment?.getSelectedIds() ?: return
        if (selectedIds.isEmpty()) return
        
        val batch = db.batch()
        val notificationsRef = db.collection("parents").document(userId).collection("notifications")
        
        for (id in selectedIds) {
            batch.delete(notificationsRef.document(id))
        }
        
        // Show progress or disable button
        btnDeleteSelected.isEnabled = false
        
        batch.commit().addOnCompleteListener { task ->
            btnDeleteSelected.isEnabled = true
            if (isAdded) {
                if (task.isSuccessful) {
                    Toast.makeText(requireContext(), "Deleted permanently", Toast.LENGTH_SHORT).show()
                    exitSelectionMode()
                } else {
                    Toast.makeText(requireContext(), "Failed to delete: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showListFragment(filterType: String) {
        val fragment = NotificationListFragment.newInstance(filterType)
        fragment.setOnSelectionChangedListener { count ->
            if (isAdded) {
                tvSelectedCount.text = getString(CommonR.string.selected_count_format, count)
                btnDeleteSelected.text = "DELETE ($count)"
                bottomDeleteBar.visibility = if (count > 0) View.VISIBLE else View.GONE
                
                // Update Select All / Unselect All text
                val isAll = fragment.adapter.isAllSelected()
                btnSelectAll.text = if (isAll) getString(CommonR.string.unselect_all) else getString(CommonR.string.select_all)
            }
        }
        
        this.listFragment = fragment
        childFragmentManager.beginTransaction()
            .replace(R.id.notificationListContainer, fragment, "LIST_FRAGMENT")
            .commit()
    }

    private fun updateFilterUI() {
        val context = context ?: return
        
        // Reset all
        btnAll.setBackgroundColor(Color.TRANSPARENT)
        btnAll.setTextColor("#666666".toColorInt())
        btnUnread.setBackgroundColor(Color.TRANSPARENT)
        btnUnread.setTextColor("#666666".toColorInt())
        btnRead.setBackgroundColor(Color.TRANSPARENT)
        btnRead.setTextColor("#666666".toColorInt())

        // Highlight selected
        val activeBtn = when(currentFilter) {
            "ALL" -> btnAll
            "UNREAD" -> btnUnread
            "READ" -> btnRead
            else -> btnAll
        }
        
        activeBtn.setBackgroundResource(CommonR.drawable.rectangle_shape)
        activeBtn.backgroundTintList = ContextCompat.getColorStateList(context, CommonR.color.yellow_primary)
        activeBtn.setTextColor(Color.BLACK)
    }
}
