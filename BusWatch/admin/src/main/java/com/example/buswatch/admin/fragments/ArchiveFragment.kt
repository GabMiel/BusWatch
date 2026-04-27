package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.admin.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlin.math.ceil

class ArchiveFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private var currentTab = AdminHome.ArchiveTab.PARENTS
    
    private val itemsPerPage = 10
    private var currentPage = 1
    private var totalCount = 0
    private var sortDirection = Query.Direction.ASCENDING
    private var searchQuery = ""

    companion object {
        fun newInstance(tab: AdminHome.ArchiveTab) = ArchiveFragment().apply {
            arguments = Bundle().apply {
                putSerializable("tab", tab)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tab = arguments?.getSerializable("tab") as? AdminHome.ArchiveTab
        if (tab != null) currentTab = tab
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val layoutRes = when (currentTab) {
            AdminHome.ArchiveTab.PARENTS -> R.layout.fragment_archive
            AdminHome.ArchiveTab.DRIVERS -> R.layout.fragment_driver_archive
            AdminHome.ArchiveTab.CONDUCTORS -> R.layout.fragment_conductor_archive
            AdminHome.ArchiveTab.BUS -> R.layout.fragment_bus_archive
            AdminHome.ArchiveTab.STOPS -> R.layout.fragment_stop_archive
            AdminHome.ArchiveTab.ROUTES -> R.layout.fragment_route_archive
        }
        return inflater.inflate(layoutRes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        fetchData()
    }

    private fun setupUI(view: View) {
        view.findViewById<TextView>(R.id.tabArchivedParents)?.setOnClickListener { switchTab(AdminHome.ArchiveTab.PARENTS) }
        view.findViewById<TextView>(R.id.tabArchivedDrivers)?.setOnClickListener { switchTab(AdminHome.ArchiveTab.DRIVERS) }
        view.findViewById<TextView>(R.id.tabArchivedConductors)?.setOnClickListener { switchTab(AdminHome.ArchiveTab.CONDUCTORS) }
        view.findViewById<TextView>(R.id.tabArchivedBus)?.setOnClickListener { switchTab(AdminHome.ArchiveTab.BUS) }
        view.findViewById<TextView>(R.id.tabArchivedStops)?.setOnClickListener { switchTab(AdminHome.ArchiveTab.STOPS) }
        view.findViewById<TextView>(R.id.tabArchivedRoutes)?.setOnClickListener { switchTab(AdminHome.ArchiveTab.ROUTES) }

        view.findViewById<RecyclerView>(R.id.recyclerArchived)?.layoutManager = LinearLayoutManager(requireContext())
        
        view.findViewById<ImageButton>(R.id.btnSort)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.showSortOptions("Archived " + currentTab.name.lowercase()) { dir ->
                sortDirection = dir
                currentPage = 1
                fetchData()
            }
        }

        val etSearch = view.findViewById<EditText>(R.id.searchArchived)
        etSearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.lowercase() ?: ""
                currentPage = 1
                fetchData()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Pagination Controls
        view.findViewById<TextView>(R.id.btnFirstPage)?.setOnClickListener {
            if (currentPage != 1) {
                currentPage = 1
                fetchData()
            }
        }

        view.findViewById<TextView>(R.id.btnPrevPage)?.setOnClickListener {
            if (currentPage > 1) {
                currentPage--
                fetchData()
            }
        }

        view.findViewById<TextView>(R.id.btnNextPage)?.setOnClickListener {
            val totalPages = ceil(totalCount.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
            if (currentPage < totalPages) {
                currentPage++
                fetchData()
            }
        }

        view.findViewById<TextView>(R.id.btnLastPage)?.setOnClickListener {
            val totalPages = ceil(totalCount.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
            if (currentPage != totalPages && totalPages > 0) {
                currentPage = totalPages
                fetchData()
            }
        }
    }

    private fun switchTab(tab: AdminHome.ArchiveTab) {
        (requireActivity() as? AdminHome)?.replaceFragment(newInstance(tab))
    }

    private fun fetchData() {
        when (currentTab) {
            AdminHome.ArchiveTab.PARENTS -> fetchParents()
            AdminHome.ArchiveTab.DRIVERS -> fetchDrivers()
            AdminHome.ArchiveTab.CONDUCTORS -> fetchConductors()
            AdminHome.ArchiveTab.BUS -> fetchBuses()
            AdminHome.ArchiveTab.STOPS -> fetchStops()
            AdminHome.ArchiveTab.ROUTES -> fetchRoutes()
        }
    }

    private fun updatePaginationUI() {
        val view = view ?: return
        val totalPages = ceil(totalCount.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
        
        view.findViewById<EditText>(R.id.etCurrentPage)?.setText(currentPage.toString())
        view.findViewById<TextView>(R.id.tvTotalPages)?.text = " of $totalPages"
        
        view.findViewById<TextView>(R.id.btnPrevPage)?.isEnabled = currentPage > 1
        view.findViewById<TextView>(R.id.btnFirstPage)?.isEnabled = currentPage > 1
        view.findViewById<TextView>(R.id.btnNextPage)?.isEnabled = currentPage < totalPages
        view.findViewById<TextView>(R.id.btnLastPage)?.isEnabled = currentPage < totalPages
    }

    private fun showDeleteConfirmation(onConfirm: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirm Delete")
            .setMessage("Are you sure you want to delete this permanently? This action cannot be undone.")
            .setPositiveButton("Confirm") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fetchParents() {
        db.collection("parents").whereIn("status", listOf("archived", "rejected")).get().addOnSuccessListener { documents ->
            val allItems = documents.map { doc ->
                @Suppress("UNCHECKED_CAST")
                val profile = doc.get("profile") as? Map<String, Any>
                UserAdmin(doc.id, "${profile?.get("firstName")} ${profile?.get("lastName")}", "Parent", isArchived = true, status = doc.getString("status") ?: "archived")
            }.filter { it.name.lowercase().contains(searchQuery) }

            val sorted = if (sortDirection == Query.Direction.ASCENDING) allItems.sortedBy { it.name.lowercase() } else allItems.sortedByDescending { it.name.lowercase() }
            totalCount = sorted.size
            val paged = paginateList(sorted)

            view?.findViewById<RecyclerView>(R.id.recyclerArchived)?.adapter = ArchivedUserAdapter(paged,
                onRestoreClick = { (requireActivity() as? AdminHome)?.restoreUserInternal(it)?.addOnSuccessListener { 
                    Toast.makeText(requireContext(), "Restored successfully", Toast.LENGTH_SHORT).show()
                    fetchData() 
                } },
                onDeleteClick = { user -> showDeleteConfirmation { (requireActivity() as? AdminHome)?.deleteUserInternal(user); fetchData() } },
                onViewClick = { (requireActivity() as? AdminHome)?.showParentDetail(it) { fetchData() } }
            )
            updatePaginationUI()
        }
    }

    private fun fetchDrivers() {
        db.collection("drivers").whereEqualTo("status", "archived").get().addOnSuccessListener { documents ->
            val allItems = documents.map { doc -> UserAdmin(doc.id, "${doc.getString("firstName")} ${doc.getString("lastName")}", "Driver", isArchived = true, status = "archived") }
                .filter { it.name.lowercase().contains(searchQuery) }

            val sorted = if (sortDirection == Query.Direction.ASCENDING) allItems.sortedBy { it.name.lowercase() } else allItems.sortedByDescending { it.name.lowercase() }
            totalCount = sorted.size
            val paged = paginateList(sorted)

            view?.findViewById<RecyclerView>(R.id.recyclerArchived)?.adapter = ArchivedUserAdapter(paged,
                onRestoreClick = { (requireActivity() as? AdminHome)?.restoreDriverInternal(it)?.addOnSuccessListener { 
                    Toast.makeText(requireContext(), "Restored successfully", Toast.LENGTH_SHORT).show()
                    fetchData() 
                } },
                onDeleteClick = { user -> showDeleteConfirmation { (requireActivity() as? AdminHome)?.deleteUserInternal(user); fetchData() } },
                onViewClick = { (requireActivity() as? AdminHome)?.showDriverDetail(it) { fetchData() } }
            )
            updatePaginationUI()
        }
    }

    private fun fetchConductors() {
        db.collection("conductors").whereEqualTo("status", "archived").get().addOnSuccessListener { documents ->
            val allItems = documents.map { doc -> UserAdmin(doc.id, "${doc.getString("firstName")} ${doc.getString("lastName")}", "Conductor", isArchived = true, status = "archived") }
                .filter { it.name.lowercase().contains(searchQuery) }

            val sorted = if (sortDirection == Query.Direction.ASCENDING) allItems.sortedBy { it.name.lowercase() } else allItems.sortedByDescending { it.name.lowercase() }
            totalCount = sorted.size
            val paged = paginateList(sorted)

            view?.findViewById<RecyclerView>(R.id.recyclerArchived)?.adapter = ArchivedUserAdapter(paged,
                onRestoreClick = { (requireActivity() as? AdminHome)?.restoreConductorInternal(it)?.addOnSuccessListener { 
                    Toast.makeText(requireContext(), "Restored successfully", Toast.LENGTH_SHORT).show()
                    fetchData() 
                } },
                onDeleteClick = { user -> showDeleteConfirmation { (requireActivity() as? AdminHome)?.deleteUserInternal(user); fetchData() } },
                onViewClick = { (requireActivity() as? AdminHome)?.showConductorDetail(it) { fetchData() } }
            )
            updatePaginationUI()
        }
    }

    private fun fetchBuses() {
        db.collection("buses").whereEqualTo("status", "Archived").get().addOnSuccessListener { snapshots ->
            val allItems = snapshots.map { doc -> BusAdmin(doc.id, doc.getString("busNumber") ?: "N/A", "Archived") }
                .filter { it.busNumber.lowercase().contains(searchQuery) }

            val sorted = if (sortDirection == Query.Direction.ASCENDING) allItems.sortedBy { it.busNumber.lowercase() } else allItems.sortedByDescending { it.busNumber.lowercase() }
            totalCount = sorted.size
            val paged = paginateList(sorted)

            view?.findViewById<RecyclerView>(R.id.recyclerArchived)?.adapter = ArchivedBusAdapter(paged,
                onRestoreClick = { (requireActivity() as? AdminHome)?.restoreBusInternal(it)?.addOnSuccessListener { 
                    Toast.makeText(requireContext(), "Restored successfully", Toast.LENGTH_SHORT).show()
                    fetchData() 
                } },
                onDeleteClick = { bus -> showDeleteConfirmation { (requireActivity() as? AdminHome)?.deleteBusInternal(bus); fetchData() } },
                onViewClick = { (requireActivity() as? AdminHome)?.showBusDetail(it) }
            )
            updatePaginationUI()
        }
    }

    private fun fetchStops() {
        db.collection("stops").whereEqualTo("status", "archived").get().addOnSuccessListener { snapshots ->
            val allItems = snapshots.map { doc -> StopAdmin(doc.id, doc.getString("name") ?: "N/A", doc.getDouble("latitude") ?: 0.0, doc.getDouble("longitude") ?: 0.0) }
                .filter { it.name.lowercase().contains(searchQuery) }

            val sorted = if (sortDirection == Query.Direction.ASCENDING) allItems.sortedBy { it.name.lowercase() } else allItems.sortedByDescending { it.name.lowercase() }
            totalCount = sorted.size
            val paged = paginateList(sorted)

            view?.findViewById<RecyclerView>(R.id.recyclerArchived)?.adapter = ArchivedStopAdapter(paged,
                onRestoreClick = { (requireActivity() as? AdminHome)?.restoreStopInternal(it)?.addOnSuccessListener { 
                    Toast.makeText(requireContext(), "Restored successfully", Toast.LENGTH_SHORT).show()
                    fetchData() 
                } },
                onDeleteClick = { stop -> showDeleteConfirmation { (requireActivity() as? AdminHome)?.deleteStopInternal(stop); fetchData() } },
                onViewClick = { (requireActivity() as? AdminHome)?.showStopDetailInternal(it) { switchTab(AdminHome.ArchiveTab.STOPS) } }
            )
            updatePaginationUI()
        }
    }

    private fun fetchRoutes() {
        db.collection("routes").whereEqualTo("status", "Archived").get().addOnSuccessListener { snapshots ->
            val allItems = snapshots.map { doc -> RouteAdmin(doc.id, doc.getString("routeName") ?: "N/A", doc.getString("busNumber") ?: "N/A", doc.getString("driverName") ?: "N/A", doc.getLong("currentCapacity")?.toInt() ?: 0, doc.getLong("maxCapacity")?.toInt() ?: 0, "Archived") }
                .filter { it.routeName.lowercase().contains(searchQuery) }

            val sorted = if (sortDirection == Query.Direction.ASCENDING) allItems.sortedBy { it.routeName.lowercase() } else allItems.sortedByDescending { it.routeName.lowercase() }
            totalCount = sorted.size
            val paged = paginateList(sorted)

            view?.findViewById<RecyclerView>(R.id.recyclerArchived)?.adapter = ArchivedRouteAdapter(paged,
                onRestoreClick = { (requireActivity() as? AdminHome)?.restoreRouteInternal(it)?.addOnSuccessListener { 
                    Toast.makeText(requireContext(), "Restored successfully", Toast.LENGTH_SHORT).show()
                    fetchData() 
                } },
                onDeleteClick = { route -> showDeleteConfirmation { (requireActivity() as? AdminHome)?.deleteRouteInternal(route); fetchData() } },
                onViewClick = { (requireActivity() as? AdminHome)?.showRouteDetailInternal(it) { switchTab(AdminHome.ArchiveTab.ROUTES) } }
            )
            updatePaginationUI()
        }
    }

    private fun <T> paginateList(list: List<T>): List<T> {
        val totalPages = ceil(totalCount.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
        if (currentPage > totalPages) currentPage = totalPages
        if (currentPage < 1) currentPage = 1
        
        val start = (currentPage - 1) * itemsPerPage
        val end = minOf(start + itemsPerPage, totalCount)
        
        return if (start < totalCount) list.subList(start, end) else emptyList()
    }
}
