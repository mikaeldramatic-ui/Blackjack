package com.example.blackjack

interface AceCallback {
    fun onAceChosen(handTag: GameEngine.HandTag, card: Card, value: Int)
}