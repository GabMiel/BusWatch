package com.example.buswatch.admin

import android.content.Context
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.Query

/**
 * A helper class to manage pagination and sorting UI and logic for different fragments.
 */
class PaginationHelper(
    private val context: Context,
    private val view: View,
    private val itemsPerPage: Int,
    private val entityName: String, // e.g., "name", "routeName", "lastName" for sorting
    private val onDataRequest: (Int, Query.Direction) -> Unit
) {
    private var currentPage = 1
    private var totalItems = 0
    private var sortDirection = Query.Direction.ASCENDING

    private val etCurrentPage: EditText? = view.findViewById(R.id.etCurrentPage)
    private val tvTotalPages: TextView? = view.findViewById(R.id.tvTotalPages)
    private val btnFirst: View? = view.findViewById(R.id.btnFirstPage)
    private val btnPrev: View? = view.findViewById(R.id.btnPrevPage)
    private val btnNext: View? = view.findViewById(R.id.btnNextPage)
    private val btnLast: View? = view.findViewById(R.id.btnLastPage)
    private val btnSort: View? = view.findViewById(R.id.btnSort)

    init {
        setupListeners()
    }

    private fun setupListeners() {
        btnFirst?.setOnClickListener { setPage(1) }
        btnPrev?.setOnClickListener { setPage(currentPage - 1) }
        btnNext?.setOnClickListener { setPage(currentPage + 1) }
        btnLast?.setOnClickListener { setPage(getMaxPage()) }

        etCurrentPage?.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val input = v.text.toString().toIntOrNull() ?: currentPage
                setPage(input)
                v.clearFocus()
                true
            } else false
        }

        btnSort?.setOnClickListener {
            showSortDialog()
        }
    }

    private fun showSortDialog() {
        val options = arrayOf("Ascending (A-Z)", "Descending (Z-A)")
        AlertDialog.Builder(context)
            .setTitle("Sort by $entityName")
            .setItems(options) { _, which ->
                val newDirection = if (which == 0) Query.Direction.ASCENDING else Query.Direction.DESCENDING
                if (sortDirection != newDirection) {
                    sortDirection = newDirection
                    setPage(1) // Reset to first page on sort change
                }
            }
            .show()
    }

    fun setTotalItems(count: Int) {
        totalItems = count
        updateUI()
    }

    fun reset() {
        currentPage = 1
        updateUI()
    }

    fun getCurrentPage() = currentPage
    fun getSortDirection() = sortDirection

    private fun setPage(page: Int) {
        val max = getMaxPage()
        val validPage = page.coerceIn(1, max)
        
        // Always trigger if it's the first load (totalItems == 0 initially)
        // or if the page/sort actually changed.
        currentPage = validPage
        updateUI()
        onDataRequest(currentPage, sortDirection)
    }

    private fun getMaxPage() = Math.ceil(totalItems.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)

    private fun updateUI() {
        val max = getMaxPage()
        etCurrentPage?.setText(currentPage.toString())
        tvTotalPages?.text = " of $max"
        
        val canPrev = currentPage > 1
        val canNext = currentPage < max
        
        btnFirst?.isEnabled = canPrev
        btnPrev?.isEnabled = canPrev
        btnNext?.isEnabled = canNext
        btnLast?.isEnabled = canNext

        // Visual feedback for disabled state
        btnFirst?.alpha = if (canPrev) 1.0f else 0.3f
        btnPrev?.alpha = if (canPrev) 1.0f else 0.3f
        btnNext?.alpha = if (canNext) 1.0f else 0.3f
        btnLast?.alpha = if (canNext) 1.0f else 0.3f
    }
}
