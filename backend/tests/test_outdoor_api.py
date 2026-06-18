"""Tests de /outdoor/route. Nunca llaman a Google real."""

import logging

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.services import outdoor_service

client = TestClient(app)

PRIVATE_DESTINATION = "Calle Privada 1234 PRIVADO_NO_LOGUEAR"


def _payload(**overrides) -> dict:
    payload = {
        "origin_latitude": -34.6037,
        "origin_longitude": -58.3816,
        "destination": "plaza de prueba",
    }
    payload.update(overrides)
    return payload


def _forbid_google(monkeypatch) -> None:
    async def boom(*_args, **_kwargs):
        raise AssertionError("no debe llamar a Google en este camino")

    monkeypatch.setattr(outdoor_service, "_call_routes_api", boom)


def _forbid_ors(monkeypatch) -> None:
    async def boom(*_args, **_kwargs):
        raise AssertionError("no debe llamar a OpenRouteService en este camino")

    monkeypatch.setattr(outdoor_service, "_ors_geocode", boom)
    monkeypatch.setattr(outdoor_service, "_ors_directions", boom)


def test_route_unconfigured_degrades_honestly(monkeypatch) -> None:
    monkeypatch.setattr(outdoor_service.settings, "allow_mock_ai", False)
    monkeypatch.setattr(outdoor_service.settings, "route_provider", "openrouteservice")
    monkeypatch.setattr(outdoor_service.settings, "openrouteservice_api_key", "")
    _forbid_google(monkeypatch)
    _forbid_ors(monkeypatch)

    response = client.post("/outdoor/route", json=_payload())
    assert response.status_code == 200
    data = response.json()
    assert data["configured"] is False
    assert data["status"] == "UNCONFIGURED"
    assert data["error_code"] == "route_provider_unconfigured"
    assert data["steps"] == []


# Migración ORS: con el proveedor default, Google JAMÁS recibe solicitudes.
def test_ors_route_parses_and_never_calls_google(monkeypatch) -> None:
    monkeypatch.setattr(outdoor_service.settings, "allow_mock_ai", False)
    monkeypatch.setattr(outdoor_service.settings, "route_provider", "openrouteservice")
    monkeypatch.setattr(outdoor_service.settings, "openrouteservice_api_key", "test-ors-key")
    _forbid_google(monkeypatch)

    async def fake_geocode(_dest, _lat, _lng):
        return -58.3700, -34.6080, "Plaza de Prueba, CABA"

    async def fake_directions(_olat, _olng, _dlon, _dlat):
        return {
            "summary": {"distance": 412.3, "duration": 301.0},
            "segments": [
                {
                    "steps": [
                        {"instruction": "Diríjase al norte", "distance": 120.0},
                        {"instruction": "Gire a la derecha", "distance": 292.3},
                    ]
                }
            ],
        }

    monkeypatch.setattr(outdoor_service, "_ors_geocode", fake_geocode)
    monkeypatch.setattr(outdoor_service, "_ors_directions", fake_directions)

    data = client.post("/outdoor/route", json=_payload()).json()
    assert data["ok"] is True
    assert data["status"] == "ROUTE"
    assert data["destination_name"] == "Plaza de Prueba, CABA"
    assert data["total_distance_meters"] == 412
    assert data["total_duration_seconds"] == 301
    assert [s["instruction"] for s in data["steps"]] == [
        "Diríjase al norte",
        "Gire a la derecha",
    ]


def test_ors_geocode_empty_is_not_found(monkeypatch) -> None:
    monkeypatch.setattr(outdoor_service.settings, "allow_mock_ai", False)
    monkeypatch.setattr(outdoor_service.settings, "route_provider", "openrouteservice")
    monkeypatch.setattr(outdoor_service.settings, "openrouteservice_api_key", "test-ors-key")
    _forbid_google(monkeypatch)

    async def fake_geocode(_dest, _lat, _lng, **_kwargs):
        return None

    async def fake_nearby(_dest, _lat, _lng):
        return None

    monkeypatch.setattr(outdoor_service, "_ors_geocode", fake_geocode)
    monkeypatch.setattr(outdoor_service, "_ors_geocode_nearby", fake_nearby)
    data = client.post("/outdoor/route", json=_payload()).json()
    assert data["status"] == "NOT_FOUND"


