from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.routes.assist import router as assist_router
from app.core.config import settings

app = FastAPI(
    title="Ojo Claro AI API",
    version="0.1.0",
    description="Backend IA para asistencia visual accesible."
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"] if settings.app_env == "local" else [],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(assist_router, prefix="/api/v1")


@app.get("/health")
def health() -> dict[str, str]:
    return {
        "status": "ok",
        "app": settings.app_name,
        "env": settings.app_env
    }
