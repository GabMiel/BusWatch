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
import com.example.buswatch.admin.AddBusDialog
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.BusAdmin
import com.example.buswatch.admin.BusAdapter
import com.example.buswatch.admin.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlin.math.ceil

class BusesFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private val itemsPerPage = 10
    private var currentPage = 1
    private var totalCount = 0
    private var sortDirection = Query.Direction.ASCENDING
    private var searchQuery = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_bus, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        fetchBuses()
    }

    private fun setupUI(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.recyclerBuses)
        rv?.layoutManager = LinearLayoutManager(requireContext())

        view.findViewById<TextView>(R.id.btnAddNewBus)?.setOnClickListener {
            AddBusDialog(requireActivity(), db) { fetchBuses() }.show()
        }

        view.findViewById<ImageButton>(R.id.btnSort)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.showSortOptions("buses") { dir ->
                sortDirection = dir
                currentPage = 1
                fetchBuses()
            }
        }

        val etSearch = view.findViewById<EditText>(R.id.searchBuses)
        etSearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.lowercase() ?: ""
                currentPage = 1
                fetchBuses()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        setupPagination(view)
    }

    private fun fetchBuses() {
        db.collection("buses").whereEqualTo("status", "Active").get().addOnSuccessListener { snapshots ->
            val allBuses = snapshots.map { 
                BusAdmin(it.id, it.getString("busNumber") ?: "N/A", it.getString("status") ?: "Active") 
            }.filter { 
                it.busNumber.lowercase().contains(searchQuery)
            }

            val sortedBuses = if (sortDirection == Query.Direction.ASCENDING) {
                allBuses.sortedBy { it.busNumber.lowercase() }
            } else {
                allBuses.sortedByDescending { it.busNumber.lowercase() }
            }

            totalCount = sortedBuses.size
            val totalPages = ceil(totalCount.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
            if (currentPage > totalPages) currentPage = totalPages
            if (currentPage < 1) currentPage = 1

            val start = (currentPage - 1) * itemsPerPage
            val end = minOf(start + itemsPerPage, totalCount)
            val pagedBuses = if (start < totalCount) sortedBuses.subList(start, end) else emptyList()

            view?.findViewById<RecyclerView>(R.id.recyclerBuses)?.adapter = BusAdapter(pagedBuses,
                onViewClick = { (requireActivity() as? AdminHome)?.showBusDetail(it) },
                onEditClick = { (requireActivity() as? AdminHome)?.editBusDetail(it) },
                onArchiveClick = { (requireActivity() as? AdminHome)?.archiveBus(it) { fetchBuses() } }
            )
            updatePaginationUI()
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
        fetchBuses()
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
