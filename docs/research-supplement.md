# Suplemento de investigación pública Wi-Box, firmware custom y DUOX PLUS

Fecha de consulta: 2026-10-31. Hoja: `leaf-1.2.11`.

## 1. Resultado ejecutivo

- **duhow/wibox es firmware custom, no stock.** El repositorio construye y escribe una imagen `/usr` modificada, sustituye el arranque y añade Dropbear, HTTP y MQTT. Sus notas sobre Sofia y NTS no se convierten en contrato QVCloud del APK (`README.md:3-19`, `INSTALL.md:77-117`, `scripts/50_move_run.sh:1-4`).
- **cmos486 y segator son otras dos líneas de firmware custom.** Comparten una base, pero no tienen idénticas capacidades ni el mismo commit. SIP, RTSP, MQTT, OTA y Prometheus están presentes en código. El retorno de voz de cmos486 usa RTSP con backchannel ONVIF y go2rtc para llegar a WebRTC, no un servidor WebRTC nativo dentro del daemon.
- **Los blogs polacos tratan Fermax WayFi, no Wi-Box F03266.** Describen otro hardware, otro firmware y otra app. Solo hay correlaciones puntuales con duhow, como dos significados de frames UART. No se puede trasladar su Hi3518EV200, su RTSP/ONVIF stock o sus particiones a Wi-Box.
- **CVE-2026-86585 está confirmado por Fermax, CVE y NVD.** Afecta a firmware DUOX PLUS de monitores Wi-Fi en versiones anteriores a `01.48.001`, con CWE-347. No lista Wi-Box F03266 y no se aplica automáticamente a ese producto, a QVCloud ni a los forks.
- **La charla Recon y la charla TROOPERS son investigación DUOX PLUS y RFID.** No prueban nada sobre Wi-Box, VDS, QVCloud o la app Wibox.
- **El proyecto ESP32 es un puente eléctrico.** Usa relés, GPIO y secuencias temporizadas para reproducir pulsaciones. No demuestra integración con Wi-Box, QVCloud o DUOX.
- **La ficha oficial F03266 identifica un producto VDS descatalogado.** Publica funciones de la app y actualización remota, pero no publica QVCloud, OAuth, MQTT, SIP, RTSP, WebRTC ni un API local.
- **No cambia ninguna compatibilidad.** Este suplemento no modifica `analysis/architecture/provider-matrix.json`, no implementa proveedores y no presenta capacidades custom como disponibles en la app stock.

## 2. Metodología y taxonomía

### 2.1 Alcance

Solo se consultaron webs públicas y código público. Los clones quedaron fuera del workspace, en `/home/nacho/temp/opencode-work/wibox-research-supplement`. No se ejecutaron scripts, builds, tests ni binarios de esos proyectos. No se copiaron binarios ni código sustancial al workspace.

No se hizo ninguna de estas acciones:

- Contactar con QVCloud ni con dispositivos.
- Usar hardware físico, abrir equipos, flashear, reiniciar o actualizar firmware.
- Desactivar Sofia, una nube o cualquier servicio.
- Probar aperturas, llamadas, credenciales, exploits o técnicas de las charlas.
- Ejecutar Home Assistant, go2rtc, SIP, MQTT, RTSP o WebRTC contra equipos reales.
- Modificar app, core, README, `.unlazy`, otros análisis o la matriz normativa.

Las 25 URLs y APIs descargadas con `curl` respondieron HTTP 200. El detalle temporal, hashes y limitaciones está en `analysis/research-supplement/verification.txt`.

### 2.2 Prioridad de fuentes

1. Fabricante, registro CVE de la CNA y NVD.
2. Código e historial Git fijados por commit, con archivo y línea.
3. Documentación oficial de conferencias y ficha de producto.
4. Blogs con salidas pegadas y foros comunitarios, siempre con su límite de independencia.

Etiquetas usadas:

- **OFICIAL**: afirmación publicada por fabricante, CNA o NVD dentro de su ámbito.
- **CÓDIGO**: comportamiento visible en el commit citado. No equivale a ejecución ni a prueba física.
- **DOCUMENTADO**: afirmación de README, guía o página de conferencia. No se eleva a funcionamiento probado.
- **NARRADO**: relato de blog o foro, incluso si contiene logs o código pegado. No hay reproducción independiente.
- **NO CONFIRMADO**: dato que no puede fijarse con la fuente disponible.

### 2.3 Línea base preservada

Antes de la investigación se calcularon estos hashes:

