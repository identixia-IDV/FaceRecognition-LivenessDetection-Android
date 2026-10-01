package com.identixia.facerecognitionsdk.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.identixia.facerecognitionsdk.R
import com.identixia.facerecognitionsdk.kit.EnrolledPerson
import com.identixia.facerecognitionsdk.kit.FaceRecognitionClient

class EnrolledListActivity : AppCompatActivity() {

    private lateinit var client: FaceRecognitionClient
    private lateinit var listView: ListView
    private lateinit var txtEmpty: TextView
    private lateinit var txtCount: TextView
    private val people = mutableListOf<EnrolledPerson>()
    private val adapter = PeopleAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enrolled_list)

        client = FaceRecognitionClient.get(this)
        client.loadDatabase()

        findViewById<TextView>(R.id.txtTitle).setText(R.string.mode_enrolled_list)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        listView = findViewById(R.id.listPeople)
        txtEmpty = findViewById(R.id.txtEmpty)
        txtCount = findViewById(R.id.txtCount)
        listView.adapter = adapter

        listView.setOnItemLongClickListener { _, _, position, _ ->
            val person = people.getOrNull(position) ?: return@setOnItemLongClickListener true
            confirmDelete(person)
            true
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        client.loadDatabase()
        people.clear()
        people.addAll(client.enrolledPeople())
        adapter.notifyDataSetChanged()
        val empty = people.isEmpty()
        txtEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        listView.visibility = if (empty) View.GONE else View.VISIBLE
        txtCount.visibility = if (empty) View.INVISIBLE else View.VISIBLE
        txtCount.text = getString(R.string.enrolled_count_fmt, people.size)
    }

    private fun confirmDelete(person: EnrolledPerson) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_person)
            .setMessage(person.name)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_person) { _, _ ->
                client.removeEnrolled(setOf(person.id))
                refresh()
            }
            .show()
    }

    private inner class PeopleAdapter : BaseAdapter() {
        override fun getCount(): Int = people.size
        override fun getItem(position: Int): EnrolledPerson = people[position]
        override fun getItemId(position: Int): Long = people[position].id.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_person, parent, false)
            val person = people[position]
            view.findViewById<TextView>(R.id.textName).text = person.name
            view.findViewById<ImageView>(R.id.imageFace).setImageBitmap(client.thumbnail(person))
            view.findViewById<ImageButton>(R.id.buttonDelete).setOnClickListener {
                confirmDelete(person)
            }
            return view
        }
    }
}
