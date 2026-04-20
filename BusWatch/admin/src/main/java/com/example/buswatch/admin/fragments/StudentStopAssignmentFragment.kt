package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.admin.R
import com.example.buswatch.admin.UserAdmin
import com.example.buswatch.admin.UserAdapter
import com.google.firebase.firestore.FirebaseFirestore

class StudentStopAssignmentFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private val students = mutableListOf<UserAdmin>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_student_stop_assignment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        fetchStudents()
    }

    private fun setupUI(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.recyclerStudentAssignments)
        rv?.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun fetchStudents() {
        // Fetch parents with status approved
        db.collection("parents").whereEqualTo("status", "approved").get().addOnSuccessListener { snapshots ->
            students.clear()
            for (doc in snapshots) {
                val child = doc.get("child") as? Map<String, Any>
                val fName = child?.get("firstName") as? String ?: ""
                val lName = child?.get("lastName") as? String ?: ""
                val stopId = child?.get("stop") as? String ?: ""
                
                // We'll use the status field to show the current stop name later
                students.add(UserAdmin(doc.id, "$fName $lName", "Student", status = if (stopId.isEmpty()) "Not Assigned" else "Assigned"))
            }
            updateList()
        }
    }

    private fun updateList() {
        view?.findViewById<RecyclerView>(R.id.recyclerStudentAssignments)?.adapter = UserAdapter(students,
            onViewClick = { /* View student details */ },
            onEditClick = { /* Not needed here */ },
            onArchiveClick = { user, _ -> showStopPicker(user) }
        )
    }

    private fun showStopPicker(user: UserAdmin) {
        db.collection("stops").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            val stopNames = snapshots.map { it.getString("name") ?: "Unknown" }.toTypedArray()
            val stopIds = snapshots.map { it.id }

            AlertDialog.Builder(requireContext())
                .setTitle("Assign Stop to ${user.name}")
                .setItems(stopNames) { _, which ->
                    assignStop(user.id, stopIds[which], stopNames[which])
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun assignStop(userId: String, stopId: String, stopName: String) {
        db.collection("parents").document(userId).update("child.stop", stopId).addOnSuccessListener {
            Toast.makeText(requireContext(), "Assigned to $stopName", Toast.LENGTH_SHORT).show()
            fetchStudents()
        }
    }
}
