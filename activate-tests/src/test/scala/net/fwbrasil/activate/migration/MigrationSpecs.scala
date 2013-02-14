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
import net.fwbrasil.activate.polyglotContext

@RunWith(classOf[JUnitRunner])
class MigrationSpecs extends MigrationTest {

    override def contexts = super.contexts.filter(_ != polyglotContext)

    "Migration" should {

        "CreateTable" in {

            "createTableForAllEntities" in
                migrationTest(
                    new TestMigration()(_) {
                        import context._
                        def up = {
                            createTableForAllEntities
                        }
                        override def down = {}
                    },
                    new TestMigration()(_) {
                        import context._
                        def up =
                            createTableForAllEntities.ifNotExists
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
                        override def down = {}
                    },
                    new TestMigration()(_) {
                        import context._
                        def up = {
                            createTableForEntity[EntityWithoutAttribute].ifNotExists
                            createTableForEntity[ActivateTestEntity].ifNotExists
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
                        override def down = {}
                        override def validateUp = {
                            transactional(new EntityWithoutAttribute)
                            transactional(all[EntityWithoutAttribute])
                            transactional(new TraitAttribute1("a"))
                            transactional(all[TraitAttribute1])
                        }
                    },
                    new TestMigration()(_) {
                        import context._
                        def up = {
                            table[EntityWithoutAttribute].createTable().ifNotExists
                            table[TraitAttribute1].createTable().ifNotExists
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
                testRenameTable(migration => {
                    import migration.context._
                    migration.table[TraitAttribute1].renameTable("renamed_table")
                })

            "Table.renameTable.ifExists" in
                testRenameTable(migration => {
                    import migration.context._
                    migration.table[TraitAttribute1].renameTable("renamed_table").ifExists
                })

            def testRenameTable(action: (TestMigration) => Unit) =
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
                            action(this)
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
        //
        "RemoveTable" in {

            "removeAllEntitiesTables" in {
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
                            removeAllEntitiesTables
                                .ifExists
                        }
                        override def validateUp = {
                            validateSchemaError(new TraitAttribute1("a"))
                            validateSchemaError(all[TraitAttribute1])
                        }
                    }) must throwA[CannotRevertMigration]
            }

            "Table.removeTable" in {
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
                            table[TraitAttribute1]
                                .removeTable
                            table[TraitAttribute1]
                                .removeTable
                                .ifExists
                        }
                        override def validateUp = {
                            validateSchemaError(new TraitAttribute1("a"))
                            validateSchemaError(all[TraitAttribute1])
                        }
                    }) must throwA[CannotRevertMigration]
            }

        }

        "AddColumn" in {

            "createInexistentColumnsForEntity" in
                migrationTest(
                    new TestMigration()(_) {
                        import context._
                        def up = {
                            table[TraitAttribute1].createTable(_.column[String]("dummy"))
                        }
                        override def validateUp = {
                            validateSchemaError(new TraitAttribute1("a"))
                        }
                    },
                    new TestMigration()(_) {
                        import context._
                        def up = {
                            createInexistentColumnsForEntity[TraitAttribute1]
                        }
                        override def validateUp = {
                            transactional(new TraitAttribute1("a"))
                            transactional(all[TraitAttribute1])
                        }
                        override def validateDown = {
                            validateSchemaError(new TraitAttribute1("a"))
                        }
                    })

            "createInexistentColumnsForAllEntities" in
                migrationTest(
                    new TestMigration()(_) {
                        import context._
                        def up = {
                            table[TraitAttribute1]
                                .createTable(_.column[String]("dummy"))
                                .ifNotExists

                            createTableForAllEntities
                                .ifNotExists
                        }
                        override def validateUp = {
                            validateSchemaError(new TraitAttribute1("a"))
                        }
                    },
                    new TestMigration()(_) {
                        import context._
                        def up = {
                            createInexistentColumnsForAllEntities
                        }
                        override def validateUp = {
                            transactional(new TraitAttribute1("a"))
                            transactional(all[TraitAttribute1])
                        }
                        override def validateDown = {
                            validateSchemaError(new TraitAttribute1("a"))
                        }
                    })

            "Table.addColumns" in
                migrationTest(
                    new TestMigration()(_) {
                        import context._
                        def up = {
                            table[TraitAttribute1].createTable(_.column[String]("dummy"))
                        }
                        override def validateUp = {
                            validateSchemaError(new TraitAttribute1("a"))
                        }
                    },
                    new TestMigration()(_) {
                        import context._
                        def up = {
                            table[TraitAttribute1].addColumn(_.column[String]("attribute"))
                        }
                        override def validateUp = {
                            transactional(new TraitAttribute1("a"))
                            transactional(all[TraitAttribute1])
                        }
                        override def validateDown = {
                            validateSchemaError(new TraitAttribute1("a"))
                        }
                    })

        }