# V1.10.2 — contexto de ciudad por GEOMETRÍA: si la búsqueda amplia no
# encuentra nada, se reintenta acotado a la ciudad real del usuario
# (radio chico + layers de dirección/calle).
def test_ors_geocode_retries_nearby_when_wide_search_misses(monkeypatch) -> None:
    monkeypatch.setattr(outdoor_service.settings, "allow_mock_ai", False)
    monkeypatch.setattr(outdoor_service.settings, "route_provider", "openrouteservice")
    monkeypatch.setattr(outdoor_service.settings, "openrouteservice_api_key", "test-ors-key")
    _forbid_google(monkeypatch)

    calls: list[str] = []

    async def fake_geocode(dest, _lat, _lng, **_kwargs):
        calls.append(f"wide:{dest}")
        return None

    async def fake_nearby(dest, _lat, _lng):
        calls.append(f"nearby:{dest}")
        return -68.0600, -38.9520, "General San Martín, Neuquén"

    async def fake_directions(_olat, _olng, _dlon, _dlat):
        return {
            "summary": {"distance": 480.0, "duration": 400.0},
            "segments": [
                {"steps": [{"instruction": "Caminá derecho", "distance": 480.0}]}
            ],
        }

    monkeypatch.setattr(outdoor_service, "_ors_geocode", fake_geocode)
    monkeypatch.setattr(outdoor_service, "_ors_geocode_nearby", fake_nearby)
    monkeypatch.setattr(outdoor_service, "_ors_directions", fake_directions)

    data = client.post(
        "/outdoor/route",
        json=_payload(
            origin_latitude=-38.9516,
            origin_longitude=-68.0591,
            destination="san martin 500",
        ),
    ).json()
    assert data["status"] == "ROUTE"
    assert calls == ["wide:san martin 500", "nearby:san martin 500"]
    assert data["destination_name"] == "General San Martín, Neuquén"


# V1.10.2 — homónimos lejanos: si el primer candidato queda a más de 10 km,
# se reintenta acotado a la ciudad y gana el resultado MÁS CERCANO.
# (Evidencia real: "san martin 500" desde Neuquén elegía una San Martín
# de Río Negro a 14 km → ruta a pie de 25 km, absurda.)
def test_ors_geocode_prefers_closer_nearby_match(monkeypatch) -> None:
    monkeypatch.setattr(outdoor_service.settings, "allow_mock_ai", False)
    monkeypatch.setattr(outdoor_service.settings, "route_provider", "openrouteservice")
    monkeypatch.setattr(outdoor_service.settings, "openrouteservice_api_key", "test-ors-key")
    _forbid_google(monkeypatch)

    async def fake_geocode(_dest, _lat, _lng, **_kwargs):
        # Búsqueda amplia: homónimo de Río Negro a ~25 km del origen.
        return -67.7800, -39.0300, "500 General San Martin, RN"

    async def fake_nearby(_dest, _lat, _lng):
        # Acotada a la ciudad: la San Martín de Neuquén capital (~0 km).
        return -68.0600, -38.9520, "General San Martín, Neuquén"

    async def fake_directions(_olat, _olng, _dlon, _dlat):
        return {
            "summary": {"distance": 900.0, "duration": 800.0},
            "segments": [
                {"steps": [{"instruction": "Caminá derecho", "distance": 900.0}]}
            ],
        }

    monkeypatch.setattr(outdoor_service, "_ors_geocode", fake_geocode)
    monkeypatch.setattr(outdoor_service, "_ors_geocode_nearby", fake_nearby)
    monkeypatch.setattr(outdoor_service, "_ors_directions", fake_directions)

    data = client.post(
        "/outdoor/route",
        json=_payload(
            origin_latitude=-38.9516,
            origin_longitude=-68.0591,
            destination="san martin 500",
        ),
    ).json()
    assert data["status"] == "ROUTE"
    assert data["destination_name"] == "General San Martín, Neuquén"


