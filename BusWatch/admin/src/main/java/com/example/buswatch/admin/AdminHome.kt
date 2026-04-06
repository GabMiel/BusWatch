package com.example.buswatch.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AdminHome : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var db: FirebaseFirestore
    
    // Data storage
    private var activeUsers = mutableListOf<UserAdmin>()
    private var activeDrivers = mutableListOf<UserAdmin>()
    private var archivedUsers = mutableListOf<UserAdmin>()
    private var pendingUsers = mutableListOf<UserAdmin>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_main)

        db = FirebaseFirestore.getInstance()
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
            FirebaseAuth.getInstance().signOut()
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
        
        findViewById<TextView>(R.id.tabDrivers)?.setOnClickListener {
            loadDrivers()
        }

        findViewById<TextView>(R.id.btnViewPendingParents)?.setOnClickListener {
            loadPendingParents()
        }

        fetchParents()
    }

    private fun fetchParents() {
        db.collection("parents")
            .whereEqualTo("status", "approved")
            .get()
            .addOnSuccessListener { documents ->
                activeUsers.clear()
                for (document in documents) {
                    val firstName = document.getString("firstName") ?: ""
                    val lastName = document.getString("lastName") ?: ""
                    activeUsers.add(UserAdmin(document.id, "$firstName $lastName", "Parent", status = "approved"))
                }
                setupUserList()
            }
    }

    private fun loadDrivers() {
        loadLayout(R.layout.fragment_driver)
        setupDriverList() // Note: Driver logic might also need Firebase connection later

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
                archiveUser(user)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun archiveUser(user: UserAdmin) {
        db.collection("parents").document(user.id)
            .update("status", "archived")
            .addOnSuccessListener {
                loadUsers()
            }
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
        
        findViewById<ImageButton>(R.id.btnBackPending)?.setOnClickListener {
            loadUsers()
        }

        fetchPendingParents()
    }

    private fun fetchPendingParents() {
        db.collection("parents")
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { documents ->
                pendingUsers.clear()
                for (document in documents) {
                    val firstName = document.getString("firstName") ?: ""
                    val lastName = document.getString("lastName") ?: ""
                    pendingUsers.add(UserAdmin(document.id, "$firstName $lastName", "Parent", status = "pending"))
                }
                setupPendingList()
            }
    }

    private fun setupPendingList() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerPending) ?: return
        val adapter = PendingUserAdapter(pendingUsers,
            onAcceptClick = { user, pos -> approveUser(user, pos) },
            onRejectClick = { user, pos -> rejectUser(user, pos) },
            onViewClick = { user -> showUserDetailDialog(user, isPending = true) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun approveUser(user: UserAdmin, position: Int) {
        db.collection("parents").document(user.id)
            .update("status", "approved")
            .addOnSuccessListener {
                Toast.makeText(this, "${user.name} approved", Toast.LENGTH_SHORT).show()
                pendingUsers.removeAt(position)
                activeUsers.add(user)
                loadPendingParents() // Refresh list
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to approve user", Toast.LENGTH_SHORT).show()
            }
    }

    private fun rejectUser(user: UserAdmin, position: Int) {
        db.collection("parents").document(user.id)
            .update("status", "rejected")
            .addOnSuccessListener {
                Toast.makeText(this, "${user.name} rejected", Toast.LENGTH_SHORT).show()
                pendingUsers.removeAt(position)
                loadPendingParents() // Refresh list
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to reject user", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadArchive(showDrivers: Boolean = false) {
        if (showDrivers) {
            loadLayout(R.layout.fragment_driver_archive)
        } else {
            loadLayout(R.layout.fragment_archive)
        }
        
        findViewById<TextView>(R.id.tabArchivedParents)?.setOnClickListener {
            loadArchive(false)
        }

        findViewById<TextView>(R.id.tabArchivedDrivers)?.setOnClickListener {
            loadArchive(true)
        }

        if (!showDrivers) {
            fetchArchivedParents()
        } else {
            setupArchivedList(true)
        }
    }

    private fun fetchArchivedParents() {
        db.collection("parents")
            .whereIn("status", listOf("archived", "rejected"))
            .get()
            .addOnSuccessListener { documents ->
                val filteredList = mutableListOf<UserAdmin>()
                for (document in documents) {
                    val firstName = document.getString("firstName") ?: ""
                    val lastName = document.getString("lastName") ?: ""
                    filteredList.add(UserAdmin(document.id, "$firstName $lastName", "Parent", isArchived = true))
                }
                val recyclerView = findViewById<RecyclerView>(R.id.recyclerArchived) ?: return@addOnSuccessListener
                val adapter = UserAdapter(filteredList,
                    onViewClick = { user -> showUserDetailDialog(user, isPending = false) },
                    onArchiveClick = { user, _ ->
                        restoreUser(user)
                    }
                )
                recyclerView.layoutManager = LinearLayoutManager(this)
                recyclerView.adapter = adapter
            }
    }

    private fun restoreUser(user: UserAdmin) {
        db.collection("parents").document(user.id)
            .update("status", "approved")
            .addOnSuccessListener {
                loadArchive(false)
            }
    }

    private fun setupArchivedList(showDrivers: Boolean) {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerArchived) ?: return
        val roleFilter = if (showDrivers) "Driver" else "Parent"
        val filteredList = archivedUsers.filter { it.role == roleFilter }.toMutableList()
        
        val adapter = UserAdapter(filteredList,
            onViewClick = { user -> showUserDetailDialog(user, isPending = false) },
            onArchiveClick = { user, _ ->
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
                val pos = pendingUsers.indexOfFirst { it.id == user.id }
                if (pos != -1) approveUser(user, pos)
                dialog.dismiss()
            }
            dialogView.findViewById<Button>(R.id.btnReject)?.setOnClickListener {
                val pos = pendingUsers.indexOfFirst { it.id == user.id }
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
                val newDriver = UserAdmin(java.util.UUID.randomUUID().toString(), fullName, "Driver")
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