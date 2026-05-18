package com.example.llama

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MasteredTopicsActivity : AppCompatActivity() {
    private lateinit var listView: ListView
    private lateinit var addButton: Button
    private lateinit var topicInput: EditText
    private val topics = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mastered_topics)

        listView = findViewById(R.id.mastered_list_view)
        addButton = findViewById(R.id.btn_add_topic)
        topicInput = findViewById(R.id.topic_input)

        loadTopics()
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, topics)
        listView.adapter = adapter

        addButton.setOnClickListener {
            val newTopic = topicInput.text.toString().trim()
            if (newTopic.isNotEmpty() && !topics.contains(newTopic)) {
                topics.add(newTopic)
                saveTopics()
                adapter.notifyDataSetChanged()
                topicInput.text.clear()
                Toast.makeText(this, "Topic added", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Invalid or duplicate topic", Toast.LENGTH_SHORT).show()
            }
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            topics.removeAt(position)
            saveTopics()
            adapter.notifyDataSetChanged()
            Toast.makeText(this, "Topic removed", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun loadTopics() {
        val file = File(filesDir, "mastered_topics.txt")
        if (file.exists()) {
            topics.clear()
            topics.addAll(file.readLines().filter { it.isNotBlank() })
        }
    }

    private fun saveTopics() {
        File(filesDir, "mastered_topics.txt").writeText(topics.joinToString("\n"))
    }
}