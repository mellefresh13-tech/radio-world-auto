# Data model

## Station

```json
{
  "id": "internal-id",
  "name": "Station Name",
  "country": "DE",
  "city": null,
  "languages": ["de"],
  "genres": ["pop"],
  "homepage": "https://example.com",
  "logo": "https://example.com/logo.png",
  "status": "active",
  "aliases": [],
  "streams": [],
  "sources": []
}
```

## Stream

```json
{
  "url": "https://stream.example.com/live",
  "format": "mp3",
  "codec": "MP3",
  "bitrate_kbps": 128,
  "is_hls": false,
  "status": "online",
  "last_checked_at": "2026-09-23T00:00:00Z",
  "source": "radio-browser"
}
```

## Statuses

Station: active, temporarily_unavailable, broken, duplicate, review_required.

Stream: unknown, candidate, online, offline, blocked, invalid.

## Multiple streams

Одна станция может иметь несколько рабочих потоков. Первый релиз приложения должен уметь выбирать primary stream и автоматически переходить к fallback при ошибке.

## Genre normalization

Исходные tags сохраняются. Для UI используется канонический набор:

Pop, Rock, Electronic, Dance, Hip-Hop, R&B, Jazz, Classical, Country, Folk, Oldies, Metal, Reggae, Latin, News, Talk, Sports, Religious, Children, Other.
