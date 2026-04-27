package com.example.buswatch.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.example.buswatch.admin.fragments.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage

class AdminHome : AppCompatActivity() {
    lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage
    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_main)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()
        drawerLayout = findViewById(R.id.drawerLayout)
        
        findViewById<View>(R.id.btnMenuToggle)?.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        setupSidebarNavigation()
        
        if (savedInstanceState == null) {
            replaceFragment(DashboardFragment(), false)
        }
    }

    private fun setupSidebarNavigation() {
        findViewById<LinearLayout>(R.id.navDashboard)?.setOnClickListener { replaceFragment(DashboardFragment()); drawerLayout.closeDrawer(GravityCompat.START) }
        findViewById<LinearLayout>(R.id.navUsers)?.setOnClickListener { replaceFragment(UsersFragment()); drawerLayout.closeDrawer(GravityCompat.START) }
        findViewById<LinearLayout>(R.id.navApprovals)?.setOnClickListener { loadApprovals(); drawerLayout.closeDrawer(GravityCompat.START) }
        findViewById<LinearLayout>(R.id.navArchive)?.setOnClickListener { loadArchive(ArchiveTab.PARENTS); drawerLayout.closeDrawer(GravityCompat.START) }
        findViewById<LinearLayout>(R.id.navBus)?.setOnClickListener { replaceFragment(BusesFragment()); drawerLayout.closeDrawer(GravityCompat.START) }
        findViewById<LinearLayout>(R.id.navRouting)?.setOnClickListener { replaceFragment(RoutingFragment()); drawerLayout.closeDrawer(GravityCompat.START) }
        findViewById<LinearLayout>(R.id.navStops)?.setOnClickListener { replaceFragment(StopsFragment()); drawerLayout.closeDrawer(GravityCompat.START) }
        findViewById<LinearLayout>(R.id.navLogout)?.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, Class.forName("com.example.buswatch.Login"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    fun replaceFragment(fragment: Fragment, addToBackStack: Boolean = true) {
        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
        if (addToBackStack) {
            transaction.addToBackStack(null)
        }
        transaction.commit()
    }

    // --- Navigation Helper Methods ---

    fun showSortOptions(title: String, onSelected: (Query.Direction) -> Unit) {
        val options = arrayOf("A-Z", "Z-A")
        AlertDialog.Builder(this).setTitle("Sort $title").setItems(options) { _, which ->
            onSelected(if (which == 0) Query.Direction.ASCENDING else Query.Direction.DESCENDING)
        }.show()
    }

    fun loadDrivers() = replaceFragment(DriversFragment())
    fun loadUsers() = replaceFragment(UsersFragment())
    fun loadConductors() = replaceFragment(ConductorsFragment())
    fun loadRouting() = replaceFragment(RoutingFragment())
    fun loadStops() = replaceFragment(StopsFragment())
    fun loadApprovals() = replaceFragment(ApprovalsFragment())
    fun loadArchive(tab: ArchiveTab) = replaceFragment(ArchiveFragment.newInstance(tab))
    fun loadRouteMap() = replaceFragment(RouteMapFragment())

    // --- Detail & Edit Triggers ---

    fun showStopDetailInternal(stop: StopAdmin, onBack: (() -> Unit)? = null) {
        replaceFragment(StopDetailFragment.newInstance(stop, onBack ?: { supportFragmentManager.popBackStack() }))
    }

    fun editStopDetailInternal(stop: StopAdmin) {
        replaceFragment(StopEditFragment.newInstance(stop))
    }

    fun showParentDetail(user: UserAdmin, onBack: () -> Unit) {
        replaceFragment(ParentDetailFragment.newInstance(user, onBack))
    }

    fun editParentDetail(user: UserAdmin) {
        replaceFragment(ParentEditFragment.newInstance(user))
    }

    fun showDriverDetail(user: UserAdmin, onBack: () -> Unit = {}) {
        replaceFragment(DriverDetailFragment.newInstance(user, onBack))
    }

    fun editDriverDetail(user: UserAdmin) {
        replaceFragment(DriverEditFragment.newInstance(user))
    }

    fun showConductorDetail(user: UserAdmin, onBack: () -> Unit) {
        replaceFragment(ConductorDetailFragment.newInstance(user, onBack))
    }

    fun editConductorDetail(user: UserAdmin) {
        replaceFragment(ConductorEditFragment.newInstance(user))
    }

    fun showBusDetail(bus: BusAdmin) {
        replaceFragment(BusDetailFragment.newInstance(bus))
    }

    fun editBusDetail(bus: BusAdmin) {
        replaceFragment(BusEditFragment.newInstance(bus))
    }

    fun showRouteDetailInternal(route: RouteAdmin, onBack: (() -> Unit)? = null) {
        replaceFragment(RouteDetailFragment.newInstance(route, onBack ?: { supportFragmentManager.popBackStack() }))
    }

    fun editRouteDetailInternal(route: RouteAdmin) {
        replaceFragment(RouteEditFragment.newInstance(route))
    }

    fun showParentApprovalDetail(user: UserAdmin) {
        showParentDetail(user) { supportFragmentManager.popBackStack() }
    }
    
    fun showMapApprovalDetail(request: MapRequest) {
        replaceFragment(MapApprovalDetailFragment.newInstance(request))
    }

    fun showAddNewRouteDialogInternal() {
        AddRouteDialog(this, db) {
            // Refresh RoutingFragment if it is currently visible
            (supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? RoutingFragment)?.onViewCreated(View(this), null)
        }.show()
    }

    // --- Action Methods ---

    fun archiveUser(user: UserAdmin, onComplete: (() -> Unit)? = null) {
        val collection = when(user.role) {
            "Parent" -> "parents"
            "Driver" -> "drivers"
            "Conductor" -> "conductors"
            else -> "users"
        }
        db.collection(collection).document(user.id).update("status", "archived").addOnSuccessListener { 
            Toast.makeText(this, "Archived successfully", Toast.LENGTH_SHORT).show()
            onComplete?.invoke()
        }
    }

    fun archiveBus(bus: BusAdmin, onComplete: (() -> Unit)? = null) {
        db.collection("buses").document(bus.id).update("status", "Archived").addOnSuccessListener { 
            Toast.makeText(this, "Bus archived successfully", Toast.LENGTH_SHORT).show()
            onComplete?.invoke()
        }
    }

    fun archiveRouteInternal(route: RouteAdmin, onComplete: (() -> Unit)? = null) {
        db.collection("routes").document(route.id).update("status", "Archived").addOnSuccessListener { 
            Toast.makeText(this, "Route archived successfully", Toast.LENGTH_SHORT).show()
            onComplete?.invoke()
        }
    }

    fun archiveStopInternal(stop: StopAdmin, onComplete: (() -> Unit)? = null) {
        db.collection("stops").document(stop.id).update("status", "archived").addOnSuccessListener { 
            Toast.makeText(this, "Stop archived successfully", Toast.LENGTH_SHORT).show()
            onComplete?.invoke()
        }
    }

    fun approveUserFromApprovalsInternal(user: UserAdmin) {
        db.collection("parents").document(user.id).update("status", "approved").addOnSuccessListener { 
            sendApprovalNotification(user.id, true)
            supportFragmentManager.popBackStack()
        }
    }

    fun rejectUserFromApprovalsInternal(user: UserAdmin) {
        db.collection("parents").document(user.id).update("status", "rejected").addOnSuccessListener { 
            sendApprovalNotification(user.id, false)
            supportFragmentManager.popBackStack()
        }
    }

    private fun sendApprovalNotification(userId: String, isApproved: Boolean) {
        val title = if (isApproved) "Account Approved" else "Account Rejected"
        val message = if (isApproved) 
            "Congratulations! Your account has been approved. You can now use all features of BusWatch."
        else 
            "Unfortunately, your account application was rejected. Please contact the administrator for more details."
            
        val notifData = hashMapOf(
            "title" to title,
            "message" to message,
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false,
            "type" to "account_status"
        )
        
        db.collection("parents").document(userId).collection("notifications").add(notifData)
    }

    fun restoreUserInternal(user: UserAdmin) = db.collection("parents").document(user.id).update("status", "approved")
    fun restoreDriverInternal(user: UserAdmin) = db.collection("drivers").document(user.id).update("status", "active")
    fun restoreConductorInternal(user: UserAdmin) = db.collection("conductors").document(user.id).update("status", "active")
    fun restoreBusInternal(bus: BusAdmin) = db.collection("buses").document(bus.id).update("status", "Active")
    fun restoreStopInternal(stop: StopAdmin) = db.collection("stops").document(stop.id).update("status", "active")
    fun restoreRouteInternal(route: RouteAdmin) = db.collection("routes").document(route.id).update("status", "Active")

    fun deleteUserInternal(user: UserAdmin) {
        val collection = when(user.role) {
            "Parent" -> "parents"
            "Driver" -> "drivers"
            "Conductor" -> "conductors"
            else -> "users"
        }
        db.collection(collection).document(user.id).delete()
    }
    fun deleteBusInternal(bus: BusAdmin) = db.collection("buses").document(bus.id).delete()
    fun deleteStopInternal(stop: StopAdmin) = db.collection("stops").document(stop.id).delete()
    fun deleteRouteInternal(route: RouteAdmin) = db.collection("routes").document(route.id).delete()

    enum class ArchiveTab { PARENTS, DRIVERS, CONDUCTORS, BUS, STOPS, ROUTES }
}
