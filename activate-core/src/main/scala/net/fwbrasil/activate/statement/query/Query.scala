package net.fwbrasil.activate.statement.query

import net.fwbrasil.activate.util.ManifestUtil._
import net.fwbrasil.activate.cache.live.LiveCache
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.statement.From.runAndClearFrom
import net.fwbrasil.activate.statement.Criteria
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.statement.Statement
import net.fwbrasil.activate.statement.StatementContext
import net.fwbrasil.activate.statement.StatementSelectValue
import net.fwbrasil.activate.statement.Where
import net.fwbrasil.activate.util.ManifestUtil.erasureOf
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.statement.StatementMocks
import scala.collection.mutable.{ Map => MutableMap }
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import scala.collection.mutable.Stack
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.util.CollectionUtil
import scala.concurrent.Future
import net.fwbrasil.radon.transaction.TransactionalExecutionContext

class Query[S](override val from: From, override val where: Where, val select: Select) extends Statement(from, where) with Product {

    def execute: List[S] =
        context.executeQuery(this)

    def executeAsync(implicit texctx: TransactionalExecutionContext): Future[List[S]] =
        context.executeQueryAsync(this, texctx)

    def context =
        (for (src <- from.entitySources)
            yield ActivateContext.contextFor(src.entityClass))
            .toSet.onlyOne("All query entities sources must be from the same context.")

    private[activate] def orderByClause: Option[OrderBy] = None

    def productElement(n: Int): Any =
        n match {
            case 0 => from
            case 1 => where
            case 2 => select
        }
    def productArity: Int = 3
    def canEqual(that: Any): Boolean =
        that.getClass == classOf[Query[S]]
    override def equals(that: Any): Boolean =
        canEqual(that) &&
            (that match {
                case v: Query[S] =>
                    v.from == from &&
                        v.where == where &&
                        v.select == select
                case other =>
                    false
            })

    override def toString = from + " => where" + where + " select " + select + ""
}

case class Select(values: StatementSelectValue[_]*) {
    override def toString = "(" + values.mkString(", ") + ")"
}