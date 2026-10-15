package mx.upt.salones

import at.favre.lib.crypto.bcrypt.BCrypt
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import mx.upt.salones.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.LocalTime

// ---------- utilidades ----------

private class Sesion(val id: Int, val rol: String)

private fun ApplicationCall.sesion(): Sesion {
    val p = principal<JWTPrincipal>()!!
    return Sesion(p.payload.getClaim("id").asInt(), p.payload.getClaim("rol").asString())
}

private suspend fun ApplicationCall.error(code: HttpStatusCode, msg: String) =
    respond(code, ErrorResponse(msg))

private fun String.toHora(): LocalTime? = runCatching { LocalTime.parse(this) }.getOrNull()
private fun String.toFecha(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()
private fun LocalTime.hhmm() = "%02d:%02d".format(hour, minute)

private fun ResultRow.toEspacio() = Espacio(
    id = this[Espacios.id].value,
    nombre = this[Espacios.nombre],
    edificio = this[Espacios.edificio],
    tipo = TipoEspacio.valueOf(this[Espacios.tipo]),
    capacidad = this[Espacios.capacidad],
    equipamiento = this[Espacios.equipamiento].split(",").map { it.trim() }.filter { it.isNotEmpty() }
)

private class Ocupacion(val ini: LocalTime, val fin: LocalTime, val motivo: String)

/** Clases fijas del día + reservaciones activas, ordenadas por hora. Llamar dentro de transaction. */
private fun ocupaciones(espacioId: Int, fecha: LocalDate): List<Ocupacion> {
    val clases = Horarios.selectAll()
        .where { (Horarios.espacioId eq espacioId) and (Horarios.dia eq fecha.dayOfWeek.value) }
        .map { Ocupacion(it[Horarios.horaInicio], it[Horarios.horaFin], it[Horarios.materia]) }
    val reservas = Reservaciones.selectAll()
        .where {
            (Reservaciones.espacioId eq espacioId) and (Reservaciones.fecha eq fecha) and
                (Reservaciones.estado inList listOf("PENDIENTE", "CONFIRMADA"))
        }
        .map { Ocupacion(it[Reservaciones.horaInicio], it[Reservaciones.horaFin], "Reservado") }
    return (clases + reservas).sortedBy { it.ini }
}

private fun ResultRow.toReservacion() = Reservacion(
    id = this[Reservaciones.id].value,
    espacio = this[Espacios.nombre],
    solicitante = this[Usuarios.nombre],
    fecha = this[Reservaciones.fecha].toString(),
    horaInicio = this[Reservaciones.horaInicio].hhmm(),
    horaFin = this[Reservaciones.horaFin].hhmm(),
    motivo = this[Reservaciones.motivo],
    estado = EstadoReservacion.valueOf(this[Reservaciones.estado])
)

private fun reservacionesQuery() = (Reservaciones innerJoin Espacios innerJoin Usuarios).selectAll()

// ---------- autenticación ----------

fun Route.authRoutes() {
    post("/api/auth/login") {
        val req = call.receive<LoginRequest>()
        val u = transaction {
            Usuarios.selectAll().where { Usuarios.correo eq req.correo.trim().lowercase() }.singleOrNull()
        }
        val ok = u != null &&
            BCrypt.verifyer().verify(req.contrasena.toCharArray(), u[Usuarios.claveHash]).verified
        if (!ok) return@post call.error(HttpStatusCode.Unauthorized, "Correo o contraseña incorrectos")
        call.respond(
            LoginResponse(Jwt.token(u!![Usuarios.id].value, u[Usuarios.rol]), u[Usuarios.nombre], u[Usuarios.rol])
        )
    }
}

// ---------- espacios y disponibilidad ----------

fun Route.espacioRoutes() = authenticate("auth") {
    get("/api/espacios") {
        val tipo = call.parameters["tipo"]
        val edificio = call.parameters["edificio"]
        val lista = transaction {
            val q = Espacios.selectAll()
            tipo?.let { q.andWhere { Espacios.tipo eq it.uppercase() } }
            edificio?.let { q.andWhere { Espacios.edificio eq it } }
            q.orderBy(Espacios.nombre).map { it.toEspacio() }
        }
        call.respond(lista)
    }

    // /api/disponibilidad?fecha=2026-10-15&hora=10:00&tipo=SALON&edificio=...
    get("/api/disponibilidad") {
        val fecha = call.parameters["fecha"]?.toFecha() ?: LocalDate.now()
        val hora = call.parameters["hora"]?.toHora() ?: LocalTime.now().withSecond(0).withNano(0)
        val tipo = call.parameters["tipo"]
        val edificio = call.parameters["edificio"]
        val lista = transaction {
            val q = Espacios.selectAll()
            tipo?.let { q.andWhere { Espacios.tipo eq it.uppercase() } }
            edificio?.let { q.andWhere { Espacios.edificio eq it } }
            q.orderBy(Espacios.nombre).map { fila ->
                val esp = fila.toEspacio()
                val ocup = ocupaciones(esp.id, fecha)
                val actual = ocup.firstOrNull { hora >= it.ini && hora < it.fin }
                if (actual != null) {
                    EspacioDisponible(esp, false, null, actual.motivo)
                } else {
                    val siguiente = ocup.firstOrNull { it.ini > hora }
                    EspacioDisponible(esp, true, siguiente?.ini?.hhmm(), null)
                }
            }
        }
        call.respond(lista)
    }
}

// ---------- reservaciones del usuario ----------

fun Route.reservacionRoutes() = authenticate("auth") {
    post("/api/reservaciones") {
        val s = call.sesion()
        val req = call.receive<ReservacionRequest>()
        val fecha = req.fecha.toFecha() ?: return@post call.error(HttpStatusCode.BadRequest, "Fecha no válida (yyyy-MM-dd)")
        val ini = req.horaInicio.toHora() ?: return@post call.error(HttpStatusCode.BadRequest, "Hora de inicio no válida (HH:mm)")
        val fin = req.horaFin.toHora() ?: return@post call.error(HttpStatusCode.BadRequest, "Hora de fin no válida (HH:mm)")
        if (!fin.isAfter(ini)) return@post call.error(HttpStatusCode.BadRequest, "La hora de fin debe ser posterior a la de inicio")
        if (fecha.isBefore(LocalDate.now())) return@post call.error(HttpStatusCode.BadRequest, "No se puede reservar en el pasado")
        if (req.motivo.isBlank()) return@post call.error(HttpStatusCode.BadRequest, "Indica el motivo de la reservación")

        // Regla provisional: alumnos quedan PENDIENTE de aprobación; maestros y admin se confirman solos.
        val estado = if (s.rol == "ALUMNO") "PENDIENTE" else "CONFIRMADA"

        val nuevoId = transaction {
            val existe = Espacios.selectAll().where { Espacios.id eq req.espacioId }.count() > 0
            if (!existe) return@transaction -1
            val choca = ocupaciones(req.espacioId, fecha).any { ini < it.fin && fin > it.ini }
            if (choca) return@transaction -2
            Reservaciones.insertAndGetId {
                it[usuarioId] = s.id; it[espacioId] = req.espacioId; it[Reservaciones.fecha] = fecha
                it[horaInicio] = ini; it[horaFin] = fin; it[motivo] = req.motivo.trim(); it[Reservaciones.estado] = estado
            }.value
        }
        when (nuevoId) {
            -1 -> call.error(HttpStatusCode.NotFound, "El espacio no existe")
            -2 -> call.error(HttpStatusCode.Conflict, "El espacio ya está ocupado en ese horario")
            else -> {
                val r = transaction { reservacionesQuery().where { Reservaciones.id eq nuevoId }.single().toReservacion() }
                call.respond(HttpStatusCode.Created, r)
            }
        }
    }

    get("/api/reservaciones/mias") {
        val s = call.sesion()
        val lista = transaction {
            reservacionesQuery().where { Reservaciones.usuarioId eq s.id }
                .orderBy(Reservaciones.fecha to SortOrder.DESC, Reservaciones.horaInicio to SortOrder.DESC)
                .map { it.toReservacion() }
        }
        call.respond(lista)
    }

    delete("/api/reservaciones/{id}") {
        val s = call.sesion()
        val id = call.parameters["id"]?.toIntOrNull() ?: return@delete call.error(HttpStatusCode.BadRequest, "Id no válido")
        val filas = transaction {
            Reservaciones.update({ (Reservaciones.id eq id) and (Reservaciones.usuarioId eq s.id) }) {
                it[estado] = "CANCELADA"
            }
        }
        if (filas == 0) call.error(HttpStatusCode.NotFound, "Reservación no encontrada")
        else call.respond(mapOf("mensaje" to "Reservación cancelada"))
    }
}

// ---------- administración ----------

fun Route.adminRoutes() = authenticate("auth") {
    get("/api/admin/reservaciones/pendientes") {
        if (call.sesion().rol != "ADMIN") return@get call.error(HttpStatusCode.Forbidden, "Solo administradores")
        val lista = transaction {
            reservacionesQuery().where { Reservaciones.estado eq "PENDIENTE" }
                .orderBy(Reservaciones.fecha to SortOrder.ASC, Reservaciones.horaInicio to SortOrder.ASC)
                .map { it.toReservacion() }
        }
        call.respond(lista)
    }

    for ((accion, nuevoEstado) in listOf("aprobar" to "CONFIRMADA", "rechazar" to "RECHAZADA")) {
        post("/api/admin/reservaciones/{id}/$accion") {
            if (call.sesion().rol != "ADMIN") return@post call.error(HttpStatusCode.Forbidden, "Solo administradores")
            val id = call.parameters["id"]?.toIntOrNull() ?: return@post call.error(HttpStatusCode.BadRequest, "Id no válido")
            val filas = transaction {
                Reservaciones.update({ (Reservaciones.id eq id) and (Reservaciones.estado eq "PENDIENTE") }) {
                    it[estado] = nuevoEstado
                }
            }
            if (filas == 0) call.error(HttpStatusCode.NotFound, "No hay una reservación pendiente con ese id")
            else call.respond(mapOf("mensaje" to "Reservación $nuevoEstado"))
        }
    }
}
