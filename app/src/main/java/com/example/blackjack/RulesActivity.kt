package com.example.blackjack

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.blackjack.databinding.ActivityRulesBinding

class RulesActivity: AppCompatActivity() {

    private lateinit var binding: ActivityRulesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRulesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBackRules.setOnClickListener {
            finish()
        }
    }
}