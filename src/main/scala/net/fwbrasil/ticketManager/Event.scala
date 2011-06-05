package net.fwbrasil.ticketManager

import tools.scalap.scalax.rules.scalasig._
import scala.runtime._
import java.util.concurrent.atomic.AtomicLong
import net.fwbrasil.ticketManager.TicketManagerContext._
import java.util.Date
import net.fwbrasil.activate.util.RichList._
import scala.util.Random.nextInt
import net.fwbrasil.radon.util.ThreadUtil._

object BarCodeGenerator {
	val counter = new AtomicLong(0)
	def generate = counter.incrementAndGet.toString
}

class Seat(val sitting: Var[Customer], val room: Var[Room]) extends Entity {
	def isFree = sitting.get == None
	def sit(customer: Customer) = {
		if (!isFree)
			throw new IllegalStateException("Tryng to sit in a used seat.")
		
		sitting := customer
	}
}

case class Room(name: Var[String],  numberOfSeats: Int)
	extends Entity {
	for (i <- 0 until numberOfSeats)
		new Seat(None, this)
	def seats = allWhere[Seat](_.room :== this)
	def freeSeats = allWhere[Seat](_.room :== this, _.sitting isNone)
}

case class Customer(name: Var[String])
	extends Entity {

	def enterTicketBoothQueue = {
		val ticketBoothOption = TicketBooth.withPlaceInQueue
		if (ticketBoothOption != None)
			ticketBoothOption.get.enterQueue(this)
	}

	def buyTickets(ticketBooth: TicketBooth) =
		for (i <- 0 to nextInt(2)) {
			val eventOption = ticketBooth.getAvailableEvents.randomElementOption
			if(eventOption != None) {
				val ticketOption = ticketBooth.getAvailableTickets(eventOption.get).randomElementOption
				if(ticketOption != None) {
					ticketBooth.buy(ticketOption.get, this)
				}
			}
		}
}

case class Ticket(event: Var[Event],
	seat: Var[Seat],
	barCode: Var[String],
	soldFor: Var[Customer])
	extends Entity

case class Event(eventDate: Var[Date],
	room: Var[Room],
	ended: Var[Boolean])
	extends Entity {

	for (seat <- room.seats)
		Ticket(this, seat, BarCodeGenerator.generate, None)

	def availableTickets =
		allWhere[Ticket](_.event :== this, _.soldFor isNone)

}

object Event {
	def getAvailableEvents =
		allWhere[Event](_.ended :== false).filter(_.availableTickets.nonEmpty)
}

case class TicketBooth(queue: Var[TicketBoothQueue] = TicketBoothQueue(), open: Var[Boolean] = None) extends Entity {
	def enterQueue(customer: Customer) =
		queue.enter(customer)
	def getAvailableEvents =
		Event.getAvailableEvents
	def getAvailableTickets(event: Event) =
		event.availableTickets
	def buy(ticket: Ticket, customer: Customer) =
		ticket.soldFor := customer
	def start = {
		transactional {
			open := true
		}
		transactionalWhile(open) {
			val customerOption = queue.next
			if (customerOption != None) {
				customerOption.get.buyTickets(this)
			}
			Thread.sleep(1000)
		}
	}
	def stop =
		open := false
}

object TicketBooth {
	def withPlaceInQueue =
		all[TicketBooth].filter(!_.queue.isFull).firstOption
}

case class TicketBoothQueue(queueSize: Var[Int] = 40) extends Entity {
	def next = {
		val ticketBoothQueueCustomerOption = customers.firstOption
		val ret = ticketBoothQueueCustomerOption.map(!_.customer)			
		if(ticketBoothQueueCustomerOption != None)
			ticketBoothQueueCustomerOption.get.delete
		ret
	}
	def customers =
		allWhere[TicketBoothQueueCustomer](_.ticketBoothQueue :== this)
			.sortComparable(_.position.get.get)
	def enter(customer: Customer) =
		if (isFull)
			throw new IllegalStateException("Full queue")
		else
			TicketBoothQueueCustomer(this, customer, customers.size)
	def isFull =
		customers.size == !queueSize
}

case class TicketBoothQueueCustomer(ticketBoothQueue: Var[TicketBoothQueue], customer: Var[Customer], position: Var[Int]) extends Entity

object Run {

	def main(args: Array[String]) {

		val a: Boolean = true
		
		transactional {
			Event(new Date, Room("room", 2000), None)
		}

		val ticketBooth = transactional {
			TicketBooth()
		}
		runInNewThread {
			ticketBooth.start
		}

		val customer =
			transactional(Customer("teste"))

		transactional {
			customer.enterTicketBoothQueue
		}
		
		transactional {
			ticketBooth.stop
		}
		
	}
}