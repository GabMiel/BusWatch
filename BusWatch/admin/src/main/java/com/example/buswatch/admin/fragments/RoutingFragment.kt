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
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.admin.RouteAdmin
import com.example.buswatch.admin.RouteAdapter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlin.math.ceil

class RoutingFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private val itemsPerPage = 10
    private var currentPage = 1
    private var totalCount = 0
    private var sortDirection = Query.Direction.ASCENDING
    private var searchQuery = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_routing, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        fetchPage()
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
                fetchPage()
            }
        }

        val etSearch = view.findViewById<EditText>(R.id.searchRoutes)
        etSearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.lowercase() ?: ""
                currentPage = 1
                fetchPage()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        setupPagination(view)
    }

    private fun fetchPage() {
        db.collection("routes")
            .whereEqualTo("status", "Active")
            .get()
            .addOnSuccessListener { snapshots ->
                if (snapshots == null || snapshots.isEmpty) {
                    view?.findViewById<RecyclerView>(R.id.recyclerRoutes)?.adapter = RouteAdapter(emptyList(), {}, {}, {})
                    totalCount = 0
                    updatePaginationUI()
                    return@addOnSuccessListener
                }
                
                val rawRoutes = mutableListOf<RouteAdmin>()
                var processedCount = 0
                val totalDocs = snapshots.size()

                for (doc in snapshots) {
                    val routeId = doc.id
                    val routeName = doc.getString("routeName") ?: "N/A"
                    val busId = doc.getString("busId") ?: ""
                    val driverId = doc.getString("driverId") ?: ""
                    val maxCapacity = doc.getLong("maxCapacity")?.toInt() ?: 40
                    val stopIds = doc.get("stopIds") as? List<String> ?: emptyList()

                    db.collection("buses").document(busId).get().addOnSuccessListener { busDoc ->
                        val actualBusNo = busDoc.getString("busNumber") ?: doc.getString("busNumber") ?: "N/A"
                        db.collection("drivers").document(driverId).get().addOnSuccessListener { driverDoc ->
                            val actualDriverName = if (driverDoc.exists()) {
                                "${driverDoc.getString("firstName")} ${driverDoc.getString("lastName")}"
                            } else {
                                doc.getString("driverName") ?: "N/A"
                            }

                            if (stopIds.isEmpty()) {
                                rawRoutes.add(RouteAdmin(routeId, routeName, actualBusNo, actualDriverName, 0, maxCapacity))
                                processedCount++
                                if (processedCount == totalDocs) finalizeFetch(rawRoutes)
                            } else {
                                db.collection("parents").whereIn("child.stop", stopIds).get().addOnSuccessListener { parentSnapshots ->
                                    rawRoutes.add(RouteAdmin(routeId, routeName, actualBusNo, actualDriverName, parentSnapshots.size(), maxCapacity))
                                    processedCount++
                                    if (processedCount == totalDocs) finalizeFetch(rawRoutes)
                                }.addOnFailureListener {
                                    rawRoutes.add(RouteAdmin(routeId, routeName, actualBusNo, actualDriverName, 0, maxCapacity))
                                    processedCount++
                                    if (processedCount == totalDocs) finalizeFetch(rawRoutes)
                                }
                            }
                        }
                    }.addOnFailureListener {
                        processedCount++
                        if (processedCount == totalDocs) finalizeFetch(rawRoutes)
                    }
                }
            }
    }

    private fun finalizeFetch(allRoutes: List<RouteAdmin>) {
        val filtered = allRoutes.filter { it.routeName.lowercase().contains(searchQuery) }
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

        updateAdapter(paged)
        updatePaginationUI()
    }

    private fun updateAdapter(routes: List<RouteAdmin>) {
        view?.findViewById<RecyclerView>(R.id.recyclerRoutes)?.adapter = RouteAdapter(routes,
            onViewClick = { (requireActivity() as? AdminHome)?.showRouteDetailInternal(it) },
            onEditClick = { (requireActivity() as? AdminHome)?.editRouteDetailInternal(it) },
            onArchiveClick = { (requireActivity() as? AdminHome)?.archiveRouteInternal(it) { fetchPage() } })
    }

    private fun setupPagination(view: View) {
        val etPage = view.findViewById<EditText>(R.id.etCurrentPage)
        etPage?.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val input = v.text.toString().toIntOrNull() ?: 1
                handleJumpToPage(input)
                true
            } else false
        }
        view.findViewById<View>(R.id.btnFirstPage)?.setOnClickListener { handleJumpToPage(1) }
        view.findViewById<View>(R.id.btnPrevPage)?.setOnClickListener { handleJumpToPage(currentPage - 1) }
        view.findViewById<View>(R.id.btnNextPage)?.setOnClickListener { handleJumpToPage(currentPage + 1) }
        view.findViewById<View>(R.id.btnLastPage)?.setOnClickListener { 
            val totalPages = ceil(totalCount.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
            handleJumpToPage(totalPages)
        }
    }

    private fun handleJumpToPage(page: Int) {
        val maxPage = ceil(totalCount.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
        currentPage = page.coerceIn(1, maxPage)
        fetchPage()
    }

    private fun updatePaginationUI() {
        val view = view ?: return
        val maxPage = ceil(totalCount.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
        view.findViewById<EditText>(R.id.etCurrentPage)?.setText(currentPage.toString())
        view.findViewById<TextView>(R.id.tvTotalPages)?.text = " of $maxPage"
        
        view.findViewById<View>(R.id.btnPrevPage)?.isEnabled = currentPage > 1
        view.findViewById<View>(R.id.btnFirstPage)?.isEnabled = currentPage > 1
        view.findViewById<View>(R.id.btnNextPage)?.isEnabled = currentPage < maxPage
        view.findViewById<View>(R.id.btnLastPage)?.isEnabled = currentPage < maxPage
    }
}
