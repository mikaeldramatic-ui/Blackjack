// GameEngine.kt
package com.example.blackjack

import android.util.Log
import kotlin.random.Random

class GameEngine(
    seed: Long = System.currentTimeMillis(),
    rng: Random = Random(seed)
) {

    private fun log(msg: String) {
        try { Log.d("BJ_ENG", msg) } catch (_: Throwable) {}
    }

    enum class Outcome { PLAYER_WIN, PLAYER_LOSE, PUSH, BLACKJACK_PLAYER, BLACKJACK_DEALER }
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

        object NoOp : EngineEvent()
        data class BlackjackDetected(val who: Outcome, val payoutAmount: Int) : EngineEvent()
    }

    // ---------------- Internal state ----------------
    private val deck = Deck(rng)
    private val dealerCards = mutableListOf<Card>()

    private val mainCards = mutableListOf<Card>()
    private val h1Cards = mutableListOf<Card>()
    private val h2Cards = mutableListOf<Card>()

    // manual chosen ace values keyed by code "AS", "10H", etc.
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
     * Returns InitialDeal or NeedAceChoice (if initial hand contains an ace that needs user).
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

        // prepare split lists (kept so UI can show them if split later)
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

        val playerScore = calculateBestScoreWithPreference(mainCards, preferOneAceAsOne = false)
        val playerBlackjack = (playerScore == 21)
        val dealerBlackjack = (getCardNumericValue(d1) + getCardNumericValue(d2) == 21)

        // if player has an ace (and not immediate blackjack) ask for initial ace choice
        val playerHasAce = mainCards.any { it.rank == "A" }
        val initialAceEvent = if (playerHasAce && !playerBlackjack) {
            EngineEvent.NeedAceChoice(HandTag.MAIN, mainCards.first { it.rank == "A" })
        } else EngineEvent.NoOp

        val initialDealEvent = EngineEvent.InitialDeal(
            playerCardsMain = mainCards.toList(),
            playerCardsHand1 = null,
            playerCardsHand2 = null,
            dealerVisible = d1,
            dealerHidden = d2
        )

        log("startNewRound(bet=$bet) -> p1=${cardCode(p1)} p2=${cardCode(p2)} d1=${cardCode(d1)} d2=${cardCode(d2)}")
        log("playerScore=$playerScore playerBlackjack=$playerBlackjack dealerBJ=$dealerBlackjack initialAceEvent=${initialAceEvent::class.java.simpleName}")

        return if (initialAceEvent is EngineEvent.NeedAceChoice) initialAceEvent else initialDealEvent
    }

    /**
     * Player requests hit. Handles split and non-split flows.
     */
    fun playerHit(): EngineEvent {
        if (!roundActive) {
            log("playerHit() -> NoOp (roundActive=false)")
            return EngineEvent.NoOp
        }

        log("playerHit() called (isSplit=$isSplit isPlayingHand1=$isPlayingHand1)")

        if (!isSplit) {
            val card = deck.draw()
            mainCards.add(card)
            log("playerHit -> drew ${cardCode(card)}")

            if (card.rank == "A" && !manualAceValues.containsKey(cardCode(card))) {
                log("playerHit -> NeedAceChoice for ${cardCode(card)}")
                return EngineEvent.NeedAceChoice(HandTag.MAIN, card)
            }

            val score = calculateBestScoreWithPreference(mainCards, preferOneAceAsOne = handPreferFlagFromManuals(mainCards))
            log("playerHit -> main score after draw = $score")

            if (score > 21) {
                roundActive = false
                log("playerHit -> BUST main ($score)")
                return EngineEvent.HandBusted(HandTag.MAIN, score)
            } else if (score == 21) {
                log("playerHit -> reached 21 (main)")
                return EngineEvent.Hand21(HandTag.MAIN, score)
            }
            return EngineEvent.CardDealt(HandTag.MAIN, card)
        } else {
            // split mode (simpler behavior: treat each hand independently)
            if (isPlayingHand1) {
                val card = deck.draw()
                h1Cards.add(card)
                log("playerHit (hand1) -> drew ${cardCode(card)}")

                if (card.rank == "A" && !manualAceValues.containsKey(cardCode(card))) {
                    log("playerHit (hand1) -> NeedAceChoice ${cardCode(card)}")
                    return EngineEvent.NeedAceChoice(HandTag.HAND1, card)
                }

                val score = calculateBestScoreWithPreference(h1Cards, preferOneAceAsOne = handPreferFlagFromManuals(h1Cards))
                log("playerHit (hand1) score = $score")
                if (score > 21) {
                    // switch automatically to hand2 (UI will handle switching)
                    isPlayingHand1 = false
                    log("playerHit (hand1) -> busted, switching to hand2")
                    return EngineEvent.HandBusted(HandTag.HAND1, score)
                } else if (score == 21) {
                    // auto-stand for this hand
                    isPlayingHand1 = false
                    log("playerHit (hand1) -> reached 21, switching to hand2")
                    return EngineEvent.Hand21(HandTag.HAND1, score)
                }
                return EngineEvent.CardDealt(HandTag.HAND1, card)
            } else {
                val card = deck.draw()
                h2Cards.add(card)
                log("playerHit (hand2) -> drew ${cardCode(card)}")

                if (card.rank == "A" && !manualAceValues.containsKey(cardCode(card))) {
                    log("playerHit (hand2) -> NeedAceChoice ${cardCode(card)}")
                    return EngineEvent.NeedAceChoice(HandTag.HAND2, card)
                }

                val score = calculateBestScoreWithPreference(h2Cards, preferOneAceAsOne = handPreferFlagFromManuals(h2Cards))
                log("playerHit (hand2) score = $score")
                if (score > 21) {
                    roundActive = false
                    log("playerHit (hand2) -> busted")
                    return EngineEvent.HandBusted(HandTag.HAND2, score)
                } else if (score == 21) {
                    roundActive = false
                    log("playerHit (hand2) -> reached 21")
                    return EngineEvent.Hand21(HandTag.HAND2, score)
                }
                return EngineEvent.CardDealt(HandTag.HAND2, card)
            }
        }
    }

    /**
     * Player chooses to stand on current hand. For split-> switch to hand2 on first stand.
     */
    fun playerStand(): EngineEvent {
        if (!roundActive) {
            log("playerStand() -> NoOp (round not active)")
            return EngineEvent.NoOp
        }

        log("playerStand() called (isSplit=$isSplit isPlayingHand1=$isPlayingHand1)")

        if (!isSplit) {
            log("playerStand -> NoOp (not split), caller should call dealerPlay()")
            return EngineEvent.NoOp
        } else {
            if (isPlayingHand1) {
                isPlayingHand1 = false
                log("playerStand -> SwitchToHand2")
                return EngineEvent.SwitchToHand2
            } else {
                log("playerStand -> NoOp (finished both hands)")
                return EngineEvent.NoOp
            }
        }
    }

    /**
     * Attempt to split the main hand. Returns true if allowed and performed.
     */
    fun attemptSplit(): Boolean {
        log("attemptSplit() called")
        if (isSplit) {
            log("attemptSplit -> already split")
            return false
        }
        if (mainCards.size != 2) {
            log("attemptSplit -> mainCards.size != 2")
            return false
        }

        val c1 = mainCards[0]
        val c2 = mainCards[1]
        val rank1 = c1.rank
        val rank2 = c2.rank
        val v1 = getCardNumericValue(c1)
        val v2 = getCardNumericValue(c2)
        val both10Value = (v1 == 10 && v2 == 10)
        val sameRank = rank1 == rank2
        if (!both10Value && !sameRank) {
            log("attemptSplit -> not splittable (sameRank=$sameRank both10Value=$both10Value)")
            return false
        }

        isSplit = true
        isPlayingHand1 = true

        h1Cards.clear()
        h2Cards.clear()
        h1Cards.add(c1)
        h2Cards.add(c2)
        mainCards.clear()

        log("attemptSplit -> success. h1=${cardCode(c1)} h2=${cardCode(c2)}")
        return true
    }

    /**
     * Dealer plays to >=17 then returns RoundResult event.
     */
    fun dealerPlay(): EngineEvent {
        log("dealerPlay() start -> dealerCards=${dealerCards.map { cardCode(it) }}")
        ensureDeck()
        while (true) {
            val dScore = calculateBestScoreWithPreference(dealerCards, preferOneAceAsOne = false)
            log("dealerPlay -> current dealer score = $dScore")
            if (dScore >= 17) break
            ensureDeck()
            val card = deck.draw()
            dealerCards.add(card)
            log("dealerPlay -> dealer drew ${cardCode(card)} newScore=${calculateBestScoreWithPreference(dealerCards, false)}")
        }

        // produce RoundResult
        val ev = evaluateRoundResultAndProduceEvent()
        log("dealerPlay() end -> produced event ${ev::class.java.simpleName} (outcome=${(ev as? EngineEvent.RoundResult)?.outcome})")
        roundActive = false
        return ev
    }

    /**
     * Set manual ace value (1 or 11) for a specific card.
     */
    fun setManualAceValue(handTag: HandTag, card: Card, chosenValue: Int) {
        if (chosenValue != 1 && chosenValue != 11) {
            log("setManualAceValue -> invalid value $chosenValue for ${cardCode(card)}")
            return
        }
        manualAceValues[cardCode(card)] = chosenValue
        log("setManualAceValue -> ${cardCode(card)} = $chosenValue (hand=$handTag)")
    }

    /**
     * Force resolve all unresolved aces as 1.
     */
    fun forceResolveAllAcesAsOne() {
        val all = listOf(mainCards, h1Cards, h2Cards, dealerCards)
        for (hand in all) {
            for (c in hand) {
                if (c.rank == "A" && !manualAceValues.containsKey(cardCode(c))) {
                    manualAceValues[cardCode(c)] = 1
                    log("forceResolveAllAcesAsOne -> forcing ${cardCode(c)} = 1")
                }
            }
        }
    }

    // ---------------- Result computation ----------------
    private fun evaluateRoundResultAndProduceEvent(): EngineEvent {
        val dealerScoreFinal = calculateBestScoreWithPreference(dealerCards, preferOneAceAsOne = false)
        log("evaluateRoundResultAndProduceEvent -> dealerScoreFinal = $dealerScoreFinal")

        if (!isSplit) {
            val playerScoreFinal = calculateBestScoreWithPreference(mainCards, preferOneAceAsOne = handPreferFlagFromManuals(mainCards))
            log("evaluateRoundResultAndProduceEvent -> playerScoreFinal = $playerScoreFinal (main) currentBet=$currentBet")

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

            log("evaluateRoundResultAndProduceEvent -> outcome=$outcome winAmount=$winAmount")
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

            log("evaluateRoundResultAndProduceEvent (split) -> h1=$h1Score h2=$h2Score currentBet=$currentBet")

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

            log("evaluateRoundResultAndProduceEvent (split) -> outcome=$outcome totalWin=$totalWin totalBet=$totalBet")
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

    // ---------------- Utility getters ----------------
    fun getPlayerCardsMain(): List<Card> = mainCards.toList()
    fun getPlayerCardsHand1(): List<Card>? = if (h1Cards.isNotEmpty()) h1Cards.toList() else null
    fun getPlayerCardsHand2(): List<Card>? = if (h2Cards.isNotEmpty()) h2Cards.toList() else null
    fun getDealerCards(): List<Card> = dealerCards.toList()
    fun getDealerHiddenCard(): Card? = if (dealerCards.size >= 2) dealerCards[1] else null
    fun getDealerScore(): Int = calculateBestScoreWithPreference(dealerCards, preferOneAceAsOne = false)
    fun getDealerVisibleScore(): Int = dealerCards.firstOrNull()?.value ?: 0
    fun getScore(handTag: HandTag): Int {
        return when (handTag) {
            HandTag.MAIN -> calculateBestScoreWithPreference(mainCards, preferOneAceAsOne = handPreferFlagFromManuals(mainCards))
            HandTag.HAND1 -> calculateBestScoreWithPreference(h1Cards, preferOneAceAsOne = handPreferFlagFromManuals(h1Cards))
            HandTag.HAND2 -> calculateBestScoreWithPreference(h2Cards, preferOneAceAsOne = handPreferFlagFromManuals(h2Cards))
        }
    }
    fun getCurrentBet(): Int = currentBet

    fun canSplit(): Boolean {
        if (mainCards.size != 2) return false
        val c1 = mainCards[0]
        val c2 = mainCards[1]
        val sameRank = c1.rank == c2.rank
        val both10Value = (c1.value == 10 && c2.value == 10)
        return sameRank || both10Value
    }

    // ---------------- Internals ----------------
    private fun resetRound() {
        log("resetRound()")
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
        if (deck.remaining() < 6) {
            log("ensureDeck -> remaining < 6, rebuilding deck")
            deck.rebuildAndShuffle()
        }
    }

    private fun cardCode(card: Card): String = "${card.rank}${card.suit}"
    private fun getCardNumericValue(card: Card): Int = card.value

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

    private fun handPreferFlagFromManuals(cards: List<Card>): Boolean {
        for (c in cards) {
            if (c.rank == "A" && manualAceValues[cardCode(c)] == 1) return true
        }
        return false
    }

    fun moveToHand2() {
        isPlayingHand1 = false
    }
}