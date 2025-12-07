package com.example.blackjack

import android.app.AlertDialog
import android.content.Context

class AceDialogManager(
    private val ctx: Context,
    private val callback: AceCallBack
) {
    var dialogOpen = false

    fun requestAceValue(handTag: GameEngine.HandTag, card: String) {
        if (dialogOpen) return
        dialogOpen = true

        AlertDialog.Builder(ctx)
            .setTitle("Ace Value")
            .setMessage("Choose value for $card")
            .setPositiveButton("11") { _, _ ->
                dialogOpen = false
                callback.onAceChosen(handTag, card, 11)
            }
            .setNegativeButton("1") { _, _ ->
                dialogOpen = false
                callback.onAceChosen(handTag, card, 1)
            }
            .setCancelable(false)
            .show()
    }
}