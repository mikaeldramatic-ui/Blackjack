package com.example.blackjack

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.blackjack.databinding.ActivityHighscoreBinding

class HighscoreActivity : AppCompatActivity() {

    private lateinit var binding : ActivityHighscoreBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHighscoreBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBackHighscore.setOnClickListener {
            finish()

        }

        //Placeholder
        binding.score1.text = "1. Micke - 500"
        binding.score2.text = "2. Arvid - 420"
        binding.score3.text = "3. David - 320"
        binding.score4.text = "4. Stefan - 230"
        binding.score5.text = "5. Johan - 100"

    }
}