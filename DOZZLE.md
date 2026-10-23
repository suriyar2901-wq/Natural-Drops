# Dozzle live logs (backend + frontend)

Dozzle is a lightweight Docker log viewer. Natural Drops ships it in `docker-compose.yml` so you can watch **live** API/container errors without SSH.

## Start

```bash
cd Natural-Drops
# ensure .env has DB + JWT vars
docker compose up -d --build
```

| Service | URL |
|---------|-----|
| Backend API | http://localhost:8080 |
| Dozzle logs | http://localhost:8082 |

## App configuration

### Backend
- Env: `DOZZLE_URL` (default `http://localhost:8082`)
- Property: `app.dozzle.url`
- API: `GET /api/admin/logs/dozzle` (JWT required)
- Settings key: `dozzleUrl` (editable by admin)

### Frontend
- Admin → **More → Live Logs (Dozzle)** — opens the viewer
- Admin → **App Settings → Dozzle logs** — save custom URL
- Optional env: `EXPO_PUBLIC_DOZZLE_URL` (overrides API/settings)

## What you see in Dozzle
- Container `natural-drops-backend` — Spring Boot / API errors
- Container `natural-drops-frontend` — only if you run the frontend as a Docker service
- Search for `ERROR`, `Exception`, `500`

## Live server tip
Set `DOZZLE_URL` to your public Dozzle URL and set `DOZZLE_USERNAME` / `DOZZLE_PASSWORD` so logs are not open to everyone.
