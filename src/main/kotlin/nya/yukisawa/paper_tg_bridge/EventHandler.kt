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
            val name = (event.player.displayName() as TextComponent).content()
            sendMessage(PlainTextComponentSerializer.plainText().serialize(message()), name)
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!config.logJoinLeave) return
        val name = (event.player.displayName() as TextComponent).content()
        val text = config.joinString.replace("%username%", name)
        sendMessage(text)
    }

    @EventHandler
    fun onPlayerLeave(event: PlayerQuitEvent) {
        if (!config.logJoinLeave) return
        val name = (event.player.displayName() as TextComponent).content()
        val text = config.leaveString.replace("%username%", name)
        sendMessage(text)
    }

    @EventHandler
    fun onPlayerDied(event: PlayerDeathEvent) {
        if (!config.logDeath) return
        if (config.debug) plugin.server.logger.info(event.deathMessage().toString())

        event.deathMessage()?.let { it ->
            val comp = it as TranslatableComponent
            val text = comp.processComponent(config.lang)
            sendMessage(text.replace("\$s", ""))
        }
    }


    @EventHandler
    fun onPlayerAsleep(event: PlayerBedEnterEvent) {
        if (!config.logPlayerAsleep) return
        if (event.bedEnterResult != PlayerBedEnterEvent.BedEnterResult.OK)
            return
        val name = (event.player.displayName() as TextComponent).content()

        val text = config.asleepString.replace("%username%", name)
        sendMessage(text)
    }

    @EventHandler
    fun onPlayerAdvancementDone(event: PlayerAdvancementDoneEvent) {
        if (!config.logPlayerAdvancement) return
        if (config.debug) event.message()?.let { plugin.server.logger.info(it.toString()) }
        val name = (event.player.displayName() as TextComponent).content()
        event.message()?.let {
            val advancement =
                ((it as TranslatableComponent).args()[1] as TranslatableComponent).args()[0] as TranslatableComponent
            val titleKey = advancement.key()
            val descriptionKey = titleKey.replace("title", "description")
            if (config.debug) plugin.server.logger.info("title: $titleKey \ndescription: $descriptionKey")
            if (config.lang.containsKey(titleKey)) {
                val text = config.advancementString
                    .replace("%type%", config.lang[it.key()]!!.replace("%s", ""))
                    .replace("%username%", name)
                    .replace("%advancement%", config.lang[titleKey]!!)
                    .replace("%description%", config.lang[descriptionKey]!!)
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
