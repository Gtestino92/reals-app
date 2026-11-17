# Corrección de ownership y revalidación de perfil

Rama: `feature/profile-web-parity`. HEAD inicial: `dc7bfef67074909ebca06a512594aba977f832b9`.
Se verificó el worktree limpio, se actualizó la referencia de `origin/development`
y se comparó la rama antes de editar. Esta corrección no modifica Web, backend,
contratos HTTP ni `gradle.properties`. La validaci?n se entreg? inicialmente sin commits, push ni merge; el usuario autoriz? posteriormente publicar estas correcciones. No se solicit? merge.

## Causas

Los handlers y la presentación comparaban referencias de `ProvisionedSession`.
Una copia legítima del usuario/perfil cancelaba operaciones compatibles,
desmontaba la vista previa o descartaba una respuesta dejando flags pendientes.
Además, `ON_RESUME` invocaba el mismo callback que el reintento explícito: una
lectura de fondo activaba carga/refresh bloqueante. Las lecturas no tenían una
prioridad inferior a las escrituras.

## Invariantes

- La identidad incluye un identificador del lifetime de autenticación, el usuario
  backend y el perfil. No depende de la referencia del snapshot ni solo del userId.
- Login/logout rotan la identidad local, incluso para el mismo usuario. Reload y
  renovación del token no la rotan. Las copias conservan ese identificador.
- Un reemplazo real cancela trabajo y limpia estado privado incompatible. Una
  renovación compatible conserva pantalla, borradores, selección y operaciones.
- Ninguna lectura anterior a una escritura puede reemplazar su respuesta ni
  levantar un bloqueo por incertidumbre.
- Una respuesta de perfil que quedó detrás de un snapshot confirmado más nuevo
  no se instala: se consulta el estado autorizado y se finalizan los flags. Si
  esa consulta también queda detrás, se conserva el snapshot más nuevo.
- Una lectura saludable de fondo no cambia foco/scroll ni bloquea edición. Un
  fallo conserva el contenido confirmado compatible; preguntas exige una lectura
  exitosa antes de nuevas escrituras. Revocación y estados incompatibles se
  tratan de forma estricta.

