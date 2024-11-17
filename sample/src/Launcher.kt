import klite.*
import klite.annotations.annotated
import klite.jdbc.*
import klite.json.JsonBody
import klite.oauth.AuthRoutes
import klite.oauth.OAuthRoutes
import klite.oauth.OAuthUserProvider
import klite.openapi.openApi
import kotlinx.coroutines.delay
import users.UserRepository
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.nio.file.Path
import java.time.Duration.ofSeconds

fun main() {
  sampleServer().start()
}

fun sampleServer(port: Int = 8080) = Server(listen = InetSocketAddress(port)).apply {
  Config.useEnvFile()
  use<JsonBody>() // enables parsing/sending of application/json requests/responses, depending on the Accept header

  if (Config.isDev) startDevDB() // start docker-compose db automatically
  use(DBModule(PooledDataSource())) // configure a DataSource
  use<DBMigrator>() //  migrate the DB
  use<RequestTransactionHandler>() // runs each request in a transaction

  assets("/", AssetsHandler(Path.of("public"), useIndexForUnknownPaths = true))

  register(HttpClient.newBuilder().connectTimeout(ofSeconds(5)).build())

  before<AdminChecker>()
  after { ex, err -> ex.header("X-Error", err?.message ?: "none") }

  context("/hello") {
    get { "Hello World" }

    get("/delay") {
      delay(1000)
      "Waited for 1 sec"
    }

    get("/failure") { error("Failure") }

    get("/admin") @AdminOnly {
      "Only for admins"
    }

    get("/param/:param") {
      "Path: ${path("param")}, Query: $queryParams"
    }

    get("/proxy-call/:name") {
      HttpClientApiGenerator.create<SomeApi>("http://localhost:8080").sayHello(path("name")!!)
    }

    post("/post") {
      data class JsonRequest(val required: String, val hello: String = "World")
      body<JsonRequest>()
    }

    decorator { ex, h -> "<${h(ex)}>" }
    get("/decorated") { "!!!" }
  }

  context("/api") {
    useOnly<JsonBody>() // in case only json should be supported in this context
    before(CorsHandler()) // enable CORS for this context, so that Swagger-UI can access the API
    useHashCodeAsETag() // automatically send 304 NotModified if request generates the same response as before
    annotated<APIRoutes>() // read routes from an annotated class - such classes are easier to unit-test
    annotated<SSERoutes>("/sse") // Server-Side Events sample
    openApi()
  }

  context("/auth") {
    register<OAuthUserProvider>(UserRepository::class)
    annotated<AuthRoutes>()
    annotated<OAuthRoutes>()
  }

  api(SomeApiImpl::class) {
    useOnly<JsonBody>()
  }

//  context("/foo") {
//    annotated<SomeApiImpl>(usesInterface = true)
//  }
}