| Archivo | SHA-256 |
|---|---|
| `docs/rea-analysis.md` | `19bce697e5e1b5482cfd3249a953cb9e32d2761086b97d60f3b5f8bf1d8338c7` |
| `docs/provider-architecture.md` | `675b059d23499ec551115966026e2bb352dbf1b3183b775fdca64a23ba403a63` |
| `docs/dynamic-analysis.md` | `2b7efc28e3987785153e8472ae678e729bf3b0e9837a73092674cdf7925b0160` |
| `analysis/architecture/provider-matrix.json` | `912878dcef3f697cef5d7fc561dc1d1542a241673993343909fbdf4d6dd6a915` |
| `analysis/dynamic/inventory.json` | `f31824799489b4e89712be985c611efc80f6502a830fd513e74c540bb9523ea6` |

El workspace no expone metadatos `.git`, por lo que no se inventa un commit propio. La preservación se verifica por hash y por comparación estructural de estados en `verify_research.py`.

## 3. Fuentes, revisiones y credibilidad

Todas las entradas se consultaron el 2026-10-31. Las URLs completas, metadatos, evidencias y clasificación máquina están en `analysis/research-supplement/source-matrix.json`.

| ID | Fuente y credibilidad | Producto y revisión | Licencia | Clasificación |
|---|---|---|---|---|
| `duhow_repo` | Repo principal, código | Wi-Box, `main`, `d8502d780f1d4ac3f2ec1a8425a98588fd7f53a7` | GPL-3.0, `LICENSE:1-2` | Custom Wi-Box duhow |
| `duhow_install` | Guía del mismo commit | Stock B007/B013 documentado, parche `mtd4` | Misma fuente GPL-3.0 | Instalación custom, no stock |
| `linuch_part1` | Blog archivado, narración con salidas | WayFi, `V500.R001.A003.00.CG0021.B003` | No evaluada | WayFi histórico |
| `linuch_part2` | Blog archivado, narración con boot log | WayFi Hi3518EV200, 47 MB RAM, flash 16 MB | No evaluada | WayFi histórico |
| `linuch_part3` | Blog archivado, narración y script pegado | WayFi, modificación `mtd4` y CGI | No evaluada | WayFi custom histórico |
| `cmos486_blog` | Blog del autor del fork | Wi-Box GK7102S, descarga documentada v0.18.14 | No evaluada | Custom cmos486 |
| `cmos486_media_repo` | Repo principal, código e historial | `custom`, `090854bef1085679fb3f42e431e9ffa2d1ad958c`, `v0.18.15-4-g090854b` | GPL-3.0 | Custom cmos486 |
| `cmos486_card_repo` | Repo principal, código | `main`, `a0d24354f1daf1cff02ce0cf9381b72d8c90fc14`, `v1.0.1-1-ga0d2435` | Apache-2.0 | UI Home Assistant |
| `segator_media_repo` | Repo principal, código e historial | `main`, `a64530bb5a17d7b1ce512bf065ccba90ae2d0027`, `v0.18.1-1-ga64530b` | GPL-3.0 | Custom segator/SIP |
| `ha_forum_post7` | Foro, corroboración comunitaria | Wi-Box sin versión concreta | No evaluada | Relato stock y referencia cruzada a custom |
| `esp32_repo` | Repo principal, código | `main`, `cb21e44ce0820975dfeff47a53ec915e0976099c` | GPL-3.0 | Puente físico ESP32 |
| `recon_talk` | Página oficial Recon 2026 | DUOX PLUS y módulos RFID, charla 2026-06-20 | No evaluada | Investigación DUOX PLUS/RFID |
| `troopers_home` | Sitio oficial general | Solo contexto de congreso | No evaluada | Contexto, sin evidencia Wi-Box |
| `troopers_duox_talk` | Página oficial TROOPERS26 | DUOX PLUS, señal digital de dos hilos | No evaluada | Investigación DUOX PLUS |
| `circl_cve` | Agregador que reproduce CNA/NVD | CVE-2026-86585 | No evaluada | CVE DUOX PLUS |
| `fermax_advisories` | Aviso oficial del fabricante | VEO/VEO-XS, `< 01.48.001` | No evaluada | Confirmación primaria CVE |
| `nvd_page` | Página NVD | CVE-2026-86585 | No evaluada | Confirmación NVD |
| `nvd_api` | API oficial NVD | Publicación NVD y plataformas estructuradas | No evaluada | Confirmación NVD legible |
| `cve_awg_api` | Registro oficial CVE de la CNA Fermax | Publicación CNA, CWE y rango afectado | No evaluada | Registro CVE primario |
| `fermax_product` | Ficha oficial de producto | Wi-Box referencia 3266, F03266 | No evaluada | Stock/producto VDS oficial |

