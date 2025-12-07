package com.example.blackjack

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.blackjack.databinding.ActivityGameboardBinding
import java.lang.reflect.Method

private fun log(msg: String) {
    Log.d("BJ_ENG", msg)
}

class GameBoardActivity : AppCompatActivity(), AceCallback, RoundManager.RoundListener {

    private lateinit var binding: ActivityGameboardBinding

    private lateinit var engine: GameEngine
    private lateinit var players: PlayerController
    private lateinit var ui: UIManager
    private lateinit var handViews: HandViewManager
    private lateinit var overlays: OverlayManager
    private lateinit var aceManager: AceDialogManager
    private lateinit var round: RoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AppContext.init(this)

        // single player starter
        players = PlayerController(listOf(Player(1, "Player", money = 200, bet = 0)))

        engine = GameEngine()
        ui = UIManager(binding)
        overlays = OverlayManager(binding)
        handViews = HandViewManager(binding)
        aceManager = AceDialogManager(this)

        round = RoundManager(
            engine = engine,
            players = players,
            ui = ui,
            handView = handViews,
            overlays = overlays,
            aceManager = aceManager,
            listener = this
        )

        setupButtons()
        showBetDialog()
    }

    private fun setupButtons() {
        binding.btnHit.setOnClickListener { round.playerHit() }
        binding.btnStand.setOnClickListener { round.playerStand() }
        binding.btnSplit.setOnClickListener { round.attemptSplit() }
        binding.btnQuit.setOnClickListener { finish() }
    }

    // ------------------------
    // Bet dialog
    // ------------------------
    private fun showBetDialog() {
        if (players.isEmpty) {
            finish()
            return
        }

        val p = players.currentPlayer()

        val allBets = listOf(10, 20, 50, 100)
        val possibleBets = allBets.filter { it <= p.money }

        if (possibleBets.isEmpty()) {
            Toast.makeText(this, "You are out of money — eliminated.", Toast.LENGTH_LONG).show()
            players.removeCurrentPlayer()
            if (players.isEmpty) finish() else onNextPlayerRequested()
            return
        }

        val betLabels = possibleBets.map { "$${it}" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Place your bet (${p.money}$ available)")
            .setItems(betLabels) { _, index ->
                p.bet = possibleBets[index]
                round.startRound()
            }
            .setNegativeButton("Fold") { _, _ ->
                players.removeCurrentPlayer()
                if (players.isEmpty) finish() else onNextPlayerRequested()
            }
            .setCancelable(false)
            .show()
    }

    // ------------------------
    // AceCallback implementation - uses a safe forward (reflection + fallback)
    // ------------------------
    override fun onAceChosen(handTag: GameEngine.HandTag, card: Card, value: Int) {
        // Direct forward to RoundManager (preferred and type-safe)
        try {
            round.resolveAceChoice(handTag, card, value)
        } catch (t: Throwable) {
            Log.w("BJ_ACE", "Direct call failed, falling back to safe forward: ${t.message}", t)
            forwardResolveAceChoiceSafely(handTag, card, value)
        }
    }
    private fun forwardResolveAceChoiceSafely(handTag: GameEngine.HandTag, card: Card, value: Int) {
        // 1) Try reflection call to RoundManager.resolveAceChoice(handTag, card, value)
        try {
            val rmClass = round::class.java
            // Try to find a method named "resolveAceChoice" with parameter types (HandTag, Card, int)
            val paramTypesCandidates = arrayOf(
                arrayOf<Class<*>>(GameEngine.HandTag::class.java, Card::class.java, Int::class.java),
                arrayOf<Class<*>>(GameEngine.HandTag::class.java, Card::class.java, Integer::class.java)
            )

            var method: Method? = null
            for (pt in paramTypesCandidates) {
                try {
                    method = rmClass.getMethod("resolveAceChoice", *pt)
                    if (method != null) break
                } catch (ignored: NoSuchMethodException) {
                    // try next candidate
                }
            }
            if (method == null) {
                // try declared methods as a last resort (in case it's private)
                for (m in rmClass.declaredMethods) {
                    if (m.name == "resolveAceChoice" && m.parameterTypes.size == 3) {
                        method = m
                        method.isAccessible = true
                        break
                    }
                }
            }

            if (method != null) {
                Log.d("BJ_ACE", "Invoking RoundManager.resolveAceChoice via reflection")
                // invoke. If primitive int param expected, Java reflection will handle boxing.
                method.invoke(round, handTag, card, value)
                return
            } else {
                Log.w("BJ_ACE", "RoundManager.resolveAceChoice method not found via reflection")
            }
        } catch (ex: Throwable) {
            Log.w("BJ_ACE", "Reflection call failed: ${ex.message}", ex)
            // fall through to fallback below
        }

        // 2) Fallback: directly apply to engine and update UI so user's choice is respected
        try {
            Log.d("BJ_ACE", "Applying fallback: engine.setManualAceValue(...)")
            engine.setManualAceValue(handTag, card, value)

            // Update scores in UI
            // Update main/hand/dealer scores as appropriate
            try {
                val score = engine.getScore(handTag)
                when (handTag) {
                    GameEngine.HandTag.MAIN -> handViews.updateMainScore(score)
                    GameEngine.HandTag.HAND1 -> handViews.updateHand1Score(score)
                    GameEngine.HandTag.HAND2 -> handViews.updateHand2Score(score)
                }
            } catch (e: Throwable) {
                Log.w("BJ_ACE", "Failed updating individual hand score: ${e.message}")
            }

            // Update dealer score as well (in case)
            try {
                handViews.updateDealerScore(engine.getScore(GameEngine.HandTag.MAIN))
            } catch (e: Throwable) {
                Log.w("BJ_ACE", "Failed updating dealer score: ${e.message}")
            }

            // Re-enable buttons so player can continue (this mirrors RoundManager behavior)
            try {
                ui.enableButtons(hit = true, stand = true, split = false)
            } catch (e: Throwable) {
                Log.w("BJ_ACE", "Failed enabling UI buttons in fallback: ${e.message}")
            }

        } catch (e: Throwable) {
            Log.e("BJ_ACE", "Fallback failed too: ${e.message}", e)
        }
    }

    // ------------------------
    // RoundListener (callbacks from RoundManager)
    // ------------------------
    override fun onNextPlayerRequested() {
        // Ask bet for next player
        showBetDialog()
    }

    override fun onGameOver() {
        finish()
    }
}