# Structure du repository

Strategie : **monorepo** (un seul depot GitHub pour le back, le front et l'infra).
Avantage : un seul endroit pour les issues, les PR et la CI, et des changements
back + front livres ensemble.

```
footbooking/
├── .github/
│   ├── workflows/
│   │   ├── backend-ci.yml          # build + tests Spring Boot a chaque PR
│   │   └── frontend-ci.yml         # lint + tests + build React (a activer en F0)
│   ├── ISSUE_TEMPLATE/
│   │   └── task.md                 # modele de tache guidee
│   └── PULL_REQUEST_TEMPLATE.md
│
├── backend/                        # API Spring Boot
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/footbooking/
│       │   ├── FootbookingApplication.java
│       │   ├── config/             # securite, CORS, beans globaux
│       │   ├── common/             # exceptions, utilitaires, reponses d'erreur
│       │   ├── auth/               # B1  utilisateurs, JWT
│       │   ├── venue/              # B2  complexes
│       │   ├── field/              # B2  terrains, horaires, tarifs
│       │   ├── availability/       # B3  calcul des creneaux
│       │   ├── booking/            # B4  reservations
│       │   ├── payment/            # B5  paiements, webhooks
│       │   ├── notification/       # B6  WhatsApp, SMS, outbox
│       │   └── admin/              # B7  back-office
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/       # V1__..., V2__... (Flyway, jamais modifier une migration deja appliquee)
│       └── test/java/com/footbooking/   # meme arborescence que main
│
├── frontend/                       # React + Vite + PWA (F0)
│   ├── Dockerfile
│   ├── package.json
│   ├── public/                     # icones PWA, manifest
│   └── src/
│       ├── api/                    # client HTTP + hooks TanStack Query
│       ├── components/             # composants reutilisables
│       ├── features/               # un dossier par domaine (auth, fields, booking...)
│       ├── pages/
│       ├── routes/
│       ├── i18n/
│       └── main.tsx
│
├── infra/                          # nginx, scripts de deploiement, config prod
├── docs/                           # documentation (ce dossier)
├── scripts/                        # scripts utilitaires (creation des issues GitHub)
├── docker-compose.yml              # dev : db + api (+ web plus tard)
├── .env.example
├── .gitignore
├── CONTRIBUTING.md
└── README.md
```

## Organisation du code backend : par domaine, pas par couche

Chaque package de domaine contient ses propres classes :

```
booking/
├── Booking.java              # entite JPA
├── BookingRepository.java
├── BookingService.java       # logique metier
├── BookingController.java    # endpoints REST
├── BookingDtos.java          # records requete/reponse
└── BookingStatus.java
```

Regle : un domaine ne depend d'un autre que via son **service** (jamais en utilisant
directement le repository d'un autre domaine).

## Strategie de branches

| Branche | Role |
|---|---|
| `main` | toujours deployable, protegee (PR + CI verte obligatoires) |
| `develop` | integration (optionnelle si tu es seul : tu peux travailler depuis `main`) |
| `feature/B2-venue-crud` | une branche par tache, nommee d'apres l'ID de la tache |
| `fix/...` | corrections |

## Conventions de commit (Conventional Commits)

```
feat(venue): ajoute la creation de complexe
fix(booking): corrige le calcul de fin de creneau
chore(docker): met a jour l'image postgres
test(booking): ajoute le test de concurrence
docs: complete le README
```

## Protection de `main` (Settings > Branches)

- Require a pull request before merging
- Require status checks to pass (`backend-ci`)
- Interdire le push direct

## Labels GitHub

`backend`, `frontend`, `infra`, `mvp`, `bug`, `docs`, `blocked`
et un label par etape : `B1`, `B2`, ... `F0`, `F1`, ...
(crees automatiquement par `scripts/create-github-issues.sh`)
