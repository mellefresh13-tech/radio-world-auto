from __future__ import annotations

import pycountry


def all_countries() -> list[dict[str, str]]:
    result = []
    for country in pycountry.countries:
        result.append(
            {
                "alpha2": country.alpha_2,
                "alpha3": country.alpha_3,
                "name": country.name,
            }
        )
    return sorted(result, key=lambda item: item["name"])
