package com.example.buswatch.driver

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.buswatch.common.R as CommonR
import java.util.Locale

class StudentAdapter(
    private var students: List<Student>,
    private var currentTab: String = "Morning",
    private val onPickUpClick: (Student) -> Unit,
    private val onDropOffClick: (Student) -> Unit,
    private val onStudentClick: (Student) -> Unit
) : RecyclerView.Adapter<StudentAdapter.StudentViewHolder>() {

    class StudentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(if (view.id == R.id.studentRowContainer) R.id.tvStudentName else R.id.tvPickupName)
        val grade: TextView = view.findViewById(R.id.tvStudentGrade)
        val stop: TextView? = view.findViewById(R.id.tvStopLocation)
        val photo: ImageView? = view.findViewById(if (view.id == R.id.studentRowContainer) R.id.imgStudentAvatar else R.id.imgPickupStudent)
        val btnAction: TextView? = view.findViewById(R.id.btnPickUp)
        val isPickupList: Boolean = view.id != R.id.studentRowContainer
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val layout = if (parent.id == R.id.recyclerPickup) R.layout.item_pickup_row else R.layout.item_student_row
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = students[position]
        holder.name.text = student.name
        
        if (student.distanceMeters != null && holder.isPickupList) {
            holder.grade.text = String.format(Locale.getDefault(), "%d meters away", student.distanceMeters)
        } else {
            holder.grade.text = student.grade
        }

        holder.stop?.let { 
            it.text = it.context.getString(CommonR.string.stop_format, student.stopName)
            it.visibility = if (holder.isPickupList) View.VISIBLE else View.GONE
        }

        holder.photo?.let { img ->
            Glide.with(img.context)
                .load(student.photoUrl)
                .circleCrop()
                .placeholder(CommonR.drawable.ic_person_placeholder)
                .into(img)
        }

        val eligibleForTab = when (currentTab) {
            "Morning" -> student.rideOption.contains("Morning") || student.rideOption.contains("Round Trip")
            "Afternoon" -> student.rideOption.contains("Afternoon") || student.rideOption.contains("Round Trip")
            else -> student.rideOption != "Not Riding"
        }

        val isFinished = when (currentTab) {
            "Morning" -> student.status == "At School"
            "Afternoon" -> student.status == "At Home"
            else -> false
        }

        val isActive = eligibleForTab && !isFinished

        if (isActive) {
            holder.itemView.setBackgroundResource(CommonR.drawable.bg_student_row_active)
            holder.name.alpha = 1.0f
            holder.grade.alpha = 1.0f
            holder.stop?.alpha = 1.0f
        } else {
            holder.itemView.setBackgroundResource(CommonR.drawable.bg_student_row)
            holder.name.alpha = 0.5f
            holder.grade.alpha = 0.5f
            holder.stop?.alpha = 0.5f
        }

        holder.itemView.setOnClickListener {
            onStudentClick(student)
        }

        holder.btnAction?.let { btn ->
            if (!isActive || !holder.isPickupList) {
                btn.visibility = View.GONE
            } else {
                when (student.status) {
                    "Heading to Stop", "At Home" -> { 
                        btn.visibility = View.VISIBLE
                        btn.text = btn.context.getString(CommonR.string.pick_up)
                        btn.setBackgroundResource(CommonR.drawable.bg_pickup_btn)
                        btn.setOnClickListener { onPickUpClick(student) }
                    }
                    "On Board", "At School" -> { 
                        if (currentTab == "Afternoon" && student.status == "At School") {
                            btn.visibility = View.VISIBLE
                            btn.text = btn.context.getString(CommonR.string.pick_up)
                            btn.setBackgroundResource(CommonR.drawable.bg_pickup_btn)
                            btn.setOnClickListener { onPickUpClick(student) }
                        } else if (student.status == "On Board") {
                            btn.visibility = View.VISIBLE
                            btn.text = btn.context.getString(CommonR.string.drop_off)
                            btn.setBackgroundResource(CommonR.drawable.bg_dropoff_btn)
                            btn.setOnClickListener { onDropOffClick(student) }
                        } else {
                            btn.visibility = View.GONE
                        }
                    }
                    else -> {
                        btn.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun getItemCount() = students.size

    fun updateStudents(newStudents: List<Student>, tab: String) {
        val oldStudents = this.students
        val oldTab = this.currentTab
        
        this.students = newStudents
        this.currentTab = tab

        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldStudents.size
            override fun getNewListSize(): Int = newStudents.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldStudents[oldItemPosition].id == newStudents[newItemPosition].id
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldTab == tab && oldStudents[oldItemPosition] == newStudents[newItemPosition]
            }
        })
        diffResult.dispatchUpdatesTo(this)
    }
}
