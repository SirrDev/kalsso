# Footbooking

## Demarrage (Docker)

```bash
cp .env.example .env      # puis modifier DB_PASSWORD et JWT_SECRET
docker compose up --build
```

- API : http://localhost:8080
- Swagger : http://localhost:8080/swagger-ui.html
- Sante : http://localhost:8080/actuator/health

## Tester l'authentification

```bash
curl -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"email":"owner@test.com","password":"motdepasse123","fullName":"Moussa Diop","phone":"+221770000000","role":"OWNER","whatsappOptIn":true}'

curl -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"owner@test.com","password":"motdepasse123"}'

curl localhost:8080/api/me -H "Authorization: Bearer <TOKEN>"
```

Voir `DEVELOPMENT_STEPS.txt` pour la feuille de route.

## Documentation

- `docs/REPOSITORY_STRUCTURE.md` : structure du depot, branches, conventions
- `docs/GUIDED_TASKS.md` : taches guidees pas a pas
- `scripts/create-github-issues.sh` : cree labels et issues sur GitHub
- `CONTRIBUTING.md` : workflow de contribution

## Publier sur GitHub

```bash
git init -b main
git add . && git commit -m "chore: initial project setup"
gh repo create footbooking --private --source=. --push
./scripts/create-github-issues.sh
```

## Tester B2 (complexes et terrains)

```bash
TOKEN=<token OWNER obtenu au login>

curl -X POST localhost:8080/api/owner/venues -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Stade Pikine","address":"Rue 10","city":"Dakar","phone":"+221770000000"}'

curl -X POST localhost:8080/api/owner/venues/<VENUE_ID>/fields -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Terrain 1","format":"FIVE","surface":"SYNTHETIC","slotMinutes":60}'

curl localhost:8080/api/owner/venues -H "Authorization: Bearer $TOKEN"
```
