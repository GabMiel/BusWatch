package com.example.buswatch.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.common.R as CommonR

class AdminHome : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    
    // Mock data storage
    private var activeUsers = mutableListOf(
        UserAdmin(1, "Robert Wilson", "Parent"),
        UserAdmin(3, "Jane Doe", "Parent")
    )
    private var activeDrivers = mutableListOf(
        UserAdmin(2, "Mike Johnson", "Driver"),
        UserAdmin(4, "John Smith", "Driver")
    )
    private var archivedUsers = mutableListOf(
        UserAdmin(5, "Old User", "Parent", isArchived = true)
    )
    private var pendingUsers = mutableListOf(
        UserAdmin(10, "New Parent Request", "Parent")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        val btnMenuToggle = findViewById<ImageButton>(R.id.btnMenuToggle)

        btnMenuToggle.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        setupSidebarNavigation()
        
        if (savedInstanceState == null) {
            loadDashboard()
        }
    }

    private fun setupSidebarNavigation() {
        findViewById<LinearLayout>(R.id.navDashboard)?.setOnClickListener {
            loadDashboard()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        findViewById<LinearLayout>(R.id.navUsers)?.setOnClickListener {
            loadUsers()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        findViewById<LinearLayout>(R.id.navArchive)?.setOnClickListener {
            loadArchive()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        findViewById<LinearLayout>(R.id.navLogout)?.setOnClickListener {
            val intent = Intent(this, Class.forName("com.example.buswatch.Login"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun loadDashboard() {
        loadLayout(R.layout.fragment_dashboard)
    }

    private fun loadUsers() {
        loadLayout(R.layout.fragment_users)
        setupUserList()

        findViewById<TextView>(R.id.tabDrivers)?.setOnClickListener {
            loadDrivers()
        }

        findViewById<TextView>(R.id.btnViewPendingParents)?.setOnClickListener {
            loadPendingParents()
        }
    }

    private fun loadDrivers() {
        loadLayout(R.layout.fragment_driver)
        setupDriverList()

        findViewById<TextView>(R.id.tabParents)?.setOnClickListener {
            loadUsers()
        }

        findViewById<TextView>(R.id.btnAddNewDriver)?.setOnClickListener {
            showAddDriverDialog()
        }
    }

    private fun setupUserList() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerUsers) ?: return
        val adapter = UserAdapter(activeUsers, 
            onViewClick = { user -> showUserDetailDialog(user, isPending = false) },
            onArchiveClick = { user, _ -> 
                val index = activeUsers.indexOf(user)
                if (index != -1) {
                    activeUsers.removeAt(index)
                    user.isArchived = true
                    archivedUsers.add(user)
                    loadUsers()
                }
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupDriverList() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerUsers) ?: return
        val adapter = UserAdapter(activeDrivers, 
            onViewClick = { user -> showUserDetailDialog(user, isPending = false) },
            onArchiveClick = { user, _ -> 
                val index = activeDrivers.indexOf(user)
                if (index != -1) {
                    activeDrivers.removeAt(index)
                    user.isArchived = true
                    archivedUsers.add(user)
                    loadDrivers() 
                }
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadPendingParents() {
        loadLayout(R.layout.fragment_pending_parents)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerPending) ?: return
        
        findViewById<ImageButton>(R.id.btnBackPending)?.setOnClickListener {
            loadUsers()
        }

        val adapter = PendingUserAdapter(pendingUsers,
            onAcceptClick = { user, pos -> approveUser(user, pos) },
            onRejectClick = { user, pos -> rejectUser(user, pos) },
            onViewClick = { user -> showUserDetailDialog(user, isPending = true) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun approveUser(user: UserAdmin, position: Int) {
        pendingUsers.removeAt(position)
        activeUsers.add(user)
        loadUsers()
    }

    private fun rejectUser(user: UserAdmin, position: Int) {
        pendingUsers.removeAt(position)
        user.isArchived = true
        archivedUsers.add(user)
        loadArchive()
    }

    private fun loadArchive(showDrivers: Boolean = false) {
        if (showDrivers) {
            loadLayout(R.layout.fragment_driver_archive)
        } else {
            loadLayout(R.layout.fragment_archive)
        }
        
        setupArchivedList(showDrivers)

        findViewById<TextView>(R.id.tabArchivedParents)?.setOnClickListener {
            loadArchive(false)
        }

        findViewById<TextView>(R.id.tabArchivedDrivers)?.setOnClickListener {
            loadArchive(true)
        }
    }

    private fun setupArchivedList(showDrivers: Boolean) {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerArchived) ?: return
        val roleFilter = if (showDrivers) "Driver" else "Parent"
        val filteredList = archivedUsers.filter { it.role == roleFilter }.toMutableList()
        
        val adapter = UserAdapter(filteredList,
            onViewClick = { user -> showUserDetailDialog(user, isPending = false) },
            onArchiveClick = { user, _ ->
                // In archive view, clicking the archive button RESTORES the user
                archivedUsers.remove(user)
                user.isArchived = false
                if (user.role == "Driver") activeDrivers.add(user) else activeUsers.add(user)
                loadArchive(showDrivers)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun showUserDetailDialog(user: UserAdmin, isPending: Boolean) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view_parent, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.tvFirstName)?.text = user.name.split(" ").getOrNull(0) ?: user.name
        dialogView.findViewById<TextView>(R.id.tvLastName)?.text = user.name.split(" ").getOrNull(1) ?: ""

        dialogView.findViewById<ImageButton>(R.id.btnClose)?.setOnClickListener {
            dialog.dismiss()
        }

        if (isPending) {
            dialogView.findViewById<Button>(R.id.btnAccept)?.setOnClickListener {
                val pos = pendingUsers.indexOf(user)
                if (pos != -1) approveUser(user, pos)
                dialog.dismiss()
            }
            dialogView.findViewById<Button>(R.id.btnReject)?.setOnClickListener {
                val pos = pendingUsers.indexOf(user)
                if (pos != -1) rejectUser(user, pos)
                dialog.dismiss()
            }
        } else {
            dialogView.findViewById<LinearLayout>(R.id.layoutActionButtons)?.visibility = android.view.View.GONE
        }

        dialog.show()
    }

    private fun showAddDriverDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_driver, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<ImageButton>(R.id.btnCloseAddDriver)?.setOnClickListener { dialog.dismiss() }
        
        dialogView.findViewById<TextView>(R.id.btnSaveDriver)?.setOnClickListener {
            val firstName = dialogView.findViewById<EditText>(R.id.etFirstName).text.toString()
            val lastName = dialogView.findViewById<EditText>(R.id.etLastName).text.toString()
            val fullName = "$firstName $lastName"
            
            if (firstName.isNotEmpty() && lastName.isNotEmpty()) {
                val newDriver = UserAdmin(activeDrivers.size + 100, fullName, "Driver")
                activeDrivers.add(newDriver)
                loadDrivers()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun loadLayout(layoutResId: Int) {
        val container = findViewById<android.widget.FrameLayout>(R.id.fragmentContainer)
        container.removeAllViews()
        layoutInflater.inflate(layoutResId, container, true)
    }
}
