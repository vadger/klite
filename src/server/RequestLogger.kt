package server

import com.sun.net.httpserver.HttpExchange
import java.util.logging.Logger

class RequestLogger: AsyncFilter() {
  private val log = Logger.getLogger(javaClass.name)

  override fun before(exchange: HttpExchange) {
    exchange["start"] = System.nanoTime()
  }

  override fun after(exchange: HttpExchange) {
    exchange.apply {
      val ms = (System.nanoTime() - get("start") as Long) / 1000_000
      log.info("$requestMethod $requestPath: $responseCode in $ms ms")
    }
  }
}
