# ChatUtilities DiosesMon

Panel de utilidades para el servidor DiosesMon (Minecraft 1.21.1, Fabric). Reúne en una sola ventana el chat (general, clan, alianza, privados, raids y comercio), los teletransportes, los pagos y los accesos rápidos. Sin bordes, con temas de color: seis paletas y una personalizada con tus propios colores (RGB o código hexadecimal).

## Instalación

1. Copia `ChatUtilitiesDiosesMon.jar` en la carpeta `mods` de tu instancia.
2. Requisitos: Fabric Loader 0.16 o superior, Fabric API (0.102 o superior para 1.21.1) y Java 21.
3. El servidor no necesita instalar nada. El mod es solo de cliente.
4. No lo instales junto a Amanecer Dorado: los dos usan la misma tecla.

## Uso

- **H** abre o cierra el panel. Se cambia en Opciones > Controles > ChatUtilities DiosesMon.
- La primera vez que entras a un mundo después de instalarlo, sale un aviso arriba a la izquierda: "Pulsa H para abrir el panel". Solo sale una vez.
- La configuración se abre con el **engranaje** de la cabecera del panel. Tiene cuatro pestañas: Avisos, Sonidos, Tamaños y Tema.
- La configuración se guarda en `config/chatutilitiesdiosesmon.json`.

## Diseño del panel

- **Pestañas arriba**, tipo carpeta. Los mensajes sin leer salen dentro de la pestaña, por ejemplo "Clan (12)".
- **Cabecera**: icono y nombre de la pestaña abierta, buscador de palabras, engranaje y ✕.
- **Barra de iconos a la derecha**: Chat, TP y Pagos, y debajo los accesos rápidos.

## Accesos rápidos (barra derecha)

| Icono | Acción |
|---|---|
| Máquina de curación (si el juego tiene Cobblemon) | Curar Pokémon |
| Filete | Comida |
| Yunque | Reparar todo |
| Cama | AFK |

El botón **AFK** está **verde** si no estás AFK y **rojo** si lo estás. Al pasar el ratón dice "Estás AFK ahora · mm:ss", y mientras estás AFK el contador se ve debajo del botón. El estado sale de los mensajes del servidor, así que también cambia si el servidor te quita el AFK al moverte.

## Pestañas del chat

| Pestaña | Qué muestra | Cómo envía |
|---|---|---|
| General | Chat de jugadores y avisos de legendarios (aparición y captura). Nada más del servidor. | Chat normal o `/comando` |
| Clan | Mensajes del clan. | `/clan chat <mensaje>` |
| Alianza | Mensajes de los clanes aliados. | `/clan chat <mensaje>` |
| Privados | Lista de conversaciones a la izquierda (cabeza, botón TP y ✕) y mensajes en burbujas a la derecha. "Nuevo chat…" abre una conversación nueva; la lista de nombres solo sale al escribir. | `/msg <jugador> <mensaje>` |
| Raids | Raids anunciadas con el mensaje completo, botón **TPA** y botón **MSG**, que abre el privado con esa persona. A la derecha, tu grupo de raid. | `/raids …` |
| Comercio | Mensajes de compra, venta y cambio del chat público. | Chat normal |

**Buscador de palabras**: filtra la pestaña abierta (General, Clan, Alianza, Privados y Comercio). Busca en el texto, el autor y el tag, sin distinguir mayúsculas ni tildes. Con varias palabras, muestra las líneas que tienen todas. Si no hay coincidencias dice "Sin resultados de búsqueda".

**Historial guardado**: los mensajes se guardan entre sesiones en `config/chatutilitiesdiosesmon/history/` (general, clan y privados, 2000 líneas cada uno), así que el buscador también encuentra mensajes de días anteriores.

## TP y Pagos

- **TP**: solicitudes con Aceptar y Rechazar, historial y envío de `/tpa`, `/tpahere` y `/tpcancel`.
- **Pagos**: jugador, moneda, cantidad (con botones +1K, +10K, +100K y +1M) y confirmación en dos clics. El historial muestra el estado de cada pago:
  - **Confirmado**: el servidor respondió "Has enviado…".
  - **Rechazado**: el servidor respondió con un error justo después del pago, o un pago en Pokédólares no se confirmó en 12 segundos. Se sigue guardando en el historial.
  - **Enviando…**: esperando la respuesta del servidor.
  - **Sin confirmar**: Battle Points y Safari Points, de los que no se conoce el mensaje de confirmación.
  - **Recibido**: pagos que te enviaron.

