package nya.yukisawa.paper_tg_bridge

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import okhttp3.OkHttpClient
import org.bukkit.Bukkit
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.Runnable
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
    private var commandResult = ""
    private var commandTimer: Timer? = null
    private val commandSender = plugin.server.createCommandSender {
        commandResult += it.processComponent(config.lang) + "\n"
        //plugin.server.logger.info(result)
        commandTimer?.cancel()
        commandTimer = Timer()
        commandTimer!!.schedule(object : TimerTask() {
            override fun run() {
                runBlocking {
                    plugin.server.logger.info(commandResult)
                    if (commandResult.isBlank()) return@runBlocking
                    sendMessageWithoutParse(commandResult)
                    commandResult = ""
                }
            }
        }, 100)
    }
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
            meow to ::meowHandler,
            command to ::commandHandler
        )
    }

    private val meow = listOf("喵喵喵~", "喵呜！", "Nya~", "喵", "Meow~", "喵喵？", "捕捉小猫咪！")

    private suspend fun initialize() {
        me = api.getMe().result!!
        val commands = config.commands.run { listOf(time, online, meow, command) }
            .zip(
                C.COMMAND_DESC.run {
                    listOf(timeDesc, onlineDesc, meowDesc, commandDesc)
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

    private suspend fun commandHandler(ctx: HandlerContext) {
        if (!config.admins.contains(ctx.message?.from?.username)) {
            sendMessageToTelegram(config.noPermission)
            return
        }
        if (ctx.commandArgs.size == 1) {
            sendMessageWithoutParse("${ctx.commandArgs[0]} <command>")
            return
        }
        val args = ctx.commandArgs.subList(1, ctx.commandArgs.size)
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val cmd = args.joinToString(" ")
            plugin.server.logger.info(cmd)
            plugin.server.dispatchCommand(commandSender, cmd)
        })
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
            text = processMessage(msg, config.messageTrim),
            username = msg.from.rawUserMention(),
            chatTitle = msg.chat.title,
        )
    }

    private fun processMessage(msg: Message, trim: Int = 0, showMore: Boolean = true): Component {
        val text = Component.text()
        msg.replyToMessage?.let {
            text.append(
                Component
                    .text(
                        "[回复 ${it.from?.rawUserMention()}: \"${
                            PlainTextComponentSerializer.plainText().serialize(processMessage(it, 10, false))
                        }\"]"
                    )
                    .color(NamedTextColor.GOLD)
                    .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, processMessage(it)))
            )
        }
        msg.forwardFrom?.let {
            val info = Component.text("转发自用户 ${it.rawUserMention()}")
            it.username?.let { username -> info.append(Component.text("\n@$username")) }
            text.append(
                Component
                    .text("[转发]")
                    .color(NamedTextColor.GOLD)
                    .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, info))
            )
        }
        msg.forwardFromChat?.let {
            val info = Component.text()
            when (it.type) {
                "channel" -> info.append(Component.text("转发自频道 ${it.title}"))
                "group" -> info.append(Component.text("转发自群组 ${it.title} 的管理员"))
            }
            text.append(
                Component
                    .text("[转发]")
                    .color(NamedTextColor.GOLD)
                    .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, info.build()))
            )
        }
        if (!msg.photo.isNullOrEmpty()) {
            text.append(
                Component
                    .text("[图片]")
                    .color(NamedTextColor.BLUE)
            )
        }
        msg.sticker?.let {
            text.append(
                Component
                    .text("[${it.emoji}贴纸]")
                    .color(NamedTextColor.BLUE)
                    .hoverEvent(
                        HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, Component.text("来自贴纸包 ${it.setName}"))
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
            text.append(
                Component.text("[投票] ${processMessageText(it.question, trim, showMore)}").color(NamedTextColor.BLUE)
            )
        }

        msg.text?.let {
            text.append(processMessageText(it, trim, showMore))
        }

        if (text.children().isEmpty()) text.content("[消息]").color(NamedTextColor.DARK_GREEN)
        msg.caption?.let {
            text.append(processMessageText(it, trim, showMore))
        }
        return text.build()
    }

    private fun processMessageText(text: String, trim: Int, showMore: Boolean): Component {
        val msg = text.replace("\n", " ")
        val builder = Component.text()
        if (trim != 0 && msg.length > trim) {
            builder.append(Component.text("${msg.substring(0, trim)}..."))
            if (showMore) builder.append(
                Component.text("[全文]")
                    .color(NamedTextColor.GOLD)
                    .hoverEvent(
                        HoverEvent.hoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            Component.text(text)
                        )
                    )
            )
        } else builder.append(Component.text(msg))
        return builder.build()
    }

    suspend fun sendMessageToTelegram(text: String, username: String? = null) {
        val formatted = username?.let {
            config.telegramFormat
                .replace(C.USERNAME_PLACEHOLDER, username.fullEscape())
                .replace(C.MESSAGE_TEXT_PLACEHOLDER, text.escapeHTML())
        } ?: text
        config.allowedChats.forEach { chatId ->
            api.sendMessage(chatId, formatted)
        }
    }

    suspend fun sendMessageWithoutParse(text: String, username: String? = null) {
        val formatted = username?.let {
            config.telegramFormat
                .replace(C.USERNAME_PLACEHOLDER, username.fullEscape())
                .replace(C.MESSAGE_TEXT_PLACEHOLDER, text.escapeHTML())
        } ?: text
        plugin.server.logger.info("sending $formatted")
        config.allowedChats.forEach { chatId ->
            api.sendMessageWithoutParse(chatId, formatted)
        }
    }
}
