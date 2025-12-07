package com.example.blackjack

import android.app.AlertDialog
import android.content.Context

class AceDialogManager(private val context: Context) {

    private var currentDialog: AlertDialog? = null
    private var currentCallback: ((Int) -> Unit)? = null

    fun requestAceValue(
        handTag: GameEngine.HandTag,
        card: Card,
        onChosen: (Int) -> Unit
    ) {
        // Close any dialog from a previous round
        forceCloseDialog()

        currentCallback = onChosen

        val dialog = AlertDialog.Builder(context)
            .setTitle("Ace Value")
            .setMessage("Choose value for ${card.rank}${card.suit}")
            .setPositiveButton("11") { _, _ ->
                currentCallback?.invoke(11)
                clearState()
            }
            .setNegativeButton("1") { _, _ ->
                currentCallback?.invoke(1)
                clearState()
            }
            .setCancelable(false)
            .create()

        currentDialog = dialog
        dialog.show()
    }

    // -----------------------------------------------------
    // Force-close dialog safely (used by RoundManager)
    // -----------------------------------------------------
    fun forceCloseDialog() {
        currentDialog?.dismiss()
        clearState()
    }

    // Clears dialog + callback
    private fun clearState() {
        currentDialog = null
        currentCallback = null
    }
}