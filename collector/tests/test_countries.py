from radio_catalog.countries import all_countries


def test_country_registry_contains_expected_scale_and_codes() -> None:
    countries = all_countries()

    assert len(countries) >= 240
    assert any(item["alpha2"] == "DE" for item in countries)
    assert any(item["alpha2"] == "BY" for item in countries)
