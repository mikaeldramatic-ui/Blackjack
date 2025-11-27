package com.example.blackjack

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.AbsSavedState
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.view.ViewGroup
import android.widget.ImageView
import com.example.blackjack.databinding.ActivityGameboardBinding

class GameBoardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameboardBinding

    //Game - Variables (Fill in later)

    private var playerScore = 0
    private var dealerScore = 0
    private val deck = mutableListOf<String>() //Placeholder every card represent every card (AS , 10H) change later on

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Init UI With StartValue
        binding.playerScore.text = "Player: 0"
        binding.dealerScore.text = "Dealer: 0"

        //Buttons
        binding.btnHit.setOnClickListener {
            onHitClicked()
        }

        binding.btnStand.setOnClickListener {
            onStandClicked()
        }

        binding.btnQuit.setOnClickListener {
            showQuitDialog()
        }

        //Prepare game ( deckcards and so on...) - Implement function down below
        createDeck()
        shuffleDeck()


        //---------------UI - Handlers-------------

        private fun onHitClicked() {
           //Temporary Placeholder
            hitCardToPlayer()
            updateScoreOnUI()
        }

        private fun onStandClicked () {
            //When player stand
            dealerPlay()
            updateScoreOnUI()
            checkRoundResult()
        }

        private fun showQuitDialog() {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Quit Game?")
            builder.setMessage("Are you sure you want to quit the current game?")
            builder.setPositiveButton("Yes") {dialog : DialogInterface, _: Int ->
                dialog.dismiss()
                finish() //Closes GameBoardActivity and returns
            }
            builder.setNegativeButton("No") {dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            builder.setCancelable(true)
            builder.show()
        }

        //-------------- Game Function-----------

        private fun createDeck() {
            //TODO: Build e complete carddeck example AH, 2H and so on..
            deck.clear()
            val ranks = listOf("A","2","3","4","5","6","7","8","9","10","J","Q","K")
            val suits = listOf("H","D","C","S") //Hearts, Diamonds, Clubs, Spades
            for (s in suits) {
                for (r in ranks) {
                    deck.add(r+s)
                }
            }
        }

        private fun shuffleDeck() {
            deck.shuffle()
        }

        private fun dealInitialCards() {
            //TODO get two cards to player and to dealer and show them in UI
            //Examplestructure:
            //drawCardForPlayer()
            //draCardForDealer(hidden=true) eventually one hidden card
        }

        private fun hitCardToPlayer() {
            //Draw a card from deckand adds in playercardslayout as ImageView
            if (deck.isEmpty()) {
                createDeck()
                shuffleDeck()
            }
            val card = deck.removeAt(0)
            //TODO Count score for card and uppdate playerScore
            addCardImageToLayout(card, binding.playerCards)
        }

        private fun dealerPlay() {
            //Simple placeholder: Dealer pulls till 17 or more
            //TODO Implement real dealer-logic and update dealerScore
            while (dealerScore <17) {
                if (deck.isEmpty()) {
                    createDeck()
                    shuffleDeck()
                }
                val card = deck.removeAt(0)
                //TODO : Count score and update dealerScore
                addCardImageToLayout(card, binding.dealerCards)
            }

        }

        private fun checkRoundResult() {
            //TODO: compare playerScore and dealerScore and start win/loose-activity
            //Example placeholder
            if (playerScore >21) {
                //Start loose-activity
                startLooseActivity()
            }else if (dealerScore>21 || playerScore > dealerScore) {
                startWinsActivity()
            }else if (playerScore == dealerScore) {
                //Push - Tie (Maybe shows message)
            } else {
                startLooseActivity()
            }
        }

        //-------------UI helper--------------

        private fun updateScoreOnUI() {
            binding.playerScore.text = "Player: $playerScore"
            binding.playerScore.text = "Dealer: $dealerScore"

        }

        private fun addCardImageToLayout(cardCode:String, container: ViewGroup) {
            //Temporary: Showing generic card-Backsida until du have seperate cardpics
            val iv = ImageView(this)
            //IF leater have drawable-name lika "card 10H add cardCode --> drawable
            iv.setImageResource(R.drawable.card_back) //Make sure I have a drawable card_back
            val 1p= ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
            )
            1p.setMargins(8, 0, 8, 0)
            iv.layoutParams = 1p
                    container.addView(iv)
        }

        private fun startWinsActivity() {
            //TODO : Send information  like score if i want
            val intent = Intent(this,WinsActivity::class.java)
            startActivity(intent)
        }

        private fun startLooseActivity() {
            val intent = Intent(this, LooseActivity::class.java)
            startActivity(intent)
        }
    }
}