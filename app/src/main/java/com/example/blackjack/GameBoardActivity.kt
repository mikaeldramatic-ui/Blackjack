package com.example.blackjack

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.blackjack.databinding.ActivityGameboardBinding

class GameBoardActivity : AppCompatActivity(), AceDialogManager.AceCallback {

    private lateinit var binding: ActivityGameboardBinding

    // Core logic components
    private lateinit var engine: GameEngine
    private lateinit var playerController: PlayerController
    private lateinit var roundManager: RoundManager

    // UI managers
    private lateinit var handView: HandViewManager
    private lateinit var overlay: OverlayManager
    private lateinit var aceDialog: AceDialogManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Needed for deck drawable IDs
        AppContext.init(this)

        // Receive player count
        val playerCount = intent.getIntExtra("playerCount", 1)
        val players = mutableListOf<Player>()

        // Create players
        for (i in 1..playerCount) {
            players.add(Player(id = i, name = generateRandomName()))
        }

        // Controllers
        engine = GameEngine()
        playerController = PlayerController(players)

        // UI managers
        handView = HandViewManager(binding)
        overlay = OverlayManager(binding)
        aceDialog = AceDialogManager(this, this)

        // Round Manager (brain)
        roundManager = RoundManager(
            engine = engine,
            playerController = playerController,
            handView = handView,
            overlay = overlay,
            aceDialog = aceDialog,
            activity = this
        )

        setupButtons()

        // Start game
        roundManager.startTurn()
    }

    // ---------------- Button Listener Setup ----------------

    private fun setupButtons() {

        binding.btnHit.setOnClickListener {
            roundManager.onHit()
        }

        binding.btnStand.setOnClickListener {
            roundManager.onStand()
        }

        binding.btnSplit.setOnClickListener {
            roundManager.onSplit()
        }

        binding.btnQuit.setOnClickListener {
            finish()
        }
    }

    // ---------------- Ace callback from AceDialogManager ----------------
    override fun onAceChosen(handTag: GameEngine.HandTag, cardCode: String, value: Int) {
        roundManager.onAceChosen(handTag, cardCode, value)
    }

    // Generate names
    private fun generateRandomName(): String {
        val names = listOf(
            "Alex","Blake","Charlie","Dakota","Eden","Finn","Harper","Indigo","Jordan",
            "Kai","Luca","Milan","Nova","Phoenix","Quinn","Riley","Sky","Tatum","Winter","Zion"
        )
        return names.random()
    }
}