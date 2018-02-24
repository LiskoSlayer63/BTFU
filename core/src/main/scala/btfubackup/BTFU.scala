package btfubackup

import java.io.File
import java.nio.file.{InvalidPathException, Path}

trait LogWrapper {
  def debug(s: String)
  def err(s: String)
  def warn(s: String)
  def warn(s: String, e: Throwable)
}

object BTFU {
  var cfg: BTFUConfig = _
  var log: LogWrapper = _
  var serverLive = false
}

abstract class BTFU(log: LogWrapper) {
  BTFU.log = log

  def getDediShlooper: Option[() => Option[String]]

  /**
    * @param path to check
    * @return Some(errormessage) if there is a problem, or None if the backup path is acceptable
    */
  def startupPathChecks(path: Path): Option[String] = {
    if (path.equals(BTFU.cfg.mcDir))
      return Some("Backups directory is not set or matches your minecraft directory.")

    if (!path.toFile.exists())
      return Some(s"Backups directory ${'"'}$path${'"'} does not exist.")

    if (FileActions.subdirectoryOf(path, BTFU.cfg.mcDir))
      return Some(s"Backups directory ${'"'}$path${'"'} is inside your minecraft directory ${'"'}${BTFU.cfg.mcDir}${'"'}.\n" +
        s"This mod backups your entire minecraft directory, so that won't work.")

    if (FileActions.subdirectoryOf(BTFU.cfg.mcDir, path))
      return Some(s"Backups directory ${'"'}$path${'"'} encompasses your minecraft server!\n" +
        s"(are you trying to run a backup without copying it, or back up to a directory your minecraft server is in?)")

    None
  }

  def handleStartupPathChecks(): Boolean = {
    startupPathChecks(BTFU.cfg.backupDir).foreach { error =>
      (if (BTFU.cfg.disablePrompts) None else getDediShlooper) match {
        case Some(shlooper) =>
          btfubanner()
          var pathCheck: Option[String] = Some(error)
          var enteredPath: Path = null
          do {
            log.err(pathCheck.get)
            log.err("Please enter a new path and press enter (or exit out and edit btfu.cfg)")

            pathCheck = try {
              startupPathChecks(FileActions.canonicalize(new File(shlooper().getOrElse(return false)).toPath))
            } catch {
              case t: InvalidPathException => Some(s"Invalid path: ${t.getMessage}")
              case t: Throwable => Some(s"${t.getClass.getCanonicalName}: ${t.getMessage}")
            }
          } while (pathCheck.isDefined)

          log.err(s"Awesome!  Your backups will go in $enteredPath.  I will shut up until something goes wrong!")
          BTFU.cfg.c.setBackupDir(enteredPath)
          return true
        case None =>
          btfubanner()
          log.err(s"/============================================================")
          log.err(s"| $error")
          log.err(s"| Please configure the backup path in btfu.cfg.")
          log.err(s"\\============================================================")
          return false
      }
    }
    true
  }

  def btfubanner(): Unit = {
    Seq(
      ",'\";-------------------;\"`.",
      ";[]; BBB  TTT FFF U  U ;[];",
      ";  ; B  B  T  F   U  U ;  ;",
      ";  ; B  B  T  F   U  U ;  ;",
      ";  ; BBB   T  FFF U  U ;  ;",
      ";  ; B  B  T  F   U  U ;  ;",
      ";  ; B  B  T  F   U  U ;  ;",
      ";  ; BBB   T  F    UU  ;  ;",
      ";  `.                 ,'  ;",
      ";    \"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"    ;",
      ";    ,-------------.---.  ;",
      ";    ;  ;\"\";       ;   ;  ;",
      ";    ;  ;  ;       ;   ;  ;",
      ";    ;  ;  ;       ;   ;  ;",
      ";//||;  ;  ;       ;   ;||;",
      ";\\\\||;  ;__;       ;   ;\\/;",
      " `. _;          _  ;  _;  ;",
      "   \" \"\"\"\"\"\"\"\"\"\"\" \"\"\"\"\" \"\"\""
    ).foreach{s => log.err("               " + s)}
  }
}