La tarjeta tiene un fichero Apache-2.0 real aunque la API de GitHub devolviera `NOASSERTION`. Se prioriza el fichero `LICENSE:1-2` sobre esa heurística. Las licencias GPL-3.0 se comprueban igualmente en el fichero de cada repo, no solo en la API.

## 4. duhow/wibox: qué demuestra y qué no

### 4.1 Hardware y versiones

**CÓDIGO/DOCUMENTADO:** el README enlaza la ficha Wi-Box y declara pruebas con `V500.R001.A103.00.G0021.B007` y `V500.R001.A103.00.G0021.B013` (`README.md:17-19`). No hay base para generalizar a otras versiones.

**DOCUMENTADO:** las notas describen alimentación de 18 V CC, flash de 16 MB, `mtd4` como `/usr` en CramFS y `mtd5` como `/mnt` en JFFS2 (`docs/system.md:1-30`). INSTALL sitúa la consola serie en `ttySGK2` a 115200 baudios, mientras los comandos del intercom usan `/dev/ttySGK1` (`INSTALL.md:6-12`, `include/web/run.cgi:5-12`). Son interfaces y funciones distintas.

La guía contiene una variante tipográfica `G021` junto a `G0021` (`INSTALL.md:39-40`). Se conserva la discrepancia, sin normalizarla como si fuera una versión nueva confirmada.

### 4.2 Prueba de que es custom

Hay cuatro evidencias independientes dentro del mismo commit:

1. El README dice que contiene scripts para parchear y usar el dispositivo localmente en lugar de la nube (`README.md:3-5`).
2. INSTALL construye una imagen custom y escribe solo `mtd4` (`INSTALL.md:77-85`, `INSTALL.md:109-117`).
3. El build renombra el `run.sh` original a `run-orig.sh` y copia el contenido nuevo (`scripts/50_move_run.sh:1-4`, `scripts/93_include.sh:11-16`).
4. El modo factory ejecuta el arranque original y la guía advierte que Sofia y el parche no funcionan como dos planos de control simultáneos (`include/run.sh:37`, `USAGE.md:27-37`).

Por tanto, HTTP, MQTT, Dropbear y los topics de este repositorio no son evidencia de stock QVCloud.

### 4.3 Sofia y nube

El README presenta Sofia como el programa original que puede desactivarse (`README.md:9`). El arranque custom tiene más matiz: salvo configuración contraria, puede invocar `Sofia_temp.sh` durante un tiempo acotado para inicializar hardware (`include/run.sh:108-126`, `include/bin/Sofia_temp.sh:3-4`, `include/bin/Sofia_temp.sh:36-45`). Esto no demuestra que la nube stock permanezca operativa. El script mata después el cliente NTS (`include/bin/Sofia_temp.sh:43-45`).

Las notas describen Sofia, puertos y un agente NTS con registro y P2P (`docs/system.md:73-101`, `docs/system.md:148-193`). Son documentación de tercero dentro del repo. No incluyen una especificación request/response del APK, tokens, rutas de login o formato de eventos. Este suplemento no contactó esos servicios y no reutiliza sus IP históricas.

### 4.4 SSH, HTTP y MQTT

- **SSH custom:** el build compila Dropbear 2020.81 y el arranque lo inicia, desactivando telnet cuando Dropbear queda disponible (`scripts/82_dropbear.sh:5-9`, `scripts/82_dropbear.sh:47-68`, `include/run.sh:27-35`). No prueba SSH en stock.
- **HTTP custom:** BusyBox httpd escucha en 80 u 81 y el CGI escribe acciones en UART (`include/web/setup.sh:13-18`, `include/web/run.cgi:3-21`). No hay autenticación visible en esos archivos. Una escritura UART o un pulso `OFF` no prueba efecto físico.
- **MQTT custom:** el listener suscribe topics locales y traduce apertura, llamada, desvío y F1 (`include/bin/listener_mqtt.sh:37-88`). La guía dice que los binarios no están incluidos y que la prueba fue MQTT plano en 1883 (`USAGE.md:10-25`). El build configura `WITH_TLS=no` (`scripts/81_mosquitto.sh:59-63`). No es el MQTT OAuth estático del APK ni un canal QVCloud.

## 5. Blogs polacos: valor real y límite WayFi/Wi-Box

