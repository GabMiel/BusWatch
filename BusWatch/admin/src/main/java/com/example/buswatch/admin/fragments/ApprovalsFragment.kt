package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.MapRequest
import com.example.buswatch.admin.MapRequestAdapter
import com.example.buswatch.admin.PendingUserAdapter
import com.example.buswatch.admin.R
import com.example.buswatch.admin.UserAdmin
import com.example.buswatch.admin.StopAdmin
import com.google.firebase.firestore.FirebaseFirestore

class ApprovalsFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private var currentTab = Tab.REGISTRATION

    enum class Tab { REGISTRATION, MAP, STOPS }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_approvals, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        fetchData()
    }

    private fun setupUI(view: View) {
        view.findViewById<TextView>(R.id.tabRegistration)?.setOnClickListener {
            currentTab = Tab.REGISTRATION
            updateTabUI(view)
            fetchPendingUsers()
        }

        view.findViewById<TextView>(R.id.tabMapLocations)?.setOnClickListener {
            currentTab = Tab.MAP
            updateTabUI(view)
            fetchMapRequests()
        }

        view.findViewById<TextView>(R.id.tabStopApprovals)?.setOnClickListener {
            currentTab = Tab.STOPS
            updateTabUI(view)
            fetchPendingStops()
        }

        view.findViewById<RecyclerView>(R.id.recyclerApprovals)?.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun updateTabUI(view: View) {
        val tabReg = view.findViewById<TextView>(R.id.tabRegistration)
        val tabMap = view.findViewById<TextView>(R.id.tabMapLocations)
        val tabStops = view.findViewById<TextView>(R.id.tabStopApprovals)
        
        val activeRes = com.example.buswatch.common.R.drawable.bg_tab_active
        val inactiveRes = com.example.buswatch.common.R.drawable.bg_tab_inactive

        tabReg?.setBackgroundResource(if (currentTab == Tab.REGISTRATION) activeRes else inactiveRes)
        tabMap?.setBackgroundResource(if (currentTab == Tab.MAP) activeRes else inactiveRes)
        tabStops?.setBackgroundResource(if (currentTab == Tab.STOPS) activeRes else inactiveRes)
    }

    private fun fetchData() {
        when (currentTab) {
            Tab.REGISTRATION -> fetchPendingUsers()
            Tab.MAP -> fetchMapRequests()
            Tab.STOPS -> fetchPendingStops()
        }
    }

    private fun fetchPendingUsers() {
        db.collection("parents").whereEqualTo("status", "pending").get().addOnSuccessListener { snapshots ->
            if (!isAdded) return@addOnSuccessListener
            val pendingUsers = snapshots.map { doc ->
                @Suppress("UNCHECKED_CAST")
                val profile = doc.get("profile") as? Map<String, Any>
                UserAdmin(doc.id, "${profile?.get("firstName")} ${profile?.get("lastName")}", "Parent", status = "pending")
            }.toMutableList()
            view?.findViewById<RecyclerView>(R.id.recyclerApprovals)?.adapter = PendingUserAdapter(pendingUsers) { user ->
                (requireActivity() as? AdminHome)?.showParentApprovalDetail(user)
            }
        }
    }

    private fun fetchMapRequests() {
        db.collection("map_requests").whereEqualTo("status", "pending").get().addOnSuccessListener { snapshots ->
            if (!isAdded) return@addOnSuccessListener
            val mapRequests = snapshots.map { doc ->
                MapRequest(
                    id = doc.id,
                    studentName = doc.getString("studentName") ?: "N/A",
                    parentId = doc.getString("parentId") ?: "",
                    studentId = doc.getString("studentId"),
                    currentAddress = doc.getString("currentAddress") ?: "N/A",
                    pendingAddress = doc.getString("pendingAddress") ?: "N/A",
                    currentLat = doc.getDouble("currentLat") ?: 0.0,
                    currentLng = doc.getDouble("currentLng") ?: 0.0,
                    pendingLat = doc.getDouble("pendingLat") ?: 0.0,
                    pendingLng = doc.getDouble("pendingLng") ?: 0.0,
                    docPath = doc.getString("docPath") ?: ""
                )
            }.toMutableList()
            view?.findViewById<RecyclerView>(R.id.recyclerApprovals)?.adapter = MapRequestAdapter(mapRequests) { request, _ ->
                (requireActivity() as? AdminHome)?.showMapApprovalDetail(request)
            }
        }
    }

    private fun fetchPendingStops() {
        // Implementation for stop approvals fetch if needed, 
        // for now just placeholder to avoid build error with missing adapter
    }
}
