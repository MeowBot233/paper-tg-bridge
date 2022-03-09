package nya.yukisawa.paper_tg_bridge

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit


fun String.escapeHTML() = this
    .replace("&", "&amp;")
    .replace(">", "&gt;")
    .replace("<", "&lt;")

fun String.escapeColorCodes() = replace("\u00A7.".toRegex(), "").replace("\u0024.".toRegex(), "")

fun String.fullEscape() = escapeHTML().escapeColorCodes()

fun User.rawUserMention(): String = firstName + (lastName?.let { " $it" } ?: "")

fun Component.processComponent(lang: Map<String, String>): String {
    return when (this) {
        is TextComponent -> {
            var text = content()
            this.children().forEach { text += it.processComponent(lang) }
            text
        }
        is TranslatableComponent -> {
            //Bukkit.getServer().logger.info(toString())
            if (!lang.containsKey(key())) key()
            var text = lang[key()]!!
            var i = 0
            do {
                val index = text.indexOf("%s")
                text = text.replaceFirst("%s", "%${++i}\$s")
            } while (index != -1)
            for (t in 1..args().size) {
                Bukkit.getServer().logger.info("replacing %$t\$s with ${args()[t - 1].processComponent(lang)}")
                text = text.replace("%$t\$s", args()[t - 1].processComponent(lang))
            }
            text
        }
        else -> PlainTextComponentSerializer.plainText().serialize(this)
    }
}