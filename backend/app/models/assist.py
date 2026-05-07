from enum import Enum
from pydantic import BaseModel, Field


class AppCommandType(str, Enum):
    DESCRIBE_SCENE = "DESCRIBE_SCENE"
    READ_TEXT = "READ_TEXT"
    READ_DOCUMENT = "READ_DOCUMENT"
    IDENTIFY_PRODUCT = "IDENTIFY_PRODUCT"
    EMERGENCY_HELP = "EMERGENCY_HELP"
    CURRENT_LOCATION = "CURRENT_LOCATION"
    UNKNOWN = "UNKNOWN"


class ConfidenceLevel(str, Enum):
    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"


class ResponseCategory(str, Enum):
    TEXT_READING = "TEXT_READING"
    SCENE_DESCRIPTION = "SCENE_DESCRIPTION"
    PRODUCT_IDENTIFICATION = "PRODUCT_IDENTIFICATION"
    DOCUMENT_SUMMARY = "DOCUMENT_SUMMARY"
    LOCATION_HELP = "LOCATION_HELP"
    EMERGENCY = "EMERGENCY"
    SYSTEM = "SYSTEM"


class AppCommand(BaseModel):
    type: AppCommandType
    originalText: str
    locale: str = "es-AR"
    requiresCamera: bool = False
    requiresLocation: bool = False
    highRisk: bool = False


class GeoPoint(BaseModel):
    latitude: float
    longitude: float
    accuracyMeters: float | None = None


class AssistRequest(BaseModel):
    command: AppCommand
    userMessage: str
    imageBase64: str | None = Field(default=None, max_length=8_000_000)
    location: GeoPoint | None = None
    deviceLocale: str = "es-AR"
    accessibilityMode: str = "VOICE_FIRST"


class SuggestedAction(BaseModel):
    id: str
    label: str
    commandType: AppCommandType


class AssistResponse(BaseModel):
    spokenText: str
    shortText: str
    confidence: ConfidenceLevel
    category: ResponseCategory
    safetyNotice: str | None = None
    suggestedActions: list[SuggestedAction] = Field(default_factory=list)
