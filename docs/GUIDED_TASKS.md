# Tâches guidées

État : octobre 2026 — 232 tests — migrations Flyway V1 à V8.
Chaque tâche = une branche + une PR.

## Méthode de travail

1. `git checkout main`, `git pull`, puis `git checkout -b feature/<nom>` **en premier**.
2. `git branch --show-current` avant de coder ou de commiter.
3. Coder une étape, la vérifier, compter les `@Test` puis le total Maven.
4. `git status --short` avant chaque commit : jamais `.env`, jamais de clé réelle.
5. PR, CI verte (`build-test`), lire `Tests run: N`, fusionner.
6. `git checkout main`, `git pull`, puis seulement `git branch -d <branche>`.

Toute évolution de schéma = nouvelle migration (prochaine : `V9`). Tests dans le même package que le code testé.

## Déjà fait

| Bloc | Contenu |
|---|---|
| B0 | Docker, Flyway, Actuator, Swagger |
| B1 | Inscription, connexion, JWT, rôles ; refresh token tournant ; admin au démarrage ; limitation de débit |
| B2 | Complexes, terrains, horaires, fermetures, tarifs, fuseau (photos non faites) |
| B3 | Créneaux, disponibilités, recherche, validation admin |
| B4 | Réservation, blocage 10 min, annulation, côté propriétaire |
| B5 | PayTech avec faux fournisseur : commission, IPN HMAC, paiement tardif, remboursement |
| B6-1 à B6-5 | Modèle, outbox, worker avec réessais, consentement et repli SMS, rappels 24 h et 2 h |

## En attente de fournisseurs externes

- **B5-8** : bac à sable PayTech (compte et clés de test, tunnel HTTPS). Ne jamais coller les clés.
- **B6-6** : WhatsApp (modèles Meta approuvés) et SMS. Les paramètres des payloads sont `start`, `end`,
  `price`, `currency`, `date`, `hour`, `field`, `amount`.
- **B1-5 / B1-6** : mot de passe oublié et OTP (dépendent de B6-6).

## B7 · Tableau de bord et administration

- **B7-1** Statistiques propriétaire : revenus nets, **uniquement les paiements `SUCCEEDED`**,
  taux de remplissage, dates lues dans le fuseau du complexe.
- **B7-2** Personnel STAFF rattaché à un complexe (voir et bloquer, pas les revenus).
- **B7-3** Liste des paiements, rapport des commissions, litiges.

## B8 · Qualité et production

- [ ] Test de concurrence : 20 threads, 1 succès et 19 × 409 (Testcontainers, nom `*IT`, `mvn verify`).
- [ ] Vérification CI que le nombre de tests ne baisse pas.
- [ ] Swagger et api-docs désactivés en production.
- [ ] Format d'erreur unifié (400 produits par Spring).
- [ ] Supprimer l'utilisateur généré par Spring.
- [ ] `.env.example` : `ADMIN_*`, `APP_NOTIFICATION_SENDER`, `NOTIFICATION_*`, `REMINDER_POLL_MS`,
      `RATE_LIMIT_*`, `JWT_REFRESH_DAYS`, `DB_PORT`, variables PayTech (valeurs vides).
- [ ] `docker-compose.prod.yml` : PayTech, admin, notifications, rate limit, `PAYMENT_PROVIDER=paytech`.
- [ ] `server.forward-headers-strategy: native` derrière Caddy (sinon le limiteur voit l'IP du proxy).
- [ ] Purge planifiée des refresh tokens révoqués ou expirés.
- [ ] Logs structurés, sauvegardes `pg_dump`, déploiement SSH (`DEPLOY_ENABLED`).
- [ ] Dependabot : fermer les PR qui repassent le Dockerfile en Java 26.
- [ ] Upload des photos de terrain (S3 ou MinIO).

## Frontend

- **F0** Socle : Vite, React, TypeScript, Tailwind, PWA, Router, TanStack Query, axios (JWT **et refresh
  token** : renouveler sur 401, remplacer le jeton à chaque rotation), i18n, Dockerfile Nginx,
  recréer `frontend-ci.yml` (`hashFiles` seulement au niveau des étapes).
- **F1** Authentification, routes par rôle.
- **F2** Parcours client (recherche, calendrier, paiement PayTech, annulation).
- **F3** Espace propriétaire.
- **F4** PWA. **F5** Vitest, Playwright, CI.
