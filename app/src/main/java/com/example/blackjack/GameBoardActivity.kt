package com.example.blackjack

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.blackjack.databinding.ActivityGameboardBinding
import kotlin.random.Random

class GameBoardActivity : AppCompatActivity() {

    // Multiplayer
    private var playerCount = 1
    private var players = mutableListOf<Player>()
    private var currentPlayerIndex = 0

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

    private var currentBet = 0

    // Game variables
    private var playerScore = 0
    private var dealerScore = 0
    private val deck = mutableListOf<String>()

    private var dealerHiddenCardCode: String? = null
    private var dealerHiddenImageView: ImageView? = null

    // Ace preference flags (user-chosen)
    private var mainPreferAceAsOne = false
    private var hand1PreferAceAsOne = false
    private var hand2PreferAceAsOne = false

    private val manualAceValues = mutableMapOf<String, Int>()

    // Use main looper handler for delayed actions
    private val handler = Handler(Looper.getMainLooper())

    private var aceDialogInProgress = false

    // Ace-dialog watchdog to avoid deadlocks
    private var aceDialogWatchdogStart: Long? = null
    private val ACE_DIALOG_TIMEOUT_MS = 5_000L // 5 seconds

    // Prevent duplicate round resolution
    private var roundResolutionScheduled = false
    private var roundResolving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Multiplayer
        playerCount = intent.getIntExtra("playerCount", 1)
        createPlayers()

        updatePlayerHeader()

        // UI default text
        binding.playerScore.text = "Player: 0"
        binding.dealerScore.text = "Dealer: 0"

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

    //---------------- Create Players --------------------

    private fun createPlayers() {
        players.clear()
        for (i in 1..playerCount) {
            val randomName = generateRandomName()
            players.add(Player(id = i, name = randomName))
        }
        currentPlayerIndex = 0
    }

    // ------- Multiplayer helpers ----------------------

    private fun currentPlayer(): Player {
        if (players.isEmpty()) throw IllegalStateException("No players")
        if (currentPlayerIndex < 0 || currentPlayerIndex >= players.size) currentPlayerIndex = 0
        return players[currentPlayerIndex]
    }

    private fun updatePlayerHeader() {
        val p = currentPlayer()
        binding.playerMoney.text = "${p.name} – $${p.money}"
    }

    private fun advanceToNextPlayer() {
        if (players.isEmpty()) {
            finish()
            return
        }

        currentPlayerIndex++
        if (currentPlayerIndex >= players.size) currentPlayerIndex = 0

        // Reset UI and state for next player
        resetHandsForNewRound()

        binding.dealerCards.removeAllViews()
        binding.playerScore.text = "Player: 0"
        binding.dealerScore.text = "Dealer: 0"

        // Clear any pending/resolving state for next player
        roundResolutionScheduled = false
        roundResolving = false

        updatePlayerHeader()
        askForBet()
    }

    // -------------------- Betting --------------------
    private fun askForBet() {
        val player = currentPlayer()

        // Player eliminated?
        if (player.money < 10) {
            showNoMoneyDialog()
            return
        }

        // Bet options
        val betOptions = listOf(10, 20, 50, 100)

        // Filter only bets the player can afford
        val affordable = betOptions.filter { it <= player.money }

        // Convert to strings + include Fold
        val bets = (affordable.map { it.toString() } + "Fold").toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("${player.name} – Place Your Bet")
            .setItems(bets) { _, which ->
                val choice = bets[which]

                if (choice == "Fold") {
                    // SINGLE PLAYER → quit
                    if (playerCount == 1) {
                        finish()
                        return@setItems
                    }

                    // MULTIPLAYER → remove player completely
                    val foldedIndex = currentPlayerIndex
                    players.removeAt(foldedIndex)

                    // adjust index
                    if (players.isEmpty()) {
                        finish()
                        return@setItems
                    }
                    if (foldedIndex >= players.size) {
                        currentPlayerIndex = 0
                    }

                    // move on to next stage
                    updatePlayerHeader()
                    askForBet()
                    return@setItems
                }

                currentBet = choice.toInt()

                // Deduct immediately
                player.money -= currentBet
                updatePlayerHeader()

                dealInitialCards()
            }
            .setCancelable(false)
            .show()
    }

    // -------------------- Reset helper --------------------
    // Centralized reset for split UI/state so nothing bleeds into next round
    private fun resetHandsForNewRound() {
        // logical state
        isSplit = false
        isPlayingHand1 = true

        hand1Cards.clear()
        hand2Cards.clear()
        hand1Score = 0
        hand2Score = 0

        // UI cleanup
        try {
            binding.playerHand1.visibility = View.GONE
            binding.playerHand2.visibility = View.GONE
            binding.playerHand1.removeAllViews()
            binding.playerHand2.removeAllViews()

            binding.playerCards.visibility = View.VISIBLE
            binding.playerCards.removeAllViews()

            binding.playerHand1.alpha = 1f
            binding.playerHand2.alpha = 1f

            binding.playerScore.visibility = View.VISIBLE
            binding.playerHand1Score.visibility = View.GONE
            binding.playerHand2Score.visibility = View.GONE
        } catch (e: Exception) {
            Log.w("UI_RESET", "resetHandsForNewRound() failed: ${e.message}")
        }
    }

