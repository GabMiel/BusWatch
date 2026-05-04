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
import com.example.buswatch.admin.AddStopDialog
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.admin.StopAdmin
import com.example.buswatch.admin.StopAdapter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlin.math.ceil

class StopsFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private val itemsPerPage = 10
    private var currentPage = 1
    private var totalCount = 0
    private var sortDirection = Query.Direction.ASCENDING
    private var searchQuery = ""
    
    private var stopsListener: ListenerRegistration? = null
    private var parentsListener: ListenerRegistration? = null
    
    private var allStopsList = mutableListOf<StopAdmin>()
    private val stopCounts = mutableMapOf<String, Int>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_stops, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        startListeners()
    }

    private fun startListeners() {
        // Listen to parents for student counts
        parentsListener = db.collection("parents").addSnapshotListener { snapshots, e ->
            if (e != null || snapshots == null) return@addSnapshotListener
            stopCounts.clear()
            for (pDoc in snapshots) {
                @Suppress("UNCHECKED_CAST")
                val child = pDoc.get("child") as? Map<String, Any>
                child?.get("stop")?.toString()?.let { if (it.isNotEmpty()) stopCounts[it] = (stopCounts[it] ?: 0) + 1 }

                @Suppress("UNCHECKED_CAST")
                val childrenList = pDoc.get("children") as? List<Map<String, Any>>
                childrenList?.forEach { c ->
                    c["stop"]?.toString()?.let { if (it.isNotEmpty()) stopCounts[it] = (stopCounts[it] ?: 0) + 1 }
                }
            }
            updateList()
        }

        // Listen to stops
        stopsListener = db.collection("stops")
            .whereEqualTo("status", "active")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                allStopsList = snapshots.map { doc ->
                    StopAdmin(doc.id, doc.getString("name") ?: "N/A", doc.getDouble("latitude") ?: 0.0, doc.getDouble("longitude") ?: 0.0)
                }.toMutableList()
                updateList()
            }
    }

    override fun onDestroyView() {
        stopsListener?.remove()
        parentsListener?.remove()
        super.onDestroyView()
    }

    private fun updateList() {
        val filtered = allStopsList.filter { it.name.lowercase().contains(searchQuery) }
        val sorted = if (sortDirection == Query.Direction.ASCENDING) {
            filtered.sortedBy { it.name.lowercase() }
        } else {
            filtered.sortedByDescending { it.name.lowercase() }
        }

        totalCount = sorted.size
        val totalPages = ceil(totalCount.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
        if (currentPage > totalPages) currentPage = totalPages
        if (currentPage < 1) currentPage = 1

        val start = (currentPage - 1) * itemsPerPage
        val end = minOf(start + itemsPerPage, totalCount)
        val paged = if (start < totalCount) sorted.subList(start, end) else emptyList()

        paged.forEach { it.studentCount = stopCounts[it.id] ?: 0 }

        view?.findViewById<RecyclerView>(R.id.recyclerStops)?.adapter = StopAdapter(paged,
            onViewClick = { (requireActivity() as? AdminHome)?.showStopDetailInternal(it) },
            onEditClick = { (requireActivity() as? AdminHome)?.editStopDetailInternal(it) },
            onArchiveClick = { (requireActivity() as? AdminHome)?.archiveStopInternal(it) }
        )
        updatePaginationUI()
    }

    private fun setupUI(view: View) {
        view.findViewById<RecyclerView>(R.id.recyclerStops)?.layoutManager = LinearLayoutManager(requireContext())
        view.findViewById<TextView>(R.id.btnAddNewStop)?.setOnClickListener { AddStopDialog(requireContext(), db) {}.show() }
        view.findViewById<ImageButton>(R.id.btnSort)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.showSortOptions("stops") { dir ->
                sortDirection = dir
                updateList()
            }
        }
        view.findViewById<EditText>(R.id.searchStops)?.addTextChangedListener(object : TextWatcher {
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
        view.findViewById<TextView>(R.id.tvTotalPages)?.text = " of $maxPage"
        
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
