package btfubackup

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.Executors

import btfubackup.BTFU.cfg
import net.minecraft.server.MinecraftServer

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future}
import scala.util.{Failure, Success, Try}

class BTFUPerformer (val server: MinecraftServer) {
  var nextRun: Option[Long] = None
  var backupProcess: Option[BackupProcess] = None
  val worldSavingControl = new WorldSavingControl(server)

  def scheduleNextRun(): Unit = { nextRun = Some(System.currentTimeMillis + 1000 * 60 * 5) }

  def scheduleNextRun(time: Long): Unit = {
    nextRun = Some(time)
  }

  def tick(): Unit = {
    worldSavingControl.mainThreadTick()

    backupProcess.foreach{ p =>
      if (p.isCompleted) {
        p.futureTask.value.foreach {
          case Failure(exception) =>
            BTFU.logger.error("Backup process failed!", exception)
            BTFU.logger.error("====== ATTENTION ATTENTION ATTENTION ======")
            BTFU.logger.error("| BACKUP FAILED, YOU DO NOT HAVE A BACKUP |")
            BTFU.logger.error("===========================================")
          case Success(_) => ()
        }

        backupProcess = None
        if (BTFU.serverLive) scheduleNextRun()
      }
    }

    nextRun.foreach{ mils =>
      if (System.currentTimeMillis() >= mils && backupProcess.isEmpty) {
        backupProcess = Some(new BackupProcess(worldSavingControl))
        BTFU.logger.debug("Starting scheduled backup")
      }
    }
  }
}

object BTFUPerformer {
  val dateFormat = new SimpleDateFormat(s"yyyy-MM-dd_HH${
    if (System.getProperty("os.name").startsWith("Windows")) "." else ":" // windows can't have colons in filenames
  }mm")

  implicit val executor: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
}

class BackupProcess (val worldSavingControl: WorldSavingControl) {
  val fileActions = if (cfg.systemless) JvmNativeFileActions else ExternalCommandFileActions
  val modelDir = cfg.backupDir.resolve("model")
  val tmpDir = cfg.backupDir.resolve("tmp")

  private def datestampedBackups: List[(String, Long)] = cfg.backupDir.toFile.list.toList.
      map { s => (s, Try{ BTFUPerformer.dateFormat.parse(s).getTime }) }.
      collect { case (s, Success(d)) => (s, d) }.
      sortBy(_._2).reverse // sort by time since epoch descending

  private def deleteTmp() = deleteBackup("tmp") // clean incomplete backup copies
  private def deleteBackup(name: String) = fileActions.delete(new File(s"${cfg.backupDir}/$name"))

  import BTFUPerformer.executor

  val futureTask: Future[Unit] = Future { task() }
  private def task(): Unit = {
    /**
      * Phase 1: trim backups
      */
    {
      deleteTmp()

      var backups = datestampedBackups
      if (cfg.maxAgeSec > 0) {
        backups.headOption.map(_._2) match {
          case Some(newestTime) =>
            backups.dropWhile{case (_, time) => newestTime - time <= 1000 * cfg.maxAgeSec}.drop(1)
              .foreach { case (name, _) =>
                BTFU.logger.debug(s"Trimming old backup $name")
                deleteBackup(name)
              }
          case None =>
            BTFU.logger.debug("No old backups, skipping")
        }
      }

      while ({backups = datestampedBackups; backups.length + 1 > cfg.maxBackups}) {
        val toRemove = backups.sliding(3).map {
          case List((_, d1), (s, _), (_, d0)) =>
            (s, 1000000*(backups.head._2 - d0)/(d1 - d0)) // fitness score for removal
        }.maxBy(_._2)._1
        BTFU.logger.debug(s"Trimming backup $toRemove")
        deleteBackup(s"$toRemove")
      }
    }

    /**
      * Phase 2: rsync
      */
    BTFU.logger.info("Saving world...")
    worldSavingControl.saveOffAndFlush()
    val backupDatestamp = System.currentTimeMillis() // used later
    BTFU.logger.info("Rsyncing...")
    val rsyncSuccess = fileActions.sync(cfg.mcDir, modelDir, BTFU.cfg.excludes)
    worldSavingControl.restoreSaving()
    if (! rsyncSuccess) { // if we aborted here, we just have a partial rsync that can be corrected next time
      BTFU.logger.warn("rsync failed")
      return
    }

    /**
      * Phase 3: hardlink copy
      */
    if (! fileActions.hardlinkCopy(modelDir, tmpDir)) {
      BTFU.logger.warn("hardlink copy failed")
      deleteTmp()
      return
    }

    /**
      * Give the successful backup a date-name!
      */
    val datestr = BTFUPerformer.dateFormat.format(new Date(backupDatestamp))
    if (! tmpDir.toFile.renameTo(cfg.backupDir.resolve(datestr).toFile))
      BTFU.logger.warn("rename failure??")
    else
      BTFU.logger.debug(s"Backup succeeded: $datestr")
  }

  def isCompleted = futureTask.isCompleted
}