    // -------------------- Deal initial --------------------
    private fun dealInitialCards() {
        // ensure all hand-views and split state are cleared before dealing
        resetHandsForNewRound()

        roundResolutionScheduled = false
        roundResolving = false

        enablePlayerButtons()
        binding.btnSplit.visibility = View.GONE
        binding.btnSplit.isEnabled = false

        binding.dealerCards.removeAllViews()

        // reset logical lists and scores
        playerCards.clear()
        hand1Cards.clear()
        hand2Cards.clear()

        playerScore = 0
        dealerScore = 0
        hand1Score = 0
        hand2Score = 0

        manualAceValues.clear()

        // reset ace preferences each round
        mainPreferAceAsOne = false
        hand1PreferAceAsOne = false
        hand2PreferAceAsOne = false

        if (deck.size < 6) {
            createDeck()
            shuffleDeck()
        }

        // Player: draw 2 cards
        val p1 = deck.removeAt(0)
        val p2 = deck.removeAt(0)

        // fill logical player list
        playerCards.add(p1)
        playerCards.add(p2)

        // show them on UI
        addCardImageToLayout(p1, binding.playerCards)
        addCardImageToLayout(p2, binding.playerCards)

        // compute raw best score
        playerScore = calculateBestScore(playerCards)

        // Prepare split logical hands (one card each)
        hand1Cards.add(p1)
        hand2Cards.add(p2)
        hand1Score = calculateBestScore(hand1Cards)
        hand2Score = calculateBestScore(hand2Cards)

        // Dealer: one visible, one hidden
        val d1 = deck.removeAt(0)
        val d2 = deck.removeAt(0)

        dealerScore = getCardValue(d1)
        addDealerCard(d1, hidden = false)

        // Create hidden ImageView for dealer second card
        val iv = ImageView(this)
        iv.setImageResource(R.drawable.card_back)
        val lp = ViewGroup.MarginLayoutParams(dpToPx(72), dpToPx(100))
        lp.setMargins(8, 0, 8, 0)
        iv.layoutParams = lp
        binding.dealerCards.addView(iv, 1)
        animateCardIn(iv)

        dealerHiddenCardCode = d2
        dealerHiddenImageView = iv

        // update UI scores (show soft/hard if ace)
        updateScoreOnUI()
        updateSplitScoresOnUi()

        // Offer Split if applicable
        checkForSplitOption(p1, p2)

        // Black Jack Logic
        val playerBlackjack = (playerScore == 21)
        val dealerBlackjack = (getCardValue(d1) + getCardValue(d2) == 21)

        // If player has an ace in initial deal and not blackjack -> prompt immediately
        val playerHasAceInitially = p1.startsWith("A") || p2.startsWith("A")
        if (playerHasAceInitially && !playerBlackjack) {
            handler.post {
                Log.d("ACE_DEBUG", "Initial deal: Ace found in $playerCards -> prompting")
                promptAceChoiceFor("main", p1.takeIf { it.startsWith("A") } ?: p2)
            }
        }

        // If either has blackjack handle and stop round
        if (!playerBlackjack && !dealerBlackjack) return

        // Round ends instantly -> disable buttons + handle blackjack outcomes
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

    // -------------------- Split option --------------------
    private fun checkForSplitOption(card1: String, card2: String) {
        if (isSplit) {
            binding.btnSplit.visibility = View.GONE
            binding.btnSplit.isEnabled = false
            return
        }

        val rank1 = card1.dropLast(1)
        val rank2 = card2.dropLast(1)

        val value1 = getCardValue(card1)
        val value2 = getCardValue(card2)

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
        // reveal dealer hidden card immediately
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
                currentPlayer().money += amount
                binding.playerMoney.text = "${currentPlayer().name} – $${currentPlayer().money}"
                showBlackjackOverlay("player", amount)
            }
            "dealer" -> {
                // player already paid bet — show overlay
                binding.playerMoney.text = "${currentPlayer().name} – $${currentPlayer().money}"
                showBlackjackOverlay("dealer")
            }
        }
    }

    // -------------------- Hit --------------------
    private fun onHitClicked() {
        // block clicks if resolution or ace dialog is in progress
        if (roundResolutionScheduled || roundResolving || aceDialogInProgress) return
        if (!binding.btnHit.isEnabled) return

        if (!isSplit) {
            hitCardToPlayer()
            updateScoreOnUI()

            // Player can no longer split after hitting
            binding.btnSplit.visibility = View.GONE
            binding.btnSplit.isEnabled = false

            // Direct bust-check after hit
            playerScore = calculateBestScoreWithPreference(playerCards, mainPreferAceAsOne)
            if (playerScore > 21) {
                roundResolutionScheduled = true
                disablePlayerButtons()
                handler.postDelayed({ checkRoundResult() }, 300)
            }

            return
        }

        // Split mode
        if (isPlayingHand1) {
            hitSplitHand1()
            updateSplitScoresOnUi()
            updateSplitHandHighLight()

            if (hand1Score > 21) {
                // move to hand2 if hand1 busted
                isPlayingHand1 = false
                binding.playerHand1.alpha = 0.5f
                binding.playerHand2.alpha = 1f
                updateSplitHandHighLight()
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
        if (currentPlayer().money < currentBet) {
            Toast.makeText(this, "${currentPlayer().name} cannot afford to split", Toast.LENGTH_SHORT).show()
            return
        }

        // Hide split button permanently for this round
        binding.btnSplit.visibility = View.GONE
        binding.btnSplit.isEnabled = false

        binding.playerScore.visibility = View.GONE

        if (isSplit) return

        isSplit = true
        isPlayingHand1 = true

        // Deduct bet for second hand
        currentPlayer().money -= currentBet
        binding.playerMoney.text = "${currentPlayer().name} – $${currentPlayer().money}"

        // Show split UI
        binding.playerCards.visibility = View.GONE
        binding.playerHand1.visibility = View.VISIBLE
        binding.playerHand2.visibility = View.VISIBLE

        // Clear and place initial single card for each hand
        binding.playerHand1.removeAllViews()
        binding.playerHand2.removeAllViews()
        addCardImageToLayout(hand1Cards[0], binding.playerHand1)
        addCardImageToLayout(hand2Cards[0], binding.playerHand2)

        // compute split scores and update UI
        hand1Score = calculateBestScore(hand1Cards)
        hand2Score = calculateBestScore(hand2Cards)
        updateSplitScoresOnUi()

        // Start with hand1 active
        binding.playerHand1.alpha = 1f
        binding.playerHand2.alpha = 0.4f
        updateSplitHandHighLight()
    }

    // -------------------- Split hits --------------------
    private fun hitSplitHand1() {
        if (deck.isEmpty()) {
            createDeck(); shuffleDeck()
        }

        val card = deck.removeAt(0)
        hand1Cards.add(card)
        addCardImageToLayout(card, binding.playerHand1)

        Log.d("ACE_DEBUG", "HIT: drew $card into HAND1 -> $hand1Cards")

        hand1Score = calculateBestScore(hand1Cards)
        updateSplitScoresOnUi()

        if (card.startsWith("A")) {
            promptAceChoiceFor("hand1", card)
        }
    }

    private fun hitSplitHand2() {
        if (deck.isEmpty()) {
            createDeck(); shuffleDeck()
        }

        val card = deck.removeAt(0)
        hand2Cards.add(card)
        addCardImageToLayout(card, binding.playerHand2)

        Log.d("ACE_DEBUG", "HIT: drew $card into HAND2 -> $hand2Cards")

        hand2Score = calculateBestScore(hand2Cards)
        updateSplitScoresOnUi()

        if (card.startsWith("A")) {
            promptAceChoiceFor("hand2", card)
        }
    }

    private fun hitCardToPlayer() {
        if (roundResolutionScheduled || roundResolving) return

        if (deck.isEmpty()) {
            createDeck()
            shuffleDeck()
        }

        val card = deck.removeAt(0)
        playerCards.add(card)
        Log.d("ACE_DEBUG", "HIT: drew $card into MAIN -> $playerCards")

        // Recompute using preference if set
        playerScore = calculateBestScoreWithPreference(playerCards, mainPreferAceAsOne)

        addCardImageToLayout(card, binding.playerCards)

        // If it's an Ace, prompt immediately
        if (card.startsWith("A")) {
            promptAceChoiceFor("main", card)
        } else {
            // update UI text if no ace prompt
            updateScoreOnUI()
        }
    }

    // -------------------- Stand (dealer sequence) --------------------
    private fun onStandClicked() {
        if (roundResolutionScheduled || roundResolving || aceDialogInProgress) return
        if (!binding.btnStand.isEnabled) return

        if (!isSplit) {
            disablePlayerButtons()
            roundResolutionScheduled = true
            handler.post { dealerPlay() }
            return
        }

        if (isPlayingHand1) {
            // Move to hand2
            isPlayingHand1 = false
            updateSplitHandHighLight()
            return
        }

        // Already on hand2 -> dealer plays after split
        disablePlayerButtons()
        roundResolutionScheduled = true
        handler.postDelayed({ dealerPlayAfterSplit() }, 600)
    }

    // Dealer flow for split
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

        handler.postDelayed({ drawDealerCardsSequentiallyAfterSplit() }, 600)
    }

    private fun drawDealerCardsSequentiallyAfterSplit() {
        if (aceDialogInProgress) {
            val start = aceDialogWatchdogStart
            if (start != null && System.currentTimeMillis() - start > (ACE_DIALOG_TIMEOUT_MS + 1000)) {
                Log.w("ACE_DEBUG", "Dealer loop (split): long waiting for ace dialog — forcing resolution now")
                forceResolvePendingAcesAsOne()
            } else {
                handler.postDelayed({ drawDealerCardsSequentiallyAfterSplit() }, 400)
                return
            }
        }

        if (dealerScore >= 17) {
            handler.postDelayed({ evaluateSplitResults() }, 700)
            return
        }

        if (deck.isEmpty()) {
            createDeck(); shuffleDeck()
        }

        val card = deck.removeAt(0)
        dealerScore += getCardValue(card)
        if (dealerScore > 21 && card.startsWith("A")) dealerScore -= 10

        addDealerCard(card, hidden = false)
        updateScoreOnUI()
        handler.postDelayed({ drawDealerCardsSequentiallyAfterSplit() }, 700)
    }

    // -------------------- Split evaluation --------------------
    private fun evaluateSplitResults() {
        // ensure up-to-date scores using preferences (if user made choices)
        hand1Score = calculateBestScoreWithPreference(hand1Cards, hand1PreferAceAsOne)
        hand2Score = calculateBestScoreWithPreference(hand2Cards, hand2PreferAceAsOne)

        val hand1Bust = hand1Score > 21
        val hand2Bust = hand2Score > 21

        // total amount that was originally bet for this round (already deducted earlier)
        val totalBet = if (isSplit) currentBet * 2 else currentBet

        var totalWin = 0

        // HAND 1
        totalWin += when {
            hand1Bust -> 0
            dealerScore > 21 || hand1Score > dealerScore -> currentBet * 2
            hand1Score < dealerScore -> 0
            else -> currentBet
        }

        // HAND 2
        totalWin += when {
            hand2Bust -> 0
            dealerScore > 21 || hand2Score > dealerScore -> currentBet * 2
            hand2Score < dealerScore -> 0
            else -> currentBet
        }

        val pushCount = listOf(
            !hand1Bust && hand1Score == dealerScore,
            !hand2Bust && hand2Score == dealerScore
        ).count { it }

        val winCount = listOf(
            !hand1Bust && (dealerScore > 21 || hand1Score > dealerScore),
            !hand2Bust && (dealerScore > 21 || hand2Score > dealerScore)
        ).count { it }

        val lossCount = 2 - pushCount - winCount
        val net = totalWin - totalBet

        // Mix results
        if (pushCount > 0 && (winCount > 0 || lossCount > 0)) {
            showMixedSplitOverlay(pushCount, winCount, lossCount, net)
            // reset of UI will happen inside overlay handlers
            return
        }

        Log.d(
            "ACE_DEBUG",
            "evaluateSplitResults(): hand1Score=$hand1Score hand2Score=$hand2Score dealerScore=$dealerScore " +
                    "hand1Bust=$hand1Bust hand2Bust=$hand2Bust totalBet=$totalBet totalWin=$totalWin"
        )

        // Apply money result
        currentPlayer().money += totalWin
        binding.playerMoney.text = "${currentPlayer().name} – $${currentPlayer().money}"

        // Decide overlay
        when {
            totalWin > totalBet -> {
                binding.winAmountText.text = "+$totalWin"
                showWinOverlay(totalWin)
            }
            totalWin == totalBet -> {
                showBlackjackOverlay("push")
            }
            else -> {
                val netLoss = totalBet - totalWin
                binding.loseAmountText.text = "-$netLoss"
                showLoseOverlay(netLoss)
            }
        }

        // Reset split state & UI back to normal (logical reset)
        isSplit = false
        isPlayingHand1 = true

        currentBet = 0

        // Make sure main hand view will be rebuilt once next round starts
        binding.playerHand1.visibility = View.GONE
        binding.playerHand2.visibility = View.GONE
        binding.playerCards.visibility = View.VISIBLE
        binding.playerCards.removeAllViews()
        binding.playerScore.visibility = View.VISIBLE

        // Rebuild main player's visual hand (so UI is consistent)
        for (c in playerCards) addCardImageToLayout(c, binding.playerCards)

        // Update UI numbers
        updateScoreOnUI()
        updateSplitScoresOnUi()
    }

    // -------------------- Dealer normal play --------------------
    private fun dealerPlay() {
        // If ace dialog is open, wait (watchdog will force after timeout)
        if (aceDialogInProgress) {
            handler.postDelayed({ dealerPlay() }, 400)
            return
        }

        // mark that dealer sequence is scheduled
        roundResolutionScheduled = true

        handler.postDelayed({
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

            handler.postDelayed({ drawDealerCardsSequentially() }, 600)
        }, 600)
    }

    private fun drawDealerCardsSequentially() {
        if (aceDialogInProgress) {
            val start = aceDialogWatchdogStart
            if (start != null && System.currentTimeMillis() - start > (ACE_DIALOG_TIMEOUT_MS + 1000)) {
                Log.w("ACE_DEBUG", "Dealer loop: long waiting for ace dialog — forcing resolution now")
                forceResolvePendingAcesAsOne()
            } else {
                handler.postDelayed({ drawDealerCardsSequentially() }, 400)
                return
            }
        }

        if (dealerScore >= 17) {
            handler.postDelayed({ checkRoundResult() }, 700)
            return
        }

        if (deck.isEmpty()) {
            createDeck(); shuffleDeck()
        }

        val card = deck.removeAt(0)
        dealerScore += getCardValue(card)
        if (dealerScore > 21 && card.startsWith("A")) dealerScore -= 10

        addDealerCard(card, hidden = false)
        updateScoreOnUI()
        handler.postDelayed({ drawDealerCardsSequentially() }, 700)
    }

    // -------------------- Round result --------------------
    private fun checkRoundResult() {
        // Avoid double-entering
        if (roundResolving) return

        // mark resolving
        roundResolving = true
        roundResolutionScheduled = false

        if (aceDialogInProgress) {
            // if ace dialog still present - give a short grace and watchdog will eventually forceResolve
            handler.postDelayed({ checkRoundResult() }, 400)
            roundResolving = false
            return
        }

        // recalc final playerScore using preference
        playerScore = calculateBestScoreWithPreference(playerCards, mainPreferAceAsOne)

        Log.d(
            "ROUND_TEST",
            "Start_checkRoundResult():" +
                    "playerScore=$playerScore dealerScore=$dealerScore currentBet=$currentBet" +
                    "playerMoney=${currentPlayer().money}"
        )

        when {
            playerScore > 21 -> {
                Log.d("ROUND_TEST", "RESULT = BUST | lose=$currentBet")
                binding.playerMoney.text = "${currentPlayer().name} – $${currentPlayer().money}"
                showLoseOverlay(currentBet)
            }

            dealerScore > 21 || playerScore > dealerScore -> {
                val payout = currentBet * 2
                currentPlayer().money += payout
                Log.d("ROUND_TEST", "RESULT = WIN | payout=$payout | moneyAfter=${currentPlayer().money}")
                binding.playerMoney.text = "${currentPlayer().name} – $${currentPlayer().money}"
                showWinOverlay(payout)
                currentBet = 0
            }

            dealerScore == playerScore -> {
                currentPlayer().money += currentBet
                Log.d("ROUND_TEST", "RESULT = PUSH | refunded=$currentBet | moneyAfter=${currentPlayer().money}")
                binding.playerMoney.text = "${currentPlayer().name} – $${currentPlayer().money}"
                showBlackjackOverlay("push")
                currentBet = 0
            }

            else -> {
                Log.d("ROUND_TEST", "RESULT = LOSS | lost=$currentBet | moneyAfter=${currentPlayer().money}")
                binding.playerMoney.text = "${currentPlayer().name} – $${currentPlayer().money}"
                showLoseOverlay(currentBet)
                currentBet = 0
            }
        }
    }

    private fun goToWin() {
        binding.playerMoney.text = "${currentPlayer().name} – $${currentPlayer().money}"
        startWinsActivity()
    }

    private fun goToLose() {
        binding.playerMoney.text = "${currentPlayer().name} – $${currentPlayer().money}"
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

    // ---------- Overlays (win/lose/blackjack) ----------
    private fun showWinOverlay(amount: Int = currentBet) {
        val overlay = binding.winOverlay
        binding.winTotalMoney.text = "${currentPlayer().name} – $${currentPlayer().money}"
        binding.winAmountText.text = "+$amount"
        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        overlay.scaleX = 0.8f
        overlay.scaleY = 0.8f

        overlay.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(400)
            .withEndAction {
                handler.postDelayed({
                    overlay.animate().alpha(0f).setDuration(400).withEndAction {
                        overlay.visibility = View.GONE
                        currentBet = 0

                        roundResolving = false
                        roundResolutionScheduled = false

                        // Reset UI state for next round
                        resetHandsForNewRound()

                        if (currentPlayer().money < 10) {
                            showEliminatedOverlay(currentPlayer().name)
                        } else {
                            advanceToNextPlayer()
                        }
                    }.start()
                }, 700)
            }.start()
    }

    private fun showLoseOverlay(amount: Int = currentBet) {
        val overlay = binding.loseOverlay
        binding.loseTotalMoney.text = "${currentPlayer().name} – $${currentPlayer().money}"
        binding.loseAmountText.text = "-$amount"
        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        overlay.scaleX = 0.8f
        overlay.scaleY = 0.8f

        overlay.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(400)
            .withEndAction {
                handler.postDelayed({
                    overlay.animate().alpha(0f).setDuration(400).withEndAction {
                        overlay.visibility = View.GONE
                        currentBet = 0

                        roundResolving = false
                        roundResolutionScheduled = false

                        // Reset UI state for next round
                        resetHandsForNewRound()

                        if (currentPlayer().money < 10) {
                            showEliminatedOverlay(currentPlayer().name)
                        } else {
                            advanceToNextPlayer()
                        }
                    }.start()
                }, 700)
            }.start()
    }

    private fun showEliminatedOverlay(playerName: String) {
        val overlay = binding.loseOverlay
        binding.loseAmountText.text = "$playerName IS OUT!"
        binding.loseTotalMoney.text = "Money: $0"

        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        overlay.scaleX = 0.8f
        overlay.scaleY = 0.8f

        overlay.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(400)
            .withEndAction {
                handler.postDelayed({
                    overlay.animate().alpha(0f).setDuration(400).withEndAction {
                        overlay.visibility = View.GONE

                        roundResolving = false
                        roundResolutionScheduled = false

                        // Remove player from list and continue
                        if (players.isNotEmpty() && currentPlayerIndex < players.size) {
                            players.removeAt(currentPlayerIndex)
                        }

                        if (players.isEmpty()) {
                            finish()
                            return@withEndAction
                        }
                        if (currentPlayerIndex >= players.size) {
                            currentPlayerIndex = 0
                        }

                        // Reset UI and go to next player's turn
                        resetHandsForNewRound()
                        updatePlayerHeader()
                        askForBet()
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
                binding.blackjackTotal.text = "${currentPlayer().name} – $${currentPlayer().money}"
            }

            "dealer" -> {
                binding.blackjackTitle.text = "DEALER BLACKJACK"
                binding.blackjackAmount.text = "-$currentBet"
                binding.blackjackTotal.text = "${currentPlayer().name} – $${currentPlayer().money}"
            }

            "push" -> {
                binding.blackjackTitle.text = "PUSH"
                binding.blackjackAmount.text = "+0"
                binding.blackjackTotal.text = "${currentPlayer().name} – $${currentPlayer().money}"
            }
        }

        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        overlay.scaleX = 0.8f
        overlay.scaleY = 0.8f

        overlay.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(400)
            .withEndAction {
                handler.postDelayed({
                    overlay.animate().alpha(0f).setDuration(400).withEndAction {
                        overlay.visibility = View.GONE
                        currentBet = 0

                        roundResolving = false
                        roundResolutionScheduled = false

                        // Reset UI state for next round
                        resetHandsForNewRound()

                        advanceToNextPlayer()
                    }.start()
                }, 900)
            }.start()
    }

    private fun showMixedSplitOverlay(pushCount: Int, winCount: Int, lossCount: Int, net: Int) {
        val overlay = binding.loseOverlay

        binding.loseTotalMoney.text = "${currentPlayer().name} - $${currentPlayer().money}"
        binding.loseAmountText.text = "PUSH: $pushCount | WIN: $winCount | LOSE: $lossCount\nNet: $net"

        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        overlay.scaleX = 0.8f
        overlay.scaleY = 0.8f

        overlay.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(400)
            .withEndAction {
                handler.postDelayed({
                    overlay.animate().alpha(0f).setDuration(400).withEndAction {
                        overlay.visibility = View.GONE

                        roundResolving = false
                        roundResolutionScheduled = false

                        // Reset UI state for next round
                        resetHandsForNewRound()

                        advanceToNextPlayer()
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

    // ---------- UI helpers ----------
    private fun disablePlayerButtons() {
        binding.btnHit.isEnabled = false
        binding.btnStand.isEnabled = false
    }

    private fun enablePlayerButtons() {
        binding.btnHit.isEnabled = true
        binding.btnStand.isEnabled = true
    }

    private fun updateScoreOnUI() {
        if (!isSplit) {
            binding.playerScore.visibility = View.VISIBLE
            binding.playerScore.text = "Player: ${formatAceScore(playerCards, mainPreferAceAsOne)}"
            // numeric score using preference
            playerScore = calculateBestScoreWithPreference(playerCards, mainPreferAceAsOne)

            Log.d("ACE_DEBUG", "updateScoreOnUi: Player cards = $playerCards -> ${binding.playerScore.text}")

            // Global bust check (only when no round resolution ongoing)
            if (!roundResolving && !roundResolutionScheduled) {
                if (playerScore > 21) {
                    roundResolutionScheduled = true
                    disablePlayerButtons()
                    handler.postDelayed({ checkRoundResult() }, 300)
                    return
                } else {
                    // normal state — allow buttons
                    binding.btnHit.isEnabled = true
                    binding.btnStand.isEnabled = true
                }
            }
        } else {
            binding.playerScore.visibility = View.GONE
        }

        binding.dealerScore.text = "Dealer: $dealerScore"
    }

    private fun updateSplitScoresOnUi() {
        if (!isSplit) {
            binding.playerHand1Score.visibility = View.GONE
            binding.playerHand2Score.visibility = View.GONE
            return
        }

        binding.playerHand1Score.visibility = View.VISIBLE
        binding.playerHand2Score.visibility = View.VISIBLE

        binding.playerHand1Score.text = "Hand1: ${formatAceScore(hand1Cards, hand1PreferAceAsOne)}"
        binding.playerHand2Score.text = "Hand2: ${formatAceScore(hand2Cards, hand2PreferAceAsOne)}"

        // numeric recalc using preferences
        hand1Score = calculateBestScoreWithPreference(hand1Cards, hand1PreferAceAsOne)
        hand2Score = calculateBestScoreWithPreference(hand2Cards, hand2PreferAceAsOne)

        Log.d("ACE_DEBUG", "updateSplitScores: hand1=$hand1Cards -> $hand1Score; hand2=$hand2Cards -> $hand2Score")
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
     */
    private fun calculateBestScore(cards: List<String>): Int {
        if (cards.isEmpty()) return 0
        var total = 0
        var aceCount = 0
        for (c in cards) {
            val rank = c.dropLast(1)
            when (rank) {
                "A" -> {
                    total += 11; aceCount++
                }
                "K", "Q", "J" -> total += 10
                else -> total += rank.toIntOrNull() ?: 0
            }
        }
        while (total > 21 && aceCount > 0) {
            total -= 10
            aceCount--
        }
        return total
    }

    // calculate score applying a "prefer ace as 1 once" flag
    private fun calculateBestScoreWithPreference(cards: List<String>, preferOneAceAsOne: Boolean): Int {
        if (cards.isEmpty()) return 0
        var total = 0
        var aces = 0
        for (c in cards) {
            val rank = c.dropLast(1)
            when (rank) {
                "A" -> {
                    total += 11; aces++
                }
                "K", "Q", "J" -> total += 10
                else -> total += rank.toIntOrNull() ?: 0
            }
        }
        if (preferOneAceAsOne && aces > 0) {
            total -= 10
        } else {
            while (total > 21 && aces > 0) {
                total -= 10
                aces--
            }
        }
        return total
    }

    // -------------------- Ace prompt + handling --------------------
    /**
     * Prompt the user to choose Ace = 1 or 11 for a given hand.
     * handTag must be exactly one of: "main", "hand1", "hand2"
     */
    private fun promptAceChoiceFor(handTag: String, cardCode: String) {
        Log.d("ACE_DEBUG", "promptAceChoiceFor() called for: $handTag card=$cardCode")

        // If this ace was already chosen, skip dialog
        manualAceValues[cardCode]?.let {
            recalcAndRefreshUi()
            return
        }

        if (aceDialogInProgress) {
            Log.d("ACE_DEBUG", "promptAceChoiceFor(): a dialog already in progress — skipping new prompt")
            return
        }

        aceDialogInProgress = true
        disablePlayerButtons()

        // start watchdog
        aceDialogWatchdogStart = System.currentTimeMillis()

        val builder = AlertDialog.Builder(this)
            .setTitle("Ace value")
            .setMessage("Count this Ace as 1 or 11?")
            .setPositiveButton("11") { dialogInterface, _ ->
                manualAceValues[cardCode] = 11
                when (handTag) {
                    "main" -> mainPreferAceAsOne = false
                    "hand1" -> hand1PreferAceAsOne = false
                    "hand2" -> hand2PreferAceAsOne = false
                }

                recalcAndRefreshUi()

                // If main hand busted after choice -> schedule resolution
                if (!isSplit && calculateBestScoreWithPreference(playerCards, mainPreferAceAsOne) > 21) {
                    aceDialogInProgress = false
                    disablePlayerButtons()
                    dialogInterface.dismiss()
                    handler.postDelayed({ checkRoundResult() }, 250)
                    return@setPositiveButton
                }

                // split handling
                if (isSplit) {
                    hand1Score = calculateBestScoreWithPreference(hand1Cards, hand1PreferAceAsOne)
                    hand2Score = calculateBestScoreWithPreference(hand2Cards, hand2PreferAceAsOne)

                    if (handTag == "hand1" && hand1Score > 21) {
                        isPlayingHand1 = false
                        updateSplitHandHighLight()
                    }

                    if (handTag == "hand2" && hand2Score > 21) {
                        disablePlayerButtons()
                        dialogInterface.dismiss()
                        aceDialogInProgress = false
                        handler.postDelayed({ dealerPlayAfterSplit() }, 250)
                        return@setPositiveButton
                    }
                }

                aceDialogInProgress = false
                enablePlayerButtons()
                dialogInterface.dismiss()
            }
            .setNegativeButton("1") { dialogInterface, _ ->
                manualAceValues[cardCode] = 1

                when (handTag) {
                    "main" -> mainPreferAceAsOne = true
                    "hand1" -> hand1PreferAceAsOne = true
                    "hand2" -> hand2PreferAceAsOne = true
                }

                recalcAndRefreshUi()

                if (!isSplit && calculateBestScoreWithPreference(playerCards, mainPreferAceAsOne) > 21) {
                    aceDialogInProgress = false
                    disablePlayerButtons()
                    dialogInterface.dismiss()
                    handler.postDelayed({ checkRoundResult() }, 250)
                    return@setNegativeButton
                }

                if (isSplit) {
                    hand1Score = calculateBestScoreWithPreference(hand1Cards, hand1PreferAceAsOne)
                    hand2Score = calculateBestScoreWithPreference(hand2Cards, hand2PreferAceAsOne)

                    if (handTag == "hand1" && hand1Score > 21) {
                        isPlayingHand1 = false
                        updateSplitHandHighLight()
                    }

                    if (handTag == "hand2" && hand2Score > 21) {
                        disablePlayerButtons()
                        dialogInterface.dismiss()
                        aceDialogInProgress = false
                        handler.postDelayed({ dealerPlayAfterSplit() }, 250)
                        return@setNegativeButton
                    }
                }

                aceDialogInProgress = false
                enablePlayerButtons()
                dialogInterface.dismiss()
            }
            .setCancelable(true)

        val dlg = builder.show()

        dlg.setOnDismissListener {
            // reset watchdog + flag
            aceDialogWatchdogStart = null
            aceDialogInProgress = false
            enablePlayerButtons()
            recalcAndRefreshUi()
        }
        dlg.setOnCancelListener {
            aceDialogWatchdogStart = null
            aceDialogInProgress = false
            enablePlayerButtons()
            recalcAndRefreshUi()
        }

        // Start watchdog check slightly after timeout (non-blocking)
        handler.postDelayed({
            try {
                val start = aceDialogWatchdogStart
                if (aceDialogInProgress && start != null && System.currentTimeMillis() - start >= ACE_DIALOG_TIMEOUT_MS) {
                    Log.w("ACE_DEBUG", "Ace dialog watchdog fired — auto-resolving remaining aces as 1 to avoid deadlock")
                    forceResolvePendingAcesAsOne()
                    try {
                        if (dlg.isShowing) dlg.dismiss()
                    } catch (_: Exception) { }
                }
            } catch (e: Exception) {
                Log.e("ACE_DEBUG", "watchdog error: ${e.message}")
            }
        }, ACE_DIALOG_TIMEOUT_MS + 200)
    }

    // Recalculate numeric scores using preferences and refresh UIs
    private fun recalcAndRefreshUi() {
        playerScore = calculateBestScoreWithPreference(playerCards, mainPreferAceAsOne)
        hand1Score = calculateBestScoreWithPreference(hand1Cards, hand1PreferAceAsOne)
        hand2Score = calculateBestScoreWithPreference(hand2Cards, hand2PreferAceAsOne)

        updateScoreOnUI()
        updateSplitScoresOnUi()
    }

    private fun formatAceScore(cards: List<String>, preferAceAsOne: Boolean = false): String {
        if (cards.isEmpty()) return "0"

        var total = 0
        var softTotal = 0
        var aceCount = 0

        for (c in cards) {
            val rank = c.dropLast(1)

            if (rank == "A") {
                aceCount++
                val manual = manualAceValues[c]

                if (manual != null) {
                    total += manual
                    softTotal += manual
                } else {
                    // default before user picks → 11
                    total += 11
                    softTotal += 11
                }
            } else {
                val v = when (rank) {
                    "K", "Q", "J" -> 10
                    else -> rank.toIntOrNull() ?: 0
                }
                total += v
                softTotal += v
            }
        }

        // If all aces have been manually chosen → show ONLY the chosen total
        if (cards.any { manualAceValues.containsKey(it) }) {
            return total.toString()
        }

        // No manual choice yet → classic soft/hard display
        var altTotal = softTotal
        var ac = aceCount
        while (altTotal > 21 && ac > 0) {
            altTotal -= 10
            ac--
        }

        return if (aceCount > 0 && softTotal != altTotal)
            "$softTotal (or $altTotal)"
        else
            "$altTotal"
    }

    private fun generateRandomName(): String {
        val names = listOf(
            "Alex", "Blake", "Charlie", "Dakota", "Eden", "Finn", "Harper",
            "Indigo", "Jordan", "Kai", "Luca", "Milan", "Nova", "Phoenix",
            "Quinn", "Riley", "Sky", "Tatum", "Winter", "Zion"
        )
        return names.random()
    }

    // IF ace dialog freezes
    private fun forceResolvePendingAcesAsOne() {
        // Mark all not-manually-decided aces as 1 for all hands
        val allHands = listOf(playerCards, hand1Cards, hand2Cards)
        for (hand in allHands) {
            for (c in hand) {
                if (c.startsWith("A") && !manualAceValues.containsKey(c)) {
                    manualAceValues[c] = 1
                }
            }
        }
        // prefer flags
        mainPreferAceAsOne = true
        hand1PreferAceAsOne = true
        hand2PreferAceAsOne = true

        // clear watchdog + dialog flag and refresh UI
        aceDialogWatchdogStart = null
        aceDialogInProgress = false

        // Ensure UI and score updated before continuing
        recalcAndRefreshUi()

        // If a resolution was pending, run it now (safeguard)
        if (roundResolutionScheduled && !roundResolving) {
            handler.post { checkRoundResult() }
        }
    }
}