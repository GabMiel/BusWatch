package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
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

class ConductorsFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private val itemsPerPage = 20
    private var currentPage = 1
    private var totalCount = 0
    private var sortDirection = Query.Direction.ASCENDING

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
                AddConductorDialog(activity, db) { fetchPage() }.show()
            }
        }

        view.findViewById<ImageButton>(R.id.btnSort)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.showSortOptions("conductors") { dir ->
                sortDirection = dir
                currentPage = 1
                fetchPage()
            }
        }
    }

    private fun fetchCount() {
        db.collection("conductors").whereEqualTo("status", "active").get().addOnSuccessListener { 
            totalCount = it.size()
        }
    }

    private fun fetchPage() {
        db.collection("conductors")
            .whereEqualTo("status", "active")
            .orderBy("lastName", sortDirection)
            .limit(itemsPerPage.toLong())
            .get()
            .addOnSuccessListener { snapshots ->
                val conductors = snapshots.map { UserAdmin(it.id, "${it.getString("firstName")} ${it.getString("lastName")}", "Conductor", status = "active") }.toMutableList()
                view?.findViewById<RecyclerView>(R.id.recyclerUsers)?.adapter = UserAdapter(conductors, 
                    { (requireActivity() as? AdminHome)?.showConductorDetail(it) { fetchPage() } }, 
                    { (requireActivity() as? AdminHome)?.editConductorDetail(it) }, 
                    { user, _ -> (requireActivity() as? AdminHome)?.archiveUser(user) }
                )
            }
    }
}
