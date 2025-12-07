// GameEngine.kt
package com.example.blackjack

import kotlin.random.Random

/**
 * GameEngine
 *
 * Pure game logic for a single player's round vs dealer (supports split).
 * - No Android dependencies (uses Card and Deck).
 * - All communication happens via EngineEvent sealed classes.
 *
 * Usage:
 *   val engine = GameEngine()
 *   val ev = engine.startNewRound(bet)
 *   // react to ev in UI, call engine.setManualAceValue(...) when user chooses ace value
 *   // call engine.playerHit(), engine.playerStand(), engine.attemptSplit(), engine.dealerPlay() as needed
 *
 * Notes:
 *  - Card identity for manual ace choices is represented by a string code "RANKSUIT" (e.g. "AC", "10H").
 *  - Engine does not manage money/bets outside of returning winAmount in RoundResult; caller (Activity/Controller)
 *    should apply payouts to player's money.
 */
class GameEngine(
    seed: Long = System.currentTimeMillis(),
    rng: Random = Random(seed)
) {

    // ------------ Public model types ------------

    enum class Outcome {
        PLAYER_WIN,
        PLAYER_LOSE,
        PUSH,
        BLACKJACK_PLAYER,
        BLACKJACK_DEALER
    }

    enum class HandTag { MAIN, HAND1, HAND2 }

    sealed class EngineEvent {
        data class InitialDeal(
            val playerCardsMain: List<Card>,
            val playerCardsHand1: List<Card>?,
            val playerCardsHand2: List<Card>?,
            val dealerVisible: Card,
            val dealerHidden: Card
        ) : EngineEvent()

        data class CardDealt(val handTag: HandTag, val card: Card) : EngineEvent()
        data class DealerCardDealt(val card: Card) : EngineEvent()
        data class NeedAceChoice(val handTag: HandTag, val card: Card) : EngineEvent()
        data class HandBusted(val handTag: HandTag, val score: Int) : EngineEvent()
        data class Hand21(val handTag: HandTag, val score: Int) : EngineEvent()
        object SwitchToHand2 : EngineEvent()
        data class RoundResult(
            val outcome: Outcome,
            val winAmount: Int,
            val playerCardsMain: List<Card>?,
            val playerCardsHand1: List<Card>?,
            val playerCardsHand2: List<Card>?,
            val dealerCards: List<Card>
        ) : EngineEvent()

        data class BlackjackDetected(val who: Outcome, val payoutAmount: Int) : EngineEvent()
        object NoOp : EngineEvent()
    }

    // ------------ Internal state ------------

    private val deck = Deck(rng)
    private val dealerCards = mutableListOf<Card>()

    private val mainCards = mutableListOf<Card>()
    private val h1Cards = mutableListOf<Card>()
    private val h2Cards = mutableListOf<Card>()

    // manual chosen ace values keyed by cardCode (eg "AC", "10H")
    private val manualAceValues = mutableMapOf<String, Int>()

    var isSplit = false
        private set
    var isPlayingHand1 = true
        private set

    private var currentBet = 0
    var roundActive = false
        private set

    // ---------------- Public API ----------------

    /**
     * Start a new round with the provided bet.
     * Returns EngineEvent.InitialDeal (or NeedAceChoice if the initial hand contains an ace that needs user choice).
     */
    fun startNewRound(bet: Int): EngineEvent {
        resetRound()
        currentBet = if (bet < 0) 0 else bet
        ensureDeck()

        // draw two player cards
        val p1 = deck.draw()
        val p2 = deck.draw()
        mainCards.add(p1)
        mainCards.add(p2)

        // prepare split hands
        h1Cards.clear()
        h2Cards.clear()
        h1Cards.add(p1)
        h2Cards.add(p2)

        // dealer two cards
        val d1 = deck.draw()
        val d2 = deck.draw()
        dealerCards.add(d1)
        dealerCards.add(d2)

        roundActive = true
        isSplit = false
        isPlayingHand1 = true
        manualAceValues.clear()

        // compute scores
        val playerScore = calculateBestScoreWithPreference(mainCards, preferOneAceAsOne = false)
        val dealerBlackjack = (getCardNumericValue(d1) + getCardNumericValue(d2) == 21)
        val playerBlackjack = playerScore == 21

        // initial ace prompt if player has an ace and not blackjack
        val playerHasAce = mainCards.any { it.rank == "A" }
        val initialAceEvent = if (playerHasAce && !playerBlackjack) {
            EngineEvent.NeedAceChoice(HandTag.MAIN, mainCards.first { it.rank == "A" })
        } else EngineEvent.NoOp

        // If immediate blackjack situations -> return InitialDeal but caller should also handle blackjack detection.
        val initialDealEvent = EngineEvent.InitialDeal(
            playerCardsMain = mainCards.toList(),
            playerCardsHand1 = null,
            playerCardsHand2 = null,
            dealerVisible = d1,
            dealerHidden = d2
        )

        // If initial ace prompt required, prefer to return NeedAceChoice so UI can ask first
        return if (initialAceEvent is EngineEvent.NeedAceChoice) initialAceEvent else initialDealEvent
    }

    /**
     * Player requests a hit. Returns an EngineEvent describing action/result.
     */
    fun playerHit(): EngineEvent {
        if (!roundActive) return EngineEvent.NoOp

        if (!isSplit) {
            val card = deck.draw()
            mainCards.add(card)
            // If ace not resolved -> request choice
            if (card.rank == "A" && !manualAceValues.containsKey(cardCode(card))) {
                return EngineEvent.NeedAceChoice(HandTag.MAIN, card)
            }
            val score = calculateBestScoreWithPreference(mainCards, preferOneAceAsOne = handPreferFlagFromManuals(mainCards))
            if (score > 21) {
                roundActive = false
                return EngineEvent.HandBusted(HandTag.MAIN, score)
            } else if (score == 21) {
                return EngineEvent.Hand21(HandTag.MAIN, score)
            }
            return EngineEvent.CardDealt(HandTag.MAIN, card)
        } else {
            // split mode
            if (isPlayingHand1) {
                val card = deck.draw()
                h1Cards.add(card)
                if (card.rank == "A" && !manualAceValues.containsKey(cardCode(card))) {
                    return EngineEvent.NeedAceChoice(HandTag.HAND1, card)
                }
                val score = calculateBestScoreWithPreference(h1Cards, preferOneAceAsOne = handPreferFlagFromManuals(h1Cards))
                if (score > 21) {
                    // move to second hand automatically (UI should react to HandBusted and SwitchToHand2)
                    isPlayingHand1 = false
                    return EngineEvent.HandBusted(HandTag.HAND1, score)
                }
                return EngineEvent.CardDealt(HandTag.HAND1, card)
            } else {
                val card = deck.draw()
                h2Cards.add(card)
                if (card.rank == "A" && !manualAceValues.containsKey(cardCode(card))) {
                    return EngineEvent.NeedAceChoice(HandTag.HAND2, card)
                }
                val score = calculateBestScoreWithPreference(h2Cards, preferOneAceAsOne = handPreferFlagFromManuals(h2Cards))
                if (score > 21) {
                    roundActive = false
                    return EngineEvent.HandBusted(HandTag.HAND2, score)
                }
                return EngineEvent.CardDealt(HandTag.HAND2, card)
            }
        }
    }

    /**
     * Player chooses to stand on current hand.
     * If split and on hand1 -> returns SwitchToHand2.
     * If finishing last hand -> returns NoOp; caller should then call dealerPlay() to continue.
     */
    fun playerStand(): EngineEvent {
        if (!roundActive) return EngineEvent.NoOp

        if (!isSplit) {
            return EngineEvent.NoOp
        } else {
            if (isPlayingHand1) {
                isPlayingHand1 = false
                return EngineEvent.SwitchToHand2
            } else {
                // finished both hands
                return EngineEvent.NoOp
            }
        }
    }

    /**
     * Attempt to split the main hand. Returns true if allowed and performed.
     * After a successful split, caller must deduct the second bet externally.
     */
    fun attemptSplit(): Boolean {
        if (isSplit) return false
        if (mainCards.size != 2) return false

        val c1 = mainCards[0]
        val c2 = mainCards[1]
        val rank1 = c1.rank
        val rank2 = c2.rank
        val v1 = getCardNumericValue(c1)
        val v2 = getCardNumericValue(c2)
        val both10Value = (v1 == 10 && v2 == 10)
        val sameRank = rank1 == rank2
        if (!both10Value && !sameRank) return false

        isSplit = true
        isPlayingHand1 = true

        h1Cards.clear()
        h2Cards.clear()
        h1Cards.add(c1)
        h2Cards.add(c2)
        mainCards.clear()
        return true
    }

    /**
     * Dealer plays until soft/hard >= 17, then engine returns RoundResult.
     */
    fun dealerPlay(): EngineEvent {
        // reveal hidden card already included inside dealerCards list by startNewRound
        // Draw until dealer score >= 17 using soft-ace logic.
        ensureDeck()
        while (true) {
            val dScore = calculateBestScoreWithPreference(dealerCards, preferOneAceAsOne = false)
            if (dScore >= 17) break
            ensureDeck()
            val card = deck.draw()
            dealerCards.add(card)
        }

        // produce RoundResult
        val ev = evaluateRoundResultAndProduceEvent()
        roundActive = false
        return ev
    }

    /**
     * Provide manual ace value chosen by user for a specific card.
     * - handTag is purely informational (not required to find the card)
     * - chosenValue must be 1 or 11
     */
    fun setManualAceValue(handTag: HandTag, card: Card, chosenValue: Int) {
        if (chosenValue != 1 && chosenValue != 11) return
        manualAceValues[cardCode(card)] = chosenValue
    }

    /**
     * Force resolve all remaining (unresolved) aces as 1.
     */
    fun forceResolveAllAcesAsOne() {
        val all = listOf(mainCards, h1Cards, h2Cards, dealerCards)
        for (hand in all) {
            for (c in hand) {
                if (c.rank == "A" && !manualAceValues.containsKey(cardCode(c))) {
                    manualAceValues[cardCode(c)] = 1
                }
            }
        }
    }

    // Expose lists for UI to draw
    fun getPlayerCardsMain(): List<Card> = mainCards.toList()
    fun getPlayerCardsHand1(): List<Card>? = if (h1Cards.isNotEmpty()) h1Cards.toList() else null
    fun getPlayerCardsHand2(): List<Card>? = if (h2Cards.isNotEmpty()) h2Cards.toList() else null
    fun getDealerCards(): List<Card> = dealerCards.toList()
    fun getCurrentBet(): Int = currentBet

    fun getScore(handTag: HandTag): Int {
        return when (handTag) {
            HandTag.MAIN -> calculateBestScoreWithPreference(mainCards, preferOneAceAsOne = handPreferFlagFromManuals(mainCards))
            HandTag.HAND1 -> calculateBestScoreWithPreference(h1Cards, preferOneAceAsOne = handPreferFlagFromManuals(h1Cards))
            HandTag.HAND2 -> calculateBestScoreWithPreference(h2Cards, preferOneAceAsOne = handPreferFlagFromManuals(h2Cards))
        }
    }

    // ------------- Internal helpers --------------

    private fun resetRound() {
        dealerCards.clear()
        mainCards.clear()
        h1Cards.clear()
        h2Cards.clear()
        manualAceValues.clear()
        roundActive = false
        isSplit = false
        isPlayingHand1 = true
        currentBet = 0
    }

    private fun ensureDeck() {
        if (deck.remaining() < 6) deck.rebuildAndShuffle()
    }

    private fun cardCode(card: Card): String = "${card.rank}${card.suit}"

    private fun getCardNumericValue(card: Card): Int {
        // Use Card.value if provided, otherwise infer
        return card.value
    }

    /**
     * Calculate best score of a hand honoring manualAceValues where present.
     * preferOneAceAsOne = true forces treating one (non-manual) ace as 1 (subtract 10) once.
     */
    private fun calculateBestScoreWithPreference(cards: List<Card>, preferOneAceAsOne: Boolean): Int {
        if (cards.isEmpty()) return 0
        var total = 0
        var aces = 0
        for (c in cards) {
            if (c.rank == "A") {
                val manual = manualAceValues[cardCode(c)]
                if (manual != null) {
                    total += manual
                } else {
                    total += 11
                    aces++
                }
            } else {
                total += getCardNumericValue(c)
            }
        }
        if (preferOneAceAsOne && aces > 0) {
            total -= 10
            return total
        }
        var rem = aces
        while (total > 21 && rem > 0) {
            total -= 10
            rem--
        }
        return total
    }

    private fun calculateBestScore(cards: List<Card>): Int {
        if (cards.isEmpty()) return 0
        var total = 0
        var aces = 0
        for (c in cards) {
            if (c.rank == "A") {
                total += 11
                aces++
            } else {
                total += getCardNumericValue(c)
            }
        }
        while (total > 21 && aces > 0) {
            total -= 10
            aces--
        }
        return total
    }

    private fun handPreferFlagFromManuals(cards: List<Card>): Boolean {
        for (c in cards) {
            if (c.rank == "A" && manualAceValues[cardCode(c)] == 1) return true
        }
        return false
    }

    /**
     * Evaluate round result and produce EngineEvent.RoundResult describing outcome and winAmount.
     *
     * For non-split: winAmount = 2 * bet for win, = bet for push, = 0 for loss.
     * For split: compute aggregated returns (caller must have deducted two bets earlier).
     */
    private fun evaluateRoundResultAndProduceEvent(): EngineEvent {
        val dealerScoreFinal = calculateBestScoreWithPreference(dealerCards, preferOneAceAsOne = false)

        if (!isSplit) {
            val playerScoreFinal = calculateBestScoreWithPreference(mainCards, preferOneAceAsOne = handPreferFlagFromManuals(mainCards))
            val outcome: Outcome
            val winAmount: Int

            when {
                playerScoreFinal > 21 -> {
                    outcome = Outcome.PLAYER_LOSE
                    winAmount = 0
                }
                dealerScoreFinal > 21 || playerScoreFinal > dealerScoreFinal -> {
                    outcome = Outcome.PLAYER_WIN
                    winAmount = currentBet * 2
                }
                dealerScoreFinal == playerScoreFinal -> {
                    outcome = Outcome.PUSH
                    winAmount = currentBet
                }
                else -> {
                    outcome = Outcome.PLAYER_LOSE
                    winAmount = 0
                }
            }

            return EngineEvent.RoundResult(
                outcome = outcome,
                winAmount = winAmount,
                playerCardsMain = mainCards.toList(),
                playerCardsHand1 = null,
                playerCardsHand2 = null,
                dealerCards = dealerCards.toList()
            )
        } else {
            val h1Score = calculateBestScoreWithPreference(h1Cards, preferOneAceAsOne = handPreferFlagFromManuals(h1Cards))
            val h2Score = calculateBestScoreWithPreference(h2Cards, preferOneAceAsOne = handPreferFlagFromManuals(h2Cards))

            val h1Bust = h1Score > 21
            val h2Bust = h2Score > 21

            val totalBet = currentBet * 2
            var totalWin = 0

            totalWin += when {
                h1Bust -> 0
                dealerScoreFinal > 21 || h1Score > dealerScoreFinal -> currentBet * 2
                h1Score < dealerScoreFinal -> 0
                else -> currentBet
            }

            totalWin += when {
                h2Bust -> 0
                dealerScoreFinal > 21 || h2Score > dealerScoreFinal -> currentBet * 2
                h2Score < dealerScoreFinal -> 0
                else -> currentBet
            }

            val outcome = when {
                totalWin == 0 -> Outcome.PLAYER_LOSE
                totalWin == totalBet -> Outcome.PUSH
                totalWin > totalBet -> Outcome.PLAYER_WIN
                else -> Outcome.PUSH
            }

            return EngineEvent.RoundResult(
                outcome = outcome,
                winAmount = totalWin,
                playerCardsMain = null,
                playerCardsHand1 = h1Cards.toList(),
                playerCardsHand2 = h2Cards.toList(),
                dealerCards = dealerCards.toList()
            )
        }
    }
}