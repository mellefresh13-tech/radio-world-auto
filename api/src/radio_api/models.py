from __future__ import annotations

from pydantic import BaseModel, Field


class StreamResponse(BaseModel):
    url: str
    protocol: str | None = None
    format: str | None = None
    codec: str | None = None
    bitrate_kbps: int | None = None
    reliability: float | None = None
    is_hls: bool = False
    status: str
    last_checked_at: str | None = None
    source: str


class StationResponse(BaseModel):
    id: str
    name: str
    country: str
    city: str | None = None
    languages: list[str] = Field(default_factory=list)
    genres: list[str] = Field(default_factory=list)
    homepage: str | None = None
    logo: str | None = None
    status: str
    has_online_stream: bool
    streams: list[StreamResponse] = Field(default_factory=list)


class StationListResponse(BaseModel):
    stations: list[StationResponse] = Field(default_factory=list)
    total: int
    limit: int
    offset: int


class CountryResponse(BaseModel):
    code: str
    station_count: int


class GenreResponse(BaseModel):
    name: str
    station_count: int


class MetadataProbeResponse(BaseModel):
    url: str
    ok: bool
    http_status: int | None = None
    content_type: str | None = None
    redirected_url: str | None = None
    icy_metaint: int | None = None
    icy_name: str | None = None
    icy_genre: str | None = None
    icy_br: str | None = None
    icy_url: str | None = None
    metadata_protocol: str | None = None
    stream_title: str | None = None
    has_track_metadata: bool = False
    raw_metadata: str | None = None
    error: str | None = None


class StationMetadataResponse(BaseModel):
    id: str
    name: str
    country: str
    city: str | None = None
    homepage: str | None = None
    streams: list[MetadataProbeResponse] = Field(default_factory=list)


class MetadataProbeListResponse(BaseModel):
    stations: list[StationMetadataResponse] = Field(default_factory=list)
    total: int
    probed_stations: int
    probed_streams: int
