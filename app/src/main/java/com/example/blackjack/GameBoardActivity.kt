package com.example.blackjack

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

    //Multiplayer
    private var playerCount = 1
    private var players = mutableListOf<Player>()
    private var currentPlayerIndex =0

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Multiplayer
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

    //----------------Create Players--------------------

    private fun createPlayers() {
        players.clear()

        for (i in 1..playerCount) {
            val randomName = generateRandomName()
            players.add(Player(id= i, name= randomName))
        }
        currentPlayerIndex = 0
    }

    //-------Multiplayer helpers----------------------

    private fun currentPlayer() : Player= players[currentPlayerIndex]

    private fun updatePlayerHeader() {
        val p = currentPlayer()
        binding.playerMoney.text = "${p.name} – $${p.money}"
    }
    private fun advanceToNextPlayer() {
        currentPlayerIndex = (currentPlayerIndex + 1) % playerCount

        //------Reset UI
        binding.playerCards.removeAllViews()
        binding.dealerCards.removeAllViews()
        binding.playerScore.text = "Player: 0"
        binding.dealerScore.text = "Dealer: 0"

        updatePlayerHeader()
        askForBet()
    }



    // -------------------- Betting --------------------
    private fun askForBet() {
        if (currentPlayer().money < 10) {
            showNoMoneyDialog()
            return
        }

        val bets = arrayOf("10", "20", "50", "100", "Fold")

        AlertDialog.Builder(this)
            .setTitle("${currentPlayer().name} – Place Your Bet")
            .setItems(bets) { _, which ->
                if (bets[which] == "Fold") {
                    finish()
                    return@setItems
                }
                currentBet = bets[which].toInt()
                if (currentBet > currentPlayer().money) currentBet = 10

                // Deduct the player's bet now (split will deduct again if used)
                currentPlayer().money -= currentBet
               updatePlayerHeader()

                dealInitialCards()
            }
            .setCancelable(false)
            .show()
    }

    // -------------------- Deal initial --------------------
    private fun dealInitialCards() {
        enablePlayerButtons()
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
            // small post so UI settles
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
        if (!isSplit) {
            hitCardToPlayer()
            updateScoreOnUI()
            // Player can no longer split after hitting
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

        if (hand1Score > 21) {
            // will be handled in prompt or directly in UI flow
            return
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
        if (!isSplit) {
            dealerPlay()
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

        if(aceDialogInProgress){
            handler.postDelayed({drawDealerCardsSequentiallyAfterSplit()}, 400)
            return
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

        // HAND 1: add whatever this hand should return (0 = lost, currentBet = push, currentBet*2 = win)
        totalWin += when {
            hand1Bust -> 0
            dealerScore > 21 || hand1Score > dealerScore -> currentBet * 2 // win -> return bet + win
            hand1Score < dealerScore -> 0 // loss -> nothing returned
            else -> currentBet // push -> return bet only
        }

        // HAND 2: same rules
        totalWin += when {
            hand2Bust -> 0
            dealerScore > 21 || hand2Score > dealerScore -> currentBet * 2
            hand2Score < dealerScore -> 0
            else -> currentBet
        }

        // Debugging log so you can trace exact result in LogCat
        Log.d(
            "ACE_DEBUG",
            "evaluateSplitResults(): hand1Score=$hand1Score hand2Score=$hand2Score dealerScore=$dealerScore " +
                    "hand1Bust=$hand1Bust hand2Bust=$hand2Bust totalBet=$totalBet totalWin=$totalWin"
        )

        // Apply money result
        currentPlayer().money += totalWin
        binding.playerMoney.text = "${currentPlayer().name} – $${currentPlayer().money}"

        // Decide which overlay to show:
        // - If totalWin > totalBet -> player got back more than they staked (net win). Show Win overlay.
        // - If totalWin == totalBet -> effectively push (got back exactly the stake). Show push overlay.
        // - If totalWin == 0 -> lost all bets (show Lose overlay for totalBet).
        when {
            totalWin > totalBet -> {
                // Player received more than they staked -> show win with the returned amount
                binding.winAmountText.text = "+$totalWin"
                showWinOverlay(totalWin)
            }
            totalWin == totalBet -> {
                // Everything returned equals stake -> treat as push
                showBlackjackOverlay("push")
            }
            else -> {
                // Player got nothing back -> lost entire stake (totalBet)
                binding.loseAmountText.text = "-$totalBet"
                showLoseOverlay(totalBet)
            }
        }

        // Reset split state & UI back to normal
        isSplit = false
        isPlayingHand1 = true

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
        if(aceDialogInProgress){
            handler.postDelayed({dealerPlay()},400)
            return
        }


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
        if(aceDialogInProgress) {
            handler.postDelayed({drawDealerCardsSequentially()}, 400)
            return
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

        if(aceDialogInProgress) {
            handler.postDelayed({checkRoundResult()},400)
        }

        // recalc final playerScore using preference
        playerScore = calculateBestScoreWithPreference(playerCards, mainPreferAceAsOne)

        when {
            playerScore > 21 -> {
                binding.playerMoney.text = "${currentPlayer().name} – $${currentPlayer().money}"
                showLoseOverlay(currentBet)
            }
            dealerScore > 21 || playerScore > dealerScore -> {
                val payout = currentBet * 2
                currentPlayer().money += payout
                binding.playerMoney.text = "${currentPlayer().name} – $${currentPlayer().money}"
                showWinOverlay(payout)
            }
            dealerScore == playerScore -> {
                currentPlayer().money += currentBet
                binding.playerMoney.text = "${currentPlayer().name} – $${currentPlayer().money}"
                showBlackjackOverlay("push")
            }
            else -> {
                binding.playerMoney.text = "${currentPlayer().name} – $${currentPlayer().money}"
                showLoseOverlay(currentBet)
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
                        askForBet()
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
                        if (currentPlayer().money < 10) startLooseActivity() else askForBet()
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
                "A" -> { total += 11; aceCount++ }
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
                "A" -> { total += 11; aces++ }
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

        // If this ace already has a chosen value -> skip
        manualAceValues[cardCode]?.let { saved ->
            Log.d("ACE_DEBUG", "Ace $cardCode already chosen as $saved — skipping prompt")
            recalcAndRefreshUi()
            return
        }

        if (aceDialogInProgress) {
            Log.d("ACE_DEBUG", "Dialog already up — ignore")
            return
        }

        aceDialogInProgress = true
        disablePlayerButtons()

        AlertDialog.Builder(this)
            .setTitle("Ace value")
            .setMessage("Count this Ace as 1 or 11?")
            .setPositiveButton("11") { dialog, _ ->

                Log.d("ACE_DEBUG", "User chose ACE=11 for $handTag ($cardCode)")

                manualAceValues[cardCode] = 11

                when (handTag) {
                    "main" -> mainPreferAceAsOne = false
                    "hand1" -> hand1PreferAceAsOne = false
                    "hand2" -> hand2PreferAceAsOne = false
                }

                recalcAndRefreshUi()
                aceDialogInProgress = false
                enablePlayerButtons()
                dialog.dismiss()
            }
            .setNegativeButton("1") { dialog, _ ->

                Log.d("ACE_DEBUG", "User chose ACE=1 for $handTag ($cardCode)")

                manualAceValues[cardCode] = 1

                when (handTag) {
                    "main" -> mainPreferAceAsOne = true
                    "hand1" -> hand1PreferAceAsOne = true
                    "hand2" -> hand2PreferAceAsOne = true
                }

                recalcAndRefreshUi()
                aceDialogInProgress = false
                enablePlayerButtons()
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
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

        var softTotal = 0
        var aceCount = 0

        for (c in cards) {
            val rank = c.dropLast(1)
            when (rank) {
                "A" -> {
                    softTotal += 11
                    aceCount++
                }
                "K", "Q", "J" -> softTotal += 10
                else -> softTotal += rank.toIntOrNull() ?: 0
            }
        }

        // If user explicitly prefers Ace as 1 -> always subtract 10 once
        if (preferAceAsOne && aceCount > 0) {
            val forced = softTotal - 10
            return forced.toString()
        }

        // Normal soft/hard calculation
        var altTotal = softTotal
        var ac = aceCount
        while (altTotal > 21 && ac > 0) {
            altTotal -= 10
            ac--
        }

        return if (aceCount > 0 && softTotal != altTotal) {
            "$softTotal (or $altTotal)"
        } else {
            "$altTotal"
        }
    }

    private fun generateRandomName(): String {
        val names = listOf(
            "Alex",
            "Blake",
            "Charlie",
            "Dakota",
            "Eden",
            "Finn",
            "Harper",
            "Indigo",
            "Jordan",
            "Kai",
            "Luca",
            "Milan",
            "Nova",
            "Phoenix",
            "Quinn",
            "Riley",
            "Sky",
            "Tatum",
            "Winter",
            "Zion"
        )

        return names.random()
    }

}