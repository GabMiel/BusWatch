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
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.admin.AddStopDialog
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.admin.StopAdmin
import com.example.buswatch.admin.StopAdapter
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlin.math.ceil

class StopsFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private val itemsPerPage = 10
    private var currentPage = 1
    private var totalCount = 0
    private var sortDirection = Query.Direction.ASCENDING
    private var searchQuery = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_stops, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        fetchCount()
        fetchPage()
    }

    private fun setupUI(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.recyclerStops)
        rv?.layoutManager = LinearLayoutManager(requireContext())

        view.findViewById<TextView>(R.id.btnAddNewStop)?.setOnClickListener {
            AddStopDialog(requireContext(), db) { fetchPage() }.show()
        }

        view.findViewById<ImageButton>(R.id.btnSort)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.showSortOptions("stops") { dir ->
                sortDirection = dir
                currentPage = 1
                fetchPage()
            }
        }

        val etSearch = view.findViewById<EditText>(R.id.searchStops)
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

    private fun fetchCount() {
        db.collection("stops").whereEqualTo("status", "active").get().addOnSuccessListener { 
            totalCount = it.size()
            updatePaginationUI()
        }
    }

    private fun fetchPage() {
        db.collection("stops")
            .whereEqualTo("status", "active")
            .get()
            .addOnSuccessListener { snapshots ->
                val allStops = snapshots.map { doc ->
                    StopAdmin(doc.id, doc.getString("name") ?: "N/A", doc.getDouble("latitude") ?: 0.0, doc.getDouble("longitude") ?: 0.0)
                }.filter { 
                    it.name.lowercase().contains(searchQuery)
                }

                val sortedStops = if (sortDirection == Query.Direction.ASCENDING) {
                    allStops.sortedBy { it.name.lowercase() }
                } else {
                    allStops.sortedByDescending { it.name.lowercase() }
                }
                
                totalCount = sortedStops.size
                val totalPages = ceil(totalCount.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
                if (currentPage > totalPages) currentPage = totalPages
                if (currentPage < 1) currentPage = 1
                
                val start = (currentPage - 1) * itemsPerPage
                val end = minOf(start + itemsPerPage, totalCount)
                val pagedStops = if (start < totalCount) sortedStops.subList(start, end) else emptyList()
                
                if (pagedStops.isEmpty()) {
                    view?.findViewById<RecyclerView>(R.id.recyclerStops)?.adapter = StopAdapter(emptyList(), {}, {}, {})
                    updatePaginationUI()
                    return@addOnSuccessListener
                }

                // Fetch student counts for paged stops from ALL parents
                db.collection("parents").get().addOnSuccessListener { parentDocs ->
                    val stopCounts = mutableMapOf<String, Int>()
                    for (pDoc in parentDocs) {
                        // Check single child
                        val child = pDoc.get("child") as? Map<String, Any>
                        val cStopId = child?.get("stop") as? String
                        if (!cStopId.isNullOrEmpty()) {
                            stopCounts[cStopId] = (stopCounts[cStopId] ?: 0) + 1
                        }

                        // Check children list
                        val childrenList = pDoc.get("children") as? List<Map<String, Any>>
                        childrenList?.forEach { c ->
                            val sId = c["stop"] as? String
                            if (!sId.isNullOrEmpty()) {
                                stopCounts[sId] = (stopCounts[sId] ?: 0) + 1
                            }
                        }
                    }

                    pagedStops.forEach { stop ->
                        stop.studentCount = stopCounts[stop.id] ?: 0
                    }

                    view?.findViewById<RecyclerView>(R.id.recyclerStops)?.adapter = StopAdapter(pagedStops,
                        onViewClick = { (requireActivity() as? AdminHome)?.showStopDetailInternal(it) },
                        onEditClick = { (requireActivity() as? AdminHome)?.editStopDetailInternal(it) },
                        onArchiveClick = { stop ->
                            (requireActivity() as? AdminHome)?.archiveStopInternal(stop) { fetchPage() }
                        }
                    )
                    updatePaginationUI()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error fetching stops: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
