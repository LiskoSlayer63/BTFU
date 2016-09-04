package btfubackup

import net.minecraft.server.MinecraftServer

object WorldSavingControl {
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

  private def waitPerformTask(t: Int) {
    lock.synchronized{
      if (task != 0) throw new RuntimeException(s"Simultaneously scheduled WorldSavingControl tasks, $t and $task, are there multiple BTFU threads??")
      task = t
      lock.wait()
    }
  }

  /**
    * blocks while waiting for the save-off and flush to be performed on the main thread
    */
  def saveOffAndFlush() = waitPerformTask(1)

  /**
    * blocks while waiting for the save-on to be performed on the main thread
    */
  def restoreSaving() = waitPerformTask(2)

  private def realSaveTasks(t: Int) = {
    t match {
      case 1 => // save-off and save-all
        MinecraftServer.getServer.worldServers.foreach { worldserver =>
          if (worldserver != null) {
            worldserver.levelSaving = true
            worldserver.saveAllChunks(true, null)
            worldserver.saveChunkData()
            worldserver.levelSaving = false
          }
        }
      case 2 => // save-on
        MinecraftServer.getServer.worldServers.foreach { worldserver =>
          if (worldserver != null) {
            worldserver.levelSaving = true
          }
        }
      case _ => throw new IllegalArgumentException(s"internal error in WorldSavingControl: invalid task: $t")
    }
  }
}
