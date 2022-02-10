package org.kraftwerk28.spigot_tg_bridge

object Constants {
    const val configFilename = "config.yml"
    object WARN {
        const val noConfigWarning = "没有找到配置文件！默认配置文件将写入到 config.yml."
        const val noToken = "Bot token 必填！"
    }
    object INFO {
        const val reloading = "重新加载..."
        const val reloadComplete = "完成"
    }
    object TIMES_OF_DAY {
        const val day = "\uD83C\uDFDE 白天"
        const val sunset = "\uD83C\uDF06 日落"
        const val night = "\uD83C\uDF03 夜晚"
        const val sunrise = "\uD83C\uDF05 日出"
    }
    const val USERNAME_PLACEHOLDER = "%username%"
    const val MESSAGE_TEXT_PLACEHOLDER = "%message%"
    const val CHAT_TITLE_PLACEHOLDER = "%chat%"
    object COMMANDS {
        const val PLUGIN_RELOAD = "tgbridge_reload"
    }
    object COMMAND_DESC {
        const val timeDesc = "获取服务器时间"
        const val onlineDesc = "获取在线玩家"
        const val chatIDDesc = "获取chat id"
    }
}
