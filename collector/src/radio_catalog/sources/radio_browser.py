from __future__ import annotations

import random
import socket

from ..http import get_json

DISCOVERY_HOST = "all.api.radio-browser.info"
FALLBACK_SERVERS = (
    "de1.api.radio-browser.info",
    "nl1.api.radio-browser.info",
)


def discover_servers() -> list[str]:
    """Discover current Radio Browser mirrors using the official DNS method."""
    servers: list[str] = []
    seen_ips: set[str] = set()

    try:
        addresses = socket.getaddrinfo(
            DISCOVERY_HOST,
            None,
            type=socket.SOCK_STREAM,
        )
    except OSError:
        addresses = []

    for address in addresses:
        ip = address[4][0].split("%", 1)[0]
        if ip in seen_ips:
            continue
        seen_ips.add(ip)

        try:
            hostname = socket.gethostbyaddr(ip)[0].rstrip(".").lower()
        except OSError:
            continue

        if hostname.endswith(".api.radio-browser.info") and hostname not in servers:
            servers.append(hostname)

    # DNS discovery is authoritative; these mirrors are only a fallback if DNS
    # or reverse DNS is temporarily unavailable.
    for hostname in FALLBACK_SERVERS:
        if hostname not in servers:
            servers.append(hostname)

    random.shuffle(servers)
    return [f"https://{hostname}" for hostname in servers]


def fetch_all_stations(limit: int = 20_000) -> list[dict]:
    errors: list[str] = []

    for base_url in discover_servers():
        try:
            payload = get_json(
                f"{base_url}/json/stations",
                params={"limit": limit, "hidebroken": "true"},
                timeout=30.0,
                retries=1,
            )
            if not isinstance(payload, list):
                raise ValueError("Radio Browser did not return a station list")
            return payload
        except (RuntimeError, ValueError) as exc:
            errors.append(f"{base_url}: {exc}")

    details = "; ".join(errors) if errors else "DNS discovery returned no servers"
    raise RuntimeError(f"All Radio Browser servers failed: {details}")
