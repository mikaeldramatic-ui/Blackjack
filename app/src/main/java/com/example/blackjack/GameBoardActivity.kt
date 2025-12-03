package com.example.blackjack

import android.R.attr.type
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

    //Split mode
    private var isSplit = false
    private var isPlayingHand1 = true

    private var hand1Score = 0
    private var hand2Score = 0

    private var hand1Cards= mutableListOf<String>()
    private var hand2Cards= mutableListOf<String>()

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
        binding.btnSplit.setOnClickListener { onSplitClicked() }
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

        val bets = arrayOf("10", "20", "50", "100", "Fold")

        AlertDialog.Builder(this)
            .setTitle("Place Your Bet")
            .setItems(bets) { _, which ->

                //Player chose to Fold

                if (bets[which] == "Fold") {
                    //OR go back to menu, just write StartActivity(Intent(this, Main  'Which activity like menu, highscore etc'::Class.java
                    finish()
                    return@setItems
                }
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

        enablePlayerButtons()
        binding.btnSplit.visibility = View.GONE
        binding.btnSplit.isEnabled = false

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

        //Initial Split hand container (Logical not Ui)
        hand1Cards.clear()
        hand2Cards.clear()
        hand1Cards.add(p1)
        hand2Cards.add(p2)

        //Initialize hand scores for potential split usage

        hand1Score=getCardValue(p1)
        hand2Score=getCardValue(p2)

        //Ace Handling
        if (playerScore > 21 && (p1.startsWith("A") || p2.startsWith("A"))) playerScore -= 10

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

        // Offer Split if applicable (only if no Blackjack)
        checkForSplitOption(p1,p2)


        //Black Jack Logic//

        val playerBlackjack = (playerScore == 21)
        val dealerBlackjack = (getCardValue(cardCode = d1) + getCardValue(d2) == 21)

        //IF no one has Black Jack - game begins
        if (!playerBlackjack && !dealerBlackjack) return

        //Disable button because Round is over Immediately
        disablePlayerButtons()

        //Hide Split-button
        binding.btnSplit.visibility = View.GONE
        binding.btnSplit.isEnabled = false

        //IF Both-> PUSH
        if (playerBlackjack && dealerBlackjack) {
            handleBlackjackOutcome("push")
            return
        }
        //IF player wins with Black jack
        if (playerBlackjack) {
            handleBlackjackOutcome("player")
            return
        }
        //IF dealer wins with Black jack
        if (dealerBlackjack) {
            handleBlackjackOutcome("dealer")
            return
        }
    }

    private fun checkForSplitOption(card1: String, card2: String) {

        val rank1 = card1.dropLast(1)
        val rank2 = card2.dropLast(1)

        val value1 = getCardValue(card1)
        val value2 = getCardValue(card2)

        //Rule : split ANY 10-value
        val bothAre10Value = (value1 ==10 && value2 == 10)

        //Rule : Same Rank (A+A, 8+8 , Q+Q, etc)
        val sameRank = rank1 == rank2

        //Show split button
        if(bothAre10Value || sameRank) {
            binding.btnSplit.visibility = View.VISIBLE
            binding.btnSplit.isEnabled = true
        } else {
            binding.btnSplit.visibility = View.GONE
            binding.btnSplit.isEnabled = false
        }
    }
    private fun handleBlackjackOutcome(result: String) {
        // Flip dealer hidden card immediately so player can see it
        dealerHiddenCardCode?.let { code ->
            dealerHiddenImageView?.let { iv ->
                val realRes = resources.getIdentifier(getCardDrawableName(code), "drawable", packageName)
                if (realRes != 0) flipCard(iv, realRes)
            }
        }

        dealerHiddenCardCode = null
        dealerHiddenImageView = null

        when (result) {
            "push" -> {
                // No money changes
                showBlackjackOverlay("push")
            }
            "player" -> {
                // Blackjack payout: player receives back bet + 1.5x = total +2.5*bet
                val amount = (currentBet * 2.5).toInt()
                playerMoney += amount
                binding.playerMoney.text = "Money: $$playerMoney"
                showBlackjackOverlay("player", amount)
            }
            "dealer" -> {
                // Dealer blackjack → player loses bet
                playerMoney -= currentBet
                binding.playerMoney.text = "Money: $$playerMoney"
                showBlackjackOverlay("dealer")
            }
        }
    }

    // -------------------- Hit --------------------
    private fun onHitClicked() {
                //IF not split- normal hi
        if  (!isSplit) {
            hitCardToPlayer()
            updateScoreOnUI()
            if (playerScore >21) {
                disablePlayerButtons()
                handler.postDelayed({ checkRoundResult()}, 500)
            }
            return
        }
                //Split Mode
        if(isPlayingHand1) {
            hitSplitHand1()
            updateScoreOnUI()
            updateSplitHandHighLight()

            if(hand1Score >21) {
                isPlayingHand1 = false
                binding.playerHand1.alpha = 0.5f
            }
        } else {
            hitSplitHand2()
            updateScoreOnUI()
            updateSplitHandHighLight()

            if(hand2Score >21) {
                //Both hands done - Dealer plays
                disablePlayerButtons()
                handler.postDelayed({dealerPlayAfterSplit()}, 700)
            }
        }

    }
    private fun onSplitClicked() {
        if (isSplit) return //Already split

        isSplit = true
        isPlayingHand1 = true

        //Deduct bet for second hand
        playerMoney -= currentBet
        binding.playerMoney.text = "Money: $$playerMoney"

        //Hide original container, show split
        binding.playerCards.visibility = View.GONE
        binding.playerHand1.visibility = View.VISIBLE
        binding.playerHand2.visibility = View.VISIBLE

        //Clear UI and place one card in each hand
        binding.playerHand1.removeAllViews()
        binding.playerHand2.removeAllViews()

        addCardImageToLayout(hand1Cards[0],binding.playerHand1)
        addCardImageToLayout(hand2Cards[0], binding.playerHand2)

        //Highlight active hand
        binding.playerHand1.alpha = 1f
        binding.playerHand2.alpha = 0.4f

        updateScoreOnUI()
        updateSplitHandHighLight()

    }

                //Split Hits

    // First hand
    private fun hitSplitHand1() {
        if (deck.isEmpty()) { createDeck(); shuffleDeck() }
        val card = deck.removeAt(0)
        hand1Cards.add(card)
        addCardImageToLayout(card, binding.playerHand1)

        hand1Score += getCardValue(card)

        val firstCard = hand1Cards.first()
        if (firstCard.startsWith("A")) {
            // Hand 1 finish immediately
            isPlayingHand1 = false
            updateSplitHandHighLight()
            binding.playerHand1.alpha = 0.5f
        }
    }

    // Second hand
    private fun hitSplitHand2() {
        if (deck.isEmpty()) { createDeck(); shuffleDeck() }
        val card = deck.removeAt(0)
        hand2Cards.add(card)
        addCardImageToLayout(card, binding.playerHand2)

        hand2Score += getCardValue(card)

        val firstCard = hand2Cards.first()
        if (firstCard.startsWith("A")) {
            disablePlayerButtons()
            handler.postDelayed({ dealerPlayAfterSplit() }, 700)
        }
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
        //Normal game (no split)
        if(!isSplit) {
            dealerPlay()
            return
        }

        //Split mode

        if(isPlayingHand1) {
            //Move to hand 2
            isPlayingHand1 = false
            updateSplitHandHighLight()
            return
        }

        //If already on hand 2 - Dealer plays
        disablePlayerButtons()
        handler.postDelayed({ dealerPlayAfterSplit()}, 600)
    }

    //Dealer play after split

    private fun dealerPlayAfterSplit() {
                //Reveal hidden card
        dealerHiddenCardCode?.let {code ->
            dealerHiddenImageView?.let {iv ->
                val realRes = resources.getIdentifier(getCardDrawableName(code),"drawable",packageName)
                if (realRes !=0) flipCard(iv,realRes)
            }
        }
        dealerScore += dealerHiddenCardCode?.let {getCardValue(it) } ?:0
        dealerHiddenCardCode = null
        dealerHiddenImageView = null

        updateScoreOnUI()

        //Standard dealer logic

        handler.postDelayed({
            drawDealerCardsSequentiallyAfterSplit()
        }, 600)
    }

    private fun drawDealerCardsSequentiallyAfterSplit() {
        if (dealerScore >=17) {
            //Evaluate split hands
            handler.postDelayed({evaluateSplitResults() },700)
            return
        }

        val card = deck.removeAt(0)
        dealerScore += getCardValue(card)
        addDealerCard(card, hidden = false)
        updateScoreOnUI()

        handler.postDelayed({drawDealerCardsSequentiallyAfterSplit()}, 700)
    }

    private fun evaluateSplitResults() {

        //Result hand by hand
        val result1 = evaluateSingleHand(hand1Score)
        val result2 = evaluateSingleHand(hand2Score)

        var totalWin = 0

        if(result1 > 0) totalWin +=currentBet
        if(result2 > 0) totalWin +=currentBet
        if(result1 < 0) totalWin -=currentBet
        if(result2 < 0) totalWin -=currentBet

        playerMoney += totalWin
        binding.playerMoney.text = "Money: $$playerMoney"

        //Shows overlays depends outcome
        when {
            totalWin > 0 -> showWinOverlay()
            totalWin < 0 -> showLoseOverlay()
            else -> showBlackjackOverlay("push")
        }

        //Reset Split mode
        isSplit = false
        isPlayingHand1 = true

        binding.playerHand1.visibility = View.GONE
        binding.playerHand2.visibility = View.GONE

    }

    private fun evaluateSingleHand (score: Int): Int {

        return when {
            score > 21 -> -1        //Bust
            dealerScore > 21 -> 1   //Dealer Bust
            score > dealerScore -> 1
            score < dealerScore -> -1
            else -> 0
        }

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
        binding.winTotalMoney.text= "Money: $$playerMoney"
        binding.winAmountText.text = "+$currentBet"
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
        binding.loseTotalMoney.text="Money: $$playerMoney"
        binding.loseAmountText.text ="-$currentBet"
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

    private fun showBlackjackOverlay(type: String, winAmount: Int = 0) {
        val overlay = binding.blackjackOverlay

        when (type) {
            "player" -> {
                binding.blackjackTitle.text = "BLACKJACK!"
                binding.blackjackAmount.text = "+$winAmount"
                binding.blackjackTotal.text = "Money: $$playerMoney"
            }
            "dealer" -> {
                binding.blackjackTitle.text = "DEALER BLACKJACK"
                binding.blackjackAmount.text = "-$currentBet"
                binding.blackjackTotal.text = "Money: $$playerMoney"
            }
            "push" -> {
                binding.blackjackTitle.text = "PUSH"
                binding.blackjackAmount.text = "+0"
                binding.blackjackTotal.text = "Money: $$playerMoney"
            }
        }

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
                }, 900)
            }.start()
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

                // Buttons come back after Bust

    private fun disablePlayerButtons(){
        binding.btnHit.isEnabled = false
        binding.btnStand.isEnabled = false
    }

    private fun enablePlayerButtons(){
        binding.btnHit.isEnabled= true
        binding.btnStand.isEnabled = true
    }


    private fun updateScoreOnUI() {
        binding.playerScore.text = "Player: $playerScore"
        binding.dealerScore.text = "Dealer: $dealerScore"
    }

    private fun updateSplitHandHighLight() {
        if (!isSplit) return

        if(isPlayingHand1) {
            binding.playerHand1.alpha = 1f
            binding.playerHand2.alpha = 0.4f
        } else {
            binding.playerHand1.alpha = 0.4f
            binding.playerHand2.alpha = 1f
        }
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