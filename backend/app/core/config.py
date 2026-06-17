from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "Ojo Claro AI"
    app_env: str = "local"
    openai_api_key: str = ""
    # Modelo primario y fallback, ambos configurables por env. El fallback se usa
    # solo si la API rechaza el primario (p. ej. modelo no disponible para la cuenta).
    # No hardcodear el modelo en varios lugares: estos dos campos son la fuente unica.
    openai_model: str = "gpt-5.4-mini"
    openai_model_fallback: str = "gpt-5.2"
    gemini_api_key: str = ""
    image_retention_seconds: int = 0
    allow_mock_ai: bool = False
    # Path opcional a un system prompt de /intent en disco (override). Si esta
    # vacio, se usa el embebido en app/services/intent_prompt.py.
    intent_prompt_path: str = ""

    # --- Estela Agent Core v1 (/agent/next) ---
    # Planner remoto: SOLO este modelo. Sin fallback automatico a un modelo
    # grande: si gpt-5.4-mini no responde, el agente degrada honesto.
    openai_agent_model: str = "gpt-5.4-mini"
    openai_agent_max_output_tokens: int = 600
    openai_agent_timeout_seconds: float = 20.0
    openai_agent_max_steps: int = 8
    openai_agent_max_replans: int = 2
    openai_agent_max_duration_seconds: int = 90

    # --- Estela Outdoor Guidance v1 (/outdoor/route) ---
    # Proveedor de rutas peatonales. "openrouteservice" (default) u "google".
    # Las claves viven SOLO en backend/.env; vacia => degradacion honesta.
    route_provider: str = "openrouteservice"
    openrouteservice_api_key: str = ""
    google_maps_api_key: str = ""

    @property
    def has_openrouteservice_api_key(self) -> bool:
        return bool(self.openrouteservice_api_key.strip())

    @property
    def has_google_maps_api_key(self) -> bool:
        return bool(self.google_maps_api_key.strip())

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore"
    )

    @property
    def has_openai_api_key(self) -> bool:
        return bool(self.openai_api_key.strip())


settings = Settings()
