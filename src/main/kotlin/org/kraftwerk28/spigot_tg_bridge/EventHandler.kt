package org.kraftwerk28.spigot_tg_bridge

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.*

class EventHandler(
    private val plugin: Plugin,
    private val config: Configuration,
    private val tgBot: TgBot,
) : Listener {

    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        if (!config.logFromMCtoTG) return
        event.run {
            var name = ""
            plugin.chat?.let {
                name += it.getPlayerPrefix(player) + " "
            }
            name += player.displayName
            sendMessage(message, name)
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!config.logJoinLeave) return
        var name = ""
        plugin.chat?.let {
            name += it.getPlayerPrefix(event.player) + " "
        }
        name += event.player.displayName
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
        name += event.player.displayName
        val text = config.leaveString.replace("%username%", name)
        sendMessage(text)
    }

    @EventHandler
    fun onPlayerDied(event: PlayerDeathEvent) {
        if (!config.logDeath) return
        event.deathMessage?.let {
            val username = event.entity.displayName.fullEscape()
            var name = ""
            plugin.chat?.let {
                name += it.getPlayerPrefix(event.entity) + " "
            }
            name += event.entity.displayName
            val text = it.replace(username, "<b>$name</b>")
            sendMessage(text)
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
        name += event.player.displayName
        val text = config.asleepString.replace("%username%", name)
        sendMessage(text)
    }

    @EventHandler
    fun onPlayerAdvancementDoneEvent(event: PlayerAdvancementDoneEvent) {
        var name = ""
        plugin.chat?.let {
            name += it.getPlayerPrefix(event.player) + " "
        }
        name += event.player.displayName
        val text = config.advancementString
            .replace("%username%", name)
            .replace("%advancement%", event.advancement.toString())
        sendMessage(text)
    }

    private fun sendMessage(text: String, username: String? = null) {
        plugin.launch {
            tgBot.sendMessageToTelegram(text, username)
        }
    }
}
