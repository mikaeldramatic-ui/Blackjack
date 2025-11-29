package com.example.blackjack

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.blackjack.databinding.ActivityNewGameBinding

class NewGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewGameBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityNewGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSinglePlayer.setOnClickListener {
          val intent = Intent(this, GameBoardActivity::class.java)
            startActivity(intent)
        }
        //Back button
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnVsMode.setOnClickListener {
            val intent = Intent(this, VsModeActivity::class.java)
            startActivity(intent)
        }
    }
}
