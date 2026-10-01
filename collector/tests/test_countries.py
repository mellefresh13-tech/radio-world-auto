from radio_catalog.countries import all_countries
from radio_catalog.normalize import resolve_country


def test_country_registry_contains_expected_scale_and_codes() -> None:
    countries = all_countries()

    assert len(countries) >= 240
    assert any(item["alpha2"] == "DE" for item in countries)
    assert any(item["alpha2"] == "BY" for item in countries)


def test_country_resolver_handles_name_and_code() -> None:
    assert resolve_country("DE") == "DE"
    assert resolve_country("Germany") == "DE"
