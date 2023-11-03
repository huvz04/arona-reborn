package com.diyigemt.arona.utils

import com.charleskorn.kaml.Yaml
import com.diyigemt.arona.communication.TencentBotConfig
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.charset.Charset

@Serializable
data class RedisConfig(
  val host: String,
  val port: Int,
  val db: ULong = 0u
)
@Serializable
data class AronaConfig(
  val debug: Boolean = false,
  val bot: TencentBotConfig,
  val adminToken: String,
  val redis: RedisConfig
)

internal val aronaConfig: AronaConfig by lazy {
  val reader = File("config.yaml").bufferedReader(Charset.forName("UTF-8"))
  val input = reader.use { it.readText() }
  Yaml.default.decodeFromString(AronaConfig.serializer(), input)
}

internal val isDebug = aronaConfig.debug
