package nya.yukisawa.paper_tg_bridge

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.bukkit.event.player.PlayerBedEnterEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class EventHandler(
    private val plugin: Plugin,
    private val config: Configuration,
    private val tgBot: TgBot,
) : Listener {

    @EventHandler
    fun onPlayerChat(event: AsyncChatEvent) {
        if (!config.logFromMCtoTG) return
        event.run {
            var name = ""
            plugin.chat?.let {
                name += it.getPlayerPrefix(player) + " "
            }
            name += PlainTextComponentSerializer.plainText().serialize(player.displayName())
            sendMessage(PlainTextComponentSerializer.plainText().serialize(message()), name)
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!config.logJoinLeave) return
        var name = ""
        plugin.chat?.let {
            name += it.getPlayerPrefix(event.player) + " "
        }
        name += PlainTextComponentSerializer.plainText().serialize(event.player.displayName())
        val text = config.joinString.replace("%username%", name)
        sendMessage(text)
    }

    @EventHandler
    fun onPlayerLeave(event: PlayerQuitEvent) {
        if (!config.logJoinLeave) return
        var name = ""
        plugin.chat?.let {
            name += it.getPlayerPrefix(event.player) + " "
        }
        name += PlainTextComponentSerializer.plainText().serialize(event.player.displayName())
        val text = config.leaveString.replace("%username%", name)
        sendMessage(text)
    }

    @EventHandler
    fun onPlayerDied(event: PlayerDeathEvent) {
        if (!config.logDeath) return
        if(config.debug) plugin.server.logger.info(event.deathMessage().toString())

        event.deathMessage()?.let { it ->
            val comp = it as TranslatableComponent
            val username = PlainTextComponentSerializer.plainText().serialize(event.player.displayName())
            var name = ""
            plugin.chat?.let {
                name += it.getPlayerPrefix(event.player) + " "
            }
            name += username

            var text = config.death[comp.key()]
            if(text.isNullOrEmpty())
                sendMessage(comp.key())
            else {
                for(i in 1 .. comp.args().size) {
                    var arg = comp.args()[i-1]
                    if(arg is TranslatableComponent) {
                        text = text!!.replace("%$i", "<b>${(arg.args()[0].children()[0] as TextComponent).content()}</b>")
                    }else {
                        text = text!!.replace("%$i", "<b>${(arg as TextComponent).content()}</b>")
                    }
                }
                text!!.replace(username, name)
                sendMessage(text)
            }

        }
    }

    @EventHandler
    fun onPlayerAsleep(event: PlayerBedEnterEvent) {
        if (!config.logPlayerAsleep) return
        if (event.bedEnterResult != PlayerBedEnterEvent.BedEnterResult.OK)
            return
        var name = ""
        plugin.chat?.let {
            name += it.getPlayerPrefix(event.player) + " "
        }
        name += PlainTextComponentSerializer.plainText().serialize(event.player.displayName())
        val text = config.asleepString.replace("%username%", name)
        sendMessage(text)
    }

    @EventHandler
    fun onPlayerAdvancementDone(event: PlayerAdvancementDoneEvent) {
        if (!config.logPlayerAdvancement) return
        if (config.debug) event.message()?.let { plugin.server.logger.info(it.toString()) }
        var name = ""
        plugin.chat?.let {
            name += it.getPlayerPrefix(event.player) + " "
        }
        name += PlainTextComponentSerializer.plainText().serialize(event.player.displayName())
        event.message()?.let {
            val advancement =
                ((it as TranslatableComponent).args()[1] as TranslatableComponent).args()[0] as TranslatableComponent
            val titleKey = advancement.key()
            val descriptionKey = titleKey.replace("title", "description")
            if (config.debug) plugin.server.logger.info("title: $titleKey \ndescription: $descriptionKey")
            if (config.advancements.containsKey(titleKey)) {
                val text = config.advancementString
                    .replace("%type%", config.advancements[it.key()]!!)
                    .replace("%username%", name)
                    .replace("%advancement%", config.advancements[titleKey]!!)
                    .replace("%description%", config.advancements[descriptionKey]!!)
                sendMessage(text)
            } else sendMessage(titleKey)
        }

    }

    private fun sendMessage(text: String, username: String? = null) {
        plugin.launch {
            tgBot.sendMessageToTelegram(text.escapeColorCodes(), username)
        }
    }
}
