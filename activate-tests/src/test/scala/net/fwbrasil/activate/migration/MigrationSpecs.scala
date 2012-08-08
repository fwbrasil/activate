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

		"CreateTable" in {

			"createTableForAllEntities" in
				migrationTest(
					new TestMigration()(_) {
						import context._
						def up = {
							createTableForAllEntities
						}
						override def validateDown = {
							validateSchemaError(new EntityWithoutAttribute)
							validateSchemaError(all[EntityWithoutAttribute])
							validateSchemaError(newFullActivateTestEntity)
							validateSchemaError(all[ActivateTestEntity])
						}
					})

			"createTableForEntity" in
				migrationTest(
					new TestMigration()(_) {
						import context._
						def up = {
							createTableForEntity[EntityWithoutAttribute]
							createTableForEntity[ActivateTestEntity]
						}
						override def validateDown = {
							validateSchemaError(new EntityWithoutAttribute)
							validateSchemaError(all[EntityWithoutAttribute])
							validateSchemaError(newEmptyActivateTestEntity)
							validateSchemaError(all[ActivateTestEntity])
						}
					})

			"Table.createTable[Entity]" in
				migrationTest(
					new TestMigration()(_) {
						import context._
						def up = {
							table[EntityWithoutAttribute].createTable()
							table[TraitAttribute1].createTable(
								_.column[String]("attribute"),
								_.column[String]("dummy"))
						}
						override def validateUp = {
							transactional(new EntityWithoutAttribute)
							transactional(all[EntityWithoutAttribute])
							transactional(new TraitAttribute1("a"))
							transactional(all[TraitAttribute1])
						}
						override def validateDown = {
							validateSchemaError(new EntityWithoutAttribute)
							validateSchemaError(all[EntityWithoutAttribute])
							validateSchemaError(new TraitAttribute1("a"))
							validateSchemaError(all[TraitAttribute1])
						}
					})

			"Table.createTable(name: String)" in
				migrationTest(
					new TestMigration()(_) {
						import context._
						def up = {
							table("EntityWithoutAttribute").createTable()
							table("TraitAttribute1").createTable(
								_.column[String]("attribute"),
								_.column[String]("dummy"))
						}
						override def validateUp = {
							transactional(new EntityWithoutAttribute)
							transactional(all[EntityWithoutAttribute])
							transactional(new TraitAttribute1("a"))
							transactional(all[TraitAttribute1])
						}
						override def validateDown = {
							validateSchemaError(new EntityWithoutAttribute)
							validateSchemaError(all[EntityWithoutAttribute])
							validateSchemaError(new TraitAttribute1("a"))
							validateSchemaError(all[TraitAttribute1])
						}
					})

		}

		"RenameTable" in {

			"Table.renameTable" in
				migrationTest(
					new TestMigration()(_) {
						import context._
						def up = {
							createTableForEntity[TraitAttribute1]
						}
					},
					new TestMigration()(_) {
						import context._
						def up = {
							table[TraitAttribute1].renameTable("renamed_table")
						}
						override def validateUp = {
							validateSchemaError(new TraitAttribute1("a"))
							validateSchemaError(all[TraitAttribute1])
						}
						override def validateDown = {
							transactional(new TraitAttribute1("a"))
							transactional(all[TraitAttribute1])
						}
					})

		}

		"RemoveTable" in {

			"removeAllEntitiesTables" in {

			}

			"Table.removeTable" in {

			}

		}

		"AddColumn" in {

			"createInexistentColumnsForEntity" in {

			}

			"createInexistentColumnsForAllEntities" in {

			}

			"Table.addColumns" in {

			}

		}

		"RenameColumn" in {

			"Table.renameColumn" in {

			}
		}

		"RemoveColumn" in {
			"Table.removeColumns" in {

			}

		}

		"AddIndex" in {

			"Table.addIndexes" in {

			}

		}

		"RemoveIndex" in {

			"Table.removeIndexes" in {

			}
		}

		"AddReference" in {
			"createReferencesForAllEntities" in {

			}

			"createReferencesForEntity" in {

			}
			"Table.addReferences" in {

			}

		}

		"RemoveReference" in {
			"removeReferencesForAllEntities" in {

			}

			"Table.removeReferences" in {

			}
		}

		"CustomScript" in {

		}

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
