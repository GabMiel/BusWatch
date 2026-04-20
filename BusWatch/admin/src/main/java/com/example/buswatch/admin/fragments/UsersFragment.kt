package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.admin.UserAdmin
import com.example.buswatch.admin.UserAdapter
import com.example.buswatch.admin.StopAdmin
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class UsersFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private val itemsPerPage = 20
    private var currentPage = 1
    private var totalCount = 0
    private var sortDirection = Query.Direction.ASCENDING
    private var selectedStopId: String? = null
    private var selectedStopName: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_users, container, false)
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

        view.findViewById<TextView>(R.id.tabDrivers)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.loadDrivers()
        }

        view.findViewById<TextView>(R.id.tabConductors)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.loadConductors()
        }

        view.findViewById<ImageButton>(R.id.btnSort)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.showSortOptions("parents") { dir ->
                sortDirection = dir
                currentPage = 1
                fetchPage()
            }
        }

        view.findViewById<ImageButton>(R.id.btnFilterByStop)?.setOnClickListener {
            showStopFilterDialog()
        }

        view.findViewById<TextView>(R.id.btnClearFilter)?.setOnClickListener {
            selectedStopId = null
            selectedStopName = null
            currentPage = 1
            updateFilterUI()
            fetchCount()
            fetchPage()
        }
    }

    private fun showStopFilterDialog() {
        db.collection("stops").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            val stops = snapshots.map { doc ->
                StopAdmin(doc.id, doc.getString("name") ?: "N/A", 0.0, 0.0)
            }

            if (stops.isEmpty()) {
                Toast.makeText(requireContext(), "No active stops found", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            val stopNames = stops.map { it.name }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Filter by Stop")
                .setItems(stopNames) { _, which ->
                    selectedStopId = stops[which].id
                    selectedStopName = stops[which].name
                    currentPage = 1
                    updateFilterUI()
                    fetchCount()
                    fetchPage()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun updateFilterUI() {
        val view = view ?: return
        val layout = view.findViewById<LinearLayout>(R.id.layoutFilterStatus)
        val info = view.findViewById<TextView>(R.id.tvFilterInfo)

        if (selectedStopId != null) {
            layout.visibility = View.VISIBLE
            info.text = "Filtering by Stop: $selectedStopName"
        } else {
            layout.visibility = View.GONE
        }
    }

    private fun fetchCount() {
        var query: Query = db.collection("parents").whereEqualTo("status", "approved")
        
        if (selectedStopId != null) {
            query = query.whereEqualTo("child.stop", selectedStopId)
        }

        query.get().addOnSuccessListener { 
            totalCount = it.size()
        }
    }

    private fun fetchPage() {
        var query: Query = db.collection("parents").whereEqualTo("status", "approved")
        
        if (selectedStopId != null) {
            query = query.whereEqualTo("child.stop", selectedStopId)
        }

        query.orderBy("lastName", sortDirection)
            .limit(itemsPerPage.toLong())
            .get()
            .addOnSuccessListener { snapshots ->
                val users = snapshots.map { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val profile = doc.get("profile") as? Map<String, Any>
                    UserAdmin(doc.id, "${profile?.get("firstName")} ${profile?.get("lastName")}", "Parent", status = "approved")
                }.toMutableList()
                view?.findViewById<RecyclerView>(R.id.recyclerUsers)?.adapter = UserAdapter(users, 
                    { (requireActivity() as? AdminHome)?.showParentDetail(it) { fetchPage() } }, 
                    { (requireActivity() as? AdminHome)?.editParentDetail(it) }, 
                    { user, _ -> (requireActivity() as? AdminHome)?.archiveUser(user) }
                )
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
