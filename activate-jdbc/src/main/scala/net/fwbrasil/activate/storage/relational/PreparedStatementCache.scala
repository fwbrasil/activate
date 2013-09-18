package net.fwbrasil.activate.storage.relational

import java.sql.Connection
import scala.collection.mutable.HashMap
import java.sql.PreparedStatement
import scala.collection.mutable.Stack
import java.sql.ResultSet
import net.fwbrasil.activate.util.IdentityHashMap
import com.jolbox.bonecp.ConnectionHandle
import scala.collection.concurrent.TrieMap
import com.google.common.collect.MapMaker
import net.fwbrasil.activate.util.ConcurrentCache

class PreparedStatementCache {

    private val cache = (new MapMaker).weakKeys.makeMap[Connection, TrieMap[String, ConcurrentCache[PreparedStatement]]]

    def clear = {
        import scala.collection.JavaConversions._
        cache.values.foreach(_.values.foreach(_.toList.foreach(_.close)))
        cache.clear
    }

    def acquireFor(connection: Connection, statement: QlStatement, readOnly: Boolean) =
        acquireFrom(cacheFor(connection), statement.statement).getOrElse {
            if (!readOnly)
                connection.prepareStatement(statement.indexedStatement)
            else
                connection.prepareStatement(statement.indexedStatement, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
        }

    def release(connection: Connection, statement: QlStatement, ps: PreparedStatement) =
        stackFor(cacheFor(connection), statement.statement).offer(ps)

    private def realConnection(connection: Connection) =
        connection match {
            case conn: ConnectionHandle =>
                conn.getInternalConnection
            case conn =>
                conn
        }

    private def acquireFrom(
        cache: TrieMap[String, ConcurrentCache[PreparedStatement]],
        statement: String): Option[PreparedStatement] =
        aquireFrom(stackFor(cache, statement))

    private def aquireFrom(stack: ConcurrentCache[PreparedStatement]): Option[PreparedStatement] =
        Option(stack.poll)

    private def cacheFor(connection: Connection) = {
        val key = realConnection(connection)
        var value = cache.get(key)
        if (value == null) {
            value = new TrieMap[String, ConcurrentCache[PreparedStatement]]
            cache.putIfAbsent(key, value)
        }
        value
    }

    private def stackFor(cache: TrieMap[String, ConcurrentCache[PreparedStatement]], statement: String) =
        if (!cache.contains(statement)) {
            val stack = new ConcurrentCache[PreparedStatement]("PreparedStatementCache", 10)
            cache.put(statement, stack)
            stack
        } else
            cache(statement)

}