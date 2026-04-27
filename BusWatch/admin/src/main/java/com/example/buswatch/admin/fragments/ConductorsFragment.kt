package com.example.buswatch.admin.fragments

import android.content.Intent
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.admin.AddConductorDialog
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.admin.UserAdapter
import com.example.buswatch.admin.UserAdmin
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlin.math.ceil

class ConductorsFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private val itemsPerPage = 10
    private var currentPage = 1
    private var totalCount = 0
    private var sortDirection = Query.Direction.ASCENDING
    private var searchQuery = ""
    
    private var currentAddConductorDialog: AddConductorDialog? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            currentAddConductorDialog?.handleImageResult(result.data?.data)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_conductor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        fetchCount()
        fetchPage()
    }

    private fun setupUI(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.recyclerUsers)
        rv?.layoutManager = LinearLayoutManager(requireContext())

        view.findViewById<TextView>(R.id.tabParents)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.loadUsers()
        }

        view.findViewById<TextView>(R.id.tabDrivers)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.loadDrivers()
        }

        view.findViewById<TextView>(R.id.btnAddNewConductor)?.setOnClickListener {
            val activity = requireActivity() as? AppCompatActivity
            if (activity != null) {
                currentAddConductorDialog = AddConductorDialog(activity, db, pickImageLauncher) { fetchPage() }
                currentAddConductorDialog?.show()
            }
        }

        view.findViewById<ImageButton>(R.id.btnSort)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.showSortOptions("conductors") { dir ->
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

        // Pagination Controls
        view.findViewById<TextView>(R.id.btnFirstPage)?.setOnClickListener {
            if (currentPage != 1) {
                currentPage = 1
                fetchPage()
            }
        }

        view.findViewById<TextView>(R.id.btnPrevPage)?.setOnClickListener {
            if (currentPage > 1) {
                currentPage--
                fetchPage()
            }
        }

        view.findViewById<TextView>(R.id.btnNextPage)?.setOnClickListener {
            val totalPages = ceil(totalCount.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
            if (currentPage < totalPages) {
                currentPage++
                fetchPage()
            }
        }

        view.findViewById<TextView>(R.id.btnLastPage)?.setOnClickListener {
            val totalPages = ceil(totalCount.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
            if (currentPage != totalPages && totalPages > 0) {
                currentPage = totalPages
                fetchPage()
            }
        }
    }

    private fun fetchCount() {
        db.collection("conductors").whereEqualTo("status", "active")
            .get()
            .addOnSuccessListener { 
                totalCount = it.size()
                updatePaginationUI()
            }
    }

    private fun fetchPage() {
        // Removed .orderBy() from Firestore query to avoid FAILED_PRECONDITION
        db.collection("conductors")
            .whereEqualTo("status", "active")
            .get()
            .addOnSuccessListener { snapshots ->
                val allConductors = snapshots.map { doc ->
                    UserAdmin(
                        doc.id, 
                        "${doc.getString("firstName")} ${doc.getString("lastName")}", 
                        "Conductor", 
                        status = "active",
                        avatarUrl = doc.getString("conductorAvatar") ?: ""
                    )
                }.filter { 
                    it.name.lowercase().contains(searchQuery)
                }

                // Perform in-memory sorting
                val sortedConductors = if (sortDirection == Query.Direction.ASCENDING) {
                    allConductors.sortedBy { it.name.lowercase() }
                } else {
                    allConductors.sortedByDescending { it.name.lowercase() }
                }
                
                totalCount = sortedConductors.size
                val totalPages = ceil(totalCount.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
                if (currentPage > totalPages) currentPage = totalPages
                if (currentPage < 1) currentPage = 1
                
                val start = (currentPage - 1) * itemsPerPage
                val end = minOf(start + itemsPerPage, totalCount)
                
                val pagedConductors = if (start < totalCount) sortedConductors.subList(start, end) else emptyList()

                view?.findViewById<RecyclerView>(R.id.recyclerUsers)?.adapter = UserAdapter(pagedConductors.toMutableList(), 
                    { (requireActivity() as? AdminHome)?.showConductorDetail(it) { fetchPage() } }, 
                    { (requireActivity() as? AdminHome)?.editConductorDetail(it) }, 
                    { user, _ -> (requireActivity() as? AdminHome)?.archiveUser(user) }
                )
                updatePaginationUI()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error fetching conductors: ${e.message}", Toast.LENGTH_SHORT).show()
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
}
