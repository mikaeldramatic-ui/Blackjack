package com.example.blackjack

import android.os.Bundle
import android.os.PersistableBundle
import androidx.appcompat.app.AppCompatActivity
import com.example.blackjack.databinding.ActivityCreditsBinding
class CreditsActivity: AppCompatActivity() {

    private lateinit var binding: ActivityCreditsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCreditsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBackCredits.setOnClickListener {
            finish()
        }

    }

}
