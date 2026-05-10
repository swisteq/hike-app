#!/usr/bin/env python3
"""Import mountain huts from OpenStreetMap (Overpass API) into geonames_features."""

import json
import subprocess
import sys
import urllib.request
import urllib.parse

OVERPASS_URL = "https://overpass-api.de/api/interpreter"

# All Polish mountains bbox (covers Tatry, Beskidy, Sudety, Bieszczady)
QUERY = """
[out:json][timeout:90];
(
  node["tourism"="alpine_hut"](49.0,14.0,51.5,24.5);
  node["tourism"="wilderness_hut"](49.0,14.0,51.5,24.5);
  way["tourism"="alpine_hut"](49.0,14.0,51.5,24.5);
  way["tourism"="wilderness_hut"](49.0,14.0,51.5,24.5);
);
out center;
"""

DOCKER_CONTAINER = "gorskie-postgres"
DB_USER = "gorskie"
DB_NAME = "gorskie_wyprawy"
# Negative IDs to avoid collision with GeoNames IDs
OSM_ID_OFFSET = -1_000_000_000


def fetch_osm_huts():
    print("Pobieranie schronisk z OpenStreetMap...")
    data = urllib.parse.urlencode({"data": QUERY}).encode()
    req = urllib.request.Request(OVERPASS_URL, data=data)
    req.add_header("User-Agent", "gorskie-wyprawy-import/1.0")
    with urllib.request.urlopen(req, timeout=120) as resp:
        result = json.loads(resp.read())
    return result["elements"]


def build_inserts(elements):
    rows = []
    seen = set()
    for el in elements:
        tags = el.get("tags", {})
        name = tags.get("name") or tags.get("name:pl") or tags.get("int_name")
        if not name:
            continue

        if el["type"] == "node":
            lat, lon = el["lat"], el["lon"]
        elif el["type"] == "way" and "center" in el:
            lat, lon = el["center"]["lat"], el["center"]["lon"]
        else:
            continue

        osm_id = el["id"]
        db_id = OSM_ID_OFFSET - osm_id  # guaranteed negative, unique

        key = (round(lat, 5), round(lon, 5))
        if key in seen:
            continue
        seen.add(key)

        name_esc = name.replace("'", "''")
        rows.append(
            f"({db_id}, '{name_esc}', '{name_esc}', {lat}, {lon}, 'S', 'HUT', 'PL')"
        )

    return rows


import os, tempfile
SQL_FILE_HOST = os.path.join(tempfile.gettempdir(), "osm_huts_import.sql")
SQL_FILE_CONTAINER = "/tmp/osm_huts_import.sql"


def run_psql_cmd(sql):
    """Run short SQL directly (no length limit risk)."""
    result = subprocess.run(
        ["docker", "exec", DOCKER_CONTAINER,
         "psql", "-U", DB_USER, "-d", DB_NAME, "-c", sql],
        capture_output=True, text=True
    )
    if result.returncode != 0:
        print("BŁĄD psql:", result.stderr, file=sys.stderr)
        sys.exit(1)
    return result.stdout.strip()


def run_psql_file(path_in_container):
    """Run SQL from a file inside the container."""
    result = subprocess.run(
        ["docker", "exec", DOCKER_CONTAINER,
         "psql", "-U", DB_USER, "-d", DB_NAME, "-f", path_in_container],
        capture_output=True, text=True
    )
    if result.returncode != 0:
        print("BŁĄD psql:", result.stderr, file=sys.stderr)
        sys.exit(1)
    return result.stdout.strip()


def main():
    elements = fetch_osm_huts()
    print(f"Pobrano {len(elements)} elementów z OSM.")

    rows = build_inserts(elements)
    print(f"Unikalnych schronisk z nazwą: {len(rows)}")

    if not rows:
        print("Brak danych do wstawienia.")
        return

    # Write all SQL to a single file, copy into container, execute
    lines = ["DELETE FROM geonames_features WHERE id < 0;"]
    lines.append(
        "INSERT INTO geonames_features "
        "(id, name, ascii_name, latitude, longitude, feature_class, feature_code, country_code) VALUES"
    )
    for i, row in enumerate(rows):
        sep = "," if i < len(rows) - 1 else ";"
        lines.append(f"  {row}{sep}")
    sql_content = "\n".join(lines) + "\n"

    with open(SQL_FILE_HOST, "w", encoding="utf-8") as f:
        f.write(sql_content)
    print(f"Zapisano SQL do {SQL_FILE_HOST} ({len(sql_content)//1024} KB)")

    subprocess.run(
        ["docker", "cp", SQL_FILE_HOST, f"{DOCKER_CONTAINER}:{SQL_FILE_CONTAINER}"],
        check=True
    )
    print("Skopiowano plik do kontenera, uruchamiam import...")

    run_psql_file(SQL_FILE_CONTAINER)

    count = run_psql_cmd("SELECT COUNT(*) FROM geonames_features WHERE id < 0;")
    print(f"\nGotowe! Schronisk OSM w bazie: {count.splitlines()[-2].strip()}")

    tatry = run_psql_cmd(
        "SELECT name, round(latitude::numeric,4), round(longitude::numeric,4) "
        "FROM geonames_features "
        "WHERE id < 0 AND latitude BETWEEN 49.1 AND 49.45 AND longitude BETWEEN 19.7 AND 20.3 "
        "ORDER BY name;"
    )
    print("\nSchroniska tatrzańskie w bazie:")
    print(tatry)


if __name__ == "__main__":
    main()
