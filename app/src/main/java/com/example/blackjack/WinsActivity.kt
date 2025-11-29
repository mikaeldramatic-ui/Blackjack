package com.example.blackjack

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.blackjack.databinding.ActivityWinsBinding


class WinsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWinsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWinsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Receive values

        val playerScore = intent.getIntExtra("PlayerScore", 0)
        val dealerScore = intent.getIntExtra("DealerScore", 0)

        binding.txtWinTitle.text = "You Win!"
        binding.txtScoreInfo.text = "Player: $playerScore\nDealer: $dealerScore"

        binding.btnWinBackMenu.setOnClickListener {
            finish()
        }

        //Play Again - Start new Game

        binding.btnPlayAgain.setOnClickListener {
            val intent = Intent(this, GameBoardActivity::class.java)
            startActivity(intent)
            finish() //CLose activity
        }

        //Back to Main

        binding.btnWinBackMenu.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}