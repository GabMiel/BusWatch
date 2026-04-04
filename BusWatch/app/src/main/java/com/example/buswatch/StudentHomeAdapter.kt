package com.example.buswatch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.common.R as CommonR

data class StudentHome(
    val name: String,
    val grade: String,
    val school: String,
    val status: String,
    val avatarResId: Int
)

class StudentHomeAdapter(
    private val students: List<StudentHome>,
    private val onItemClick: (StudentHome) -> Unit
) : RecyclerView.Adapter<StudentHomeAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvStudentName)
        val grade: TextView = view.findViewById(R.id.tvStudentGrade)
        val school: TextView = view.findViewById(R.id.tvStudentSchool)
        val status: TextView = view.findViewById(R.id.tvStudentStatus)
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
        holder.avatar.setImageResource(student.avatarResId)

        // Setup Spinner
        val adapter = ArrayAdapter.createFromResource(
            holder.itemView.context,
            CommonR.array.ride_options,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        holder.spinner.adapter = adapter

        holder.card.setOnClickListener { onItemClick(student) }
    }

    override fun getItemCount() = students.size
}
