package com.example.buswatch.driver

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.common.R as CommonR

data class Student(
    val id: String,
    val name: String, 
    val grade: String,
    val status: String = "At Home"
)

class StudentAdapter(
    private val students: List<Student>,
    private val onPickUpClick: (Student) -> Unit,
    private val onDropOffClick: (Student) -> Unit
) : RecyclerView.Adapter<StudentAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvStudentName)
        val grade: TextView = view.findViewById(R.id.tvStudentGrade)
        val container: View = view.findViewById(R.id.studentRowContainer)
        val btnAction: TextView = view.findViewById(R.id.btnPickUp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Shared layout for both list and live tracking pickup
        val layout = if (parent.id == R.id.recyclerPickup) R.layout.item_pickup_row else R.layout.item_student_row
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val student = students[position]
        holder.name.text = student.name
        holder.grade.text = student.grade

        // Status Logic: At Home -> Heading to Stop -> On Board -> At School
        when (student.status) {
            "Heading to Stop" -> {
                holder.container.setBackgroundResource(CommonR.drawable.bg_student_row_picked) // Yellow/Light Green
                holder.btnAction.visibility = View.VISIBLE
                holder.btnAction.text = holder.itemView.context.getString(CommonR.string.pick_up)
                holder.btnAction.setOnClickListener { onPickUpClick(student) }
            }
            "On Board" -> {
                holder.container.setBackgroundResource(CommonR.drawable.bg_student_row_picked)
                holder.btnAction.visibility = View.VISIBLE
                holder.btnAction.text = holder.itemView.context.getString(CommonR.string.drop_off)
                holder.btnAction.setOnClickListener { onDropOffClick(student) }
            }
            "At School" -> {
                holder.container.setBackgroundResource(android.R.color.white)
                holder.btnAction.visibility = View.GONE
            }
            else -> {
                holder.container.setBackgroundResource(android.R.color.white)
                holder.btnAction.visibility = View.GONE
            }
        }
    }

    override fun getItemCount() = students.size
}
