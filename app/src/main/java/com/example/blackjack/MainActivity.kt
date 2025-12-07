package com.example.blackjack

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.blackjack.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppContext.init(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        binding.btnNewGame.setOnClickListener {
            val intent = Intent(this, NewGameActivity::class.java)
            startActivity(intent)
        }

        binding.btnHighscore.setOnClickListener {

            val intent = Intent(this, HighscoreActivity::class.java)
            startActivity(intent)
        }

        binding.btnRules.setOnClickListener {
            val intent = Intent(this, RulesActivity::class.java)
            startActivity(intent)
        }

        binding.btnCredits.setOnClickListener {
            val intent = Intent(this, CreditsActivity::class.java)
            startActivity(intent)
        }


    }
}