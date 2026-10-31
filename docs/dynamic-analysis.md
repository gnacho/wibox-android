# Análisis dinámico controlado de Wibox Fermax v2.2

Fecha: 2026-10-31. Hojas `leaf-1.2.3` y `leaf-1.2.5`.

## 1. Resultado ejecutivo

Se realizaron dos intentos separados y se conserva la evidencia de ambos.

**Intento 1:** el AVD ARM64 de Android 37 no arrancó en el host x86_64. No hubo instalación ni tráfico.

**Intento 2:** la imagen oficial Android 30 Google APIs x86_64 sí ejecutó el APK original mediante traducción ARM por proceso. El APK se instaló, arrancó en frío y alcanzó la pantalla principal vacía sin dispositivos vinculados.

**Intento 3:** se introdujo un QR totalmente ficticio de laboratorio mediante DocumentsUI. La consulta online no llegó a `userFreeRegister`/`userLogin` y no hubo bind. En cambio, el actualizador devolvió HTTP 200 y un XML completo con `result=0`, lo que aporta el primer request+response de éxito observado.

Resultado dinámico del intento 2:

- Traducción nativa confirmada: `Initialized NDK translation (aarch64), version 0.2.2`.
- Una request stock observada por MITM: GET del actualizador `version_check.jsp`.
- Conexiones HTTPS de Wibox a `fermax.qvcloud.net:443` confirmadas por DNS y UID.
- Conexiones TCP de Wibox al puerto `1884` confirmadas, con payload y función exacta sin decodificar.
- No se generó una sesión opaca observada, ni registro, login, OAuth, Shadow o dispositivo vinculado.
- No hay response HTTP del actualizador: el upstream presentó un certificado caducado y MITM cortó antes de recibirla.
- Ninguna operación nueva puede salir de `Unsupported`.
- No se afirma compatibilidad cloud completa.

## 2. Autorización y alcance

La autorización permite ejecutar el APK original solo en un emulador aislado, aceptar el registro opaco de una sesión de prueba generada por la app y capturar tráfico local.

No se realizó ninguna de estas acciones:

- Usar teléfono físico o su red.
- Reutilizar `wibox-test` para ejecutar el APK.
- Abrir `Add Device`, vincular videoporteros o introducir IDs ficticios.
- Introducir cuentas o secretos del usuario.
- Activar `lock1`, `F1`, llamada, preview o cualquier acción física.
- Modificar firmware o contactar duhow/cmos486 o laboratorios.
- Usar root, Frida, Objection o un traductor instalado manualmente.
- Relajar TLS del cliente Kotlin. En el intento 3 se permitió `ssl_insecure` solo en el proxy de laboratorio para leer el upstream caducado, nunca en el cliente.

Sí se aceptaron los términos dentro del AVD de prueba y se cerró un aviso informativo de Android 10, pasos necesarios para alcanzar la pantalla principal stock. En el intento 3 se autorizó además un QR ficticio de cuatro campos, nunca escaneado de un dispositivo real, para intentar el registro opaco sin confirmar bind final.

## 3. Fuentes oficiales para el intento 2

Las fuentes primarias consultadas y aplicadas son:

1. Android Developers Blog, "Run ARM apps on the Android Emulator":
   https://android-developers.googleblog.com/2020/03/run-arm-apps-on-android-emulator.html
2. Android Emulator release notes:
   https://developer.android.com/studio/releases/emulator

La primera explica que las imágenes Android 11 Google APIs ejecutan el sistema x86 de forma nativa y traducen instrucciones ARM a x86 dentro del proceso de la app. La tecnología está disponible en imágenes Google APIs/Play Store y no exige root ni instalación manual de un traductor.

Para la captura de red se contrastó también la documentación oficial de networking avanzado y consola. Esta distingue el PCAP QEMU de la captura WiFi mediante netsim y documenta `-feature -WiFiPacketStream` para revertir el modelo de red compartido.

## 4. Identidad del APK y ABI

Antes de instalar se verificó el APK original y sus bibliotecas nativas.

| Campo | Valor verificado |
|---|---|
| Paquete | `com.fermax.wibox` |
| Versión | `v2.2`, versionCode `18` |
| Tamaño | `45816991` bytes |
| SHA-256 | `08d78731fe79481b60b4250e736f653cd7af91932844a612b0b5c5a989aac7ce` |
| `native-code` | `arm64-v8a`, `armeabi-v7a` |
| Bibliotecas ARM64 | 14, muestra ELF `e_machine=183` |
| Bibliotecas ARMv7 | 13, muestra ELF `e_machine=40` |
| Bibliotecas x86_64 | 0 |

