package com.example.blackjack

data class Player(

    val id: Int,
    var name: String,
    var money: Int = 200,
    var bet : Int = 0,
    var cards: MutableList<String> = mutableListOf(),
    var score: Int= 0,
    var preferAceAsOne:Boolean = false,
    var isBust: Boolean = false,
    var isStanding: Boolean = false,
)
