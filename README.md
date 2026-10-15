# PROYECTO – Laboratorios y salones libres (UPT)

Autora: Yareli Escobar (@yarelijusto15-blip)
Ingeniería en Sistemas Computacionales · Kotlin Multiplatform

## Etapa 1: servidor

## Requisitos
- JDK 17
- MySQL 8 con una base vacía:  CREATE DATABASE salones CHARACTER SET utf8mb4;

## Configuración (variables de entorno, todas opcionales)
DB_URL  (por defecto jdbc:mysql://localhost:3306/salones)
DB_USER (por defecto root)   DB_PASS (por defecto vacío)
JWT_SECRET   PORT (por defecto 8080)

## Ejecutar
Abrir la carpeta en IntelliJ IDEA o Android Studio (descarga Gradle solo) y correr la tarea  :server:run
En la primera ejecución se crean las tablas y datos de ejemplo.

## Usuarios de prueba
admin@upt.edu.mx / admin1234          (ADMIN)
2310168@upt.edu.mx / alumno1234       (ALUMNO)
maestro@upt.edu.mx / maestro1234      (MAESTRO)

## Endpoints
POST /api/auth/login                          { correo, contrasena }
GET  /api/espacios?tipo=SALON|LABORATORIO&edificio=
GET  /api/disponibilidad?fecha=yyyy-MM-dd&hora=HH:mm&tipo=&edificio=
POST /api/reservaciones                       { espacioId, fecha, horaInicio, horaFin, motivo }
GET  /api/reservaciones/mias
DELETE /api/reservaciones/{id}                (cancela la propia)
GET  /api/admin/reservaciones/pendientes      (ADMIN)
POST /api/admin/reservaciones/{id}/aprobar|rechazar   (ADMIN)
Todos menos el login requieren el encabezado  Authorization: Bearer <token>
