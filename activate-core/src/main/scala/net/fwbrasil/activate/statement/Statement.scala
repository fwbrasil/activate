package net.fwbrasil.activate.statement

import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateContext
import From.runAndClearFrom
import net.fwbrasil.activate.cache.live.LiveCache
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.util.ManifestUtil.erasureOf

trait StatementContext extends StatementValueContext with OperatorContext {

	private[activate] val liveCache: LiveCache

	protected def mockEntity[E <: Entity: Manifest]: E =
		mockEntity[E]()

	protected def mockEntity[E <: Entity: Manifest](otherEntitySources: T forSome { type T <: Entity }*): E = {
		var mockEntity = StatementMocks.mockEntity(erasureOf[E])
		if (otherEntitySources.toSet.contains(mockEntity))
			mockEntity = StatementMocks.mockEntityWithoutCache(erasureOf[E])
		From.createAndRegisterEntitySource(erasureOf[E], mockEntity);
		mockEntity
	}

	def where(value: Criteria) =
		Where(value)

}

abstract class Statement(from: From, where: Where) {
	override def toString = from + " => where" + where
}

