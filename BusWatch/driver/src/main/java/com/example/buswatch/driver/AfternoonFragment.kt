package com.example.buswatch.driver

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.buswatch.common.R as CommonR
import com.example.buswatch.driver.databinding.FragmentDriverAfternoonBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.*

class AfternoonFragment : Fragment() {

    private var _binding: FragmentDriverAfternoonBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: DriverViewModel by activityViewModels()
    private var studentAdapter: StudentAdapter? = null

    private val handler = Handler(Looper.getMainLooper())
    private val timeRunnable = object : Runnable {
        override fun run() {
            updateGreeting(viewModel.userName.value ?: "Driver")
            handler.postDelayed(this, 60000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDriverAfternoonBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ensure ViewModel knows we are in Afternoon mode
        viewModel.setCurrentTab("Afternoon")

        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        updateTabUI()
        
        handler.post(timeRunnable)
    }

    private fun setupRecyclerView() {
        binding.recyclerStudents.layoutManager = LinearLayoutManager(requireContext())
        studentAdapter = StudentAdapter(
            students = emptyList(),
            currentTab = "Afternoon",
            isPickupLayout = false,
            onPickUpClick = { student -> 
                // Afternoon pick up (from school) sets status to "Heading Home"
                viewModel.updateStudentStatus(student.id, "Heading Home")
            },
            onDropOffClick = { student -> 
                // Use the centralized drop-off logic in ViewModel
                viewModel.dropOffStudent(student)
            },
            onStudentClick = { student ->
                showStudentMedicalInfo(student)
            }
        )
        binding.recyclerStudents.adapter = studentAdapter
    }

    private fun setupObservers() {
        viewModel.userName.observe(viewLifecycleOwner) { name ->
            updateGreeting(name)
        }

        viewModel.assignedRoute.observe(viewLifecycleOwner) { route ->
            route?.let {
                binding.tvMorningTime.text = it.morningTime
                binding.tvAfternoonTime.text = it.afternoonTime
                binding.btnStartTrip.isEnabled = true
            } ?: run {
                binding.btnStartTrip.isEnabled = false
            }
        }

        viewModel.userRole.observe(viewLifecycleOwner) { role ->
            binding.btnStartTrip.text = if (role == "Conductor") "OPEN STUDENT ROSTER" else getString(CommonR.string.start_trip)
        }

        viewModel.students.observe(viewLifecycleOwner) { students ->
            studentAdapter?.updateStudents(students, "Afternoon")
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                viewModel.clearToast()
            }
        }
    }

    private fun showStudentMedicalInfo(student: Student) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_student_medical_info, null)
        val dialog = MaterialAlertDialogBuilder(requireContext(), CommonR.style.Theme_BusWatch_Dialog_Rounded)
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.tvStudentName).text = student.name
        dialogView.findViewById<TextView>(R.id.tvStudentGrade).text = student.grade
        dialogView.findViewById<TextView>(R.id.tvBloodType).text = student.bloodType
        dialogView.findViewById<TextView>(R.id.tvAllergies).text = student.allergies
        dialogView.findViewById<TextView>(R.id.tvConditions).text = student.medicalConditions
        dialogView.findViewById<TextView>(R.id.tvMedications).text = student.medications
        dialogView.findViewById<TextView>(R.id.tvEmergencyName).text = student.emergencyContact
        dialogView.findViewById<TextView>(R.id.tvEmergencyPhone).text = student.emergencyPhone

        val imgPhoto = dialogView.findViewById<ImageView>(R.id.imgStudentPhoto)
        Glide.with(this)
            .load(student.photoUrl)
            .circleCrop()
            .placeholder(CommonR.drawable.ic_person_placeholder)
            .into(imgPhoto)

        dialogView.findViewById<View>(R.id.btnClose).setOnClickListener { dialog.dismiss() }
        
        dialogView.findViewById<View>(R.id.btnCallEmergency).setOnClickListener {
            val phone = student.emergencyPhone
            if (phone.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = "tel:$phone".toUri()
                }
                startActivity(intent)
            }
        }
        
        dialog.show()
    }

    private fun setupClickListeners() {
        binding.tabMorning.setOnClickListener { (activity as? DriverHome)?.loadHome() }
        
        binding.btnStartTrip.setOnClickListener {
            viewModel.startTrip()
            (activity as? DriverHome)?.loadLiveTracking()
        }

        binding.containerNavAccount.setOnClickListener { (activity as? DriverHome)?.loadAccount() }
        binding.containerNavHome.setOnClickListener { (activity as? DriverHome)?.loadHome() }
        binding.containerNavSettings.setOnClickListener { (activity as? DriverHome)?.loadSettings() }
        binding.btnSOS.setOnClickListener { (activity as? DriverHome)?.showSOSConfirmation() }
        binding.btnSortStudents.setOnClickListener { showSortPopup(it) }
    }

    private fun showSortPopup(view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menu.add("Order By:").isEnabled = false
        popup.menu.add("Ascending (A-Z)").setOnMenuItemClickListener {
            viewModel.setSortMode("Name", true)
            true
        }
        popup.menu.add("Descending (Z-A)").setOnMenuItemClickListener {
            viewModel.setSortMode("Name", false)
            true
        }
        popup.menu.add("Default (Stop Order)").setOnMenuItemClickListener {
            viewModel.setSortMode("Stop", true)
            true
        }
        popup.show()
    }

    private fun updateGreeting(name: String) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val amPm = if (calendar.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        
        val timeGreeting = when (hour) {
            in 0..11 -> getString(CommonR.string.good_morning)
            in 12..17 -> getString(CommonR.string.good_afternoon)
            else -> getString(CommonR.string.good_evening)
        }
        
        binding.tvGreeting.text = getString(CommonR.string.greeting_format, timeGreeting, name)
        binding.tvCurrentTime.text = String.format(Locale.getDefault(), "%d:%02d %s", if (hour % 12 == 0) 12 else hour % 12, minute, amPm)
    }

    private fun updateTabUI() {
        val context = context ?: return
        binding.tabMorning.setBackgroundResource(CommonR.drawable.bg_tab_inactive)
        binding.tabMorning.setTextColor(ContextCompat.getColor(context, CommonR.color.gray_text))
        binding.tabAfternoon.setBackgroundResource(CommonR.drawable.bg_tab_active)
        binding.tabAfternoon.setTextColor(Color.BLACK)

        // Highlight Afternoon Schedule Time
        binding.tvMorningTime.setTextColor(ContextCompat.getColor(context, CommonR.color.gray_text))
        binding.tvMorningTime.setTypeface(null, Typeface.NORMAL)
        binding.tvAfternoonTime.setTextColor(Color.BLACK)
        binding.tvAfternoonTime.setTypeface(null, Typeface.BOLD)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(timeRunnable)
        _binding = null
    }
}
