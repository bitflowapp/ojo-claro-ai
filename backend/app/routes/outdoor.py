"""Endpoint POST /outdoor/route (Estela Outdoor Guidance v1).

Las coordenadas de origen llegan, se usan para la ruta y NO se loguean ni se
persisten. El alias /api/v1 lo monta app.main.
"""

from fastapi import APIRouter
from pydantic import BaseModel, ConfigDict, Field

from app.services.outdoor_service import compute_walking_route, reverse_geocode

router = APIRouter(tags=["outdoor"])


class OutdoorReverseRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    latitude: float = Field(ge=-90.0, le=90.0)
    longitude: float = Field(ge=-180.0, le=180.0)


class OutdoorReverseResponse(BaseModel):
    ok: bool
    configured: bool
    status: str
    label: str | None
    error_code: str | None


@router.post("/outdoor/reverse", response_model=OutdoorReverseResponse)
async def outdoor_reverse(request: OutdoorReverseRequest) -> OutdoorReverseResponse:
    result = await reverse_geocode(
        latitude=request.latitude,
        longitude=request.longitude,
    )
    return OutdoorReverseResponse.model_validate(result)


class OutdoorRouteRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    origin_latitude: float = Field(ge=-90.0, le=90.0)
    origin_longitude: float = Field(ge=-180.0, le=180.0)
    destination: str = Field(min_length=2, max_length=200)


class OutdoorRouteStep(BaseModel):
    instruction: str
    distance_meters: int


class OutdoorRouteResponse(BaseModel):
    ok: bool
    configured: bool
    status: str
    destination_name: str | None
    total_distance_meters: int
    total_duration_seconds: int
    steps: list[OutdoorRouteStep]
    error_code: str | None


@router.post("/outdoor/route", response_model=OutdoorRouteResponse)
async def outdoor_route(request: OutdoorRouteRequest) -> OutdoorRouteResponse:
    result = await compute_walking_route(
        origin_lat=request.origin_latitude,
        origin_lng=request.origin_longitude,
        destination=request.destination,
    )
    return OutdoorRouteResponse.model_validate(result)
