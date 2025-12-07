package com.example.blackjack

import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.example.blackjack.databinding.ActivityGameboardBinding

class OverlayManager(
    private val binding: ActivityGameboardBinding
) {

    fun hideAll() {
        binding.winOverlay.visibility = View.GONE
        binding.loseOverlay.visibility = View.GONE
        binding.pushOverlay.visibility = View.GONE

        binding.winOverlay.alpha = 0f
        binding.loseOverlay.alpha = 0f
        binding.pushOverlay.alpha = 0f
    }

    // -----------------------
    // WIN OVERLAY
    // -----------------------
    fun showWinOverlay(amount: Int) {
        binding.winAmountText.text = "+$amount"
        binding.winTotalMoney.text = "Money: $${getMoney()}"

        showOverlay(binding.winOverlay)
    }

    fun showBlackjackOverlay(amount: Int) {
        binding.blackjackAmountText.text = "+$amount"
        binding.blackjackTotalMoney.text = "Money: $${getMoney()}"

        showOverlay(binding.blackjackOverlay)
    }

    // -----------------------
    // LOSE OVERLAY
    // -----------------------
    fun showLoseOverlay(amount: Int) {
        binding.loseAmountText.text = "-$amount"
        binding.loseTotalMoney.text = "Money: $${getMoney()}"

        showOverlay(binding.loseOverlay)
    }

    // -----------------------
    // PUSH OVERLAY
    // -----------------------
    fun showPushOverlay(amount: Int) {
        binding.pushOverlay.visibility = View.VISIBLE
        binding.pushOverlay.alpha = 0f

        // (Push overlay has no amount text)
        animateFadeIn(binding.pushOverlay)
    }



    // -----------------------
    // HELPERS
    // -----------------------
    private fun showOverlay(overlay: View) {
        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        animateFadeIn(overlay)
    }

    private fun animateFadeIn(view: View) {
        view.animate()
            .alpha(1f)
            .setDuration(450)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun getMoney(): Int {
        // winTotalMoney / loseTotalMoney ska alltid visa aktuella pengar
        // Men RoundManager uppdaterar UI-managern först → binding.playerMoney uppdateras alltid.
        val text = binding.playerMoney.text.toString().replace("Money: $", "")
        return text.toIntOrNull() ?: 0
    }

    fun resetVisualState() {
        val all = listOf(
            binding.winOverlay,
            binding.loseOverlay,
            binding.pushOverlay,
            binding.blackjackOverlay
        )

        all.forEach { view ->
            view.clearAnimation()
            view.animate().cancel()

            view.visibility = View.GONE
            view.alpha = 0f
            view.scaleX = 1f
            view.scaleY = 1f
        }
    }
}