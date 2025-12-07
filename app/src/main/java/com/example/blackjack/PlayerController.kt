package com.example.blackjack

/**
 * PlayerController
 *
 * Responsibility:
 *  - Manage list of players for multiplayer
 *  - Advance turns, remove folded/eliminated players
 *  - Provide safe getters for current player and index management
 */
class PlayerController(initialPlayers: List<Player> = emptyList()) {

    private val players = initialPlayers.toMutableList()
    private var currentIndex = 0

    val isEmpty: Boolean
        get() = players.isEmpty()

    fun size(): Int = players.size

    fun getPlayers(): List<Player> = players.toList()

    fun resetWith(playersList: List<Player>) {
        players.clear()
        players.addAll(playersList)
        currentIndex = 0
    }

    fun addPlayer(p: Player) {
        players.add(p)
    }

    fun currentPlayer(): Player {
        if (players.isEmpty()) throw IllegalStateException("No players")
        if (currentIndex < 0 || currentIndex >= players.size) currentIndex = 0
        return players[currentIndex]
    }

    fun currentPlayerIndex(): Int = currentIndex

    fun advanceToNextPlayer() {
        if (players.isEmpty()) return
        currentIndex++
        if (currentIndex >= players.size) currentIndex = 0
    }

    /**
     * Remove current player (used when folding/eliminated). After removal,
     * currentIndex will point to the next logical player (or 0 if list wrapped).
     */
    fun removeCurrentPlayer() {
        if (players.isEmpty()) return
        players.removeAt(currentIndex)
        if (players.isEmpty()) {
            currentIndex = 0
            return
        }
        if (currentIndex >= players.size) currentIndex = 0
    }

    /**
     * Fold current player (multiplayer): remove or mark depending on behaviour.
     * Returns the removed Player or null.
     */
    fun foldCurrentPlayer(): Player? {
        if (players.isEmpty()) return null
        val removed = players.removeAt(currentIndex)
        if (players.isEmpty()) {
            currentIndex = 0
            return removed
        }
        if (currentIndex >= players.size) currentIndex = 0
        return removed
    }

    /**
     * Eliminate current if money < minimum.
     */
    fun eliminateIfBroke(minimum: Int = 10): Boolean {
        if (players.isEmpty()) return false
        val p = currentPlayer()
        return if (p.money < minimum) {
            removeCurrentPlayer()
            true
        } else false
    }

    fun resetIndexToZero() {
        currentIndex = 0
    }
}