# Known Risks and Next Steps

Documento de riesgos operativos para demo y continuidad. No es una lista de features nuevas; separa lo validado de lo que todavia necesita trabajo.

## Riesgos conocidos

- Backend depende de PC + ngrok: la APK debug puede depender de que la computadora tenga el backend vivo y el tunel activo.
- Ngrok puede caerse o cambiar: si cambia la URL, la APK instalada con la URL anterior no llega al backend.
- GPS interior puede fluctuar: en interiores, subsuelos o zonas urbanas densas puede haber precision baja o fixes viejos.
- WhatsApp contacto/memoria pendiente: la resolucion de contactos puede fallar si el contacto no esta en memoria interna o no es visible/confiable.
- Voz Android puede sonar generica: el TTS actual depende del motor local; cloud TTS no esta activo.
- Navegacion exterior debe probarse con acompanante: no hacer pruebas de movilidad real sin una persona que pueda intervenir.
- No usar Estela para cruzar calles como autoridad: debe responder con prudencia y no confirmar seguridad de cruce.
- UI de WhatsApp cambia: cambios de WhatsApp pueden romper lectura de pantalla, apertura de chat o deteccion de boton enviar.
- Worktree sucio: hay muchos cambios previos y archivos locales; revisar cuidadosamente antes de cualquier commit.

## Next steps recomendados

- Crear una rama limpia o PR separado para ordenar solo docs/scripts si se quiere publicar QA.
- Confirmar que `.idea` no se commitee.
- Definir una fuente de verdad para contactos de demo y aliases frecuentes.
- Reforzar verificacion del titulo del chat antes del tap de envio.
- Preparar una URL estable de backend para pilotos si se quiere evitar dependencia de ngrok local.
- Hacer prueba exterior con acompanante: ubicacion, ruta, cuanto falta, recalcular y cancelar.
- Validar voz real en Moto: latencia STT, continuidad, volumen y claridad del TTS.
- Mantener tests de contrato: no exigir frases exactas salvo safety y consentimientos.

## No tocar todavia sin plan

- `GlobalAssistantService.kt` salvo bug critico y minimo.
- `OutdoorForegroundService` y coordinadores GPS.
- Flujo de envio WhatsApp.
- Contrato backend conversacional, excepto tests o documentacion.
- `.env`, claves, `.idea` y configuraciones locales de IDE.
