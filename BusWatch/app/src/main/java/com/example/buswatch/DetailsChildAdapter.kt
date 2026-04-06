package com.example.buswatch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.buswatch.common.R as CommonR

data class ChildDetail(
    val name: String,
    val grade: String,
    val school: String,
    val status: String,
    val avatarName: String? = null,
    val avatarUrl: String? = null
)

class DetailsChildAdapter(
    private val children: List<ChildDetail>,
    private val onViewClick: (ChildDetail) -> Unit
) : RecyclerView.Adapter<DetailsChildAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvChildName)
        val grade: TextView = view.findViewById(R.id.tvChildGrade)
        val school: TextView = view.findViewById(R.id.tvChildSchool)
        val status: TextView = view.findViewById(R.id.tvChildStatus)
        val avatar: ImageView = view.findViewById(R.id.imgChildAvatar)
        val btnView: ImageButton = view.findViewById(R.id.btnChildView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_details_child, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val child = children[position]
        holder.name.text = child.name
        holder.grade.text = child.grade
        holder.school.text = child.school
        holder.status.text = child.status
        
        // Handle Avatar loading using Glide
        if (!child.avatarUrl.isNullOrEmpty()) {
            Glide.with(holder.avatar.context)
                .load(child.avatarUrl)
                .placeholder(CommonR.drawable.child)
                .error(CommonR.drawable.child)
                .circleCrop()
                .into(holder.avatar)
        } else {
            setDefaultAvatar(holder.avatar, child.avatarName)
        }
        
        holder.btnView.setOnClickListener { onViewClick(child) }
        holder.itemView.setOnClickListener { onViewClick(child) }
    }

    private fun setDefaultAvatar(imageView: ImageView, avatarName: String?) {
        val context = imageView.context
        val avatarResId = if (!avatarName.isNullOrEmpty()) {
            @Suppress("DiscouragedApi")
            val resId = context.resources.getIdentifier(avatarName, "drawable", context.packageName)
            if (resId != 0) resId else CommonR.drawable.child
        } else {
            CommonR.drawable.child
        }
        imageView.setImageResource(avatarResId)
    }

    override fun getItemCount() = children.size
}
