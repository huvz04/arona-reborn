package com.diyigemt.arona.chat.command

import com.diyigemt.arona.command.CommandExecuteResult
import com.diyigemt.arona.command.CommandManager
import com.diyigemt.arona.communication.command.CommandSender.Companion.toCommandSender
import com.diyigemt.arona.communication.event.TencentMessageEvent
import com.diyigemt.arona.communication.message.tencentCustomMarkdown
import com.diyigemt.arona.config.AutoSavePluginData
import com.diyigemt.arona.config.value
import com.diyigemt.arona.plugins.AronaPlugin
import com.diyigemt.arona.plugins.AronaPluginDescription
import com.diyigemt.arona.utils.error
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.UsageError
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

@Suppress("unused")
object PluginMain : AronaPlugin(
  AronaPluginDescription(
    id = "com.diyigemt.arona.chat.command",
    name = "chat-command",
    author = "diyigemt",
    version = "0.1.6",
    description = "chat-command"
  )
) {
  override fun onLoad() {
    val ignoreList = Config.ignoreUser + Config.ignoreGroup + Config.ignoreGuild
    pluginEventChannel().subscribeAlways<TencentMessageEvent>(
      CoroutineExceptionHandler { _, throwable ->
        logger.error(throwable)
      },
    ) {
      if (it.subject.id in ignoreList) {
        return@subscribeAlways
      }
      val commandSender = runCatching {
        it.toCommandSender()
      }.getOrNull() ?: return@subscribeAlways
      // TODO exception print
      PluginMain.launch {
        when (val result = CommandManager.executeCommand(commandSender, it.message).await()) {
          is CommandExecuteResult.Success -> {

          }

          is CommandExecuteResult.UnmatchedSignature -> {
            // 发送错误处理
            val helpMessage = result.command.getFormattedHelp(result.exception as? CliktError) ?: return@launch
            val fullHelp = result.command.getFormattedHelp() ?: return@launch
            val md = tencentCustomMarkdown { }
              .apply { content = fullHelp } +
              "用户手册: [AronaDoc](https://doc.arona.diyigemt.com/v2/manual/command)"
            commandSender.sendMessage(md)
          }

          is CommandExecuteResult.ExecutionFailed -> {
            when (result.exception) {
              is UsageError -> {
                val helpMessage = result.command.getFormattedHelp(result.exception as? CliktError) ?: return@launch
                val md = tencentCustomMarkdown { }
                  .apply { content = helpMessage } +
                  "用户手册: [AronaDoc](https://doc.arona.diyigemt.com/v2/manual/command)"
                commandSender.sendMessage(md)
              }

              else -> result.exception.let { it1 -> logger.error(it1) }
            }
          }

          is CommandExecuteResult.PermissionDenied -> {
            commandSender.sendMessage("权限不足")
          }

          else -> result.exception?.let { it1 -> logger.error(it1) }
        }
      }
    }
  }
}

object Config : AutoSavePluginData("config") {
  val ignoreGuild by value(listOf<String>())
  val ignoreGroup by value(listOf<String>())
  val ignoreUser by value(listOf<String>())
}
