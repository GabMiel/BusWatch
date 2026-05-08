package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.admin.RouteAdmin
import com.example.buswatch.admin.RouteAdapter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlin.math.ceil

class RoutingFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private val itemsPerPage = 10
    private var currentPage = 1
    private var totalCount = 0
    private var sortDirection = Query.Direction.ASCENDING
    private var searchQuery = ""
    
    private var routesListener: ListenerRegistration? = null
    private var parentsListener: ListenerRegistration? = null
    private var busesListener: ListenerRegistration? = null
    private var driversListener: ListenerRegistration? = null

    private var allRoutesList = mutableListOf<RouteAdmin>()
    private val stopOccupancy = mutableMapOf<String, Int>()
    private val busMap = mutableMapOf<String, String>()
    private val driverMap = mutableMapOf<String, String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_routing, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        startListeners()
    }

    private fun startListeners() {
        // Listen to buses for names
        busesListener = db.collection("buses").addSnapshotListener { snapshots, _ ->
            snapshots?.forEach { busMap[it.id] = it.getString("busNumber") ?: "N/A" }
            updateRouteDetails()
        }

        // Listen to drivers for names
        driversListener = db.collection("drivers").addSnapshotListener { snapshots, _ ->
            snapshots?.forEach { driverMap[it.id] = "${it.getString("firstName")} ${it.getString("lastName")}" }
            updateRouteDetails()
        }

        // Listen to parents for occupancy
        parentsListener = db.collection("parents").addSnapshotListener { snapshots, e ->
            if (e != null || snapshots == null) return@addSnapshotListener
            stopOccupancy.clear()
            for (pDoc in snapshots) {
                @Suppress("UNCHECKED_CAST")
                val child = pDoc.get("child") as? Map<String, Any>
                child?.get("stop")?.toString()?.let { if (it.isNotEmpty()) stopOccupancy[it] = (stopOccupancy[it] ?: 0) + 1 }
                
                @Suppress("UNCHECKED_CAST")
                val childrenList = pDoc.get("children") as? List<Map<String, Any>>
                childrenList?.forEach { c ->
                    c["stop"]?.toString()?.let { if (it.isNotEmpty()) stopOccupancy[it] = (stopOccupancy[it] ?: 0) + 1 }
                }
            }
            updateRouteDetails()
        }

        // Listen to routes
        routesListener = db.collection("routes")
            .whereEqualTo("status", "Active")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                allRoutesList = snapshots.map { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val stopIds = doc.get("stopIds") as? List<String> ?: emptyList()
                    RouteAdmin(
                        id = doc.id,
                        routeName = doc.getString("routeName") ?: "N/A",
                        busNumber = "Loading...", 
                        driverName = "Loading...",
                        currentCapacity = 0,
                        maxCapacity = doc.getLong("maxCapacity")?.toInt() ?: 40,
                        status = "Active",
                        morningStartTime = doc.getString("morningStartTime") ?: "",
                        morningEndTime = doc.getString("morningEndTime") ?: "",
                        afternoonStartTime = doc.getString("afternoonStartTime") ?: "",
                        afternoonEndTime = doc.getString("afternoonEndTime") ?: "",
                        busId = doc.getString("busId") ?: "",
                        driverId = doc.getString("driverId") ?: "",
                        stopIds = stopIds
                    )
                }.toMutableList()
                updateRouteDetails()
            }
    }

    private fun updateRouteDetails() {
        allRoutesList.forEach { route ->
            route.busNumber = busMap[route.busId] ?: "N/A"
            route.driverName = driverMap[route.driverId] ?: "N/A"
            route.currentCapacity = route.stopIds.sumOf { stopOccupancy[it] ?: 0 }
        }
        updateList()
    }

    override fun onDestroyView() {
        routesListener?.remove()
        parentsListener?.remove()
        busesListener?.remove()
        driversListener?.remove()
        super.onDestroyView()
    }

    private fun setupUI(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.recyclerRoutes)
        rv?.layoutManager = LinearLayoutManager(requireContext())

        view.findViewById<TextView>(R.id.tabAllRoutesMap)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.loadRouteMap()
        }

        view.findViewById<TextView>(R.id.btnAddNewRoute)?.setOnClickListener { 
             (requireActivity() as? AdminHome)?.showAddNewRouteDialogInternal()
        }

        view.findViewById<ImageButton>(R.id.btnSort)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.showSortOptions("routes") { dir ->
                sortDirection = dir
                currentPage = 1
                updateList()
            }
        }

        val etSearch = view.findViewById<EditText>(R.id.searchRoutes)
        etSearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.lowercase() ?: ""
                currentPage = 1
                updateList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        setupPagination(view)
    }

    private fun updateList() {
        val filtered = allRoutesList.filter { it.routeName.lowercase().contains(searchQuery) }
        val sorted = if (sortDirection == Query.Direction.ASCENDING) {
            filtered.sortedBy { it.routeName.lowercase() }
        } else {
            filtered.sortedByDescending { it.routeName.lowercase() }
        }

        totalCount = sorted.size
        val totalPages = ceil(totalCount.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
        if (currentPage > totalPages) currentPage = totalPages
        if (currentPage < 1) currentPage = 1

        val start = (currentPage - 1) * itemsPerPage
        val end = minOf(start + itemsPerPage, totalCount)
        val paged = if (start < totalCount) sorted.subList(start, end) else emptyList()

        view?.findViewById<RecyclerView>(R.id.recyclerRoutes)?.adapter = RouteAdapter(paged,
            onViewClick = { (requireActivity() as? AdminHome)?.showRouteDetailInternal(it) },
            onEditClick = { (requireActivity() as? AdminHome)?.editRouteDetailInternal(it) },
            onArchiveClick = { (requireActivity() as? AdminHome)?.archiveRouteInternal(it) { } })
        
        updatePaginationUI()
    }

    private fun setupPagination(view: View) {
        val etPage = view.findViewById<EditText>(R.id.etCurrentPage)
        etPage?.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                handleJumpToPage(v.text.toString().toIntOrNull() ?: 1)
                true
            } else false
        }
        view.findViewById<View>(R.id.btnFirstPage)?.setOnClickListener { handleJumpToPage(1) }
        view.findViewById<View>(R.id.btnPrevPage)?.setOnClickListener { handleJumpToPage(currentPage - 1) }
        view.findViewById<View>(R.id.btnNextPage)?.setOnClickListener { handleJumpToPage(currentPage + 1) }
        view.findViewById<View>(R.id.btnLastPage)?.setOnClickListener { 
            val maxPage = ceil(totalCount.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
            handleJumpToPage(maxPage)
        }
    }

    private fun handleJumpToPage(page: Int) {
        val maxPage = ceil(totalCount.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
        currentPage = page.coerceIn(1, maxPage)
        updateList()
    }

    private fun updatePaginationUI() {
        val view = view ?: return
        val maxPage = ceil(totalCount.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
        view.findViewById<EditText>(R.id.etCurrentPage)?.setText(currentPage.toString())
        val totalPagesText = " of $maxPage"
        view.findViewById<TextView>(R.id.tvTotalPages)?.text = totalPagesText
        
        val btnPrev = view.findViewById<View>(R.id.btnPrevPage)
        val btnFirst = view.findViewById<View>(R.id.btnFirstPage)
        val btnNext = view.findViewById<View>(R.id.btnNextPage)
        val btnLast = view.findViewById<View>(R.id.btnLastPage)

        val canPrev = currentPage > 1
        val canNext = currentPage < maxPage

        btnPrev?.isEnabled = canPrev
        btnFirst?.isEnabled = canPrev
        btnNext?.isEnabled = canNext
        btnLast?.isEnabled = canNext

        btnPrev?.alpha = if (canPrev) 1.0f else 0.3f
        btnFirst?.alpha = if (canPrev) 1.0f else 0.3f
        btnNext?.alpha = if (canNext) 1.0f else 0.3f
        btnLast?.alpha = if (canNext) 1.0f else 0.3f
    }
}
