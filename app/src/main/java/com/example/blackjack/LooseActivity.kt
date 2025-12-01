package com.example.blackjack

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.blackjack.databinding.ActivityLooseBinding




class LooseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLooseBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityLooseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Receive values

        val playerScore = intent.getIntExtra("PlayerScore", 0)
        val dealerScore = intent.getIntExtra("DealerScore", 0)

        binding.txtLooseTitle.text = "You Lost!"
        binding.txtScoreInfo.text = "Player: $playerScore\nDealer: $dealerScore"

        //Try again
        binding.btnLoosePlayAgain.setOnClickListener {
            val intent = Intent(this, GameBoardActivity::class.java)
            startActivity(intent)
            finish()
        }

        //Back to Main
        binding.btnLooseBackMenu.setOnClickListener {
            val intent= Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}