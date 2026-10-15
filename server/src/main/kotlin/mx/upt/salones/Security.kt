package mx.upt.salones

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

object Jwt {
    private const val ISSUER = "upt-salones"
    private val secret = System.getenv("JWT_SECRET") ?: "cambia-este-secreto-en-produccion"
    private val algorithm = Algorithm.HMAC256(secret)

    val verifier = JWT.require(algorithm).withIssuer(ISSUER).build()

    fun token(id: Int, rol: String): String = JWT.create()
        .withIssuer(ISSUER)
        .withClaim("id", id)
        .withClaim("rol", rol)
        .withExpiresAt(Date(System.currentTimeMillis() + 8 * 3600_000L))
        .sign(algorithm)
}