La identidad coincide con `analysis/apk/inventory.json` y `docs/apk-analysis.md`.

## 5. Intento 1: Android 37 ARM64, bloqueo conservado

| Propiedad | Valor |
|---|---|
| AVD nuevo | `wibox-stock-capture` |
| Serial reservado | `emulator-5556` |
| Imagen | `system-images;android-37.0;default;arm64-v8a`, revisión 6 |
| Host | x86_64 |
| Android arrancado | No |
| APK instalado | No |
| AVD eliminado | Sí |

Error exacto:

```text
Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host. System image must match the host architecture.
```

El error está en `/home/nacho/temp/opencode-work/wibox-dynamic-raw/emulator-console.log`, SHA-256 `79d0947ba2b9ae532abf9b7cdd3536b3fdcd71a3eba46bbe3fcd38a376164e25`.

Este intento no aporta requests, responses, sesión ni discovery. Su AVD fue eliminado y no se alteró `wibox-test`.

## 6. Intento 2: AVD, ABI y aislamiento

| Propiedad | Valor |
|---|---|
| AVD nuevo | `wibox-stock-arm` |
| Serial explícito | `emulator-5558` |
| Imagen | `system-images;android-30;google_apis;x86_64`, revisión 16 |
| AVD reutilizado | No |
| Datos de `wibox-test` reutilizados | No |
| Android arrancado | Sí |
| APK instalado | Sí, `Success` |
| Dispositivos vinculados | 0 |
| Acciones físicas | 0 |
| AVD eliminado al terminar | Sí |

Verificación `getprop` antes de instalar:

```text
sys.boot_completed=1
ro.build.version.sdk=30
ro.product.cpu.abi=x86_64
ro.product.cpu.abilist=x86_64,x86,arm64-v8a,armeabi-v7a,armeabi
ro.product.cpu.abilist32=x86,armeabi-v7a,armeabi
ro.product.cpu.abilist64=x86_64,arm64-v8a
ro.dalvik.vm.isa.arm=x86
ro.dalvik.vm.isa.arm64=x86_64
```

Todos los comandos adb contra Android usaron `adb -s emulator-5558`. No se ejecutó ninguno contra un teléfono físico ni contra `wibox-test`.

## 7. Herramientas

| Herramienta | Versión | Uso |
|---|---|---|
| Android Emulator | 37.2.13.0, build 16740729 | Dos intentos AVD |
| Android 30 Google APIs | Revisión 16 | Traducción ARM oficial |
| adb | 37.0.1-15733141 | ABI, instalación, UI, sockets y limpieza |
| aapt | 0.2-13193326 | Identidad y `native-code` |
| OpenJDK | 17.0.20.1 | Herramientas SDK |
| Python | 3.14.8 | ZIP, ELF, análisis y verificadores |
| mitmproxy | 12.3.1 | Interceptación local del actualizador |
| tcpdump | 4.99.7 | Intento de captura host, sin privilegios suficientes |
| qrencode | Instalado en el host | QR ficticio local de attempt3 |

mitmproxy se instaló en `/home/nacho/temp/opencode-work/wibox-mitm-venv`, fuera del repo. Escuchó solo en loopback del host. Su CA y flows quedaron en el raw externo.

## 8. Procedimiento reproducible

### 8.1 Imagen y AVD del intento 2

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME="$HOME/Android/Sdk"

"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  'system-images;android-30;google_apis;x86_64'

printf 'no\n' | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" \
  create avd \
  -n wibox-stock-arm \
  -k 'system-images;android-30;google_apis;x86_64' \
  -d pixel_6
```

### 8.2 Arranque y comprobación ABI

```bash
"$ANDROID_HOME/emulator/emulator" \
  -avd wibox-stock-arm -port 5558 \
  -no-window -no-audio -no-boot-anim \
  -no-snapshot -no-snapshot-save -no-snapshot-load \
  -no-metrics -gpu swiftshader_indirect -memory 2048 \
  -tcpdump /home/nacho/temp/opencode-work/wibox-dynamic-raw/attempt2/capture.pcap
