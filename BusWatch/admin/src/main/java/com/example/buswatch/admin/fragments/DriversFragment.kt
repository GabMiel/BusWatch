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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.admin.AddDriverDialog
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.DriverAdapter
import com.example.buswatch.admin.R
import com.example.buswatch.admin.UserAdmin
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlin.math.ceil

class DriversFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private val itemsPerPage = 10
    private var currentPage = 1
    private var totalCount = 0
    private var sortDirection = Query.Direction.ASCENDING
    private var searchQuery = ""
    
    private var currentAddDriverDialog: AddDriverDialog? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            currentAddDriverDialog?.handleImageResult(result.data?.data)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_driver, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        fetchPage()
    }

    private fun setupUI(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.recyclerUsers)
        rv?.layoutManager = LinearLayoutManager(requireContext())

        view.findViewById<TextView>(R.id.tabParents)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.loadUsers()
        }

        view.findViewById<TextView>(R.id.tabConductors)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.loadConductors()
        }

        view.findViewById<TextView>(R.id.btnAddNewDriver)?.setOnClickListener {
            currentAddDriverDialog = AddDriverDialog(
                requireActivity() as AppCompatActivity, 
                db, 
                pickImageLauncher
            ) { fetchPage() }
            currentAddDriverDialog?.show()
        }

        view.findViewById<ImageButton>(R.id.btnSort)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.showSortOptions("drivers") { dir ->
                sortDirection = dir
                currentPage = 1
                fetchPage()
            }
        }

        val etSearch = view.findViewById<EditText>(R.id.searchUsers)
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
        db.collection("drivers")
            .whereEqualTo("status", "active")
            .get()
            .addOnSuccessListener { snapshots ->
                val allDrivers = snapshots.map { doc ->
                    UserAdmin(
                        doc.id, 
                        "${doc.getString("firstName")} ${doc.getString("lastName")}", 
                        "Driver", 
                        status = "active",
                        avatarUrl = doc.getString("driverAvatar") ?: ""
                    )
                }.filter { 
                    it.name.lowercase().contains(searchQuery)
                }

                val sortedDrivers = if (sortDirection == Query.Direction.ASCENDING) {
                    allDrivers.sortedBy { it.name.lowercase() }
                } else {
                    allDrivers.sortedByDescending { it.name.lowercase() }
                }
                
                totalCount = sortedDrivers.size
                val totalPages = ceil(totalCount.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
                if (currentPage > totalPages) currentPage = totalPages
                if (currentPage < 1) currentPage = 1
                
                val start = (currentPage - 1) * itemsPerPage
                val end = minOf(start + itemsPerPage, totalCount)
                
                val pagedDrivers = if (start < totalCount) sortedDrivers.subList(start, end) else emptyList()

                view?.findViewById<RecyclerView>(R.id.recyclerUsers)?.adapter = DriverAdapter(pagedDrivers.toMutableList(), 
                    { (requireActivity() as? AdminHome)?.showDriverDetail(it) { fetchPage() } }, 
                    { (requireActivity() as? AdminHome)?.editDriverDetail(it) }, 
                    { user, _ -> (requireActivity() as? AdminHome)?.archiveUser(user) { fetchPage() } }
                )
                updatePaginationUI()
            }
            .addOnFailureListener { e ->
                if (isAdded) {
                    Toast.makeText(requireContext(), "Error fetching drivers: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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
        val totalPages = ceil(totalCount.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
        
        view.findViewById<EditText>(R.id.etCurrentPage)?.setText(currentPage.toString())
        view.findViewById<TextView>(R.id.tvTotalPages)?.text = getString(CommonR.string.page_of_format, totalPages)
        
        val btnPrev = view.findViewById<View>(R.id.btnPrevPage)
        val btnFirst = view.findViewById<View>(R.id.btnFirstPage)
        val btnNext = view.findViewById<View>(R.id.btnNextPage)
        val btnLast = view.findViewById<View>(R.id.btnLastPage)

        val canPrev = currentPage > 1
        val canNext = currentPage < totalPages

        btnPrev?.isEnabled = canPrev
        btnFirst?.isEnabled = canPrev
        btnNext?.isEnabled = canNext
        btnLast?.isEnabled = canNext

        // Visual feedback for disabled state
        btnPrev?.alpha = if (canPrev) 1.0f else 0.3f
        btnFirst?.alpha = if (canPrev) 1.0f else 0.3f
        btnNext?.alpha = if (canNext) 1.0f else 0.3f
        btnLast?.alpha = if (canNext) 1.0f else 0.3f
    }
}
