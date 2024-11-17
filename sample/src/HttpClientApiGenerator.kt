import klite.annotations.ApiContextPath
import klite.annotations.GET
import klite.annotations.PathParam
import klite.json.JsonBody
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.reflect.KFunction
import kotlin.reflect.full.createType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.kotlinFunction

class HttpClientApiGenerator(private val baseUrl: String) : InvocationHandler {

  private val httpClient: HttpClient = HttpClient.newHttpClient()

  override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any {
    val apiContextPath = method.declaringClass.getAnnotation(ApiContextPath::class.java)
      ?: throw IllegalArgumentException("${ApiContextPath::class} is mandatory in the interface definition")

    val getAnnotation = method.getAnnotation(GET::class.java)
      ?: throw UnsupportedOperationException("Only GET methods are supported")

    var path = apiContextPath.value + getAnnotation.value
    val pathParams = extractPathParams(method.kotlinFunction!!, args)

    pathParams.forEach { (key, value) ->
      path = path.replace(":$key", value)
    }

    val request = HttpRequest.newBuilder()
      .uri(URI.create(baseUrl + path))
      .GET()
      .build()

    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())

    println(response.statusCode())

    return JsonBody().parse(response.body(), method.returnType.kotlin.createType())
  }

  private fun extractPathParams(method: KFunction<*>, args: Array<out Any>?): Map<String, String> {
    if (args == null) return emptyMap()

    return method.parameters.subList(1, method.parameters.size).mapIndexedNotNull { index, parameter ->
      parameter.findAnnotation<PathParam>()?.let {
        parameter.name!! to args[index].toString()
      }
    }.toMap()
  }

  companion object {
    inline fun <reified T : Any> create(baseUrl: String): T {
      return Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
        HttpClientApiGenerator(baseUrl)
      ) as T
    }
  }
}
