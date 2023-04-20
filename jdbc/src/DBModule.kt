package klite.jdbc

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import klite.*
import klite.StatusCode.Companion.Conflict
import javax.sql.DataSource

val Config.dbPoolMaxSize get() = optional("DB_POOL_SIZE")?.toInt() ?: ((optional("NUM_WORKERS")?.toInt() ?: 5) + (optional("JOB_WORKERS")?.toInt() ?: 5))

class DBModule(val dataSource: DataSource): Extension {
  constructor(configure: HikariConfig.() -> Unit = {}): this(HikariDataSource(HikariConfig().apply {
    poolName = "app-db"
    jdbcUrl = Config.required("DB_URL")
    username = Config.optional("DB_USER")
    password = Config.optional("DB_PASS")
    minimumIdle = 1
    maximumPoolSize = Config.dbPoolMaxSize
    configure()
    logger().info("Connecting to $jdbcUrl${username?.let { ", user: $username" }}")
  }))

  override fun install(server: Server) = server.run {
    registry.register<DataSource>(dataSource)
    errors.on(AlreadyExistsException::class, Conflict)
    onStop { (dataSource as? AutoCloseable)?.close() }
  }
}

/** Call this before to add support for Heroku-like DATABASE_URL env variable */
fun initHerokuDB(suffix: String = "?sslmode=require") {
  val url = Config.optional("DATABASE_URL") ?: return
  val m = "postgres://(?<user>.+?):(?<password>.+?)@(?<hostportdb>.*)".toRegex().matchEntire(url)?.groups ?: return
  Config["DB_URL"] = "jdbc:postgresql://${m["hostportdb"]!!.value}$suffix"
  Config["DB_USER"] = m["user"]!!.value
  Config["DB_PASS"] = m["password"]!!.value
}

/** Call this before to switch to less-privileged DB user after DB migration */
fun useAppDBUser(user: String = Config.optional("DB_APP_USER", "app"), password: String = Config["DB_APP_PASS"]) {
  Config["DB_USER"] = user
  Config["DB_PASS"] = password
}
