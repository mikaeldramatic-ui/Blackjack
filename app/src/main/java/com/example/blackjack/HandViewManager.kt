package com.example.blackjack

import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import com.example.blackjack.databinding.ActivityGameboardBinding

class HandViewManager(
    private val binding: ActivityGameboardBinding
) {

    private var dealerHiddenView: ImageView? = null

    // -----------------------------------------------------
    // CLEAR ALL
    // -----------------------------------------------------
    fun clearAll() {
        binding.playerCards.removeAllViews()

        binding.playerHand1.removeAllViews()
        binding.playerHand1.visibility = View.GONE
        binding.playerHand1Score.visibility = View.GONE

        binding.playerHand2.removeAllViews()
        binding.playerHand2.visibility = View.GONE
        binding.playerHand2Score.visibility = View.GONE

        binding.dealerCards.removeAllViews()
        dealerHiddenView = null
    }

    // -----------------------------------------------------
    // INITIAL PLAYER CARDS
    // -----------------------------------------------------
    fun showInitialPlayerCards(cards: List<Card>) {
        cards.forEach { addAnimatedCard(binding.playerCards, it) }
    }

    fun addPlayerCard(card: Card) {
        addAnimatedCard(binding.playerCards, card)
    }

    // -----------------------------------------------------
    // DEALER VISIBLE
    // -----------------------------------------------------
    fun addDealerCard(card: Card) {
        addAnimatedCard(binding.dealerCards, card)
    }

    // -----------------------------------------------------
    // DEALER HIDDEN
    // -----------------------------------------------------
    fun showDealerHidden() {
        if (dealerHiddenView != null) return
        val img = ImageView(binding.root.context)
        img.layoutParams = LinearLayout.LayoutParams(120, 180).apply {
            setMargins(8, 0, 8, 0)
        }

        img.setImageResource(R.drawable.card_back)

        binding.dealerCards.addView(img)
        dealerHiddenView = img
    }

    // -----------------------------------------------------
    // FLIP HIDDEN CARD
    // -----------------------------------------------------
    fun flipDealerHiddenTo(card: Card) {
        val view = dealerHiddenView ?: return

        view.animate()
            .rotationY(90f)
            .setDuration(150)
            .withEndAction {
                view.setImageResource(card.drawableRes)
                view.rotationY = -90f
                view.animate().rotationY(0f).setDuration(150).start()
            }
            .start()
    }

    // -----------------------------------------------------
    // SPLIT DISPLAY
    // -----------------------------------------------------
    fun enableSplitMode(hand1: List<Card>, hand2: List<Card>) {
        binding.playerHand1.visibility = View.VISIBLE
        binding.playerHand2.visibility = View.VISIBLE
        binding.playerHand1Score.visibility = View.VISIBLE
        binding.playerHand2Score.visibility = View.VISIBLE

        binding.playerCards.removeAllViews()
        binding.playerHand1.removeAllViews()
        binding.playerHand2.removeAllViews()

        hand1.forEach { addAnimatedCard(binding.playerHand1, it) }
        hand2.forEach { addAnimatedCard(binding.playerHand2, it) }
    }

    fun hideSplitScores() {
        binding.playerHand1.visibility = View.GONE
        binding.playerHand2.visibility = View.GONE
        binding.playerHand1Score.visibility = View.GONE
        binding.playerHand2Score.visibility = View.GONE
    }

    fun addToHand1(card: Card) {
        addAnimatedCard(binding.playerHand1, card)
    }

    fun addToHand2(card: Card) {
        addAnimatedCard(binding.playerHand2, card)
    }

    fun focusHand1() {
        binding.playerHand1.alpha = 1f
        binding.playerHand2.alpha = 0.4f
    }

    fun focusHand2() {
        binding.playerHand1.alpha = 0.4f
        binding.playerHand2.alpha = 1f
    }

    // -----------------------------------------------------
    // SCORE UI - player
    // -----------------------------------------------------
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

    // -----------------------------------------------------
    // SCORE UI - dealer
    // -----------------------------------------------------
    fun updateDealerScore(score: Int) {
        binding.dealerScore.text = "Dealer: $score"
    }

    /**
     * If you want to display dealer's soft/hard option (e.g. when an ace is involved),
     * this helper will show "low / high" format.
     */
    fun updateDealerScoreWithAceOptions(low: Int, high: Int) {
        binding.dealerScore.text = "Dealer: $low / $high"
    }

    // -----------------------------------------------------
    // ACE OPTIONAL VALUES (player)
    // -----------------------------------------------------
    fun updateMainScoreWithAceOptions(low: Int, high: Int) {
        binding.playerScore.text = "Player: $low / $high"
    }

    // -----------------------------------------------------
    // ANIMATED ADD CARD
    // -----------------------------------------------------
    private fun addAnimatedCard(layout: LinearLayout, card: Card) {
        val img = ImageView(layout.context)

        img.layoutParams = LinearLayout.LayoutParams(120, 180).apply {
            setMargins(8, 0, 8, 0)
        }

        img.setImageResource(card.drawableRes)

        // initial state matches animation expectations
        img.alpha = 0f
        img.translationX = -150f

        layout.addView(img)

        img.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(350)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    // -----------------------------------------------------
    // FORCE REDRAW ALL (safe helper)
    // -----------------------------------------------------
    fun forceRedrawAll(
        playerCards: List<Card>? = null,
        dealerCardsList: List<Card>? = null
    ) {
        clearAll()

        val pCards = playerCards ?: emptyList()
        showInitialPlayerCards(pCards)

        val dCards = dealerCardsList ?: emptyList()
        if (dCards.isNotEmpty()) {
            addDealerCard(dCards[0])
            if (dCards.size > 1) showDealerHidden()
        }
    }
}