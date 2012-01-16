package net.fwbrasil.activate.entity

import java.util.IdentityHashMap

object sizeOf {

	def apply[V <% EntityValue[V]](value: V) =
		sizeOf(value, new IdentityHashMap())

	private[this] def sizeOf[V](value: EntityValue[V], visited: IdentityHashMap[Any, Any]): Long = {
		if (visited.containsKey(value.value))
			0
		else {
			visited.put(value.value, null)
			value match {
				case v: IntEntityValue =>
					760
				case v: BooleanEntityValue =>
					200
				case v: CharEntityValue =>
					184
				case v: StringEntityValue =>
					64 + 8 * ((v.value.size - 2) % 4)
				case v: FloatEntityValue =>
					184
				case v: DoubleEntityValue =>
					184
				case v: BigDecimalEntityValue =>
					10008
				case v: DateEntityValue =>
					1944
				case v: CalendarEntityValue =>
					616
				case v: ByteArrayEntityValue =>
					16 + 8 * ((v.value.size - 2) % 4)
				case v: EntityValue[_] =>
					4 + sizeOf(v, visited)
			}
		}
	}
}