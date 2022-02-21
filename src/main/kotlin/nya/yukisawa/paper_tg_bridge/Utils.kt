package nya.yukisawa.paper_tg_bridge


fun String.escapeHtml() = this
    .replace("&", "&amp;")
    .replace(">", "&gt;")
    .replace("<", "&lt;")

fun String.escapeHTML() = this
    .replace("&", "&amp;")
    .replace(">", "&gt;")
    .replace("<", "&lt;")

fun String.escapeColorCodes() = replace("\u00A7.".toRegex(), "").replace("\u0024.".toRegex(), "")

fun String.fullEscape() = escapeHTML().escapeColorCodes()

fun User.rawUserMention(): String = firstName + (lastName?.let { " $it" } ?: "")