La serie usa el nombre **Fermax WayFi** y un JSON con paquete `com.fermax.wayfi` en la primera parte. Eso la separa de `com.fermax.wibox` v2.2 analizado en el workspace. La primera parte fija firmware `V500.R001.A003.00.CG0021.B003`, distinto de los B007/B010/B013 de los forks Wi-Box.

| Parte | Qué aporta la fuente | Contraste independiente | Lo que no se puede afirmar |
|---|---|---|---|
| 1, 2019-03-29 | Escaneo con telnet, HTTP, RTSP, UPnP, ONVIF, QVCloud/UMEYE y una URI RTSP. Incluye salidas Nmap/ONVIF y JSON narrados | duhow también documenta Sofia y NTS, pero para Wi-Box dice ONVIF deshabilitado (`docs/system.md:75-81`). No hay PCAP original ni binario contrastado | Que Wi-Box stock tenga RTSP u ONVIF abierto, que use la misma URI o que los endpoints sigan vigentes |
| 2, 2019-04-04 | Apertura de WayFi, UART, U-Boot, Hi3518EV200, ARMv5, 47 MB RAM y flash de 16 MB | Los boot logs están pegados en el artículo. No hay imagen original analizada. Wi-Box custom usa drivers Goke y `ttySGK*` en duhow/cmos486 | Que Wi-Box F03266 tenga Hi3518EV200, 47 MB RAM, la misma disposición de pines o el mismo acceso |
| 3, 2020-10-26 | Sofia, UMEYE, CramFS, modificación `mtd4`, CGI y frames para abrir/iniciar preview | Los significados `TRANSFER_CMD_UNLOCK_DOOR` y `START_CALL` coinciden con `duhow/include/wibox_codes.txt`. El dispositivo serie del blog es `/dev/ttyAMA2`, no `/dev/ttySGK1` | Que todo el set de comandos, el CGI, el mapa MTD o el SoC sea idéntico en Wi-Box |

**Conclusión:** aportan historia de ingeniería inversa, candidatos de protocolo y dos correlaciones UART. La mayor parte sigue siendo narración con evidencia pegada, no verificación primaria actual. La existencia de un script pegado en la parte 3 prueba qué decía el autor, no que se haya ejecutado en Wi-Box.

## 6. cmos486/wibox-media: capacidades confirmadas en código

Commit: `090854bef1085679fb3f42e431e9ffa2d1ad958c`. `git describe` devuelve `v0.18.15-4-g090854b`. El README enlaza una descarga v0.18.14 y existe una tag posterior v0.18.15. Por eso se fija el commit, no se presenta HEAD como si fuera una release única.

| Capacidad | Evidencia concreta | Clasificación y límite |
|---|---|---|
| RTSP con H.264 y PCMA | SDP `trackID=0` H.264, `trackID=1` PCMA y listener por defecto 8554 (`src/sip_media/rtsp_stream.c:29`, `src/sip_media/rtsp_stream.c:445-475`, `docs/architecture.md:85-100`) | CÓDIGO custom. No prueba RTSP en stock |
| Audio de retorno ONVIF | Pista `sendonly` PCMA `trackID=2` y procesamiento RTP de entrada (`src/sip_media/rtsp_stream.c:453-460`, `src/sip_media/rtsp_stream.c:480-535`, `src/sip_media/rtsp_stream.c:829-880`) | CÓDIGO custom. Es backchannel RTSP, no WebRTC nativo |
| WebRTC extremo a extremo | La arquitectura publicada es WiBox RTSP -> go2rtc -> navegador WebRTC (`docs/homeassistant-two-way-audio.md:7-20`) | DOCUMENTADO + CÓDIGO de la tarjeta. Requiere go2rtc y componentes HA |
| Eventos de llamada/UART | Discovery con eventos `ringing`, `established`, `hang_up` y publicación JSON con `call_id`, secuencia y estado (`src/sip_media/mqtt.c:617-640`, `src/sip_media/mqtt.c:1646-1664`) | CÓDIGO custom. No es el modelo FCM/QVCloud |
| Snapshots | Publicación de `snapshot/image` en base64, entidades MQTT y escritura atómica (`src/sip_media/mqtt.c:476-527`, `src/sip_media/mqtt.c:868-909`, `src/sip_media/snapshot_file.c:10-56`) | CÓDIGO custom. No se verificó una imagen real |
| OTA | Updater HTTPS, descarga GitHub, MD5, escritura MTD y botones MQTT (`docs/updates.md:6-22`, `src/firmware_update.c:1234-1311`, `src/sip_media/mqtt.c:1265-1354`) | CÓDIGO custom. No se instaló nada ni se afirma firma criptográfica |
| Prometheus | `/metrics` y `/healthz`, versión, uptime, MQTT, llamadas, UART y RSSI (`src/sip_media/prometheus.c:126-242`, `src/sip_media/prometheus.c:261-286`, `docs/architecture.md:202-212`) | CÓDIGO custom. Puerto documentado 9617 |
| SIP | Daemon y flujo SIP heredados (`README.md:40-48`, `docs/architecture.md:39-83`) | CÓDIGO/DOCUMENTADO custom. No prueba SIP stock |

