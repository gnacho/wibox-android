# v0.3.0 - Vista en vivo por el túnel del SDK

4 de noviembre de 2026

Primera versión que consigue abrir la cámara del timbre y pintar vídeo en el móvil. También es la primera que dice la verdad cuando no puede: si el canal está ocupado, lo dice en un segundo en lugar de quedarse en negro.

## Lo que funciona, verificado en un Honor Magic V5

- Alta por invitación. Se acepta el QR del timbre y el cloud devuelve la cuenta y la contraseña dinámica. El canal `userauth` habla el contrato real del fabricante: POST ascendente y long-poll descendente con cookie, envelope XStream con los campos en kebab-case y versión vacía.
- Token de dispositivo. `get-device-token` devuelve `authCode` y la `dataEncodeKey` de 32 bytes que usa el canal de vídeo.
- Túnel al timbre. `createP2PClient` del SDK, espera a que el servicio UST esté listo y `addPortByP2P(deviceId, 0, -1, 0, 9, 1200, 20)` devuelve un puerto local. El SDK escucha en `127.0.0.1` y publica ahí el puerto de media del timbre.
- QUII sobre TCP. Setup de 32 bytes en claro con `0xA9`, respuesta con estado 0, cifrado 2 y perfil SHA 1. Después el play con usuario `adminapp2`, contraseña derivada del `authCode` y destino `/mode=real&idc=1&ids=1&ap=2`. Llegan frames H264 y G711A y se pintan con `MediaCodec`.
- Apertura remota. Orden transparente 4 dentro de la sesión QUII, con la contraseña de apertura en SHA-256, cerradura 1 y canal 1. Respuesta `WIBOX-UNLOCK RESP 0000` y `UNLOCK error=0`.
- CGI local por el mismo túnel. `get.device.status` devuelve MAC, versión de firmware, modelo y zona horaria. También responden `get.product.info`, `get.tfcard.info` y `get.encode.fps`.
- Aviso de cuelgue. El timbre manda un transparente 0xFE con el motivo. La app lo traduce y lo enseña: canal ocupado, sin permiso, vídeo apagado, batería, etc.
- Reintento de canal ocupado. Cada 30 segundos, hasta 20 intentos, con cuenta atrás en pantalla.
- Cierre limpio. La sesión se cierra de forma síncrona al salir de la pantalla y al parar el servicio: socket, códec, audio y puerto P2P. Antes quedaba viva y dejaba al timbre ocupado para todos, incluida la app oficial.

## Lo que no funciona todavía

- El canal del timbre se queda ocupado con frecuencia. La app oficial dice lo mismo cuando pasa, así que no es un fallo del protocolo. Mientras está ocupado no hay vídeo: ahora se avisa y se reintenta, antes era una pantalla negra sin explicación.
- Despertar el timbre no está resuelto. Ni el `device-awaken` por MQTT ni el `deviceAwaken` del SDK lo garantizan. Después de despertar, el estado que devuelve el SDK sigue siendo la máscara de reposo y el MQTT lo lista como fuera de línea.
- La ruta sin SDK (UST, MQTT y túnel RB-UDP en Kotlin puro) abre el túnel pero no recibe candidatos al `p2pconnect` en esta red. Queda como respaldo y no se usa.
- El color sale con dominante verde o cian en el decodificador Tegra del Shield donde se midió. Los aspectos BT709 lo mejoran y no lo quitan. No se ha medido en el móvil objetivo.
- La app no puede gestionar la cuenta ni desvincular el timbre. El proveedor no expone esas operaciones y la app oficial no se toca.

## Lo que no está en el repositorio

El SDK nativo del fabricante (`libqv-p2p-v2.so`), sus certificados de cliente y las credenciales del timbre no se publican. El binario es propietario y los certificados y contraseñas son datos de un dispositivo real. Sin ellos el proyecto compila y arranca, pero la vista en vivo no puede abrir túnel.

Para usarlo en local hacen falta dos cosas:

- `app/src/main/assets/client.pem`, `client.txt` y `qv_ca.pem`, extraídos del APK oficial.
- En `local.properties`: `wibox.account`, `wibox.password`, `wibox.clientId` y `wibox.unlockPassword`.

Las credenciales también se pueden pasar por entorno (`WIBOX_ACCOUNT`, `WIBOX_PASSWORD`, `WIBOX_CLIENT_ID`, `WIBOX_UNLOCK_PASSWORD`).

## Cómo se probó

- 76 tests deterministas del núcleo en verde, sin red: contrato del envelope, concurrencia del canal `userauth`, protocolo QUII, framing TCP, demux de paquetes, CGI y permisos de dispositivo.
- Tests de la app en verde.
- Pruebas en dispositivo real: instalación por ADB, arranque de la vista en vivo, lectura de logs propios y capturas de pantalla. El vídeo se vio con el OSD del timbre en pantalla, y las rondas posteriores devolvieron cuelgue por canal ocupado, que es exactamente lo que decía la app oficial.

## Pendiente

- Confirmar si el canal ocupado se libera solo con el tiempo o si queda una sesión zombi en el timbre. La fuga nuestra está cerrada.
- Medir el color en el móvil objetivo.
- Decidir si el SDK nativo se puede sustituir por la ruta UST cuando esa red funcione.
- Gestionar el dispositivo desde la app cuando el proveedor exponga esas operaciones (issue #19).
