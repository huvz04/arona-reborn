package com.diyigemt.kivotos.coffee

import com.diyigemt.arona.arona.database.DatabaseProvider
import com.diyigemt.arona.arona.database.student.StudentSchema
import com.diyigemt.arona.arona.database.student.StudentTable
import com.diyigemt.arona.command.AbstractCommand
import com.diyigemt.arona.command.SubCommand
import com.diyigemt.arona.communication.command.UserCommandSender
import com.diyigemt.arona.communication.message.*
import com.diyigemt.arona.utils.*
import com.diyigemt.kivotos.Kivotos
import com.diyigemt.kivotos.KivotosCommand
import com.diyigemt.kivotos.coffee.CoffeeCommand.updateCoffeeStudents
import com.diyigemt.kivotos.subButton
import com.diyigemt.kivotos.tools.database.DocumentCompanionObject
import com.diyigemt.kivotos.tools.database.idFilter
import com.diyigemt.kivotos.tools.database.withCollection
import com.github.ajalt.clikt.parameters.arguments.argument
import com.mongodb.client.model.Updates
import com.mongodb.client.result.UpdateResult
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId

private suspend fun UserCommandSender.coffee() = CoffeeDocument.withCollection<CoffeeDocument, CoffeeDocument?> {
  find(filter = idFilter(userDocument().id)).limit(1).firstOrNull()
} ?: CoffeeDocument.withCollection<CoffeeDocument, CoffeeDocument> {
  CoffeeDocument(userDocument().id).also {
    insertOne(it)
  }
}

private val md = tencentCustomMarkdown {
  h1("夏莱附属咖啡厅")
}

@SubCommand(forClass = KivotosCommand::class)
@Suppress("unused")
object CoffeeCommand : AbstractCommand(
  Kivotos,
  "咖啡厅",
  description = "咖啡厅系列指令",
) {
  suspend fun UserCommandSender.coffee0() {
    updateCoffeeStudents(currentContext.invokedSubcommand == null)
  }

  @SubCommand
  @Suppress("unused")
  object CoffeeTouchCommand : AbstractCommand(
    Kivotos,
    "摸头",
    description = "咖啡厅摸头指令",
    help = """
      /赛博基沃托斯 咖啡厅 摸头 日奈
    """.trimIndent()
  ) {
    private val studentName by argument("学生名")
    suspend fun UserCommandSender.coffeeTouch() {
      val coffee = coffee()
      val targetStudent = DatabaseProvider.dbQuery {
        StudentSchema.find { StudentTable.name eq studentName }.firstOrNull()
      }
      if (targetStudent == null) {
        val md = tencentCustomMarkdown {
          +"没法摸摸$studentName, 学生不存在"
        }
        sendMessage(md)
        return
      }
      if (coffee.touchedStudents.isNotEmpty()) {
        val students = DatabaseProvider.dbQuery {
          StudentSchema.find {
            StudentTable.id inList coffee.touchedStudents
          }.toList()
        }
        if (studentName !in students.map { it.name }) {
          val md = tencentCustomMarkdown {
            +"没法摸摸$studentName, 她没来访问呢"
          }
          sendMessage(md)
          return
        }
        val md = md + tencentCustomMarkdown {
          + "你摸了摸$studentName, 功德+3"
        }
        val kb = tencentCustomKeyboard(bot.unionOpenidOrId) {
          (students.map { it.name }.filter { it != studentName } + listOf("一键摸头")).windowed(2, 2, true)
            .forEach { r ->
              row {
                r.forEach { c ->
                  if (c == "一键摸头") {
                    subButton(c, "咖啡厅 一键摸头")
                  } else {
                    subButton("摸摸$c", "咖啡厅 摸头 $c")
                  }
                }
              }
            }
        }
        sendMessage(md + kb)
        coffee.updateTouchedStudents(coffee.touchedStudents.toMutableList().also {
          it.remove(targetStudent.id.value)
        })
      } else {
        sendMessage(md + tencentCustomMarkdown {
          +"所有学生已经被摸过了, 等下一次刷新吧"
        })
      }
    }
  }

  @SubCommand
  @Suppress("unused")
  object CoffeeTouchAllCommand : AbstractCommand(
    Kivotos,
    "一键摸头",
    description = "咖啡厅一键摸头指令",
  ) {
    suspend fun UserCommandSender.coffeeTouch() {
      val coffee = coffee()
      if (coffee.touchedStudents.isEmpty()) {
        sendMessage("所有学生已经被摸过了, 等下一次刷新吧")
        return
      }
      val students = DatabaseProvider.dbQuery {
        StudentSchema.find {
          StudentTable.id inList coffee.touchedStudents
        }.toList()
      }
      sendMessage("你分别对\n${students.joinToString("\n") { it.name }}\n使出了摸摸,效果拔群!")
      coffee.updateTouchedStudents(listOf())
    }
  }

  @Suppress("lower_case")
  private const val StudentUpdateTime0 = "03:00:00"
  private const val StudentUpdateTime1 = "15:00:00"
  private suspend fun UserCommandSender.updateCoffeeStudents(sendMessage: Boolean) {
    val coffee = coffee()
    var md = md + tencentCustomMarkdown {
      +"当前咖啡厅等级: ${coffee.level}"
    }
    val kb = tencentCustomKeyboard(bot.unionOpenidOrId) {}
    val last = coffee.lastStudentUpdateTime
    if (checkStudentUpdate(last)) {
      // 需要更新来访的学生
      val students = DatabaseProvider.dbQuery {
        val max = StudentSchema.count()
        StudentSchema.find {
          StudentTable.id inList IntArray(studentCount(coffee.level)) { (1..max.toInt()).random() }.toList()
        }.toList()
      }
      md += tencentCustomMarkdown {
        +"来访学生"
        list {
          students.map { it.name }.forEach { s ->
            +s
          }
        }
      }
      kb + tencentCustomKeyboard(bot.unionOpenidOrId) {
        (students.map { it.name } + listOf("一键摸头")).windowed(2, 2, true).forEach { r ->
          row {
            r.forEach { c ->
              if (c == "一键摸头") {
                subButton(c, "咖啡厅 一键摸头")
              } else {
                subButton("摸摸$c", "咖啡厅 摸头 $c")
              }
            }
          }
        }
      }
      coffee.updateStudents(students.map { it.id.value })
    } else {
      val visitedStudents = DatabaseProvider.dbQuery {
        StudentSchema.find {
          StudentTable.id inList coffee.students
        }.toList()
      }
      md += tencentCustomMarkdown {
        +"来访学生"
        list {
          visitedStudents.map { it.name }.forEach { s ->
            +s
          }
        }
      }
      // 获取还没摸头的学生
      if (coffee.touchedStudents.isNotEmpty()) {
        val students = DatabaseProvider.dbQuery {
          StudentSchema.find {
            StudentTable.id inList coffee.touchedStudents
          }.toList()
        }
        kb + tencentCustomKeyboard(bot.unionOpenidOrId) {
          (students.map { it.name } + listOf("一键摸头")).windowed(2, 2, true).forEach { r ->
            row {
              r.forEach { c ->
                if (c == "一键摸头") {
                  subButton(c, "咖啡厅 一键摸头")
                } else {
                  subButton("摸摸$c", "咖啡厅 摸头 $c")
                }
              }
            }
          }
        }
      }
    }
    if (sendMessage) {
      sendMessage(md + kb)
    }
  }

  private fun checkStudentUpdate(last0: String): Boolean {
    val now = now()
    val nowDatetime = now.toDateTime()
    val last = last0.toInstant()
    val morning = "${currentDate()} $StudentUpdateTime0"
    val afternoon = "${currentDate()} $StudentUpdateTime1"
    // 超过12小时 肯定需要更新
    return (now - last).inWholeHours >= 12L ||
      morning in last0..nowDatetime ||
      afternoon in last0..nowDatetime
  }

  private fun studentCount(level: Int) = when (level) {
    1 -> 1
    in (1..4) -> 2
    in (5..7) -> 3
    else -> 4
  }
}

