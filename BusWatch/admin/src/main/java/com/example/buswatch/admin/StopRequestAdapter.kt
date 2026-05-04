package com.example.buswatch.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.buswatch.common.R as CommonR

class StopRequestAdapter(
    private var requests: MutableList<StopRequest>,
    private val onViewClick: (StopRequest, Int) -> Unit
) : RecyclerView.Adapter<StopRequestAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val studentName: TextView = view.findViewById(R.id.tvStudentName)
        val avatar: ImageView = view.findViewById(R.id.imgAvatar)
        val btnView: ImageButton = view.findViewById(R.id.btnView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_map_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = requests[position]
        holder.studentName.text = request.studentName

        Glide.with(holder.itemView.context)
            .load(request.parentAvatarUrl)
            .placeholder(CommonR.drawable.ic_stop_marker)
            .circleCrop()
            .into(holder.avatar)

        holder.btnView.setOnClickListener { onViewClick(request, position) }
        holder.itemView.setOnClickListener { onViewClick(request, position) }
    }

    override fun getItemCount() = requests.size
}