        "RenameColumn" in {

            "Table.renameColumn" in
                testRenameColumn { migration =>
                    import migration.context._
                    migration.table[TraitAttribute1].renameColumn("attribute", _.column[String]("attribute_renamed"))
                }

            "Table.renameColumn.ifExists" in
                testRenameColumn { migration =>
                    import migration.context._
                    migration.table[TraitAttribute1].renameColumn("attribute", _.column[String]("attribute_renamed")).ifExists
                }

            def testRenameColumn(action: (TestMigration) => Unit) =
                migrationTest(
                    new TestMigration()(_) {
                        import context._
                        def up = {
                            createTableForEntity[TraitAttribute1]
                        }
                        override def validateUp = {
                            transactional(new TraitAttribute1("a"))
                        }
                    },
                    new TestMigration()(_) {
                        import context._
                        def up = {
                            action(this)
                        }
                        override def validateUp = {
                            validateSchemaError(new TraitAttribute1("a"))
                        }
                        override def validateDown = {
                            transactional(new TraitAttribute1("a"))
                        }
                    })
        }

        "RemoveColumn" in {
            "Table.removeColumns" in {
                migrationTest(
                    new TestMigration()(_) {
                        import context._
                        def up = {
                            createTableForEntity[TraitAttribute1]
                        }
                        override def validateUp = {
                            transactional(new TraitAttribute1("a"))
                        }
                    },
                    new TestMigration()(_) {
                        import context._
                        def up = {
                            table[TraitAttribute1].removeColumn("attribute")
                            table[TraitAttribute1].removeColumn("attribute").ifExists
                        }
                        override def validateUp = {
                            validateSchemaError(new TraitAttribute1("a"))
                        }
                    }) must throwA[CannotRevertMigration]
            }

        }

        "AddIndex" in {

            "Table.addIndexes" in
                migrationTest(
                    new TestMigration()(_) {
                        import context._
                        def up = {
                            createTableForEntity[TraitAttribute1]
                            table[TraitAttribute1].addIndex("attribute", "att_idx")
                            table[TraitAttribute1].addIndex("attribute", "att_idx").ifNotExists
                        }
                        override def down = {}
                        override def validateUp = {
                            transactional(new TraitAttribute1("a"))
                        }
                    },
                    new TestMigration()(_) {
                        import context._
                        def up =
                            table[TraitAttribute1].addIndex("attribute", "att_idx").ifNotExists
                    })

        }

        "RemoveIndex" in {

            "Table.removeIndexes" in {
                migrationTest(
                    new TestMigration()(_) {
                        import context._
                        def up = {
                            createTableForEntity[TraitAttribute1]
                            table[TraitAttribute1].addIndex("attribute", "att_idx")
                            table[TraitAttribute1].removeIndex("attribute", "att_idx")
                            table[TraitAttribute1].removeIndex("attribute", "att_idx").ifExists
                        }
                        override def down = {}
                        override def validateUp = {
                            transactional(new TraitAttribute1("a"))
                        }
                    },
                    new TestMigration()(_) {
                        import context._
                        def up =
                            table[TraitAttribute1].removeIndex("attribute", "att_idx").ifExists
                    })
            }
        }

