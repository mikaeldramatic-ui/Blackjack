package com.example.blackjack

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import com.example.blackjack.databinding.ActivityGameboardBinding

/**
 * OverlayManager
 *
 * Encapsulates showing/hiding of win/lose/blackjack overlays.
 * Uses the ActivityGameboardBinding instance that your Activity already has.
 *
 * It does NOT touch game logic — instead it notifies the optional callback
 * when the overlay animation+delay finishes so caller can continue (advance player, etc).
 */
class OverlayManager(
    private val binding: ActivityGameboardBinding,
    private val callback: Callback? = null,
    private val animDuration: Long = 400L,
    private val visibleDelay: Long = 700L
) {

    private val handler = Handler(Looper.getMainLooper())

    interface Callback {
        /**
         * Called after the overlay finished hiding and the manager thinks it's time
         * to continue (advance turn / ask for bet / whatever caller needs).
         *
         * The overlayType string will be one of: "win", "lose", "push", "blackjack", "mixed", "eliminated"
         * amount contains the main numeric amount relevant (positive for win, negative for loss, 0 for push)
         */
        fun onOverlayFinished(overlayType: String, amount: Int)
    }

    // --- Public API used from existing code ---
    fun showWinOverlay(amount: Int) {
        val overlay = binding.winOverlay
        safeSetText(binding.winTotalMoney, "${binding.playerMoney.text}")
        safeSetText(binding.winAmountText, "+$amount")

        showOverlay(overlay) {
            // reset bet handled externally; notify caller
            callback?.onOverlayFinished("win", amount)
        }
    }

    fun showLoseOverlay(amount: Int) {
        val overlay = binding.loseOverlay
        safeSetText(binding.loseTotalMoney, "${binding.playerMoney.text}")
        safeSetText(binding.loseAmountText, "-$amount")

        showOverlay(overlay) {
            callback?.onOverlayFinished("lose", -amount)
        }
    }

    /**
     * Blackjack overlay:
     * type = "player" | "dealer" | "push"
     * winAmount used when type == "player"
     */
    fun showBlackjackOverlay(type: String, winAmount: Int = 0) {
        val overlay = binding.blackjackOverlay

        when (type) {
            "player" -> {
                safeSetText(binding.blackjackTitle, "BLACKJACK!")
                safeSetText(binding.blackjackAmount, "+$winAmount")
                safeSetText(binding.blackjackTotal, "${binding.playerMoney.text}")
            }
            "dealer" -> {
                safeSetText(binding.blackjackTitle, "DEALER BLACKJACK")
                safeSetText(binding.blackjackAmount, "-${binding.winAmountText.text}") // fallback
                safeSetText(binding.blackjackTotal, "${binding.playerMoney.text}")
            }
            "push" -> {
                safeSetText(binding.blackjackTitle, "PUSH")
                safeSetText(binding.blackjackAmount, "+0")
                safeSetText(binding.blackjackTotal, "${binding.playerMoney.text}")
            }
            else -> {
                safeSetText(binding.blackjackTitle, type.uppercase())
                safeSetText(binding.blackjackAmount, "+$winAmount")
                safeSetText(binding.blackjackTotal, "${binding.playerMoney.text}")
            }
        }

        showOverlay(overlay) {
            val overlayType = when (type) {
                "player" -> "blackjack"
                "dealer" -> "blackjack"
                "push" -> "push"
                else -> "blackjack"
            }
            callback?.onOverlayFinished(overlayType, winAmount)
        }
    }

    /**
     * Mixed split results / custom text overlay — reuses loseOverlay view to show text.
     * pushCount/winCount/lossCount/net are shown in the loseAmountText field.
     */
    fun showMixedSplitOverlay(pushCount: Int, winCount: Int, lossCount: Int, net: Int) {
        val overlay = binding.loseOverlay
        safeSetText(binding.loseTotalMoney, "${binding.playerMoney.text}")
        safeSetText(binding.loseAmountText, "PUSH: $pushCount | WIN: $winCount | LOSE: $lossCount\nNet: $net")

        showOverlay(overlay) {
            callback?.onOverlayFinished("mixed", net)
        }
    }

    fun showEliminatedOverlay(playerName: String) {
        val overlay = binding.loseOverlay
        safeSetText(binding.loseAmountText, "$playerName IS OUT!")
        safeSetText(binding.loseTotalMoney, "Money: $0")

        showOverlay(overlay) {
            callback?.onOverlayFinished("eliminated", 0)
        }
    }

    // --- Internal helper to animate/show/hide overlays consistently ---
    private fun showOverlay(overlay: View, onComplete: () -> Unit) {
        // set initial state
        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        overlay.scaleX = 0.8f
        overlay.scaleY = 0.8f

        // bring overlay to front (in case)
        overlay.bringToFront()

        // animate in
        overlay.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(animDuration)
            .withEndAction {
                // keep visible for visibleDelay then animate out
                handler.postDelayed({
                    overlay.animate().alpha(0f).setDuration(animDuration).withEndAction {
                        overlay.visibility = View.GONE
                        onComplete()
                    }.start()
                }, visibleDelay)
            }.start()
    }

    private fun safeSetText(tv: TextView, text: String) {
        try {
            tv.text = text
        } catch (_: Exception) { /* ignore if view missing */ }
    }
}