package com.example.blackjack

interface AceCallBack {
    fun onAceChosen(handTag: GameEngine.HandTag, card: String, value: Int)
}