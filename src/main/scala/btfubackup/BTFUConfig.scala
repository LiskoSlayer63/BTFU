package btfubackup

import java.io.File
import java.nio.file.Path

import com.electronwill.nightconfig.core.UnmodifiableConfig
import net.minecraftforge.common.ForgeConfigSpec

import scala.jdk.CollectionConverters._

case class BTFUConfig (var backupDirProp: ForgeConfigSpec.ConfigValue[String], maxBackups: Int, disablePrompts: Boolean,
                       cmds: BTFUNativeCommands, systemless: Boolean, excludes: Iterable[String], maxAgeSec: Int) {
  val mcDir = FileActions.canonicalize(new File(".").toPath)

  def backupDir: Path = {
    FileActions.canonicalize(new File(backupDirProp.get()).toPath)
  }

  def setBackupDir(path: Path): Unit = {
    backupDirProp.set(path.toString)
  }
}

case class BTFUNativeCommands(rsync: String, cp: String, rm: String)

object BTFUConfig {
  val RSYNC="rsync"
  val CP="cp"
  val RM="rm"

  val builder = new ForgeConfigSpec.Builder()

  builder.comment("System Configuration")
    .push("system")

  builder.comment("use jvm implementation for backup tasks (disable to use platform-native rsync/cp/rm)")
    .define("systemless", true)

  builder.comment("custom rsync command")
    .define(RSYNC, RSYNC)

  builder.comment("custom cp command")
    .define(CP, CP)

  builder.comment("custom rm command")
    .define(RM, RM)

  builder.pop()
  builder.comment("BTFU Configuration")
    .push("btfu")

  builder.comment("backup directory")
    .define("backupDir", "")

  builder.comment("number of backups to keep")
    .define("backupCount", 128)

  builder.comment("disable interactive prompts if true")
    .define("disableInteractive", false)

  builder.comment("excluded paths",
    "For normal operation, see rsync manual for --exclude.  For systemless mode, see java.nio.file.PathMatcher.",
    "Patterns are for relative paths from the server root."
  ).define("exclude", List.empty[String].asJava)

  builder.comment("maximum backup age",
    "Backups older than this many days will be deleted prior to logarithmic pruning, -1 to keep a complete history"
  ).define("maxBackupAge", -1)

  builder.pop()

  val spec: ForgeConfigSpec = builder.build()

  def apply(c: UnmodifiableConfig): BTFUConfig = {
    val systemless = c.get[ForgeConfigSpec.BooleanValue]("system.systemless").get()
    val commands = if (systemless) (RSYNC, CP, RM) else ( // do not expose native tool path flags until systemless is disabled
      c.get[ForgeConfigSpec.ConfigValue[String]]("system.rsync").get(),
      c.get[ForgeConfigSpec.ConfigValue[String]]("system.cp").get(),
      c.get[ForgeConfigSpec.ConfigValue[String]]("system.rm").get()
    )
    val conf = BTFUConfig(
      c.get[ForgeConfigSpec.ConfigValue[String]]("btfu.backupDir"),
      c.get[ForgeConfigSpec.IntValue]("btfu.backupCount").get(),
      c.get[ForgeConfigSpec.BooleanValue]("btfu.disableInteractive").get(),
      (BTFUNativeCommands.apply _).tupled(commands),
      systemless,
      c.get[ForgeConfigSpec.ConfigValue[java.util.List[String]]]("btfu.exclude").get().asScala,
      60*60*24*c.get[ForgeConfigSpec.IntValue]("btfu.maxBackupAge").get()
    )
    conf
  }
}
