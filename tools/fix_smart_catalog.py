from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "android/app/src/main/java/com/mellefresh13/radio"


def replace_once(path: Path, old: str, new: str):
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected 1 match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


cache = SRC / "CatalogCacheStore.kt"
replace_once(cache,
'''data class Snapshot(\n        val savedAt: Long,\n        val stations: List<Station>,\n        val countries: List<CountryItem>,\n        val genres: List<GenreItem>\n    )''',
'''data class Snapshot(\n        val savedAt: Long,\n        val catalogVersion: String?,\n        val stations: List<Station>,\n        val countries: List<CountryItem>,\n        val genres: List<GenreItem>\n    )''')
replace_once(cache, 'val savedAt = root.optLong("saved_at", 0L)', 'val savedAt = root.optLong("saved_at", 0L)\n                val catalogVersion = root.optString("catalog_version").takeIf { it.isNotBlank() }')
replace_once(cache, 'Snapshot(savedAt, stations, countries, genres)', 'Snapshot(savedAt, catalogVersion, stations, countries, genres)')
replace_once(cache,
'''fun save(\n        stations: Collection<Station>,\n        countries: Collection<CountryItem>,\n        genres: Collection<GenreItem>\n    )''',
'''fun save(\n        stations: Collection<Station>,\n        countries: Collection<CountryItem>,\n        genres: Collection<GenreItem>,\n        catalogVersion: String? = null\n    )''')
replace_once(cache, '.put("saved_at", System.currentTimeMillis())\n                .put("stations",', '.put("saved_at", System.currentTimeMillis())\n                .putOpt("catalog_version", catalogVersion)\n                .put("stations",')

repo = SRC / "CatalogRepository.kt"
replace_once(repo,
'''interface CatalogRepository {\n    fun loadStations(''',
'''data class CatalogSnapshot(\n    val stations: List<Station>,\n    val countries: List<CountryItem>,\n    val genres: List<GenreItem>,\n    val version: String\n)\n\ninterface CatalogRepository {\n    fun loadCatalog(\n        currentStations: List<Station>,\n        currentVersion: String?,\n        callback: (Result<CatalogSnapshot>) -> Unit\n    )\n\n    fun loadStations(''')

api_repo = SRC / "ApiCatalogRepository.kt"
replace_once(api_repo,
'''class ApiCatalogRepository(\n    private val client: RadioApiClient = RadioApiClient()\n) : CatalogRepository {\n''',
'''class ApiCatalogRepository(\n    private val client: RadioApiClient = RadioApiClient()\n) : CatalogRepository {\n\n    override fun loadCatalog(\n        currentStations: List<Station>,\n        currentVersion: String?,\n        callback: (Result<CatalogSnapshot>) -> Unit\n    ) {\n        client.loadCatalog(currentStations, currentVersion) { result ->\n            callback(result.map { snapshot ->\n                CatalogSnapshot(\n                    stations = snapshot.stations.map(::mapStation),\n                    countries = snapshot.countries.map { CountryItem(countryName(it.code), it.code, flagFor(it.code), it.stationCount) },\n                    genres = snapshot.genres.map { GenreItem(it.name, it.stationCount) },\n                    version = snapshot.version\n                )\n            })\n        }\n    }\n''')

main = SRC / "MainActivity.kt"
replace_once(main,
'''    private fun renderPlayer() {\n        playerLogoView = null;''',
'''    private fun renderPlayer() {\n        controller?.currentMediaItem?.mediaId?.let { mediaId ->\n            catalog.firstOrNull { it.id == mediaId }?.let { station ->\n                currentStation = station\n                restoredStationId = station.id\n            }\n        }\n        playerLogoView = null;''')
replace_once(main, 'private var currentStation: Station? = null', 'private var currentStation: Station? = null\n    private var catalogVersion: String? = null')
replace_once(main, 'catalog = it.stations.toMutableList();\n                        applyPersistedState()', 'catalog = it.stations.toMutableList();\n                        catalogVersion = it.catalogVersion\n                        applyPersistedState()')
replace_once(main,
'''    private fun loadRemoteCatalog() {\n        catalogRepository.loadStations(limit = 50_000) { result -> result.onSuccess { stations ->\n            if (stations.isNotEmpty()) {\n                catalog = stations.toMutableList(); applyPersistedState(); restoreStationFromState(); syncPlayerPlaylist(); saveCatalogCacheAsync(); renderPlayer()\n            }\n        } }\n        catalogRepository.loadCountries { result -> result.onSuccess { countries -> remoteCountries = countries; saveCatalogCacheAsync() } }\n        catalogRepository.loadGenres { result -> result.onSuccess { genres -> remoteGenres = genres; saveCatalogCacheAsync() } }\n    }''',
'''    private fun loadRemoteCatalog() {\n        catalogRepository.loadCatalog(catalog, catalogVersion) { result -> result.onSuccess { snapshot ->\n            catalog = snapshot.stations.toMutableList()\n            catalogVersion = snapshot.version\n            remoteCountries = snapshot.countries\n            remoteGenres = snapshot.genres\n            applyPersistedState()\n            restoreStationFromState()\n            syncPlayerPlaylist()\n            saveCatalogCacheAsync()\n            renderPlayer()\n        } }\n    }''')
replace_once(main, 'cacheExecutor.execute { catalogCacheStore.save(stations, countries, genres) }', 'cacheExecutor.execute { catalogCacheStore.save(stations, countries, genres, catalogVersion) }')

gradle = ROOT / "android/app/build.gradle.kts"
replace_once(gradle,
'''tasks.named("preBuild") {\n    dependsOn("applyUiStabilityFix")\n}''',
'''tasks.register<org.gradle.api.tasks.Exec>("applySmartCatalogFix") {\n    workingDir(rootProject.projectDir.parentFile)\n    commandLine("python3", "tools/fix_smart_catalog.py")\n    dependsOn("applyUiStabilityFix")\n}\n\ntasks.named("preBuild") {\n    dependsOn("applySmartCatalogFix")\n}''')

subprocess.run(["python3", str(ROOT / "tools/fix_smart_catalog_client.py")], check=True)
print("smart catalog + previous-station UI patch applied")