```

```bash
ADB="$ANDROID_HOME/platform-tools/adb"
"$ADB" -s emulator-5558 wait-for-device
"$ADB" -s emulator-5558 shell getprop ro.product.cpu.abilist
"$ADB" -s emulator-5558 shell getprop ro.product.cpu.abilist64
"$ADB" -s emulator-5558 shell getprop ro.dalvik.vm.isa.arm64
```

### 8.3 Instalación y arranque stock

```bash
"$ADB" -s emulator-5558 install --no-incremental \
  "$HOME/Descargas/wibox-fermax-v2.2/Wibox-Fermax-v2.2.apk"

"$ADB" -s emulator-5558 shell am start -W \
  -n com.fermax.wibox/com.quvii.ubell.main.view.LoadingActivity
```

Resultado: instalación `Success`, `Status: ok`, `LaunchState: COLD`. Después se aceptaron los términos y se cerró el aviso de Android 10. No se abrió el flujo de alta.

### 8.4 Interceptación local

```bash
mitmdump \
  --set confdir=/home/nacho/temp/opencode-work/wibox-dynamic-raw/attempt2/mitm-config \
  --listen-host <loopback> --listen-port 8080 \
  --set flow_detail=3 \
  -w /home/nacho/temp/opencode-work/wibox-dynamic-raw/attempt2/mitm-flows.mitm
```

Proxy solo en el AVD, con el alias especial del host sustituido por un placeholder sanitizado:

```bash
"$ADB" -s emulator-5558 shell settings put global http_proxy <emulator-host-alias>:8080
"$ADB" -s emulator-5558 shell am force-stop com.fermax.wibox
"$ADB" -s emulator-5558 shell am start -W \
  -n com.fermax.wibox/com.quvii.ubell.main.view.LoadingActivity
```

Al terminar:

```bash
"$ADB" -s emulator-5558 shell settings put global http_proxy :0
"$ADB" -s emulator-5558 emu kill
"$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" \
  delete avd -n wibox-stock-arm
```

No se instaló CA de usuario y no se desactivó validación upstream. El cliente Kotlin no fue modificado.

## 9. Secuencia dinámica

Las horas proceden de logcat y MITM. Las variantes de red se agrupan para no confundir observaciones con el resultado de protocolo.

| Orden | Etapa | Resultado observado | Evidencia |
|---:|---|---|---|
| T0 | Intento 1 | ARM64 rechazado antes de Android | `DYN-BLOCK-001` |
| T1 | Limpieza 1 | Primer AVD eliminado | `CLEANUP-001` |
| T2 | AVD intento 2 | Android 30 arranca con ABI ARM expuesta | `DYN2-ABI-001` |
| T3, 07:06:58 | Instalación | APK v18 instalado con `Success` | Paquete y logcat |
| T4, 07:07:27 | Arranque frío | `LoadingActivity`, traducción AArch64 inicializada | `DYN2-START-001` |
| T5, 07:07:35 | Main | `MainTabActivity` y pantalla vacía tras términos | `DYN2-START-001` |
| T6 | Sockets iniciales | Conexiones QVCloud y puerto 1884 por WiFi | `DYN2-DISCOVERY-001` |
| T7, 07:44:01 | Actualizador | MITM captura GET `version_check.jsp` | `DYN2-HTTP-UPGRADE-001` |
| T8 | Reintentos de red | Se confirma cobertura limitada de PCAP y conexiones directas | `DYN2-CAPTURE-LIMIT-001` |
| T9, 07:51 a 07:54 | Arranque final | Sockets de UID Wibox a `fermax:443` y `:1884` | `/proc/net/tcp*` raw |
| T10, 07:56 | Settings | Sin cuenta visible ni dispositivo vinculado | `DYN2-SESSION-001` |
| T11 | Limpieza | Proxy a `:0`, MITM detenido y AVD eliminado | `CLEANUP-002` |

## 10. Request y response sanitizadas

La tabla distingue request observada, response no observada y valores redactados.

| ID | Método | URL host/path | Query keys y valores sanitizados | Headers | Código HTTP | Esquema request | Esquema response |
|---|---|---|---|---|---|---|---|
| HTTP-UPGRADE-001 | GET | `https://matainde.qvcloud.net:1443/webs/version/app/version_check.jsp` | `os=android`, `appid=2021`, `version=18`, `clientid=<redacted>` | `Accept-Language: en-US`, `Host`, `Connection: Keep-Alive`, `Accept-Encoding: gzip`, `User-Agent: okhttp/3.9.0` | No observado | Sin body | No observado |

