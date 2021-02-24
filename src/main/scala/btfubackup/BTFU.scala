package btfubackup

import java.io.File
import java.nio.file.Path

import net.minecraft.server.dedicated.DedicatedServer
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.TickEvent.ServerTickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.config.ModConfig
import net.minecraftforge.fml.event.server.{FMLServerAboutToStartEvent, FMLServerStoppingEvent}
import org.apache.logging.log4j.{LogManager, Logger}

object BTFU {
  var cfg: BTFUConfig = BTFUConfig(BTFUConfig.spec.getValues)
  var logger: Logger = LogManager.getLogger("BTFU")
  var serverLive = false
  var performer: Option[BTFUPerformer] = None
}

@Mod("btfu") class BTFU {
  ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BTFUConfig.spec)

  ExtensionPoints.register()
  MinecraftForge.EVENT_BUS.register(this)

  @SubscribeEvent
  def onServerTick(event: ServerTickEvent): Unit = {
    if (event.phase == TickEvent.Phase.START) {
      BTFU.performer.foreach { p =>
        p.tick()
      }
    }
  }

  /**
    * @param path to check
    * @return Some(errormessage) if there is a problem, or None if the backup path is acceptable
    */
  def startupPathChecks(path: Path): Option[String] = {
    if (path.equals(BTFU.cfg.mcDir))
      return Some("Backups directory is not set or matches your minecraft directory.")

    if (! path.toFile.exists())
      return Some(s"Backups directory ${'"'}$path${'"'} does not exist.")

    if (FileActions.subdirectoryOf(path, BTFU.cfg.mcDir))
      return Some(s"Backups directory ${'"'}$path${'"'} is inside your minecraft directory ${'"'}${BTFU.cfg.mcDir}${'"'}.\n" +
        s"This mod backups your entire minecraft directory, so that won't work.")

    if (FileActions.subdirectoryOf(BTFU.cfg.mcDir, path))
      return Some(s"Backups directory ${'"'}$path${'"'} encompasses your minecraft server!\n" +
        s"(are you trying to run a backup without copying it, or back up to a directory your minecraft server is in?)")

    None
  }

  @SubscribeEvent
  def start(e: FMLServerAboutToStartEvent): Unit = {
    // Pull config again
    BTFU.cfg = BTFUConfig(BTFUConfig.spec.getValues)

    startupPathChecks(BTFU.cfg.backupDir).foreach { error =>
      e.getServer match {
        case dedi: DedicatedServer =>
          btfubanner()
          var pathCheck: Option[String] = Some(error)
          var enteredPath: Path = null
          do {
            BTFU.logger.error(pathCheck.get)
            BTFU.logger.error("Please enter a new path and press enter (or exit out and edit btfu-common.toml)")

            val cmd = {
              while (dedi.pendingCommandList.isEmpty && dedi.isServerRunning) {
                Thread.sleep(25) // imagine a world where we use notify when we add to the threadsafe list.
              }
              if (!dedi.isServerRunning) return // if the GUI window is closed
              dedi.pendingCommandList.remove(0)
            }

            enteredPath = FileActions.canonicalize(new File(cmd.command).toPath)
            pathCheck = startupPathChecks(enteredPath)
          } while (pathCheck.isDefined)

          BTFU.logger.error(s"Awesome!  Your backups will go in $enteredPath.  I will shut up until something goes wrong!")
          BTFU.cfg.setBackupDir(enteredPath)
        case server =>
          btfubanner()
          BTFU.logger.error(s"/============================================================")
          BTFU.logger.error(s"| $error")
          BTFU.logger.error(s"| Please configure the backup path in btfu-common.toml.")
          BTFU.logger.error(s"\\============================================================")

          server.initiateShutdown(false)
      }
    }

    BTFU.performer = Some(new BTFUPerformer(e.getServer))
    BTFU.serverLive = true
    BTFU.performer.foreach { p => p.scheduleNextRun() }
  }

  @SubscribeEvent
  def stop(e: FMLServerStoppingEvent): Unit = {
    BTFU.serverLive = false
    BTFU.performer.foreach { p => p.nextRun = None }
  }

  def btfubanner(): Unit = {
    BTFU.logger.error("               ,'\";-------------------;\"`.")
    BTFU.logger.error("               ;[]; BBB  TTT FFF U  U ;[];")
    BTFU.logger.error("               ;  ; B  B  T  F   U  U ;  ;")
    BTFU.logger.error("               ;  ; B  B  T  F   U  U ;  ;")
    BTFU.logger.error("               ;  ; BBB   T  FFF U  U ;  ;")
    BTFU.logger.error("               ;  ; B  B  T  F   U  U ;  ;")
    BTFU.logger.error("               ;  ; B  B  T  F   U  U ;  ;")
    BTFU.logger.error("               ;  ; BBB   T  F    UU  ;  ;")
    BTFU.logger.error("               ;  `.                 ,'  ;")
    BTFU.logger.error("               ;    \"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"    ;")
    BTFU.logger.error("               ;    ,-------------.---.  ;")
    BTFU.logger.error("               ;    ;  ;\"\";       ;   ;  ;")
    BTFU.logger.error("               ;    ;  ;  ;       ;   ;  ;")
    BTFU.logger.error("               ;    ;  ;  ;       ;   ;  ;")
    BTFU.logger.error("               ;//||;  ;  ;       ;   ;||;")
    BTFU.logger.error("               ;\\\\||;  ;__;       ;   ;\\/;")
    BTFU.logger.error("                `. _;          _  ;  _;  ;")
    BTFU.logger.error("                  \" \"\"\"\"\"\"\"\"\"\"\" \"\"\"\"\" \"\"\"")
  }
}
