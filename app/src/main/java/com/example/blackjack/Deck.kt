// Deck.kt
package com.example.blackjack

import kotlin.random.Random

class Deck(private val rng: Random = Random(System.currentTimeMillis())) {

    private val cards = mutableListOf<Card>()

    init { rebuildAndShuffle() }

    fun rebuildAndShuffle() {
        cards.clear()

        val ranks = listOf(
            "A","2","3","4","5","6","7","8","9","10","J","Q","K"
        )
        val suits = listOf("H","D","C","S") // hearts, diamonds, clubs, spades

        for (s in suits) {
            for (r in ranks) {

                val value = when (r) {
                    "A" -> 11
                    "K","Q","J" -> 10
                    else -> r.toInt()
                }

                val drawableName = "card_${r.lowercase()}_${s.lowercase()}"
                val drawable = AppContext.resId(drawableName)

                cards.add(Card(r, s, value, drawable))
            }
        }

        shuffle()
    }

    fun shuffle() = cards.shuffle(rng)

    fun draw(): Card {
        if (cards.isEmpty()) rebuildAndShuffle()
        return cards.removeAt(0)
    }

    fun remaining() = cards.size
}