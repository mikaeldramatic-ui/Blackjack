package com.example.blackjack
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.blackjack.databinding.ActivityVsModeBinding


class VsModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVsModeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVsModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        var playerCount = 1 //min, max = 4

        binding.txtPlayerCount.text = playerCount.toString()

        binding.btnMinus.setOnClickListener {
            if (playerCount > 1) {
                playerCount--
                binding.txtPlayerCount.text = playerCount.toString()
            }
        }

        binding.btnPlus.setOnClickListener {
            if (playerCount < 4) {
                playerCount++
                binding.txtPlayerCount.text = playerCount.toString()
            }
        }

        //Back Button
        binding.btnBackVsMode.setOnClickListener {
            finish()
        }


    }
}