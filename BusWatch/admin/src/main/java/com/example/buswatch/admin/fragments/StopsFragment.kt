package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.admin.AddStopDialog
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.admin.StopAdmin
import com.example.buswatch.admin.StopAdapter
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class StopsFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private val itemsPerPage = 20
    private var currentPage = 1
    private var totalCount = 0
    private var sortDirection = Query.Direction.ASCENDING

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
        rv?.layoutManager = GridLayoutManager(requireContext(), resources.getInteger(R.integer.stops_grid_span))

        view.findViewById<TextView>(R.id.btnAddNewStop)?.setOnClickListener {
            AddStopDialog(requireContext(), db) { fetchPage() }.show()
        }

        view.findViewById<ImageButton>(R.id.btnSort)?.setOnClickListener {
            showSortOptions()
        }

        setupPagination(view)
    }

    private fun showSortOptions() {
        val options = arrayOf("A-Z", "Z-A")
        AlertDialog.Builder(requireContext()).setTitle("Sort Stops").setItems(options) { _, which ->
            sortDirection = if (which == 0) Query.Direction.ASCENDING else Query.Direction.DESCENDING
            currentPage = 1
            fetchPage()
        }.show()
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
            .orderBy("name", sortDirection)
            .limit(itemsPerPage.toLong())
            .get()
            .addOnSuccessListener { snapshots ->
                val stops = snapshots.map { doc ->
                    StopAdmin(doc.id, doc.getString("name") ?: "N/A", doc.getDouble("latitude") ?: 0.0, doc.getDouble("longitude") ?: 0.0)
                }
                
                // Fetch student counts for each stop
                val countTasks = stops.map { stop ->
                    db.collection("parents")
                        .whereEqualTo("child.stop", stop.id)
                        .get()
                        .continueWith { task ->
                            stop.studentCount = task.result?.size() ?: 0
                            stop
                        }
                }

                Tasks.whenAllSuccess<StopAdmin>(countTasks).addOnSuccessListener { updatedStops ->
                    view?.findViewById<RecyclerView>(R.id.recyclerStops)?.adapter = StopAdapter(updatedStops,
                        onViewClick = { (requireActivity() as? AdminHome)?.showStopDetailInternal(it) },
                        onEditClick = { (requireActivity() as? AdminHome)?.editStopDetailInternal(it) },
                        onArchiveClick = { archiveStop(it) }
                    )
                }
                updatePaginationUI()
            }
    }

    private fun archiveStop(stop: StopAdmin) {
        db.collection("stops").document(stop.id).update("status", "archived").addOnSuccessListener { fetchPage() }
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
            handleJumpToPage(Math.ceil(totalCount.toDouble() / itemsPerPage).toInt())
        }
    }

    private fun handleJumpToPage(page: Int) {
        val maxPage = Math.ceil(totalCount.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
        currentPage = page.coerceIn(1, maxPage)
        fetchPage()
    }

    private fun updatePaginationUI() {
        val view = view ?: return
        val maxPage = Math.ceil(totalCount.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
        view.findViewById<EditText>(R.id.etCurrentPage)?.setText(currentPage.toString())
        view.findViewById<TextView>(R.id.tvTotalPages)?.text = " of $maxPage"
    }
}
