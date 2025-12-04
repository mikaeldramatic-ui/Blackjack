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
import java.time.temporal.TemporalAmount
import kotlin.random.Random

class GameBoardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameboardBinding

    // Split mode flags
    private var isSplit = false
    private var isPlayingHand1 = true

    // Scores for split hands
    private var hand1Score = 0
    private var hand2Score = 0

    // Card lists
    private var playerCards = mutableListOf<String>()
    private var hand1Cards = mutableListOf<String>()
    private var hand2Cards = mutableListOf<String>()

    // Money
    private var playerMoney = 200
    private var currentBet = 0

    // Game variables
    private var playerScore = 0
    private var dealerScore = 0
    private val deck = mutableListOf<String>()

    private var dealerHiddenCardCode: String? = null
    private var dealerHiddenImageView: ImageView? = null

    // Use main looper handler for delays
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
                if (bets[which] == "Fold") {
                    finish()
                    return@setItems
                }
                currentBet = bets[which].toInt()
                if (currentBet > playerMoney) currentBet = 10

                // Deduct the player's bet once now (split will deduct a second time if used)
                playerMoney -= currentBet
                binding.playerMoney.text = "Money: $$playerMoney"

                dealInitialCards()
            }
            .setCancelable(false)
            .show()
    }

    // -------------------- Deal initial --------------------
    private fun dealInitialCards() {
        enablePlayerButtons()

        // UI containers reset
        binding.playerCards.visibility = View.VISIBLE
        binding.playerHand1.visibility = View.GONE
        binding.playerHand2.visibility = View.GONE
        binding.btnSplit.visibility = View.GONE
        binding.btnSplit.isEnabled = false

        binding.playerCards.removeAllViews()
        binding.dealerCards.removeAllViews()

        // reset logical lists and scores
        playerCards.clear()
        hand1Cards.clear()
        hand2Cards.clear()

        playerScore = 0
        dealerScore = 0
        hand1Score = 0
        hand2Score = 0

        if (deck.size < 4) {
            createDeck()
            shuffleDeck()
        }

        // Player 2 cards
        val p1 = deck.removeAt(0)
        val p2 = deck.removeAt(0)

        // logical lists
        playerCards.add(p1)
        playerCards.add(p2)

        // UI show them
        addCardImageToLayout(p1, binding.playerCards)
        addCardImageToLayout(p2, binding.playerCards)

        // compute logical scores (best/hard score)
        playerScore = calculateBestScore(playerCards)

        // Prepare split logical hands (one card each)
        hand1Cards.add(p1)
        hand2Cards.add(p2)
        hand1Score = calculateBestScore(hand1Cards)
        hand2Score = calculateBestScore(hand2Cards)

        // Dealer: one visible, one hidden
        val d1 = deck.removeAt(0)
        val d2 = deck.removeAt(0)

        dealerScore = 0
        dealerScore += getCardValue(d1)
        addDealerCard(d1, hidden = false)

        // Create and place dealer hidden card image
        val iv = ImageView(this)
        iv.setImageResource(R.drawable.card_back)
        val lp = ViewGroup.MarginLayoutParams(dpToPx(72), dpToPx(100))
        lp.setMargins(8, 0, 8, 0)
        iv.layoutParams = lp
        binding.dealerCards.addView(iv, 1)
        animateCardIn(iv)

        dealerHiddenCardCode = d2
        dealerHiddenImageView = iv

        // Update UI — use formatAceScore so Ace prompt appears immediately
        updateScoreOnUI()
        updateSplitScoresOnUi()

        // Offer Split if applicable (only if not already split and rules allow)
        checkForSplitOption(p1, p2)

        // Black Jack Logic (evaluate using best scores)
        val playerBlackjack = (calculateBestScore(playerCards) == 21 && playerCards.size == 2)
        val dealerBlackjack = (getCardValue(cardCode = d1) + getCardValue(d2) == 21)

        // If someone has blackjack, end round immediately
        if (!playerBlackjack && !dealerBlackjack) return

        // Round over — disable player buttons and hide split
        disablePlayerButtons()
        binding.btnSplit.visibility = View.GONE
        binding.btnSplit.isEnabled = false

        if (playerBlackjack && dealerBlackjack) {
            handleBlackjackOutcome("push")
            return
        }
        if (playerBlackjack) {
            handleBlackjackOutcome("player")
            return
        }
        if (dealerBlackjack) {
            handleBlackjackOutcome("dealer")
            return
        }
    }

    private fun checkForSplitOption(card1: String, card2: String) {
        // If already split this round — don't show
        if (isSplit) {
            binding.btnSplit.visibility = View.GONE
            binding.btnSplit.isEnabled = false
            return
        }

        val rank1 = card1.dropLast(1)
        val rank2 = card2.dropLast(1)

        val value1 = getCardValue(card1)
        val value2 = getCardValue(card2)

        // Rule: split ANY 10-value or same rank (A+A, 8+8, Q+Q)
        val bothAre10Value = (value1 == 10 && value2 == 10)
        val sameRank = rank1 == rank2

        if (bothAre10Value || sameRank) {
            binding.btnSplit.visibility = View.VISIBLE
            binding.btnSplit.isEnabled = true
        } else {
            binding.btnSplit.visibility = View.GONE
            binding.btnSplit.isEnabled = false
        }
    }

    private fun handleBlackjackOutcome(result: String) {
        // Flip dealer hidden card immediately
        dealerHiddenCardCode?.let { code ->
            dealerHiddenImageView?.let { iv ->
                val realRes = resources.getIdentifier(getCardDrawableName(code), "drawable", packageName)
                if (realRes != 0) flipCard(iv, realRes)
            }
        }

        dealerHiddenCardCode = null
        dealerHiddenImageView = null

        when (result) {
            "push" -> showBlackjackOverlay("push")
            "player" -> {
                val amount = (currentBet * 2.5).toInt()
                playerMoney += amount
                binding.playerMoney.text = "Money: $$playerMoney"
                showBlackjackOverlay("player", amount)
            }
            "dealer" -> {
                // Player already paid bet when betting; losing here means no refund
                binding.playerMoney.text = "Money: $$playerMoney"
                showBlackjackOverlay("dealer")
            }
        }
    }

    // -------------------- Hit --------------------
    private fun onHitClicked() {
        // Normal (no split)
        if (!isSplit) {
            hitCardToPlayer()
            // Update UI immediately (this will show ace prompt if card was an Ace)
            updateScoreOnUI()

            // disable split once player has chosen hit (cannot split anymore)
            binding.btnSplit.visibility = View.GONE
            binding.btnSplit.isEnabled = false

            if (playerScore > 21) {
                disablePlayerButtons()
                handler.postDelayed({ checkRoundResult() }, 500)
            }
            return
        }

        // Split mode
        if (isPlayingHand1) {
            hitSplitHand1()
            // update UI for split hands
            updateSplitScoresOnUi()
            updateSplitHandHighLight()

            if (hand1Score > 21) {
                isPlayingHand1 = false
                binding.playerHand1.alpha = 0.5f
            }
        } else {
            hitSplitHand2()
            updateSplitScoresOnUi()
            updateSplitHandHighLight()

            if (hand2Score > 21) {
                disablePlayerButtons()
                handler.postDelayed({ dealerPlayAfterSplit() }, 700)
            }
        }
    }

    private fun onSplitClicked() {
        // Hide split button permanently for this round
        binding.btnSplit.visibility = View.GONE
        binding.btnSplit.isEnabled = false

        binding.playerScore.visibility = View.GONE

        if (isSplit) return

        isSplit = true
        isPlayingHand1 = true

        // Deduct bet for second hand
        playerMoney -= currentBet
        binding.playerMoney.text = "Money: $$playerMoney"

        // Show split UI
        binding.playerCards.visibility = View.GONE
        binding.playerHand1.visibility = View.VISIBLE
        binding.playerHand2.visibility = View.VISIBLE

        // Clean and add the single card to each split hand UI
        binding.playerHand1.removeAllViews()
        binding.playerHand2.removeAllViews()

        addCardImageToLayout(hand1Cards[0], binding.playerHand1)
        addCardImageToLayout(hand2Cards[0], binding.playerHand2)

        // compute split scores and update UI
        hand1Score = calculateBestScore(hand1Cards)
        hand2Score = calculateBestScore(hand2Cards)
        updateSplitScoresOnUi()

        // Highlight active hand
        binding.playerHand1.alpha = 1f
        binding.playerHand2.alpha = 0.4f

        updateSplitHandHighLight()
        updateScoreOnUI()
    }

    // -------------------- Split hits --------------------

    private fun hitSplitHand1() {
        if (deck.isEmpty()) { createDeck(); shuffleDeck() }

        val card = deck.removeAt(0)
        hand1Cards.add(card)
        addCardImageToLayout(card, binding.playerHand1)

        hand1Score = calculateBestScore(hand1Cards)
        updateSplitScoresOnUi()

        // If bust or single ace split rule: move to second hand
        if (hand1Score > 21 || hand1Cards.first().startsWith("A")) {
            isPlayingHand1 = false
            binding.playerHand1.alpha = 0.5f
            binding.playerHand2.alpha = 1f
            updateSplitHandHighLight()
            // If hand1 busts we still allow to play second hand
            return
        }
    }

    private fun hitSplitHand2() {
        if (deck.isEmpty()) { createDeck(); shuffleDeck() }

        val card = deck.removeAt(0)
        hand2Cards.add(card)
        addCardImageToLayout(card, binding.playerHand2)

        hand2Score = calculateBestScore(hand2Cards)
        updateSplitScoresOnUi()

        if (hand2Score > 21) {
            disablePlayerButtons()
            handler.postDelayed({ dealerPlayAfterSplit() }, 700)
            return
        }

        if (hand2Cards.first().startsWith("A")) {
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
        playerCards.add(card)

        // update logical score
        playerScore = calculateBestScore(playerCards)

        // add visual card
        addCardImageToLayout(card, binding.playerCards)
    }

    // -------------------- Stand (dealer sequence) --------------------
    private fun onStandClicked() {
        // Normal game
        if (!isSplit) {
            dealerPlay()
            return
        }

        // Split: move to second hand if on first
        if (isPlayingHand1) {
            isPlayingHand1 = false
            updateSplitHandHighLight()
            return
        }

        // Already on hand2 → dealer plays
        disablePlayerButtons()
        handler.postDelayed({ dealerPlayAfterSplit() }, 600)
    }

    // -------------------- Dealer after split flow --------------------
    private fun dealerPlayAfterSplit() {
        // Reveal hidden card
        dealerHiddenCardCode?.let { code ->
            dealerHiddenImageView?.let { iv ->
                val realRes = resources.getIdentifier(getCardDrawableName(code), "drawable", packageName)
                if (realRes != 0) flipCard(iv, realRes)
            }
        }
        dealerScore += dealerHiddenCardCode?.let { getCardValue(it) } ?: 0
        dealerHiddenCardCode = null
        dealerHiddenImageView = null

        updateScoreOnUI()

        handler.postDelayed({
            drawDealerCardsSequentiallyAfterSplit()
        }, 600)
    }

    private fun drawDealerCardsSequentiallyAfterSplit() {
        if (dealerScore >= 17) {
            handler.postDelayed({ evaluateSplitResults() }, 700)
            return
        }

        if (deck.isEmpty()) { createDeck(); shuffleDeck() }

        val card = deck.removeAt(0)
        dealerScore += getCardValue(card)
        // soft Ace adjustment for dealer
        if (dealerScore > 21 && card.startsWith("A")) dealerScore -= 10

        addDealerCard(card, hidden = false)
        updateScoreOnUI()

        handler.postDelayed({ drawDealerCardsSequentiallyAfterSplit() }, 700)
    }

    // -------------------- Split result & money --------------------
    private fun evaluateSplitResults() {
        // ensure up-to-date scores
        hand1Score = calculateBestScore(hand1Cards)
        hand2Score = calculateBestScore(hand2Cards)

        val hand1Bust = hand1Score > 21
        val hand2Bust = hand2Score > 21

        var totalWin = 0

        // If dealer busts: every non-busted hand wins
        if (dealerScore > 21) {
            if (!hand1Bust) totalWin += currentBet * 2
            if (!hand2Bust) totalWin += currentBet * 2
        } else {
            // Evaluate each hand against dealer
            totalWin += when {
                hand1Bust -> -currentBet
                hand1Score > dealerScore -> currentBet * 2
                hand1Score < dealerScore -> -currentBet
                else -> currentBet // push -> return bet
            }

            totalWin += when {
                hand2Bust -> -currentBet
                hand2Score > dealerScore -> currentBet * 2
                hand2Score < dealerScore -> -currentBet
                else -> currentBet // push -> return bet
            }
        }

        // Apply money result
        playerMoney += totalWin
        binding.playerMoney.text = "Money: $$playerMoney"

        // Choose overlay
        when {
            totalWin > 0 -> {
                binding.winAmountText.text = "+$totalWin"
                showWinOverlay()
            }
            totalWin < 0 -> {
                binding.loseAmountText.text = "$totalWin"
                showLoseOverlay()
            }
            else -> showBlackjackOverlay("push")
        }

        // Reset split mode and UI
        isSplit = false
        isPlayingHand1 = true

        binding.playerHand1.visibility = View.GONE
        binding.playerHand2.visibility = View.GONE
        binding.playerCards.visibility = View.VISIBLE
        binding.playerCards.removeAllViews()
        binding.playerScore.visibility = View.VISIBLE

        // Rebuild player's visual hand from playerCards (so UI is consistent)
        for (c in playerCards) addCardImageToLayout(c, binding.playerCards)

        // Update all relevant UI
        updateScoreOnUI()
        updateSplitScoresOnUi()
    }

    private fun evaluateSingleHand(score: Int): Int {
        return when {
            score > 21 -> -1
            dealerScore > 21 -> 1
            score > dealerScore -> 1
            score < dealerScore -> -1
            else -> 0
        }
    }

    // -------------------- Dealer normal play --------------------
    private fun dealerPlay() {
        handler.postDelayed({
            // Flip hidden card and update dealer score
            dealerHiddenCardCode?.let { code ->
                dealerHiddenImageView?.let { iv ->
                    val realRes = resources.getIdentifier(getCardDrawableName(code), "drawable", packageName)
                    if (realRes != 0) flipCard(iv, realRes)
                    dealerScore += getCardValue(code)
                    if (dealerScore > 21 && code.startsWith("A")) dealerScore -= 10
                    updateScoreOnUI()
                }
            }

            dealerHiddenCardCode = null
            dealerHiddenImageView = null

            handler.postDelayed({
                drawDealerCardsSequentially()
            }, 600)
        }, 600)
    }

    private fun drawDealerCardsSequentially() {
        if (dealerScore >= 17) {
            handler.postDelayed({ checkRoundResult() }, 700)
            return
        }

        if (deck.isEmpty()) { createDeck(); shuffleDeck() }

        val card = deck.removeAt(0)
        dealerScore += getCardValue(card)
        if (dealerScore > 21 && card.startsWith("A")) dealerScore -= 10

        addDealerCard(card, hidden = false)
        updateScoreOnUI()
        handler.postDelayed({ drawDealerCardsSequentially() }, 700)
    }

    // -------------------- Check result & money --------------------
    private fun checkRoundResult() {
        // update player's logical best score to be safe
        playerScore = calculateBestScore(playerCards)

        when {
            playerScore > 21 -> {
                binding.playerMoney.text = "Money: $$playerMoney"
                showLoseOverlay(currentBet)
            }
            dealerScore > 21 || playerScore > dealerScore -> {
                val payout = currentBet * 2
                playerMoney += payout
                binding.playerMoney.text = "Money: $$playerMoney"
                showWinOverlay(payout)
            }
            dealerScore == playerScore -> {
                playerMoney += currentBet
                binding.playerMoney.text = "Money: $$playerMoney"
                showBlackjackOverlay("push")
            }
            else -> {
                binding.playerMoney.text = "Money: $$playerMoney"
                showLoseOverlay(currentBet)
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

    // Quit dialog
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

    // Win / Lose / Blackjack overlays (unchanged behavior)
    private fun showWinOverlay(amount: Int = currentBet) {
        val overlay = binding.winOverlay
        binding.winTotalMoney.text = "Money: $$playerMoney"
        binding.winAmountText.text = "+$amount"
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

    private fun showLoseOverlay(amount: Int = currentBet) {
        val overlay = binding.loseOverlay
        binding.loseTotalMoney.text = "Money: $$playerMoney"
        binding.loseAmountText.text = "-$amount"
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
                            if (playerMoney < 10) startLooseActivity() else askForBet()
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

    private fun disablePlayerButtons() {
        binding.btnHit.isEnabled = false
        binding.btnStand.isEnabled = false
    }

    private fun enablePlayerButtons() {
        binding.btnHit.isEnabled = true
        binding.btnStand.isEnabled = true
    }

    /**
     * Update the main player's displayed score.
     * If an Ace exists in the current main hand (playerCards),
     * we show the soft/hard format using formatAceScore(...) immediately.
     */
    private fun updateScoreOnUI() {
        if (!isSplit) {
            binding.playerScore.visibility = View.VISIBLE
            binding.playerScore.text = "Player: ${formatAceScore(playerCards)}"
        } else {
            binding.playerScore.visibility = View.GONE
        }

        // Dealer shows one value (we track dealerScore separately)
        binding.dealerScore.text = "Dealer: $dealerScore"
    }

    /**
     * Update the split hands' UI scores.
     * Uses formatAceScore(...) so Ace soft/hard shows for each split hand immediately.
     */
    private fun updateSplitScoresOnUi() {
        if (!isSplit) {
            binding.playerHand1Score.visibility = View.GONE
            binding.playerHand2Score.visibility = View.GONE
            return
        }

        binding.playerHand1Score.visibility = View.VISIBLE
        binding.playerHand2Score.visibility = View.VISIBLE

        binding.playerHand1Score.text = "Hand1: ${formatAceScore(hand1Cards)}"
        binding.playerHand2Score.text = "Hand2: ${formatAceScore(hand2Cards)}"
    }

    private fun updateSplitHandHighLight() {
        if (!isSplit) return

        if (isPlayingHand1) {
            binding.playerHand1.alpha = 1f
            binding.playerHand2.alpha = 0.4f
        } else {
            binding.playerHand1.alpha = 0.4f
            binding.playerHand2.alpha = 1f
        }

        binding.playerScore.visibility = View.GONE
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
    }

    private fun startLooseActivity() {
        val i = Intent(this, LooseActivity::class.java)
        i.putExtra("playerScore", playerScore)
        i.putExtra("dealerScore", dealerScore)
        startActivity(i)
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
        for (s in suits) for (r in ranks) deck.add(r + s)
    }

    private fun shuffleDeck() {
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
        val rank = cardCode.dropLast(1).lowercase()
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

    /**
     * calculateBestScore(cards) returns the best (highest <=21) value for a hand.
     * This is used for comparisons with the dealer etc.
     */
    private fun calculateBestScore(cards: List<String>): Int {
        if (cards.isEmpty()) return 0

        var total = 0
        var aceCount = 0

        for (c in cards) {
            val rank = c.dropLast(1)
            when (rank) {
                "A" -> {
                    total += 11
                    aceCount++
                }
                "K", "Q", "J" -> total += 10
                else -> total += rank.toIntOrNull() ?: 0
            }
        }

        // reduce 10 for each ace while over 21
        while (total > 21 && aceCount > 0) {
            total -= 10
            aceCount--
        }

        return total
    }

    /**
     * formatAceScore(...) returns a string showing soft/hard values if Ace present,
     * otherwise returns the single numeric value. Examples:
     * - "15 (or 5)"
     * - "13"
     * - "31 (or 21)"
     *
     * This is used for UI display so player's score displays the Ace choice immediately.
     */
    private fun formatAceScore(cards: List<String>): String {
        if (cards.isEmpty()) return "0"

        var total = 0
        var aces = 0

        for (c in cards) {
            val rank = c.dropLast(1)
            when (rank) {
                "A" -> {
                    total += 11
                    aces++
                }
                "K", "Q", "J" -> total += 10
                else -> total += rank.toIntOrNull() ?: 0
            }
        }

        val softValue = total
        var hardValue = total
        var remainingAces = aces

        // convert aces one by one from 11->1 (subtract 10) until <=21 or no aces left
        while (hardValue > 21 && remainingAces > 0) {
            hardValue -= 10
            remainingAces--
        }

        // If there's at least one Ace AND soft and hard differ, show both values
        return if (aces > 0 && softValue != hardValue) {
            "$softValue (or $hardValue)"
        } else {
            "$hardValue"
        }
    }
}