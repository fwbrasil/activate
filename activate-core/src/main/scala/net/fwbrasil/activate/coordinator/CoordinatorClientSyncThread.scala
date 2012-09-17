package net.fwbrasil.activate.coordinator

case class CoordinatorClientSyncThread(client: CoordinatorClient) extends Thread {

	val context = client.context

	setDaemon(true)
	setName("CoordinatorClientSyncThread - " + context + "@" + context.contextId)
	setPriority(Thread.MIN_PRIORITY)
	//	start

	var stopFlag = false

	val syncSleep =
		Integer.parseInt(
			Option(System.getProperty("activate.coordinator.syncSleep"))
				.getOrElse("1000"))

	override def run =
		while (!stopFlag) {
			val notifications = client.getPendingNotifications
			if (notifications.nonEmpty) {
				client.context.reloadEntities(notifications)
				client.removeNotifications(notifications)
			} else
				Thread.sleep(syncSleep)
		}
}