"""Servicio de rutas peatonales (Estela Outdoor Guidance v1).

Calcula una ruta WALK con Google Routes API v2 usando la clave que vive SOLO
en el backend. Si no hay clave, degrada honesto (configured=false): Android
informa que las rutas no están disponibles pero la ubicación local sigue viva.

Privacidad: no se loguean coordenadas ni destinos; solo longitudes y buckets.
"""

from __future__ import annotations

import logging
from typing import Any

import httpx

from app.core.config import settings

logger = logging.getLogger("ojoclaro.outdoor")

ROUTES_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
HTTP_TIMEOUT_SECONDS = 12.0
FIELD_MASK = (
    "routes.distanceMeters,routes.duration,"
    "routes.legs.steps.navigationInstruction.instructions,"
    "routes.legs.steps.distanceMeters"
)


def _mock_route(destination: str) -> dict:
    return {
        "ok": True,
        "configured": True,
        "status": "ROUTE",
        "destination_name": destination[:80],
        "total_distance_meters": 350,
        "total_duration_seconds": 280,
        "steps": [
            {"instruction": "Caminá derecho por la vereda", "distance_meters": 200},
            {"instruction": "Girá a la izquierda", "distance_meters": 150},
        ],
        "error_code": None,
    }


def _outcome(status: str, error_code: str | None = None, configured: bool = True) -> dict:
    return {
        "ok": error_code is None,
        "configured": configured,
        "status": status,
        "destination_name": None,
        "total_distance_meters": 0,
        "total_duration_seconds": 0,
        "steps": [],
        "error_code": error_code,
    }


def _parse_duration_seconds(raw: Any) -> int:
    # Routes API devuelve "280s".
    if isinstance(raw, str) and raw.endswith("s"):
        try:
            return int(float(raw[:-1]))
        except ValueError:
            return 0
    if isinstance(raw, (int, float)):
        return int(raw)
    return 0


async def _call_routes_api(
    origin_lat: float, origin_lng: float, destination: str
) -> tuple[int, dict | None]:
    payload = {
        "origin": {
            "location": {
                "latLng": {"latitude": origin_lat, "longitude": origin_lng}
            }
        },
        "destination": {"address": destination},
        "travelMode": "WALK",
        "languageCode": "es-AR",
    }
    headers = {
        "Content-Type": "application/json",
        "X-Goog-Api-Key": settings.google_maps_api_key,
        "X-Goog-FieldMask": FIELD_MASK,
    }
    async with httpx.AsyncClient(timeout=HTTP_TIMEOUT_SECONDS) as client:
        response = await client.post(ROUTES_URL, headers=headers, json=payload)
    if response.status_code >= 400:
        return response.status_code, None
    try:
        return response.status_code, response.json()
    except ValueError:
        return response.status_code, None


# --- OpenRouteService (proveedor default) ---

ORS_GEOCODE_URL = "https://api.openrouteservice.org/geocode/search"
ORS_DIRECTIONS_URL = "https://api.openrouteservice.org/v2/directions/foot-walking"


class _WalkTooFarError(Exception):
    """Ruta a pie más larga que el límite del servidor (ORS code 2004).

    V1.10.2: ya no se disfraza de NOT_FOUND; Android ofrece Maps/Uber asistido.
    """


# Si el mejor candidato queda a más de esto del origen, se intenta una
# segunda búsqueda ACOTADA a la ciudad del usuario (radio chico + layers de
# dirección/calle) y gana el resultado MÁS CERCANO. Evidencia real
# 2026-06-11 desde Neuquén capital con "san martin 500":
#  - búsqueda amplia (30 km): elegía una San Martín de Río Negro a 14 km
#    (ruta a pie de 25 km, absurda);
#  - texto con ciudad ("san martin 500, Neuquén, Argentina") y búsqueda
#    estructurada: devolvían el CENTROIDE de la ciudad, no la dirección;
#  - radio 8 km + layers address/street/venue: General San Martín (NQ) a
#    0.5 km — el resultado correcto. El contexto de ciudad se impone por
#    geometría (todo lo que está a <8 km ES la ciudad del usuario), no por
#    texto inventado.
NEAR_PREFERENCE_KM = 10.0
NEARBY_RETRY_RADIUS_KM = 8
NEARBY_RETRY_LAYERS = "address,street,venue"


