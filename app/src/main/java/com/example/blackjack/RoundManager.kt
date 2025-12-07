package com.example.blackjack

import com.example.blackjack.databinding.ActivityGameboardBinding

class RoundManager(
    private val activity: GameBoardActivity,
    private val binding: ActivityGameboardBinding,
    private val engine: GameEngine,
    private val players: PlayerController,
    private val ui: UIManager,
    private val handView: HandViewManager,
    private val overlays: OverlayManager,
    private val aceManager: AceDialogManager
) {

    private var awaitingAceFor: Pair<GameEngine.HandTag, String>? = null
    private var roundInProgress = false

    // -----------------------------------------------------
    // START NEW ROUND
    // -----------------------------------------------------
    fun startRound() {
        val p = players.currentPlayer()

        // Reset UI
        ui.resetScores()
        ui.hideSplit()
        ui.hideBlackjackMessage()
        overlays.hideAll()
        handView.clearAll()

        // Apply player bet
        val bet = p.bet
        if (bet > p.money) return // should not happen
        p.money -= bet

        ui.updatePlayerHeader(p.name, p.money)

        // Start engine round
        val event = engine.startNewRound(bet)
        roundInProgress = true

        handleEvent(event)
    }

    // -----------------------------------------------------
    // PLAYER ACTIONS
    // -----------------------------------------------------
    fun hit() {
        if (!roundInProgress) return
        handleEvent(engine.playerHit())
    }

    fun stand() {
        if (!roundInProgress) return
        val event = engine.playerStand()

        if (event is GameEngine.EngineEvent.SwitchToHand2) {
            // Switch active hand visually
            handView.focusHand2()
            ui.updateSplitScores(
                engine.getScore(GameEngine.HandTag.HAND1).toString(),
                engine.getScore(GameEngine.HandTag.HAND2).toString()
            )
            return
        }

        if (!engine.isSplit || !engine.isPlayingHand1) {
            // Standing on last hand → dealer plays
            dealerPlay()
        }
    }

    fun attemptSplit() {
        val p = players.currentPlayer()

        if (!engine.attemptSplit()) return

        // Withdraw second bet
        if (p.money < p.bet) return
        p.money -= p.bet
        ui.updatePlayerHeader(p.name, p.money)

        // Switch UI to split mode
        ui.showSplit()
        handView.enableSplitMode(engine.getPlayerCardsHand1()!!, engine.getPlayerCardsHand2()!!)
        ui.updateSplitScores(
            engine.getScore(GameEngine.HandTag.HAND1).toString(),
            engine.getScore(GameEngine.HandTag.HAND2).toString()
        )
    }

    // -----------------------------------------------------
    // DEALER PLAY
    // -----------------------------------------------------
    private fun dealerPlay() {
        val event = engine.dealerPlay()
        handleEvent(event)
    }

    // -----------------------------------------------------
    // ACE DIALOG CALLBACK
    // -----------------------------------------------------
    fun resolveAceChoice(value: Int) {
        val ace = awaitingAceFor ?: return
        awaitingAceFor = null

        engine.setManualAceValue(ace.first, ace.second, value)

        // Continue flow:
        when (ace.first) {
            GameEngine.HandTag.MAIN -> handleEvent(GameEngine.EngineEvent.NoOp)
            GameEngine.HandTag.HAND1 -> handleEvent(GameEngine.EngineEvent.NoOp)
            GameEngine.HandTag.HAND2 -> handleEvent(GameEngine.EngineEvent.NoOp)
        }
    }

    // -----------------------------------------------------
    // HANDLE ENGINE EVENTS
    // -----------------------------------------------------
    private fun handleEvent(event: GameEngine.EngineEvent) {
        when (event) {

            is GameEngine.EngineEvent.InitialDeal -> {
                handView.showInitialDeal(
                    event.playerCardsMain,
                    event.dealerVisible,
                    event.dealerHidden
                )
                ui.updateMainScore(engine.getScore(GameEngine.HandTag.MAIN).toString())

                // Check split availability
                if (engine.canSplit()) ui.showSplit()

                if (event is GameEngine.EngineEvent.NeedAceChoice) {
                    // handled below
                }
            }

            is GameEngine.EngineEvent.CardDealt -> {
                handView.addPlayerCard(event.handTag, event.card)

                val score = engine.getScore(event.handTag)
                when (event.handTag) {
                    GameEngine.HandTag.MAIN -> ui.updateMainScore(score.toString())
                    GameEngine.HandTag.HAND1, GameEngine.HandTag.HAND2 -> {
                        ui.updateSplitScores(
                            engine.getScore(GameEngine.HandTag.HAND1).toString(),
                            engine.getScore(GameEngine.HandTag.HAND2).toString()
                        )
                    }
                }
            }

            is GameEngine.EngineEvent.NeedAceChoice -> {
                awaitingAceFor = event.handTag to event.cardCode
                aceManager.showAceDialog(event.cardCode, this)
            }

            is GameEngine.EngineEvent.HandBusted -> {
                ui.disableActions()
                ui.updateMainScore("Bust!")

                if (engine.isSplit && engine.isPlayingHand1) {
                    // Switch to hand 2
                    handleEvent(GameEngine.EngineEvent.SwitchToHand2)
                } else {
                    // End round → Dealer wins
                    endRoundLose()
                }
            }

            is GameEngine.EngineEvent.Hand21 -> {
                ui.updateMainScore("21")
                ui.disableActions()
                dealerPlay()
            }

            GameEngine.EngineEvent.SwitchToHand2 -> {
                handView.focusHand2()
                ui.updateSplitScores(
                    engine.getScore(GameEngine.HandTag.HAND1).toString(),
                    engine.getScore(GameEngine.HandTag.HAND2).toString()
                )
            }

            is GameEngine.EngineEvent.DealerCardDealt -> {
                handView.addDealerCard(event.card)
                ui.updateDealerScore(engine.getScore(GameEngine.HandTag.MAIN))
            }

            is GameEngine.EngineEvent.RoundResult -> {
                finishRoundResult(event)
            }

            else -> {}
        }
    }

    // -----------------------------------------------------
    // ROUND FINISHING
    // -----------------------------------------------------

    private fun finishRoundResult(event: GameEngine.EngineEvent.RoundResult) {
        roundInProgress = false
        ui.disableActions()

        val player = players.currentPlayer()

        when (event.outcome) {

            GameEngine.Outcome.PLAYER_WIN -> {
                val win = event.winAmount - player.bet
                overlays.showWinOverlay(win)
                player.money += event.winAmount
            }

            GameEngine.Outcome.PUSH -> {
                overlays.showPushOverlay(player.bet)
                player.money += player.bet
            }

            GameEngine.Outcome.PLAYER_LOSE -> {
                overlays.showLoseOverlay(player.bet)
            }

            GameEngine.Outcome.BLACKJACK_PLAYER -> {
                overlays.showWinOverlay((player.bet * 1.5).toInt())
                player.money += (player.bet * 2.5).toInt()
            }

            GameEngine.Outcome.BLACKJACK_DEALER -> {
                overlays.showLoseOverlay(player.bet)
            }
        }

        ui.updatePlayerHeader(player.name, player.money)

        // Move to next player or restart same player
        activity.postDelayed(1500) {
            nextPlayerOrRestart()
        }
    }

    private fun nextPlayerOrRestart() {
        if (players.eliminateIfBroke()) {
            // Player removed → next automatically
        } else {
            players.advanceTurn()
        }

        // If no players left → end game handled by Activity
        if (players.isGameOver()) {
            activity.endGame()
            return
        }

        activity.startNextPlayerTurn()
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
}