### 6.1 Precisión WebRTC

El blog dice "audio bidireccional por WebRTC". La descripción de sistema es razonable si incluye go2rtc y la tarjeta, pero sería incorrecto atribuir al daemon un `RTCPeerConnection` o señalización WebRTC nativa. El daemon anuncia y consume una pista ONVIF sobre RTSP. go2rtc transforma la sesión del navegador y el retorno de audio. La tarjeta contiene `RTCPeerConnection`, tres transceivers y señalización a través del proxy de Home Assistant (`wibox-intercom-video-card.js:525-539`, `wibox-intercom-video-card.js:574-628`).

### 6.2 OTA y configuración pendiente de no simplificar

La configuración empaquetada en el commit sigue usando `firmware_update_repo=segator/wibox-media` (`include/etc/sip_media.conf.default:74-75`, `src/sip_media/config.c:84-85`). `FORK.md:93-102` documenta configurar el dispositivo con el repo del fork. Por tanto, el código OTA está confirmado, pero no se afirma que toda instalación cmos486 actualice desde cmos486 sin revisar su configuración.

MD5 comprueba integridad frente a un valor esperado. No es una firma de origen. El código también tiene lógica TLS, pero este suplemento no certifica la seguridad completa del updater ni relaciona ese diseño con CVE-2026-86585.

### 6.3 Licencia y dependencias

El fichero superior es GPL-3.0. En cmos486 y segator, `git ls-files '*LICENSE*' '*COPYING*' '*NOTICE*'` solo devuelve `LICENSE`, sin avisos adicionales separados en el árbol. `FORK.md:124-136` dice que una build completa necesita una imagen base privada con partes propietarias del SDK Goke y que no todo está en Git. La licencia superior no prueba por sí sola los derechos ni la reproducibilidad de todos los blobs y dependencias. No se hizo una auditoría legal ni de redistribución.

## 7. cmos486/wibox-intercom-video-card: UI, no daemon

**CÓDIGO:** es una tarjeta Lovelace en JavaScript. Usa `RTCPeerConnection`, micrófono con `getUserMedia`, pistas `sendonly/recvonly`, WebSocket `/api/webrtc/ws` firmado por Home Assistant y acciones configurables (`wibox-intercom-video-card.js:47`, `wibox-intercom-video-card.js:507-539`, `wibox-intercom-video-card.js:617-628`, `wibox-intercom-video-card.js:227-240`).

**DOCUMENTADO:** exige cmos486/wibox-media para el retorno, AlexxIT/WebRTC, go2rtc, HTTPS y Home Assistant 2024.4 o posterior (`README.md:89-97`, `hacs.json:1-6`). Su propia guía dice que stock no acepta ese retorno (`README.md:15-16`, `README.md:247-251`).

No implementa el daemon RTSP, MQTT, SIP, OTA ni métricas. No eleva `media.*` ni acciones en la app Kotlin. La licencia del fichero es Apache-2.0 (`LICENSE:1-2`, `README.md:299-301`).

## 8. segator/wibox-media y diff con cmos486

### 8.1 Qué añade segator

El README lo define como firmware custom Wi-Box GK7102S que sustituye el flujo cloud/app por un daemon SIP local (`README.md:1-5`). En código y documentación aparecen SIP PCMA, H.264 D1 opcional, snapshots, DTMF/MQTT, Home Assistant, Prometheus y OTA (`README.md:9-19`, `docs/architecture.md:39-54`).

GitHub marca `segator/wibox-media` como `fork=false`. Por tanto, "fork de duhow" describe procedencia técnica reconocida en sus créditos, no una relación GitHub que debamos inventar. En cambio, la API confirma `cmos486/wibox-media` como fork de segator, con `parent=segator/wibox-media`.

### 8.2 Divergencia medida

Se obtuvo segator/main dentro del clone cmos486 y se calcularon:

