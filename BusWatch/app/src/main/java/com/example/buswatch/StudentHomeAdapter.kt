package com.example.buswatch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class StudentHome(
    val id: String, // index in children list or "primary"
    val name: String,
    val grade: String,
    val school: String,
    val status: String,
    val avatarResId: Int,
    val stop: String = "---",
    val rideOption: String = "Round Trip"
)

class StudentHomeAdapter(
    private val students: List<StudentHome>,
    private val onItemClick: (StudentHome) -> Unit
) : RecyclerView.Adapter<StudentHomeAdapter.ViewHolder>() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvStudentName)
        val grade: TextView = view.findViewById(R.id.tvStudentGrade)
        val school: TextView = view.findViewById(R.id.tvStudentSchool)
        val status: TextView = view.findViewById(R.id.tvStudentStatus)
        val stop: TextView = view.findViewById(R.id.tvStudentStop)
        val avatar: ImageView = view.findViewById(R.id.imgStudent)
        val spinner: Spinner = view.findViewById(R.id.spinnerRideOption)
        val card: View = view.findViewById(R.id.studentCard)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_student_home, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val student = students[position]
        holder.name.text = student.name
        holder.grade.text = student.grade
        holder.school.text = student.school
        holder.status.text = student.status
        holder.stop.text = student.stop
        holder.avatar.setImageResource(student.avatarResId)

        // Setup Spinner
        val options = holder.itemView.context.resources.getStringArray(CommonR.array.ride_options)
        val adapter = ArrayAdapter(
            holder.itemView.context,
            android.R.layout.simple_spinner_item,
            options
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        holder.spinner.adapter = adapter

        // Set current selection without triggering listener
        val initialPosition = options.indexOf(student.rideOption).takeIf { it != -1 } ?: 0
        holder.spinner.setSelection(initialPosition, false)

        holder.spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val selectedOption = options[pos]
                if (selectedOption != student.rideOption) {
                    updateRideOptionInFirebase(student, selectedOption, holder.itemView)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        holder.card.setOnClickListener { onItemClick(student) }
    }

    private fun updateRideOptionInFirebase(student: StudentHome, option: String, view: View) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("parents").document(uid)

        if (student.id == "primary") {
            docRef.update("child.rideOption", option)
                .addOnFailureListener {
                    Toast.makeText(view.context, "Failed to update ride option", Toast.LENGTH_SHORT).show()
                }
        } else {
            // It's an index in the children list
            val index = student.id.toIntOrNull() ?: return
            docRef.get().addOnSuccessListener { document ->
                @Suppress("UNCHECKED_CAST")
                val children = document.get("children") as? List<kotlin.collections.Map<String, Any>> ?: return@addOnSuccessListener
                val newList = children.toMutableList()
                if (index < newList.size) {
                    val updatedChild = newList[index].toMutableMap()
                    updatedChild["rideOption"] = option
                    newList[index] = updatedChild
                    docRef.update("children", newList)
                        .addOnFailureListener {
                            Toast.makeText(view.context, "Failed to update ride option", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        }
    }

    override fun getItemCount() = students.size
}
