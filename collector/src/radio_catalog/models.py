from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field, HttpUrl

StreamStatus = Literal["unknown", "candidate", "online", "offline", "blocked", "invalid"]
StationStatus = Literal[
    "active", "temporarily_unavailable", "broken", "duplicate", "review_required"
]


class Stream(BaseModel):
    url: HttpUrl
    protocol: str | None = None
    format: str | None = None
    codec: str | None = None
    bitrate_kbps: int | None = Field(default=None, ge=0)
    reliability: float | None = Field(default=None, ge=0, le=1)
    is_hls: bool = False
    status: StreamStatus = "unknown"
    last_checked_at: datetime | None = None
    source: str


class SourceRecord(BaseModel):
    provider: str
    source_id: str | None = None
    source_url: HttpUrl | None = None
    discovered_at: datetime


class Station(BaseModel):
    id: str
    name: str
    country: str
    city: str | None = None
    languages: list[str] = Field(default_factory=list)
    genres: list[str] = Field(default_factory=list)
    homepage: HttpUrl | None = None
    logo: HttpUrl | None = None
    status: StationStatus = "active"
    aliases: list[str] = Field(default_factory=list)
    streams: list[Stream] = Field(default_factory=list)
    sources: list[SourceRecord] = Field(default_factory=list)
