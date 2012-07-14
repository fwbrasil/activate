package net.fwbrasil.activate.migration

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.ActivateTestContext
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.entity.Entity
import java.util.NoSuchElementException
import net.fwbrasil.activate.ActivateTestContext

@RunWith(classOf[JUnitRunner])
class MigrationSpecs extends MigrationTest {

	"Migration" should {

		"createTableForEntity EntityWithoutAttribute" in
			migrationTest(
				new TestMigration()(_) {
					import context._
					def up = {
						createTableForEntity[EntityWithoutAttribute]
					}
					override def validateUp = {
						val entity = transactional {
							new EntityWithoutAttribute
						}
						transactional {
							entity.delete
						}
					}
					override def validateDown = {
						if (context.storage.hasStaticScheme) {
							transactional {
								new EntityWithoutAttribute
							} must throwA[Exception]
							transactional {
								all[EntityWithoutAttribute]
							} must throwA[Exception]
						}
					}
				})

		"table EntityWithoutAttribute createTable" in
			migrationTest(
				new TestMigration()(_) {
					import context._
					def up = {
						table[EntityWithoutAttribute]
							.createTable()
					}
					override def validateUp = {
						val entity = transactional {
							new EntityWithoutAttribute
						}
						transactional {
							entity.delete
						}
					}
					override def validateDown = {
						if (context.storage.hasStaticScheme) {
							transactional {
								new EntityWithoutAttribute
							} must throwA[Exception]
							transactional {
								all[EntityWithoutAttribute]
							} must throwA[Exception]
						}
					}
				})
	}
}