## Avisos, animación y tamaños

- Los avisos de privados, TP y raids, y cada mensaje del clan, usan la misma animación: aparece la cabeza del jugador, se abre el mensaje, se esconde de derecha a izquierda, queda la cabeza y se desvanece. Los mensajes del clan salen uno por uno, apilados.
- El aviso de TP dice "Nombre quiere ir hacia ti · Pulsa G para responder". Si pulsas la tecla del panel mientras se ve, el panel se abre directo en TP. Con un aviso de privado, se abre directo en esa conversación.
- En las listas de mensajes, si subes, aparece el botón **↓ Bajar** para volver al último mensaje.
- En la caja de mensaje del panel, **↑** y **↓** recorren los mensajes que enviaste, como en el chat de Minecraft.
- **Mensajes del clan en pantalla**: 2 a la vez por defecto (de 1 a 5 en Configuración). Si llega otro, el más viejo se cierra cuando lleva al menos 2 segundos visible y el nuevo ocupa su lugar. En "Mover avisos" se ven los huecos numerados donde saldrá cada mensaje.
- **Chat de Minecraft**: el mod lo oculta del HUD (los mensajes siguen llegando y T lo abre normal). El botón del cartel en la barra derecha lo muestra u oculta: verde visible, rojo oculto.
- Privados, TP y clan tienen cada uno su propia escala.
- **Configuración > Avisos**: qué avisos salen, duración, visor del clan y opacidad. "Mover avisos" para colocarlos y, arrastrando la esquina dorada, agrandarlos o achicarlos enteros (caja, cabeza y texto) entre 50% y 200%. Los avisos muestran siempre el mensaje completo: la caja crece hacia abajo lo necesario.
- **Configuración > Sonidos**: un tono y un volumen para TP, privados y clan.
- **Configuración > Tamaños**: tamaño del panel (Pequeño, Normal, Grande), ancho de los avisos de privados y TP, ancho y líneas del visor del clan.

## Temas de color

**Configuración > Tema**. Pulsa una paleta y todo el mod cambia al momento: panel, configuración, avisos flotantes y visor del clan.

| Tema | Acento | Fondo |
|---|---|---|
| Océano (por defecto) | `#5CC8FF` | `#06121F` |
| Esmeralda | `#5CE6A0` | `#061A10` |
| Amatista | `#C79BFF` | `#140A22` |
| Carmesí | `#FF6B7F` | `#1E070B` |
| Sakura | `#FF9ED2` | `#1F0A16` |
| Noche | `#D8DEE9` | `#111418` |
| Personalizado | tú eliges | tú eliges |

**Personalizado**: para el color de acento y para el fondo hay un campo de **código hexadecimal** (`#5CC8FF`, `5CC8FF` o la forma corta `#5CF`) y tres barras (Rojo, Verde y Azul, de 0 a 255), con una vista previa. Un código completo se aplica mientras lo escribes y las barras se mueven solas; uno incompleto sale en rojo. La primera vez parte de los colores del tema que tenías. El resto de la paleta (textos, bordes, tarjetas, campos) se calcula sola a partir de esos dos colores. Si eliges un fondo claro, los textos pasan a oscuro. Los colores fijos con significado (verde de éxito, rojo de error, azul de privados y alianza, colores de las monedas) no cambian.

Se guarda en `config/chatutilitiesdiosesmon.json` (`theme`, `customAccent`, `customBase`).

## Actualizador

Revisa las versiones en https://github.com/mc-albin/ChatUtilitiesDiosesMon. Si hay una nueva, sale el botón "Actualizar", comprueba la huella SHA-256 y se instala al cerrar el juego.

Para publicar una versión nueva: `.\publicar.ps1 1.x.x "notas"`.

## Compilar

Requiere el JDK 21.

```
./gradlew build
```

El jar queda en `build/libs/ChatUtilitiesDiosesMon.jar`.

## Estructura

```
chatutilities/
  core/            lógica (chat, TP, pagos, raids, sonidos, config)
  fabric/          entrypoint y eventos de Fabric
  ui/              panel, configuración, editor de posiciones, Theme (paleta)
    section/       una clase por apartado
    widget/        botones, campos, listas de mensajes, burbujas y selector de jugador
  hud/             avisos flotantes y animación de llegada
```