Error de transporte observado:

```text
Certificate verify failed: certificate has expired
```

El patrón GET aparece en dos arranques del registro MITM. No se inventa un código de respuesta ni un esquema de actualización. No se relajó la validación para obtenerla.

No se observó ninguna otra request HTTP suficientemente decodificada para publicar método y path.

## 11. Sesión, OAuth, Shadow y auth

| Elemento | Resultado dinámico |
|---|---|
| `userFreeRegister` | No observado |
| `userLogin` | No observado |
| Cuenta opaca generada | No observada |
| `/qvoauthv2/token` | No observado |
| Access/refresh tokens | No observados |
| `/auth/nologin` y `/auth/user` | No observados |
| Shadow `/openapi-tdk/device/status*` | No observado |
| MQTT/realtime | No observado |
| Dispositivos vinculados | 0 |
| Cuenta visible en Settings | No |

La evidencia estática E010 sitúa el registro en el flujo de bind. Como no se abrió `Add Device` ni se vinculó nada, la ausencia de sesión no demuestra que el backend lo rechace: demuestra que el flujo autorizado no llegó a ese punto.

## 12. Discovery y sockets

### 12.1 Correlación DNS y UID

`fermax.qvcloud.net` resolvió a `47.254.155.166` y `47.254.177.52`. `matainde.qvcloud.net` resolvió a `47.254.155.166`.

`/proc/net/tcp*` confirmó conexiones `ESTABLISHED` del UID Wibox `10167` a `47.254.177.52:443` y `47.254.151.218:1884`.

| Destino | Puerto | Evidencia | Qué demuestra | Qué no demuestra |
|---|---:|---|---|---|
| `fermax.qvcloud.net`, IP `47.254.177.52` | 443/TCP | DNS, socket y UID Wibox | Conexión HTTPS stock al host bootstrap | Path, request/response, OAuth o login |
| `matainde.qvcloud.net`, IP `47.254.155.166` | 1443/TCP | MITM | Request de actualización | Response o estado de versión |
| `47.254.151.218` | 1884/TCP | Socket y UID Wibox | Canal stock establecido | Nombre del servicio, payload y esquema |
| `47.254.141.132` | 1884/TCP | Socket en otro arranque | Variación dinámica de destino | Respuesta regional o wire format |
| `47.91.95.180` | 1884/TCP | Socket en otro arranque | Variación dinámica de destino | Respuesta regional o wire format |

El destino `:1884` usa TCP en los sockets observados. No se capturaron datagramas UDP atribuibles a Wibox. Tamaños de mensajes, dirección de cada mensaje y bytes quedan sin resolver. No se afirma haber decodificado `QvLocationManager`.

## 13. Comparación con evidencia estática

| Operación | Estático | Dinámico | Conclusión |
|---|---|---|---|
| Bootstrap | E001, E005 | DNS y socket UID a `fermax:443` | Host confirmado, contrato HTTP no observado |
| Registro opaco | E004, E010, REA §20.2 | Attempt3 acepta QR ficticio, pero no supera la consulta online | Sin `userFreeRegister`/`userLogin`, sin bind ni rechazo de servidor decodificado |
| OAuth | E029, E033 a E036 | No observado | GET/token/grants no validados en runtime |
| Discovery | E034, E040, REA §20.2 | TCP `:1884` y destinos variables | Parcial, falta respuesta y mapeo regional |
| Shadow | E041, E063, E073 | No observado | Sin activación ni respuestas |
| Rutas auth | `HttpDeviceConst.java:29-44`, APK §5 | No observado | Cookies, XML y respuestas desconocidos |
| P2P nativo | E042 | Traducción ARM y socket TCP | Native ejecutado, wire format pendiente |
| Upgrade | E050 | Attempt2 captura GET; attempt3 obtiene HTTP 200 y XML `result=0` | Request y response de éxito confirmados, otros casos pendientes |
| Acciones físicas | E019, E023, E025 | No intentadas | Siguen fuera de alcance |

La request de actualización coincide con E050. No hay discrepancia de protocolo en esa parte. La caducidad del certificado es un hecho de la conexión MITM y no se extrapola a todo QVCloud.

## 14. Intento 3: QR ficticio, registro y primera response completa

### 14.1 Método y aislamiento

Se creó un AVD Android 30 nuevo, nuevamente llamado `wibox-stock-arm` y siempre dirigido con `adb -s emulator-5558`. La imagen y las ABI fueron las mismas que en attempt2. El APK se instaló con `Success`.

