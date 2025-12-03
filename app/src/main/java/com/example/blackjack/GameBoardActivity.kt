package com.example.blackjack

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.blackjack.databinding.ActivityGameboardBinding
import kotlin.random.Random

class GameBoardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameboardBinding

    // Money
    private var playerMoney = 200
    private var currentBet = 0

    // Game variables
    private var playerScore = 0
    private var dealerScore = 0
    private val deck = mutableListOf<String>()

    private var dealerHiddenCardCode: String? = null
    private var dealerHiddenImageView: ImageView? = null

    // Use main looper handler
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // UI default text
        binding.playerScore.text = "Player: 0"
        binding.dealerScore.text = "Dealer: 0"
        binding.playerMoney.text = "Money: $$playerMoney"

        // Button listeners
        binding.btnHit.setOnClickListener { onHitClicked() }
        binding.btnStand.setOnClickListener { onStandClicked() }
        binding.btnQuit.setOnClickListener { showQuitDialog() }

        // Prepare deck
        createDeck()
        shuffleDeck()

        // Begin with bet prompt
        askForBet()
    }

    // -------------------- Betting --------------------
    private fun askForBet() {
        if (playerMoney < 10) {
            showNoMoneyDialog()
            return
        }

        val bets = arrayOf("10", "20", "50", "100")

        AlertDialog.Builder(this)
            .setTitle("Place Your Bet")
            .setItems(bets) { _, which ->
                currentBet = bets[which].toInt()
                if (currentBet > playerMoney) currentBet = 10

                binding.playerMoney.text = "Money: $$playerMoney"

                // After bet - start round
                dealInitialCards()
            }
            .setCancelable(false)
            .show()
    }

    // -------------------- Deal initial --------------------
    private fun dealInitialCards() {
        binding.playerCards.removeAllViews()
        binding.dealerCards.removeAllViews()

        playerScore = 0
        dealerScore = 0

        if (deck.size < 4) {
            createDeck()
            shuffleDeck()
        }

        // Player 2 cards
        val p1 = deck.removeAt(0)
        val p2 = deck.removeAt(0)
        addCardImageToLayout(p1, binding.playerCards)
        addCardImageToLayout(p2, binding.playerCards)
        playerScore += getCardValue(p1) + getCardValue(p2)
        if (playerScore > 21 && (p1.startsWith("A") || p2.startsWith("A"))) playerScore -= 10
        if(playerScore ==21) {
            showBlackjackOverlay()
        } else if(dealerScore==21) {
            showBlackjackOverlay()
            return
            //TODO see if that worked fully.
        }


        // Dealer: one visible, one hidden
        val d1 = deck.removeAt(0)
        val d2 = deck.removeAt(0)

        dealerScore += getCardValue(d1)
        addDealerCard(d1, hidden = false)

        // Create hidden ImageView (always same size as others)
        val iv = ImageView(this)
        iv.setImageResource(R.drawable.card_back)

        val lp = ViewGroup.MarginLayoutParams(dpToPx(72), dpToPx(100))
        lp.setMargins(8, 0, 8, 0)
        iv.layoutParams = lp

        // ensure hidden card becomes second child (index 1)
        binding.dealerCards.addView(iv, 1)
        animateCardIn(iv)

        dealerHiddenCardCode = d2
        dealerHiddenImageView = iv

        updateScoreOnUI()
    }

    // -------------------- Hit --------------------
    private fun onHitClicked() {
        hitCardToPlayer()
        updateScoreOnUI()
    }

    private fun hitCardToPlayer() {
        if (deck.isEmpty()) {
            createDeck()
            shuffleDeck()
        }

        val card = deck.removeAt(0)
        playerScore += getCardValue(card)
        if (playerScore > 21 && card.startsWith("A")) playerScore -= 10

        addCardImageToLayout(card, binding.playerCards)
    }

    // -------------------- Stand (dealer sequence) --------------------
    private fun onStandClicked() {
        dealerPlay()
    }

    private fun dealerPlay() {
        // 1) Flip hidden card after short delay so player sees it
        handler.postDelayed({
            dealerHiddenCardCode?.let { code ->
                dealerHiddenImageView?.let { iv ->
                    val realRes = resources.getIdentifier(getCardDrawableName(code), "drawable", packageName)
                    if (realRes != 0) flipCard(iv, realRes)
                    // update dealer score with hidden card
                    dealerScore += getCardValue(code)
                    if (dealerScore > 21 && code.startsWith("A")) dealerScore -= 10
                    updateScoreOnUI()
                }
            }

            dealerHiddenCardCode = null
            dealerHiddenImageView = null

            // 2) After a small pause start drawing dealer cards sequentially
            handler.postDelayed({
                drawDealerCardsSequentially()
            }, 600)

        }, 600)
    }

    private fun drawDealerCardsSequentially() {
        // Dealer stops at 17 or more
        if (dealerScore >= 17) {
            // wait a little and then show result
            handler.postDelayed({ checkRoundResult() }, 700)
            return
        }

        if (deck.isEmpty()) {
            createDeck()
            shuffleDeck()
        }

        val card = deck.removeAt(0)
        dealerScore += getCardValue(card)
        if (dealerScore > 21 && card.startsWith("A")) dealerScore -= 10

        addDealerCard(card, hidden = false)
        updateScoreOnUI()

        // continue after delay
        handler.postDelayed({ drawDealerCardsSequentially() }, 700)
    }

    // -------------------- Check result & money --------------------
    private fun checkRoundResult() {
        when {
            playerScore > 21 -> {
                playerMoney -= currentBet
                binding.playerMoney.text = "Money: $$playerMoney"
                showLoseOverlay()
            }
            dealerScore > 21 || playerScore > dealerScore -> {
                playerMoney += currentBet
                binding.playerMoney.text = "Money: $$playerMoney"
                showWinOverlay()
            }
            else -> {
                playerMoney -= currentBet
                binding.playerMoney.text = "Money: $$playerMoney"
                showLoseOverlay()
            }
        }
    }

    private fun goToWin() {
        binding.playerMoney.text = "Money: $$playerMoney"
        startWinsActivity()
    }

    private fun goToLose() {
        binding.playerMoney.text = "Money: $$playerMoney"
        startLooseActivity()
    }
    //Quit game
    private fun showQuitDialog() {
        AlertDialog.Builder(this)
            .setTitle("Quit Game?")
            .setMessage("Are you sure you want to quit?")
            .setPositiveButton("Yes") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }



    // Win Overlay

    private fun showWinOverlay() {
        val overlay = binding.winOverlay
        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        overlay.scaleX = 0.8f
        overlay.scaleY = 0.8f

        overlay.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .withEndAction {

                handler.postDelayed({
                    overlay.animate()
                        .alpha(0f)
                        .setDuration(400)
                        .withEndAction {
                            overlay.visibility = View.GONE
                            askForBet()
                        }.start()
                }, 700)
            }.start()
    }

    private fun showLoseOverlay() {
        val overlay = binding.loseOverlay
        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        overlay.scaleX = 0.8f
        overlay.scaleY = 0.8f

        overlay.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .withEndAction {

                handler.postDelayed({
                    overlay.animate()
                        .alpha(0f)
                        .setDuration(400)
                        .withEndAction {
                            overlay.visibility = View.GONE

                            if (playerMoney < 10) {
                                startLooseActivity()
                            } else {
                                askForBet()
                            }
                        }.start()
                }, 700)
            }.start()
    }

    private fun showBlackjackOverlay() {
        val overlay = binding.blackjackOverlay
        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        overlay.scaleX = 0.8f
        overlay.scaleY = 0.8f

        overlay.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .withEndAction {

                handler.postDelayed({
                    overlay.animate()
                        .alpha(0f)
                        .setDuration(400)
                        .withEndAction {
                            overlay.visibility = View.GONE

                            //Black Jack Payout

                            playerMoney += (currentBet * 2)
                            binding.playerMoney.text = "Money: $$playerMoney"

                            //Start Next Round
                            askForBet()
                        }
                        .start()
                }, 800)
            }
            .start()
    }
    private fun showNoMoneyDialog() {
        AlertDialog.Builder(this)
            .setTitle("Game Over")
            .setMessage("You don't have enough money to continue!")
            .setPositiveButton("Back to menu") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    // -------------------- UI helpers --------------------
    private fun updateScoreOnUI() {
        binding.playerScore.text = "Player: $playerScore"
        binding.dealerScore.text = "Dealer: $dealerScore"
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun addDealerCard(cardCode: String, hidden: Boolean) {
        val iv = ImageView(this)
        val resName = if (hidden) "card_back" else getCardDrawableName(cardCode)
        val resId = resources.getIdentifier(resName, "drawable", packageName)
        iv.setImageResource(if (resId != 0) resId else R.drawable.card_back)

        // make sure scaleType keeps consistent appearance
        iv.scaleType = ImageView.ScaleType.CENTER_INSIDE

        val lp = ViewGroup.MarginLayoutParams(dpToPx(72), dpToPx(100))
        lp.setMargins(8, 0, 8, 0)
        iv.layoutParams = lp

        binding.dealerCards.addView(iv)
        animateCardIn(iv)
    }

    private fun addCardImageToLayout(cardCode: String, container: ViewGroup) {
        val iv = ImageView(this)
        val name = getCardDrawableName(cardCode)
        val id = resources.getIdentifier(name, "drawable", packageName)
        iv.setImageResource(if (id != 0) id else R.drawable.card_back)

        iv.scaleType = ImageView.ScaleType.CENTER_INSIDE
        val lp = ViewGroup.MarginLayoutParams(dpToPx(72), dpToPx(100))
        lp.setMargins(8, 0, 8, 0)
        iv.layoutParams = lp

        container.addView(iv)
        animateCardIn(iv)
    }

    private fun flipCard(imageView: ImageView, realResId: Int) {
        imageView.animate().scaleX(0f).setDuration(150).withEndAction {
            imageView.setImageResource(realResId)
            imageView.animate().scaleX(1f).setDuration(150).start()
        }.start()
    }

    private fun startWinsActivity() {
        val i = Intent(this, WinsActivity::class.java)
        i.putExtra("playerScore", playerScore)
        i.putExtra("dealerScore", dealerScore)
        i.putExtra("blackjack", false)
        startActivity(i)
        //finish()
    }

    private fun startLooseActivity() {
        val i = Intent(this, LooseActivity::class.java)
        i.putExtra("playerScore", playerScore)
        i.putExtra("dealerScore", dealerScore)
        startActivity(i)
        //finish()
    }

    private fun animateCardIn(v: ImageView) {
        v.scaleX = 0f
        v.scaleY = 0f
        v.alpha = 0f
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start()
    }

    // -------------------- Deck utilities --------------------
    private fun createDeck() {
        deck.clear()
        val ranks = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")
        val suits = listOf("H", "D", "C", "S")
        for (s in suits) {
            for (r in ranks) {
                deck.add(r + s)
            }
        }
    }

    private fun shuffleDeck() {
        // simple shuffle
        deck.shuffle(Random(System.currentTimeMillis()))
    }

    private fun getCardValue(cardCode: String): Int {
        val rank = cardCode.dropLast(1)
        return when (rank) {
            "A" -> 11
            "K", "Q", "J" -> 10
            else -> rank.toIntOrNull() ?: 0
        }
    }

    private fun getCardDrawableName(cardCode: String): String {
        val rank = cardCode.dropLast(1).lowercase() // "a", "10", "q"
        val suitChar = cardCode.last()
        val suit = when (suitChar) {
            'H' -> "h"
            'D' -> "d"
            'C' -> "c"
            'S' -> "s"
            else -> "back"
        }
        return "card_${rank}_$suit"
    }
}