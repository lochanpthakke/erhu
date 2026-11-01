package com.erhu.erhu.core.designsystem.component

/** What the leading icon of an [AppTopBar] does. */
sealed interface TopBarNavigation {
    /** Drawer destinations: ☰ opens the navigation drawer. */
    data class TopLevel(
        val onOpenDrawer: () -> Unit,
    ) : TopBarNavigation

    /** Pushed screens: ← goes back. */
    data class Detail(
        val onBack: () -> Unit,
    ) : TopBarNavigation
}
