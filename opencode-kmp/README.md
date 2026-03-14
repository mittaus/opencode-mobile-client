# OpenCode Remote — Kotlin Multiplatform

App Android (con base KMP) para controlar [OpenCode](https://opencode.ai) remotamente desde el celular.

---

## Prerrequisitos

### 1. JDK

| Requisito | Valor |
|-----------|-------|
| **Versión mínima** | JDK 17 |
| **Versión recomendada** | JDK 17 (LTS) — es la que usa Android Studio Hedgehog/Iguana por defecto |
| **Notas** | Android Studio incluye su propio JDK embebido. Si compilas desde terminal, verifica que `java -version` devuelva 17 o superior. Si el JDK del sistema es 21, el proyecto compila igualmente — está configurado para target JVM 17 de forma explícita en ambos módulos (`androidApp` y `shared`) para evitar incompatibilidades. |

> **¿Por qué JVM 17?**
> AGP 8.5.x y Kotlin 2.0.x soportan JVM 17 como target estable. El build tiene `compileOptions { sourceCompatibility/targetCompatibility = VERSION_17 }` y `compilerOptions { jvmTarget = JVM_17 }` para que Java y Kotlin siempre estén alineados, independientemente del JDK del sistema.

---

### 2. Android Studio

| Componente | Versión mínima recomendada |
|------------|---------------------------|
| **Android Studio** | Hedgehog (2023.1.1) o superior |
| **Versión recomendada** | Iguana (2023.2.1) / Jellyfish (2023.3.1) / Koala (2024.1.1) |
| **Plugin Kotlin** | 2.0.21 (se descarga automáticamente con Gradle) |
| **Plugin Android Gradle (AGP)** | 8.5.2 (declarado en `libs.versions.toml`) |

> Android Studio Hedgehog+ incluye soporte nativo para proyectos KMP y el asistente de importación.

---

### 3. Android SDK

Instalar desde **Android Studio → SDK Manager**:

| Componente | Versión requerida |
|------------|-------------------|
| **Android SDK Platform** | API 35 (Android 15) — `compileSdk` y `targetSdk` |
| **Android SDK Platform** | API 26 (Android 8) — `minSdk` mínimo soportado |
| **Android Build Tools** | 35.0.0 o 35.0.1 |
| **Android SDK Command-line Tools** | Latest |

> En `local.properties` se genera automáticamente la ruta `sdk.dir` cuando abres el proyecto en Android Studio por primera vez. **No commitear** ese archivo.

---

### 4. Gradle

| Requisito | Valor |
|-----------|-------|
| **Versión** | 8.10.2 (definido en `gradle/wrapper/gradle-wrapper.properties`) |
| **Gestión** | El `gradlew` / `gradlew.bat` descarga Gradle automáticamente, no requiere instalación manual |

---

### 5. Configuración del entorno

#### 5.1 Clonar y abrir

```bash
git clone <repo-url>
cd opencode-kmp
```

Abrir en Android Studio: **File → Open → seleccionar la carpeta `opencode-kmp`**
Android Studio detectará el proyecto Gradle y sincronizará automáticamente.

#### 5.2 Verificar `local.properties`

El archivo `local.properties` se genera solo. Si no existe, créalo manualmente:

```properties
# Windows
sdk.dir=C\:\\Users\\<tu-usuario>\\AppData\\Local\\Android\\Sdk

# macOS / Linux
sdk.dir=/Users/<tu-usuario>/Library/Android/sdk
```

#### 5.3 Compilar desde terminal (opcional)

```bash
# Windows
./gradlew.bat assembleDebug

# macOS / Linux
chmod +x gradlew
./gradlew assembleDebug
```

El APK de debug se genera en:
```
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

---

### 6. Checklist rápido antes de compilar

```
[ ] JDK 17 o 21 instalado (java -version)
[ ] Android Studio Hedgehog o superior
[ ] SDK Platform API 35 instalado (SDK Manager)
[ ] Build Tools 35.0.0 o 35.0.1 instalados
[ ] local.properties con sdk.dir configurado
[ ] Gradle 8.10.2 descargado (./gradlew --version)
```

---

### 7. Problemas conocidos al configurar

| Síntoma | Causa | Solución |
|---------|-------|----------|
| `Plugin already on classpath with different version (8.1.x)` | Existe un `build.gradle` (Groovy) viejo en la raíz del proyecto | Eliminar `build.gradle` y dejar solo `build.gradle.kts` |
| `Inconsistent JVM-target (17) and (21)` | Android Studio usa JDK 21 pero `compileOptions` apunta a otro target | El proyecto ya tiene `jvmTarget = JVM_17` explícito en ambos módulos, no requiere acción |
| `AndroidManifest.xml not found at src/androidMain/` | Estructura de fuentes en `src/main/` (layout v1 KMP) | Mover los fuentes a `src/androidMain/` — ya corregido en este proyecto |
| `gradlew: No such file or directory` | Wrapper scripts faltantes | Copiar `gradlew`, `gradlew.bat` y `gradle/wrapper/gradle-wrapper.jar` desde otro proyecto Android |
| `import androidx.adatastore` (typo) | Typo en import de DataStore | Corregir a `androidx.datastore.core.DataStore` — ya corregido |

---

## Arquitectura

```
┌─────────────────────────────────────────────────────────┐
│  androidApp (Jetpack Compose + ViewModel)                │
│  ┌──────────┐ ┌──────────┐ ┌───────────┐ ┌──────────┐  │
│  │ Connect  │ │ Projects │ │   Chat    │ │  Stats   │  │
│  │   VM     │ │    VM    │ │    VM     │ │    VM    │  │
│  └────┬─────┘ └────┬─────┘ └─────┬─────┘ └────┬─────┘  │
└───────┼────────────┼─────────────┼─────────────┼────────┘
        │            │   shared KMP module        │
┌───────▼────────────▼─────────────▼─────────────▼────────┐
│  Domain Layer (commonMain)                               │
│  UseCase → Repository interface → Model                  │
├──────────────────────────────────────────────────────────┤
│  Data Layer (commonMain)                                 │
│  RepositoryImpl → OpenCodeApi (Ktor) → DTOs + Mappers    │
│  SseEventParser → ServerEvent sealed class               │
│  StatsRepositoryImpl (local accumulation)                │
├───────────────────────┬──────────────────────────────────┤
│  androidMain          │  iosMain                         │
│  AndroidPreferences   │  IosPreferences (NSUserDefaults) │
│  currentTimeMs()      │  currentTimeMs()                 │
└───────────────────────┴──────────────────────────────────┘
```

### Capas

| Capa | Módulo | Tecnología |
|------|--------|------------|
| **Domain** | `shared/commonMain` | Kotlin puro — modelos, repositorios (interfaces), casos de uso |
| **Data** | `shared/commonMain` | Ktor HTTP + SSE, kotlinx.serialization, DTOs + mappers |
| **Storage** | `shared/androidMain` + `iosMain` | DataStore (Android), NSUserDefaults (iOS) |
| **Presentation** | `androidApp` | Jetpack Compose, ViewModel, Navigation Compose |
| **DI** | `androidApp` | Koin 4 |

## Estructura de archivos

```
opencode-kmp/
├── shared/
│   └── src/
│       ├── commonMain/kotlin/com/opencode/shared/
│       │   ├── domain/
│       │   │   ├── model/          # Session, Message, FileDiff, TodoItem, PermissionRequest...
│       │   │   ├── repository/     # Interfaces: SessionRepository, MessageRepository...
│       │   │   └── usecase/        # GetSessionsUseCase, SendMessageUseCase, GetDiffUseCase...
│       │   ├── data/
│       │   │   ├── remote/
│       │   │   │   ├── api/        # OpenCodeApi.kt (Ktor HTTP + SSE)
│       │   │   │   ├── dto/        # SessionDto, MessageDto, FileDiffDto...
│       │   │   │   ├── mapper/     # toDomain() extensions
│       │   │   │   └── SseEventParser.kt
│       │   │   ├── local/          # PreferencesStorage interface
│       │   │   └── repository/     # Implementaciones concretas
│       │   └── di/                 # SharedModule.kt (Koin)
│       ├── androidMain/            # AndroidPreferencesStorage, currentTimeMs
│       └── iosMain/                # IosPreferencesStorage, currentTimeMs
│
└── androidApp/
    └── src/androidMain/kotlin/com/opencode/android/
        ├── MainActivity.kt
        ├── OpenCodeApp.kt          # Koin startKoin()
        ├── di/AppModule.kt         # ViewModels + DataStore
        └── ui/
            ├── theme/              # OpenCodeTheme, colores, tipografía
            ├── navigation/         # NavGraph, Route sealed class
            ├── components/         # SharedComponents.kt
            └── screens/
                ├── connect/        # ConnectViewModel + ConnectScreen
                ├── projects/       # ProjectsViewModel + ProjectsScreen
                ├── models/         # ModelsViewModel + ModelsScreen
                ├── chat/           # ChatViewModel + ChatScreen (con Todo tab)
                ├── files/          # FilesViewModel + FilesScreen (con DiffDetailPanel)
                ├── sessions/       # SessionsViewModel + SessionsScreen
                └── stats/          # StatsViewModel + StatsScreen
```

## Flujo de eventos SSE

```
GET /event  (stream perpetuo)
     │
     ▼
SseEventParser.parse(rawJson)
     │
     ▼ ServerEvent sealed class
     ├── MessageUpdated  → ChatViewModel actualiza lista + registra tokens en StatsRepo
     ├── PermissionRequested → ChatViewModel expone pendingPermission → PermissionSheet
     ├── TodoUpdated     → ChatViewModel actualiza lista de todos
     └── SessionAborted  → ChatViewModel.isRunning = false
```

## Cómo correr el servidor en tu PC / servidor

El proyecto usa **opencode-gateway** como intermediario entre la app y el CLI de OpenCode.

```
[App Android]
  X-API-Key + X-Project-Path
       │
       ▼
[opencode-gateway :8080]
       │
       ├──► [opencode CLI :4097]  /projects/proyecto-a
       ├──► [opencode CLI :4098]  /projects/proyecto-b
       └──► [opencode CLI :4096]  bootstrap (fallback)
```

El gateway escanea automáticamente los subdirectorios de `OPENCODE_PROJECT` al arrancar y levanta un proceso `opencode serve` independiente por cada uno. La app selecciona el proyecto activo enviando el header `X-Project-Path` con la ruta absoluta del directorio.

```powershell
# Windows — desde la carpeta opencode-server-gateway
.\opencode-gateway.exe
```

```bash
# macOS / Linux
./opencode-gateway
```

La app se conecta a: `http://192.168.x.x:8080`

> El gateway expone los mismos endpoints que el CLI de OpenCode, más endpoints de proceso (`/process/status`, `/process/logs`, etc.) y pool (`/pool`, `/project/register`).

## Dependencias principales

| Librería | Versión | Uso |
|----------|---------|-----|
| Kotlin Multiplatform | 2.0.21 | Base KMP |
| Ktor Client | 3.0.1 | HTTP + SSE streaming |
| kotlinx.serialization | 1.7.3 | JSON |
| Koin | 4.0.0 | DI |
| Jetpack Compose | 1.7.3 | UI |
| Navigation Compose | 2.8.4 | Navegación |
| DataStore | 1.1.1 | Persistencia local |
| Lifecycle/ViewModel | 2.8.7 | MVVM |

## Pantallas implementadas

- **Connect** — Conectar al servidor, test de health, configurar URL y API Key
- **Projects** — Lista proyectos descubiertos por el gateway (un proceso opencode por proyecto); al seleccionar uno se envía `X-Project-Path` en todas las requests siguientes
- **Models** — Selector de modelo por proveedor (`GET /provider`)
- **Chat** — Mensajes en tiempo real, toggle Build/Plan, tab Todo list, botón Stop ⏹, chip Undo ↩
- **Files** — Git diff por sesión (`GET /session/:id/diff`), panel de diff inline
- **Sessions** — Historial, nueva sesión, reanudar sesión activa
- **Stats** — Tokens + costos acumulados localmente, uso por modelo y herramienta

## Pendiente para producción

- [ ] Reconexión automática del stream SSE con backoff exponencial
- [ ] iOS app (base KMP ya lista, falta SwiftUI o Compose Multiplatform)
- [ ] Tests unitarios de use cases y mappers
- [ ] Proguard rules para release
- [ ] Deep links para sesiones compartidas (`opencode share`)
