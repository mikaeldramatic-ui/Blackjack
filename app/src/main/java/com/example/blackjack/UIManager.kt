package com.example.blackjack

import android.view.View
import com.example.blackjack.databinding.ActivityGameboardBinding

class UIManager(
    private val binding: ActivityGameboardBinding
) {

    // -----------------------------
    // PLAYER MONEY + HEADER
    // -----------------------------
    fun updatePlayerHeader(name: String, money: Int) {
        binding.playerMoney.text = "$name – $$money"
    }

    // -----------------------------
    // SCORE UPDATES
    // -----------------------------
    fun updateMainScore(scoreText: String) {
        binding.playerScore.visibility = View.VISIBLE
        binding.playerScore.text = "Player: $scoreText"
    }

    fun updateSplitScores(score1: String, score2: String) {
        binding.playerHand1Score.visibility = View.VISIBLE
        binding.playerHand2Score.visibility = View.VISIBLE

        binding.playerHand1Score.text = "Hand1: $score1"
        binding.playerHand2Score.text = "Hand2: $score2"
    }

    fun hideSplitScores() {
        binding.playerHand1Score.visibility = View.GONE
        binding.playerHand2Score.visibility = View.GONE
    }

    fun updateDealerScore(score: Int) {
        binding.dealerScore.text = "Dealer: $score"
    }

    fun resetScores() {
        binding.playerScore.text = "Player: 0"
        binding.dealerScore.text = "Dealer: 0"
    }

    // -----------------------------
    // BUTTON CONTROL
    // -----------------------------
    fun enableActions() {
        binding.btnHit.isEnabled = true
        binding.btnStand.isEnabled = true
    }

    fun disableActions() {
        binding.btnHit.isEnabled = false
        binding.btnStand.isEnabled = false
    }

    // -----------------------------
    // SPLIT BUTTON CONTROL
    // -----------------------------
    fun showSplit() {
        binding.btnSplit.visibility = View.VISIBLE
        binding.btnSplit.isEnabled = true
    }

    fun hideSplit() {
        binding.btnSplit.visibility = View.GONE
        binding.btnSplit.isEnabled = false
    }

    // -----------------------------
    // DEFAULT UI RESET
    // -----------------------------
    fun resetHandLayout() {
        binding.playerCards.visibility = View.VISIBLE
        binding.playerHand1.visibility = View.GONE
        binding.playerHand2.visibility = View.GONE
    }

    fun resetForNewPlayer() {
        resetScores()
        hideSplit()
        enableActions()
    }

    // -----------------------------
    // BLACKJACK OVERLAY HELPER
    // -----------------------------
    fun showBlackjackMessage(text: String) {
        binding.blackjackTitle.text = text
        binding.blackjackOverlay.visibility = View.VISIBLE
    }

    fun hideBlackjackMessage() {
        binding.blackjackOverlay.visibility = View.GONE
    }
}