| Medida | Resultado |
|---|---|
| Ancestro común | `a55b8de9b19b954238a3c160e283a2c1867e8ce6` |
| Commits exclusivos cmos486 / segator | 29 / 3 |
| Diff cmos486 desde el ancestro | 39 archivos, 2843 inserciones, 227 borrados |
| Diff segator desde el ancestro | 16 archivos, 110 inserciones, 10 borrados |
| Commit inicial de la pista ONVIF | `018ceb6 feat(rtsp): ONVIF audio backchannel track for go2rtc two-way audio` |
| Commit de interoperabilidad | `3539923 Fix RTSP interop and two-way audio for go2rtc/WebRTC clients` |

Los tres commits posteriores de segator son `eed32e1`, `8e2d085` y `a64530b`, sobre fallo cerrado de hostname, release y documentación.

**Diferencia estática relevante:** en cmos486, si `SSL_set1_host` falla, el updater registra el error y continúa (`src/firmware_update.c:471-475`). En segator HEAD, el mismo fallo libera recursos y aborta (`src/firmware_update.c:471-478`, commit `eed32e1`). Esto demuestra una diferencia de ramas. No demuestra explotación, no se califica como CVE y no se extrapola a stock F03266.

## 9. ESP32: puente físico, no integración cloud

Commit: `cb21e44ce0820975dfeff47a53ec915e0976099c`, GPL-3.0.

**CÓDIGO:** el sketch define dos relés en GPIO 4 y 5, un LED, un servidor HTTP en puerto 80 y una secuencia de escrituras y esperas (`porteroCasa/porteroCasa.ino:13-33`, `porteroCasa/porteroCasa.ino:79-95`). El README describe la idea de reproducir las pulsaciones de cámara y llave (`README.md:5-10`, `README.md:16-36`). El bot de Telegram llama al endpoint del ESP32 (`telegramBot/espH0m3_bot.py:12-17`, `telegramBot/espH0m3_bot.py:26-36`).

No aparecen `Wi-Box`, `QVCloud`, `DUOX`, `MQTT`, `SIP`, `RTSP` ni `WebRTC` en los archivos de texto revisados. El modelo Fermax y el firmware no se especifican. El esquema eléctrico no se ha validado. No se ejecutó la acción y no se declara compatible con Wi-Box.

## 10. Recon y TROOPERS: DUOX PLUS/RFID, fuera de QVCloud

**Recon 2026:** la charla "From Bus Wires to Badges: Breaking Into FERMAX Through RFID", del 2026-06-20, trata el sistema propietario DUOX PLUS y paneles con módulos EM4100, MIFARE Classic y MIFARE Desfire. Resume investigación previa de dos hilos y la amplía a RFID. Fuente oficial, resumen de la charla.

**TROOPERS26:** "Reverse engineering the DUOX PLUS protocol" trata señal digital, identificación y seguridad de un sistema de dos hilos no polarizado. La página oficial incluye slides y vídeo, pero este suplemento solo usó el resumen para clasificar el alcance. La portada genérica `troopers.de` no aporta evidencia técnica adicional.

Ninguna fuente menciona Wi-Box F03266, VDS, QVCloud, la app Wibox ni una API reusable. No se puede trasladar una debilidad de bus o RFID a stock QVCloud. Tampoco se reproducen instrucciones de ataque.

## 11. CVE-2026-86585: confirmación y alcance exacto

### 11.1 Confirmación

El CVE está **confirmado** por tres fuentes convergentes:

1. Fermax lo lista en "VEO / VEO-XS monitors", con puntuación 7.7 y versiones `< 01.48.001` (`fermax.com/security-advisories`).
2. El registro CVE oficial tiene asignador `FERMAX`, estado `PUBLISHED`, CWE-347 y producto "DUOX PLUS monitor firmware (VEO Wi-Fi range)".
3. NVD API devuelve una entrada publicada, con la misma descripción y rango. Su estado de análisis es `Deferred`, no una negación del CVE.

CIRCL reproduce el registro y añade correlaciones. Se usa como agregador, no como sustituto del fabricante.

### 11.2 Fechas, producto, versión y mitigación

