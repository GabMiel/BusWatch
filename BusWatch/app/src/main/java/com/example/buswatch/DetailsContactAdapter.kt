package com.example.buswatch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class ContactDetail(
    val label: String,
    val value: String,
    val name: String? = null,
    val relationship: String? = null
)

class DetailsContactAdapter(
    private val contacts: List<ContactDetail>
) : RecyclerView.Adapter<DetailsContactAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.tvContactLabel)
        val name: TextView = view.findViewById(R.id.tvContactName)
        val relation: TextView = view.findViewById(R.id.tvContactRelation)
        val value: TextView = view.findViewById(R.id.tvContactValue)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_details_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.label.text = contact.label
        holder.value.text = contact.value
        
        if (contact.name != null) {
            holder.name.text = contact.name
            holder.name.visibility = View.VISIBLE
        } else {
            holder.name.visibility = View.GONE
        }

        if (contact.relationship != null) {
            holder.relation.text = contact.relationship
            holder.relation.visibility = View.VISIBLE
        } else {
            holder.relation.visibility = View.GONE
        }
    }

    override fun getItemCount() = contacts.size
}
