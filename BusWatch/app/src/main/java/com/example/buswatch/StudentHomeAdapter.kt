package com.example.buswatch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.collections.Map as KMap

data class StudentHome(
    val id: String,
    val name: String,
    val grade: String,
    val school: String,
    val status: String,
    val avatarResId: Int,
    val avatarUrl: String? = null,
    val stop: String = "---",
    val rideOption: String = "Round Trip",
    val schedule: String = "Schedule: Not Set"
)

class StudentHomeAdapter(
    private val students: List<StudentHome>,
    @Suppress("unused") private val isTracking: Boolean,
    private val onItemClick: (StudentHome) -> Unit
) : RecyclerView.Adapter<StudentHomeAdapter.ViewHolder>() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvStudentName)
        val grade: TextView = view.findViewById(R.id.tvStudentGrade)
        val school: TextView = view.findViewById(R.id.tvStudentSchool)
        val stop: TextView = view.findViewById(R.id.tvStudentStop)
        val status: TextView = view.findViewById(R.id.tvStudentStatus)
        val avatar: ImageView = view.findViewById(R.id.imgStudent)
        val spinner: Spinner = view.findViewById(R.id.spinnerRideOption)
        val card: View = view.findViewById(R.id.studentCard)
        val ivArrow: ImageView = view.findViewById(R.id.ivArrowIndicator)
        val schedule: TextView = view.findViewById(R.id.tvBusSchedule)
    }

    fun getStudents(): List<StudentHome> = students

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_student_home, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val student = students[position]
        holder.name.text = student.name
        holder.grade.text = student.grade
        holder.school.text = student.school
        holder.stop.text = holder.itemView.context.getString(CommonR.string.stop_not_assigned_format, student.stop)
        holder.status.text = student.status
        holder.schedule.text = student.schedule
        
        if (!student.avatarUrl.isNullOrEmpty()) {
            Glide.with(holder.avatar.context)
                .load(student.avatarUrl)
                .placeholder(student.avatarResId)
                .error(student.avatarResId)
                .circleCrop()
                .into(holder.avatar)
        } else {
            holder.avatar.setImageResource(student.avatarResId)
        }

        val isActive = student.status == "Heading to Stop" || student.status == "On Board"
        
        if (isActive) {
            holder.card.setBackgroundResource(CommonR.drawable.bg_card_light_green)
            holder.card.setOnClickListener { onItemClick(student) }
            holder.card.isClickable = true
            holder.card.isEnabled = true
            holder.ivArrow.visibility = View.VISIBLE
            holder.spinner.visibility = View.GONE
        } else {
            holder.card.setBackgroundResource(CommonR.drawable.bg_card_black_border)
            holder.card.setOnClickListener(null)
            holder.card.isClickable = false
            holder.card.isEnabled = false
            holder.ivArrow.visibility = View.GONE
            holder.spinner.visibility = View.VISIBLE
        }

        val options = holder.itemView.context.resources.getStringArray(CommonR.array.ride_options)
        val adapter = ArrayAdapter(
            holder.itemView.context,
            CommonR.layout.spinner_item_small,
            options
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        holder.spinner.adapter = adapter

        val initialPosition = options.indexOf(student.rideOption).takeIf { it != -1 } ?: 0
        holder.spinner.setSelection(initialPosition, false)

        holder.spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val selectedOption = options[pos]
                if (selectedOption != student.rideOption) {
                    updateRideOptionInFirebase(student, selectedOption)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateRideOptionInFirebase(student: StudentHome, option: String) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("parents").document(uid)

        if (student.id == "primary") {
            docRef.update("child.rideOption", option)
        } else {
            val index = student.id.toIntOrNull() ?: return
            docRef.get().addOnSuccessListener { document ->
                @Suppress("UNCHECKED_CAST")
                val children = document.get("children") as? List<KMap<String, Any>> ?: return@addOnSuccessListener
                val newList = children.toMutableList()
                if (index < newList.size) {
                    val updatedChild = newList[index].toMutableMap()
                    updatedChild["rideOption"] = option
                    newList[index] = updatedChild
                    docRef.update("children", newList)
                }
            }
        }
    }

    override fun getItemCount() = students.size
}
