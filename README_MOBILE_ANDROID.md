# App móvil Android (Kotlin + Jetpack Compose) contra las APIs de MTO

Guía de cero a app funcionando: instalar las herramientas, aprender lo justo de Kotlin y de
Jetpack Compose, y montar el cliente de **esta** API — con sus rutas, su Keycloak, su paginación y
sus trampas concretas, no las de un tutorial genérico.

Está escrita para quien viene del backend y no ha tocado Android. Cada bloque enlaza la
documentación oficial: lo que hay aquí es el camino y el porqué; el detalle exhaustivo de cada
librería vive en su doc, que además se mantiene al día sola.

> **Versiones.** Las de esta guía son las vigentes en **septiembre de 2026**: Android Studio Quail
> (2026.1.x), AGP 9.3.0, Kotlin 2.4.x, Compose BOM 2026.09.00. Android Studio subraya en
> `libs.versions.toml` cualquier versión que se haya quedado atrás y ofrece la nueva con
> `Alt+Enter`; la tabla canónica de AndroidX está en
> [developer.android.com/jetpack/androidx/versions](https://developer.android.com/jetpack/androidx/versions).
> Si algún número de aquí no resuelve, ese es el sitio donde mirar, no un fallo de la guía.

---

## Índice

| | Bloque | Qué se saca |
|---|---|---|
| [1](#1-el-mapa-qué-vas-a-consumir) | El mapa del backend | Qué expone `mto-configuration` y cómo se habla con ello |
| [2](#2-kotlin-en-una-tarde-si-vienes-de-java) | Kotlin en una tarde | Lo mínimo para leer y escribir el código de la app |
| [3](#3-herramientas-android-studio-jdk-sdk-y-emulador) | Herramientas | Android Studio, JDK, SDK, emulador |
| [4](#4-crear-el-proyecto) | Crear el proyecto | Asistente, anatomía, `libs.versions.toml`, Gradle |
| [5](#5-arquitectura-de-la-app) | Arquitectura | Capas, flujo de datos, qué va dónde |
| [6](#6-jetpack-compose-de-cero) | Jetpack Compose | Estado, recomposición, Material 3, listas, navegación |
| [7](#7-la-capa-de-red-retrofit--kotlinxserialization) | La capa de red | Retrofit, DTOs, paginación de Spring, errores RFC 9457 |
| [8](#8-autenticación-con-keycloak) | Autenticación | AppAuth + PKCE, cliente `mto-mobile`, tokens, roles |
| [9](#9-llegar-al-backend-local-desde-el-móvil) | Backend local | Emulador, `10.0.2.2`, `auth.mto.local`, tráfico en claro |
| [10](#10-primera-pantalla-completa-vías-y-perfiles) | Primera pantalla | Listado paginado + detalle, de punta a punta |
| [11](#11-escritura-el-apartado-que-cuesta-datos) | Escritura | Colecciones de hijos, `versionNumber`, errores de campo |
| [12](#12-offline-el-túnel-no-tiene-cobertura) | Offline | Room, sincronización, WorkManager |
| [13](#13-trabajos-en-segundo-plano-202--jobid) | Trabajos | `202 Accepted`, sondeo, descarga del CSV |
| [14](#14-inyección-de-dependencias-con-hilt) | Hilt | Grafo de dependencias sin ceremonia |
| [15](#15-tests) | Tests | Unitarios, red simulada, UI de Compose |
| [16](#16-publicar-firma-r8-y-requisitos-de-google-play) | Publicar | R8, firma, `targetSdk`, rendimiento |
| [17](#17-y-si-mañana-hay-ios) | Y si mañana hay iOS | KMP y Compose Multiplatform |
| [18](#18-ruta-de-aprendizaje-con-la-doc-oficial) | Ruta de aprendizaje | Qué leer y en qué orden |
| [19](#19-apéndices) | Apéndices | Tabla de endpoints, DTO→Kotlin, checklist |

---

## 1. El mapa: qué vas a consumir

Antes de instalar nada conviene saber contra qué se habla. El detalle completo está en
[`README_API.md`](README_API.md); esto es el resumen que necesita el cliente móvil.

### 1.1 Rutas

Ruta base: **`/api/v1/configuration`**. En local, con el `compose.yaml` de este repo, la aplicación
se publica en el puerto **8081** del host (dentro del contenedor escucha en el 8080).

```
http://<host>:8081/api/v1/configuration/{recurso}
```

| Familia | Recursos |
|---|---|
| Infraestructura | `execution-packages`, `stations`, `tracks`, `profiles`, `cantilevers`, `steady-arms`, `disconnectors`, `section-insulators` |
| Listas de valores (LOV) | `anchorages`, `anchorage-foundations`, `anchorage-foundation-types`, `assembly-configurations`, `cantilever-types`, `comercial-entity-types`, `disconnector-functions`, `foundations`, `foundation-types`, `pole-types`, `portals`, `portal-types`, `profile-statuses`, `return-supports`, `sectionings`, `steady-arm-types`, `support-types` |
| Fachada asíncrona | los mismos recursos bajo `/api/v1/configuration/async` |
| Trabajos | `/profiles/jobs/**`, `/lovs/jobs/**`, `/master-data/republish` |

Los verbos y sus códigos, que es lo que hay que reflejar en el cliente:

| Verbo y ruta | Código | Cuerpo |
|---|---|---|
| `GET /{recurso}/{id}` | `200` | el DTO |
| `GET /{recurso}/paged?page=&size=&sort=` | `200` | página de Spring Data |
| `POST /{recurso}/filter?page=&size=` | `200` | página de Spring Data (filtro por campos) |
| `POST /{recurso}/search` | `200` | página de Spring Data (criteria libre) |
| `POST /{recurso}` | **`201`** | el DTO creado, con su `id` |
| `PUT /{recurso}/{id}` | `200` | el DTO actualizado |
| `DELETE /{recurso}/{id}` | **`204`** | vacío |
| `POST /{recurso}/bulk` · `PUT /{recurso}/bulk` | `201` / `200` | array de DTOs |

Y los atajos que ahorran filtros al móvil:

```
GET /profiles/track/{trackId}/keyset?lastKp=&lastId=&pageSize=50   # ventana por cursor
GET /profiles/track/{trackId}/range?startKp=&endKp=                # tramo kilométrico
GET /cantilevers/profile/{profileId}
GET /steady-arms/cantilever/{cantileverId}
GET /disconnectors/station/{stationId}          · /station/name/{stationName}
GET /section-insulators/station/{stationId}     · /station/name/{stationName}
GET /{lov}                 · /{lov}/{id}        · /{lov}/code/{code}
```

La **fachada asíncrona** (`/async/...`) no sirve para nada en un móvil: atiende la petición en otro
hilo del servidor pero responde por la misma conexión, así que el cliente espera exactamente igual.
Úsese la vía síncrona y punto.

### 1.2 Seguridad

La API es un **OAuth2 Resource Server**: todo lo que cuelga de `/api/v1/configuration` exige un JWT
del realm `mto` de Keycloak, con la audiencia `mto-configuration-api`. Sin token, `401`.

Los permisos son **roles de cliente** de `mto-configuration-api` y se comprueban por verbo:

| Permiso | Qué abre |
|---|---|
| `config-read` | todos los `GET`/`HEAD`, y los `POST` de `search`, `filter` y `jobs/export` |
| `config-write` | `POST`, `PUT`, `PATCH` de registro a registro |
| `config-delete` | `DELETE` |
| `config-import` | cargas masivas: `bulk`, `jobs/bulk-*`, `jobs/import`, `master-data/republish` |
| `lov-manage` | escribir en las listas de valores (además del permiso de escritura) |
| `config-audit` | histórico de revisiones |
| `ops-metrics` / `ops-write` | Actuator |

Los perfiles de negocio son roles **de realm compuestos** que agrupan permisos: `mto-viewer`,
`mto-editor`, `mto-admin`, `mto-auditor`. Keycloak los expande al emitir el token, así que en el
JWT los permisos llegan dentro de `resource_access["mto-configuration-api"].roles`. Eso es lo que
lee la app para decidir qué botones enseña (§8.7).

### 1.3 Errores

Todos con el mismo cuerpo, `application/problem+json` ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)):

```json
{
  "type": "https://api.mto-configuration/errors/val-000",
  "title": "Error de validación",
  "status": 400,
  "detail": "La petición tiene 2 errores de validación",
  "instance": "/api/v1/configuration/profiles",
  "code": "VAL-000",
  "timestamp": "2026-08-31T10:15:00Z",
  "traceId": "a4f7fe56a69a450a",
  "retryable": false,
  "errors": [
    { "field": "profileId",               "code": "VAL-001", "message": "El campo es obligatorio" },
    { "field": "cantilevers[1].cwHeight", "code": "VAL-004", "message": "Debe ser mayor que cero" }
  ]
}
```

Dos campos valen oro en el móvil:

- **`errors[].field`** es una ruta navegable desde la raíz del cuerpo (`cantilevers[1].cwHeight`),
  así que el error se puede pintar **en el campo del formulario que lo causó** en vez de en un
  diálogo genérico.
- **`retryable`** dice si repetir la misma petición puede funcionar. Es justo lo que necesita la
  cola de sincronización offline (§12) para decidir entre reintentar o marcar el cambio como
  fallido.

| HTTP | `code` | Qué hace la app |
|---|---|---|
| `400` | `VAL-000` | pintar los errores en los campos |
| `401` | — | token caducado o ausente → refrescar, y si no, volver al login |
| `403` | — | el usuario no tiene el permiso → la pantalla no debería haberlo ofrecido |
| `404` | `NOT-001` | el recurso ya no existe → refrescar la lista |
| `409` | `CON-001` | otro ha guardado antes → recargar y ofrecer fusionar |
| `429` | — | sin hueco para el trabajo; respeta `Retry-After` |
| `500` | `TEC-999` | enseñar el `traceId`: es lo que se pega en la incidencia |

### 1.4 Y los otros MTO

La app va a consumir más de un servicio: `mto-configuration` es este, y el dominio tiene además
`mto-stock` y una puerta de entrada (`mto-gateway`). Tres consecuencias, todas en §8 y §14:

1. El token necesita **un *audience mapper* por cada API** a la que se llame. El del navegador
   (`mto-frontend`) ya los lleva para `mto-configuration-api`, `mto-stock-api` y `mto-gateway-api`;
   el cliente móvil que crearás en §8.2 necesita los suyos.
2. Una sola sesión, un solo `OkHttpClient`, **un `Retrofit` por base URL**.
3. Antes de cablear tres hosts, pregunta en `mto-platform` si el gateway es la entrada oficial: si
   expone las tres APIs bajo un mismo dominio, la app tiene un solo `baseUrl` y este punto
   desaparece.

---

## 2. Kotlin en una tarde (si vienes de Java)

No hace falta dominar Kotlin para empezar, pero sí reconocer siete cosas. La referencia buena y
corta es [Kotlin para desarrolladores de Java](https://kotlinlang.org/docs/java-to-kotlin-idioms-strings.html)
y el [tour interactivo](https://kotlinlang.org/docs/kotlin-tour-welcome.html); para practicar,
[Kotlin Koans](https://kotlinlang.org/docs/koans.html).

**1. Null está en el tipo.** `String` nunca es nulo; `String?` puede serlo, y el compilador te
obliga a resolverlo. Esto se nota mucho contra esta API, donde casi todo el DTO es opcional:

```kotlin
val nombre: String = track.name ?: "(sin nombre)"   // ?: es el "elvis": valor por defecto
val kp: String? = profile.kp                        // puede faltar, y el tipo lo dice
profile.disconnector?.name                          // ?. no llama si es null, devuelve null
```

**2. `val` / `var`.** `val` es una referencia inmutable (el `final` de Java); es lo normal. `var`
solo donde de verdad cambie.

**3. `data class` = record con `copy`.** Es la pieza central de la app: los DTOs, el estado de la
UI y el modelo de dominio son `data class`.

```kotlin
data class Track(val id: Long, val name: String, val enabled: Boolean)

val v = Track(1, "VIA 1", true)
val deshabilitada = v.copy(enabled = false)   // copia con un campo cambiado; v no se toca
```

**4. Funciones con parámetros por nombre y por defecto.** Se acabaron los cuatro constructores:

```kotlin
fun paged(page: Int = 0, size: Int = 20, sort: String? = null) = Unit
paged(size = 50)                              // los demás toman su valor por defecto
```

**5. `sealed interface` = jerarquía cerrada.** Con `when` exhaustivo, sin `else`. Es como se modela
el estado de una pantalla y el error de la API:

```kotlin
sealed interface UiState {
    data object Loading : UiState
    data class Content(val items: List<Track>) : UiState
    data class Error(val message: String) : UiState
}

when (state) {                     // el compilador exige cubrir los tres casos
    UiState.Loading    -> Spinner()
    is UiState.Content -> Lista(state.items)
    is UiState.Error   -> Aviso(state.message)
}
```

**6. Corrutinas: asíncrono escrito en línea recta.** Una función `suspend` se puede pausar sin
bloquear el hilo. Retrofit devuelve el resultado directamente y los errores son excepciones
normales; no hay callbacks ni `Future`:

```kotlin
suspend fun cargar(id: Long): Profile {          // suspend: puede tardar, no bloquea
    val dto = api.getProfile(id)                 // aquí se espera a la red
    return dto.toDomain()
}
```

Las reglas prácticas (dónde se lanzan, quién las cancela) están en
[corrutinas en Android](https://developer.android.com/kotlin/coroutines) y en las
[buenas prácticas](https://developer.android.com/kotlin/coroutines/coroutines-best-practices). La
que más ahorra disgustos: **la capa de datos decide su `Dispatcher`, la UI nunca**.

**7. `Flow` = stream frío de valores.** Un `Flow<T>` emite a lo largo del tiempo; un `StateFlow<T>`
además guarda el último valor y es lo que la UI observa. Room y DataStore devuelven `Flow`, así que
una pantalla se refresca sola cuando cambia la base de datos local.

```kotlin
val vias: StateFlow<List<Track>> = repo.observeTracks()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

Referencia: [Flow](https://kotlinlang.org/docs/flow.html) y
[StateFlow/SharedFlow en Android](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow).

> Con estas siete cosas se lee toda la guía. El resto de Kotlin (delegados, DSLs, `inline`,
> genéricos con varianza) aparece cuando hace falta y se aprende leyendo el código que ya funciona.

---

## 3. Herramientas: Android Studio, JDK, SDK y emulador

### 3.1 Instalar Android Studio

Descarga desde [developer.android.com/studio](https://developer.android.com/studio) — la versión
estable actual es la serie **Quail (2026.1.x)**. El instalador trae el SDK, el emulador y un JDK
embebido (**JBR 21**), que es el que debe usar Gradle: nada de apuntar a un JDK del sistema.

Requisitos y pasos por sistema operativo:
[guía de instalación oficial](https://developer.android.com/studio/install).

Con Android Studio no hace falta instalar Gradle aparte: cada proyecto trae su
[wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) (`./gradlew`), igual que
`./mvnw` en este repo. **AGP 9** exige Gradle 9.1+ y **JDK 17 o superior**.

### 3.2 SDK Manager

`Settings ▸ Languages & Frameworks ▸ Android SDK` (o el icono del SDK Manager). Marca:

| Pestaña | Qué marcar | Por qué |
|---|---|---|
| SDK Platforms | La API más alta estable (hoy **37**) | es el `compileSdk` con el que compilas |
| SDK Platforms | La API de tu emulador (**36**) | la imagen del dispositivo virtual |
| SDK Tools | Android SDK Build-Tools **36.0.0+** | mínimo de AGP 9 |
| SDK Tools | Android Emulator, Platform-Tools | emulador y `adb` |

Referencia: [SDK Manager](https://developer.android.com/studio/intro/update#sdk-manager).

### 3.3 Un dispositivo donde probar

**Emulador** (`Device Manager ▸ Add a new device`): elige un teléfono moderno con imagen de
**API 36** con Google Play. Consejos que ahorran horas:

- Acelera con el hipervisor del sistema: sin él, el emulador va a rastras
  ([configuración](https://developer.android.com/studio/run/emulator-acceleration)).
- El emulador tiene su propia red virtual. La dirección **`10.0.2.2` es el `localhost` de tu
  máquina** vista desde dentro, y es la clave de §9
  ([redes del emulador](https://developer.android.com/studio/run/emulator-networking)).

**Dispositivo físico**: activa *Opciones de desarrollador* y *Depuración por USB*
([instrucciones](https://developer.android.com/studio/run/device)). Es imprescindible para probar
de verdad el login (el navegador del sistema) y el comportamiento sin cobertura.

### 3.4 Comprobación

```bash
adb devices          # tu dispositivo o emulador listado
./gradlew --version  # Gradle 9.1+, JVM 17+
```

---

## 4. Crear el proyecto

### 4.1 El asistente

`File ▸ New ▸ New Project ▸ Empty Activity` (la plantilla de Compose; no elijas "Empty Views
Activity", que es la del sistema de vistas antiguo).

| Campo | Valor sugerido | Nota |
|---|---|---|
| Name | `MTO Field` | el nombre visible de la app |
| Package name | `com.alejandro.mtomobile` | identificador único; también es el esquema del *redirect* de OAuth (§8) |
| Minimum SDK | **API 26** (Android 8.0) | por debajo hay que *desugarizar* `java.time` y el Keystore es más pobre; API 26 cubre prácticamente todo el parque |
| Build configuration language | Kotlin DSL (`build.gradle.kts`) | es el valor por defecto y el de esta guía |

El asistente genera un proyecto que **ya compila y arranca**. Ejecútalo (`Shift+F10`) antes de tocar
nada: si el "Hello Android" no sale, el problema es de herramientas, no de tu código.

### 4.2 Anatomía

```
MTOField/
├── gradle/
│   ├── libs.versions.toml        <- catálogo de versiones: TODAS las dependencias van aquí
│   └── wrapper/                  <- versión de Gradle fijada
├── settings.gradle.kts           <- módulos y repositorios
├── build.gradle.kts              <- raíz: declara los plugins, no dependencias
├── gradle.properties             <- flags de Gradle y de AGP
└── app/
    ├── build.gradle.kts          <- el que de verdad tocarás
    ├── proguard-rules.pro        <- reglas de R8 para la release
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/alejandro/mtomobile/...   <- Kotlin vive en java/, sí
        │   └── res/                               <- recursos: strings, iconos, temas
        ├── debug/                                 <- lo que solo existe en debug (§9)
        ├── test/                                  <- unitarios, en la JVM del PC
        └── androidTest/                           <- instrumentados, en dispositivo
```

Dos cosas que sorprenden viniendo de Maven:

- **`src/main/java` contiene Kotlin.** Es solo el nombre de la carpeta.
- **El manifiesto no es un `web.xml`.** Declara los componentes de la app (actividades), los
  permisos que pide y los `intent-filter` por los que el sistema le entrega cosas — entre ellos, la
  vuelta del login (§8.3).

### 4.3 El catálogo de versiones

Gradle centraliza las dependencias en
[`gradle/libs.versions.toml`](https://docs.gradle.org/current/userguide/version_catalogs.html). El
asistente ya deja ahí Compose, AndroidX y los tests; **no toques esas líneas** (las pone con las
versiones que su AGP espera). Añade solo lo nuestro:

```toml
# gradle/libs.versions.toml  — AÑADIR a lo que generó el asistente

[versions]
# Red
retrofit = "3.0.0"                 # https://github.com/square/retrofit/releases
okhttp = "4.12.0"                  # la que arrastra Retrofit 3; se declara para el interceptor de logs
kotlinxSerialization = "1.9.0"     # https://github.com/Kotlin/kotlinx.serialization/releases
coroutines = "1.10.2"              # solo para el artefacto de test; el runtime lo arrastra AndroidX
turbine = "1.2.1"                  # aserciones sobre Flow en los tests
# Persistencia y trabajo en segundo plano
room = "2.8.5"
datastore = "1.2.1"
work = "2.11.2"
paging = "3.5.1"
# Inyección de dependencias
hilt = "2.60.1"                    # com.google.dagger
androidxHilt = "1.4.0"             # androidx.hilt (integración con Compose y WorkManager)
# OAuth
appauth = "0.11.1"                 # https://github.com/openid/AppAuth-Android
browser = "1.10.0"                 # Custom Tabs, que es donde se abre el login
# Procesador de anotaciones (Room y Hilt generan código con él)
ksp = "2.3.12"                     # https://github.com/google/ksp/releases

[libraries]
retrofit                    = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
retrofit-kotlinx            = { module = "com.squareup.retrofit2:converter-kotlinx-serialization", version.ref = "retrofit" }
okhttp-logging              = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }
okhttp-mockwebserver        = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }
coroutines-test             = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
turbine                     = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
kotlinx-serialization-json  = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }

room-runtime  = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx      = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
room-paging   = { module = "androidx.room:room-paging", version.ref = "room" }

datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
work-runtime          = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
paging-runtime        = { module = "androidx.paging:paging-runtime", version.ref = "paging" }
paging-compose        = { module = "androidx.paging:paging-compose", version.ref = "paging" }

hilt-android          = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler         = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
hilt-navigation       = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "androidxHilt" }
hilt-work             = { module = "androidx.hilt:hilt-work", version.ref = "androidxHilt" }

appauth = { module = "net.openid:appauth", version.ref = "appauth" }
browser = { module = "androidx.browser:browser", version.ref = "browser" }

[plugins]
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp                  = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt                 = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

> ⚠️ **Kotlin y KSP van emparejados.** Room y Hilt generan código con KSP, así que el techo de la
> versión de Kotlin no lo pone Kotlin: lo pone KSP. Si subes Kotlin por delante y la compilación
> falla con un mensaje del estilo *"ksp-X is too old for kotlin-Y"*, baja Kotlin o espera a la KSP
> correspondiente. La tabla está en [github.com/google/ksp/releases](https://github.com/google/ksp/releases).

### 4.4 `app/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)      // los tres de arriba los puso el asistente
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)     // añadidos por nosotros
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.alejandro.mtomobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.alejandro.mtomobile"
        minSdk = 26
        targetSdk = 36                            // el mínimo que exige Google Play (§16)
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // El esquema por el que Keycloak devuelve el control a la app tras el login.
        // AppAuth lo lee de aquí para registrar su actividad de recepción (§8.3).
        manifestPlaceholders["appAuthRedirectScheme"] = "com.alejandro.mtomobile"
    }

    buildFeatures {
        compose = true
        buildConfig = true                        // desde AGP 8 viene apagado; lo necesitamos para BASE_URL
    }

    buildTypes {
        debug {
            // Backend local visto desde el emulador. Ver §9 para el dispositivo físico.
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8081/api/v1/configuration/\"")
            buildConfigField("String", "OIDC_ISSUER",  "\"http://10.0.2.2:8082/realms/mto\"")
        }
        release {
            isMinifyEnabled = true                // R8: reduce y ofusca
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"https://mto.example.com/api/v1/configuration/\"")
            buildConfigField("String", "OIDC_ISSUER",  "\"https://auth.example.com/realms/mto\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // --- lo que ya trae el asistente: core-ktx, lifecycle, activity-compose, compose-bom, material3...

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.logging)           // no debugImplementation: se referencia desde main (§7.6)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)

    implementation(libs.appauth)
    implementation(libs.browser)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
}
```

Tres notas sobre **AGP 9**, que cambió cosas que verás distintas en cualquier tutorial anterior a
2026 ([notas de la versión](https://developer.android.com/build/releases/agp-9-0-0-release-notes)):

1. **Kotlin va incorporado.** AGP 9 aplica el plugin de Kotlin por su cuenta
   (`android.builtInKotlin`), así que ya **no se aplica `org.jetbrains.kotlin.android`**. Si un
   plugin de terceros se atraganta, la salida de emergencia es `android.builtInKotlin=false` en
   `gradle.properties` ([migración](https://developer.android.com/build/migrate-to-built-in-kotlin)).
2. **`kotlinOptions {}` está fuera.** Las opciones del compilador van en un bloque `kotlin {}` de
   primer nivel (`kotlin { compilerOptions { ... } }`).
3. **`kapt` no es compatible** con el Kotlin incorporado: todo lo que procese anotaciones usa KSP,
   que es lo que hace esta guía con Room y Hilt.

Y una regla de oro: **deja que el asistente genere el proyecto y añade encima**. Copiar un
`build.gradle.kts` entero de un tutorial es la forma más rápida de pelearte con versiones que no
son las de tu AGP.

---

## 5. Arquitectura de la app

Google publica una arquitectura recomendada y toda la documentación (y todos los ejemplos) la dan
por supuesta: [Guide to app architecture](https://developer.android.com/topic/architecture) y las
[recomendaciones concretas](https://developer.android.com/topic/architecture/recommendations).
Resumida:

```
          ┌──────────────────────────────────────────────┐
  UI      │  Composables  ← estado  ·  eventos →         │   pinta y recoge gestos
          │  ViewModel (StateFlow<UiState>)              │   decide qué se ve
          └───────────────────────┬──────────────────────┘
                                  │  modelo de dominio
          ┌───────────────────────┴──────────────────────┐
  Datos   │  Repository        ← única fuente de verdad  │   de dónde salen los datos
          │  ├── Remote: Retrofit + DTOs                 │
          │  └── Local:  Room + DataStore                │
          └──────────────────────────────────────────────┘
```

Las cuatro reglas que de verdad importan:

1. **Flujo de datos unidireccional.** El estado baja (de ViewModel a composables), los eventos
   suben (de composables a ViewModel). Un composable nunca escribe en el repositorio.
2. **El repositorio es la única fuente de verdad** de su dominio. La UI no sabe si el perfil vino
   de la red o de Room.
3. **Los DTOs no salen de la capa de datos.** La UI usa modelos de dominio. Parece ceremonia hasta
   la primera vez que la API añade un campo o renombra otro: se toca un mapper y nada más. En esta
   API es especialmente cierto — sus DTOs son de escritura y traen `alerts`, `versionNumber`,
   `createUser`... que a la pantalla no le importan.
4. **Nada de lógica en el `Composable`.** Si tiene un `if` con reglas de negocio, ese `if` va al
   ViewModel o al dominio, donde se puede probar sin arrancar la UI.

Estructura de paquetes que corresponde a eso — por capa dentro de cada funcionalidad, que escala
mejor que por capa global:

```
com.alejandro.mtomobile
├── MtoApplication.kt
├── core/
│   ├── network/      ApiProblem, MtoError, safeApiCall, interceptores
│   ├── auth/         AppAuth, almacén de tokens, roles
│   ├── database/     MtoDatabase, convertidores
│   └── ui/           tema, componentes compartidos
├── feature/
│   ├── tracks/       api, repository, viewmodel, screen
│   ├── profiles/     api, repository, paging, viewmodel, screen
│   └── lov/          catálogos (se cachean y casi no cambian)
└── di/               módulos de Hilt
```

Si quieres ver todo esto en una app grande, de verdad y mantenida por Google:
[Now in Android](https://github.com/android/nowinandroid) es la referencia, y
[architecture-samples](https://github.com/android/architecture-samples) la versión mínima.

---

## 6. Jetpack Compose de cero

Compose es declarativo: describes **qué** se ve para un estado dado, y el framework se encarga de
redibujar cuando ese estado cambia. No hay `findViewById`, ni XML de layout, ni adaptadores.

Documentación de partida: [Compose](https://developer.android.com/develop/ui/compose/documentation),
el codelab [Jetpack Compose basics](https://developer.android.com/codelabs/jetpack-compose-basics) y,
si empiezas de cero en Android, el curso completo
[Android Basics with Compose](https://developer.android.com/courses/android-basics-compose/course).

### 6.1 Un composable

```kotlin
@Composable
fun TrackRow(track: Track, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(track.name) },
        supportingContent = { Text("EP ${track.executionPackageId ?: "-"}") },
        trailingContent = { if (!track.enabled) AssistChip(onClick = {}, label = { Text("Baja") }) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
```

- Una función `@Composable` **no devuelve** nada: emite UI.
- `Modifier` es la cadena de decoración (tamaño, relleno, clic, semántica). El orden importa:
  `padding().background()` no pinta lo mismo que `background().padding()`.
- Los nombres van en `PascalCase` porque, conceptualmente, son componentes.

### 6.2 Estado y recomposición

Cuando cambia un estado que un composable lee, Compose vuelve a ejecutar esa función: es la
**recomposición**. Para que un valor sobreviva a esa reejecución hay que recordarlo:

```kotlin
var texto by remember { mutableStateOf("") }        // sobrevive a la recomposición
var texto by rememberSaveable { mutableStateOf("") } // además sobrevive al giro de pantalla
```

Regla de oro: **eleva el estado** (*state hoisting*). El composable recibe el valor y una función
para cambiarlo, y así es reutilizable y se puede previsualizar y testear sin ViewModel:

```kotlin
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text("Buscar") })
}
```

Referencia: [estado en Compose](https://developer.android.com/develop/ui/compose/state) y
[*state hoisting*](https://developer.android.com/develop/ui/compose/state-hoisting).

### 6.3 ViewModel + `StateFlow`: el patrón de pantalla

El `ViewModel` sobrevive a la rotación y a la recreación de la actividad, y es donde vive el estado
de la pantalla. El patrón, idéntico en todas las pantallas de la app:

```kotlin
// 1) El estado, cerrado y exhaustivo
data class TracksUiState(
    val isLoading: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val error: String? = null,
)

// 2) El ViewModel: expone estado, recibe eventos
@HiltViewModel
class TracksViewModel @Inject constructor(
    private val repository: TrackRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TracksUiState())
    val uiState: StateFlow<TracksUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {                       // se cancela solo al morir el ViewModel
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.tracks() }
                .onSuccess { tracks -> _uiState.update { it.copy(isLoading = false, tracks = tracks) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.userMessage()) } }
        }
    }
}

// 3) La pantalla: observa con conciencia del ciclo de vida
@Composable
fun TracksScreen(
    onTrackClick: (Track) -> Unit,
    viewModel: TracksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        state.error != null -> ErrorState(message = state.error!!, onRetry = viewModel::refresh)
        else -> LazyColumn {
            items(state.tracks, key = { it.id }) { track ->
                TrackRow(track) { onTrackClick(track) }
            }
        }
    }
}
```

Detalles que no son cosméticos:

- **`collectAsStateWithLifecycle()`**, no `collectAsState()`: el primero deja de recolectar cuando
  la pantalla se va a segundo plano, y eso es batería y datos móviles. Viene en
  `androidx.lifecycle:lifecycle-runtime-compose` — añádelo al catálogo si el asistente no lo puso
  ([producción de estado de UI](https://developer.android.com/topic/architecture/ui-layer/state-production)).
- **`key = { it.id }`** en `items`: sin eso, `LazyColumn` reusa filas por posición y una lista que
  cambia de orden parpadea o pierde el estado de sus elementos.
- **`LazyColumn`**, no `Column` con scroll: solo compone lo visible. Con 11.715 perfiles la
  diferencia no es sutil ([listas](https://developer.android.com/develop/ui/compose/lists)).

### 6.4 Tema y Material 3

El asistente genera `ui/theme/` con `MtoFieldTheme`. Usa
[Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3): colores,
tipografías y componentes coherentes, y modo oscuro gratis. Toca solo `Color.kt` y `Type.kt`.

Para una app de campo (guantes, sol, casco) merece la pena subir la escala tipográfica y el tamaño
mínimo de los objetivos táctiles: **48 dp** es el mínimo de accesibilidad
([accesibilidad en Compose](https://developer.android.com/develop/ui/compose/accessibility)).

Desde Android 15 las apps se dibujan **de borde a borde** por obligación con `targetSdk` alto: usa
`Scaffold` y respeta sus `innerPadding`, o la barra de estado te tapará contenido
([edge-to-edge](https://developer.android.com/develop/ui/compose/system/edge-to-edge)).

### 6.5 Navegación con rutas tipadas

```kotlin
@Serializable data object TracksRoute
@Serializable data class ProfilesRoute(val trackId: Long, val trackName: String)
@Serializable data class ProfileDetailRoute(val profileId: Long)

@Composable
fun MtoNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController, startDestination = TracksRoute) {

        composable<TracksRoute> {
            TracksScreen(onTrackClick = { navController.navigate(ProfilesRoute(it.id, it.name)) })
        }

        composable<ProfilesRoute> { entry ->
            val route: ProfilesRoute = entry.toRoute()
            ProfilesScreen(
                trackName = route.trackName,
                onProfileClick = { navController.navigate(ProfileDetailRoute(it.id)) },
            )
        }

        composable<ProfileDetailRoute> { ProfileDetailScreen(onBack = navController::navigateUp) }
    }
}
```

Y en el ViewModel, los argumentos se recuperan sin pasar por el composable:

```kotlin
@HiltViewModel
class ProfilesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProfileRepository,
) : ViewModel() {
    private val route: ProfilesRoute = savedStateHandle.toRoute()
    // route.trackId ya está disponible aquí
}
```

Las rutas tipadas (`@Serializable`, sin cadenas con `{placeholders}`) son lo recomendado desde
Navigation 2.8: [type safety in Navigation Compose](https://developer.android.com/guide/navigation/design/type-safety).
Existe además [Navigation 3](https://developer.android.com/guide/navigation/navigation-3), la
reescritura pensada para Compose; comprueba en qué estado está antes de apostar por ella en una app
que va a producción.

### 6.6 Previsualizar sin compilar la app entera

```kotlin
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TrackRowPreview() {
    MtoFieldTheme {
        TrackRow(Track(1, "VIA 1", enabled = true, executionPackageId = 4)) {}
    }
}
```

El panel de *Preview* renderiza sin desplegar; con *Live Edit* los cambios se ven mientras
escribes ([previews](https://developer.android.com/develop/ui/compose/tooling/previews)). Es la
razón principal por la que conviene que los composables reciban datos y no ViewModels.

---

## 7. La capa de red: Retrofit + kotlinx.serialization

[Retrofit](https://square.github.io/retrofit/) convierte una interfaz Kotlin en un cliente HTTP, y
[kotlinx.serialization](https://kotlinlang.org/docs/serialization.html) hace el JSON. Es la
combinación estándar en Android; si algún día compartes código con iOS, la alternativa es Ktor
Client (§17).

### 7.1 El `Json` de la app — y la trampa que borra datos

```kotlin
// core/network/Serialization.kt
val MtoJson = Json {
    ignoreUnknownKeys = true   // el backend añade campos sin avisar y la app no debe romperse
    explicitNulls = true       // un null nuestro viaja como null explícito. IMPRESCINDIBLE (abajo)
    encodeDefaults = true      // IMPRESCINDIBLE: sin esto, un campo con valor por defecto NO se envía
    coerceInputValues = false
}
```

> ⚠️ **`encodeDefaults = true` no es una preferencia de estilo: aquí protege los datos.**
> Por defecto, kotlinx.serialization **omite** del JSON toda propiedad cuyo valor sea igual a su
> valor por defecto. Y esta API interpreta una colección de hijos **ausente** como *«el padre no
> tiene ninguno»*, es decir, **borra los hijos** (§11.1 y `README_API.md` §4). Con los valores por
> defecto activados y `null` como valor por defecto de las colecciones, un `PUT` que no mencione
> las ménsulas manda `"cantilevers": null`, que es lo único que significa *«de esta colección no
> digo nada»*. Con la configuración por omisión mandaría el campo ausente, y el servidor leería
> `[]`. Tres ménsulas borradas por una línea de configuración.

### 7.2 Decimales: `BigDecimal`, nunca `Double`

`kp`, `span`, `cwHeight`, `stagger`... son `BigDecimal` en el servidor y viajan como **número JSON
conservando la escala** (`10.500`, no `10.5`). Y la escala importa: desde `V18` **el `kp` forma
parte de la clave natural del perfil**, porque una vía puede llevar dos tramos con la kilometración
reiniciada y el mismo identificador existe dos veces. Un `PUT` con el `kp` cambiado **no modifica:
crea otro perfil**.

`Double` redondea y pierde el cero final. Así que se serializa con `BigDecimal` y un serializador
propio:

```kotlin
// core/network/BigDecimalSerializer.kt
@OptIn(ExperimentalSerializationApi::class)
object BigDecimalSerializer : KSerializer<BigDecimal> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.math.BigDecimal", PrimitiveKind.STRING)

    // Acepta tanto 10.500 como "10.500": el servidor emite número, pero Jackson admite ambas.
    override fun deserialize(decoder: Decoder): BigDecimal {
        val json = decoder as? JsonDecoder ?: error("BigDecimalSerializer solo funciona con JSON")
        return BigDecimal(json.decodeJsonElement().jsonPrimitive.content)
    }

    // Se emite SIN comillas y con toPlainString(): mantiene la escala y evita la notación científica.
    override fun serialize(encoder: Encoder, value: BigDecimal) {
        val json = encoder as? JsonEncoder ?: error("BigDecimalSerializer solo funciona con JSON")
        json.encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))
    }
}
```

Y en la cabecera del fichero de DTOs, para no anotar campo por campo:

```kotlin
@file:UseSerializers(BigDecimalSerializer::class)
```

### 7.3 Los DTOs

Traducción directa de los DTOs del servidor. Todo opcional y con valor por defecto `null`: la API
devuelve muchos campos vacíos y acepta cuerpos parciales.

```kotlin
// feature/profiles/data/ProfileDto.kt
@file:UseSerializers(BigDecimalSerializer::class)

@Serializable
data class ProfileDto(
    // --- comunes a todos los DTO (BaseDTO)
    val id: Long? = null,
    val createUser: String? = null,
    val createDate: String? = null,      // ISO-8601 sin zona: "2026-08-31T10:15:00"
    val versionUser: String? = null,
    val versionDate: String? = null,
    val versionNumber: Int? = null,      // control de concurrencia optimista (§11.2)

    // --- propios del perfil
    val profileId: String? = null,
    val kp: BigDecimal? = null,
    val orderInTrack: Int? = null,
    val span: BigDecimal? = null,                    // vano HASTA el perfil siguiente, en metros
    val heightCantileverSupport: BigDecimal? = null, // mm, sin decimales
    val poleGaugeLocation: BigDecimal? = null,       // mm, sin decimales
    val railPoleDistance: BigDecimal? = null,        // mm, CON signo: a qué lado de la vía cae el poste
    val trackId: Long? = null,

    // --- relación 1:1: mandar el objeto lo crea/actualiza, mandar null lo desvincula
    val disconnector: DisconnectorDto? = null,

    // --- colecciones de hijos: null = "no digo nada". Ver §11.1 antes de tocarlas.
    val cantilevers: List<CantileverDto>? = null,

    // --- listas de valores: basta el código, el servidor lo resuelve contra el catálogo
    val anchorages: List<LovDto>? = null,            // N:M
    val sectionings: List<LovDto>? = null,           // N:M
    val sectioningFeedings: List<LovDto>? = null,    // N:M, catálogo DisconnectorFunction
    val anchorageFoundation: LovDto? = null,
    val foundation: LovDto? = null,
    val poleType: LovDto? = null,
    val portal: LovDto? = null,
    val profileStatus: LovDto? = null,
    val returnSupport: LovDto? = null,
    val supportType: LovDto? = null,
    val assemblyConfiguration: LovDto? = null,
)

@Serializable
data class LovDto(
    val id: Long? = null,
    val code: String? = null,
    val description: String? = null,
    val enabled: Boolean = true,
)

@Serializable
data class CantileverDto(
    val id: Long? = null,
    val versionNumber: Int? = null,
    val cwHeight: BigDecimal? = null,
    val stagger: BigDecimal? = null,
    val catenaryHeight: BigDecimal? = null,
    val cwElevation: BigDecimal? = null,
    val windDeflection: BigDecimal? = null,
    val armAngle: BigDecimal? = null,
    val cantileverType: LovDto? = null,
    val profileId: Long? = null,
    val steadyArm: SteadyArmDto? = null,
)

@Serializable
data class TrackDto(
    val id: Long? = null,
    val versionNumber: Int? = null,
    val name: String? = null,
    val enabled: Boolean? = null,
    val executionPackageId: Long? = null,
    val stationIds: List<Long>? = null,   // desde V17: una vía atraviesa VARIAS estaciones
    val profiles: List<ProfileDto>? = null,
)
```

> Al referenciar una LOV basta el `code`: `{"poleType": {"code": "PT1"}}`. Lo que mandes en
> `description` se ignora — manda el catálogo, no la petición. Y un código que el catálogo no
> tenga **no rompe la petición: se ignora en silencio**, así que valida los códigos en la app
> contra el catálogo que ya te has descargado (§12).

### 7.4 La página de Spring Data

`/{recurso}/paged`, `/filter` y `/search` devuelven la página de Spring Data tal cual:

```kotlin
@Serializable
data class SpringPage<T>(
    val content: List<T> = emptyList(),
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val number: Int = 0,          // índice de página, base 0
    val size: Int = 0,
    val first: Boolean = true,
    val last: Boolean = true,
    val numberOfElements: Int = 0,
    val empty: Boolean = true,
)
```

`ignoreUnknownKeys` se traga `pageable` y `sort`, que no aportan nada al cliente.

> Si un día el backend activa `spring.data.web.pageable.serialization-mode=VIA_DTO`, el sobre pasa
> a ser `{ "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }`. Hoy no
> está activado. La forma real siempre se puede comprobar en `/v3/api-docs` o pidiendo la página
> con `curl`.

### 7.5 Las interfaces de Retrofit

```kotlin
interface ProfileApi {

    @GET("profiles/{id}")
    suspend fun byId(@Path("id") id: Long): ProfileDto

    @GET("profiles/paged")
    suspend fun paged(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: String? = null,          // "kp,desc"
    ): SpringPage<ProfileDto>

    @POST("profiles/filter")
    suspend fun filter(
        @Body filter: ProfileFilterDto,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): SpringPage<ProfileDto>

    // Ventana por cursor: no pagina por offset, así que recorrer una vía entera no degrada
    @GET("profiles/track/{trackId}/keyset")
    suspend fun keyset(
        @Path("trackId") trackId: Long,
        @Query("lastKp") lastKp: BigDecimal? = null,
        @Query("lastId") lastId: Long? = null,
        @Query("pageSize") pageSize: Int = 50,
    ): List<ProfileDto>

    @GET("profiles/track/{trackId}/range")
    suspend fun range(
        @Path("trackId") trackId: Long,
        @Query("startKp") startKp: BigDecimal,
        @Query("endKp") endKp: BigDecimal,
    ): List<ProfileDto>

    @POST("profiles")
    suspend fun create(@Body dto: ProfileDto): ProfileDto

    @PUT("profiles/{id}")
    suspend fun update(@Path("id") id: Long, @Body dto: ProfileDto): ProfileDto

    @DELETE("profiles/{id}")
    suspend fun delete(@Path("id") id: Long)          // 204 → Unit
}

@Serializable
data class ProfileFilterDto(
    val profileId: String? = null,
    val trackId: Long? = null,
    val trackName: String? = null,
    val stationName: String? = null,
    val profileStatusCode: String? = null,
    val poleTypeCode: String? = null,
    val sectioningCode: String? = null,   // ojo: significa "tiene X ENTRE los suyos" (relación N:M)
    val anchorageCode: String? = null,
    val searchText: String? = null,       // busca a la vez en identificador, vía y estación
)
```

Dos cosas que hacen perder una tarde la primera vez:

- La **`baseUrl` termina en `/`** y las rutas de los métodos **no empiezan por `/`**. Con
  `baseUrl = ".../api/v1/configuration/"` y `@GET("profiles/paged")` sale
  `.../api/v1/configuration/profiles/paged`. Si pones `@GET("/profiles/paged")` con barra inicial,
  Retrofit la trata como absoluta y se come el `/api/v1/configuration`.
- Los **parámetros nulos no se envían**: `@Query("lastKp") lastKp: BigDecimal? = null` simplemente
  desaparece de la URL, que es justo lo que el endpoint de cursor espera en la primera ventana.
- En un `@Query`, Retrofit usa `toString()`, **no** el serializador de §7.2. Para `BigDecimal` da
  igual con los valores de esta API, pero si alguna vez ves notación científica en la URL, pasa el
  parámetro como `String` con `toPlainString()`.

### 7.6 El cliente OkHttp y las instancias de Retrofit

```kotlin
// di/NetworkModule.kt
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun json(): Json = MtoJson

    @Provides @Singleton
    fun okHttp(authInterceptor: AuthInterceptor): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)                   // pone el Bearer (§8.6)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            }
        }
        // En campo la cobertura va y viene: mejor fallar pronto y reintentar que colgarse un minuto
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)                  // termina en "/"
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton fun profileApi(retrofit: Retrofit): ProfileApi = retrofit.create()
    @Provides @Singleton fun trackApi(retrofit: Retrofit): TrackApi = retrofit.create()
}
```

> ⚠️ **Nunca registres el interceptor de logs en release.** Ahí van los `Authorization: Bearer ...`
> completos, y acaban en el `logcat` de cualquiera que enchufe el móvil por USB. El
> `if (BuildConfig.DEBUG)` no es decorativo: en release esa constante es `false` en tiempo de
> compilación, así que R8 elimina la rama entera y con ella la librería.
>
> La alternativa, si quieres que ni siquiera esté el código, es declarar el interceptor en un módulo
> de Hilt dentro de `src/debug/java/` y otro vacío en `src/release/java/`; entonces sí puede ir por
> `debugImplementation`. Lo que **no** funciona es `debugImplementation` con el `HttpLoggingInterceptor`
> referenciado desde `src/main/`: la release no compila.

Cuando toque hablar con `mto-stock` o con el gateway, se repite `retrofit(...)` con otra `baseUrl` y
un [`@Qualifier`](https://developer.android.com/training/dependency-injection/hilt-android#multiple-bindings)
propio. El `OkHttpClient` —y por tanto el pool de conexiones y la sesión— se comparte.

### 7.7 Errores: de `problem+json` a algo con lo que se pueda programar

```kotlin
// core/network/ApiProblem.kt
@Serializable
data class ApiProblem(
    val type: String? = null,
    val title: String? = null,
    val status: Int = 0,
    val detail: String? = null,
    val instance: String? = null,
    val code: String? = null,          // VAL-000, NOT-001, CON-001, TEC-999: estable, úsalo tú
    val timestamp: String? = null,
    val traceId: String? = null,       // lo que hay que pegar en la incidencia
    val retryable: Boolean = false,
    val errors: List<ApiFieldError> = emptyList(),
)

@Serializable
data class ApiFieldError(
    val field: String? = null,         // ruta navegable: "cantilevers[1].cwHeight"
    val code: String? = null,
    val message: String? = null,
)

sealed interface MtoError {
    data class Validation(val problem: ApiProblem) : MtoError
    data object Unauthorized : MtoError                                  // 401
    data class Forbidden(val problem: ApiProblem?) : MtoError            // 403: falta el rol
    data class NotFound(val problem: ApiProblem?) : MtoError             // 404
    data class Conflict(val problem: ApiProblem?) : MtoError             // 409: versionNumber viejo
    data class Throttled(val retryAfterSeconds: Long?) : MtoError        // 429
    data class Network(val cause: IOException) : MtoError                // sin cobertura
    data class Unexpected(val problem: ApiProblem?, val cause: Throwable?) : MtoError
}

class MtoException(val error: MtoError) : Exception(error.toString())

// core/network/SafeApiCall.kt
suspend fun <T> safeApiCall(json: Json = MtoJson, block: suspend () -> T): T =
    try {
        block()
    } catch (e: HttpException) {
        val body = e.response()?.errorBody()?.string()
        val problem = body?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<ApiProblem>(it) }.getOrNull() }
        throw MtoException(
            when (e.code()) {
                400 -> problem?.let(MtoError::Validation) ?: MtoError.Unexpected(null, e)
                401 -> MtoError.Unauthorized
                403 -> MtoError.Forbidden(problem)
                404 -> MtoError.NotFound(problem)
                409 -> MtoError.Conflict(problem)
                429 -> MtoError.Throttled(e.response()?.headers()?.get("Retry-After")?.toLongOrNull())
                else -> MtoError.Unexpected(problem, e)
            }
        )
    } catch (e: IOException) {
        throw MtoException(MtoError.Network(e))           // sin red: NO es culpa del usuario
    }
```

A partir de aquí, el repositorio envuelve cada llamada y la UI recibe errores con los que puede
hacer algo concreto:

```kotlin
class ProfileRepository @Inject constructor(private val api: ProfileApi) {
    suspend fun byId(id: Long): Profile = safeApiCall { api.byId(id) }.toDomain()
}
```

El texto que ve el usuario se decide en un solo sitio, y **por `code`, no por el `message` del
servidor**: el texto puede cambiar entre versiones, el código es estable.

```kotlin
// core/network/UserMessages.kt
fun MtoError.userMessage(): String = when (this) {
    is MtoError.Network      -> "Sin conexión. El cambio se guardará y se enviará al recuperar cobertura."
    MtoError.Unauthorized    -> "Tu sesión ha caducado. Vuelve a entrar."
    is MtoError.Forbidden    -> "No tienes permiso para esta operación."
    is MtoError.NotFound     -> "El registro ya no existe. Refresca la lista."
    is MtoError.Conflict     -> "Otra persona ha guardado antes que tú. Revisa los cambios."
    is MtoError.Throttled    -> "El servidor está saturado. Inténtalo en unos segundos."
    is MtoError.Validation   -> problem.detail ?: "Revisa los campos marcados."
    is MtoError.Unexpected   -> "Error inesperado" + (problem?.traceId?.let { " (traza $it)" } ?: "")
}

fun Throwable.userMessage(): String = (this as? MtoException)?.error?.userMessage() ?: "Error inesperado"
```

Y los errores de validación se convierten en un mapa que el formulario consulta campo a campo:

```kotlin
fun ApiProblem.fieldErrors(): Map<String, String> =
    errors.mapNotNull { fe -> fe.field?.let { it to (fe.message ?: "Valor no válido") } }.toMap()
// "cantilevers[1].cwHeight" -> "Debe ser mayor que cero"
```

---

## 8. Autenticación con Keycloak

### 8.1 Por qué AppAuth y no pedir usuario y contraseña

La tentación es poner dos campos y llamar a `/protocol/openid-connect/token` con
`grant_type=password`. **No.** Ese flujo (*Resource Owner Password Credentials*) está desaconsejado
y retirado en OAuth 2.1: obliga a la app a ver la contraseña, no admite MFA, no admite federación y
deja de funcionar el día que el realm active cualquiera de las dos cosas. Aquí además está cerrado
a propósito fuera de local (`directAccessGrantsEnabled`).

Lo correcto para una app nativa es
[RFC 8252](https://datatracker.ietf.org/doc/html/rfc8252): **Authorization Code + PKCE**, abriendo
el login en el navegador del sistema (Custom Tabs) y volviendo a la app por un esquema propio. La
librería de referencia, mantenida por la OpenID Foundation, es
[AppAuth-Android](https://github.com/openid/AppAuth-Android); implementa PKCE, el manejo del
`state`, el intercambio del código y el refresco.

Lo que gana la app: nunca ve la contraseña, hereda MFA y SSO del realm, y si mañana entra un IdP
corporativo no hay que tocar nada.

### 8.2 Lo que hay que añadir al realm

Hoy el realm `mto` tiene tres clientes: `mto-configuration-api` (la audiencia y los permisos),
`mto-configuration-svc` (cuenta de servicio) y `mto-frontend` (el navegador). **Ninguno sirve para
el móvil**: `mto-frontend` es un cliente de navegador con `redirectUris` de `http://localhost:4200`
y orígenes web, y un esquema `com.alejandro.mtomobile:/...` no encaja ahí.

Hace falta un cliente público nuevo, `mto-mobile`:

```jsonc
{
  "clients": [
    {
      "clientId": "mto-mobile",
      "name": "MTO — app móvil de campo",
      "protocol": "openid-connect",
      "publicClient": true,              // una app instalada no puede guardar un secreto
      "standardFlowEnabled": true,       // authorization code
      "implicitFlowEnabled": false,
      "directAccessGrantsEnabled": false,
      "serviceAccountsEnabled": false,
      "redirectUris": ["com.alejandro.mtomobile:/oauth2redirect"],
      "webOrigins": [],
      "attributes": {
        "pkce.code.challenge.method": "S256",
        "post.logout.redirect.uris": "com.alejandro.mtomobile:/logout"
      },
      "protocolMappers": [
        {
          "name": "audiencia-mto-configuration-api",
          "protocol": "openid-connect",
          "protocolMapper": "oidc-audience-mapper",
          "config": {
            "included.client.audience": "mto-configuration-api",
            "access.token.claim": "true",
            "id.token.claim": "false"
          }
        }
        // un mapper más por cada API que la app vaya a llamar: mto-stock-api, mto-gateway-api...
      ]
    }
  ]
}
```

Tres avisos sobre dónde vive esto:

- **`publicClient: true` sin secreto es lo correcto**, y por eso PKCE es obligatorio: es lo único
  que impide que otra app registrada con el mismo esquema intercepte el código.
- **El *audience mapper* no es opcional.** Sin él, el token no lleva `mto-configuration-api` en
  `aud` y la API lo rechaza: `audience-validation-enabled` está activo a propósito, para que el
  token del frontal de otra aplicación no valga aquí.
- **Este fichero no va en `mto-configuration`.** Los clientes de frontal viven en el realm base de
  [`mto-platform`](https://github.com/alexwarrior1991/mto-platform) (aquí solo están la API y la
  cuenta de servicio, ver [`keycloak/README.md`](keycloak/README.md)). Mientras desarrollas puedes
  crearlo a mano en la consola (`Clients ▸ Create client`), pero el sitio definitivo es el
  repositorio de plataforma, junto a `mto-frontend`.

Documentación de Keycloak para clientes nativos:
[Securing applications](https://www.keycloak.org/docs/latest/securing_apps/index.html).

### 8.3 El manifiesto

AppAuth trae su propia actividad de recepción; solo hay que decirle el esquema, y eso ya lo hiciste
en §4.4 con `manifestPlaceholders["appAuthRedirectScheme"]`. Lo único que falta en
`AndroidManifest.xml` es el permiso de red:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

> El esquema debe ser **único**: el identificador del paquete al revés o tal cual
> (`com.alejandro.mtomobile`) sirve. No uses `mtoapp://` ni nada genérico: si dos apps declaran el
> mismo esquema, Android deja elegir al usuario y el código de autorización puede acabar en la
> aplicación equivocada.

### 8.4 El repositorio de autenticación

```kotlin
// core/auth/AuthRepository.kt
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: TokenStore,
) {
    private val authService = AuthorizationService(context)

    /** Estado completo de la sesión: configuración del emisor, tokens y caducidades. */
    private val stateFlow = MutableStateFlow(AuthState())
    val isLoggedIn: Flow<Boolean> = stateFlow.map { it.isAuthorized }

    suspend fun restore() {
        tokenStore.read()?.let { stateFlow.value = AuthState.jsonDeserialize(it) }
    }

    /** Descubre los endpoints reales del realm en vez de cablearlos. */
    private suspend fun serviceConfig(): AuthorizationServiceConfiguration =
        suspendCancellableCoroutine { cont ->
            AuthorizationServiceConfiguration.fetchFromIssuer(BuildConfig.OIDC_ISSUER.toUri()) { config, ex ->
                when {
                    config != null -> cont.resume(config)
                    else -> cont.resumeWithException(ex ?: IllegalStateException("Sin configuración OIDC"))
                }
            }
        }

    /** Intent que abre el login en el navegador del sistema. */
    suspend fun authorizationIntent(): Intent {
        val request = AuthorizationRequest.Builder(
            serviceConfig(),
            CLIENT_ID,
            ResponseTypeValues.CODE,
            REDIRECT_URI.toUri(),
        )
            // offline_access es lo que hace que Keycloak emita refresh token: sin él,
            // el operario vuelve a teclear la contraseña cada vez que caduca el access token.
            .setScopes("openid", "profile", "email", "offline_access")
            .build()                      // AppAuth genera el code_verifier y el challenge S256

        return authService.getAuthorizationRequestIntent(request)
    }

    /** Vuelta del navegador: canjea el código por tokens. */
    suspend fun onAuthorizationResult(data: Intent) {
        val response = AuthorizationResponse.fromIntent(data)
        val exception = AuthorizationException.fromIntent(data)
        stateFlow.value.update(response, exception)

        if (response == null) throw exception ?: IllegalStateException("Login cancelado")

        val tokens = suspendCancellableCoroutine { cont ->
            authService.performTokenRequest(response.createTokenExchangeRequest()) { result, ex ->
                when {
                    result != null -> cont.resume(result)
                    else -> cont.resumeWithException(ex ?: IllegalStateException("Sin token"))
                }
            }
        }
        stateFlow.value.update(tokens, null)
        persist()
    }

    /**
     * Token vigente. AppAuth refresca solo si hace falta; si el refresh token ha caducado o el
     * usuario fue expulsado del realm, devuelve error y hay que volver al login.
     */
    suspend fun freshAccessToken(): String? = suspendCancellableCoroutine { cont ->
        stateFlow.value.performActionWithFreshTokens(authService) { accessToken, _, ex ->
            if (ex != null) cont.resumeWithException(ex) else cont.resume(accessToken)
        }
    }.also { persist() }                  // el estado cambia al refrescar: hay que guardarlo

    suspend fun logout() {
        tokenStore.clear()
        stateFlow.value = AuthState()
    }

    private suspend fun persist() = tokenStore.write(stateFlow.value.jsonSerializeString())

    private companion object {
        const val CLIENT_ID = "mto-mobile"
        const val REDIRECT_URI = "com.alejandro.mtomobile:/oauth2redirect"
    }
}
```

Y el lanzamiento desde la pantalla de login:

```kotlin
@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.let(viewModel::onAuthorizationResult)
    }
    Button(onClick = { viewModel.startLogin(launcher::launch) }) { Text("Entrar con tu cuenta MTO") }
}
```

### 8.5 Dónde se guardan los tokens

En `SharedPreferences` en claro, **no**: cualquier copia de seguridad o un dispositivo con root los
expone. `EncryptedSharedPreferences` tampoco: la librería *Jetpack Security* (`androidx.security:
security-crypto`) está [deprecada desde 2025](https://developer.android.com/jetpack/androidx/releases/security)
y remite a usar el **Android Keystore** directamente.

La receta vigente: una clave AES-256/GCM que vive en el Keystore (no sale del dispositivo, y en
móviles con *StrongBox* ni siquiera de su chip seguro) y el texto cifrado en
[DataStore](https://developer.android.com/topic/libraries/architecture/datastore).

```kotlin
// core/auth/TokenStore.kt
private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore("auth")

@Singleton
class TokenStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val payload = stringPreferencesKey("auth_state")

    suspend fun write(value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(value.toByteArray())
        // El IV se genera en cada cifrado y se guarda delante: reutilizarlo con GCM rompe el cifrado.
        val blob = Base64.getEncoder().encodeToString(cipher.iv + encrypted)
        context.authDataStore.edit { it[payload] = blob }
    }

    suspend fun read(): String? {
        val blob = context.authDataStore.data.first()[payload] ?: return null
        val bytes = Base64.getDecoder().decode(blob)
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        }
        return String(cipher.doFinal(bytes.copyOfRange(IV_BYTES, bytes.size)))
    }

    suspend fun clear() {
        context.authDataStore.edit { it.remove(payload) }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "mto_auth_state"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
```

Si la app maneja datos sensibles de verdad, el siguiente paso es
`setUserAuthenticationRequired(true)` en la clave: exige huella o PIN para descifrarla
([BiometricPrompt](https://developer.android.com/training/sign-in/biometric-auth)). Y en cualquier
caso, `android:allowBackup="false"` en el manifiesto para que el estado de sesión no viaje a la
nube.

### 8.6 El interceptor que pone el token

```kotlin
// core/network/AuthInterceptor.kt
@Singleton
class AuthInterceptor @Inject constructor(
    private val authRepository: AuthRepository,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        // Los interceptores de OkHttp ya corren en un hilo de background: runBlocking aquí
        // no bloquea la UI, y es la forma de cruzar del mundo suspend al síncrono de OkHttp.
        val token = runBlocking { runCatching { authRepository.freshAccessToken() }.getOrNull() }

        val request = chain.request().newBuilder()
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .header("Accept", "application/json")
            .build()

        return chain.proceed(request)
    }
}
```

AppAuth refresca por su cuenta cuando el token está a punto de caducar, así que no hace falta un
`Authenticator` para el `401` salvo para un caso: que el refresh token también haya muerto (sesión
revocada, usuario deshabilitado). Eso se resuelve arriba, no en la red — el `MtoError.Unauthorized`
de §7.7 llega al ViewModel raíz y este manda a la pantalla de login.

### 8.7 Roles: qué enseña la app

El token trae los permisos expandidos dentro de `resource_access`:

```json
{
  "resource_access": {
    "mto-configuration-api": { "roles": ["config-read", "config-write", "config-import"] }
  },
  "realm_access": { "roles": ["mto-editor"] },
  "preferred_username": "config.editor"
}
```

Leerlos en el cliente es leer el segundo segmento del JWT. **No hay que validar la firma**: eso lo
hace el servidor. Aquí solo sirve para decidir qué botones se pintan.

```kotlin
// core/auth/TokenClaims.kt
fun permissionsOf(accessToken: String, clientId: String = "mto-configuration-api"): Set<String> {
    val payload = accessToken.split('.').getOrNull(1) ?: return emptySet()
    val decoded = String(Base64.getUrlDecoder().decode(payload))
    return Json.parseToJsonElement(decoded).jsonObject["resource_access"]
        ?.jsonObject?.get(clientId)
        ?.jsonObject?.get("roles")
        ?.jsonArray?.map { it.jsonPrimitive.content }
        ?.toSet() ?: emptySet()
}

data class Permissions(val values: Set<String>) {
    val canRead   get() = "config-read" in values
    val canWrite  get() = "config-write" in values
    val canDelete get() = "config-delete" in values
    val canImport get() = "config-import" in values
    val canManageLov get() = "lov-manage" in values
}
```

```kotlin
if (permissions.canWrite) {
    FloatingActionButton(onClick = onNewProfile) { Icon(Icons.Default.Add, "Nuevo perfil") }
}
```

> **La UI oculta; el servidor decide.** Esconder el botón es cortesía con el usuario, no seguridad:
> la API vuelve a comprobar el rol en cada petición y responde `403`. Nunca al revés — jamás
> mandes desde la app un campo del tipo `"esUsuarioAdmin": true`.

---

## 9. Llegar al backend local desde el móvil

Aquí es donde se atasca todo el mundo el primer día. Son tres problemas distintos y conviene no
mezclarlos.

### 9.1 Problema 1: `localhost` no es tu máquina

Dentro del emulador, `localhost` es **el propio emulador**. Tu PC es **`10.0.2.2`**
([redes del emulador](https://developer.android.com/studio/run/emulator-networking)).

| Dónde corre la app | URL del backend (host 8081) |
|---|---|
| Emulador | `http://10.0.2.2:8081/api/v1/configuration/` |
| Dispositivo físico en la misma wifi | `http://192.168.x.x:8081/api/v1/configuration/` (IP LAN del PC) |
| Dispositivo físico por USB | `adb reverse tcp:8081 tcp:8081` y luego `http://localhost:8081/...` |

### 9.2 Problema 2: el emisor del token es una cadena, no una dirección

El backend valida que el `iss` del token sea **exactamente**
`http://auth.mto.local:8082/realms/mto` (o lo que diga `KEYCLOAK_ISSUER_URI`), y AppAuth valida que
el emisor que descubrió coincida con el del token. Así que **la app tiene que usar la misma cadena
que el backend**, y esa cadena tiene que resolverse desde el dispositivo.

`auth.mto.local` lo resuelve tu PC por el fichero `hosts` (ver
[`README_LOCAL_DOCKER.md`](README_LOCAL_DOCKER.md) §3), pero el emulador tiene su propio DNS y no
lo hereda. Dos salidas:

1. **La buena para trabajar con el móvil**: levantar la plataforma con un emisor que todos puedan
   resolver — la IP LAN de tu PC — y usar esa misma cadena en los tres sitios:

   ```properties
   # mto-platform: Keycloak publica el realm con ese nombre
   KC_HOSTNAME=http://192.168.1.50:8082
   # mto-configuration: valida contra el mismo emisor
   KEYCLOAK_ISSUER_URI=http://192.168.1.50:8082/realms/mto
   ```

   ```kotlin
   // app, buildType debug
   buildConfigField("String", "OIDC_ISSUER", "\"http://192.168.1.50:8082/realms/mto\"")
   ```

2. **La rápida para salir del paso**: `10.0.2.2:8082` como emisor, con el backend configurado con
   `KEYCLOAK_ISSUER_URI=http://10.0.2.2:8082/realms/mto`. Funciona solo con el emulador, y hay que
   acordarse de que el backend en tu PC también tiene que poder resolver esa dirección.

> Si ves `401` con el mensaje de emisor inválido, el 90% de las veces es esto: la app pidió el
> token a un nombre y el backend espera otro. Compara el `iss` del token (pégalo en
> [jwt.io](https://jwt.io) o descodifícalo con `base64 -d`) con `KEYCLOAK_ISSUER_URI`.

### 9.3 Problema 3: Android bloquea HTTP en claro

Desde Android 9 el tráfico sin TLS está prohibido por defecto. En local todo es `http://`, así que
hay que permitirlo **solo en debug**:

```xml
<!-- app/src/debug/res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">10.0.2.2</domain>
        <domain includeSubdomains="true">192.168.1.50</domain>
        <domain includeSubdomains="true">auth.mto.local</domain>
    </domain-config>
</network-security-config>
```

```xml
<!-- app/src/debug/AndroidManifest.xml -->
<application android:networkSecurityConfig="@xml/network_security_config" />
```

Al estar en `src/debug/`, la release ni lo ve: ahí todo va por HTTPS y sin excepciones
([configuración de seguridad de red](https://developer.android.com/privacy-and-security/security-config)).

> **CORS no pinta nada aquí.** `app.security.cors.allowed-origins` es para el navegador; una app
> nativa no manda `Origin` y el servidor no le aplica esa comprobación. Lo que sí tiene que estar
> bien es el `redirectUri` registrado en Keycloak (§8.2). Es un error muy común llegar del mundo
> web y ponerse a tocar CORS cuando lo que falla es el *redirect*.

### 9.4 Comprobar antes de culpar a la app

```bash
# 1) ¿responde el backend?
curl -i http://localhost:8081/actuator/health

# 2) ¿emite Keycloak?  (directAccessGrants solo está abierto en local)
TOKEN=$(curl -s -X POST http://auth.mto.local:8082/realms/mto/protocol/openid-connect/token \
  -d grant_type=password -d client_id=mto-frontend \
  -d username=config.editor -d password=local | jq -r .access_token)

# 3) ¿pasa el token por la API?
curl -i "http://localhost:8081/api/v1/configuration/tracks/paged?size=1" \
  -H "Authorization: Bearer $TOKEN"
```

Si los tres van, el problema está en el dispositivo (red o emisor). Si falla el 2 o el 3, no pierdas
el tiempo en Android Studio.

---

## 10. Primera pantalla completa: vías y perfiles

Lo que sigue es el camino completo de una pantalla real: listado paginado de perfiles de una vía,
con búsqueda. Junta todo lo anterior.

### 10.1 Modelo de dominio y mapper

```kotlin
// feature/profiles/domain/Profile.kt
data class Profile(
    val id: Long,
    val profileId: String,
    val kp: BigDecimal?,
    val orderInTrack: Int?,
    val trackId: Long?,
    val statusCode: String?,
    val poleTypeCode: String?,
    val sectioningCodes: List<String>,
    val cantileverCount: Int,
    val versionNumber: Int?,      // se guarda: hace falta para escribir sin pisar a otro (§11.2)
)

fun ProfileDto.toDomain() = Profile(
    id = requireNotNull(id) { "Un perfil del servidor siempre trae id" },
    profileId = profileId.orEmpty(),
    kp = kp,
    orderInTrack = orderInTrack,
    trackId = trackId,
    statusCode = profileStatus?.code,
    poleTypeCode = poleType?.code,
    sectioningCodes = sectionings?.mapNotNull { it.code }.orEmpty(),
    cantileverCount = cantilevers?.size ?: 0,
    versionNumber = versionNumber,
)
```

### 10.2 Paginación con Paging 3

[Paging 3](https://developer.android.com/topic/libraries/architecture/paging/v3-overview) se encarga
de pedir páginas según el usuario baja, de reintentar y de los estados de carga.

```kotlin
// feature/profiles/data/ProfilePagingSource.kt
class ProfilePagingSource(
    private val api: ProfileApi,
    private val filter: ProfileFilterDto,
) : PagingSource<Int, Profile>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Profile> = try {
        val page = params.key ?: 0
        val response = safeApiCall { api.filter(filter, page = page, size = params.loadSize) }
        LoadResult.Page(
            data = response.content.map { it.toDomain() },
            prevKey = if (page == 0) null else page - 1,
            nextKey = if (response.last) null else page + 1,
        )
    } catch (e: MtoException) {
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<Int, Profile>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
}
```

```kotlin
// feature/profiles/data/ProfileRepository.kt
fun profiles(filter: ProfileFilterDto): Flow<PagingData<Profile>> = Pager(
    // initialLoadSize = pageSize a propósito: por defecto la primera carga pide el TRIPLE,
    // y con un API que pagina por índice de página los índices dejarían de cuadrar.
    config = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 5),
    pagingSourceFactory = { ProfilePagingSource(api, filter) },
).flow
```

### 10.3 ViewModel

```kotlin
@HiltViewModel
class ProfilesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: ProfileRepository,
) : ViewModel() {

    private val route: ProfilesRoute = savedStateHandle.toRoute()
    private val query = MutableStateFlow("")

    val profiles: Flow<PagingData<Profile>> = query
        .debounce(300)                                   // no una petición por tecla
        .distinctUntilChanged()
        .flatMapLatest { text ->
            repository.profiles(ProfileFilterDto(trackId = route.trackId, searchText = text.ifBlank { null }))
        }
        .cachedIn(viewModelScope)                        // sobrevive a la rotación

    fun onQueryChange(value: String) { query.value = value }
}
```

### 10.4 La pantalla

```kotlin
@Composable
fun ProfilesScreen(
    trackName: String,
    onProfileClick: (Profile) -> Unit,
    viewModel: ProfilesViewModel = hiltViewModel(),
) {
    val profiles = viewModel.profiles.collectAsLazyPagingItems()

    Scaffold(topBar = { TopAppBar(title = { Text(trackName) }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {

            items(profiles.itemCount, key = profiles.itemKey { it.id }) { index ->
                profiles[index]?.let { profile ->
                    ListItem(
                        headlineContent = { Text(profile.profileId) },
                        supportingContent = { Text("KP ${profile.kp?.toPlainString() ?: "-"}") },
                        trailingContent = { profile.statusCode?.let { AssistChip({}, { Text(it) }) } },
                        modifier = Modifier.clickable { onProfileClick(profile) },
                    )
                    HorizontalDivider()
                }
            }

            // Estados de carga: Paging los expone, no hay que inventarlos
            when (val append = profiles.loadState.append) {
                is LoadState.Loading -> item { LoadingRow() }
                is LoadState.Error -> item { RetryRow(append.error) { profiles.retry() } }
                else -> Unit
            }
        }
    }
}
```

> **El orden lo decide el servidor.** Los perfiles de una vía salen por `orderInTrack`, con `kp` e
> `id` de desempate, que es el orden físico a lo largo de la vía; los que no tienen posición
> conocida van al final. No los reordenes en el cliente por `kp`: una vía puede llevar dos tramos
> concatenados con la kilometración reiniciada, y ordenar por `kp` los mezcla
> (`README_API.md` §"En qué orden te llegan los hijos").

### 10.5 Recorrer una vía entera: paginación por cursor

Para exportar o sincronizar una vía completa, la paginación por offset degrada. El endpoint de
cursor no:

```kotlin
data class ProfileCursor(val lastKp: BigDecimal?, val lastId: Long?)

class ProfileKeysetPagingSource(
    private val api: ProfileApi,
    private val trackId: Long,
) : PagingSource<ProfileCursor, Profile>() {

    override suspend fun load(params: LoadParams<ProfileCursor>): LoadResult<ProfileCursor, Profile> = try {
        val cursor = params.key
        val items = safeApiCall {
            api.keyset(trackId, cursor?.lastKp, cursor?.lastId, pageSize = params.loadSize)
        }.map { it.toDomain() }

        LoadResult.Page(
            data = items,
            prevKey = null,                                     // este endpoint no va hacia atrás
            nextKey = items.lastOrNull()
                ?.takeIf { items.size == params.loadSize }       // si vino menos, se acabó la vía
                ?.let { ProfileCursor(it.kp, it.id) },
        )
    } catch (e: MtoException) {
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<ProfileCursor, Profile>): ProfileCursor? = null
}
```

> El cursor son **los dos valores juntos**: mandar solo `lastKp` se trata como primera ventana,
> porque dos perfiles pueden compartir KP. Y ojo: esta ruta y `/range` paginan por `kp`, no por
> `orderInTrack`, así que en una vía de dos tramos **no** coinciden con el recorrido físico.

---

## 11. Escritura: el apartado que cuesta datos

### 11.1 Las colecciones de hijos

Esto hay que entenderlo antes de escribir el primer `PUT`. Al modificar un padre, **la lista de
hijos que mandas es el estado final completo**, no una lista de cambios:

| Lo que mandas | Lo que hace el servidor |
|---|---|
| hijo **con `id`** que ya existe | `UPDATE` de esa fila |
| hijo **sin `id`** | `INSERT` de fila nueva |
| hijo que existe y **no mandas** | **`DELETE` de esa fila** |
| hijo con un `id` que no es de este padre | se ignora |

Aplica a `execution-packages → tracks, stations`, `stations → disconnectors, sectionInsulators`,
`tracks → profiles`, `profiles → cantilevers`, y anida a cualquier profundidad. La única excepción
es `stations → tracks`: ahí la vía que no mandas **se desliga** de la estación, no se borra (una
vía atraviesa varias estaciones).

En un móvil esto es especialmente peligroso, porque la tentación de mandar "solo lo que el operario
tocó" es enorme. Dos formas de perder datos, las dos fáciles de cometer en Kotlin:

```kotlin
// ❌ MAL: construir el cuerpo desde cero con lo que hay en el formulario
api.update(id, ProfileDto(profileId = form.profileId, kp = form.kp, trackId = form.trackId))
// cantilevers queda a null... y aquí SÍ funciona, pero solo porque el valor por defecto es null
// y el Json tiene encodeDefaults = true. Con la configuración por defecto de kotlinx.serialization
// el campo se omitiría, el servidor leería [] y BORRARÍA las ménsulas.

// ❌ PEOR: reenviar los hijos sin su id
val hijos = form.cantilevers.map { CantileverDto(cwHeight = it.cwHeight, stagger = it.stagger) }
// el servidor borra las tres filas existentes e inserta tres nuevas: cambian de id,
// pierden su histórico de auditoría y rompen cualquier referencia que apuntara a ellas
```

```kotlin
// ✅ BIEN: leer, modificar sobre lo leído, devolver entero
suspend fun saveKp(profileId: Long, nuevoKp: BigDecimal): Profile = safeApiCall {
    val actual = api.byId(profileId)                 // trae los hijos con sus id
    val modificado = actual.copy(kp = nuevoKp)       // copy: lo demás viaja intacto
    api.update(profileId, modificado)
}.toDomain()
```

`data class` + `copy()` es exactamente la herramienta para esto: el resto de campos —y los `id` de
los hijos— se conservan sin que haya que escribirlos. **Regla práctica: lee el recurso, modifica
sobre lo leído, devuélvelo entero. Ni construyas el cuerpo desde cero ni le quites campos.**

Cuando de verdad quieras decir *«de esta colección no digo nada»*, el valor es `null` explícito —
no una lista vacía, no el campo ausente:

```kotlin
val soloElPadre = actual.copy(kp = nuevoKp, cantilevers = null)   // deja los hijos intactos
```

Y si quieres borrar un hijo, lo que se manda es la lista **sin** él:

```kotlin
val sinLaTercera = actual.copy(cantilevers = actual.cantilevers?.filterNot { it.id == 3L })
```

> Comprueba esto con un test de red simulada (§15) el día que lo escribas, no el día que un operario
> se quede sin ménsulas. `MockWebServer` te deja afirmar sobre el **cuerpo exacto** que sale de la
> app: que `cantilevers` viaja como `null` y no como `[]`, y que los `id` siguen ahí.

### 11.2 Concurrencia: `versionNumber` y el `409`

Cada DTO trae `versionNumber`. Si mandas el que leíste y otro ha guardado mientras tanto, la API
responde **`409 CON-001`** en vez de pisar el cambio ajeno. En una app de campo, donde una pantalla
puede quedarse abierta media hora dentro de un túnel, esto pasa de verdad.

```kotlin
suspend fun save(profile: ProfileDto): SaveResult = try {
    SaveResult.Saved(safeApiCall { api.update(profile.id!!, profile) })
} catch (e: MtoException) {
    when (e.error) {
        is MtoError.Conflict -> SaveResult.Conflict(remoto = safeApiCall { api.byId(profile.id!!) })
        else -> throw e
    }
}
```

Con el remoto en la mano, la UI puede enseñar las dos versiones y dejar elegir. Lo que **no** debe
hacer es reintentar con el `versionNumber` nuevo sin preguntar: eso es exactamente lo que el
mecanismo existe para impedir.

### 11.3 Errores de validación en el formulario

```kotlin
data class ProfileFormState(
    val dto: ProfileDto,
    val fieldErrors: Map<String, String> = emptyMap(),
    val generalError: String? = null,
)

fun ProfileFormState.errorFor(field: String): String? = fieldErrors[field]
```

```kotlin
catch (e: MtoException) {
    when (val error = e.error) {
        is MtoError.Validation -> _form.update { it.copy(fieldErrors = error.problem.fieldErrors()) }
        is MtoError.Network -> _form.update { it.copy(generalError = "Sin conexión: se guardará al recuperar cobertura") }
        else -> _form.update { it.copy(generalError = error.userMessage()) }
    }
}
```

```kotlin
OutlinedTextField(
    value = form.dto.profileId.orEmpty(),
    onValueChange = viewModel::onProfileIdChange,
    isError = form.errorFor("profileId") != null,
    supportingText = { form.errorFor("profileId")?.let { Text(it) } },
    label = { Text("Identificador") },
)
```

Usa el **`code`** (`VAL-001`, `VAL-004`) para reaccionar y el `message` para mostrar: el texto puede
cambiar entre versiones, el código no. Y para los hijos, la ruta indexada encaja directamente:
`cantilevers[1].cwHeight` identifica la fila 1 del formulario de ménsulas.

### 11.4 Cargas masivas: no desde el móvil

`POST /{recurso}/bulk` y `jobs/bulk-*` exigen `config-import` y son una sola transacción: un error
en un elemento tumba los 5.000. No es una operación de móvil. Si la app necesita subir muchos
cambios (§12), que los suba **uno a uno** desde su cola: fallará solo el que esté mal y el resto
entra.

---

## 12. Offline: el túnel no tiene cobertura

Una app de infraestructura ferroviaria trabaja donde no hay red. Sin plan offline, la app es
inútil justo donde hace falta. La estrategia estándar es
[offline-first](https://developer.android.com/topic/architecture/data-layer/offline-first):

1. **Room es la fuente de verdad de la UI.** Las pantallas leen de la base local, siempre. Nunca
   se quedan en blanco por falta de red.
2. **La red rellena Room.** Un *sync* trae lo que cambió y actualiza la base.
3. **Las escrituras van a una cola local** y se envían cuando hay cobertura.

### 12.1 Room

```kotlin
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: Long,
    val profileId: String,
    val kp: String?,                 // BigDecimal como texto: SQLite no tiene decimal exacto
    val orderInTrack: Int?,
    val trackId: Long?,
    val statusCode: String?,
    val versionNumber: Int?,
    val payload: String,             // el DTO completo en JSON, para poder hacer read-modify-write offline
    val syncedAt: Long,
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE trackId = :trackId ORDER BY orderInTrack IS NULL, orderInTrack, kp, id")
    fun observeByTrack(trackId: Long): Flow<List<ProfileEntity>>   // Flow: la UI se refresca sola

    @Upsert suspend fun upsertAll(profiles: List<ProfileEntity>)

    @Query("DELETE FROM profiles WHERE trackId = :trackId")
    suspend fun clearTrack(trackId: Long)
}
```

Dos decisiones que no son obvias:

- **`kp` como texto.** SQLite no tiene tipo decimal exacto y `REAL` es coma flotante: `10.500` se
  convertiría en `10.5` y, siendo parte de la clave natural del perfil, eso acaba creando perfiles
  duplicados al sincronizar. El texto conserva la escala.
- **Guardar el DTO entero en `payload`.** Es lo que permite aplicar la regla de §11.1 sin red: para
  modificar un perfil offline hace falta el objeto completo con los `id` de sus hijos, y pedirlo al
  servidor no es una opción dentro del túnel.

El orden del `ORDER BY` replica el del servidor (`orderInTrack`, con los nulos al final; `kp` e `id`
de desempate). Referencia: [Room](https://developer.android.com/training/data-storage/room).

Los **catálogos LOV** son el caso más agradecido: cambian poco, son pequeños y se necesitan para
rellenar cualquier formulario. Bájalos enteros (`GET /{lov}`) al entrar y guárdalos; sin ellos, un
formulario offline no puede ni ofrecer los códigos válidos.

### 12.2 La cola de cambios pendientes

```kotlin
@Entity(tableName = "pending_changes")
data class PendingChange(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val resource: String,          // "profiles"
    val remoteId: Long?,           // null = alta
    val bodyJson: String,          // el DTO COMPLETO tal y como se va a mandar
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
)
```

Y el trabajador que la vacía, con
[WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager), que
sobrevive al cierre de la app y espera a que haya red:

```kotlin
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: PendingChangeDao,
    private val api: ProfileApi,
    private val json: Json,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        var huboFalloReintentable = false

        // De uno en uno y en orden: un bulk tumbaría todo el lote por un solo registro malo (§11.4)
        for (change in dao.pending()) {
            try {
                val dto = json.decodeFromString<ProfileDto>(change.bodyJson)
                safeApiCall { if (change.remoteId == null) api.create(dto) else api.update(change.remoteId, dto) }
                dao.delete(change)
            } catch (e: MtoException) {
                when (e.error) {
                    // Sin red o error temporal: se reintenta con backoff
                    is MtoError.Network, is MtoError.Throttled -> huboFalloReintentable = true
                    // El cuerpo está mal o el permiso no existe: reintentar no lo va a arreglar
                    is MtoError.Validation, is MtoError.Forbidden, is MtoError.NotFound ->
                        dao.markFailed(change, e.error.toString())
                    // Alguien tocó el registro antes: hay que preguntar al usuario, no resolverlo aquí
                    is MtoError.Conflict -> dao.markConflict(change)
                    else -> huboFalloReintentable = true
                }
            }
        }
        return if (huboFalloReintentable) Result.retry() else Result.success()
    }
}

// Al arrancar la app
WorkManager.getInstance(context).enqueueUniqueWork(
    "mto-sync",
    ExistingWorkPolicy.KEEP,
    OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build(),
)
```

El campo **`retryable`** del error del servidor (§1.3) es exactamente la señal que necesita este
`when`: si el backend dice que no tiene sentido reintentar, no lo reintentes.

> Los conflictos (`409`) **no se resuelven solos y no se descartan**. Se marcan, se cuentan y se le
> enseñan al usuario en una bandeja de "cambios que no pudieron subirse". Resolver automáticamente
> es perder el trabajo de alguien sin decírselo.

### 12.3 Sincronización de bajada

Hoy la API no expone un "dame lo que cambió desde X", así que la sincronización de bajada es por
alcance: el usuario elige una vía (o un paquete de ejecución) y la app se la descarga entera con la
paginación por cursor de §10.5, que es la barata para recorrer mucho. Guarda el `syncedAt` y
enséñalo: *"datos de hace 2 h"* es información, no decoración.

Si más adelante hace falta algo más fino, el dominio ya publica eventos de datos maestros por
RabbitMQ (ver [`README_MESSAGING.md`](README_MESSAGING.md)); lo natural sería que el gateway o un
servicio de notificaciones los convirtiera en un *push* al móvil, no que la app hablara con el
broker.

---

## 13. Trabajos en segundo plano (202 + jobId)

Exportaciones y cargas grandes responden **`202 Accepted`** al instante con un `jobId`, y el
resultado se recoge después. Detalle completo en [`README_ASYNC_JOBS.md`](README_ASYNC_JOBS.md).

```kotlin
interface ProfileJobApi {
    @POST("profiles/jobs/export")
    suspend fun startExport(
        @Query("trackId") trackId: Long,
        @Query("mapperType") mapperType: String = "basic",   // basic | technical | default
    ): ProfileJobResponse

    @GET("profiles/jobs/{jobId}")
    suspend fun status(@Path("jobId") jobId: String): ProfileJobResponse

    @Streaming                                               // no cargues el CSV entero en memoria
    @GET("profiles/jobs/{jobId}/file")
    suspend fun download(@Path("jobId") jobId: String): ResponseBody
}

@Serializable
data class ProfileJobResponse(
    val id: String,
    val type: String? = null,
    val status: String,                  // PENDING | RUNNING | COMPLETED | FAILED
    val createdAt: String? = null,
    val finishedAt: String? = null,
    val totalItems: Int? = null,
    val processedItems: Int = 0,
    val successfulItems: Int = 0,
    val failedItems: Int = 0,
    val downloadUrl: String? = null,
    val error: String? = null,
)
```

El sondeo, con espera creciente para no freír la batería:

```kotlin
suspend fun waitForJob(jobId: String): ProfileJobResponse {
    var delayMs = 1_000L
    repeat(60) {
        val job = safeApiCall { api.status(jobId) }
        if (job.status == "COMPLETED" || job.status == "FAILED") return job
        delay(delayMs)
        delayMs = (delayMs * 2).coerceAtMost(15_000)         // 1s, 2s, 4s... hasta 15s
    }
    error("El trabajo $jobId sigue sin terminar")
}
```

La descarga distingue casos que un `404` mezclaría, y conviene tratarlos por separado:

| Código | Significado | Qué hace la app |
|---|---|---|
| `409` | existe pero aún no ha terminado | seguir sondeando |
| `410` | terminó, pero el fichero ya se purgó | relanzar la exportación |
| `404` | no existe, o es un tipo que no produce fichero | error, no reintentar |
| `429` | sin hueco de concurrencia | esperar el `Retry-After` |

> Un sondeo de minutos **no va en el `ViewModel`**: si el usuario cambia de pantalla, se cancela. Va
> en un `CoroutineWorker` de WorkManager, con notificación de progreso. Lo mismo para la descarga.

---

## 14. Inyección de dependencias con Hilt

[Hilt](https://developer.android.com/training/dependency-injection/hilt-android) es Dagger con la
ceremonia quitada; el equivalente al contenedor de Spring, resuelto en tiempo de compilación.

```kotlin
@HiltAndroidApp
class MtoApplication : Application()          // y declararla en el manifiesto: android:name=".MtoApplication"

@AndroidEntryPoint
class MainActivity : ComponentActivity() { /* ... */ }
```

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun profileRepository(impl: DefaultProfileRepository): ProfileRepository
}
```

Cuatro reglas que evitan el 90% de los errores de Hilt:

| Regla | Por qué |
|---|---|
| `@HiltViewModel` en los ViewModels y `hiltViewModel()` en el composable | es la integración con Navigation, y da un ViewModel por destino |
| `@InstallIn(SingletonComponent::class)` salvo que sepas que quieres otro alcance | evita fugas de memoria por retener un `Activity` |
| Inyecta **interfaces**, no implementaciones | es lo que permite sustituirlas en los tests |
| `@ApplicationContext`, nunca el de la actividad, en objetos de larga vida | un `Context` de actividad retenido es una fuga clásica |

---

## 15. Tests

La pirámide en Android es la de siempre, con la salvedad de que los tests *instrumentados* (los que
corren en un dispositivo) son lentos: cuantos menos, mejor.
[Guía oficial de testing](https://developer.android.com/training/testing).

### 15.1 Red simulada: el test que protege los datos

`MockWebServer` levanta un servidor HTTP de mentira y deja afirmar sobre la petición **exacta** que
salió. Es el sitio donde se blinda la regla de §11.1:

```kotlin
class ProfileRepositoryTest {

    private val server = MockWebServer()
    private lateinit var repository: ProfileRepository

    @Before fun setUp() {
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/configuration/"))
            .addConverterFactory(MtoJson.asConverterFactory("application/json".toMediaType()))
            .build()
        repository = DefaultProfileRepository(retrofit.create())
    }

    @After fun tearDown() = server.shutdown()

    @Test
    fun `al cambiar el kp, las mensulas viajan con su id`() = runTest {
        server.enqueue(MockResponse().setBody(PERFIL_CON_DOS_MENSULAS))   // respuesta del GET
        server.enqueue(MockResponse().setBody(PERFIL_CON_DOS_MENSULAS))   // respuesta del PUT

        repository.saveKp(profileId = 7, nuevoKp = BigDecimal("10.750"))

        server.takeRequest()                                              // el GET
        val put = server.takeRequest()
        val body = MtoJson.parseToJsonElement(put.body.readUtf8()).jsonObject

        // Lo que de verdad importa: no se perdieron los id de los hijos
        val ids = body["cantilevers"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.long }
        assertEquals(listOf(1L, 2L), ids)
        // ...y el kp conserva la escala
        assertEquals("10.750", body["kp"]!!.jsonPrimitive.content)
    }

    @Test
    fun `sin tocar mensulas se manda null explicito, nunca lista vacia`() = runTest {
        // ...
        assertEquals(JsonNull, body["cantilevers"])      // [] borraría las filas en el servidor
    }
}
```

### 15.2 ViewModel y corrutinas

Con [`kotlinx-coroutines-test`](https://developer.android.com/kotlin/coroutines/test) y
[Turbine](https://github.com/cashapp/turbine) para los `Flow`:

```kotlin
@Test fun `un error de red deja la pantalla en estado error`() = runTest {
    val viewModel = TracksViewModel(FakeTrackRepository(falla = true))
    viewModel.uiState.test {
        assertTrue(awaitItem().isLoading)
        assertNotNull(awaitItem().error)
    }
}
```

### 15.3 UI de Compose

```kotlin
@get:Rule val composeRule = createComposeRule()

@Test fun `sin permiso de escritura no se ofrece crear`() {
    composeRule.setContent {
        MtoFieldTheme { ProfilesContent(profiles = flowOf(PagingData.empty()), permissions = Permissions(setOf("config-read"))) }
    }
    composeRule.onNodeWithContentDescription("Nuevo perfil").assertDoesNotExist()
}
```

Los composables se testean por su **semántica** (texto, descripción de contenido, rol), que es lo
mismo que lee un lector de pantalla: un test que pasa es además una pista de que la pantalla es
accesible ([testing en Compose](https://developer.android.com/develop/ui/compose/testing)).

---

## 16. Publicar: firma, R8 y requisitos de Google Play

### 16.1 Reglas de R8

R8 reduce y ofusca en release, y se lleva por delante lo que solo se usa por reflexión. Con
kotlinx.serialization y Retrofit los `keep` necesarios ya vienen con las librerías, pero conviene
**probar la release antes de publicarla** (`./gradlew assembleRelease` e instalarla): los fallos de
R8 no salen en debug ([reducir código](https://developer.android.com/build/shrink-code)).

Si algo se rompe al serializar, el sospechoso son los DTOs; la regla mínima:

```proguard
# app/proguard-rules.pro
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class com.alejandro.mtomobile.**$$serializer { *; }
-keepclassmembers class com.alejandro.mtomobile.** { *** Companion; }
```

### 16.2 Firma y publicación

- **App Bundle (`.aab`)**, no APK: es el formato que exige Google Play
  ([App Bundle](https://developer.android.com/guide/app-bundle)).
- Firma con [Play App Signing](https://developer.android.com/studio/publish/app-signing); la clave
  de subida **nunca** se versiona: va en `local.properties` o en el gestor de secretos del CI.
- **`targetSdk` 36 como mínimo**: desde el 31 de agosto de 2026 Google Play no acepta apps ni
  actualizaciones por debajo de Android 16
  ([requisitos de nivel de API](https://developer.android.com/google/play/requirements/target-sdk)).
- Si la app es interna de la organización y no pasa por la tienda pública, la distribución puede ser
  por [Managed Google Play](https://developer.android.com/work/managed-play) — que es lo habitual
  en una app de mantenimiento ferroviario.

### 16.3 Rendimiento

Antes de dar por buena la app en un móvil de campo (gama media, batería al 20%, guantes):

- [Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview):
  bajan el tiempo de arranque y los tirones de la primera pantalla de forma medible.
- [Rendimiento en Compose](https://developer.android.com/develop/ui/compose/performance): mide en
  **release**, nunca en debug, donde Compose va deliberadamente lento.
- Modo avión con datos cargados: la app tiene que seguir siendo usable (§12).

---

## 17. Y si mañana hay iOS

Lo que se ha montado aquí es Android nativo, que es lo correcto si el objetivo es Android. Si en
algún momento hay que dar iOS:

- **[Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)** permite compartir la
  capa de datos —DTOs, repositorios, lógica— y escribir la UI nativa en cada plataforma. Es el
  reparto de menos riesgo. En ese escenario, Ktor Client sustituye a Retrofit (que es solo JVM) y
  kotlinx.serialization se queda igual.
- **[Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)** comparte además la
  UI. Comprueba su estado y sus limitaciones en iOS antes de comprometer un producto.

Lo que no cambia en ningún caso: el contrato con la API, el flujo de OAuth y todas las reglas de
§11. Si escribes la capa de datos pensando en que podría mudarse (repositorios detrás de interfaces,
DTOs sin dependencias de Android), la migración es un trabajo acotado y no una reescritura.

---

## 18. Ruta de aprendizaje con la doc oficial

Orden sugerido. Está pensado para llegar a "app que lista perfiles reales" en la primera semana, no
para leerlo todo antes de empezar.

**Semana 1 — moverse**

| Qué | Dónde | Tiempo |
|---|---|---|
| Sintaxis de Kotlin | [Kotlin tour](https://kotlinlang.org/docs/kotlin-tour-welcome.html) | 3-4 h |
| Primera app en Compose | [Jetpack Compose basics (codelab)](https://developer.android.com/codelabs/jetpack-compose-basics) | 2 h |
| Estado y recomposición | [State in Compose](https://developer.android.com/develop/ui/compose/state) | 2 h |
| Listas y navegación | [Lists](https://developer.android.com/develop/ui/compose/lists) · [Navigation](https://developer.android.com/guide/navigation) | 3 h |

**Semana 2 — datos**

| Qué | Dónde |
|---|---|
| Corrutinas y `Flow` | [Coroutines en Android](https://developer.android.com/kotlin/coroutines) · [Flow](https://developer.android.com/kotlin/flow) |
| Arquitectura | [Guide to app architecture](https://developer.android.com/topic/architecture) |
| Red | [Retrofit](https://square.github.io/retrofit/) · [kotlinx.serialization](https://kotlinlang.org/docs/serialization.html) |
| Inyección | [Hilt](https://developer.android.com/training/dependency-injection/hilt-android) |

**Semana 3 — lo que hace la app usable en campo**

| Qué | Dónde |
|---|---|
| OAuth nativo | [AppAuth-Android](https://github.com/openid/AppAuth-Android) · [RFC 8252](https://datatracker.ietf.org/doc/html/rfc8252) · [Keycloak](https://www.keycloak.org/docs/latest/securing_apps/index.html) |
| Persistencia | [Room](https://developer.android.com/training/data-storage/room) · [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) |
| Paginación | [Paging 3](https://developer.android.com/topic/libraries/architecture/paging/v3-overview) |
| Trabajo en segundo plano | [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) |
| Offline-first | [Offline-first](https://developer.android.com/topic/architecture/data-layer/offline-first) |

**Curso completo, si prefieres una vía guiada**:
[Android Basics with Compose](https://developer.android.com/courses/android-basics-compose/course)
(gratuito, de Google, con ejercicios).

**Código que merece la pena leer**: [Now in Android](https://github.com/android/nowinandroid) (app
completa con la arquitectura recomendada) y [compose-samples](https://github.com/android/compose-samples).

---

## 19. Apéndices

### 19.1 Endpoints por recurso

`$BASE = /api/v1/configuration`

| Recurso | Rutas |
|---|---|
| `execution-packages` | CRUD · `paged` · `search` · `filter` |
| `stations` | CRUD · `paged` · `search` · `filter` |
| `tracks` | CRUD · `paged` · `search` · `filter` |
| `profiles` | CRUD · `paged` · `search` · `filter` · `track/{id}/keyset` · `track/{id}/range` · `track/{id}/export` · `jobs/**` |
| `cantilevers` | CRUD · `paged` · `search` · `filter` · `profile/{profileId}` |
| `steady-arms` | CRUD · `paged` · `search` · `filter` · `cantilever/{cantileverId}` |
| `disconnectors` | CRUD · `paged` · `search` · `filter` · `station/{stationId}` · `station/name/{name}` |
| `section-insulators` | CRUD · `paged` · `search` · `filter` · `station/{stationId}` · `station/name/{name}` |
| LOV (17 catálogos) | `GET /{lov}` · `GET /{lov}/{id}` · `GET /{lov}/code/{code}` · CRUD (requiere `lov-manage`) |
| Trabajos | `profiles/jobs/{export,bulk-create,bulk-update,import}` · `profiles/jobs/{id}` · `profiles/jobs/{id}/file` · `lovs/jobs/**` · `master-data/republish` |

### 19.2 De DTO de Java a `data class` de Kotlin

| En el servidor | En la app | Cuidado con |
|---|---|---|
| `Long id` | `Long? = null` | nulo en el alta, no nulo al leer |
| `BigDecimal kp` | `BigDecimal?` con serializador propio | `Double` pierde la escala y **duplica perfiles** |
| `LocalDateTime createDate` | `String?` (ISO-8601) | se convierte en el mapper a dominio |
| `LocalDate startDate` | `String?` (`yyyy-MM-dd`) | lleva `@JsonFormat` en el servidor |
| `Integer versionNumber` | `Int?` | devuélvelo tal como lo leíste: es lo que detecta que otro guardó antes (`409`) |
| `List<CantileverDTO> cantilevers` | `List<CantileverDto>? = null` | `null` = no tocar · `[]` = **borrar todos** |
| `LovDTO poleType` | `LovDto?` | basta `code`; lo demás se ignora |
| `List<Long> stationIds` | `List<Long>? = null` | desde `V17` una vía pasa por varias estaciones |
| `Page<T>` | `SpringPage<T>` | `number` es base 0 |

### 19.3 Checklist antes de dar por buena la app

**Contrato con la API**
- [ ] `encodeDefaults = true` y `explicitNulls = true` en el `Json`, con un test que lo demuestre
- [ ] Ningún `PUT` construye el cuerpo desde cero: siempre leer-modificar-devolver
- [ ] Los decimales van por `BigDecimal`, nunca `Double`
- [ ] `versionNumber` se reenvía y el `409` se le muestra al usuario
- [ ] Los errores se reaccionan por `code`, no por el texto del `message`
- [ ] El `traceId` de un `500` es visible y copiable

**Seguridad**
- [ ] Authorization Code + PKCE, nunca usuario y contraseña en la app
- [ ] El cliente `mto-mobile` existe en el realm, con su *redirect* y sus *audience mappers*
- [ ] Los tokens están cifrados con una clave del Keystore
- [ ] `allowBackup=false` y ningún log de red en release
- [ ] El tráfico en claro solo está permitido en `src/debug/`

**Campo**
- [ ] La app arranca y es usable en modo avión
- [ ] Los cambios hechos sin cobertura se suben solos al recuperarla
- [ ] Los conflictos no se descartan en silencio
- [ ] Se ve la antigüedad de los datos descargados
- [ ] Objetivos táctiles de 48 dp y contraste suficiente al sol

**Publicación**
- [ ] `targetSdk` 36 o superior
- [ ] La build de release probada en un dispositivo real, no solo la debug
- [ ] Baseline Profile generado

---

## Documentación relacionada de este repositorio

- [`README_API.md`](README_API.md) — el contrato completo: colecciones de hijos, consultas, errores
- [`README_ASYNC_JOBS.md`](README_ASYNC_JOBS.md) — trabajos en segundo plano y sus códigos
- [`README_LOCAL_DOCKER.md`](README_LOCAL_DOCKER.md) — entorno local, `auth.mto.local` y puertos
- [`keycloak/README.md`](keycloak/README.md) — clientes, permisos y perfiles del realm
- [`README_MESSAGING.md`](README_MESSAGING.md) — eventos de datos maestros, por si mañana hay *push*