| Campo | Valor verificado |
|---|---|
| Reserva CNA | 2026-09-08T07:00:51.689Z |
| Publicación CNA | 2026-09-16T10:13:22.416Z |
| Publicación NVD | 2026-09-16T11:16:44.070Z |
| Actualización CNA | 2026-09-16T17:47:00.863Z |
| Última modificación NVD | 2026-09-18T19:44:10.957Z |
| Debilidad | CWE-347, verificación incorrecta de firma criptográfica |
| Descripción | Paquetes de actualización sin verificación de firma adecuada, si el atacante controla la entrega |
| Producto estructurado | DUOX PLUS monitor firmware, gama VEO Wi-Fi |
| Versiones afectadas | Anteriores a `01.48.001` |
| Severidad CNA | CVSS 4.0, 7.7 High, vector adyacente |
| Mitigación derivada del aviso | Usar `01.48.001` o posterior. El rango corregido se deriva del aviso de Fermax, no de una prueba propia |
| Explotación observada por este trabajo | No se investigó ni se probó |

El texto narrativo nombra VEO y VEO-XS. La lista estructurada CNA/NVD incluye además `VEO-XL-WIFI` y `VEO-XL-WIFI-DDA`, junto a VEO y VEO-XS/DDA. Se conserva esa diferencia entre descripción y plataformas estructuradas en vez de ocultarla.

### 11.3 Lo que no está afectado por inferencia

Wi-Box F03266 no aparece en el aviso, el registro CNA ni NVD. Es un producto VDS distinto de los monitores DUOX PLUS. **No se aplica el CVE a Wi-Box, QVCloud, duhow, cmos486, segator o la app Kotlin.** La presencia de actualización remota en la ficha oficial no demuestra el mismo mecanismo ni la misma debilidad.

## 12. Producto oficial F03266: lo publicado y lo ausente

La ficha oficial identifica "DESVIO DE LLAMADA WIFI VDS WI-BOX", referencia **3266**, EAN 8424299032665. En la consulta figura **"Producto descatalogado. Disponible hasta agotar existencias"**.

**OFICIAL:**

- Tecnología VDS, 3+coax o 5 hilos, no DUOX PLUS.
- Se instala en paralelo al monitor o teléfono VDS y actúa como otro terminal.
- Wi-Fi 2,4 GHz, 18 Vcc, dos pulsadores y bornes `+, L, -, V, M, Ct, F1`.
- App Android/iOS y emparejamiento mediante QR.
- Recepción de llamada con audio/vídeo, conserjería, F1 y apertura.
- 30 s para aceptar y 90 s de conversación.
- Registro, capturas, ajustes y actualización remota de firmware.

La página no publica nombre o versión de firmware, SoC, QVCloud, hosts, OAuth, APIs, MQTT, SIP, RTSP, ONVIF, WebRTC, topics ni formato de comandos. Esas ausencias no prueban que el producto carezca internamente de protocolos. Solo impiden atribuirlos a esta fuente oficial.

## 13. Matriz de separación stock, custom, DUOX y modelos

| Objeto | Evidencia y transporte | Capacidades demostradas dentro de su perfil | No se puede inferir para nuestra app stock |
|---|---|---|---|
| Wi-Box stock/QVCloud | APK y análisis previos, OAuth/Shadow parciales y ficha VDS | Solo los estados ya existentes en provider-matrix | Ninguna capacidad nueva a partir de forks |
| duhow custom | HTTP CGI, MQTT local, UART, Dropbear, arranque modificado | Acciones/eventos locales del parche | Que stock exponga esos topics, HTTP o SSH |
| segator custom | Daemon SIP, MQTT nativo, RTSP, OTA, Prometheus | SIP/media/HA de su firmware | Que stock tenga SIP, RTSP o MQTT local |
| cmos486 custom | Base segator + ONVIF backchannel, VDS, salud y fixes | Retorno por RTSP/go2rtc/WebRTC y demás cambios del commit | WebRTC nativo stock o paridad con segator HEAD |
| Tarjeta cmos486 | JavaScript Lovelace + HA + go2rtc | UI WebRTC y acciones HA configuradas | Daemon, proveedor Kotlin o soporte sin firmware custom |
| WayFi histórico | Hi3518EV200, app WayFi, Sofia/ONVIF/RTSP narrados | Investigación histórica de otro producto | Hardware, endpoints o servicios de Wi-Box F03266 |
| ESP32 genérico | Relés, GPIO, HTTP, Telegram | Puente físico para un portero no identificado | Compatibilidad Wi-Box/QVCloud/DUOX |
| DUOX PLUS Recon/TROOPERS | Bus digital de dos hilos y RFID | Investigación de ese sistema | QVCloud, VDS o Wi-Box |
| Monitores VEO/VEO-XS y plataformas CNA | DUOX PLUS, firmware `< 01.48.001` | Alcance oficial CVE-2026-86585 | Aplicación del CVE a F03266 |
| F03266 oficial | Producto VDS y app móvil, descatalogado | Funciones comerciales publicadas | Protocolos no publicados o soporte de forks |

