package com.example.buswatch.driver

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class Student(val name: String, val grade: String)

class StudentAdapter(private val students: List<Student>) : RecyclerView.Adapter<StudentAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvStudentName)
        val grade: TextView = view.findViewById(R.id.tvStudentGrade)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_student_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val student = students[position]
        holder.name.text = student.name
        holder.grade.text = student.grade
    }

    override fun getItemCount() = students.size
}