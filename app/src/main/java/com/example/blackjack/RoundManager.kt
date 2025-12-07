package com.example.blackjack

import android.os.Handler
import android.os.Looper

private fun log(msg: String) {
    android.util.Log.d("BJ_RM", msg)
}

class RoundManager(
    private val engine: GameEngine,
    private val players: PlayerController,
    private val ui: UIManager,
    private val handView: HandViewManager,
    private val overlays: OverlayManager,
    private val aceManager: AceDialogManager,
    private val listener: RoundListener
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var roundActive = false

    // protection against unwanted Ace dialogs after blackjack
    private var blackjackResolved = false

    // Ace dialog state
    data class WaitingAce(val handTag: GameEngine.HandTag, val card: Card)
    private var awaitingAce: WaitingAce? = null

    interface RoundListener {
        fun onNextPlayerRequested()
        fun onGameOver()
    }

    // -----------------------------------------------------
    // START ROUND
    // -----------------------------------------------------
    fun startRound() {
        // reset ace/dialog state (if AceDialogManager implements a forceCloseDialog)
        blackjackResolved = false
        awaitingAce = null
        try {
            aceManager.forceCloseDialog() // if missing, comment out or add in AceDialogManager
        } catch (t: Throwable) {
            // ignore if not implemented
            log("aceManager.forceCloseDialog() not available")
        }

        log("========= START ROUND =========")
        overlays.hideAll()

        if (players.isEmpty) {
            log("No players left -> game over")
            listener.onGameOver()
            return
        }

        val p = players.currentPlayer()
        log("Current player = ${p.name}, Money = ${p.money}, Bet = ${p.bet}")

        ui.disableAllButtons()
        overlays.hideAll()
        handView.clearAll()
        ui.resetScores()

        val bet = p.bet
        if (bet <= 0 || p.money < bet) {
            log("Player cannot bet -> moving to next")
            listener.onNextPlayerRequested()
            return
        }

        p.money -= bet
        ui.setMoney(p.money)
        ui.setBet(bet)
        ui.setTitle(p.name)

        val ev = engine.startNewRound(bet)
        roundActive = true

        log("Engine.startNewRound → $ev")
        handleEngineEvent(ev)
    }

    // -----------------------------------------------------
    // PLAYER HIT
    // -----------------------------------------------------
    fun playerHit() {
        if (!roundActive) {
            log("playerHit ignored → round not active")
            return
        }
        val ev = engine.playerHit()
        log("playerHit → $ev")
        handleEngineEvent(ev)
    }

    // -----------------------------------------------------
    // PLAYER STAND
    // -----------------------------------------------------
    fun playerStand() {
        if (!roundActive) {
            log("playerStand ignored → round not active")
            return
        }

        val ev = engine.playerStand()
        log("playerStand → $ev")

        when (ev) {

            is GameEngine.EngineEvent.SwitchToHand2 -> {
                log("Switch → Now playing HAND2")
                handView.focusHand2()
                ui.enableButtons(hit = true, stand = true, split = false)
            }

            is GameEngine.EngineEvent.NoOp -> {
                log("Stand finished → Checking if dealer should play")

                if (!engine.isSplit || !engine.isPlayingHand1) {
                    log("DealerPlay STARTED")
                    handleEngineEvent(engine.dealerPlay())
                }
            }

            else -> log("Stand → Ignored event type: ${ev.javaClass.simpleName}")
        }
    }

    // -----------------------------------------------------
    // SPLIT
    // -----------------------------------------------------
    fun attemptSplit() {
        val p = players.currentPlayer()

        val ok = engine.attemptSplit()
        if (!ok) {
            log("Split denied by engine")
            return
        }

        // Deduct second bet
        if (p.money < p.bet) {
            log("Player cannot afford split bet")
            return
        }

        p.money -= p.bet
        ui.setMoney(p.money)

        log("Split performed → showing separate hands")

        handView.enableSplitMode(
            engine.getPlayerCardsHand1() ?: emptyList(),
            engine.getPlayerCardsHand2() ?: emptyList()
        )

        handView.updateHand1Score(engine.getScore(GameEngine.HandTag.HAND1))
        handView.updateHand2Score(engine.getScore(GameEngine.HandTag.HAND2))

        // Temporarily disable while we auto-deal one card to each split hand
        ui.disableAllButtons()

        // Auto-deal to HAND1 then switch and auto-deal HAND2 (delays allow animations to complete)
        mainHandler.postDelayed({
            log("Auto-hit → HAND1")
            val ev1 = engine.playerHit()
            handleEngineEvent(ev1)

            mainHandler.postDelayed({
                log("Auto-switch to HAND2")
                val switchEvent = engine.playerStand()
                handleEngineEvent(switchEvent)

                mainHandler.postDelayed({
                    log("Auto-hit → HAND2")
                    val ev2 = engine.playerHit()
                    handleEngineEvent(ev2)

                    mainHandler.postDelayed({
                        ui.enableButtons(hit = true, stand = true, split = false)
                    }, 200)

                }, 350)

            }, 350)

        }, 350)
    }

    // -----------------------------------------------------
    // HANDLE ENGINE EVENTS
    // -----------------------------------------------------
    private fun handleEngineEvent(event: GameEngine.EngineEvent) {
        log("handleEngineEvent → ${event.javaClass.simpleName} :: $event")

        when (event) {

            // -------------------------------------------------
            // INITIAL DEAL (Blackjack auto-resolution)
            // -------------------------------------------------
            is GameEngine.EngineEvent.InitialDeal -> {

                handView.showInitialPlayerCards(event.playerCardsMain ?: emptyList())
                handView.addDealerCard(event.dealerVisible)
                handView.showDealerHidden()
                refreshAllScores()

                val playerHasBJ = engine.getScore(GameEngine.HandTag.MAIN) == 21 &&
                        (event.playerCardsMain?.size == 2)

                val dealerCards = engine.getDealerCards()
                val dealerHasBJ = dealerCards.size >= 2 &&
                        (dealerCards[0].value + dealerCards[1].value == 21)

                // No blackjack → normal round begins
                if (!playerHasBJ && !dealerHasBJ) {
                    ui.enableButtons(
                        hit = true,
                        stand = true,
                        split = engine.canSplit()
                    )
                    return
                }

                // -------------------------------------------------
                // BLACKJACK FOUND – FIXED VERSION
                // -------------------------------------------------
                blackjackResolved = true   // critical: blocks ace dialogs

                ui.disableAllButtons()
                revealDealerHidden()

                val p = players.currentPlayer()
                val bet = p.bet

                val result = when {
                    playerHasBJ && !dealerHasBJ ->
                        GameEngine.EngineEvent.RoundResult(
                            outcome = GameEngine.Outcome.BLACKJACK_PLAYER,
                            winAmount = (bet * 2.5).toInt(),
                            playerCardsMain = event.playerCardsMain,
                            playerCardsHand1 = null,
                            playerCardsHand2 = null,
                            dealerCards = dealerCards
                        )

                    !playerHasBJ && dealerHasBJ ->
                        GameEngine.EngineEvent.RoundResult(
                            outcome = GameEngine.Outcome.BLACKJACK_DEALER,
                            winAmount = 0,
                            playerCardsMain = event.playerCardsMain,
                            playerCardsHand1 = null,
                            playerCardsHand2 = null,
                            dealerCards = dealerCards
                        )

                    else ->
                        GameEngine.EngineEvent.RoundResult(
                            outcome = GameEngine.Outcome.PUSH,
                            winAmount = bet,
                            playerCardsMain = event.playerCardsMain,
                            playerCardsHand1 = null,
                            playerCardsHand2 = null,
                            dealerCards = dealerCards
                        )
                }

                // Delay improved: gives time for flip animation + overlay
                mainHandler.postDelayed({
                    playOutDealerCardsAndFinish(result)
                }, 1200L)
            }

            // -------------------------------------------------
            // CARD DEALT
            // -------------------------------------------------
            is GameEngine.EngineEvent.CardDealt -> {
                when (event.handTag) {
                    GameEngine.HandTag.MAIN -> {
                        handView.addPlayerCard(event.card)
                        handView.updateMainScore(engine.getScore(GameEngine.HandTag.MAIN))
                    }
                    GameEngine.HandTag.HAND1 -> {
                        handView.addToHand1(event.card)
                        handView.updateHand1Score(engine.getScore(GameEngine.HandTag.HAND1))
                    }
                    GameEngine.HandTag.HAND2 -> {
                        handView.addToHand2(event.card)
                        handView.updateHand2Score(engine.getScore(GameEngine.HandTag.HAND2))
                    }
                }
            }

            // -------------------------------------------------
            // ACE CHOICE
            // -------------------------------------------------
            is GameEngine.EngineEvent.NeedAceChoice -> {

                if (blackjackResolved) {
                    log("NeedAceChoice ignored (blackjackResolved=true)")
                    return
                }

                log("NeedAceChoice → ${event.card}")

                // Force view to reflect the current engine state
                handView.forceRedrawAll(
                    playerCards = engine.getPlayerCardsMain(),
                    dealerCardsList = engine.getDealerCards()
                )

                refreshAllScores()

                val cards = engine.getPlayerCardsMain() ?: emptyList()
                val base = cards.filter { it.rank != "A" }.sumOf { it.value }
                val low = base + 1
                val high = base + 11

                handView.updateMainScoreWithAceOptions(low, high)

                ui.disableAllButtons()
                awaitingAce = WaitingAce(event.handTag, event.card)

                mainHandler.postDelayed({
                    aceManager.requestAceValue(event.handTag, event.card) { chosen ->
                        log("Ace chosen = $chosen")
                        resolveAceChoice(event.handTag, event.card, chosen)
                    }
                }, 300)
            }

            // -------------------------------------------------
            // BUST
            // -------------------------------------------------
            is GameEngine.EngineEvent.HandBusted -> {
                log("Hand busted → ${event.handTag}  score=${event.score}")
                ui.disableAllButtons()

                val lastCard = when (event.handTag) {
                    GameEngine.HandTag.MAIN -> engine.getPlayerCardsMain().lastOrNull()
                    GameEngine.HandTag.HAND1 -> engine.getPlayerCardsHand1()?.lastOrNull()
                    GameEngine.HandTag.HAND2 -> engine.getPlayerCardsHand2()?.lastOrNull()
                }

                if (lastCard != null) {
                    when (event.handTag) {
                        GameEngine.HandTag.MAIN -> handView.addPlayerCard(lastCard)
                        GameEngine.HandTag.HAND1 -> handView.addToHand1(lastCard)
                        GameEngine.HandTag.HAND2 -> handView.addToHand2(lastCard)
                    }
                }

                when (event.handTag) {
                    GameEngine.HandTag.MAIN -> handView.updateMainScore(event.score)
                    GameEngine.HandTag.HAND1 -> handView.updateHand1Score(event.score)
                    GameEngine.HandTag.HAND2 -> handView.updateHand2Score(event.score)
                }

                // If we are in split mode and the busted hand is HAND1 → switch to HAND2
                if (engine.isSplit && event.handTag == GameEngine.HandTag.HAND1) {
                    log("Hand1 bust → asking engine to switch to HAND2")

                    val switchEv = engine.playerStand()
                    log("playerStand after bust -> $switchEv")

                    if (switchEv is GameEngine.EngineEvent.SwitchToHand2) {
                        log("Switch → Now playing HAND2 (after bust)")
                        handView.focusHand2()
                        handView.updateHand2Score(engine.getScore(GameEngine.HandTag.HAND2))
                        ui.enableButtons(hit = true, stand = true, split = false)
                        return
                    } else {
                        log("playerStand did not return SwitchToHand2 but we will switch UI anyway")
                        handView.focusHand2()
                        handView.updateHand2Score(engine.getScore(GameEngine.HandTag.HAND2))
                        ui.enableButtons(hit = true, stand = true, split = false)
                        return
                    }
                }

                // Otherwise regular bust -> round lost
                if (engine.isSplit && engine.isPlayingHand1) {
                    log("Hand1 bust detected but engine still shows isPlayingHand1=true -> switching UI as safety")
                    try {
                        engine.moveToHand2()
                    } catch (_: Throwable) {}
                    handView.focusHand2()
                    ui.enableButtons(hit = true, stand = true, split = false)
                } else {
                    endRoundLose()
                }
            }

            // -------------------------------------------------
            // EXACT 21
            // -------------------------------------------------
            is GameEngine.EngineEvent.Hand21 -> {
                log("Hand reached EXACT 21 → auto-stand")
                ui.disableAllButtons()

                // show the just-drawn card visually (last card in that specific hand)
                val last = when (event.handTag) {
                    GameEngine.HandTag.MAIN -> engine.getPlayerCardsMain().lastOrNull()
                    GameEngine.HandTag.HAND1 -> engine.getPlayerCardsHand1()?.lastOrNull()
                    GameEngine.HandTag.HAND2 -> engine.getPlayerCardsHand2()?.lastOrNull()
                }

                if (last != null) {
                    when (event.handTag) {
                        GameEngine.HandTag.MAIN -> handView.addPlayerCard(last)
                        GameEngine.HandTag.HAND1 -> handView.addToHand1(last)
                        GameEngine.HandTag.HAND2 -> handView.addToHand2(last)
                    }
                }

                // CRITICAL: wait for the final card animation to finish before proceeding
                mainHandler.postDelayed({
                    handleEngineEvent(engine.dealerPlay())
                }, 350L) // matches addAnimatedCard duration
            }

            // -------------------------------------------------
            // DEALER EXTRA CARD (visual)
            // -------------------------------------------------
            is GameEngine.EngineEvent.DealerCardDealt -> {
                handView.addDealerCard(event.card)
                handView.updateDealerScore(engine.getDealerScore())
            }

            // -------------------------------------------------
            // SWITCH HAND2
            // -------------------------------------------------
            is GameEngine.EngineEvent.SwitchToHand2 -> {
                log("Switch event received")
                handView.focusHand2()
                ui.enableButtons(hit = true, stand = true, split = false)
            }

            // -------------------------------------------------
            // FINAL RESULT
            // -------------------------------------------------
            is GameEngine.EngineEvent.RoundResult -> {
                log("RoundResult received")
                revealDealerHidden()
                playOutDealerCardsAndFinish(event)
            }

            is GameEngine.EngineEvent.NoOp -> log("NoOp → Ignored")
            is GameEngine.EngineEvent.BlackjackDetected -> log("BlackjackDetected ignored")
        }
    }

    // -----------------------------------------------------
    // SHOW HIDDEN CARD
    // -----------------------------------------------------
    private fun revealDealerHidden() {
        val hidden = engine.getDealerHiddenCard() ?: return
        log("Reveal hidden dealer card → $hidden")
        handView.flipDealerHiddenTo(hidden)

        // Slightly longer delay to ensure flip finishes
        mainHandler.postDelayed({
            handView.updateDealerScore(engine.getDealerScore())
        }, 600)
    }

    // -----------------------------------------------------
    // PLAY OUT DEALER (visual sync)
    // -----------------------------------------------------
    private fun playOutDealerCardsAndFinish(result: GameEngine.EngineEvent.RoundResult) {

        val dealerCards = result.dealerCards
        val extraCards =
            if (dealerCards.size > 2) dealerCards.subList(2, dealerCards.size) else emptyList()

        if (extraCards.isEmpty()) {
            log("No extra dealer cards → finishRoundResult()")
            mainHandler.postDelayed({ finishRoundResult(result) }, 600)
            return
        }

        val firstDelay = 500L
        val cardDelay = 700L
        val scoreDelay = 320L
        var totalDelay = firstDelay

        extraCards.forEachIndexed { index, card ->

            mainHandler.postDelayed({
                log("Dealer draws (visual) → $card")
                handView.addDealerCard(card)
            }, totalDelay)

            mainHandler.postDelayed({
                val score = engine.getDealerScore()
                log("Dealer score updated → $score")
                handView.updateDealerScore(score)
            }, totalDelay + scoreDelay)

            if (index == extraCards.lastIndex) {
                mainHandler.postDelayed({
                    finishRoundResult(result)
                }, totalDelay + scoreDelay + 600)
            }

            totalDelay += cardDelay
        }
    }

    // -----------------------------------------------------
    // FINAL RESULT
    // -----------------------------------------------------
    private fun finishRoundResult(res: GameEngine.EngineEvent.RoundResult) {

        if (res.outcome == GameEngine.Outcome.BLACKJACK_PLAYER ||
            res.outcome == GameEngine.Outcome.BLACKJACK_DEALER) {
            blackjackResolved = true
        }

        log("finishRoundResult → outcome=${res.outcome}, winAmount=${res.winAmount}")
        val p = players.currentPlayer()
        log("Player money before = ${p.money}, bet = ${p.bet}")

        roundActive = false
        ui.disableAllButtons()

        when (res.outcome) {

            GameEngine.Outcome.PLAYER_WIN -> {
                overlays.showWinOverlay(res.winAmount - p.bet)
                p.money += res.winAmount
            }

            GameEngine.Outcome.PUSH -> {
                overlays.showPushOverlay(p.bet)
                p.money += p.bet
            }

            GameEngine.Outcome.PLAYER_LOSE -> {
                overlays.showLoseOverlay(p.bet)
            }

            GameEngine.Outcome.BLACKJACK_PLAYER -> {
                overlays.showBlackjackOverlay((p.bet * 1.5).toInt())
                p.money += (p.bet * 2.5).toInt()
            }

            GameEngine.Outcome.BLACKJACK_DEALER -> {
                overlays.showLoseOverlay(p.bet)
            }
        }

        log("Player new money = ${p.money}")
        ui.setMoney(p.money)
        ui.setTitle(p.name)

        mainHandler.postDelayed({
            nextPlayerOrEnd()
        }, 1200)
    }

    private fun endRoundLose() {
        finishRoundResult(
            GameEngine.EngineEvent.RoundResult(
                outcome = GameEngine.Outcome.PLAYER_LOSE,
                winAmount = 0,
                playerCardsMain = engine.getPlayerCardsMain(),
                playerCardsHand1 = engine.getPlayerCardsHand1(),
                playerCardsHand2 = engine.getPlayerCardsHand2(),
                dealerCards = engine.getDealerCards()
            )
        )
    }

    private fun nextPlayerOrEnd() {
        val before = players.currentPlayer().money
        log("Advancing player. Money now = $before")

        if (!players.eliminateIfBroke()) players.advanceToNextPlayer()

        if (players.isEmpty) listener.onGameOver()
        else listener.onNextPlayerRequested()
    }

    // -----------------------------------------------------
    // Resolve Ace choice
    // -----------------------------------------------------
    fun resolveAceChoice(handTag: GameEngine.HandTag, card: Card, value: Int) {
        log("resolveAceChoice -> hand=$handTag card=${card.rank}${card.suit} value=$value")

        engine.setManualAceValue(handTag, card, value)
        awaitingAce = null

        refreshAllScores()

        val score = engine.getScore(handTag)
        log("resolveAceChoice -> new score for $handTag = $score")

        if (score > 21) {
            log("resolveAceChoice -> BUST detected for $handTag ($score)")
            ui.disableAllButtons()
            if (engine.isSplit && handTag == GameEngine.HandTag.HAND1 && engine.isPlayingHand1) {
                handView.focusHand2()
                ui.enableButtons(hit = true, stand = true, split = false)
            } else {
                endRoundLose()
            }
            return
        }

        if (score == 21) {
            log("resolveAceChoice -> reached 21 -> auto-stand")
            ui.disableAllButtons()
            // small delay to allow last-card animation if any
            mainHandler.postDelayed({
                handleEngineEvent(engine.dealerPlay())
            }, 300)
            return
        }

        ui.enableButtons(hit = true, stand = true, split = false)
    }

    // -----------------------------------------------------
    // SCORE REFRESH
    // -----------------------------------------------------
    private fun refreshAllScores() {
        handView.updateMainScore(engine.getScore(GameEngine.HandTag.MAIN))
        handView.updateDealerScore(engine.getDealerVisibleScore())

        if (engine.isSplit) {
            handView.updateHand1Score(engine.getScore(GameEngine.HandTag.HAND1))
            handView.updateHand2Score(engine.getScore(GameEngine.HandTag.HAND2))
        } else {
            handView.hideSplitScores()
        }
    }
}