from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_assist_read_text_mock() -> None:
    payload = {
        "command": {
            "type": "READ_TEXT",
            "originalText": "leer texto",
            "locale": "es-AR",
            "requiresCamera": True,
            "requiresLocation": False,
            "highRisk": False
        },
        "userMessage": "leer texto",
        "imageBase64": None,
        "location": None,
        "deviceLocale": "es-AR",
        "accessibilityMode": "VOICE_FIRST"
    }

    response = client.post("/api/v1/assist", json=payload)

    assert response.status_code == 200
    data = response.json()
    assert data["category"] == "TEXT_READING"
    assert "Modo lectura" in data["spokenText"]


def test_assist_describe_scene_mock() -> None:
    """Verifica que el modo descripción de escena devuelva la categoría y texto esperado."""
    payload = {
        "command": {
            "type": "DESCRIBE_SCENE",
            "originalText": "describir qué tengo enfrente",
            "locale": "es-AR",
            "requiresCamera": True,
            "requiresLocation": False,
            "highRisk": False,
        },
        "userMessage": "describir qué tengo enfrente",
        "imageBase64": None,
        "location": None,
        "deviceLocale": "es-AR",
        "accessibilityMode": "VOICE_FIRST",
    }

    response = client.post("/api/v1/assist", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["category"] == "SCENE_DESCRIPTION"
    assert "Modo descripción" in data["spokenText"]


def test_assist_emergency_help_mock() -> None:
    """Verifica que el modo emergencia devuelva la categoría y la nota de seguridad."""
    payload = {
        "command": {
            "type": "EMERGENCY_HELP",
            "originalText": "ayuda urgente",
            "locale": "es-AR",
            "requiresCamera": False,
            "requiresLocation": True,
            "highRisk": True,
        },
        "userMessage": "ayuda urgente",
        "imageBase64": None,
        "location": None,
        "deviceLocale": "es-AR",
        "accessibilityMode": "VOICE_FIRST",
    }

    response = client.post("/api/v1/assist", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["category"] == "EMERGENCY"
    assert data.get("safetyNotice") is not None


def test_assist_unknown_mock() -> None:
    """Verifica que un comando desconocido caiga en la categoría del sistema."""
    payload = {
        "command": {
            "type": "UNKNOWN",
            "originalText": "blablabla",
            "locale": "es-AR",
            "requiresCamera": False,
            "requiresLocation": False,
            "highRisk": False,
        },
        "userMessage": "blablabla",
        "imageBase64": None,
        "location": None,
        "deviceLocale": "es-AR",
        "accessibilityMode": "VOICE_FIRST",
    }

    response = client.post("/api/v1/assist", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["category"] == "SYSTEM"
    assert "Comando no reconocido" in data["shortText"]
