package com.example.buswatch.driver

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.buswatch.common.R as CommonR

class StudentAdapter(
    private val students: List<Student>,
    private val currentTab: String = "Morning",
    private val onPickUpClick: (Student) -> Unit,
    private val onDropOffClick: (Student) -> Unit
) : RecyclerView.Adapter<StudentAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Find views based on which layout is inflated
        val name: TextView = view.findViewById(if (view.id == R.id.studentRowContainer) R.id.tvStudentName else R.id.tvPickupName)
        val grade: TextView = view.findViewById(R.id.tvStudentGrade)
        val photo: ImageView? = view.findViewById(if (view.id == R.id.studentRowContainer) R.id.imgStudentAvatar else R.id.imgPickupStudent)
        val container: View = view.findViewById(if (view.id == R.id.studentRowContainer) R.id.studentRowContainer else R.id.studentRowContainer) ?: view
        val btnAction: TextView? = view.findViewById(R.id.btnPickUp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = if (parent.id == R.id.recyclerPickup) R.layout.item_pickup_row else R.layout.item_student_row
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val student = students[position]
        holder.name.text = student.name
        holder.grade.text = student.grade

        holder.photo?.let { img ->
            Glide.with(img.context)
                .load(student.photoUrl)
                .circleCrop()
                .placeholder(CommonR.drawable.ic_person_placeholder)
                .into(img)
        }

        // Logic to determine if student is "Active" for this trip
        val isRiding = when (currentTab) {
            "Morning" -> student.rideOption == "Morning Trip"
            "Afternoon" -> student.rideOption == "Afternoon Trip"
            else -> student.rideOption != "Not Riding"
        }

        // Change card background based on ride option
        if (isRiding) {
            // Highlight for students who are riding
            holder.itemView.setBackgroundResource(CommonR.drawable.bg_student_row_active)
            holder.name.alpha = 1.0f
            holder.grade.alpha = 1.0f
        } else {
            // Dim or white background for students not riding this trip
            holder.itemView.setBackgroundResource(android.R.color.white)
            holder.name.alpha = 0.5f // Dim the text to show they aren't expected
            holder.grade.alpha = 0.5f
        }

        // Action button logic (typically for live tracking roster)
        holder.btnAction?.let { btn ->
            if (!isRiding) {
                btn.visibility = View.GONE
            } else {
                when (student.status) {
                    "Heading to Stop" -> {
                        btn.visibility = View.VISIBLE
                        btn.text = btn.context.getString(CommonR.string.pick_up)
                        btn.setOnClickListener { onPickUpClick(student) }
                    }
                    "On Board" -> {
                        btn.visibility = View.VISIBLE
                        btn.text = btn.context.getString(CommonR.string.drop_off)
                        btn.setOnClickListener { onDropOffClick(student) }
                    }
                    else -> {
                        btn.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun getItemCount() = students.size
}
