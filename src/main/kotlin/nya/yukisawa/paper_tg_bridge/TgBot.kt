package nya.yukisawa.paper_tg_bridge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Duration
import java.util.*
import nya.yukisawa.paper_tg_bridge.Constants as C

typealias CmdHandler = suspend (HandlerContext) -> Unit

data class HandlerContext(
    val update: Update,
    val message: Message?,
    val chat: Chat?,
    val commandArgs: List<String> = listOf(),
)

class TgBot(
    private val plugin: Plugin,
    private val config: Configuration,
) {
    private val client: OkHttpClient = OkHttpClient
        .Builder()
        .readTimeout(Duration.ZERO)
        .build()
    private val api = Retrofit.Builder()
        .baseUrl("https://${config.telegramAPI}/bot${config.botToken}/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TgApiService::class.java)
    private val uuidHelper = Retrofit.Builder()
        .baseUrl("https://playerdb.co/api/player/minecraft/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(UuidHelper::class.java)
    private val updateChan = Channel<Update>()
    private var pollJob: Job? = null
    private var handlerJob: Job? = null
    private var currentOffset: Long = -1
    private var me: User? = null
    private val commandMap: Map<String?, CmdHandler> = config.commands.run {
        mapOf(
            online to ::onlineHandler,
            time to ::timeHandler,
            chatID to ::chatIdHandler,
            whitelist to ::whitelistHandler,
            meow to ::meowHandler
        )
    }

    private val meow = listOf("喵喵喵~", "喵呜！", "Nya~", "喵", "Meow~", "喵喵？", "捕捉小猫咪！")

    private suspend fun initialize() {
        me = api.getMe().result!!
        val commands = config.commands.run { listOf(time, online, chatID, whitelist, meow) }
            .zip(
                C.COMMAND_DESC.run {
                    listOf(timeDesc, onlineDesc, chatIDDesc, whitelistDesc, meowDesc)
                }
            )
            .map { BotCommand(it.first!!, it.second) }
            .let { SetMyCommands(it) }
        api.deleteWebhook(dropPendingUpdates = true)
        api.setMyCommands(commands)
    }

    suspend fun startPolling() {
        initialize()
        pollJob = initPolling()
        handlerJob = initHandler()
    }

    suspend fun stop() {
        pollJob?.cancelAndJoin()
        handlerJob?.join()
    }

    private fun initPolling() = plugin.launch {
        loop@while (true) {
            try {
                api.getUpdates(
                    offset = currentOffset,
                    timeout = config.pollTimeout,
                ).result?.let { updates ->
                    if (updates.isNotEmpty()) {
                        updates.forEach { updateChan.send(it) }
                        currentOffset = updates.last().updateId + 1
                    }
                }
            } catch (e: Exception) {
                when (e) {
                    is CancellationException -> break@loop
                    else -> {
                        e.printStackTrace()
                        continue@loop
                    }
                }
            }
        }
        updateChan.close()
    }

    private fun initHandler() = plugin.launch {
        updateChan.consumeEach {
            try {
                handleUpdate(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun handleUpdate(update: Update) {
        if(config.debug) plugin.server.logger.info(update.toString())
        // Ignore private message or channel post
        if (update.message?.chat?.type != "group" && update.message?.chat?.type != "supergroup")
            return
        val ctx = HandlerContext(
            update,
            update.message,
            update.message.chat,
        )
        update.message.let {
            if (it.text?.startsWith("/") == true) {
                val args = it.text.split(" ")
                val cmd = if (args[0].contains("@")) {
                    val cmds = args[0].split("@")
                    if (cmds[1] != me!!.username) return
                    cmds[0].substring(1)
                } else args[0].substring(1)

                commandMap[cmd]?.run {
                    this(ctx.copy(commandArgs = args))
                    return
                }
            }
            onMessageHandler(ctx)
        }
    }

    private suspend fun timeHandler(ctx: HandlerContext) {
        val msg = ctx.message!!
        if (!config.allowedChats.contains(msg.chat.id)) {
            return
        }
        if (plugin.server.worlds.isEmpty()) {
            api.sendMessage(
                msg.chat.id,
                "No worlds available",
                replyToMessageId = msg.messageId
            )
            return
        }
        // TODO: handle multiple worlds
        val time = plugin.server.worlds.first().time
        val text = C.TIMES_OF_DAY.run {
            when {
                time <= 12000 -> day
                time <= 13800 -> sunset
                time <= 22200 -> night
                time <= 24000 -> sunrise
                else -> ""
            }
        } + " ($time)"
        api.sendMessage(msg.chat.id, text, msg.messageId)
    }

    private suspend fun onlineHandler(ctx: HandlerContext) {
        val msg = ctx.message!!
        if (!config.allowedChats.contains(msg.chat.id)) {
            return
        }
        val playerList = plugin.server.onlinePlayers
        val playerStr = plugin.server
            .onlinePlayers
            .mapIndexed { i, s -> "${i + 1}. ${PlainTextComponentSerializer.plainText().serialize(s.displayName()).fullEscape()}" }
            .joinToString("\n")
        val text =
            if (playerList.isNotEmpty()) "${config.onlineString}:\n$playerStr"
            else config.nobodyOnlineString
        api.sendMessage(msg.chat.id, text, msg.messageId)
    }

    private suspend fun chatIdHandler(ctx: HandlerContext) {
        val msg = ctx.message!!
        val chatId = msg.chat.id
        val text = """
        |Chat ID: <code>$chatId</code>.
        |复制到配置文件中的 <code>chats</code> 部分:
        |
        |<pre>chats:
        |  # other ids...
        |  - $chatId</pre>
        """.trimMargin()
        api.sendMessage(chatId, text, msg.messageId)
    }

    private suspend fun whitelistHandler(ctx: HandlerContext) {
        val msg = ctx.message!!
        val chatId = msg.chat.id
        if (!config.admins.contains(msg.from!!.username)) {
            api.sendMessage(chatId, config.whitelistNoPermission)
            return
        }
        if (ctx.commandArgs.count() != 2 && ctx.commandArgs.count() != 3) {
            if (config.debug) plugin.server.logger.info("Wrong usage of /whitelist!")
            api.sendMessage(chatId, config.whitelistUsage, msg.messageId)
            return
        }
        when (ctx.commandArgs[1]) {
            "add" -> {
                if (ctx.commandArgs.count() != 3) {
                    if (config.debug) plugin.server.logger.info("Wrong usage of /whitelist!")
                    api.sendMessage(chatId, config.whitelistUsage, msg.messageId)
                    return
                }
                val name = ctx.commandArgs[2]
                val result = uuidHelper.getUUID(name)
                if (result.code != "player.found") {
                    if (config.debug) plugin.server.logger.info("Player <b>$name</b> is not found!")
                    api.sendMessage(
                        chatId,
                        config.whitelistFailedString.replace("%username%", "<b>$name</b>"),
                        msg.messageId
                    )
                    return
                }
                val uuid = UUID.fromString(result.data!!.player.id)
                val player = plugin.server.getOfflinePlayer(uuid)
                player.isWhitelisted = true
                plugin.server.whitelistedPlayers.add(player)
                plugin.server.reloadWhitelist()
                api.sendMessage(
                    chatId,
                    config.whitelistAddSucceedString.replace("%username%", "<b>${player.name}</b>"),
                    msg.messageId
                )
            }
            "remove" -> {
                if (ctx.commandArgs.count() != 3) {
                    api.sendMessage(chatId, config.whitelistUsage, msg.messageId)
                    return
                }
                val name = ctx.commandArgs[2]
                val player = plugin.server.getOfflinePlayerIfCached(name)
                player?.let {
                    it.isWhitelisted = false
                    api.sendMessage(
                        chatId,
                        config.whitelistRemoveSucceedString.replace("%username%", "<b>$name</b>"),
                        msg.messageId
                    )
                } ?: api.sendMessage(
                    chatId,
                    config.whitelistFailedString.replace("%username%", "<b>$name</b>"),
                    msg.messageId
                )
            }
            "list" -> {
                if (ctx.commandArgs.count() != 2) {
                    api.sendMessage(chatId, config.whitelistUsage, msg.messageId)
                    return
                }
                val players = plugin.server.whitelistedPlayers
                var text = ""
                for (player in players) {
                    text += player.name + "\n"
                }
                text += players.count()
                api.sendMessage(chatId, text, msg.messageId)
            }
            "reload" -> {
                plugin.server.reloadWhitelist()
                api.sendMessage(chatId, "✔️", msg.messageId)
            }
            "on" -> {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    plugin.server.setWhitelist(true)
                })
                api.sendMessage(chatId, "✔️", msg.messageId)
            }
            "off" -> {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    plugin.server.setWhitelist(false)
                })
                api.sendMessage(chatId, "✖️", msg.messageId)
            }
            else -> {
                if (config.debug) plugin.server.logger.info("Wrong usage of /whitelist!")
                api.sendMessage(chatId, config.whitelistUsage, msg.messageId)
            }
        }

    }

    private suspend fun meowHandler(
        @Suppress("unused_parameter") ctx: HandlerContext
    ) {
        val msg = ctx.message!!
        val chatId = msg.chat.id
        api.sendMessage(chatId, meow.shuffled().take(1)[0], msg.messageId)
    }

    private fun onMessageHandler(
        @Suppress("unused_parameter") ctx: HandlerContext
    ) {
        val msg = ctx.message!!
        if (!config.allowedChats.contains(msg.chat.id)) {
            return
        }
        if (!config.logFromTGtoMC || msg.from == null)
            return

        plugin.sendMessageToMinecraft(
            text = processMessage(msg),
            username = msg.from.rawUserMention(),
            chatTitle = msg.chat.title,
        )
    }

    private fun processMessage(msg: Message): Component {
        val text = Component.text()
        msg.replyToMessage?.let {
            text.append(
                Component.text("[回复给 ${it.from?.rawUserMention()}]").color(NamedTextColor.GOLD)
                    .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, processMessage(it)))
            )
        }
        msg.forwardFrom?.let {
            val info = Component.text("转发自用户 ${it.rawUserMention()}")
            it.username?.let { username -> info.append(Component.text("\n@$username")) }
            text.append(
                Component
                    .text("[转发消息]")
                    .color(NamedTextColor.GOLD)
                    .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, info))
            )
        }
        msg.forwardFromChat?.let {
            val info = Component.text()
            when(it.type) {
                "channel" -> info.append(Component.text("转发自频道 ${it.title}"))
                "group" -> info.append(Component.text("转发自群组 ${it.title} 的管理员"))
            }
            text.append(Component
                .text("[转发消息]")
                .color(NamedTextColor.GOLD)
                .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,info.build()))
            )
        }
        if(!msg.photo.isNullOrEmpty()){
            text.append(Component
                .text("[图片]".apply {
                    msg.caption?.let {
                        this.plus(it)
                    }
                })
                .color(NamedTextColor.BLUE)
            )
        }
        msg.sticker?.let {
            text.append(
                Component.text("[${it.emoji}贴纸]")
                    .color(NamedTextColor.BLUE)
                    .hoverEvent(
                        HoverEvent.hoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            Component.text("来自贴纸包 ${it.setName}")
                        )
                    )
            )
        }
        msg.document?.let {
            text.append(Component.text("[文件 ${it.fileName}]").color(NamedTextColor.BLUE))
        }
        msg.voice?.let {
            text.append(Component.text("[语音 ${it.duration}秒]").color(NamedTextColor.BLUE))
        }
        msg.audio?.let {
            text.append(Component.text("[音频 ${it.duration}秒]").color(NamedTextColor.BLUE))
        }
        msg.video?.let {
            text.append(Component.text("[视频 ${it.duration}秒]").color(NamedTextColor.BLUE))
        }
        msg.poll?.let {
            text.append(Component.text("[投票] ${it.question}").color(NamedTextColor.BLUE))
        }

        msg.text?.let {
            text.append(Component.text(it))
        }

        if (text.children().isEmpty()) text.content("[消息]").color(NamedTextColor.DARK_GREEN)
        msg.caption?.let {
            text.append(Component.text(it))
        }
        return text.build()
    }

    suspend fun sendMessageToTelegram(text: String, username: String? = null) {
        val formatted = username?.let {
            config.telegramFormat
                .replace(C.USERNAME_PLACEHOLDER, username.fullEscape())
                .replace(C.MESSAGE_TEXT_PLACEHOLDER, text.escapeHtml())
        } ?: text
        config.allowedChats.forEach { chatId ->
            api.sendMessage(chatId, formatted)
        }
    }
}