def _distance_km(lat_a: float, lng_a: float, lat_b: float, lng_b: float) -> float:
    """Distancia haversine aproximada en km (suficiente para ranking)."""
    import math

    rad = math.pi / 180.0
    d_lat = (lat_b - lat_a) * rad
    d_lng = (lng_b - lng_a) * rad
    a = (
        math.sin(d_lat / 2) ** 2
        + math.cos(lat_a * rad) * math.cos(lat_b * rad) * math.sin(d_lng / 2) ** 2
    )
    return 6371.0 * 2.0 * math.asin(min(1.0, math.sqrt(a)))


async def _ors_geocode(
    destination: str,
    focus_lat: float,
    focus_lng: float,
    radius_km: int = 30,
    layers: str | None = None,
) -> tuple[float, float, str] | None:
    """Mejor candidato (lon, lat, label) priorizando cercanía al origen."""
    params = {
        "api_key": settings.openrouteservice_api_key,
        "text": destination,
        "size": 1,
        "lang": "es",
        "focus.point.lat": focus_lat,
        "focus.point.lon": focus_lng,
        # Rutas PEATONALES: solo destinos en el entorno real del usuario.
        # Sin esto, Pelias puede elegir un homónimo a cientos de km y la
        # ruta a pie excede el límite del servidor (error ORS 2004).
        "boundary.circle.lat": focus_lat,
        "boundary.circle.lon": focus_lng,
        "boundary.circle.radius": radius_km,
    }
    if layers:
        params["layers"] = layers
    async with httpx.AsyncClient(timeout=HTTP_TIMEOUT_SECONDS) as client:
        response = await client.get(ORS_GEOCODE_URL, params=params)
    if response.status_code >= 400:
        raise httpx.HTTPStatusError(
            f"ors_geocode_{response.status_code}", request=response.request, response=response
        )
    features = (response.json() or {}).get("features") or []
    if not features:
        return None
    feature = features[0]
    coords = (feature.get("geometry") or {}).get("coordinates") or []
    if len(coords) < 2:
        return None
    label = ((feature.get("properties") or {}).get("label") or destination)[:80]
    return float(coords[0]), float(coords[1]), label


ORS_REVERSE_URL = "https://api.openrouteservice.org/geocode/reverse"


async def _ors_geocode_nearby(
    destination: str, focus_lat: float, focus_lng: float
) -> tuple[float, float, str] | None:
    """Reintento de geocoding ACOTADO a la ciudad real del usuario.

    Radio chico + layers de dirección/calle/lugar: el contexto de ciudad se
    impone por geometría (lo que está a <8 km ES la ciudad de la persona),
    sin inventar texto. Ver evidencia en NEARBY_RETRY_RADIUS_KM.
    """
    return await _ors_geocode(
        destination,
        focus_lat,
        focus_lng,
        radius_km=NEARBY_RETRY_RADIUS_KM,
        layers=NEARBY_RETRY_LAYERS,
    )


