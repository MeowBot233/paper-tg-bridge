package org.kraftwerk28.spigot_tg_bridge

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
import org.kraftwerk28.spigot_tg_bridge.Constants as C

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
    private val updateChan = Channel<Update>()
    private var pollJob: Job? = null
    private var handlerJob: Job? = null
    private var currentOffset: Long = -1
    private var me: User? = null
    private var commandRegex: Regex? = null
    private val commandMap: Map<String?, CmdHandler> = config.commands.run {
        mapOf(
            online to ::onlineHandler,
            time to ::timeHandler,
            chatID to ::chatIdHandler,
            // TODO:
            // linkIgn to ::linkIgnHandler,
            // getAllLinked to ::getLinkedUsersHandler,
        )
    }

    private suspend fun initialize() {
        me = api.getMe().result!!
        // I intentionally don't put optional @username in regex
        // since bot is only used in group chats
        commandRegex = """^/(\w+)@${me!!.username}(?:\s+(.+))?$""".toRegex()
        val commands = config.commands.run { listOf(time, online, chatID) }
            .zip(
                C.COMMAND_DESC.run {
                    listOf(timeDesc, onlineDesc, chatIDDesc)
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
        // Ignore private message or channel post
        if (listOf("private", "channel").contains(update.message?.chat?.type))
            return
        val ctx = HandlerContext(
            update,
            update.message,
            update.message?.chat,
        )
        update.message?.text?.let {
            commandRegex?.matchEntire(it)?.groupValues?.let { matchList ->
                commandMap[matchList[1]]?.run {
                    val args = matchList[2].split("\\s+".toRegex())
                    this(ctx.copy(commandArgs = args))
                }
            } ?: run {
                onTextHandler(ctx)
            }
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
        api.sendMessage(msg.chat.id, text, replyToMessageId = msg.messageId)
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
        api.sendMessage(msg.chat.id, text, replyToMessageId = msg.messageId)
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
        api.sendMessage(chatId, text, replyToMessageId = msg.messageId)
    }

    private suspend fun linkIgnHandler(ctx: HandlerContext) {
        val tgUser = ctx.message!!.from!!
        val mcUuid = getMinecraftUuidByUsername(ctx.message.text!!)
        if (mcUuid == null || ctx.commandArgs.isEmpty()) {
            // Respond...
            return
        }
        val (minecraftIgn) = ctx.commandArgs
        val linked = plugin.ignAuth?.linkUser(
            tgId = tgUser.id,
            tgFirstName = tgUser.firstName,
            tgLastName = tgUser.lastName,
            minecraftUsername = minecraftIgn,
            minecraftUuid = mcUuid,
        ) ?: false
        if (linked) {
            // TODO
        }
    }

    private suspend fun getLinkedUsersHandler(ctx: HandlerContext) {
        val linkedUsers = plugin.ignAuth?.run {
            getAllLinkedUsers()
        } ?: listOf()
        if (linkedUsers.isEmpty()) {
            api.sendMessage(ctx.message!!.chat.id, "No linked users.")
        } else {
            val text = "<b>Linked users:</b>\n" +
                linkedUsers.mapIndexed { i, dbUser ->
                    "${i + 1}. ${dbUser.fullName()}"
                }.joinToString("\n")
            api.sendMessage(ctx.message!!.chat.id, text)
        }
    }

    private fun onTextHandler(
        @Suppress("unused_parameter") ctx: HandlerContext
    ) {
        val msg = ctx.message!!
        plugin.server.logger.info(msg.toString())
        if (!config.logFromTGtoMC || msg.from == null)
            return
        val text = Component.empty()
        msg.replyToMessage?.let {
            text.append(Component.text("[回复给 ${it.from?.rawUserMention()}]").color(NamedTextColor.GOLD))
        }
        if(msg.photo.isNotEmpty()){
            text.append(Component.text("[${msg.photo.size}张图片]").color(NamedTextColor.BLUE))
        }
        msg.sticker?.let {
            text.append(Component.text("[贴纸]").color(NamedTextColor.BLUE))
                .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, Component.text("来自贴纸包 ${it.setName}")) )
        }
        msg.document?.let {
            text.append(Component.text("[文件] ${it.fileName}").color(NamedTextColor.BLUE))
        }
        msg.video?.let {
            text.append(Component.text("[视频]").color(NamedTextColor.BLUE))
        }
        msg.poll?.let {
            text.append(Component.text("[投票] ${it.question}").color(NamedTextColor.BLUE))
        }

        msg.text?.let {
            text.append(Component.text(it))
        }

        if(text == Component.empty()) text.content("[消息]").color(NamedTextColor.DARK_GREEN)
        plugin.sendMessageToMinecraft(
            text = text,
            username = msg.from.rawUserMention(),
            chatTitle = msg.chat.title,
        )
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
