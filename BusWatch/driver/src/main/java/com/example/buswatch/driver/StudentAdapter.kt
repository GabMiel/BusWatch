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
    private val isPickupLayout: Boolean = false,
    private val onPickUpClick: (Student) -> Unit,
    private val onDropOffClick: (Student) -> Unit,
    private val onStudentClick: (Student) -> Unit
) : RecyclerView.Adapter<StudentAdapter.StudentViewHolder>() {

    companion object {
        private const val TYPE_STUDENT_ROW = 0
        private const val TYPE_PICKUP_ROW = 1
    }

    class StudentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById<TextView>(R.id.tvPickupName) ?: view.findViewById(R.id.tvStudentName)
        val grade: TextView = view.findViewById(R.id.tvStudentGrade)
        val stop: TextView? = view.findViewById(R.id.tvStopLocation)
        val photo: ImageView? = view.findViewById<ImageView>(R.id.imgPickupStudent) ?: view.findViewById(R.id.imgStudentAvatar)
        val btnAction: TextView? = view.findViewById(R.id.btnPickUp)
    }

    override fun getItemViewType(position: Int): Int {
        if (isPickupLayout) return TYPE_PICKUP_ROW
        return TYPE_STUDENT_ROW
    }

    /**
     * Requirement update:
     * - Active (Green) -> Students currently in transit (Heading to stop, On board, or Heading home, Riding).
     * - Inactive (Orange) -> Students who are idle (At Home, At School) or finished their trip.
     */
    private fun isStudentActive(student: Student): Boolean {
        if (!isStudentEligible(student)) return false
        val status = student.status.lowercase().trim()
        
        return when (currentTab) {
            "Morning" -> status in listOf("heading to stop", "on board")
            "Afternoon" -> status in listOf("heading home", "riding")
            else -> false
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val layout = if (isPickupLayout) R.layout.item_pickup_row else R.layout.item_student_row
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = students[position]
        holder.name.text = student.name

        val active = isStudentActive(student)
        val eligible = isStudentEligible(student)
        val status = student.status.lowercase().trim()

        val distanceSuffix = if (student.distanceMeters != null) {
            " • ${student.distanceMeters}m away"
        } else ""
        holder.grade.text = "${student.grade}$distanceSuffix"

        holder.stop?.let { 
            it.text = it.context.getString(CommonR.string.stop_format, student.stopName)
            it.visibility = View.VISIBLE
        }

        holder.photo?.let { img ->
            Glide.with(img.context)
                .load(student.photoUrl)
                .circleCrop()
                .placeholder(CommonR.drawable.ic_person_placeholder)
                .into(img)
        }

        // Apply background: Active in transit = Green, Idle/Arrived = Orange
        if (active) {
            holder.itemView.setBackgroundResource(CommonR.drawable.bg_student_row_active)
        } else {
            holder.itemView.setBackgroundResource(CommonR.drawable.bg_student_row)
        }

        holder.itemView.alpha = 1.0f
        holder.itemView.setOnClickListener { onStudentClick(student) }
        holder.itemView.isClickable = true

        holder.btnAction?.let { btn ->
            // Update: Only show action button if the student is ELIGIBLE, ACTIVE, and NOT YET ARRIVED.
            if (!isPickupLayout || !eligible || !active) {
                btn.visibility = View.GONE
            } else {
                when (currentTab) {
                    "Morning" -> {
                        when (status) {
                            "heading to stop" -> {
                                btn.visibility = View.VISIBLE
                                btn.text = btn.context.getString(CommonR.string.pick_up)
                                btn.setBackgroundResource(CommonR.drawable.bg_pickup_btn)
                                btn.setOnClickListener { onPickUpClick(student) }
                            }
                            "on board" -> {
                                btn.visibility = View.VISIBLE
                                btn.text = btn.context.getString(CommonR.string.drop_off)
                                btn.setBackgroundResource(CommonR.drawable.bg_dropoff_btn)
                                btn.setOnClickListener { onDropOffClick(student) }
                            }
                            else -> btn.visibility = View.GONE
                        }
                    }
                    "Afternoon" -> {
                        when (status) {
                            "heading home" -> {
                                btn.visibility = View.VISIBLE
                                btn.text = btn.context.getString(CommonR.string.pick_up)
                                btn.setBackgroundResource(CommonR.drawable.bg_pickup_btn)
                                btn.setOnClickListener { onPickUpClick(student) }
                            }
                            "riding" -> {
                                btn.visibility = View.VISIBLE
                                btn.text = btn.context.getString(CommonR.string.drop_off)
                                btn.setBackgroundResource(CommonR.drawable.bg_dropoff_btn)
                                btn.setOnClickListener { onDropOffClick(student) }
                            }
                            else -> btn.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }
    
    private fun isStudentEligible(student: Student): Boolean {
        val ride = student.rideOption.lowercase()
        if (ride.contains("not riding")) return false
        return when (currentTab) {
            "Morning" -> ride.contains("morning") || ride.contains("round trip")
            "Afternoon" -> ride.contains("afternoon") || ride.contains("round trip")
            else -> false
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