El QR se generó localmente con cuatro campos separados por espacios:

```text
apName=<fictitious-ap-name> uid=<fictitious-uid> authCode=<fictitious-auth-code> model=TESTMODEL
```

El UID está reservado como inexistente y el authCode es ficticio. No proceden de un dispositivo ni del raw anterior. La imagen se generó con `qrencode` y se seleccionó mediante DocumentsUI. No se usó cámara física, `Add Online` ni discovery LAN.

La pantalla `DADeviceBindActivity` quedó con UID y password ficticios antes de pulsar una sola vez `Save`. No se pulsó ninguna confirmación posterior de vinculación.

### 14.2 Resultado de registro y discovery

El presenter ejecuta `queryDeviceOnlineState` antes de llamar a bind. En este intento se observaron conexiones directas periódicas a `47.254.155.166:443` y al canal TCP `47.254.141.132:1884`, pero el flujo no llegó a `userFreeRegister` ni `userLogin`.

| Elemento | Resultado attempt3 |
|---|---|
| QR aceptado en formulario | Sí |
| Consulta online iniciada | Sí |
| `userFreeRegister` | No activado |
| `userLogin` | No activado |
| Cuenta opaca | No generada |
| Tokens OAuth | Ninguno |
| Bind | No realizado |
| Rechazo de servidor decodificado | No observado |
| Acción física | Ninguna |

No se afirma que el servidor haya rechazado el UID: no se decodificó una respuesta que lo demuestre. El límite medido es que el flujo no superó la fase online previa. Las consultas fueron detenidas con `am force-stop`, sin falsificar un estado online.

### 14.3 Request y response del actualizador

La excepción `ssl_insecure=true` se aplicó solo al proxy de laboratorio autorizado para este intento, para leer el upstream caducado. No se modificó TLS del cliente Kotlin ni se instalaron credenciales reales.

| ID | Método | URL host/path | Query keys y valores | Headers request | Código | Esquema response |
|---|---|---|---|---|---:|---|
| HTTP-UPGRADE-002 | GET | `https://matainde.qvcloud.net:1443/webs/version/app/version_check.jsp` | `os=android`, `appid=2021`, `version=18`, `clientid=<redacted>` | `Accept-Language: en-US`, `Host`, `Connection: Keep-Alive`, `Accept-Encoding: gzip`, `User-Agent: okhttp/3.9.0` | 200 | XML `envelope`, 308 bytes |

Response headers: `Content-Type: text/xml;charset=UTF-8` y `Transfer-Encoding: chunked`. Existió `Set-Cookie`, con valor conservado solo en raw y publicado como `<redacted>`.

Esquema XML observado:

```text
envelope
  header
    flag = tdkcloud
    result = 0
  content
    priority = 0
    version = 5.1.11
    url = vacío
    description = vacío
    config
      version = 0
      url = vacío
      picture = vacío
```

Esta es la primera operación de la fase con request y response de éxito observados.

## 15. Operaciones que pueden salir de `Unsupported`

**Solo `app_upgrade_check`, y únicamente para el contrato observado.**

- Demostrado: método GET, host, puerto 1443, path, query keys, headers, HTTP 200, headers de respuesta y esquema XML de éxito.
- Pendiente: respuestas no 200, errores del proveedor, semántica de decisión de actualización y política TLS estricta de producción.
- `unsupported_updates` contiene solo esta operación con alcance `request and HTTP 200 XML success response`.

**`discoverServices` permanece `Unsupported`:**

- Demostrado: conexiones TCP al puerto 1884, destinos variables y consultas online.
- Pendiente: respuesta `QvLocationManager`, mapeo regional, payload, UDP/P2P y activación MQTT.

Registro, login, OAuth, lista, realtime, Shadow, media, rutas auth y acciones físicas también siguen sin evidencia suficiente.

## 16. Límites de captura y bloqueos concretos

### 16.1 PCAP QEMU

Los PCAP generados por `-tcpdump` cubren `eth0`. Wibox usa `wlan0` para QVCloud, por lo que esos archivos no contienen sus conexiones. No se usa esa ausencia como prueba de inactividad.

Se conservaron como diagnóstico de instrumentación, no como evidencia de requests.

### 16.2 Captura en el host

El intento de captura en el host falló exactamente con:

```text
Attempt to create packet socket failed - CAP_NET_RAW may be required
```