        "AddReference" in {

            def test(f: (Migration) => Unit) =
                migrationTest(
                    new TestMigration()(_) {
                        import context._
                        def up = {
                            createTableForAllEntities
                        }
                    },
                    new TestMigration()(_) {
                        import context._
                        def up = {
                            f(this)
                        }
                        def prepare = transactional {
                            val entity = newEmptyActivateTestEntity
                            entity.bigStringValue = "notbig"
                            val refEntity = new EntityWithoutAttribute
                            entity.entityWithoutAttributeValue = refEntity
                            refEntity
                        }
                        override def validateUp = {
                            val entity = prepare
                            validateSchemaError(entity.delete)
                        }
                        override def validateDown = {
                            val entity = prepare
                            transactional(entity.delete)
                        }
                    })

            "createReferencesForAllEntities" in
                test(_.createReferencesForAllEntities)

            "createReferencesForAllEntities.ifNotExists" in
                test(_.createReferencesForAllEntities.ifNotExists)

            "createReferencesForEntity" in
                test(c => c.createReferencesForEntity[ActivateTestContext#ActivateTestEntity])

            "createReferencesForEntity.ifNotExists" in
                test(c => c.createReferencesForEntity[ActivateTestContext#ActivateTestEntity].ifNotExists)

            "Table.addReferences" in
                test(_.table[ActivateTestContext#ActivateTestEntity].addReference("entityWithoutAttributeValue", "EntityWithoutAttribute", "fk_ewa"))

            "Table.addReferences.ifNotExists" in
                test(_.table[ActivateTestContext#ActivateTestEntity].addReference("entityWithoutAttributeValue", "EntityWithoutAttribute", "fk_ewa").ifNotExists)

        }

        "RemoveReference" in {

            def test(f: (Migration) => Unit) =
                migrationTest(
                    new TestMigration()(_) {
                        import context._
                        def up = {
                            createTableForAllEntities
                            createReferencesForAllEntities
                        }
                    },
                    new TestMigration()(_) {
                        import context._
                        def up = {
                            f(this)
                        }
                        def prepare = transactional {
                            val entity = newEmptyActivateTestEntity
                            entity.bigStringValue = "notbig"
                            val refEntity = new EntityWithoutAttribute
                            entity.entityWithoutAttributeValue = refEntity
                            (entity, refEntity)
                        }
                        override def validateUp = {
                            val (entity, refEntity) = prepare
                            transactional(refEntity.delete)
                            transactional(entity.delete)
                        }
                        override def validateDown = {
                            val (entity, refEntity) = prepare
                            validateSchemaError(refEntity.delete)
                        }
                    })

            "removeReferencesForAllEntities" in
                test(_.removeReferencesForAllEntities)

            "Table.removeReferences" in
                test(_.table[ActivateTestContext#ActivateTestEntity].removeReference("entityWithoutAttributeValue", "EntityWithoutAttribute", "ActivateTestE_entityWithoutA"))
        }

        "CustomScript" in {

            "CannotRevert" in {
                migrationTest(
                    new TestMigration()(_) {
                        import context._
                        def up = {
                            customScript {

                            }
                        }
                    }) must throwA[CannotRevertMigration]
            }

            migrationTest(
                new TestMigration()(_) {
                    import context._
                    def up = {
                        createTableForAllEntities
                        customScript {
                            new TraitAttribute1("a")
                        }
                    }
                    override def down = {
                        removeAllEntitiesTables
                    }
                },
                new TestMigration()(_) {
                    import context._
                    def attributes =
                        transactional(all[TraitAttribute1].map(_.attribute).toSet)
                    def up = {
                        customScript {
                            update {
                                (e: TraitAttribute1) => where(e isNotNull) set (e.attribute := "b")
                            }
                        }
                    }
                    override def validateUp = {
                        attributes must beEqualTo(Set("b"))
                    }
                    override def down = {
                        customScript {
                            update {
                                (e: TraitAttribute1) => where(e isNotNull) set (e.attribute := "a")
                            }
                        }
                    }
                    override def validateDown = {
                        attributes must beEqualTo(Set("a"))
                    }
                })

        }

        "produce an error if there is a transactional" in {
            "up" in {
                migrationTest(
                    new TestMigration()(_) {
                        import context._
                        def up = {
                            transactional {

                            }
                        }
                    }) must throwA[IllegalStateException]
            }

            "up" in {
                migrationTest(
                    new TestMigration()(_) {
                        import context._
                        def up = {
                        }
                        override def down = {
                            transactional {

                            }
                        }
                    }) must throwA[IllegalStateException]
            }
        }

    }
}
