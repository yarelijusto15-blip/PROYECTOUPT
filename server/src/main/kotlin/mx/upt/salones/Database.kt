package mx.upt.salones

import at.favre.lib.crypto.bcrypt.BCrypt
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.time
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.LocalTime

object Usuarios : IntIdTable("usuario") {
    val correo = varchar("correo", 120).uniqueIndex()
    val nombre = varchar("nombre", 120)
    val claveHash = varchar("clave_hash", 100)
    val rol = varchar("rol", 20) // ALUMNO, MAESTRO, ADMIN
}

object Espacios : IntIdTable("espacio") {
    val edificio = varchar("edificio", 60)
    val nombre = varchar("nombre", 60)
    val tipo = varchar("tipo", 20) // SALON, LABORATORIO
    val capacidad = integer("capacidad")
    val equipamiento = varchar("equipamiento", 255) // separado por comas
}

/** Horario fijo de clases: dia 1 = lunes ... 7 = domingo */
object Horarios : IntIdTable("horario") {
    val espacioId = reference("espacio_id", Espacios)
    val dia = integer("dia")
    val horaInicio = time("hora_inicio")
    val horaFin = time("hora_fin")
    val materia = varchar("materia", 100)
}

object Reservaciones : IntIdTable("reservacion") {
    val usuarioId = reference("usuario_id", Usuarios)
    val espacioId = reference("espacio_id", Espacios)
    val fecha = date("fecha")
    val horaInicio = time("hora_inicio")
    val horaFin = time("hora_fin")
    val motivo = varchar("motivo", 200)
    val estado = varchar("estado", 20) // PENDIENTE, CONFIRMADA, CANCELADA, RECHAZADA
}

object DatabaseFactory {
    fun init() {
        val cfg = HikariConfig().apply {
            jdbcUrl = System.getenv("DB_URL")
                ?: "jdbc:mysql://localhost:3306/salones?useUnicode=true&characterEncoding=utf8"
            username = System.getenv("DB_USER") ?: "root"
            password = System.getenv("DB_PASS") ?: ""
            driverClassName = "com.mysql.cj.jdbc.Driver"
            maximumPoolSize = 5
        }
        Database.connect(HikariDataSource(cfg))
        transaction {
            SchemaUtils.create(Usuarios, Espacios, Horarios, Reservaciones)
            if (Usuarios.selectAll().count() == 0L) seed()
        }
    }

    private fun hash(p: String) = BCrypt.withDefaults().hashToString(12, p.toCharArray())

    private fun seed() {
        fun usuario(c: String, n: String, p: String, r: String) = Usuarios.insert {
            it[correo] = c; it[nombre] = n; it[claveHash] = hash(p); it[rol] = r
        }
        usuario("admin@upt.edu.mx", "Administrador", "admin1234", "ADMIN")
        usuario("2310168@upt.edu.mx", "Yareli Escobar", "alumno1234", "ALUMNO")
        usuario("maestro@upt.edu.mx", "Maestro Demo", "maestro1234", "MAESTRO")

        fun esp(ed: String, nom: String, t: String, cap: Int, eq: String) = Espacios.insertAndGetId {
            it[edificio] = ed; it[nombre] = nom; it[tipo] = t; it[capacidad] = cap; it[equipamiento] = eq
        }.value
        val ing = "Edificio de Ingenierías"
        val ci = "Centro de Información"
        val i101 = esp(ing, "Salón I-101", "SALON", 40, "Proyector,Clima,Pizarrón")
        val i203 = esp(ing, "Salón I-203", "SALON", 35, "Proyector,Pizarrón")
        val i105 = esp(ing, "Salón I-105", "SALON", 40, "Clima,Pizarrón")
        esp(ing, "Salón I-206", "SALON", 30, "Proyector,Clima")
        esp(ci, "Laboratorio de Cómputo 1", "LABORATORIO", 30, "Android Studio,MySQL,Proyector")
        val lab2 = esp(ci, "Laboratorio de Cómputo 2", "LABORATORIO", 25, "Kotlin,Git,Proyector")
        val redes = esp(ci, "Laboratorio de Redes", "LABORATORIO", 20, "Packet Tracer,Switches,Routers")
        esp(ci, "Laboratorio de Software", "LABORATORIO", 28, "IntelliJ,Docker,Proyector")

        fun hor(e: Int, d: Int, i: String, f: String, m: String) = Horarios.insert {
            it[espacioId] = e; it[dia] = d
            it[horaInicio] = LocalTime.parse(i); it[horaFin] = LocalTime.parse(f); it[materia] = m
        }
        for (d in 1..5) {
            hor(redes, d, "09:00", "11:00", "Redes II")
            hor(i105, d, "09:00", "11:00", "Cálculo")
            hor(i101, d, "13:00", "15:00", "Programación")
        }
        hor(lab2, 1, "08:00", "10:00", "Base de Datos")
        hor(i203, 2, "11:30", "13:00", "Álgebra Lineal")
    }
}
