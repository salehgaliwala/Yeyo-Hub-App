package com.trackigniter.client

import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.fragment.app.Fragment

class StatusFragment : Fragment() {

    private var adapter: ArrayAdapter<String>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.list, container, false)
        val listView = view.findViewById<ListView>(android.R.id.list)
        
        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, android.R.id.text1, StatusActivity.messages)
        listView.adapter = adapter
        
        adapter?.let { StatusActivity.adapters.add(it) }
        
        setHasOptionsMenu(true)
        return view
    }

    override fun onDestroyView() {
        adapter?.let { StatusActivity.adapters.remove(it) }
        super.onDestroyView()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.status, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.clear) {
            StatusActivity.clearMessages()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
