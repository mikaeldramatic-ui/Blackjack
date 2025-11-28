package com.example.blackjack

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.view.ViewGroup
import android.widget.ImageView
import com.example.blackjack.databinding.ActivityGameboardBinding

class GameBoardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameboardBinding

    // Game - Variables
    private var playerScore = 0
    private var dealerScore = 0
    private val deck = mutableListOf<String>() // e.g. "AH", "10S", "QC"

    private var dealerHiddenCard: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Init UI With StartValue
        binding.playerScore.text = "Player: 0"
        binding.dealerScore.text = "Dealer: 0"

        // Buttons
        binding.btnHit.setOnClickListener { onHitClicked() }
        binding.btnStand.setOnClickListener { onStandClicked() }
        binding.btnQuit.setOnClickListener { showQuitDialog() }

        // Prepare game (deck cards and so on...)
        createDeck()
        shuffleDeck()

        // Deal initial cards
        dealInitialCards()
    }

    // --------------- UI - Handlers -------------
    private fun onHitClicked() {
        hitCardToPlayer()
        updateScoreOnUI()
    }

    private fun onStandClicked() {
        dealerPlay()
        updateScoreOnUI()
        checkRoundResult()
    }

    private fun showQuitDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Quit Game?")
        builder.setMessage("Are you sure you want to quit the current game?")
        builder.setPositiveButton("Yes") { dialog: DialogInterface, _: Int ->
            dialog.dismiss()
            finish()
        }
        builder.setNegativeButton("No") { dialog: DialogInterface, _: Int ->
            dialog.dismiss()
        }
        builder.setCancelable(true)
        builder.show()
    }

    // ---------------- Card names ----------------
    private fun getCardDrawableName(cardCode: String): String {
        val rank = cardCode.dropLast(1) // "A", "10", "Q"
        val suit = cardCode.last()      // 'H','C','D','S' -> Char

        val rankName = when (rank) {
            "A" -> "ace"
            "J" -> "jack"
            "Q" -> "queen"
            "K" -> "king"   // lowercase for consistency
            else -> rank    // 2..10
        }

        val suitName = when (suit) {
            'H' -> "hearts"
            'D' -> "diamonds"
            'C' -> "clubs"
            'S' -> "spades"
            else -> "back"
        }

        return "card_${rankName}_of_${suitName}"
    }

    // -------------- Game functions --------------
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

    private fun getCardValue(cardCode: String): Int {
        val rank = cardCode.dropLast(1) // "A","10","K" etc.

        return when (rank) {
            "A" -> 11
            "K", "Q", "J" -> 10
            else -> rank.toInt()
        }
    }

    private fun shuffleDeck() {
        deck.shuffle()
    }

    private fun dealInitialCards() {
        // Ensure deck has enough cards
        if (deck.size < 4) {
            createDeck()
            shuffleDeck()
        }

        // Player gets 2 cards
        val playerCard1 = deck.removeAt(0)
        val playerCard2 = deck.removeAt(0)

        addCardImageToLayout(playerCard1, binding.playerCards)
        addCardImageToLayout(playerCard2, binding.playerCards)

        playerScore += getCardValue(playerCard1)
        playerScore += getCardValue(playerCard2)

        // Fix Aces for player
        if (playerScore > 21 && (playerCard1.startsWith("A") || playerCard2.startsWith("A"))) {
            playerScore -= 10
        }

        // Dealer gets 1 visible and 1 hidden card
        val dealerCard1 = deck.removeAt(0) // visible
        val dealerCard2 = deck.removeAt(0) // hidden

        dealerScore += getCardValue(dealerCard1)

        // Add visible card
        addDealerCard(dealerCard1, hidden = false)

        // Hidden card (face down)
        dealerHiddenCard = dealerCard2
        addDealerCard(dealerCard2, hidden = true)

        updateScoreOnUI()
    }

    private fun hitCardToPlayer() {
        if (deck.isEmpty()) {
            createDeck()
            shuffleDeck()
        }
        val card = deck.removeAt(0)

        val value = getCardValue(card)
        playerScore += value

        // ACE fix (if player goes above 21)
        if (playerScore > 21 && card.startsWith("A")) {
            playerScore -= 10
        }
        addCardImageToLayout(card, binding.playerCards)
    }

    private fun dealerPlay() {
        // Flip dealer's hidden card (if any)
        dealerHiddenCard?.let { hidden ->
            // Get the face-down card (second card)
            val hiddenView = binding.dealerCards.getChildAt(1)
            if (hiddenView is ImageView) {
                val hiddenCardView = hiddenView

                val drawableName = getCardDrawableName(hidden)
                val realResId = resources.getIdentifier(drawableName, "drawable", packageName)

                // Flip animation (flipCard must exist)
                flipCard(hiddenCardView, realResId)

                // Update dealer score with that hidden card
                dealerScore += getCardValue(hidden)
                if (dealerScore > 21 && hidden.startsWith("A")) {
                    dealerScore -= 10
                }
            } else {
                // Fallback: if not an ImageView, remove and add real card
                binding.dealerCards.removeViewAt(1)
                addDealerCard(hidden, hidden = false)
                dealerScore += getCardValue(hidden)
                if (dealerScore > 21 && hidden.startsWith("A")) {
                    dealerScore -= 10
                }
            }
        }

        // Hidden card has been shown
        dealerHiddenCard = null

        // Dealer draws until 17
        while (dealerScore < 17) {
            if (deck.isEmpty()) {
                createDeck()
                shuffleDeck()
            }

            val card = deck.removeAt(0)
            val value = getCardValue(card)
            dealerScore += value

            // ACE fix for dealer
            if (dealerScore > 21 && card.startsWith("A")) {
                dealerScore -= 10
            }

            addCardImageToLayout(card, binding.dealerCards)
        }
    }

    private fun checkRoundResult() {
        if (playerScore > 21) {
            startLooseActivity()
        } else if (dealerScore > 21 || playerScore > dealerScore) {
            startWinsActivity()
        } else if (playerScore == dealerScore) {
            // Push - Tie (handle as you want)
        } else {
            startLooseActivity()
        }
    }

    // ------------- UI helper --------------
    private fun updateScoreOnUI() {
        binding.playerScore.text = "Player: $playerScore"
        binding.dealerScore.text = "Dealer: $dealerScore"
    }

    private fun addDealerCard(cardCode: String, hidden: Boolean) {
        val iv = ImageView(this)

        if (hidden) {
            iv.setImageResource(R.drawable.card_back)
        } else {
            val drawableName = getCardDrawableName(cardCode)
            val resId = resources.getIdentifier(drawableName, "drawable", packageName)
            iv.setImageResource(if (resId != 0) resId else R.drawable.card_back)
        }

        val lp = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(8, 0, 8, 0)
        iv.layoutParams = lp

        binding.dealerCards.addView(iv)
        animateCardIn(iv)
    }

    private fun addCardImageToLayout(cardCode: String, container: ViewGroup) {
        val iv = ImageView(this)
        val drawableName = getCardDrawableName(cardCode)
        val resId = resources.getIdentifier(drawableName, "drawable", packageName)
        iv.setImageResource(if (resId != 0) resId else R.drawable.card_back)

        val lp = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(8, 0, 8, 0)
        iv.layoutParams = lp
        container.addView(iv)

        animateCardIn(iv)
    }

    private fun flipCard(imageView: ImageView, realResId: Int) {
        imageView.animate()
            .scaleX(0f)
            .setDuration(150)
            .withEndAction {
                imageView.setImageResource(realResId)
                imageView.animate()
                    .scaleX(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    private fun startWinsActivity() {
        val intent = Intent(this, WinsActivity::class.java)
        intent.putExtra("playerScore", playerScore)
        intent.putExtra("dealerScore", dealerScore)
        startActivity(intent)
    }

    private fun startLooseActivity() {
        val intent = Intent(this, LooseActivity::class.java)
        intent.putExtra("playerScore", playerScore)
        intent.putExtra("dealerScore", dealerScore)
        startActivity(intent)
    }

    private fun animateCardIn(view: ImageView) {
        view.scaleX = 0f
        view.scaleY = 0f
        view.alpha = 0f

        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .start()
    }
}