package net.fwbrasil.activate.storage.relational

import java.sql.Connection
import scala.collection.mutable.HashMap
import java.sql.PreparedStatement
import scala.collection.mutable.Stack
import java.sql.ResultSet
import net.fwbrasil.activate.util.IdentityHashMap
import scala.collection.concurrent.TrieMap
import com.google.common.collect.MapMaker
import net.fwbrasil.activate.util.ConcurrentCache
import com.zaxxer.hikari.proxy.ConnectionProxy

class PreparedStatementCache {

    private val cache = (new MapMaker).weakKeys.makeMap[Connection, TrieMap[String, ConcurrentCache[(PreparedStatement, List[String])]]]

    def clear = {
        import scala.collection.JavaConversions._
        cache.values.foreach(_.values.foreach(_.toList.foreach(_._1.close)))
        cache.clear
    }

    def acquireFor(connection: Connection, statement: QlStatement, readOnly: Boolean) =
        acquireFrom(cacheFor(connection), statement.statement).getOrElse {
            val stmt =
                if (!readOnly)
                    connection.prepareStatement(statement.indexedStatement)
                else
                    connection.prepareStatement(statement.indexedStatement, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
            (stmt, statement.columns)
        }

    def release(connection: Connection, statement: QlStatement, ps: PreparedStatement, columns: List[String]) =
        stackFor(cacheFor(connection), statement.statement).offer(ps, columns)

    private def realConnection(connection: Connection) =
        connection match {
            case conn: ConnectionProxy =>
                conn.unwrap(classOf[Connection])
            case conn =>
                conn
        }

    private def acquireFrom(
        cache: TrieMap[String, ConcurrentCache[(PreparedStatement, List[String])]],
        statement: String): Option[(PreparedStatement, List[String])] =
        aquireFrom(stackFor(cache, statement))

    private def aquireFrom(stack: ConcurrentCache[(PreparedStatement, List[String])]): Option[(PreparedStatement, List[String])] =
        Option(stack.poll).filter(!_._1.isClosed)

    private def cacheFor(connection: Connection) = {
        val key = realConnection(connection)
        var value = cache.get(key)
        if (value == null) {
            value = new TrieMap[String, ConcurrentCache[(PreparedStatement, List[String])]]
            cache.putIfAbsent(key, value)
        }
        value
    }

    private def stackFor(cache: TrieMap[String, ConcurrentCache[(PreparedStatement, List[String])]], statement: String) =
        if (!cache.contains(statement)) {
            val stack = new ConcurrentCache[(PreparedStatement, List[String])]("PreparedStatementCache", 10)
            cache.put(statement, stack)
            stack
        } else
            cache(statement)

}