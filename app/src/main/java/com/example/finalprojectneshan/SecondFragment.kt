package com.example.finalprojectneshan


import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.carto.styles.MarkerStyle
import com.carto.styles.MarkerStyleBuilder
import com.carto.utils.BitmapUtils
import org.neshan.common.model.LatLng
import org.neshan.servicessdk.search.NeshanSearch
import org.neshan.servicessdk.search.model.Item
import org.neshan.servicessdk.search.model.NeshanSearchResult
import retrofit2.Callback




class SecondFragment(private val location: LatLng, private val onDataPass: PassDataToActivity) :
    Fragment(),
    SearchAdapter.OnSearchItemListener {


    val MY_CONSTANT = "service.4ccd66d4b31f4d8bb354e0311f98d5cc"
    private val TAG = "Search"
    private lateinit var editText: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var items: List<Item>
    private lateinit var adapter: SearchAdapter


    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_second, container, false)

        editText = v.findViewById(R.id.SearchEditText)
        recyclerView = v.findViewById(R.id.recyclerView)
        return v

    }


    override fun onStart() {
        super.onStart()
        // everything related to ui is initialized here
        initLayoutReferences()
    }


    // Initializing layout references (views, map and map events)
    private fun initLayoutReferences() {
        // Initializing views
        initViews()
        // Initializing mapView element

        //listen for search text change
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                search(s.toString())
                Log.i(TAG, "afterTextChanged: $s")
            }
        })

        editText.setOnEditorActionListener(object : TextView.OnEditorActionListener {
            override fun onEditorAction(p0: TextView?, p1: Int, p2: KeyEvent?): Boolean {
                if (p1 == EditorInfo.IME_ACTION_SEARCH) {
                    closeKeyBoard()
                    search(editText.text.toString())
                }
                return false
            }
        })

    }

    // We use findViewByID for every element in our layout file here
    private fun initViews() {

        items = java.util.ArrayList()
        adapter = SearchAdapter(items, this)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun search(term: String) {
        val searchPosition: LatLng = location
        updateCenterMarker(searchPosition)
        NeshanSearch.Builder(MY_CONSTANT)
            .setLocation(searchPosition)
            .setTerm(term)
            .build().call(object : Callback<NeshanSearchResult?> {
                override fun onResponse(
                    call: retrofit2.Call<NeshanSearchResult?>,
                    response: retrofit2.Response<NeshanSearchResult?>
                ) {
                    if (response.code() == 403) {
                        Toast.makeText(
                            requireContext(),
                            "کلید دسترسی نامعتبر",
                            Toast.LENGTH_LONG
                        ).show()
                        return
                    }
                    if (response.body() != null) {
                        val result = response.body()
                        items = result!!.items
                        adapter.updateList(items)
                    }
                }

                override fun onFailure(call: retrofit2.Call<NeshanSearchResult?>, t: Throwable) {
                    Log.i(TAG, "onFailure: " + t.message)
                    Toast.makeText(requireContext(), "ارتباط برقرار نشد!", Toast.LENGTH_SHORT)
                        .show()
                }
            })
    }

    private fun updateCenterMarker(LatLng: LatLng) {

    }

    private fun getCenterMarkerStyle(): MarkerStyle? {
        val markerStyleBuilder = MarkerStyleBuilder()
        markerStyleBuilder.size = 50f
        markerStyleBuilder.bitmap = BitmapUtils.createBitmapFromAndroidBitmap(
            BitmapFactory.decodeResource(
                resources, R.drawable.center_marker
            )
        )
        return markerStyleBuilder.buildStyle()
    }


    private fun getMarkerStyle(size: Float): MarkerStyle? {
        val styleCreator = MarkerStyleBuilder()
        styleCreator.size = size
        styleCreator.bitmap = BitmapUtils.createBitmapFromAndroidBitmap(
            BitmapFactory.decodeResource(
                resources, R.drawable.ic_marker
            )
        )
        return styleCreator.buildStyle()
    }

    private fun closeKeyBoard() {

    }

    fun showSearchClick(view: View?) {
        closeKeyBoard()
        adapter!!.updateList(items!!)
    }

    fun showMarkersClick(view: View) {
        adapter.updateList(java.util.ArrayList())
        closeKeyBoard()
        var minLat = Double.MAX_VALUE
        var minLng = Double.MAX_VALUE
        var maxLat = Double.MIN_VALUE
        var maxLng = Double.MIN_VALUE
        for (item in items) {
            val location = item.location
            val latLng = location.latLng
            minLat = Math.min(latLng.latitude, minLat)
            minLng = Math.min(latLng.longitude, minLng)
            maxLat = Math.max(latLng.latitude, maxLat)
            maxLng = Math.max(latLng.longitude, maxLng)
        }
        if (items.isNotEmpty()) {

        }

    }

    override fun onSearchItemClick(item: Item?) {
        closeKeyBoard()
        adapter.updateList(java.util.ArrayList())
        onDataPass.passData(item)

    }


}