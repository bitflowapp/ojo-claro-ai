from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "Ojo Claro AI"
    app_env: str = "local"
    openai_api_key: str = ""
    gemini_api_key: str = ""
    image_retention_seconds: int = 0
    allow_mock_ai: bool = True

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore"
    )


settings = Settings()
