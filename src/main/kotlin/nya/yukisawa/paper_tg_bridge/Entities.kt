package nya.yukisawa.paper_tg_bridge

import com.google.gson.annotations.SerializedName as Name

data class TgResponse<T>(
    val ok: Boolean,
    val result: T?,
    val description: String?,
)


data class User(
    @Name("id") val id: Long,
    @Name("is_bot") val isBot: Boolean,
    @Name("first_name") val firstName: String,
    @Name("last_name") val lastName: String? = null,
    @Name("username") val username: String? = null,
    @Name("language_code") val languageCode: String? = null,
)

data class Chat(
    val id: Long,
    val type: String,
    val title: String = "",
    val username: String? = null,
    @Name("first_name") val firstName: String? = null,
    @Name("last_name") val lastName: String? = null,
)

data class Message(
    @Name("message_id") val messageId: Long,
    val from: User? = null,
    @Name("sender_chat") val senderChat: Chat? = null,
    @Name("forward_from") val forwardFrom: User? = null,
    @Name("forward_from_chat") val forwardFromChat: Chat? = null,
    val date: Long,
    val chat: Chat,
    @Name("reply_to_message") val replyToMessage: Message? = null,
    val text: String? = null,
    val photo:Array<PhotoSize>? = emptyArray(),
    val sticker: Sticker? = null,
    val video: Any? = null,
    val voice: Any? = null,
    val audio: Audio? = null,
    val document: Document? = null,
    val poll: Poll? = null
)

data class PhotoSize(
    @Name("file_id") val fileId: String
)

data class Sticker(
    val emoji: String? = null,
    @Name("set_name") val setName: String? = null
)

data class Poll(
    val question: String
)

data class Document(
    @Name("file_name") val fileName: String
)

data class Audio(
    val duration: Int
)

data class Update(
    @Name("update_id") val updateId: Long,
    val message: Message? = null,
)

data class BotCommand(val command: String, val description: String)

data class SetMyCommands(val commands: List<BotCommand>)
