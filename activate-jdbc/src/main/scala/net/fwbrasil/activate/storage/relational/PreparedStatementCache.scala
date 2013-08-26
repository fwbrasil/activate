package net.fwbrasil.activate.storage.relational

import java.sql.Connection
import scala.collection.mutable.HashMap
import java.sql.PreparedStatement
import scala.collection.mutable.Stack
import java.sql.ResultSet
import net.fwbrasil.activate.util.IdentityHashMap
import com.jolbox.bonecp.ConnectionHandle
import scala.collection.concurrent.TrieMap

class PreparedStatementCache {

    private val cache = new TrieMap[Connection, TrieMap[String, Stack[PreparedStatement]]]()

    def clear =
        cache.clear

    def acquireFor(connection: Connection, statement: String, readOnly: Boolean) =
        acquireFrom(cacheFor(connection), statement, readOnly).getOrElse {
            if (!readOnly)
                connection.prepareStatement(statement)
            else
                connection.prepareStatement(statement, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
        }

    def release(connection: Connection, statement: String, ps: PreparedStatement) = {
        val stack = stackFor(cacheFor(connection), statement)
        stack.synchronized {
            stack.push(ps)
        }
    }

    private def realConnection(connection: Connection) =
        connection match {
            case conn: ConnectionHandle =>
                conn.getInternalConnection
            case conn =>
                conn
        }

    private def acquireFrom(
        cache: TrieMap[String, Stack[PreparedStatement]],
        statement: String,
        readOnly: Boolean): Option[PreparedStatement] =
        aquireFrom(stackFor(cache, statement), readOnly)

    private def aquireFrom(stack: Stack[PreparedStatement], readOnly: Boolean): Option[PreparedStatement] =
        stack.synchronized {
            if (stack.isEmpty)
                None
            else
                Some(stack.pop)
        }

    private def cacheFor(connection: Connection) = {
        val realConnection = this.realConnection(connection)
        cache.getOrElseUpdate(realConnection, new TrieMap[String, Stack[PreparedStatement]])
    }

    private def stackFor(cache: TrieMap[String, Stack[PreparedStatement]], statement: String) =
        cache.getOrElseUpdate(statement, Stack())

}