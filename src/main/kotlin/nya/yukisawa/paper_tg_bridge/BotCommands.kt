package nya.yukisawa.paper_tg_bridge

import org.bukkit.configuration.file.FileConfiguration

class BotCommands(cfg: FileConfiguration) {
    val time: String?
    val online: String?
    val chatID: String?
    val whitelist: String?
    val meow: String?

    init {
        cfg.run {
            time = getString("commands.time")
            online = getString("commands.online")
            chatID = getString("commands.chat_id")
            whitelist = getString("commands.whitelist")
            meow = getString("commands.meow")
        }
    }
}
