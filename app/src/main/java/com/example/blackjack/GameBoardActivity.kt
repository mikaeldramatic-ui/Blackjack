package com.example.blackjack

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
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

    private var dealerHiddenCard: String? = null

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

        dealInitialCards()
    }




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

    //----------------Card names-------------//

    private fun getCardDrawableName(cardCode: String): String{
        val rank = cardCode.dropLast(1) //"A", "10", "Q"
        val suit = cardCode.last()          //"H","C","D","S"

        val rankName = when (rank) {
            "A" -> "ace"
            "J" -> "jack"
            "Q" -> "queen"
            "K" -> "King"
            else -> rank //2..10
        }

        val suitName = when (suit) {
            "H" -> "hearts"
            "D" -> "diamonds"
            "C" -> "clubs"
            "S" -> "spades"
            else -> "back"
        }

        return "card_${rankName}_of_${suitName}"
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

    private fun getCardValue(cardCode: String): Int {
        val rank = cardCode.dropLast[1] // "A","10","K" etc..//

        return when (rank) {
            "A" -> 11       //handling extra later on
            "K","Q","J" -> 10
            else -> rank.toInt() //2-10
        }
    }

        private fun shuffleDeck() {
            deck.shuffle()
        }

        private fun dealInitialCards() {

        // Player gets 2 cards
            val playerCard1 = deck.removeAt(0)
            val playerCards2 = deck.removeAt(0)

            addCardImageToLayout(playerCard1, binding.playerCards)
            addCardImageToLayout(playerCards2, binding.playerCards)

            playerScore +=getCardValue(playerCard1)
            playerScore +=getCardValue(playerCards2)

            //FIX Aces //

            if (playerScore >21 && (playerCard1.startsWith("A") || playerCards2.startsWith("A"))) {
                playerScore -=10
            }

            //Dealer get 1 visible and 1 hidden card//

            val dealerCard1 = deck.removeAt(0 ) //Visible
            val dealerCard2 = deck.removeAt(0) //hidden

            dealerScore += getCardValue(dealerCard1)

            //Add Visible card
            addDealerCard(dealerCard1, hidden = false)

            //Hidden card
            addHiddenCard = dealerCard2
            addDealerCard(dealerCard2, hidden = true)

            updateScoreOnUI()
        }

        private fun hitCardToPlayer() {
            //Draw a card from deckand adds in playercardslayout as ImageView
            if (deck.isEmpty()) {
                createDeck()
                shuffleDeck()
            }
            val card = deck.removeAt(0)

            val value = getCardValue(card)
            playerScore += value

            //ACE fix (if player goes above 21) //
            if (playerScore> 21 && card.startsWith("A")){
                playerScore -= 10
            }
            addCardImageToLayout(card, binding.playerCards)
        }



    private fun dealerPlay() {

        //Flip hidden card
        dealerHiddenCard?.let { dealerHiddenCard ->
            //Remove old face-down card
            binding.dealerCards.removeViewAt(1)

            //Add real card
            addDealerCard(hidden, hidden = false)

            dealerScore += getCardValue(hidden)

            //Fix Ace

            if  ( dealerScore > 21 && hidden.startWith("A")) {
                dealerScore -= 10
            }
        }
        dealerHiddenCard = null

        while (dealerScore <17){
            if(deck.isEmpty()){
                createDeck()
                shuffleDeck()
            }

            val card = deck.removeAt(0)

            val value = getCardValue(card)
            dealerScore += value

            //ACE fix for dealer //

            if(dealerScore >21 && card.startsWith("A")) {
                dealerScore -=10
            }
            addCardImageToLayout(card,binding.dealerCards)
        }
    }

        private fun checkRoundResult() {
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
            binding.dealerScore.text = "Dealer: $dealerScore"

        }

    private fun addDealerCard(cardCode: String, hidden: Boolean) {
        val iv = ImageView(this)

        if (hidden) {
            iv.setImageResource(R.drawable.card_back)
        } else {
            val drawableName = getCardDrawableName(cardCode)
            val resId = resources.getIdentifier(drawableName, "drawable", packageName)
            iv.setImageResource(if (resId !=0) resId else R.drawable.card_back)
        }

        val lp = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(8,0,8,0)
        iv.layoutParams = lp

        binding.dealerCards.addView(iv)
    }

        private fun addCardImageToLayout(cardCode:String, container: ViewGroup) {
            //Temporary: Showing generic card-Backsida until du have seperate cardpics
            val iv = ImageView(this)
            //Get right drawable-name
            val drawableName = getCardDrawableName(cardCode)

            //Get ID from drawable
            val resId = resources.getIdentifier(drawableName, "drawable",packageName)

            //IF card founded -> show -> or -> back
            iv.setImageResource(if(resId !=0) resId else R.drawable.card_back)

            val lp= ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(8, 0, 8, 0)
            iv.layoutParams = lp
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