async def reverse_geocode(latitude: float, longitude: float) -> dict:
    """Calle/barrio aproximados para "¿dónde estoy?".

    Las coordenadas se usan y NO se loguean. Devuelve un dict plano:
    {ok, configured, status, label, error_code}. El label es texto corto
    hablable ("San Martín 500, Centro, Ciudad").
    """
    if settings.allow_mock_ai:
        return {
            "ok": True,
            "configured": True,
            "status": "OK",
            "label": "Calle de prueba 123, Barrio Demo",
            "error_code": None,
        }
    if not settings.has_openrouteservice_api_key:
        return {
            "ok": False,
            "configured": False,
            "status": "UNCONFIGURED",
            "label": None,
            "error_code": "reverse_unconfigured",
        }
    params = {
        "api_key": settings.openrouteservice_api_key,
        "point.lat": latitude,
        "point.lon": longitude,
        "size": 1,
        "lang": "es",
        "layers": "address,street,locality,neighbourhood",
    }
    try:
        async with httpx.AsyncClient(timeout=HTTP_TIMEOUT_SECONDS) as client:
            response = await client.get(ORS_REVERSE_URL, params=params)
        if response.status_code >= 400:
            logger.warning("outdoor reverse upstream status=%d", response.status_code)
            return {
                "ok": False,
                "configured": True,
                "status": "ERROR",
                "label": None,
                "error_code": f"reverse_upstream_{response.status_code}",
            }
        features = (response.json() or {}).get("features") or []
        if not features:
            return {
                "ok": True,
                "configured": True,
                "status": "NOT_FOUND",
                "label": None,
                "error_code": None,
            }
        props = features[0].get("properties") or {}
        # name = "Calle 123" o nombre del lugar; locality/neighbourhood dan
        # el contexto humano. Nunca devolvemos coordenadas.
        parts = [
            str(props.get("name") or "").strip(),
            str(props.get("neighbourhood") or "").strip(),
            str(props.get("locality") or props.get("county") or "").strip(),
        ]
        label = ", ".join(p for p in parts if p)[:120]
        if not label:
            label = str(props.get("label") or "").strip()[:120]
        if not label:
            return {
                "ok": True,
                "configured": True,
                "status": "NOT_FOUND",
                "label": None,
                "error_code": None,
            }
        return {
            "ok": True,
            "configured": True,
            "status": "OK",
            "label": label,
            "error_code": None,
        }
    except httpx.TimeoutException:
        return {
            "ok": False,
            "configured": True,
            "status": "ERROR",
            "label": None,
            "error_code": "reverse_timeout",
        }
    except Exception:  # noqa: BLE001 - clasificación honesta sin detalles
        logger.warning("outdoor reverse failed", exc_info=False)
        return {
            "ok": False,
            "configured": True,
            "status": "ERROR",
            "label": None,
            "error_code": "reverse_error",
        }


async def _ors_directions(
    origin_lat: float, origin_lng: float, dest_lon: float, dest_lat: float
) -> dict | None:
    payload = {
        "coordinates": [[origin_lng, origin_lat], [dest_lon, dest_lat]],
        "language": "es",
        "instructions": True,
    }
    headers = {
        "Authorization": settings.openrouteservice_api_key,
        "Content-Type": "application/json",
    }
    async with httpx.AsyncClient(timeout=HTTP_TIMEOUT_SECONDS) as client:
        response = await client.post(ORS_DIRECTIONS_URL, headers=headers, json=payload)
    if response.status_code == 404:
        # Punto no ruteable cerca del origen/destino: tratar como no encontrado.
        return None
    if response.status_code == 400 and '"code":2004' in response.text:
        # Destino real pero fuera de alcance peatonal: estado propio para que
        # Android degrade útil (abrir Maps / Uber asistido), no "no encontrado".
        raise _WalkTooFarError()
    if response.status_code >= 400:
        raise httpx.HTTPStatusError(
            f"ors_directions_{response.status_code}", request=response.request, response=response
        )
    routes = (response.json() or {}).get("routes") or []
    return routes[0] if routes else None