# V1.10.2 — destino real pero fuera de alcance peatonal (ORS 2004): estado
# propio TOO_FAR, no NOT_FOUND ni error técnico.
def test_ors_walk_too_far_is_distinct_status(monkeypatch) -> None:
    monkeypatch.setattr(outdoor_service.settings, "allow_mock_ai", False)
    monkeypatch.setattr(outdoor_service.settings, "route_provider", "openrouteservice")
    monkeypatch.setattr(outdoor_service.settings, "openrouteservice_api_key", "test-ors-key")
    _forbid_google(monkeypatch)

    async def fake_geocode(_dest, _lat, _lng, **_kwargs):
        return -68.06, -38.95, "Destino Lejano"

    async def fake_directions(_olat, _olng, _dlon, _dlat):
        raise outdoor_service._WalkTooFarError()

    monkeypatch.setattr(outdoor_service, "_ors_geocode", fake_geocode)
    monkeypatch.setattr(outdoor_service, "_ors_directions", fake_directions)

    data = client.post(
        "/outdoor/route",
        json=_payload(origin_latitude=-38.9516, origin_longitude=-68.0591),
    ).json()
    assert data["status"] == "TOO_FAR"
    assert data["ok"] is True
    assert data["steps"] == []


def test_route_mock_mode_returns_walking_route(monkeypatch) -> None:
    monkeypatch.setattr(outdoor_service.settings, "allow_mock_ai", True)
    _forbid_google(monkeypatch)

    response = client.post("/api/v1/outdoor/route", json=_payload())
    data = response.json()
    assert data["ok"] is True
    assert data["status"] == "ROUTE"
    assert data["total_distance_meters"] > 0
    assert len(data["steps"]) >= 2
    assert all(step["instruction"] for step in data["steps"])


def test_route_parses_google_payload(monkeypatch) -> None:
    async def fake(_lat, _lng, _dest):
        return 200, {
            "routes": [
                {
                    "distanceMeters": 420,
                    "duration": "300s",
                    "legs": [
                        {
                            "steps": [
                                {
                                    "navigationInstruction": {"instructions": "Caminá hacia el norte"},
                                    "distanceMeters": 120,
                                },
                                {
                                    "navigationInstruction": {"instructions": "Girá a la derecha"},
                                    "distanceMeters": 300,
                                },
                            ]
                        }
                    ],
                }
            ]
        }

    monkeypatch.setattr(outdoor_service.settings, "allow_mock_ai", False)
    monkeypatch.setattr(outdoor_service.settings, "route_provider", "google")
    monkeypatch.setattr(outdoor_service.settings, "google_maps_api_key", "test-key")
    monkeypatch.setattr(outdoor_service, "_call_routes_api", fake)
    _forbid_ors(monkeypatch)

    data = client.post("/outdoor/route", json=_payload()).json()
    assert data["total_distance_meters"] == 420
    assert data["total_duration_seconds"] == 300
    assert [s["instruction"] for s in data["steps"]] == [
        "Caminá hacia el norte",
        "Girá a la derecha",
    ]


def test_route_not_found(monkeypatch) -> None:
    async def fake(_lat, _lng, _dest):
        return 200, {"routes": []}

    monkeypatch.setattr(outdoor_service.settings, "allow_mock_ai", False)
    monkeypatch.setattr(outdoor_service.settings, "route_provider", "google")
    monkeypatch.setattr(outdoor_service.settings, "google_maps_api_key", "test-key")
    monkeypatch.setattr(outdoor_service, "_call_routes_api", fake)
    _forbid_ors(monkeypatch)

    data = client.post("/outdoor/route", json=_payload()).json()
    assert data["status"] == "NOT_FOUND"


@pytest.mark.parametrize("bad", [{"destination": "x"}, {"origin_latitude": 999.0}])
def test_route_request_validation(monkeypatch, bad: dict) -> None:
    _forbid_google(monkeypatch)
    response = client.post("/outdoor/route", json=_payload(**bad))
    assert response.status_code == 422


def test_destination_not_logged(monkeypatch, caplog) -> None:
    monkeypatch.setattr(outdoor_service.settings, "allow_mock_ai", True)
    _forbid_google(monkeypatch)
    caplog.set_level(logging.DEBUG, logger="ojoclaro.outdoor")

    client.post("/outdoor/route", json=_payload(destination=PRIVATE_DESTINATION))
    assert "PRIVADO_NO_LOGUEAR" not in caplog.text
    # Tampoco coordenadas exactas en logs.
    assert "-34.6037" not in caplog.text


def test_agent_registry_includes_outdoor_tools() -> None:
    from app.services.agent_tools import AGENT_TOOL_NAMES

    for tool in (
        "check_location_permission",
        "check_location_services_enabled",
        "get_current_location",
        "describe_current_location",
        "start_outdoor_guidance",
        "get_route_progress",
        "stop_outdoor_guidance",
        "describe_scene_on_demand",
    ):
        assert tool in AGENT_TOOL_NAMES