## 14. Implicaciones futuras para proveedores

Todo lo siguiente es **oportunidad de diseño, no disponibilidad actual**.

| Vía | Oportunidad | Requisitos antes de afirmar soporte | Límite actual |
|---|---|---|---|
| MQTT duhow | Adaptador local para un perfil duhow versionado | Dispositivo con ese firmware, versión concreta, broker, topics, payload, auth, TLS si el entorno lo permite, descubrimiento y pruebas de ack | Firmware custom obligatorio. MQTT plano y sin ack físico en la evidencia. No mezclar con stock |
| MQTT cmos486/segator | Otro perfil local, con entidades y estados distintos | Elegir fork y commit, mapear `media/state`, UART, snapshots y comandos, negociar media aparte, probar reconexión y duplicados | No comparte automáticamente el contrato duhow ni el QVCloud estático del APK |
| Media custom | Evaluar RTSP/SIP y, solo para cmos486, go2rtc/WebRTC | Implementación FOSS, autenticación, permisos Android, ciclo de llamada, latencia, pruebas por modelo/firmware y hardware autorizado | Ninguna de estas rutas está implementada en la app ni demuestra P2P stock |
| Self-hosted | Aprender del patrón local HA para una API propia versionada | Cuentas, enrolamiento, autorización, inventario, eventos, acciones, ack, media, revocación, almacenamiento y suite de seguridad | HA/go2rtc no es una nube API lista para nuestra app. No hay adaptador self-hosted |
| Binder/AIDL | Exponer metadatos, capacidades, eventos y acciones de un provider real a terceros autorizados | Provider implementado, scopes por dispositivo, UID+firma, ack por niveles, gaps, cancelación y pruebas con dos firmas | Binder no crea compatibilidad. No transporta media ni secretos y sigue el diseño separado de `docs/external-api.md` |
| ESP32 | Posible perfil físico independiente, solo si se define y prueba otro hardware | Modelo eléctrico, aislamiento, estados, ack físico, seguridad del endpoint y autorización expresa | Sin evidencia Wi-Box y sin ninguna prueba realizada |
| DUOX/RFID | Investigación separada para entender otros productos | Evidencia específica de producto, protocolo y autorización | No aporta requisitos ni compatibilidad a stock Wi-Box |

La ficha descatalogada puede aumentar el interés de opciones locales mantenibles, pero no sustituye la investigación QVCloud ni la evidencia necesaria para cada capacidad.

## 15. Límites y trabajo no realizado

- No hay prueba runtime de duhow, cmos486, segator, la tarjeta, go2rtc, SIP, RTSP, WebRTC, MQTT, OTA o Prometheus. "CÓDIGO" significa presencia estática, no funcionamiento medido.
- No se validaron los bancos de pruebas narrados por los autores, sus fotografías, logs de coexistencia VDS, cifras de jitter ni snapshots reales.
- No se analizaron los PCAP originales de WayFi porque no fueron aportados. Las salidas archivadas no se elevan a captura propia.
- No se analizaron slides o vídeos de Recon/TROOPERS. Solo se usa el alcance publicado en sus páginas oficiales.
- No se hizo auditoría exhaustiva de seguridad de los forks, de sus blobs, licencias de terceros o actualizadores.
- No se comprobó si las releases descargables coinciden byte a byte con los commits. Se fija el commit fuente y, por separado, lo que dice cada README.
- NVD HTML no dio texto útil con `webfetch`, aunque `curl` respondió 200. La confirmación legible se hizo con NVD API y el registro CVE oficial.
- No hay fuentes inaccesibles en la pasada `curl` final: 25/25 devolvieron HTTP 200.
- No se modificaron compatibilidades, proveedores, código de app/core ni archivos fuera del ownership de esta hoja.

## 16. Verificación

`analysis/research-supplement/verify_research.py` usa solo la biblioteca estándar. Comprueba las fuentes obligatorias, commits/licencias, clasificación, alcance CVE, referencias cruzadas, línea base y ausencia de elevación. Incluye controles negativos aislados para mezcla stock/custom, CVE aplicado indebidamente a F03266, claims sin fuente y elevación de provider-matrix.

Comando esperado:

```bash
python3 analysis/research-supplement/verify_research.py
```

Salida final esperada:

```text
WIBOX_RESEARCH_SUPPLEMENT_VERIFIED
```
