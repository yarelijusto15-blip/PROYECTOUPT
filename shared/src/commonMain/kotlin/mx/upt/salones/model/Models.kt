package mx.upt.salones.model

import kotlinx.serialization.Serializable

@Serializable
enum class TipoEspacio { SALON, LABORATORIO }

@Serializable
enum class EstadoReservacion { PENDIENTE, CONFIRMADA, CANCELADA, RECHAZADA }

@Serializable
data class Espacio(
    val id: Int,
    val nombre: String,
    val edificio: String,
    val tipo: TipoEspacio,
    val capacidad: Int,
    val equipamiento: List<String>
)

/** Estado de un espacio en una fecha y hora dadas. horas en formato HH:mm */
@Serializable
data class EspacioDisponible(
    val espacio: Espacio,
    val libre: Boolean,
    val libreHasta: String?,      // si está libre: hasta qué hora (null = todo el día)
    val ocupadoPor: String?       // si está ocupado: materia o "Reservado"
)

@Serializable
data class LoginRequest(val correo: String, val contrasena: String)

@Serializable
data class LoginResponse(val token: String, val nombre: String, val rol: String)

/** fecha en formato yyyy-MM-dd, horas en formato HH:mm */
@Serializable
data class ReservacionRequest(
    val espacioId: Int,
    val fecha: String,
    val horaInicio: String,
    val horaFin: String,
    val motivo: String
)

@Serializable
data class Reservacion(
    val id: Int,
    val espacio: String,
    val solicitante: String,
    val fecha: String,
    val horaInicio: String,
    val horaFin: String,
    val motivo: String,
    val estado: EstadoReservacion
)

@Serializable
data class ErrorResponse(val mensaje: String)