La identidad no usa tokens como claves ni los almacena. Un epoch local distingue
logins consecutivos del mismo UID. No se usa solo `lastSignInTimestamp`: Firebase
documenta una granularidad de hasta dos minutos para sign-ins consecutivos.
[Referencia oficial de Firebase](https://firebase.google.com/docs/reference/android/com/google/firebase/auth/FirebaseUserMetadata).

## Matriz de mutaciones y respuestas revisadas

| Ruta | Protección / resultado |
| --- | --- |
| Firebase login, registro, Google y logout; construcción y copia de sesión | Epoch de autenticación + UID; el loader captura y vuelve a comprobar la identidad antes de entregar el snapshot. Las copias preservan el lifetime. |
| Collector de `RealsRootViewModel` | Los handlers comparan ownership explícito. Una copia compatible no cancela; otra autenticación/perfil cancela y limpia datos incompatibles. Los formularios guardados se delimitan por autenticación. |
| Renovación backend con un perfil abierto | Conserva Ready durante una renovación de la misma autenticación, conserva estado local al recibirla y vuelve a comprobar requisitos legales. No cambia el flujo de bootstrap de otras superficies. |
| Preview: apertura, retry, resume, cierre y reemplazo | Snapshot completo por request generation y owner. Resume usa `revalidating`, conservando contenido. Si cambió el perfil confirmado durante la lectura, se consulta de nuevo sin instalar la lectura vieja. Cerrar impide reapertura por un resume tardío. |
| Preguntas: lectura inicial, retry y resume | Inicial/retry pueden bloquear; resume saludable conserva snapshot y feedback sin activar `loading/refreshing`. Los fallos retienen el snapshot compatible y requieren reconciliación; 401/403 y revocación retiran datos confirmados. |
| Preguntas: upsert, delete y selección | Invalidan y cancelan la lectura anterior antes de escribir. El request de mutación y owner protegen la instalación autoritativa. Se conservan los drafts independientes. Una lectura vieja no elimina incertidumbre. |
| Datos, preferencias y fotos: reads/writes y respuestas secundarias | El job privado excluye duplicados aunque se reconstruyan flags. Se comprueba owner al publicar e instalar. Una respuesta desplazada por un perfil nuevo provoca lectura autorizada de perfil/fotos; no reemplaza snapshots más nuevos y finaliza flags incluso si la reconciliación falla. |
| Países, creación, activación y verificación de email | Las rutas secundarias comprueban owner antes de publicar/instalar y actualizan el estado compatible más reciente. No se reinstala una sesión capturada ni se aplica un error viejo a otra cuenta. |
| Composición y `ON_RESUME` | Callbacks separados de retry y resume; `rememberUpdatedState` evita callbacks obsoletos. Se conservan la composición del diálogo y los estados saveable compatibles. Los listeners se retiran al disponer. |

Se revisaron solapamientos, resultados fuera de orden, cambios de identidad con
operaciones pendientes, snapshots nuevos durante reconciliación, duplicación de
acciones, fallos y revocación. No se usa polling para reparar instalaciones
parciales. No se añadieron endpoints, dependencias ni otro framework de navegación.

## Validación focalizada

Comando final:

```powershell
.\gradlew.bat --daemon --configuration-cache --build-cache --console=plain :app:testLocalDebugUnitTest --tests com.reals.app.data.repository.AuthenticationSessionIdentityTest --tests com.reals.app.ui.root.OwnerProfilePreviewHandlerTest --tests com.reals.app.ui.root.ProfileQuestionOperationHandlerTest --tests com.reals.app.ui.root.ProfileOperationHandlerCountriesTest --tests com.reals.app.ui.root.ProfileOperationHandlerPhotoReorderTest --tests com.reals.app.ui.root.ProfileOperationHandlerLocalEmailVerificationTest --tests com.reals.app.ui.root.RealsRootViewModelProfileRoutingTest --tests com.reals.app.ui.root.SessionCoordinatorLocalEmailVerificationTest :app:compileLocalDebugAndroidTestKotlin
```

El comando compila Kotlin principal y las pruebas Android. No se ejecutó la suite
completa. Una ejecución intermedia detectó un import faltante en una prueba nueva;
se corrigió antes de las validaciones finales.

Las dos nuevas pruebas Compose en `ProfileResumeLifecycleTest` usan eventos de
`LifecycleRegistry`: verifican callback silencioso, preservación de borrador,
selección y foco, Save habilitado durante la lectura saludable y scroll del
preview. Compilan, pero no se ejecutaron: `adb devices` no detectó dispositivos.

Gradle sigue advirtiendo que `:app:validateLocalEnvironment` no puede serializarse
en configuration cache. Es la advertencia existente; no se cambiaron opciones de
Gradle para ocultarla.

## Límites

- Falta ejecutar las pruebas Compose y los flujos reales de Firebase en Android.
- Falta comprobar revocación, reautenticación y background/resume con servicios
  reales; los tests JVM usan respuestas y gates controlados.
- El epoch es local al runtime: no se persiste como credencial. Esta corrección
  no certifica restauración de proceso; mantiene aislamiento estricto cuando no
  puede probarse continuidad de autenticación.

El resultado final, el inventario de archivos y `git diff --stat` se registran a continuación.


## Resultado final

`BUILD SUCCESSFUL`: 90 tests JVM, cero fallos. Compilaci?n principal y `:app:compileLocalDebugAndroidTestKotlin` correctas.

| Clase | Tests | Fallos |
| --- | ---: | ---: |
| `AuthenticationSessionIdentityTest` | 3 | 0 |
| `OwnerProfilePreviewHandlerTest` | 13 | 0 |
| `ProfileOperationHandlerCountriesTest` | 17 | 0 |
| `ProfileOperationHandlerLocalEmailVerificationTest` | 6 | 0 |
| `ProfileOperationHandlerPhotoReorderTest` | 7 | 0 |
| `ProfileQuestionOperationHandlerTest` | 20 | 0 |
| `RealsRootViewModelProfileRoutingTest` | 15 | 0 |
| `SessionCoordinatorLocalEmailVerificationTest` | 9 | 0 |

`git diff --check`: correcto. Todos los archivos modificados/nuevos decodifican como UTF-8 y no hay marcadores de mojibake en l?neas agregadas.

### Diff

`git diff --stat` (archivos ya versionados):

```text
 .../app/data/repository/FirebaseAuthRepository.kt  |  21 +-
 app/src/main/java/com/reals/app/di/AppContainer.kt |   2 +
 .../java/com/reals/app/di/RealsRootDependencies.kt |   3 +
 .../reals/app/domain/model/ProvisionedSession.kt   |  10 +
 .../usecase/ProvisionAndLoadProfileUseCase.kt      |  10 +
 .../app/ui/profile/OwnerProfilePreviewDialog.kt    |   5 +-
 .../reals/app/ui/profile/ProfileQuestionScreen.kt  |   5 +-
 .../reals/app/ui/profile/ProfileStatusScreen.kt    |  11 +-
 .../app/ui/root/OwnerProfilePreviewHandler.kt      |  34 ++-
 .../app/ui/root/ProfileNavigationRestoration.kt    |   2 +-
 .../reals/app/ui/root/ProfileOperationHandler.kt   | 236 ++++++++++++++-------
 .../app/ui/root/ProfileQuestionOperationHandler.kt |  42 +++-
 .../main/java/com/reals/app/ui/root/RealsApp.kt    |   6 +-
 .../java/com/reals/app/ui/root/RealsRootUiState.kt |   1 +
 .../com/reals/app/ui/root/RealsRootViewModel.kt    |  25 +++
 .../com/reals/app/ui/root/SessionCoordinator.kt    |   5 +-
 .../test/java/com/reals/app/testutil/TestApi.kt    |   3 +-
 .../test/java/com/reals/app/testutil/TestDomain.kt |   1 +
 .../app/ui/root/OwnerProfilePreviewHandlerTest.kt  |  89 ++++++++
 .../root/ProfileOperationHandlerCountriesTest.kt   | 114 ++++++++++
 .../ui/root/ProfileQuestionOperationHandlerTest.kt | 101 +++++++++
 .../root/RealsRootViewModelProfileRoutingTest.kt   |  48 +++++
 22 files changed, 664 insertions(+), 110 deletions(-)
```

Archivos nuevos sin staging:

- `app/src/androidTest/java/com/reals/app/ui/profile/ProfileResumeLifecycleTest.kt`
- `app/src/main/java/com/reals/app/ui/root/ProfileReadPolicy.kt`
- `app/src/test/java/com/reals/app/data/repository/AuthenticationSessionIdentityTest.kt`
- `docs/profile-session-revalidation-validation.md`

Archivos versionados modificados:

- `app/src/main/java/com/reals/app/data/repository/FirebaseAuthRepository.kt`
- `app/src/main/java/com/reals/app/di/AppContainer.kt`
- `app/src/main/java/com/reals/app/di/RealsRootDependencies.kt`
- `app/src/main/java/com/reals/app/domain/model/ProvisionedSession.kt`
- `app/src/main/java/com/reals/app/domain/usecase/ProvisionAndLoadProfileUseCase.kt`
- `app/src/main/java/com/reals/app/ui/profile/OwnerProfilePreviewDialog.kt`
- `app/src/main/java/com/reals/app/ui/profile/ProfileQuestionScreen.kt`
- `app/src/main/java/com/reals/app/ui/profile/ProfileStatusScreen.kt`
- `app/src/main/java/com/reals/app/ui/root/OwnerProfilePreviewHandler.kt`
- `app/src/main/java/com/reals/app/ui/root/ProfileNavigationRestoration.kt`
- `app/src/main/java/com/reals/app/ui/root/ProfileOperationHandler.kt`
- `app/src/main/java/com/reals/app/ui/root/ProfileQuestionOperationHandler.kt`
- `app/src/main/java/com/reals/app/ui/root/RealsApp.kt`
- `app/src/main/java/com/reals/app/ui/root/RealsRootUiState.kt`
- `app/src/main/java/com/reals/app/ui/root/RealsRootViewModel.kt`
- `app/src/main/java/com/reals/app/ui/root/SessionCoordinator.kt`
- `app/src/test/java/com/reals/app/testutil/TestApi.kt`
- `app/src/test/java/com/reals/app/testutil/TestDomain.kt`
- `app/src/test/java/com/reals/app/ui/root/OwnerProfilePreviewHandlerTest.kt`
- `app/src/test/java/com/reals/app/ui/root/ProfileOperationHandlerCountriesTest.kt`
- `app/src/test/java/com/reals/app/ui/root/ProfileQuestionOperationHandlerTest.kt`
- `app/src/test/java/com/reals/app/ui/root/RealsRootViewModelProfileRoutingTest.kt`

Resumen: identidad expl?cita de autenticaci?n independiente del snapshot; preservaci?n compatible y cancelaci?n real; reads silenciosos con prioridad de writes; reconciliaci?n autorizada y finalizaci?n de pendientes; regresiones JVM y de ciclo de vida Compose.
