from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.routes.agent import router as agent_router
from app.routes.assist import router as assist_router
from app.routes.conversation import router as conversation_router
from app.routes.intent import router as intent_router
from app.routes.outdoor import router as outdoor_router
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

app.include_router(assist_router)
app.include_router(assist_router, prefix="/api/v1")
app.include_router(intent_router)
app.include_router(intent_router, prefix="/api/v1")
app.include_router(agent_router)
app.include_router(agent_router, prefix="/api/v1")
app.include_router(outdoor_router)
app.include_router(outdoor_router, prefix="/api/v1")
app.include_router(conversation_router)
app.include_router(conversation_router, prefix="/api/v1")


@app.get("/health")
def health() -> dict[str, bool | str]:
    return {
        "ok": True,
        "service": "ojo-claro-backend",
    }
