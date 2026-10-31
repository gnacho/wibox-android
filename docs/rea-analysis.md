# Analisis de REA y fuentes Wibox para un clon mantenible en Kotlin

## 1. Resumen ejecutivo

**HECHO:** `https://github.com/morluto/rea`, rama `main`, commit `d48e472fa6a664cacbb400560e673533005795ce`, es **REA: Reverse Engineer Anything**, una herramienta local de ingenieria inversa con CLI y MCP. No es un cliente Wibox ni un SDK Fermax (`README.md:6-10`, `package.json:2-10`). En su arbol no aparecen `wibox`, `fermax` ni `duox`: `git grep -in -e wibox -e fermax -e duox HEAD` termino con exit 1 y sin salida.

**HECHO:** REA es util para inspeccionar el APK y capturas historicas, no como especificacion cloud. Ofrece analisis estatico Android y lectura offline de HAR, SAZ y pcap (`README.md:187-229`, `docs/web-network-captures.md:1-6`, `docs/pcap-analysis.md:1-6`).

**HECHO DE CODIGO, fuente adicional:** `duhow/wibox`, commit `d8502d780f1d4ac3f2ec1a8425a98588fd7f53a7`, si contiene una implementacion local para Wibox parcheado. Sus scripts confirman comandos UART de cuatro bytes, CGI HTTP y topics MQTT para apertura, llamada, desvio, F1, timbre y Home Assistant (`include/wibox_codes.txt:1-24`, `include/web/run.cgi:1-21`, `include/bin/listener_mqtt.sh:37-88`). Esto prueba el comportamiento del **firmware parcheado**, no la API cloud del APK original.

**DOCUMENTACION DE TERCERO:** `duhow/wibox` tambien documenta un agente NTS cloud, una IP con UDP 8300 y HTTPS 443, autenticacion con `DevId` y `AuthPwd`, registro y conexion P2P. Son notas y un log pegado en `docs/system.md:148-193`, no una captura o binario que hayamos contrastado. No se contacto ese servicio.

