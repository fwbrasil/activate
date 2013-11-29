//package net.fwbrasil.activate.migration
//
//import net.fwbrasil.activate.storage.Storage
//import net.fwbrasil.activate.ActivateContext
//import net.fwbrasil.activate.entity.EntityHelper
//import net.fwbrasil.activate.util.ManifestUtil._
//import net.fwbrasil.activate.util.Reflection._
//import net.fwbrasil.activate.util.Logging
//
//class StorageMigration[A <: ActivateContext](val context: A, val oldStorage: Storage[_]) extends Logging {
//
//    import context._
//
//    val newStorage =
//        context.storage
//
//    def updateProgress(entityName: String, progressPercentage: Double) {
//        val width = 30
//        var string = ""
//        string += " ["
//        val i = 0
//        for (i <- 0 until (progressPercentage * width).intValue) {
//            string += "."
//        }
//        for (i <- (progressPercentage * width).intValue until width) {
//            string += " "
//        }
//        string += "] " + ((progressPercentage * 100).intValue) + "% " + entityName
//        info(string)
//    }
//
//    def migrate(step: Int = 1) = logInfo("Migrating storage...") {
//        import language.postfixOps
//        val classes = EntityHelper.metadatas.map(m => EntityHelper.concreteClasses(m.entityClass)).flatten.filter(context.acceptEntity(_)).toSet
//        for (clazz <- classes) {
//            set(context, "storage", oldStorage)
//            val ids =
//                transactional {
//                    produceQuery {
//                        (e: BaseEntity) => where(e isNotNull) select (e.id)
//                    }(manifestClass(clazz)).execute
//                }
//            val groups = ids.grouped(step).toList
//            if (groups.isEmpty)
//                updateProgress(clazz.getSimpleName, 1)
//            else
//                updateProgress(clazz.getSimpleName, 0)
//            for (i <- 0 until groups.size) {
//                val slot = groups(i)
//                transactional {
//                    for (id <- slot) {
//                        set(context, "storage", oldStorage)
//                        val entity = byId[BaseEntity](id).get
//                        liveCache.initialize(entity)
//                        entity.setNotPersisted
//                        entity.vars.foreach(ref => ref.put(ref.get)) //touch
//                        set(context, "storage", newStorage)
//                    }
//                }
//                updateProgress(clazz.getSimpleName, (i + 1).doubleValue / groups.size.doubleValue)
//            }
//
//        }
//    }
//}