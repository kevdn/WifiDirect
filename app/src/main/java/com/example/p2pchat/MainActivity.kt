package com.example.p2pchat

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val btnStartChat = findViewById<Button>(R.id.btnStartChat)

        btnStartChat.setOnClickListener {
            val username = etUsername.text.toString().trim()
            if (username.isNotEmpty()) {
                val intent = Intent(this, ChatActivity::class.java)
                intent.putExtra("username", username)
                startActivity(intent)
            } else {
                etUsername.error = "Please enter your username"
            }
        }
    }
}