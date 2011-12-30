package net.fwbrasil.activate.storage.map.cassandra

import java.io.UnsupportedEncodingException;

import java.util.Date;

import org.apache.cassandra.thrift.Cassandra;
import org.apache.cassandra.thrift.Column;
import org.apache.cassandra.thrift.ColumnOrSuperColumn;
import org.apache.cassandra.thrift.ColumnParent;
import org.apache.cassandra.thrift.ColumnPath;
import org.apache.cassandra.thrift.ConsistencyLevel;
import org.apache.cassandra.thrift.InvalidRequestException;
import org.apache.cassandra.thrift.NotFoundException;
import org.apache.cassandra.thrift.SlicePredicate;
import org.apache.cassandra.thrift.SliceRange;
import org.apache.cassandra.thrift.TimedOutException;
import org.apache.cassandra.thrift.UnavailableException;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import net.fwbrasil.activate.serialization.Serializator
import net.fwbrasil.activate.storage.map.MapStorage
import net.fwbrasil.activate.storage.map.PropertyAssignment
import net.fwbrasil.activate.entity.Entity
import org.objenesis.ObjenesisHelper

trait CassandraStorage extends MapStorage {
	
	val serializator: Serializator
	val cassandraPort = 9160
	val cassandraHost: String
	val keyspace: String
	
	lazy val tr = {
		val tr = new TSocket(cassandraHost, cassandraPort)
		tr.open
		tr
	}
    lazy val proto = new TBinaryProtocol(tr);
    def client = new Cassandra.Client(proto);
    
    override def toStorage(assignments: Set[PropertyAssignment[Entity, Any]]): Unit = {
    	this.synchronized{
	    	for(PropertyAssignment(path, value) <- assignments) {
				val colFamily = path.entityClass.getCanonicalName
		    	val colPath = new ColumnPath(colFamily);
		        colPath.setColumn(serializator.toSerialized(path.propertyName));
		        val timestamp = System.currentTimeMillis();
		        val serialized = serializator.toSerialized(value)
//		        client.insert(keyspace, path.entityId, colPath, serialized, timestamp, ConsistencyLevel.ONE);
	    	}
    	}
    }
}