async def _compute_route_openrouteservice(
    origin_lat: float, origin_lng: float, destination: str
) -> dict:
    try:
        geocoded = await _ors_geocode(destination, origin_lat, origin_lng)
        first_distance_km = (
            _distance_km(origin_lat, origin_lng, geocoded[1], geocoded[0])
            if geocoded is not None
            else None
        )
        # V1.10.2 — reintento acotado a la ciudad real del usuario: mejora
        # direcciones tipo "san martin 500" y evita homónimos lejanos
        # (gana el candidato más cercano al origen).
        needs_city_context = geocoded is None or (
            first_distance_km is not None and first_distance_km > NEAR_PREFERENCE_KM
        )
        if needs_city_context:
            logger.info(
                "outdoor ors geocode retry_nearby=true farFirstHit=%s",
                first_distance_km is not None,
            )
            retry = await _ors_geocode_nearby(destination, origin_lat, origin_lng)
            if retry is not None:
                retry_distance_km = _distance_km(
                    origin_lat, origin_lng, retry[1], retry[0]
                )
                if first_distance_km is None or retry_distance_km < first_distance_km:
                    geocoded = retry
        if geocoded is None:
            return _outcome("NOT_FOUND", error_code=None)
        dest_lon, dest_lat, label = geocoded

        route = await _ors_directions(origin_lat, origin_lng, dest_lon, dest_lat)
        if route is None:
            return _outcome("NOT_FOUND", error_code=None)
    except _WalkTooFarError:
        logger.info("outdoor ors route too_far=true")
        return _outcome("TOO_FAR", error_code=None)
    except httpx.HTTPStatusError as error:
        status = error.response.status_code if error.response is not None else 0
        body = (error.response.text[:140].replace("\n", " ") if error.response is not None else "")
        # El body de error de ORS es JSON descriptivo, sin claves ni payload.
        logger.warning("outdoor ors http error stage=%s status=%d detail=%s", error.args[0] if error.args else "?", status, body)
        return _outcome("ERROR", error_code=f"route_upstream_{status}")
    except httpx.HTTPError as error:
        logger.warning("outdoor ors network error err=%s", error.__class__.__name__)
        return _outcome("ERROR", error_code="route_upstream_network")

    summary = route.get("summary") or {}
    steps_out: list[dict] = []
    for segment in route.get("segments") or []:
        for step in segment.get("steps") or []:
            instruction = str(step.get("instruction") or "").strip()
            if instruction:
                steps_out.append(
                    {
                        "instruction": instruction[:160],
                        "distance_meters": int(float(step.get("distance") or 0)),
                    }
                )
    if not steps_out:
        return _outcome("NOT_FOUND", error_code=None)

    result = {
        "ok": True,
        "configured": True,
        "status": "ROUTE",
        "destination_name": label,
        "total_distance_meters": int(float(summary.get("distance") or 0)),
        "total_duration_seconds": int(float(summary.get("duration") or 0)),
        "steps": steps_out[:40],
        "error_code": None,
    }
    logger.info(
        "outdoor ors route ok steps=%d distance_bucket=%s",
        len(steps_out),
        "lt500" if result["total_distance_meters"] < 500 else "gte500",
    )
    return result


async def compute_walking_route(
    origin_lat: float, origin_lng: float, destination: str
) -> dict:
    clean_destination = " ".join(destination.split())[:200]

    if settings.allow_mock_ai:
        logger.info("outdoor mock route destination_len=%d", len(clean_destination))
        return _mock_route(clean_destination)

    provider = settings.route_provider.strip().lower()

    # Proveedor default: OpenRouteService. Google queda desactivado salvo
    # selección explícita; con ORS activo, Google no recibe NINGUNA solicitud.
    if provider == "openrouteservice":
        if not settings.has_openrouteservice_api_key:
            logger.warning("outdoor route unconfigured: OPENROUTESERVICE_API_KEY missing")
            return _outcome(
                "UNCONFIGURED", error_code="route_provider_unconfigured", configured=False
            )
        return await _compute_route_openrouteservice(
            origin_lat, origin_lng, clean_destination
        )

    if not settings.has_google_maps_api_key:
        logger.warning("outdoor route unconfigured: GOOGLE_MAPS_API_KEY missing")
        return _outcome("UNCONFIGURED", error_code="route_provider_unconfigured", configured=False)

    try:
        status_code, payload = await _call_routes_api(origin_lat, origin_lng, clean_destination)
    except httpx.HTTPError as error:
        logger.warning("outdoor routes network error err=%s", error.__class__.__name__)
        return _outcome("ERROR", error_code="route_upstream_network")

    if payload is None:
        logger.warning("outdoor routes http error status=%d", status_code)
        return _outcome("ERROR", error_code=f"route_upstream_{status_code}")

    routes = payload.get("routes") or []
    if not routes:
        return _outcome("NOT_FOUND", error_code=None)

    route = routes[0]
    steps_out: list[dict] = []
    for leg in route.get("legs") or []:
        for step in leg.get("steps") or []:
            instruction = (
                (step.get("navigationInstruction") or {}).get("instructions") or ""
            ).strip()
            distance = int(step.get("distanceMeters") or 0)
            if instruction:
                steps_out.append(
                    {"instruction": instruction[:160], "distance_meters": distance}
                )

    result = {
        "ok": True,
        "configured": True,
        "status": "ROUTE",
        "destination_name": clean_destination[:80],
        "total_distance_meters": int(route.get("distanceMeters") or 0),
        "total_duration_seconds": _parse_duration_seconds(route.get("duration")),
        "steps": steps_out[:40],
        "error_code": None,
    }
    logger.info(
        "outdoor route ok steps=%d distance_bucket=%s",
        len(steps_out),
        "lt500" if result["total_distance_meters"] < 500 else "gte500",
    )
    return result
