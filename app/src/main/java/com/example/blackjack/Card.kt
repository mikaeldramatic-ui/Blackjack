package com.example.blackjack

data class Card (
    val rank: String,          // 2-10 J , Q , K , A
    val suit: String,         //  Clubs, Diamonds, Hearts, Spades
    val value: Int,          //   Blackjack value
    val drawableRes: Int    //    Pictures in drawable
)

