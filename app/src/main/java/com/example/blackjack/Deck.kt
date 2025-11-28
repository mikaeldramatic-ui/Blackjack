package com.example.blackjack

class Deck {

    private val cards = mutableListOf<Card>()
    private var currentIndex = 0

    init {
        createDeck()
        shuffle()
    }

    //----------------------- Create whole Card Deck automatic -----------------//

    private fun createDeck() {

        val suits = listOf("c","d","h","s") //Clubs, diamonds, hearts, spades
        val ranks = listOf(
            "2","3","4","5","6","7","8","9","10","j","q","k","a"
        )

        for (suit in suits) {
            for (rank in ranks) {

                val value = when (rank) {
                    "j", "q", "k" ->10
                    "a" -> 11
                    else -> rank.toInt()
                }

                //Find right drawable based on filename i drawable
                val drawableName = "card_${rank}_${suit}"
                val drawableRes = getDrawableId(drawableName)

                //Add card in deck
                cards.add(
                    Card(
                        rank= rank,
                        suit=suit,
                        value=value,
                        drawableRes = drawableRes
                    )
                )
            }
        }

        //------Get drawable-ID from Source-----//

        private fun getDrawableId(name:String): Int {
            val resId = Myapp.instance.resources.getIdentifier(
                name,
                "drawable",
                Myapp.instance.packageName
            )

            if (resId == 0) {
                throw RuntimeException("Drawable missing: $name.png - doublecheck if filename is in drawable!")
            }

            return resId
        }

        //-------shuffle cards-----//

        fun shuffle() {
            cards.shuffle()
            currentIndex = 0
        }

        //-------Draw next card ----//

        fun drawCard() : Card {
            if (currentIndex >= cards.size) {
                shuffle()
            }
            return cards[currentIndex++]
        }
    }
}