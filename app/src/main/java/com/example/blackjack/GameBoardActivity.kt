package com.example.blackjack

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.view.ViewGroup
import android.widget.ImageView
import com.example.blackjack.databinding.ActivityGameboardBinding

class GameBoardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameboardBinding

    // Game variables
    private var playerScore = 0
    private var dealerScore = 0
    private val deck = mutableListOf<String>()

    private var dealerHiddenCardCode: String? = null
    private var dealerHiddenImageView: ImageView? = null

    private val handler = android.os.Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.playerScore.text = "Player: 0"
        binding.dealerScore.text = "Dealer: 0"

        binding.btnHit.setOnClickListener { onHitClicked() }
        binding.btnStand.setOnClickListener { onStandClicked() }
        binding.btnQuit.setOnClickListener { showQuitDialog() }

        createDeck()
        shuffleDeck()
        dealInitialCards()
    }

    // --- UI Handlers ---
    private fun onHitClicked() {
        hitCardToPlayer()
        updateScoreOnUI()
    }

    private fun onStandClicked() {
        dealerPlay()
    }

    private fun showQuitDialog() {
        val b = AlertDialog.Builder(this)
        b.setTitle("Quit Game?")
        b.setMessage("Are you sure you want to quit?")
        b.setPositiveButton("Yes") { d: DialogInterface, _ ->
            d.dismiss()
            finish()
        }
        b.setNegativeButton("No") { d, _ -> d.dismiss() }
        b.show()
    }

    // --- Card image naming ---
    private fun getCardDrawableName(cardCode: String): String {
        val rank = cardCode.dropLast(1).lowercase()
        val suit = when (cardCode.last()) {
            'H' -> "h"
            'D' -> "d"
            'C' -> "c"
            'S' -> "s"
            else -> "back"
        }
        return "card_${rank}_${suit}"
    }

    // --- Deck ---
    private fun createDeck() {
        deck.clear()
        val ranks = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")
        val suits = listOf("H", "D", "C", "S")
        for (s in suits) for (r in ranks) deck.add(r + s)
    }

    private fun shuffleDeck() {
        deck.shuffle()
    }

    private fun getCardValue(cardCode: String): Int {
        return when (val rank = cardCode.dropLast(1)) {
            "A" -> 11
            "K", "Q", "J" -> 10
            else -> rank.toInt()
        }
    }

    // --- Deal initial cards ---
    private fun dealInitialCards() {
        binding.playerCards.removeAllViews()
        binding.dealerCards.removeAllViews()

        playerScore = 0
        dealerScore = 0

        if (deck.size < 4) {
            createDeck()
            shuffleDeck()
        }

        val p1 = deck.removeAt(0)
        val p2 = deck.removeAt(0)

        addCardImageToLayout(p1, binding.playerCards)
        addCardImageToLayout(p2, binding.playerCards)

        playerScore += getCardValue(p1) + getCardValue(p2)
        if (playerScore > 21 && (p1.startsWith("A") || p2.startsWith("A"))) playerScore -= 10

        val d1 = deck.removeAt(0)
        val d2 = deck.removeAt(0)

        dealerScore += getCardValue(d1)

        addDealerCard(d1, hidden = false)

        // Add hidden dealer card
        val iv = ImageView(this)
        iv.setImageResource(R.drawable.card_back)

        val lp = ViewGroup.MarginLayoutParams(
            dpToPx(72),
            dpToPx(100)
        )
        lp.setMargins(8, 0, 8, 0)
        iv.layoutParams = lp

        binding.dealerCards.addView(iv, 1)
        animateCardIn(iv)

        dealerHiddenCardCode = d2
        dealerHiddenImageView = iv

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

    // --- Dealer logic ---
    private fun dealerPlay() {

        // 1. dealer shows hiddencard after a while
        handler.postDelayed({

            dealerHiddenCardCode?.let { code ->
                dealerHiddenImageView?.let { iv ->
                    val realRes = resources.getIdentifier(
                        getCardDrawableName(code), "drawable", packageName
                    )
                    if (realRes != 0) flipCard(iv, realRes)

                    dealerScore += getCardValue(code)
                    if (dealerScore > 21 && code.startsWith("A")) dealerScore -= 10
                    updateScoreOnUI()
                }
            }

            dealerHiddenCardCode = null
            dealerHiddenImageView = null

            // 2. dealer start with drag
            handler.postDelayed({

                drawDealerCardsSequentially()

            }, 600)

        }, 600)
    }

    private fun drawDealerCardsSequentially() {
        if(dealerScore > 17) {
            //Done and shows results after card pause
            handler.postDelayed({checkRoundResult()},700)
            return
        }

        if (deck.isEmpty()) {
            createDeck()
            shuffleDeck()
        }

        val card = deck.removeAt(0)
        dealerScore += getCardValue(card)
        if(dealerScore > 21 && card.startsWith("A")) dealerScore -=10

        addDealerCard(card, hidden = false)
        updateScoreOnUI()

        //Draw next card after 600ms
        handler.postDelayed({
            checkRoundResult()
        }, 700 )
    }

    private fun checkRoundResult() {
        when {
            playerScore > 21 -> startLooseActivity()
            dealerScore > 21 || playerScore > dealerScore -> startWinsActivity()
            else -> startLooseActivity()
        }
    }

    // --- UI helpers ---
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

        iv.scaleType = ImageView.ScaleType.CENTER_INSIDE


        val lp = ViewGroup.MarginLayoutParams(
            dpToPx(72),   // width
            dpToPx(100)   // height
        )
        lp.setMargins(8, 0, 8, 0)
        iv.layoutParams = lp

        binding.dealerCards.addView(iv)
        animateCardIn(iv)
    }
    // -----------------------------------------------

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
        finish()
    }

    private fun startLooseActivity() {
        val i = Intent(this, LooseActivity::class.java)
        i.putExtra("playerScore", playerScore)
        i.putExtra("dealerScore", dealerScore)
        startActivity(i)
        finish()
    }

    private fun animateCardIn(v: ImageView) {
        v.scaleX = 0f
        v.scaleY = 0f
        v.alpha = 0f
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start()
    }
}