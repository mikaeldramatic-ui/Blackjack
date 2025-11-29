package com.example.blackjack

import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import com.example.blackjack.databinding.ActivityLooseBinding
import java.util.zip.Inflater

class LooseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLooseBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityLooseBinding.inflate(LayoutInflater)
        setContentView(binding.root)

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