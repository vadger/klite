package klite.annotations

import klite.*
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import kotlin.annotation.AnnotationTarget.*
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KParameter.Kind.INSTANCE
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.superclasses
import kotlin.reflect.jvm.javaMethod

@Target(CLASS) annotation class Path(val value: String)
@Target(CLASS) annotation class ApiContextPath(val value: String)
@Target(FUNCTION) annotation class GET(val value: String = "")
@Target(FUNCTION) annotation class POST(val value: String = "")
@Target(FUNCTION) annotation class PUT(val value: String = "")
@Target(FUNCTION) annotation class PATCH(val value: String = "")
@Target(FUNCTION) annotation class DELETE(val value: String = "")
@Target(FUNCTION) annotation class OPTIONS(val value: String = "")

@Target(VALUE_PARAMETER) annotation class PathParam(val value: String = "")
@Target(VALUE_PARAMETER) annotation class QueryParam(val value: String = "")
@Target(VALUE_PARAMETER) annotation class BodyParam(val value: String = "")
@Target(VALUE_PARAMETER) annotation class HeaderParam(val value: String = "")
@Target(VALUE_PARAMETER) annotation class CookieParam(val value: String = "")
@Target(VALUE_PARAMETER) annotation class SessionParam(val value: String = "")
@Target(VALUE_PARAMETER) annotation class AttrParam(val value: String = "")

/**
 * Adds all annotated methods as routes, sorted by path (matching more specific paths first).
 * Routes can also implement Before/After interfaces.
 *
 * Use the @XXXParam annotations to bind specific types of params.
 * Non-annotated binding of well known classes is possible, like [HttpExchange] and [Session].
 * Non-annotated custom class is interpreted as the whole POST/PUT body, e.g. a data class deserialized from json.
 */
@Suppress("NAME_SHADOWING")
fun Router.annotated(path: String = "", routes: Any, annotations: List<Annotation> = emptyList(), usesInterface: Boolean = false) {
  val cls = routes::class
  val path = path + (cls.annotation<Path>(checkInterfaces = true)?.value ?: "")
  val classDecorators = mutableListOf<Decorator>()
  if (routes is Before) classDecorators += routes.toDecorator()
  if (routes is After) classDecorators += routes.toDecorator()
  cls.functions.asSequence().mapNotNull { f ->
    val interfaceFunction = if (usesInterface) f.interfaceFunction() else f
    val a = interfaceFunction?.kliteAnnotation ?: return@mapNotNull null
    val method = RequestMethod.valueOf(a.annotationClass.simpleName!!)
    val subPath = a.annotationClass.members.first().call(a) as String
    val handler = classDecorators.wrap(FunHandler(routes, f, interfaceFunction))
    subPath to Route(method, pathParamRegexer.from(path + subPath), annotations + f.annotations + cls.annotations, handler)
  }.sortedBy { it.first.replace(':', '~') }.forEach { add(it.second) }
}

fun KFunction<*>.interfaceFunction(): KFunction<*>? {
  return this.name.let { methodName ->
    this.javaMethod?.declaringClass?.kotlin?.superclasses
      ?.asSequence()
      ?.flatMap { it.functions.asSequence() }
      ?.find { it.name == methodName && it.parameters.size == this.parameters.size }
  }
}

fun Router.annotated(routes: Any) = annotated("", routes)
inline fun <reified T: Any> Router.annotated(path: String = "", annotations: List<Annotation> = emptyList(), usesInterface: Boolean = false) = annotated(path, require<T>(), annotations, usesInterface)

private val packageName = GET::class.java.packageName
private val KAnnotatedElement.kliteAnnotation get() = annotations.filter { it.annotationClass.java.packageName == packageName }
  .let { if (it.size > 1) error("$this cannot have multiple klite annotations: $it") else it.firstOrNull() }

class FunHandler(
  val instance: Any,
  val implementationFunction: KFunction<*>,
  interfaceFunction: KFunction<*> = implementationFunction
): Handler {
  val params = interfaceFunction.parameters.zip(implementationFunction.parameters)
    .map { Param(it.first, it.second) }

  override suspend fun invoke(e: HttpExchange): Any? = try {
    val args = params.associate { p -> p.implementationParam to p.valueFrom(e, instance) }.filter { !it.key.isOptional || it.value != null }
    implementationFunction.callSuspendBy(args)
  } catch (e: InvocationTargetException) {
    throw e.targetException
  }
}

class Param(interfaceParam: KParameter, val implementationParam: KParameter) {
  val p: KParameter = interfaceParam
  val cls = interfaceParam.type.classifier as KClass<*>
  val source: Annotation? = interfaceParam.kliteAnnotation
  val name: String = source?.value ?: p.name ?: ""

  fun valueFrom(e: HttpExchange, instance: Any) = try {
    if (implementationParam.kind == INSTANCE) instance
    else if (cls == HttpExchange::class) e
    else if (cls == Session::class) e.session
    else if (cls == InputStream::class) e.requestStream
    else {
      when (source) {
        is PathParam -> e.path(name)?.toType()
        is QueryParam -> e.query(name).let { if (it == null && p.type.classifier == Boolean::class && name in e.queryParams) true else it?.toType() }
        is HeaderParam -> e.header(name)?.toType()
        is CookieParam -> e.cookie(name)?.toType()
        is SessionParam -> e.session[name]?.toType()
        is AttrParam -> e.attr(name)
        is BodyParam -> e.body<Any?>(name)?.let { if (it is String) it.trimToNull()?.toType() else it }
        else -> e.body(p.type)
      }
    }
  } catch (e: Exception) {
    if (e is IllegalArgumentException || e.message?.contains(name) == true) throw e
    throw IllegalArgumentException("Cannot get $name: ${e.message}", e)
  }

  private val Annotation.value: String? get() = (javaClass.getMethod("value").invoke(this) as String).takeIf { it.isNotEmpty() }
  private fun String.toType() = Converter.from<Any>(this, p.type)
}

inline fun <reified T : Annotation> KFunction<*>.findInterfaceAnnotation(): T? {
  return this.name.let { methodName ->
    javaMethod?.declaringClass?.kotlin?.superclasses?.asSequence()?.flatMap { it.functions.asSequence() }
      ?.find { it.name == methodName }?.findAnnotation<T>()
  }
}

inline fun <reified T: Annotation> KClass<*>.annotation(checkInterfaces: Boolean = false): T? = java.getAnnotation(T::class.java)
  ?: if (checkInterfaces) java.interfaces.asSequence().mapNotNull { it.getAnnotation(T::class.java) }.firstOrNull() else null

inline fun <reified T: Annotation> KFunction<*>.annotation(checkSuperTypes: Boolean = false): T? = javaMethod!!.getAnnotation(T::class.java)
  ?: if (checkSuperTypes) findInterfaceAnnotation<T>() else null
