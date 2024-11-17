import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpContext
import com.sun.net.httpserver.HttpPrincipal
import klite.*
import klite.annotations.ApiContextPath
import klite.annotations.GET
import klite.annotations.Path
import klite.annotations.PathParam
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.URI

@ApiContextPath("/some-api")
interface SomeApi {

  @GET("/say-hello/:name") fun sayHello(@PathParam name: String): Greeting
}

class SomeApiImpl : SomeApi {
  override fun sayHello(name: String): Greeting {
    return Greeting(message = "Hello, $name")
  }

}

data class Greeting(val message: String)

//class DummyOriginalHttpExchange : OriginalHttpExchange() {
//  override fun close() {
//    throw RuntimeException("Dummy implementation")
//  }
//
//  override fun getRequestURI(): URI {
//    throw RuntimeException("Dummy implementation")
//  }
//
//  override fun getRequestMethod(): String {
//    throw RuntimeException("Dummy implementation")
//  }
//
//  override fun getRequestHeaders(): Headers {
//    throw RuntimeException("Dummy implementation")
//  }
//
//  override fun getResponseHeaders(): Headers {
//    throw RuntimeException("Dummy implementation")
//  }
//
//  override fun getHttpContext(): HttpContext {
//    throw RuntimeException("Dummy implementation")
//  }
//
//  override fun getRequestBody(): InputStream {
//    throw RuntimeException("Dummy implementation")
//  }
//
//  override fun getResponseBody(): OutputStream {
//    throw RuntimeException("Dummy implementation")
//  }
//
//  override fun sendResponseHeaders(rCode: Int, responseLength: Long) {
//    throw RuntimeException("Dummy implementation")
//  }
//
//  override fun getRemoteAddress(): InetSocketAddress {
//    throw RuntimeException("Dummy implementation")
//  }
//
//  override fun getResponseCode(): Int {
//    throw RuntimeException("Dummy implementation")
//  }
//
//  override fun getLocalAddress(): InetSocketAddress {
//    throw RuntimeException("Dummy implementation")
//  }
//
//  override fun getProtocol(): String {
//    throw RuntimeException("Dummy implementation")
//  }
//
//  override fun getAttribute(name: String?): Any {
//    throw RuntimeException("Dummy implementation")
//  }
//
//  override fun setAttribute(name: String?, value: Any?) {
//    throw RuntimeException("Dummy implementation")
//  }
//
//  override fun setStreams(i: InputStream?, o: OutputStream?) {
//    throw RuntimeException("Dummy implementation")
//  }
//
//  override fun getPrincipal(): HttpPrincipal {
//    throw RuntimeException("Dummy implementation")
//  }
//}
//
//class DummyRouterConfig:
//  RouterConfig(emptyList(), emptyList(), emptyList()) {
//  override val registry: Registry
//    get() = SimpleRegistry()
//  override val pathParamRegexer: PathParamRegexer
//    get() = PathParamRegexer()
//}
//
//object ClientInvocationExchange
//  : HttpExchange(DummyOriginalHttpExchange(), DummyRouterConfig(), null, "")
