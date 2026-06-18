"""V1.4 — POST /outdoor/reverse: calle/barrio útiles para "¿dónde estoy?"."""

from fastapi.testclient import TestClient

import app.routes.outdoor as outdoor_routes
from app.main import app

client = TestClient(app)


def test_reverse_ok(monkeypatch):
    async def fake_reverse(latitude: float, longitude: float) -> dict:
        return {
            "ok": True,
            "configured": True,
            "status": "OK",
            "label": "San Martín 500, Centro",
            "error_code": None,
        }

    monkeypatch.setattr(outdoor_routes, "reverse_geocode", fake_reverse)
    response = client.post(
        "/outdoor/reverse", json={"latitude": -32.95, "longitude": -60.65}
    )
    assert response.status_code == 200
    body = response.json()
    assert body["ok"] is True
    assert body["label"] == "San Martín 500, Centro"


def test_reverse_not_found(monkeypatch):
    async def fake_reverse(latitude: float, longitude: float) -> dict:
        return {
            "ok": True,
            "configured": True,
            "status": "NOT_FOUND",
            "label": None,
            "error_code": None,
        }

    monkeypatch.setattr(outdoor_routes, "reverse_geocode", fake_reverse)
    response = client.post(
        "/api/v1/outdoor/reverse", json={"latitude": 0.0, "longitude": 0.0}
    )
    assert response.status_code == 200
    assert response.json()["label"] is None


def test_reverse_rejects_invalid_coordinates():
    response = client.post(
        "/outdoor/reverse", json={"latitude": 999, "longitude": 0}
    )
    assert response.status_code == 422


def test_reverse_label_never_includes_coordinates(monkeypatch):
    async def fake_reverse(latitude: float, longitude: float) -> dict:
        return {
            "ok": True,
            "configured": True,
            "status": "OK",
            "label": "Bulevar Oroño, Rosario",
            "error_code": None,
        }

    monkeypatch.setattr(outdoor_routes, "reverse_geocode", fake_reverse)
    response = client.post(
        "/outdoor/reverse", json={"latitude": -32.95, "longitude": -60.65}
    )
    text = response.text
    assert "-32.95" not in text
    assert "-60.65" not in text
