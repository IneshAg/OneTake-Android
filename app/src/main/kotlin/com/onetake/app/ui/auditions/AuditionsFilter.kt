package com.onetake.app.ui.auditions

enum class AuditionFilter(val label: String) {
    ACTIVE("Active"),
    DUE_THIS_WEEK("Due this week"),
    SENT("Sent"),
    ALL("All");

    companion object {
        val default = ACTIVE
    }
}
