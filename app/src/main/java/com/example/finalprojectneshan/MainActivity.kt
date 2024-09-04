package com.example.finalprojectneshan

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val secondActivity=findViewById<Button>(R.id.button1)
        secondActivity.setOnClickListener{
            val Intent=Intent(this,SecondActivity::class.java)
            startActivity(Intent)
        }
    }
}
