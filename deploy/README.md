# API deployment

The catalog workflow builds a versioned API image after generating and validating the SQLite catalog.

Image:

`ghcr.io/mellefresh13-tech/radio-world-auto-api:latest`

The image contains:

- FastAPI application;
- the generated SQLite catalog;
- only online streams from the latest verified snapshot.

## Local run

Pull the image and expose port 8000:

```bash
docker run --rm -p 8000:8000 ghcr.io/mellefresh13-tech/radio-world-auto-api:latest
```

Health check:

```text
GET /health
```

The container listens on `0.0.0.0:8000`.

## Updating the catalog

The `Catalog snapshot` GitHub Actions workflow rebuilds the catalog on schedule and publishes a new image tag.

The SHA-specific image is:

```text
ghcr.io/mellefresh13-tech/radio-world-auto-api:<commit-sha>
```

Use the SHA tag for reproducible deployments and `latest` for a moving deployment.
