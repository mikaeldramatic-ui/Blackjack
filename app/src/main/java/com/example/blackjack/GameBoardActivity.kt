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

    // Game - Variables
    private var playerScore = 0
    private var dealerScore = 0
    private val deck = mutableListOf<String>() // e.g. "AH", "10S", "QC"

   private var dealerHiddenCardCode: String? =null
    private var dealerHiddenImageView: ImageView? = null

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

        val rankLower = rank.lowercase()

        val suitLetter = when (suit) {
            'H' -> "h"
            'D' -> "d"
            'C' -> "c"
            'S' -> "s"
            else -> "back"
        }

        return "card_${rankLower}_${suitLetter}"
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

        //Reset views & scores

        binding.playerCards.removeAllViews()
        binding.dealerCards.removeAllViews()
        playerScore = 0
        dealerScore = 0

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
        //Blackjack Check
        if (playerScore ==21) {
            showBlackjackAnimation {
                val intent = Intent(this, WinsActivity::class.java)
                intent.putExtra("playerScore", playerScore)
                intent.putExtra("dealerScore", dealerScore)
                intent.putExtra("blackjack", true)
                startActivity(intent)
                finish()
            }
            return
        }

        // Dealer gets 1 visible and 1 hidden card
        val dealerCard1 = deck.removeAt(0) // visible
        val dealerCard2 = deck.removeAt(0) // hidden

        dealerScore += getCardValue(dealerCard1)

        // Add visible card
        addDealerCard(dealerCard1, hidden = false)

        //Create and add hidden ImageView

        val iv= ImageView(this)
        iv.setImageResource(R.drawable.card_back)
        val lp = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(8,0,8,0)
        iv.layoutParams = lp
                binding.dealerCards.addView(iv)
        animateCardIn(iv)

        dealerHiddenCardCode = dealerCard2
        dealerHiddenImageView = iv
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
        dealerHiddenCardCode?.let { hiddenCode ->
            dealerHiddenImageView?.let { hiddenIv ->
                val drawableName = getCardDrawableName(hiddenCode)
                val realResId = resources.getIdentifier(drawableName, "drawable", packageName)

                if (realResId !=0) {
                    //Flip animation then set real image
                    flipCard(hiddenIv, realResId)
                } else {
                    //Fallback if resID missing
                    hiddenIv.setImageResource(R.drawable.card_back)
                }

                //Update Dealer score with that hidden card
                dealerScore += getCardValue(hiddenCode)
                if (dealerScore > 21 && hiddenCode.startsWith("A")) {
                    dealerScore -= 10
                }
                updateScoreOnUI()

            } ?: run {
                //If we don't have image  (edge case) remove view at 1 and add real card
                if (binding.dealerCards.childCount >1) {
                    binding.dealerCards.removeViewAt(1)
                }
                addDealerCard(hiddenCode, hidden = false)
                dealerScore += getCardValue(hiddenCode)
                if (dealerScore >21 && hiddenCode.startsWith("A")) dealerScore -=10
                updateScoreOnUI()

            }
        }

        //Hidden card has been shown
        dealerHiddenCardCode = null
        dealerHiddenImageView = null

        //Dealer draws until 17
        while ( dealerScore < 17) {
            if (deck.isEmpty()) {
                createDeck()
                shuffleDeck()
            }
            val card = deck.removeAt(0)
            val value = getCardValue(card)
            dealerScore += value

            //Ace fix for dealer

            if (dealerScore > 21 && card.startsWith("A")) {
                dealerScore -= 10
            }

            addCardImageToLayout(card, binding.dealerCards)
            updateScoreOnUI()
        }
    }

    private fun checkRoundResult(){
        //Controlling who won after game finished

        //Player bust
        if(playerScore >21) {
            startLooseActivity()
            return
        }

        //Dealer bust
        if(dealerScore >21 || playerScore > dealerScore) {
            startWinsActivity()
            return
        }

        //Else the dealer wins
        (startLooseActivity())
    }

    // ------------- UI helper --------------
    private fun updateScoreOnUI() {
        binding.playerScore.text = "Player: $playerScore"
        binding.dealerScore.text = "Dealer: $dealerScore"
    }

    private fun dpToPx(dp: Int): Int{
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
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

    private fun dpTopx(dp: Int) : Int{
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun addCardImageToLayout(cardCode: String, container: ViewGroup) {
        val iv = ImageView(this)
        val drawableName = getCardDrawableName(cardCode)
        val resId = resources.getIdentifier(drawableName, "drawable", packageName)
        if (resId == 0) {
            // fallback och logg
            Log.w("GameBoard", "Drawable not found: $drawableName for cardCode=$cardCode")
        }
        iv.setImageResource(if (resId != 0) resId else R.drawable.card_back)

        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        val lp = ViewGroup.MarginLayoutParams(
            dpToPx(72), // Width in Dp -adjusment after how big card i want
            dpToPx(100) //Height in dp
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
        intent.putExtra("blackjack", false)
        startActivity(intent)
        finish()
    }

    private fun startLooseActivity() {
        val intent = Intent(this, LooseActivity::class.java)
        intent.putExtra("playerScore", playerScore)
        intent.putExtra("dealerScore", dealerScore)
        startActivity(intent)
        finish()
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

    private fun showBlackjackAnimation(onFinish: () -> Unit) {
        val overlay = binding.blackjackOverlay
        overlay.visibility = View.VISIBLE
        overlay.scaleX = 0f
        overlay.scaleY = 0f
        overlay.alpha = 0f

        overlay.animate()
            .alpha(1f)
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(400)
            .withEndAction {
                overlay.animate()
                    .alpha(0f)
                    .setDuration(500)
                    .withEndAction {
                        overlay.visibility = View.GONE
                        onFinish()
                    }
                    .start()
            }
            .start()
    }
}