No se usó root, `sudo` ni `pkexec`.

### 16.3 Netsim

La forma `-netsim-args=--pcap` fue rechazada por sintaxis. `-netsim-args "--pcap"` arrancó, pero el netsimd compartido ya existía y no generó un PCAP nuevo. No se reinició el daemon porque podría afectar a `wibox-test`.

### 16.4 MITM y upstream

En attempt2, el certificado caducado del upstream impidió observar la response del actualizador. En attempt3, la excepción limitada al proxy permitió obtener HTTP 200 y el XML completo. Otras conexiones QVCloud continuaron directas y no fueron descifradas.

### 16.5 Registro y OAuth

Attempt2 no llegó al alta. Attempt3 introdujo un QR ficticio y activó la consulta online, pero no llegó a `userFreeRegister`. No se falsificó un estado online ni se amplió el alcance para obtener una sesión.

## 17. Raw y sanitización

Todo el raw permanece fuera del repo:

- Intento 1: `/home/nacho/temp/opencode-work/wibox-dynamic-raw/`.
- Intento 2: `/home/nacho/temp/opencode-work/wibox-dynamic-raw/attempt2/`.
- Intento 3: `/home/nacho/temp/opencode-work/wibox-dynamic-raw/attempt3/`.

El manifiesto `/home/nacho/temp/opencode-work/wibox-dynamic-raw/attempt2/raw-manifest.json` contiene 66 archivos con nombre, tamaño y SHA-256. Su propio SHA-256 es `dc8f171adad9a9c6dd4ae5f391075114763f3e5a1d804ad3f46ce4e22d335e38`.

El manifiesto `/home/nacho/temp/opencode-work/wibox-dynamic-raw/attempt3/raw-manifest.json` contiene 55 archivos. Su propio SHA-256 es `1072339bddf5e98f0cfd7ed5b770f8b1b0977f2f4ceb453696c23650140a239f`.

Datos sensibles identificados:

- `mitm-config`: CA y material privado local.
- `mitm-flows.mitm` y `mitmdump-console.log`: `clientid` opaco, cookie de respuesta y metadatos.
- PCAP, logcat, getprop, sockets, `/proc/net/tcp*`, capturas y XML de UI.
- Almacenamiento AVD eliminado, no copiado al repo.

Dentro del workspace solo hay documentación, JSON, Python y TXT sanitizados. No hay PCAP, flows, CA, capturas ni valores reales de `clientid`, tokens, cookies, passwords o IDs opacos.

## 18. Limpieza verificada

- Proxy global restablecido a `:0` antes de detener cada AVD.
- mitmdump detenido tras cada captura.
- `wibox-stock-arm` eliminado tras attempt2 y recreado/eliminado de nuevo tras attempt3.
- `wibox-stock-capture` ya estaba eliminado en el intento 1.
- `emulator -list-avds` muestra solo `revela_test` y `wibox-test`.
- `wibox-test` permanece activo y sin modificaciones.
- No queda proceso QEMU de los AVD temporales.

## 19. Segunda revisión de seguridad y correspondencia

Se revisaron E001, E004, E005, E010, E019, E023, E025, E029, E033 a E036, E040 a E042, E050, E063, E073, `HttpDeviceConst.java:29-44` y REA §20.2:

- La traducción ARM oficial no se confunde con root ni traducción manual.
- La ausencia de OAuth no se convierte en ausencia de funcionalidad en el servidor.
- Los sockets `:1884` no se presentan como respuesta de discovery decodificada.
- La request y la response de éxito de upgrade coinciden con estático; los demás casos siguen desconocidos.
- El QR ficticio no produjo registro, bind ni tokens OAuth, y no se confunde con un dispositivo real.
- La excepción `ssl_insecure` queda limitada al proxy de attempt3 y no altera el cliente Kotlin.
- Los PCAP incompletos no sustentan conclusiones negativas.
- No se intentaron acciones físicas ni vinculación.
- Todo dato sensible permanece fuera del repo.

## 20. Verificación

```bash
python3 analysis/dynamic/verify_dynamic.py
python3 analysis/dynamic/verify_sanitized.py
```

Salidas únicas esperadas y medidas:

```text
WIBOX_DYNAMIC_ANALYSIS_VERIFIED
WIBOX_DYNAMIC_SANITIZED_VERIFIED
```

Comandos, resultados, hashes y limpieza se registran en `analysis/dynamic/verification.txt`.
