# Paper <-> Telegram bridge plugin

### 这个插件支持在Telegram和Minecraft同步聊天消息和各种信息。

## 如何使用:

1. 从 [releases](https://github.com/YukisawaNya/paper-tg-bridge/releases) 下载jar文件，然后把它放到服务器的 `plugins/` 文件夹 **或者** clone
   这个 repo 并运行 `gradle`。

2. 如果你有 telegram bot, 跳过这一步。 否则请使用 [BotFather](https://t.me/BotFather) 创建一个bot。根据指示创建bot并保存好 __token__ 。 **注意：**
   为了让你的bot能接收普通消息而不只是命令，你要关闭 [privacy mode](https://core.telegram.org/bots#privacy-mode) 。请去 **Bot Settings -> Group
   Privacy** ，点击 **Turn Off**.

3. 下一步，你需要把这个bot给插件使用。 你可以选择一种方法:
   - 启动服务器，让配置文件自动生成，然后关闭服务器，进入下一步
   - 复制 [config.yml](https://raw.githubusercontent.com/YukisawaNya/paper-tg-bridge/master/src/main/resources/config.yml)
     到服务器的 `plugins/SpigotTGBridge/` 目录内。

4. 把bot的 __token__ 放进 `config.yml` 如下：
   ```yaml
   botToken: abcdefghijklmnopq123123123
   # 别的配置...
   ```

5. 启动Paper服务器.

6. 把bot添加到群聊。在群里使用 `/chat_id` 命令。bot会回复给你一个 __chat id__ 。打开 `config.yml` 并把这个ID放到 `chats` 部分，如下：
    ```yaml
    botToken: abcdefghijklmnopq123123123
    chats:
      - -123456789
      - 987654321
      # 别的ID...
    ```

7. 你可以继续修改 `config.yml`
   如果不小心删除了什么，请看 [这里](https://raw.githubusercontent.com/YukisawaNya/paper-tg-bridge/master/src/main/resources/config.yml)。

8. 重启服务器或者在服务器后台使用 `tgbridge_reload` 。

## 插件配置:

|           名称           | 描述                                                                                      |           类型           |
|:----------------------:|:----------------------------------------------------------------------------------------|:----------------------:|
|         enable         | 插件是否启用                                                                                  |       `boolean`        |
|        botToken        | Bot token ([How to create bot](https://core.telegram.org/bots#3-how-do-i-create-a-bot)) |        `string`        |
|         chats          | Bot要把消息发到哪里                                                                             | `number[] or string[]` |
|   serverStartMessage   | 服务器启动时发送什么                                                                              |        `string`        |
|   serverStopMessage    | 服务器停止时发送什么                                                                              |        `string`        |
|      logJoinLeave      | 玩家进出服务器时发消息                                                                             |       `boolean`        |
|     logFromMCtoTG      | 从Minecraft转发消息到Telegram                                                                 |       `boolean`        |
|     logFromTGtoMC      | 从Telegram转发消息到Minecraft                                                                 |       `boolean`        |
|     logPlayerDeath     | 发送玩家死亡消息                                                                                |       `boolean`        |
|  logPlayerAdvancement  | 发送玩家获取的进度                                                                               |       `bollean`        |
|    logPlayerAsleep     | 发送玩家睡觉信息                                                                                |       `boolean`        |
|        strings         | 一些字符串                                                                                   | `Map<string, string>`  |
|        commands        | bot使用的命令                                                                                | `Map<string, string>`  |

## Telegram bot 命令:

自定义命令。

|     命令     | 描述                                                           |
|:----------:|:-------------------------------------------------------------|
| `/online`  | 获取在线玩家列表                                                     |
|  `/time`   | 获取服务器的 [时间](https://minecraft.gamepedia.com/Day-night_cycle) |
| `/chat_id` | 获取当前Chat ID                                                  |

## 插件命令:

|        命令         | 描述                      |
|:-----------------:|:------------------------|
| `tgbridge_reload` | 重新加载插件配置文件。只可以从服务器后台调用。 |
