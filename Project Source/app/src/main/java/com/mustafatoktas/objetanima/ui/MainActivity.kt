package com.mustafatoktas.objetanima.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.mustafatoktas.objetanima.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
    }

}