package com.example.blackjack

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.example.blackjack.databinding.ActivityGameboardBinding

class HandViewManager(
    private val binding: ActivityGameboardBinding
) {

    // -----------------------
    // Public API
    // -----------------------

    fun showMainHand(cards: List<Card>) {
        binding.playerCards.visibility = View.VISIBLE
        binding.playerHand1.visibility = View.GONE
        binding.playerHand2.visibility = View.GONE

        binding.playerCards.removeAllViews()

        for (c in cards) addCard(binding.playerCards, c)
    }

    fun showSplitHands(
        hand1: List<Card>,
        hand2: List<Card>,
        activeHand: Int // 1 or 2
    ) {
        binding.playerCards.visibility = View.GONE
        binding.playerHand1.visibility = View.VISIBLE
        binding.playerHand2.visibility = View.VISIBLE

        binding.playerHand1.removeAllViews()
        binding.playerHand2.removeAllViews()

        hand1.forEach { addCard(binding.playerHand1, it) }
        hand2.forEach { addCard(binding.playerHand2, it) }

        highlightActiveHand(activeHand)
    }

    fun showDealerInitial(visible: Card, hiddenRes: Int) {
        binding.dealerCards.removeAllViews()

        // visible card
        addCard(binding.dealerCards, visible)

        // hidden card
        addBackCard(binding.dealerCards, hiddenRes)
    }

    fun showDealerCards(cards: List<Card>) {
        binding.dealerCards.removeAllViews()
        cards.forEach { addCard(binding.dealerCards, it) }
    }

    fun revealDealerHiddenCard(index: Int, realDrawable: Int) {
        if (index < 0 || index >= binding.dealerCards.childCount) return
        val iv = binding.dealerCards.getChildAt(index) as? ImageView ?: return
        flipCard(iv, realDrawable)
    }

    fun clearAll() {
        binding.playerCards.removeAllViews()
        binding.playerHand1.removeAllViews()
        binding.playerHand2.removeAllViews()
        binding.dealerCards.removeAllViews()
    }

    fun highlightActiveHand(activeHand: Int) {
        if (activeHand == 1) {
            binding.playerHand1.alpha = 1f
            binding.playerHand2.alpha = 0.4f
        } else {
            binding.playerHand1.alpha = 0.4f
            binding.playerHand2.alpha = 1f
        }
    }

    // -----------------------
    // Internal helpers
    // -----------------------

    private fun addCard(container: ViewGroup, card: Card) {
        val iv = ImageView(container.context)
        iv.setImageResource(card.drawableRes)
        iv.scaleType = ImageView.ScaleType.CENTER_INSIDE

        val lp = ViewGroup.MarginLayoutParams(dp(72), dp(100))
        lp.setMargins(8, 0, 8, 0)
        iv.layoutParams = lp

        container.addView(iv)
        animateCardIn(iv)
    }

    private fun addBackCard(container: ViewGroup, drawableRes: Int) {
        val iv = ImageView(container.context)
        iv.setImageResource(drawableRes)
        iv.scaleType = ImageView.ScaleType.CENTER_INSIDE

        val lp = ViewGroup.MarginLayoutParams(dp(72), dp(100))
        lp.setMargins(8, 0, 8, 0)
        iv.layoutParams = lp

        container.addView(iv)
        animateCardIn(iv)
    }

    private fun flipCard(iv: ImageView, newDrawable: Int) {
        iv.animate().scaleX(0f).setDuration(150).withEndAction {
            iv.setImageResource(newDrawable)
            iv.animate().scaleX(1f).setDuration(150).start()
        }.start()
    }

    private fun animateCardIn(iv: ImageView) {
        iv.alpha = 0f
        iv.scaleX = 0f
        iv.scaleY = 0f
        iv.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .start()
    }

    private fun dp(value: Int): Int {
        val density = binding.root.resources.displayMetrics.density
        return (value * density + 0.5f).toInt()
    }
}