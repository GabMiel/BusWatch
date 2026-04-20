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
import com.google.firebase.firestore.FirebaseFirestore

class ApprovalsFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private var isRegistrationTab = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_approvals, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        fetchData()
    }

    private fun setupUI(view: View) {
        view.findViewById<TextView>(R.id.tabMapLocations)?.setOnClickListener {
            isRegistrationTab = false
            updateTabUI(view)
            fetchMapRequests()
        }

        view.findViewById<TextView>(R.id.tabRegistration)?.setOnClickListener {
            isRegistrationTab = true
            updateTabUI(view)
            fetchPendingUsers()
        }

        view.findViewById<RecyclerView>(R.id.recyclerApprovals)?.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun updateTabUI(view: View) {
        val tabReg = view.findViewById<TextView>(R.id.tabRegistration)
        val tabMap = view.findViewById<TextView>(R.id.tabMapLocations)
        
        if (isRegistrationTab) {
            tabReg?.setBackgroundResource(com.example.buswatch.common.R.drawable.bg_tab_active)
            tabMap?.setBackgroundResource(com.example.buswatch.common.R.drawable.bg_tab_inactive)
        } else {
            tabReg?.setBackgroundResource(com.example.buswatch.common.R.drawable.bg_tab_inactive)
            tabMap?.setBackgroundResource(com.example.buswatch.common.R.drawable.bg_tab_active)
        }
    }

    private fun fetchData() {
        if (isRegistrationTab) fetchPendingUsers() else fetchMapRequests()
    }

    private fun fetchPendingUsers() {
        db.collection("parents").whereEqualTo("status", "pending").get().addOnSuccessListener { snapshots ->
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
}
