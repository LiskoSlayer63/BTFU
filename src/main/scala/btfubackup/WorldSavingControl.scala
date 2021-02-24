package btfubackup

import net.minecraft.server.MinecraftServer

class WorldSavingControl (val server: MinecraftServer) {
  private val lock = new Object()
  private var task:Int = 0

  /**
    * perform any scheduled tasks on the main thread
    */
  def mainThreadTick(): Unit = {
    lock.synchronized{
      if (task != 0) {
        realSaveTasks(task)
        task = 0
        lock.notify()
      }
    }
  }

  private def waitPerformTask(t: Int): Unit = {
    lock.synchronized{
      if (task != 0) throw new RuntimeException(s"Simultaneously scheduled WorldSavingControl tasks, $t and $task, are there multiple BTFU threads??")
      task = t
      lock.wait()
    }
  }

  /**
    * blocks while waiting for the save-off and flush to be performed on the main thread
    */
  def saveOffAndFlush(): Unit = waitPerformTask(1)

  /**
    * blocks while waiting for the save-on to be performed on the main thread
    */
  def restoreSaving(): Unit = waitPerformTask(2)

  private def realSaveTasks(t: Int): Unit = {
    t match {
      case 1 => // save-off and save-all
        server.getWorlds.forEach { worldserver =>
          if (worldserver != null) {
            worldserver.disableLevelSaving = false
            try {
              worldserver.save(null, true, false)
            } catch {
              case e: Throwable => BTFU.logger.warn("Exception from WorldServer.save", e)
            }

            worldserver.disableLevelSaving = true
          }
        }
      case 2 => // save-on
        server.getWorlds.forEach { worldserver =>
          if (worldserver != null) {
            worldserver.disableLevelSaving = false
          }
        }
      case _ => throw new IllegalArgumentException(s"internal error in WorldSavingControl: invalid task: $t")
    }
  }
}
