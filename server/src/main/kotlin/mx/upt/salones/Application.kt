package mx.upt.salones

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import mx.upt.salones.model.ErrorResponse

fun main() {
    embeddedServer(
        Netty,
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) { json() }
    install(CORS) {
        // Necesario para el cliente web. En producción, limitar al dominio real.
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Post)
    }
    install(StatusPages) {
        exception<Throwable> { call, e ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Error del servidor"))
        }
    }
    install(Authentication) {
        jwt("auth") {
            realm = "salones-upt"
            verifier(Jwt.verifier)
            validate { cred ->
                if (cred.payload.getClaim("id").asInt() != null) JWTPrincipal(cred.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Sesión no válida o expirada"))
            }
        }
    }

    DatabaseFactory.init()

    routing {
        get("/") { call.respond(mapOf("servicio" to "Salones UPT", "estado" to "ok")) }
        authRoutes()
        espacioRoutes()
        reservacionRoutes()
        adminRoutes()
    }
}
