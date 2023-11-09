package com.diyigemt.arona.chat.command

import com.diyigemt.arona.command.CommandExecuteResult
import com.diyigemt.arona.command.CommandManager
import com.diyigemt.arona.communication.command.CommandSender.Companion.toCommandSender
import com.diyigemt.arona.communication.event.TencentMessageEvent
import com.diyigemt.arona.communication.message.PlainText
import com.diyigemt.arona.plugins.AronaPlugin
import com.diyigemt.arona.plugins.AronaPluginDescription
import com.diyigemt.arona.utils.error
import com.github.ajalt.clikt.core.CliktError
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

object PluginMain : AronaPlugin(
  AronaPluginDescription(
    id = "com.diyigemt.arona.chat.command",
    name = "chat-command",
    author = "diyigemt",
    version = "0.1.0",
    description = "chat-command"
  )
) {
  override fun onLoad() {
    pluginEventChannel().subscribeAlways<TencentMessageEvent>(
      CoroutineExceptionHandler { _, throwable ->
        logger.error(throwable)
      },
    ) {
      // 命令必须以 "/" 开头
      // TODO 正式环境上线
      val text = it.message.filterIsInstance<PlainText>().firstOrNull() ?: return@subscribeAlways
      val commandText = text.toString()
      if (!commandText.startsWith("/")) {
        return@subscribeAlways
      }
      val commandSender = runCatching {
        it.toCommandSender()
      }.getOrNull() ?: return@subscribeAlways
      // TODO exception print
      PluginMain.launch {
        when (val result = CommandManager.executeCommand(commandSender, it.message)) {
          is CommandExecuteResult.Success -> {

          }

          is CommandExecuteResult.UnmatchedSignature -> {
            // 发送错误处理
            val helpMessage = result.command.getFormattedHelp(result.exception as? CliktError) ?: return@launch
            commandSender.sendMessage(helpMessage)
          }

          else -> {
            result.exception?.let { it1 -> logger.error(it1) }
          }
        }
      }
    }
  }
}
