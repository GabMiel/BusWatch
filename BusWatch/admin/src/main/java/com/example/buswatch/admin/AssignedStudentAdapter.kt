package com.example.buswatch.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.buswatch.common.R as CommonR

data class AssignedStudent(
    val id: String,
    val name: String,
    val grade: String,
    val photoUrl: String = ""
)

class AssignedStudentAdapter(
    private val students: List<AssignedStudent>
) : RecyclerView.Adapter<AssignedStudentAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val photo: ImageView = view.findViewById(R.id.imgStudentPhoto)
        val name: TextView = view.findViewById(R.id.tvStudentName)
        val grade: TextView = view.findViewById(R.id.tvStudentGrade)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_stop_assigned_student, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val student = students[position]
        holder.name.text = student.name
        holder.grade.text = student.grade

        val placeholder = CommonR.drawable.ic_person_placeholder

        if (student.photoUrl.isNotEmpty()) {
            Glide.with(holder.photo.context)
                .load(student.photoUrl)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(placeholder)
                .error(placeholder)
                .into(holder.photo)
        } else {
            holder.photo.setImageResource(placeholder)
        }
    }

    override fun getItemCount() = students.size
}