**AFIRMACION DE TERCERO:** el blog de cmos486 describe otro firmware custom con RTSP, MQTT y audio bidireccional WebRTC directo, sin depender de SIP para su tarjeta. Es evidencia sobre `cmos486/wibox-media`, no sobre el firmware o el cloud de fabrica. Fuente: [cmos486, "Firmware custom para el Fermax WiBox"](https://www.cmos486.es/blog/wibox-media-fermax-firmware-home-assistant), secciones "Implementado en el custom firmware" y "La tarjeta de Home Assistant".

**Conclusion:** no hay todavia una especificacion completa del cloud Wibox del APK. Si hay evidencia aprovechable para acotar dominio y protocolos locales. `core:cloud` no debe adoptar los topics del parche ni el WebRTC del fork como si fueran el contrato del fabricante.

## 2. Fuentes, revisiones, licencias y metodo

### 2.1 Fuentes fijadas

| Fuente | Revision y papel | Licencia y reutilizacion |
|---|---|---|
| `https://github.com/morluto/rea` | `main`, `d48e472fa6a664cacbb400560e673533005795ce`, paquete `rea-agents` 6.8.0. Herramienta de analisis | MIT, `LICENSE:1-13`. Solo identificadores y hechos |
| `https://github.com/duhow/wibox#1` | `main`, `d8502d780f1d4ac3f2ec1a8425a98588fd7f53a7`. Firmware parcheado y notas Wibox | GPL-3.0, `LICENSE:1-18`. No se copia codigo sustancial |
| `https://www.cmos486.es/blog/wibox-media-fermax-firmware-home-assistant` | Articulo publicado 2026-09-12, leido 2026-10-31. Custom `cmos486/wibox-media` | Se resume con enlace. No se reutiliza codigo |

Para `morluto/rea`, `git remote get-url origin`, `git branch --show-current` y `git rev-parse HEAD` confirmaron origen y revision. Su fecha es `2026-10-31T03:13:07+08:00`. Para `duhow/wibox`, los mismos comandos confirmaron el commit indicado, con fecha `2026-10-18T15:52:35+02:00`. Ambos clones quedaron en `/home/nacho/temp/opencode-work/rea-reference` y `/home/nacho/temp/opencode-work/duhow-wibox-reference`, respectivamente, con `git status --short` sin cambios.

El fragmento `#1` de la URL aportada para duhow se conserva como identificador de la peticion. El analisis cubre la raiz del repositorio en el commit fijado, no un issue extraido de ese fragmento.

### 2.2 Taxonomia de evidencia

- **HECHO DE CODIGO:** se ve directamente en un script o contrato del commit citado. No implica ejecucion ni prueba en hardware.
- **HECHO DOCUMENTADO:** un repositorio lo afirma en documentacion o en un log conservado. Se cita, pero no se eleva a comportamiento verificado por nosotros.
- **AFIRMACION DE TERCERO:** procede del blog o de otro proyecto enlazado y no se ha contrastado con su codigo.
- **HIPOTESIS:** interpretacion pendiente de prueba. Nunca se usa para completar un endpoint o secuencia.
- **PROPUESTA:** recomendacion para Kotlin, no comportamiento atribuido a Wibox.

### 2.3 Cobertura y limites

- Solo analisis estatico y lectura publica. No se uso ninguna credencial, no se ejecuto firmware y no se contacto con Wibox, NTS, MQTT, SIP o WebRTC real.
- No habia un APK Wibox ni una captura de red propia. Por tanto, ninguna fuente queda correlacionada con `applicationId`, version o certificado del APK objetivo.
- En REA se revisaron 6860 archivos seguidos, incluidos 5429 `.ts`, con `git ls-files`. No contiene `.kt` ni `.kts`.
- Los cinco submodulos de REA son motores genericos de analisis y no se inicializaron (`.gitmodules:1-15`). No aportan evidencia Wibox.
- duhow no incluye el binario propietario Sofia ni `npc_nts_client_config.ini`. Sus afirmaciones cloud no pueden completarse solo con los scripts presentes.
- Las versiones de firmware probadas por duhow son `V500.R001.A103.00.G0021.B007` y `V500.R001.A103.00.G0021.B013` (`README.md:17-19`). No se deduce compatibilidad universal.

## 3. Comparabilidad entre fuentes, APK y firmware

| Objeto | Que demuestra | Que no demuestra |
|---|---|---|
| REA | Como inspeccionar artefactos y conservar evidencia | Ningun endpoint, token o accion Wibox |
| Scripts duhow | Comportamiento local del firmware parcheado en el commit | API del APK, nube de fabrica o comportamiento de todas las versiones |
| Notas `docs/system.md` duhow | Hallazgos de tercero sobre Sofia, NTS, puertos y secretos | Que el APK actual use exactamente esos mensajes o que la IP siga vigente |
| Blog cmos486 | Funciones declaradas de un fork custom posterior | Protocolo cloud original, autenticacion del APK o necesidad de WebRTC en fabrica |
| APK objetivo, aun no analizado | Sera la referencia primaria para la app que se quiere clonar | No puede sustituirse por analogias con estos proyectos |

**HECHO:** REA no esta asociado a ninguna version Wibox. Su `jadxApp` solo define campos genericos `package`, `version_name`, `version_code`, SDK y permisos que podra extraer del APK (`src/android/JadxProtocol.ts:73-80`).

**HIPOTESIS:** REA pudo aportarse como herramienta para producir la referencia desde el APK, no como implementacion de referencia. Falta contexto para confirmarlo y no afecta a las conclusiones tecnicas.

## 4. Arquitectura y lenguajes de REA

REA organiza las dependencias hacia dominio. El dispatcher selecciona CLI o MCP, la composicion crea proveedores, el servidor traduce peticiones y la aplicacion comparte workflows. Contratos y dominio contienen esquemas y semantica (`docs/architecture.mermaid:4-28`, `docs/architecture.mermaid:72-83`).

| Componente | Responsabilidad | Evidencia |
|---|---|---|
| `scripts/rea.mjs`, `src/cli.ts` | CLI y seleccion MCP stdio/HTTP | `scripts/rea.mjs:9-20`, `scripts/rea.mjs:31-68` |
| `src/main.ts`, `src/server/` | MCP, sesiones y transporte | `src/main.ts:41-69` |
| `src/application/` | Workflows compartidos | `docs/architecture.mermaid:21-28` |
| `src/contracts/`, `src/domain/` | Esquemas, Evidence y errores | `src/contracts/errorSchemas.ts:37-38`, `src/domain/evidence.ts:84-107` |
| `src/android/`, `bridge/android/` | APK estatico con JADX externo | `docs/android-analysis.md:1-14` |
| `src/captures/`, `src/pcap/` | Capturas guardadas | `docs/pcap-analysis.md:1-6` |
| `bridge/`, `native/` | Puentes Python, Java, Swift y nativos | `package.json:49-108`, `docs/architecture.mermaid:65-69` |

**Valor para Kotlin:** la separacion entre contratos, dominio y adaptadores es reutilizable como idea. No hay logica Wibox que portar ni archivos Kotlin en REA.

## 5. Matriz de funciones Wibox por procedencia

| Funcion | Fabrica, documentacion duhow | Custom duhow, codigo | Blog cmos486 | Estado para clon del APK |
|---|---|---|---|---|
| Login cloud | NTS inicia autenticacion y registro (`docs/system.md:168-179`) | No aplica | No tratado | Sin request/response de APK |
| Renovacion de token | No documentada | No aplica | No tratado | Desconocida |
| Alta o pairing | `DevId`, `AuthPwd` y Verify Code (`docs/system.md:148-153`) | AP local para configurar WiFi (`include/bin/ap_start.sh:13-28`) | Configuracion WiFi tras flashear | Falta secuencia original |
| Descubrimiento LAN | UDP 5000 y cadena `ASZENO.SEARCH.V4.1` (`docs/system.md:276-290`) | Heredado de Sofia, no reimplementado | No tratado | Candidato a contrastar |
| Apertura | No hay mensaje cloud | UART y MQTT/HTTP confirmados (`include/web/run.cgi:5-14`, `include/bin/listener_mqtt.sh:45-50`) | Apertura declarada | Dominio plausible, transporte cloud desconocido |
| Iniciar/terminar llamada | No hay mensaje cloud | `START_CALL` y `STOP_CALL` (`include/wibox_codes.txt:19-21`) | Llamada WebRTC custom | Falta secuencia original |
| F1 y guardia | No hay mensaje cloud | `START_F1`, `STOP_F1`, `CALL_GUARD` (`include/wibox_codes.txt:17-23`) | No es foco | No asumir equivalencia cloud |
| Desvio | No hay mensaje cloud | `PUSH_STATE_0/1` y topic `forward` (`include/bin/listener.sh:147-155`) | No es foco | No asumir equivalencia cloud |
| Timbre y estados | No hay eventos cloud | `ALARM_REPORT`, `HANG_UP`, `CMD_STOP_RING` (`docs/codes.md:18-23`, `docs/codes.md:34-38`) | Eventos declarados | Modelos cloud desconocidos |
| Audio/video | No queda especificado | Audio marcado pendiente en README (`README.md:9-15`) | RTSP y WebRTC declarados | No trasladar WebRTC al APK |
| SIP | No implementado en duhow | No hay cliente SIP en el arbol | Daemon SIP de otro fork permanece, pero no es foco | Sin evidencia de SIP cloud |
| MQTT | No es el cloud NTS documentado | Plano 1883 y topics locales (`USAGE.md:10-25`) | MQTT para HA declarado | No usar como cloud de fabrica |

**HECHO:** esta matriz no demuestra que el fabricante carezca de funciones. Separa lo confirmado para cada implementacion y evita cubrir huecos por analogia.

## 6. Endpoints, transportes y modelos

### 6.1 Cloud y Sofia, solo documentacion duhow

El NTS Client se describe como intermediario entre cloud y el puerto local de management `TCP/34567`. Requiere `AuthPwd`, que puede pasarse con `-E`, y toma `DevId` de almacenamiento persistente (`docs/system.md:148-153`).

Secuencia documentada:

1. Inicio del agente y deteccion de red/NAT.
2. Conexion a autenticacion por HTTPS y login.
3. Registro del dispositivo en el servidor.
4. Solicitud P2P y creacion de conexiones TCP logicas (`docs/system.md:168-193`).

| Endpoint o canal | Valor documentado | Clase de evidencia y limite |
|---|---|---|
| Cloud UDP | `47.254.155.166:8300` | Log de tercero, `docs/system.md:168-175`. No contactado ni verificado vigente |
| Cloud HTTPS | `47.254.155.166:443` | Mismo limite. No hay ruta HTTP, headers o cuerpo |
| Management local | `TCP/34567` | `docs/system.md:75-81`. No hay esquema de mensajes en el repo |
| Web Sofia | `TCP/80` y `TCP/443` | `docs/system.md:75-81`. ONVIF aparece deshabilitado |
| Autodiscovery | `UDP/5000` | `docs/system.md:75-81`, `docs/system.md:276-290` |
| UDP adicional | `UDP/5002` | Proposito desconocido, `docs/system.md:75-81` |
| `system_sofia` | `TCP/6683`, loopback segun notas | Paquetes de 1032 bytes, `docs/system.md:103-112` |
| `interDebug` | `TCP/9527` tras señal | Consola de debug, `docs/system.md:195-212` |

**HECHO DOCUMENTADO:** la configuracion cloud se asocia a `npc_nts_client_config.ini` y estados `/var/cloud/states` (`docs/system.md:59-60`, `docs/system.md:95-101`). Esos archivos no estan incluidos para inspeccion, por lo que no se conocen rutas, claves o formato completos.

### 6.2 HTTP local del firmware parcheado, confirmado en codigo

`setup.sh` arranca BusyBox httpd en puerto 80, o 81 si 80 esta ocupado (`include/web/setup.sh:13-18`). El front-end llama `./cgi-bin/run.cgi?c=` (`include/web/index.html:38-63`).

| Comando CGI | Efecto confirmado | Respuesta o limite |
|---|---|---|
| `open` | Escribe `TRANSFER_CMD_UNLOCK_DOOR` en UART | No hay ack fisico ni JSON (`include/web/run.cgi:5-6`) |
| `dspy` | Escribe `START_CALL` | `include/web/run.cgi:7-8` |
| `dspn` | Escribe `STOP_CALL` | `include/web/run.cgi:7-8` |
| `fone` | Activa F1, espera 3 segundos y desactiva | `include/web/run.cgi:9-10` |
| `guard` | Escribe `CALL_GUARD` | `include/web/run.cgi:9-10` |
| `unlock` | Inicia llamada, abre a los 2 segundos y cierra 1 segundo despues | Secuencia temporizada, no confirmacion de cerradura (`include/web/run.cgi:12-14`) |

El CGI devuelve `Content-type: text/html` si la funcion termina, 404 si el comando no existe y 500 si falla la escritura UART (`include/web/run.cgi:5-21`). No hay autenticacion visible en `setup.sh` ni en el CGI. Esto es un riesgo del parche si se expone la red, no una prueba de que el cloud de fabrica carezca de autenticacion.

### 6.3 MQTT local del firmware parcheado, confirmado en codigo

La raiz es `wibox/${MODEL}`, el client id es `wibox_${MODEL}` y las credenciales opcionales se leen de `/mnt/mtd/mqtt.conf` (`include/bin/mqtt_functions.sh:9-30`). La guia dice que se probo MQTT plano en 1883 (`USAGE.md:10-25`) y el build usa `WITH_TLS=no` (`scripts/81_mosquitto.sh:59-63`).

| Topic bajo `wibox/${MODEL}` | Payload y accion | Evidencia |
|---|---|---|
| `/door/set` | `ON` o `PRESS` escribe unlock. Publica `OFF` al segundo | `include/bin/listener_mqtt.sh:42-51` |
| `/door` | `ON/ONLINE` inicia llamada, `OFF/OFFLINE` la termina | `include/bin/listener_mqtt.sh:52-63` |
| `/forward` | `ON/OFF` cambia desvio con `PUSH_STATE_1/0` | `include/bin/listener_mqtt.sh:64-75` |
| `/f1` | `ON`, `OFF` o `PRESS`, este ultimo con pulso de 3 segundos | `include/bin/listener_mqtt.sh:77-88` |
| `/ding` | `ON/OFF` para timbre | `include/bin/listener.sh:122-140` |
| `/ding/last` | Timestamp retenido de la ultima llamada | `include/bin/listener.sh:122-127` |
| Raiz | `init`, `config`, `online`, `offline`, will retenido y keepalive 300 | `include/bin/listener_mqtt.sh:37-43`, `include/bin/listener.sh:65-74` |
| `/wifi/stats` | JSON con metricas, incluida RSSI | `include/bin/mqtt_wifi_stats.sh:6-26` |

Home Assistant discovery usa `homeassistant/<type>/<unique_id>/config` y publica entidades de puerta, F1, llamada, desvio, timbre, ultima llamada y calidad WiFi (`include/bin/mqtt_config_homeassistant.sh:5-16`, `include/bin/mqtt_config_homeassistant.sh:64-98`).

### 6.4 UART local, confirmado en codigo y tabla

El dispositivo es `/dev/ttySGK1`. El listener lee cuatro bytes por codigo (`include/bin/listener.sh:3-7`, `include/bin/listener.sh:86-99`). La documentacion describe prefijo `FB` y un checksum por suma, con ejemplos y significados (`docs/codes.md:1-12`).

| Nombre | Frame | Direccion y funcion |
|---|---|---|
| `ALARM_REPORT` | `FB 11 00 1C` | Entrada, llamada desde calle |
| `TRANSFER_CMD_UNLOCK_DOOR` | `FB 12 01 1E` | Salida, apertura rele 1 |
| `HANG_UP_0` | `FB 13 00 1E` | Entrada, timeout sin respuesta documentado como 30 segundos |
| `START_CALL` | `FB 14 01 20` | Entrada/salida, inicio de llamada |
| `STOP_CALL` | `FB 14 00 1F` | Salida, fin de llamada |
| `CALL_GUARD` | `FB 15 00 20` | Salida, llamada a guardia |
| `START_F1`, `STOP_F1` | `FB 17 01 23`, `FB 17 00 22` | Salida, rele F1 |
| `PUSH_STATE_0`, `PUSH_STATE_1` | `FB 19 00 24`, `FB 19 01 25` | Entrada/salida, deshabilita o habilita WiFi/llamadas |
| `CMD_STOP_RING` | `FB 23 00 2E` | Entrada, llamada tomada por telefono fisico |

La fuente de frames es `include/wibox_codes.txt:1-24` y la interpretacion `docs/codes.md:12-39`. Los campos marcados `unknown` deben permanecer desconocidos.

**Cobertura real del listener:** el fichero de codigos no equivale a una lista de funciones soportadas. `listener.sh` si maneja `CMD_RESET` para volver a fabrica (`include/bin/listener.sh:92-99`), pero no tiene ramas para todos los codigos definidos. Por ejemplo, `HANG_UP_1` esta definido en `include/wibox_codes.txt:4-6` y no tiene un `elif is_code HANG_UP_1` en `include/bin/listener.sh:86-159`. Otros codigos como `MCU_STATE`, `SAVE_ADDR` o `CALL_GUARD_ERROR_2` quedan como observaciones, no como una maquina de estados completa.

### 6.5 Modelos REA que no son Wibox

| Modelo | Proposito real | Evidencia |
|---|---|---|
| `Evidence` | Procedencia con `evidence_id`, `provider`, `normalized_result`, `confidence`, `authority` y `limitations` | `src/domain/evidence.ts:84-107` |
| `AnalysisErrorProjection` | Error REA con `code`, `category`, `retryable` y `remediation` | `src/contracts/errorSchemas.ts:7-38` |
| `jadxApp` | Metadatos genericos de un APK | `src/android/JadxProtocol.ts:73-80` |
| ADB/Frida device | Dispositivos de analisis, no videoporteros | `src/domain/adb/adbDeviceAnalysis.ts:471-485`, `src/domain/fridaDiscovery.ts:3-13` |

## 7. Autenticacion, pairing, credenciales y tokens

### 7.1 Cloud de fabrica

**HECHO DOCUMENTADO:** NTS utiliza `DevId` y un Verify Code `AuthPwd`. El arranque con `-E ${m_dVerifyCode}` aparece en la lista de comandos de Sofia (`docs/system.md:130-146`) y el requisito se explica en `docs/system.md:148-153`.

**DESCONOCIDO:** no hay peticion de login, respuesta, access token, refresh token, expiracion, logout o renovacion. La frase "Device is registered" en el log (`docs/system.md:173-179`) no permite reconstruir pairing ni los mensajes intercambiados.

### 7.2 Secreto persistente documentado

`docs/system.md` situa secretos codificados en base64 en `/mnt/mtd/Config/secret` (`docs/system.md:305-318`). Base64 es codificacion, no prueba de cifrado.

| Campo | Interpretacion de la documentacion | Certeza |
|---|---|---|
| `TD` y `TD5` | `deviceToken` dividido en secciones | Documentado, sin formato de mensaje ni expiracion |
| `K1`, `K2` | Dos claves alfanumericas de 32 caracteres | Documentado, uso desconocido |
| `VC` | Verify Code impreso en el QR del hardware | Documentado |
| `DVC` | Device Verify Code, `innerAuthCode` o `m_dVerifyCode` | Documentado |
| `HVC` | Hash Verify Code | El texto dice "Apparently a SHA256", algoritmo no confirmado |
| `FD` | `Activated` o `NotActive` | Documentado |
| `ASC`, `RST` | Flags | Significado incierto incluso en las notas |

No se copian valores. Estos campos no justifican llamar OAuth o JWT al mecanismo ni inventar una politica de renovacion.

### 7.3 Credenciales WiFi, shell y MQTT

- La guia duhow documenta passwords root de fabrica distintos segun consola (`INSTALL.md:39-52`). No se usaron ni se reproducen aqui.
- La misma guia dice que las credenciales WiFi de Sofia estan cifradas y que no podian recuperarlas (`INSTALL.md:90-107`). No identifica algoritmo ni clave.
- El parche guarda MQTT en `/mnt/mtd/mqtt.conf`, lo copia a `/tmp`, exporta valores con `xargs` y los pasa como argumentos `-u` y `-P` (`include/bin/mqtt_functions.sh:9-26`). Esto expone una superficie de secreto en ficheros y linea de comandos que no debe copiarse en Kotlin.
- El AP del parche deriva SSID `IDS7938` mas cuatro caracteres del UDID, password del UDID completo e IP `192.168.111.1` (`include/bin/ap_start.sh:13-28`). Es provisioning local, no pairing cloud. Si el UDID es observable, no es un secreto robusto.
- `STA_TO_AP` se documenta como el codigo recibido tras mantener el boton mas de 5 segundos, que provoca reinicio y modo AP (`docs/codes.md:34-35`). Es un mecanismo fisico de provisioning documentado, no un alto de cuenta cloud.

### 7.4 REA no aporta autenticacion Wibox

La sesion UUID de MCP se crea con `randomUUID()` tras `initialize` (`src/server/http/serveStreamableHttp.ts:201-220`, `src/server/http/serveStreamableHttp.ts:404-417`). No es login de usuario. La configuracion REA solo selecciona motores, rutas, limites y timeouts (`src/config/environment.ts:47-134`). Las listas de nombres sensibles sirven para excluir credenciales de capturas, no para autenticar (`src/application/environment/environmentBuildReport.ts:23-79`).

## 8. Descubrimiento, dispositivos, acciones y estados

### 8.1 Autodiscovery documentado

Las notas proponen escucha UDP y broadcast de la cadena `ASZENO.SEARCH.V4.1` hacia puerto 5000. La respuesta pegada incluye version B007 (`docs/system.md:276-290`). No hay datagrama binario completo, esquema de respuesta, autenticacion ni prueba con el APK.

### 8.2 Acciones y eventos del parche

El listener convierte eventos UART en MQTT y alarmas locales. `ALARM_REPORT` publica timbre y timestamp, `HANG_UP_0` marca llamada perdida y `CMD_STOP_RING` indica que se descolgo el telefono fisico (`include/bin/listener.sh:122-146`). Los estados de alarma visibles son `Ring`, `Missed`, `Answered` y `Open door` (`include/web/index.html:38-45`).

La apertura automatica puede encadenar `ALARM_REPORT`, `START_CALL`, unlock y `STOP_CALL` cuando existen ciertos flags (`include/bin/listener.sh:108-135`). Es una maquina de estados del parche, no una maquina de estados cloud confirmada.

### 8.3 Lista de dispositivos

No hay un modelo cloud de instalaciones o dispositivos en duhow. Los modelos ADB y Frida de REA no llenan ese hueco. El identificador `MODEL` de MQTT se deriva del hostname, no demuestra el `DevId` o el esquema de identidad del fabricante (`include/bin/mqtt_functions.sh:20-30`).

### 8.4 Direccion VDS, distinta del pairing cloud

duhow registra `SAVE_ADDR 0x04` tras configurar con el boton P2 y pulsar el portero (`docs/codes.md:30-31`). El blog cmos486 afirma que el WiBox necesita una direccion VDS para escuchar el pulsador correcto y que una direccion incorrecta impide recibir llamadas sin error visible. Esta direccion identifica el destino en el bus VDS segun esa fuente. No debe modelarse como `deviceId` cloud ni como prueba de emparejamiento NTS sin evidencia adicional. Fuente del blog: [cmos486](https://www.cmos486.es/blog/wibox-media-fermax-firmware-home-assistant), "Paso 7: dile que piso es".

## 9. Tiempo real: MQTT, SIP, WebRTC y otros canales

| Canal | Que esta confirmado | Limite de aplicabilidad |
|---|---|---|
| NTS/P2P | Log documenta P2P y TCP logico tras autenticacion (`docs/system.md:184-193`) | No hay wire format ni relacion demostrada con el APK actual |
| MQTT duhow | Pub/sub local, will, retained, keepalive y topics de accion/estado | Firmware parcheado, no cloud de fabrica |
| WebSocket REA | CDP loopback (`src/browser/CdpConnection.ts:79-113`) | Herramienta de analisis, no Wibox |
| SSE REA | Streamable MCP (`src/server/http/serveStreamableHttp.ts:180-199`) | Herramienta de analisis, no Wibox |
| SIP | Blog dice que el daemon del fork segator sigue disponible | Afirmacion de tercero sobre custom. No prueba SIP en stock ni da endpoints |
| WebRTC | Blog declara audio bidireccional directo contra `cmos486/wibox-media` y tarjeta HA | No contrastado con codigo y no aplicable automaticamente al APK |
| RTSP | Blog declara video H.264 por RTSP en custom | Igual que el anterior |

Fuente del blog para SIP, WebRTC y RTSP: [cmos486](https://www.cmos486.es/blog/wibox-media-fermax-firmware-home-assistant), secciones "Implementado en el custom firmware", "El origen de todo" y "La tarjeta de Home Assistant".

**DESCONOCIDO:** ninguna fuente aporta registrar o URI SIP, extension, password, negociacion SDP, signalling WebRTC, servidores STUN/TURN, credenciales ICE, URL o credenciales RTSP. Tampoco se detalla el canal de eventos cloud original. El blog describe como maximo la intencion y arquitectura declarada del fork, no un contrato implementable de media.

**HIPOTESIS:** el sistema original puede combinar cloud, canal local y media, pero las fuentes no permiten decidir la topologia exacta del APK. La prueba debe salir de bibliotecas, clases, manifiesto y trafico de la version objetivo.

## 10. Criptografia y proteccion de comunicaciones

- **Cloud:** `AuthPwd`, token y campos `K1/K2` estan documentados, pero no su uso criptografico. `HVC` solo se sospecha SHA-256 (`docs/system.md:305-318`). No hay prueba de pinning, firma de comandos o cifrado de payloads.
- **WiFi Sofia:** la guia afirma cifrado sin recuperacion de credenciales, sin algoritmo (`INSTALL.md:90-107`).
- **MQTT parcheado:** plano, build `WITH_TLS=no` (`scripts/81_mosquitto.sh:59-63`). Usuario/password opcional no cifra el canal.
- **HTTP parcheado:** BusyBox httpd en 80/81 sin configuracion TLS visible (`include/web/setup.sh:13-18`).
- **Actualizacion parcheada:** MD5 compara bytes antes y despues de escribir, no autentica origen (`include/bin/update_firmware.sh:23-49`).
- **REA:** SHA-256 de Evidence y HMAC del bridge Cutter pertenecen a la herramienta (`src/domain/evidence.ts:35-46`, `src/cutter/cutterBridgeMac.ts:1-16`). No revelan criptografia Wibox.

## 11. Firmware y compatibilidad

### 11.1 duhow

El README marca Sofia desactivable, apertura HTTP/MQTT, Home Assistant, toolchain y Dropbear como implementados. Audio queda sin marcar (`README.md:9-15`). Las versiones probadas son B007 y B013 (`README.md:17-19`).

La guia indica telnet posible en B007 y deshabilitado en versiones nuevas como B013, por lo que recomienda acceso serie (`INSTALL.md:35-43`). Hay una diferencia tipografica entre `G021` en esa frase y `G0021` en README; no se debe normalizar sin comprobar la version real.

Sofia y el parche son mutuamente excluyentes segun `USAGE.md:27-37`. `/mnt/mtd/factory` devuelve el arranque original.

La actualizacion escribe solo `mtd4` `/usr`, limita tamano y compara MD5 (`include/bin/update_firmware.sh:1-18`, `include/bin/update_firmware.sh:23-49`). La guia exige backups y recuperacion serie (`INSTALL.md:3-12`, `INSTALL.md:109-126`). No se ejecuto ninguno de esos pasos.

### 11.2 Blog cmos486

El articulo declara SoC Goke GK7102S, cramfs, bus VDS, UART y acceso telnet segun version. Añade B010 con telnet y B013 solo serie, y recomienda tratar versiones nuevas como solo serie hasta prueba contraria. Tambien afirma que una direccion VDS incorrecta impide recibir llamadas sin error visible. Son afirmaciones de tercero no contrastadas aqui con codigo o laboratorio.

Fuente: [cmos486](https://www.cmos486.es/blog/wibox-media-fermax-firmware-home-assistant), secciones "Informacion WiBox", "Lo que tocaba averiguar" y "Paso 1: lee antes de flashear".

El custom usa `/mnt/mtd/sip_media.conf`, con `mqtt_host`, `mqtt_user` y `mqtt_pass`, y el articulo dice que video/RTSP empiezan desactivados. No es el mismo fichero `/mnt/mtd/mqtt.conf` del parche duhow, por lo que no deben fusionarse configuraciones. Fuente: mismo articulo, "Paso 5: configuralo".

## 12. Errores, reintentos y timeouts

### 12.1 Cloud de fabrica

**DESCONOCIDO:** no hay envelope de error, codigos, backoff, reintentos, rate limit, idempotencia, reconexion o resincronizacion del cloud original. Los mensajes del log NTS no son un contrato de error.

### 12.2 Custom duhow, confirmado en codigo

| Mecanismo | Politica real | Limite |
|---|---|---|
| CGI | 404 para comando desconocido, 500 si falla escritura UART | Sin JSON ni codigo de negocio (`include/web/run.cgi:5-21`) |
| Apertura MQTT | Publica `OFF` al segundo | Es reset del comando, no ack fisico (`include/bin/listener_mqtt.sh:45-50`) |
| Keepalive | `mosquitto_sub -k 300` y will offline retenido | No implica entrega exactamente una vez (`include/bin/listener_mqtt.sh:37-43`) |
| Heartbeat MQTT | Timeout de lectura 45 segundos y reinicio tras 60 segundos en ciertos estados | Reinicio del dispositivo custom, no backoff (`include/bin/heartbeat_mqtt.sh:7-24`) |
| Healthcheck red | 10 intentos, espera 20 segundos, ping timeout 3 segundos, reinicio al agotar | No copiar como politica cloud (`include/bin/heartbeat.sh:3-10`, `include/bin/heartbeat.sh:33-61`) |
| Llamada | `HANG_UP_0` se documenta como timeout de 30 segundos | Dato de firmware original observado por tercero (`docs/codes.md:18-23`) |

### 12.3 REA, no reutilizable como valores Wibox

REA tiene errores tipados con `retryable` y `remediation` (`src/contracts/errorSchemas.ts:7-38`). MCP usa JSON-RPC con `error.code`, `error.message` e `id: null` (`src/server/http/serveStreamableHttp.ts:111-120`). JADX usa 120000 ms por defecto (`src/domain/analysisResourceLimits.ts:109-115`) y CDP 20000 ms por comando/handshake (`src/domain/browserDeadline.ts:8-18`). Ninguno es un default Wibox.

## 13. Configuracion, secretos y riesgos

| Riesgo | Evidencia | Recomendacion acotada |
|---|---|---|
| Sustituir APK por referencias custom | duhow y blog describen firmware distinto | Mantener perfiles `stock-cloud`, `duhow-local` y `cmos486-custom` separados |
| HTTP local sin auth visible y acciones por GET | `include/web/setup.sh:13-18`, `include/web/index.html:50-63`, `include/web/run.cgi:14-18` | No exponer ni reutilizar ese diseño. Exigir autenticacion, autorizacion y proteccion CSRF en cualquier clon local |
| MQTT sin TLS | `USAGE.md:14-25`, `scripts/81_mosquitto.sh:59-63` | No asumir confidencialidad. Para Kotlin, TLS y validacion de broker segun evidencia y entorno |
| Secretos por argv/ficheros | `include/bin/mqtt_functions.sh:14-26` | No copiar export con `xargs` ni argumentos con password. Almacen atomico y logs redactados |
| AP con password derivada de UDID | `include/bin/ap_start.sh:13-20` | No tratar identificador observable como secreto |
| Firmware sin verificacion de autenticidad | Solo MD5 en `include/bin/update_firmware.sh:23-49` | Firma y origen verificados para cualquier update propio |
| Cloud documentado por IP vieja | `docs/system.md:168-179` | No fijar IP. Resolver endpoints desde APK/config/captura de la version objetivo |
| Ausencia de ack fisico | Escritura UART y OFF temporizado | No informar "puerta abierta" sin evento que lo confirme |
| Debug y servicios heredados | `system_sofia`, interDebug y telnet documentados | No habilitar servicios por analogia. Revisar superficie en APK/firmware real |
| Capturas con credenciales | Campos TD/VC/DVC y configs | Fixtures saneados y sin tokens reales |

REA tambien tiene un listener HTTP plano `/mcp` con default loopback (`src/server/http/serveStreamableHttp.ts:44-45`, `src/config/mcpHttpEndpoint.ts:13-16`). Su riesgo de despliegue no es parte de Wibox y no se audito aqui.

## 14. Confirmado, hipotesis y desconocido

### 14.1 Confirmado directamente en codigo

- UART, comandos y eventos del parche duhow en el commit fijado.
- CGI local y sus secuencias temporizadas.
- Topics, payloads, retained, will y keepalive del MQTT duhow.
- Config wifi/AP y derivacion de identidad MQTT del parche.
- Arranque, exclusion Sofia/parche y verificacion MD5 de update.
- Funciones REA de analisis estatico y capturas offline.

### 14.2 Documentado por terceros, pendiente de contraste primario

- Agente NTS, IP/ports, autenticacion, registro y P2P.
- Puertos Sofia, autodiscovery y paquetes `system_sofia`.
- Campos de `/mnt/mtd/Config/secret` y significado atribuido.
- Versiones y vias de acceso a firmware.
- VDS, hardware y comportamiento descrito por cmos486.
- RTSP, WebRTC, SIP y funciones del fork cmos486.

### 14.3 Hipotesis explicitas

- **HIPOTESIS:** REA pudo aportarse como herramienta de analisis del APK, no como referencia de protocolo.
- **HIPOTESIS:** el APK original podria reutilizar NTS/P2P o algun canal relacionado. Debe probarse con clases, librerias y captura, no deducirse del nombre Sofia.

### 14.4 Desconocido

- API cloud, hosts actuales, rutas y versiones.
- Request/response de login, pairing y lista de dispositivos.
- Refresh, expiracion, logout y almacenamiento en el APK.
- Wire format NTS y relacion exacta APK-dispositivo-cloud.
- Acciones cloud, ack, estados, orden y garantias de entrega.
- SIP, WebRTC, WebSocket o media de fabrica.
- TLS, pinning, criptografia, errores, rate limit e idempotencia.
- Compatibilidad con la version concreta del APK objetivo.

## 15. Contrato recomendado para `core:cloud` mantenible

Todo en esta seccion es **PROPUESTA**. Los datos locales aportan vocabulario de dominio, no el contrato cloud.

### 15.1 Fronteras

- `core:cloud`: Kotlin puro, modelos, casos de uso, sesion, errores y puertos. Sin Android ni cliente HTTP concreto en las interfaces.
- `cloud:fermax`: DTO y endpoints de la version confirmada del APK. Ningun DTO se filtra al dominio.
- `local:duhow`, solo si se decide soportarlo: adaptador independiente para UART/HTTP/MQTT del parche. No se presenta como cloud.
- `media`: SIP/WebRTC/RTSP solo cuando una fuente primaria defina el mecanismo objetivo.
- `infra:auth`: almacen atomico y redaccion. Android Keystore se evalua por necesidad, no por costumbre.

### 15.2 Operaciones candidatas

| Operacion | Contrato minimo | Condicion para activarla |
|---|---|---|
| `authenticate` | Mecanismo y respuesta tipada | Request/response y errores observados |
| `refreshSession` | Single-flight y margen temporal | Refresh, expiracion y revocacion confirmados |
| `logout` | Borrado local y revocacion remota si existe | Secuencia confirmada |
| `pairDevice` | Identidad, Verify Code o datos reales | Flujo original y permisos confirmados |
| `listDevices` | Dispositivos y paginacion | Modelo cloud observado |
| `discoverLocalDevices` | Resultado y cobertura LAN | Datagramas y autenticacion confirmados |
| `performAction` | Accion, recurso, ack y correlacion | Comando y respuesta cloud observados |
| `observeEvents` | `Flow<CloudEvent>` con huecos explícitos | Canal, orden, replay y deduplicacion observados |
| `close` | Libera recursos sin revocar implicitamente | Siempre |

`openDoor`, `startCall`, `stopCall`, `setForward` y `triggerF1` son nombres de dominio plausibles gracias al parche. No deben publicarse como operaciones cloud soportadas hasta tener mensajes y respuestas del fabricante.

### 15.3 Modelos y puertos

- `AuthSession`: secretos opacos, refresh opcional y `expiresAt` solo si existe. `toString` redactado.
- `TokenStore`: `load`, `save`, `clear`, atomicos y separados por cuenta/entorno.
- `Device`: id estable, nombre, capacidades y estado desconocido explicito.
- `DeviceCapability`: apertura, llamada, desvio, F1 u otros solo si el dispositivo los declara o se confirman.
- `CloudAction`: tipo, recurso, parametros e idempotencia solo si el servidor la admite.
- `CloudEvent`: tipo, dispositivo, secuencia/tiempo si existen y payload desconocido preservado.
- `CloudTransport` y `RealtimeTransport`: request/response y eventos aislados.
- `Clock`: caducidad, backoff y pruebas deterministas.

### 15.4 Errores y robustez

Jerarquia sellada `CloudError` para autenticacion, autorizacion, recurso ausente, conflicto, rate limit, red, timeout, protocolo, capacidad no soportada y desconocido. Conservar codigos del proveedor como metadatos saneados.

1. Una renovacion en vuelo por sesion.
2. No inventar refresh si el APK no lo demuestra.
3. Reintentar solo lecturas idempotentes o acciones con idempotencia confirmada.
4. No repetir aperturas tras respuesta ambigua. El CGI/MQTT del parche no ofrece ack fisico y no sirve de modelo de confirmacion.
5. Respetar `Retry-After` si existe. Backoff con jitter y limite inyectable.
6. Separar timeouts de conexion, lectura, operacion y realtime.
7. Marcar perdida de eventos al reconectar. MQTT retained del parche no implica la semantica cloud.
8. No registrar tokens, passwords, Verify Codes, UDID completos ni payloads sensibles.

### 15.5 Pruebas minimas

- Fixtures saneados por version y campos desconocidos.
- Login, refresh, expiracion, revocacion y concurrencia.
- Paginacion, duplicados y capacidades de dispositivos.
- 401, 403, 409, 429, 5xx, red, timeout y JSON invalido.
- No reintento de accion fisica ambigua ni falso ack.
- Reconexion, duplicados, huecos y cierre de `Flow`.
- Redaccion de logs y ausencia de secretos en fixtures.
- Contratos DTO/dominio golden por version del APK.
- Si se soporta duhow, pruebas separadas para frames UART y topics sin mezclarlas con `cloud:fermax`.

## 16. Plan de contraste con el APK

1. Registrar SHA-256, `applicationId`, `versionName`, `versionCode`, firmas y procedencia. REA expone el modelo generico en `src/android/JadxProtocol.ts:73-80` y comandos en `docs/android-analysis.md:219-253`.
2. Inventariar manifiesto, componentes, permisos, servicios, receivers y deep links con `inspect_android_package`.
3. Buscar clases y recursos `Sofia`, `NTS`, `DevId`, `AuthPwd`, `Verify`, `34567`, `5000`, cloud, token y media. Un nombre coincidente sera pista, no prueba de equivalencia.
4. Seguir construccion y consumo de mensajes con `inspect_android_method` y `trace_android_references`. REA declara que no resuelve reflection, JNI o dispatch runtime (`src/contracts/android/androidToolContracts.ts:166-170`).
5. Revisar librerias nativas/JNI si NTS, TLS o media no esta en DEX. No convertir ausencia DEX en ausencia total.
6. Extraer endpoints y modelos solo con evidencia de productor y consumidor. Registrar clase, metodo, sobrecarga y artefacto.
7. Buscar almacenamiento real: preferencias, BD, ficheros, Keystore, `Config/secret`, migraciones y borrado de sesion.
8. Contrastar NTS y autodiscovery con datagramas/capturas autorizadas. No usar la IP documentada como destino de pruebas.
9. Analizar capturas offline. HAR/SAZ no repite peticiones (`docs/web-network-captures.md:1-6`) y pcap no adquiere trafico (`docs/pcap-analysis.md:1-6`).
10. Separar perfiles si APK, firmware duhow y fork cmos486 difieren. No promediar tokens, rutas, topics o media.
11. Solo entonces fijar DTO, secuencias, errores, reintentos y adaptadores `core:cloud`.

## 17. Funciones REA utiles para ese trabajo

| Operacion | Aporta | Limite |
|---|---|---|
| `inspect_android_package` | Paquete, SDK, permisos, manifiesto y componentes | No ejecuta ni autentica APK (`src/contracts/android/androidToolContracts.ts:52-69`) |
| `search_android_classes` | Busca clases locales | No prueba runtime (`src/contracts/android/androidToolContracts.ts:98-110`) |
| `inspect_android_class` | Miembros y mapping R8 | Nombres observados primarios (`src/contracts/android/androidToolContracts.ts:113-140`) |
| `inspect_android_method` | Metodo con fallback smali | Puede quedar incompleto (`src/contracts/android/androidToolContracts.ts:142-164`) |
| `trace_android_references` | Usos estaticos | Sin reflection/JNI/runtime (`src/contracts/android/androidToolContracts.ts:166-170`) |
| `inspect_android_jni_bindings` | Relaciones DEX/JNI | No carga librerias (`src/contracts/android/androidToolContracts.ts:23-50`) |
| `inspect_web_network_capture` | HAR, mitmproxy, SAZ | Offline (`docs/web-network-captures.md:1-6`) |
| `inspect_pcap_capture` | Endpoints, DNS, SNI, protocolos y URI | Cobertura limitada por captura (`docs/pcap-analysis.md:40-65`) |

## 18. Cierre

**HECHO:** REA no contiene el protocolo Wibox. duhow aporta codigo local parcheado y notas cloud de tercero. cmos486 aporta afirmaciones sobre otro custom. Ninguna combinacion de las tres permite afirmar que conocemos el cloud del APK.

**Resultado aprovechable:** vocabulario de acciones y eventos, candidatos de descubrimiento y NTS para contrastar, riesgos concretos que no copiar y una herramienta para el analisis primario del APK.

El inventario estructurado esta en `analysis/rea/inventory.json`. Distingue `source` REA, `additional_sources`, `duhow_wibox.code_confirmed`, `duhow_wibox.third_party_stock_notes` y `cmos486_third_party_claims`. El verificador local comprueba estructura, evidencias, presencia o justificacion de autenticacion/dispositivos/tiempo real y un control negativo sin modificar entregables.

## 19. Registro de las cuatro pasadas

1. **Entrega completa:** se fijaron REA y duhow por commit, se leyeron sus licencias y el blog, y se inventariaron arquitectura, endpoints, modelos, transportes, funciones, autenticacion, dispositivos, realtime, criptografia, errores, configuracion y riesgos.
2. **Relectura experta:** se revisaron las secuencias NTS, pairing frente a provisioning, VDS frente a identidad cloud, acciones UART, cobertura del listener y limites de SIP/WebRTC/RTSP. Se añadio la diferencia entre codigos definidos y codigos manejados.
3. **Busqueda de errores y omisiones:** se corrigio la licencia duhow para no inferir una variante no declarada, se añadieron riesgos de GET sin autenticacion y se reforzaron componentes, evidencias negativas y validacion de archivos incompletos.
4. **Pulido:** se comprobo coherencia entre informe e inventario, referencias archivo:linea, fragmentos citados, separacion de fuentes y ausencia de secretos. El comando final y su salida se registran en `analysis/rea/verification.txt`.

## 20. Ejecucion verificada de REA sobre el APK

### 20.1 Alcance y toolchain

Despues del analisis del repositorio se ejecuto REA 6.8.0 contra el APK Wibox v2.2 ya identificado por SHA-256. Se uso el paquete oficial `rea-agents` publicado en npm y el provider `jadx-headless-mcp` 0.7.1. El JAR tiene SHA-256 `6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75` y JDK 17 lo enumera sin errores.

El build desde el checkout no fue la via final: Node 26.12.1 rechazo el modo TypeScript `transform` usado por el script de metadata. No se modifico REA para ocultar ese fallo. Se instalo el paquete oficial precompilado en `/home/nacho/temp/opencode-work/rea-runtime` y se invoco `scripts/rea.mjs`. Todos los analisis fueron estaticos, sin dispositivos, credenciales ni conexiones al cloud Wibox.

### 20.2 Evidencia generada

Los resultados completos estan en `analysis/rea/wibox-*.json`. El artefacto `analysis/rea/wibox-inspect-android-package.json` confirma el hash del APK, paquete, version, SDK, 6.674 clases y 1.521 recursos. Las busquedas tuvieron cobertura completa del indice de clases de REA.

| Area | Metodo REA | Resultado contrastado |
|---|---|---|
| Paquete | `inspect-android-package` | Identidad y componentes del APK analizado |
| OAuth | `inspect-android-class`, `inspect-android-method` | `OAuthApi.getToken` y `refreshToken` usan `/qvoauthv2/token`; los managers construyen los grants `password` y `refresh_token` |
| Shadow | `inspect-android-class`, `inspect-android-method` | Cuatro operaciones API y callers de `QvShadowHttpManager` para status, multi, subscribe y unsubscribe |
| Registro opaco | `trace-android-references`, `inspect-android-method` | `UserApi.upChannelForFreeLogin` es usado por `QvAuthManager` y `HttpUserAuthManager`; no se infiere un body de exito de alto nivel |
| Discovery | `inspect-android-method` | `QvLocationManager` llega a `QvP2PManager.queryServiceAddress(typeMask, groupId, callback)` |
| MQTT y P2P | `search-android-classes`, `inspect-android-class` | Clases y constantes localizadas; no se convierte su presencia en actividad dinamica ni en un wire format reutilizable |

Las interfaces Retrofit devuelven firma pero no cuerpo, por lo que REA no muestra sus anotaciones en `inspect-android-method`. Se trazaron referencias y se inspeccionaron los callers con cuerpo en vez de interpretar esa ausencia como ausencia de contrato.

### 20.3 Efecto sobre la implementacion

La ejecucion confirma el contrato OAuth y las operaciones Shadow ya implementadas con sus fixtures. Tambien confirma que el registro opaco usa `UserApi`, pero no aporta por si solo una respuesta tipada segura para exponerlo como flujo de alto nivel.

El descubrimiento regional no queda reducido a una URL HTTP fija: depende de P2P, tipos de servicio, region y callbacks. Por eso `discoverServices` continua como `Unsupported`. MQTT, realtime, P2P y acciones fisicas mantienen sus limites documentados. La ejecucion de REA no demuestra comportamiento runtime, validez de credenciales, disponibilidad del servicio ni compatibilidad con una cuenta real.
