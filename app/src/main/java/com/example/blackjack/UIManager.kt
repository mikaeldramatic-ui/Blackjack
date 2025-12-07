package com.example.blackjack

import android.view.View
import com.example.blackjack.databinding.ActivityGameboardBinding

class UIManager(private val binding: ActivityGameboardBinding) {

    fun setMoney(amount: Int) {
        binding.playerMoney.text = "Money: $$amount"
    }

    fun setBet(amount: Int) {
        // lägg till Bet-text om du vill
    }

    fun setTitle(text: String) {
        // optional
    }

    fun enableButtons(hit: Boolean, stand: Boolean, split: Boolean) {
        binding.btnHit.isEnabled = hit
        binding.btnStand.isEnabled = stand
        binding.btnSplit.isEnabled = split
    }

    fun disableAllButtons() {
        enableButtons(false, false, false)
    }

    fun enableActions() {
        binding.btnHit.isEnabled = true
        binding.btnStand.isEnabled = true
        binding.btnSplit.isEnabled = binding.btnSplit.visibility == View.VISIBLE
    }

    fun disableActions() {
        binding.btnHit.isEnabled = false
        binding.btnStand.isEnabled = false
        binding.btnSplit.isEnabled = false
    }


    fun resetScores() {
        binding.playerScore.text = "Player: 0"
        binding.dealerScore.text = "Dealer: 0"
        binding.playerHand1Score.visibility = View.GONE
        binding.playerHand2Score.visibility = View.GONE
    }

    fun showSplit() {
        binding.btnSplit.visibility = View.VISIBLE
        binding.btnSplit.isEnabled = true
    }

    fun hideSplit() {
        binding.btnSplit.visibility = View.GONE
    }

    fun updateDealerScore(score: Int) {
        binding.dealerScore.text = "Dealer: $score"
    }

    fun updateMainScore(score: Int) {
        binding.playerScore.text = "Player: $score"
    }

    fun updateHand1Score(score: Int) {
        binding.playerHand1Score.visibility = View.VISIBLE
        binding.playerHand1Score.text = "Hand1: $score"
    }

    fun updateHand2Score(score: Int) {
        binding.playerHand2Score.visibility = View.VISIBLE
        binding.playerHand2Score.text = "Hand2: $score"
    }
}
