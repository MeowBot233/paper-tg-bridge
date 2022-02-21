package nya.yukisawa.paper_tg_bridge

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import nya.yukisawa.paper_tg_bridge.Constants as C

class CommandHandler(private val plugin: Plugin) : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is ConsoleCommandSender && !sender.isOp) return false
        return when (label) {
            C.COMMANDS.PLUGIN_RELOAD -> {
                plugin.launch { plugin.reload(sender) }
                true
            }
            else -> false
        }
    }
}
