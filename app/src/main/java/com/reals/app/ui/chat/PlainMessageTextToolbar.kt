package com.reals.app.ui.chat

import android.os.Build
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

internal class PlainMessageTextToolbar(
    private val view: View,
) : TextToolbar {
    private var actionMode: ActionMode? = null
    private var contentRect: Rect = Rect.Zero
    private var copyAction: (() -> Unit)? = null
    private var selectAllAction: (() -> Unit)? = null

    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
        onAutofillRequested: (() -> Unit)?,
    ) {
        contentRect = rect
        copyAction = onCopyRequested
        selectAllAction = onSelectAllRequested

        if (actionMode == null) {
            status = TextToolbarStatus.Shown
            actionMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                view.startActionMode(callback(), ActionMode.TYPE_FLOATING)
            } else {
                view.startActionMode(callback())
            }
        } else {
            actionMode?.invalidate()
        }
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        showMenu(
            rect = rect,
            onCopyRequested = onCopyRequested,
            onPasteRequested = onPasteRequested,
            onCutRequested = onCutRequested,
            onSelectAllRequested = onSelectAllRequested,
            onAutofillRequested = null,
        )
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        actionMode?.finish()
        actionMode = null
    }

    private fun callback(): ActionMode.Callback {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            object : ActionMode.Callback2() {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean = populateMenu(menu)
                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = populateMenu(menu)
                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean =
                    handleAction(mode, item.itemId)

                override fun onDestroyActionMode(mode: ActionMode) {
                    status = TextToolbarStatus.Hidden
                    actionMode = null
                }

                override fun onGetContentRect(mode: ActionMode, view: View, outRect: android.graphics.Rect) {
                    outRect.set(
                        contentRect.left.toInt(),
                        contentRect.top.toInt(),
                        contentRect.right.toInt(),
                        contentRect.bottom.toInt(),
                    )
                }
            }
        } else {
            object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean = populateMenu(menu)
                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = populateMenu(menu)
                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean =
                    handleAction(mode, item.itemId)

                override fun onDestroyActionMode(mode: ActionMode) {
                    status = TextToolbarStatus.Hidden
                    actionMode = null
                }
            }
        }
    }

    private fun populateMenu(menu: Menu): Boolean {
        menu.clear()
        if (copyAction != null) {
            menu.add(0, CopyItemId, 0, PlainMessageCopyActionLabel)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        if (selectAllAction != null) {
            menu.add(0, SelectAllItemId, 1, PlainMessageSelectAllActionLabel)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        return menu.size() > 0
    }

    private fun handleAction(mode: ActionMode, itemId: Int): Boolean {
        return when (itemId) {
            CopyItemId -> {
                copyAction?.invoke()
                mode.finish()
                true
            }
            SelectAllItemId -> {
                selectAllAction?.invoke()
                mode.invalidate()
                true
            }
            else -> false
        }
    }
}

internal val PlainMessageTextToolbarActionLabels = listOf(
    PlainMessageCopyActionLabel,
    PlainMessageSelectAllActionLabel,
)

private const val PlainMessageCopyActionLabel = "Copiar"
private const val PlainMessageSelectAllActionLabel = "Seleccionar todo"
private const val CopyItemId = 1
private const val SelectAllItemId = 2
