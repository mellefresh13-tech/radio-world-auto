from __future__ import annotations

CANONICAL_GENRES = [
    "Pop",
    "Rock",
    "Electronic",
    "Dance",
    "Hip-Hop",
    "R&B",
    "Jazz",
    "Classical",
    "Country",
    "Folk",
    "Oldies",
    "Metal",
    "Reggae",
    "Latin",
    "News",
    "Talk",
    "Sports",
    "Religious",
    "Children",
    "Other",
]

GENRE_ALIASES = {
    "pop music": "Pop",
    "top 40": "Pop",
    "mainstream": "Pop",
    "chr": "Pop",
    "rock music": "Rock",
    "hard rock": "Rock",
    "classic rock": "Rock",
    "electronica": "Electronic",
    "edm": "Electronic",
    "house": "Electronic",
    "techno": "Electronic",
    "trance": "Electronic",
    "dance music": "Dance",
    "hip hop": "Hip-Hop",
    "hiphop": "Hip-Hop",
    "rnb": "R&B",
    "r&b": "R&B",
    "classics": "Classical",
    "news/talk": "News",
    "news & talk": "News",
    "talk radio": "Talk",
    "sport": "Sports",
    "religion": "Religious",
    "religious music": "Religious",
    "kids": "Children",
    "children's": "Children",
}


def normalize_genres(tags: list[str]) -> list[str]:
    result: list[str] = []

    for tag in tags:
        clean = " ".join(tag.strip().casefold().split())
        if not clean:
            continue

        canonical = GENRE_ALIASES.get(clean)
        if canonical is None:
            canonical = next(
                (genre for genre in CANONICAL_GENRES if genre.casefold() == clean),
                None,
            )

        if canonical and canonical not in result:
            result.append(canonical)

    return result or ["Other"]
