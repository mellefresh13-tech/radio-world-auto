from __future__ import annotations

import random
import socket

import dns.resolver

from ..http import get_json

DISCOVERY_HOST = "all.api.radio-browser.info"
SRV_RECORD = "_api._tcp.radio-browser.info"
# Bootstrap names documented or observed in the Radio Browser network. The
# live DNS/SRV and /json/servers discovery below add any current mirrors.
DATABASE_DUMP_URL = "https://db.radio-browser.info/all.json"
# The official dump is the last-resort source when every API mirror is down.
# Reject small/truncated-looking payloads so they cannot become a published catalog.
MIN_DATABASE_DUMP_STATIONS = 10_000

FALLBACK_SERVERS = (
    "de1.api.radio-browser.info",
    "de2.api.radio-browser.info",
    "fi1.api.radio-browser.info",
    "nl1.api.radio-browser.info",
    "at1.api.radio-browser.info",
)


def _is_api_server(hostname: str) -> bool:
    hostname = hostname.rstrip(".").lower()
    return hostname.endswith(".api.radio-browser.info")


def _srv_server_names() -> list[str]:
    """Use the official SRV discovery alternative documented by Radio Browser."""
    try:
        answers = dns.resolver.resolve(SRV_RECORD, "SRV", lifetime=3.0)
    except Exception as exc:
        # DNS/SRV can be unavailable in some CI environments; other discovery
        # mechanisms and the bootstrap list must still be attempted.
        print(f"Radio Browser DNS diagnostic: SRV lookup failed for {SRV_RECORD}: {type(exc).__name__}: {exc}")
        return []

    names: list[str] = []
    for answer in answers:
        hostname = str(answer.target).rstrip(".").lower()
        if _is_api_server(hostname) and hostname not in names:
            names.append(hostname)
    print(f"Radio Browser DNS diagnostic: SRV {SRV_RECORD} returned {names or 'no API server names'}")
    return names


def discover_servers() -> list[str]:
    """Discover mirrors using DNS A/AAAA + reverse DNS and the SRV record."""
    servers: list[str] = _srv_server_names()
    seen_ips: set[str] = set()

    try:
        addresses = socket.getaddrinfo(
            DISCOVERY_HOST,
            None,
            type=socket.SOCK_STREAM,
        )
    except OSError as exc:
        print(f"Radio Browser DNS diagnostic: A/AAAA lookup for {DISCOVERY_HOST} failed: {type(exc).__name__}: {exc}")
        addresses = []

    discovered_ips = sorted({address[4][0].split("%", 1)[0] for address in addresses})
    print(f"Radio Browser DNS diagnostic: A/AAAA {DISCOVERY_HOST} returned {discovered_ips or 'no addresses'}")

    for address in addresses:
        ip = address[4][0].split("%", 1)[0]
        if ip in seen_ips:
            continue
        seen_ips.add(ip)

        try:
            hostname = socket.gethostbyaddr(ip)[0].rstrip(".").lower()
        except OSError as exc:
            print(f"Radio Browser DNS diagnostic: reverse lookup for {ip} failed: {type(exc).__name__}: {exc}")
            continue

        print(f"Radio Browser DNS diagnostic: reverse lookup {ip} -> {hostname}")
        if _is_api_server(hostname) and hostname not in servers:
            servers.append(hostname)

    # Preserve a fallback path when DNS and reverse DNS are blocked in CI.
    for hostname in FALLBACK_SERVERS:
        if hostname not in servers:
            servers.append(hostname)

    random.shuffle(servers)
    print(f"Radio Browser DNS diagnostic: final discovered/bootstrap server list: {servers}")
    return [f"https://{hostname}" for hostname in servers]


def discover_from_server_list(seed_servers: list[str]) -> list[str]:
    """Expand seeds with the official /json/servers list from a reachable mirror."""
    servers = list(seed_servers)
    seen = set(servers)

    # One valid response contains the current mirror list. If a seed is down,
    # keep going until another mirror responds.
    for base_url in seed_servers:
        try:
            payload = get_json(f"{base_url}/json/servers", timeout=8.0, retries=1)
        except RuntimeError as exc:
            print(f"Radio Browser DNS diagnostic: {base_url}/json/servers failed: {exc}")
            continue

        if not isinstance(payload, list):
            continue

        discovered: list[str] = []
        for item in payload:
            if not isinstance(item, dict):
                continue
            hostname = item.get("name")
            if not isinstance(hostname, str) or not _is_api_server(hostname):
                continue
            url = f"https://{hostname.rstrip('.').lower()}"
            if url not in seen:
                seen.add(url)
                discovered.append(url)

        servers.extend(discovered)
        if discovered:
            break

    random.shuffle(servers)
    return servers


def fetch_all_stations(limit: int = 20_000) -> list[dict]:
    errors: list[str] = []
    servers = discover_from_server_list(discover_servers())

    for base_url in servers:
        try:
            payload = get_json(
                f"{base_url}/json/stations",
                params={"limit": limit, "hidebroken": "true"},
                timeout=30.0,
                retries=1,
            )
            if not isinstance(payload, list):
                raise ValueError("Radio Browser did not return a station list")
            if not payload:
                raise ValueError("Radio Browser returned an empty station list")
            if not all(isinstance(item, dict) for item in payload):
                raise ValueError("Radio Browser returned non-object station records")

            named_payload = [
                item
                for item in payload
                if isinstance(item.get("name"), str) and item["name"].strip()
            ]
            skipped = len(payload) - len(named_payload)
            if skipped:
                print(
                    f"Radio Browser {base_url}: skipped {skipped} station records "
                    "without a non-empty name"
                )
            if not named_payload:
                raise ValueError("Radio Browser returned no stations with a non-empty name")
            return named_payload
        except (RuntimeError, ValueError) as exc:
            errors.append(f"{base_url}: {exc}")

    # Radio Browser also publishes a full JSON database dump. It is independent
    # of the API mirror fleet, so it can keep catalog updates working during an
    # API outage. Unlike /json/stations, the dump has no health/resolved fields;
    # normalize_radio_browser() already falls back from url_resolved to url, and
    # the normal stream verification pipeline still decides what gets published.
    try:
        payload = get_json(DATABASE_DUMP_URL, timeout=90.0, retries=2)
        if not isinstance(payload, list):
            raise ValueError("database dump is not a station list")
        if len(payload) < MIN_DATABASE_DUMP_STATIONS:
            raise ValueError(
                f"database dump contains only {len(payload)} stations; "
                f"expected at least {MIN_DATABASE_DUMP_STATIONS}"
            )
        if not all(isinstance(item, dict) for item in payload):
            raise ValueError("database dump contains non-object station records")

        named_payload = [
            item
            for item in payload
            if isinstance(item.get("name"), str) and item["name"].strip()
        ]
        skipped = len(payload) - len(named_payload)
        if skipped:
            print(f"Radio Browser database dump: skipped {skipped} station records without a non-empty name")
        if len(named_payload) < MIN_DATABASE_DUMP_STATIONS:
            raise ValueError(
                f"database dump contains only {len(named_payload)} usable named stations; "
                f"expected at least {MIN_DATABASE_DUMP_STATIONS}"
            )
        print(
            "Radio Browser API mirrors unavailable; using database dump "
            f"({len(named_payload)} usable records, skipped {skipped})"
        )
        return named_payload
    except (RuntimeError, ValueError) as exc:
        errors.append(f"{DATABASE_DUMP_URL}: {exc}")

    details = "; ".join(errors) if errors else "DNS discovery returned no servers"
    raise RuntimeError(f"All Radio Browser sources failed: {details}")