@Serializable
data class CoffeeDocument(
  @BsonId val id: String, // 用户id userDocument().id
  val level: Int = 5,
  val students: List<Int> = listOf(), // 来访的学生
  val touchedStudents: List<Int> = listOf(), // 能摸的学生, 为什么不是摸过的学生呢, 因为有些来的学生你没有啊
  val lastTouchTime: String = "2000-11-04 05:14:00", // 摸头计时器 每3小时刷新一次
  val lastInviteTime: String = "2000-11-04 05:14:00",
  val lastStudentUpdateTime: String = "2000-11-04 05:14:00",
  val lastRewordCollectTime: String = "2000-11-04 05:14:00",
) {
  suspend fun updateStudents(students: List<Int>) {
    updateStudents0(CoffeeDocument::students.name, students)
    updateStudents0(CoffeeDocument::touchedStudents.name, students)
    updateTime(CoffeeDocument::lastStudentUpdateTime.name, currentDateTime())
  }

  suspend fun updateTouchedStudents(students: List<Int>) {
    updateStudents0(CoffeeDocument::touchedStudents.name, students)
  }

  suspend fun updateTime(name: String, value: String) {
    withCollection<CoffeeDocument, UpdateResult> {
      updateOne(
        filter = idFilter(id),
        update = Updates.set(name, value)
      )
    }
  }

  private suspend fun updateStudents0(name: String, students: List<Int>) {
    withCollection<CoffeeDocument, UpdateResult> {
      updateOne(
        filter = idFilter(id),
        update = Updates.set(name, students)
      )
    }
  }

  companion object : DocumentCompanionObject {
    override val documentName = "CoffeeTouch"
  }
}
