package klite.sample.klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.*
import ch.tutteli.atrium.api.verbs.expect
import klite.d
import klite.jdbc.*
import klite.sample.TempTableDBTest
import klite.toValues
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigDecimal.ZERO
import java.util.*
import java.util.UUID.randomUUID

open class JdbcExtensionsTest: TempTableDBTest() {
  @Test fun `insert & query`() {
    val id = randomUUID()
    db.insert(table, mapOf("id" to id, "hello" to "Hello", "world" to 42.d))

    val id2 = randomUUID()
    db.insert(table, mapOf("id" to id2, "hello" to "Hello2"))

    db.insertBatch(table, (3..10).asSequence().map { mapOf("id" to randomUUID(), "hello" to "Hello$it", "world" to it) })

    expect(db.select(table, id) { getUuid() }).toEqual(id)

    expect(db.select(table, "hello" to "Hello") { getUuid() }).toContain(id)
    expect(db.select<SomeData>(table)).toContain(SomeData("Hello", 42, id))
    expect(db.select(table, "hello" to "World") { getUuid() }).toBeEmpty()

    expect(db.select(table, "hello" gt "Hello") { getUuid() }).toContain(id2)
    expect(db.select(table, "hello" to listOf("Hello", "Hello2")) { getUuid() }).toContain(id, id2)
    expect(db.select(table, "hello" to listOf("Hello", "Hello2"), suffix = "order by hello desc") { getUuid() }).toContainExactly(id2, id)
    expect(db.select(table, "hello" to listOf("Hello", "Hello2"), suffix = "limit 1") { getUuid() }).toContainExactly(id)
    expect(db.select(table, "hello" to NotIn("Hello2")) { getUuid() }).toContain(id)
    expect(db.select(table, "world" to 42.d) { getUuid() }).toContainExactly(id)

    expect(db.select(table, emptyList(), "where world is null") { getUuid() }).toContainExactly(id2)
    expect(db.select("$table a join $table b on a.id = b.id", emptyList()) { getUuid("b.id") }).toContain(id, id2)
  }

  @Test fun generatedKey() {
    val generatedKey = GeneratedKey<Int>()
    db.insert(table, mapOf("id" to randomUUID(), "hello" to "Hello", "gen" to generatedKey))
    expect(generatedKey.value).toBeGreaterThanOrEqualTo(1)
  }

  @Test fun `generatedKeys batch`() {
    val values = (1..2).map { mapOf("id" to randomUUID(), "hello" to "Hello", "gen" to GeneratedKey<Int>()) }
    db.insertBatch(table, values.asSequence())
    values.forEach {
      expect((it["gen"] as GeneratedKey<Int>).value).toBeGreaterThanOrEqualTo(1)
    }
  }

  @Test fun `generatedKey with type`() {
    val generatedKey = GeneratedKey(BigDecimal::class)
    db.insert(table, mapOf("id" to randomUUID(), "hello" to "BD", "gen" to generatedKey))
    expect(generatedKey.value).toBeGreaterThan(ZERO)
  }

  @Test fun upsert() {
    val data = SomeData("World", 37)
    expect(db.upsert(table, data.toValues())).toEqual(1)
    expect(db.upsert(table, data.toValues())).toEqual(1)
    expect(db.upsert(table, data.toValues(), where = listOf(SomeData::world neq 37))).toEqual(0)

    var loaded: SomeData = db.select(table, data.id) { create() }
    expect(loaded).toEqual(data)

    expect(db.upsert(table, data.toValues(SomeData::world to 38), skipUpdateFields = setOf(SomeData::world.name))).toEqual(1)
    loaded = db.select(table, data.id) { create() }
    expect(loaded).toEqual(data)
  }

  @Test fun `update and delete`() {
    val data = SomeData("World", 37)
    db.insert(table, data.toValues())

    db.update(table, mapOf("world" to 39), "id" to data.id)
    expect(db.select(table, data.id) { create<SomeData>() }).toEqual(data.copy(world = 39))

    db.delete(table, listOf("world" to 39))
    expect { db.select(table, data.id) { } }.toThrow<NoSuchElementException>()
  }

  data class SomeData(val hello: String, val world: Int?, val id: UUID = randomUUID())
}
