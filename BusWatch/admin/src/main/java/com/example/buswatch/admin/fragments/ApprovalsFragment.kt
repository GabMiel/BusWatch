package com.example.buswatch.admin.fragments

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.MapRequest
import com.example.buswatch.admin.MapRequestAdapter
import com.example.buswatch.admin.PendingUserAdapter
import com.example.buswatch.admin.R
import com.example.buswatch.admin.UserAdmin
import com.example.buswatch.admin.StopRequest
import com.example.buswatch.admin.StopRequestAdapter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ApprovalsFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private var currentTab = Tab.REGISTRATION
    private var listenerRegistration: ListenerRegistration? = null

    enum class Tab { REGISTRATION, MAP, STOPS }

    companion object {
        private const val ARG_START_TAB = "start_tab"

        fun newInstance(startTab: Tab): ApprovalsFragment {
            val fragment = ApprovalsFragment()
            val args = Bundle()
            args.putString(ARG_START_TAB, startTab.name)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getString(ARG_START_TAB)?.let {
            currentTab = Tab.valueOf(it)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_approvals, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        updateTabUI(view)
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

        val blackColor = ContextCompat.getColor(requireContext(), com.example.buswatch.common.R.color.black)
        val grayColor = ContextCompat.getColor(requireContext(), com.example.buswatch.common.R.color.gray_text)

        tabReg?.apply {
            setBackgroundResource(if (currentTab == Tab.REGISTRATION) activeRes else inactiveRes)
            setTextColor(if (currentTab == Tab.REGISTRATION) blackColor else grayColor)
            setTypeface(null, if (currentTab == Tab.REGISTRATION) Typeface.BOLD else Typeface.NORMAL)
        }
        tabMap?.apply {
            setBackgroundResource(if (currentTab == Tab.MAP) activeRes else inactiveRes)
            setTextColor(if (currentTab == Tab.MAP) blackColor else grayColor)
            setTypeface(null, if (currentTab == Tab.MAP) Typeface.BOLD else Typeface.NORMAL)
        }
        tabStops?.apply {
            setBackgroundResource(if (currentTab == Tab.STOPS) activeRes else inactiveRes)
            setTextColor(if (currentTab == Tab.STOPS) blackColor else grayColor)
            setTypeface(null, if (currentTab == Tab.STOPS) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun fetchData() {
        when (currentTab) {
            Tab.REGISTRATION -> fetchPendingUsers()
            Tab.MAP -> fetchMapRequests()
            Tab.STOPS -> fetchPendingStops()
        }
    }

    private fun fetchPendingUsers() {
        listenerRegistration?.remove()
        listenerRegistration = db.collection("parents").whereEqualTo("status", "pending")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                if (!isAdded) return@addSnapshotListener
                
                val pendingUsers = snapshots.map { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val profile = doc.get("profile") as? Map<String, Any>
                    UserAdmin(
                        id = doc.id,
                        name = "${profile?.get("firstName")} ${profile?.get("lastName")}",
                        role = "Parent",
                        status = "pending",
                        avatarUrl = profile?.get("parentAvatarUrl") as? String ?: ""
                    )
                }.toMutableList()
                
                view?.findViewById<RecyclerView>(R.id.recyclerApprovals)?.adapter = PendingUserAdapter(pendingUsers) { user ->
                    (requireActivity() as? AdminHome)?.showParentApprovalDetail(user)
                }
            }
    }

    private fun fetchMapRequests() {
        listenerRegistration?.remove()
        listenerRegistration = db.collection("map_requests").whereEqualTo("status", "pending")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                if (!isAdded) return@addSnapshotListener
                
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
                        docPath = doc.getString("docPath") ?: "",
                        parentAvatarUrl = doc.getString("parentAvatarUrl") ?: ""
                    )
                }.toMutableList()
                
                view?.findViewById<RecyclerView>(R.id.recyclerApprovals)?.adapter = MapRequestAdapter(mapRequests) { request, _ ->
                    (requireActivity() as? AdminHome)?.showMapApprovalDetail(request)
                }
            }
    }

    private fun fetchPendingStops() {
        listenerRegistration?.remove()
        listenerRegistration = db.collection("stop_requests").whereEqualTo("status", "pending")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                if (!isAdded) return@addSnapshotListener
                
                val stopRequests = snapshots.map { doc ->
                    StopRequest(
                        id = doc.id,
                        parentId = doc.getString("parentId") ?: "",
                        studentName = doc.getString("studentName") ?: "N/A",
                        studentFirstName = doc.getString("studentFirstName") ?: "",
                        studentLastName = doc.getString("studentLastName") ?: "",
                        currentStopId = doc.getString("currentStopId") ?: "",
                        currentStopName = doc.getString("currentStopName") ?: "N/A",
                        currentStopLat = doc.getDouble("currentStopLat") ?: 0.0,
                        currentStopLng = doc.getDouble("currentStopLng") ?: 0.0,
                        proposedStopId = doc.getString("proposedStopId") ?: "",
                        proposedStopName = doc.getString("proposedStopName") ?: "N/A",
                        proposedStopLat = doc.getDouble("proposedStopLat") ?: 0.0,
                        proposedStopLng = doc.getDouble("proposedStopLng") ?: 0.0,
                        status = doc.getString("status") ?: "pending",
                        parentAvatarUrl = doc.getString("parentAvatarUrl") ?: ""
                    )
                }.toMutableList()
                
                view?.findViewById<RecyclerView>(R.id.recyclerApprovals)?.adapter = StopRequestAdapter(stopRequests) { request, _ ->
                    (requireActivity() as? AdminHome)?.showStopApprovalDetail(request)
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listenerRegistration?.remove()
    }
}
