# Tâches guidées

État : octobre 2026 — 167 tests — migrations Flyway V1 à V6.
Chaque tâche = une branche + une PR. Format : objectif, étapes, critères d'acceptation, test manuel.

## Méthode de travail (à suivre pour chaque tâche)

1. `git checkout main`, `git pull`, puis `git checkout -b feature/<nom>` **en premier**.
2. `git branch --show-current` : vérifier le nom avant de coder ou de commiter.
3. Coder une étape, puis la vérifier. Compter les `@Test` du fichier modifié, puis le total Maven.
4. `git status --short` avant chaque commit : jamais `.env`, jamais de clé réelle.
5. PR, attendre la CI verte (`build-test`), lire `Tests run: N` dans le résumé, fusionner.
6. `git checkout main`, `git pull`, puis seulement `git branch -d <branche>`.

Rappels : toute évolution de schéma = nouvelle migration (`Vn+1`), jamais une ancienne modifiée.
Les fichiers de test vont dans `src/test`, dans le même package que le code testé.
Pour les tests locaux : `. .\scripts\dev-helpers.ps1` puis `Init`.

---

## Déjà fait (résumé)

| Bloc | Contenu |
|---|---|
| B0 | Docker, Flyway, Actuator, Swagger |
| B1 (partiel) | Inscription, connexion, JWT, rôles |
| B2 | Complexes, terrains, horaires, jours de fermeture, tarifs, fuseau horaire |
| B3 | Générateur de créneaux, disponibilités publiques, recherche, validation des complexes par un admin |
| B4 | Réservation avec blocage de 10 min, nettoyage planifié, annulation à 24 h, réservations et blocages côté propriétaire, statut `BOOKED` |
| B5 | Paiement PayTech (faux fournisseur), commission 3 %, notification signée HMAC, paiement tardif, remboursement intégral automatique, reprise admin |

---

## B5-8 · Validation avec le bac à sable PayTech

**Prérequis** : compte PayTech et clés de test. Ne jamais coller les clés dans un fichier commité ni dans une conversation.
**Branche** `feature/B5-8-paytech-sandbox`

**Objectif** : confirmer les hypothèses qu'on n'a pas pu vérifier sans compte.

**Étapes**
1. Mettre les clés dans `.env` (ignoré par Git) : `PAYTECH_API_KEY`, `PAYTECH_API_SECRET`, `PAYTECH_ENV=test`, retirer `PAYMENT_PROVIDER=fake`.
2. Exposer l'API en HTTPS avec un tunnel (ngrok ou cloudflared) et renseigner `PAYTECH_IPN_URL` (`https://…/api/payments/paytech/ipn`).
3. Créer une réservation, appeler `POST /api/bookings/{id}/pay`, ouvrir l'URL renvoyée, payer avec le compte de test.
4. Lire dans les logs de l'API ce que PayTech envoie vraiment.

**À constater (et corriger si besoin)**
- [ ] La notification arrive en formulaire (et non en JSON).
- [ ] `hmac_compute` est présent et notre vérification l'accepte.
- [ ] Le montant annoncé en mode test (réel, ou 100 à 150 CFA débités).
- [ ] Le comportement de `POST /payment/refund-payment` et la notification de remboursement.
- [ ] Les URL `success_url` et `cancel_url` : à ajouter à `docker-compose.yml` si nécessaire.

---

## B6 · Notifications WhatsApp et SMS

**À lancer tôt, hors code** : compte Meta Business, modèles de messages WhatsApp à faire approuver (plusieurs jours), choix du fournisseur SMS (l'API SMS de PayTech est une option, prix et fiabilité à vérifier).

### B6-1 · Interface et modèle
**Branche** `feature/B6-1-notification-model`
1. L'entité `Notification` existe déjà en base (table de la migration V1). La mapper, avec `NotificationStatus` (`PENDING`, `SENT`, `FAILED`) et `NotificationChannel` (`WHATSAPP`, `SMS`).
2. Migration `V7` : colonne `dedupe_key` unique (évite les doublons, indispensable pour les rappels) et colonne d'erreur.
3. Interface `MessageSender` (canal, destinataire, modèle, paramètres) et un faux envoi de test, actif seulement si une propriété le demande.
- Critères : l'application démarre, la migration est appliquée, tests unitaires du mapping.

### B6-2 · Boîte d'envoi (outbox)
**Branche** `feature/B6-2-outbox`
1. Un service `NotificationService.enqueue(...)` qui écrit dans la table, **dans la même transaction** que l'événement métier (réservation confirmée, annulée, remboursée).
2. Brancher : confirmation de réservation (après paiement), annulation, remboursement effectué.
- Critères : si la transaction échoue, aucune notification n'est créée ; un événement répété ne crée pas de doublon (`dedupe_key`).

### B6-3 · Worker d'envoi
**Branche** `feature/B6-3-worker`
1. Tâche planifiée qui envoie les `PENDING` dont `send_at` est passé, avec verrou de ligne (comme pour les remboursements).
2. Réessais espacés, nombre maximal de tentatives, passage à `FAILED` avec message d'erreur visible dans les logs.
- Critères : un envoi qui échoue est retenté ; après abandon, il reste visible ; deux workers ne l'envoient pas deux fois.

### B6-4 · Consentement et destinataires
**Branche** `feature/B6-4-recipients`
1. WhatsApp seulement si `whatsapp_opt_in` est vrai et le téléphone renseigné ; sinon SMS.
2. Repli sur SMS si l'envoi WhatsApp échoue.
3. Jamais de numéro de téléphone ni de message complet dans les logs.

### B6-5 · Rappels
**Branche** `feature/B6-5-reminders`
1. Tâche planifiée : réservations `CONFIRMED` dont le début est dans environ 24 h, puis dans environ 2 h.
2. Idempotence par `dedupe_key` (`booking-<id>-reminder-24h`).
3. Heure du rappel exprimée dans le fuseau du complexe.

### B6-6 · Vrais fournisseurs
**Branche** `feature/B6-6-providers`
1. Implémentation WhatsApp (Cloud API Meta ou BSP) avec les modèles approuvés.
2. Implémentation SMS du fournisseur choisi.
3. Clés dans `.env` seulement ; fournisseur choisi par configuration, `fake` jamais en production.

---

## B1 · Suite de l'authentification

| Tâche | Détail | Dépend de |
|---|---|---|
| B1-4 Refresh token | jeton d'accès court + jeton de rafraîchissement révocable | rien |
| B1-5 Mot de passe oublié | jeton à usage unique, durée limitée, envoyé par SMS ou email | B6 |
| B1-6 Vérification du téléphone | code OTP à 6 chiffres, expiration, limite de tentatives | B6 |
| B1-7 Admin au démarrage | création du compte ADMIN depuis des variables d'environnement, remplace la promotion manuelle en base | rien |
| B1-8 Limitation de débit | sur login, inscription, OTP | rien |

---

## B7 · Tableau de bord et administration

### B7-1 · Statistiques du propriétaire
- Revenus nets sur une période, par terrain.
- **Compter uniquement les paiements `SUCCEEDED`** (jamais `REFUNDED`).
- Taux de remplissage = créneaux réservés / créneaux ouverts.
- Critères : un paiement remboursé disparaît du revenu ; les dates sont lues dans le fuseau du complexe.

### B7-2 · Personnel (STAFF)
- Lier un utilisateur STAFF à un complexe ; droits limités (voir et bloquer des créneaux, pas les revenus).
- Réutiliser le contrôle de propriété existant.

### B7-3 · Administration
- Liste des paiements, rapport des commissions, gestion des litiges.

---

## B8 · Qualité et production

- [ ] **Test de concurrence** : 20 threads réservent le même créneau, exactement une réussite (Testcontainers, vraie base PostgreSQL ; Docker est disponible dans la CI GitHub).
- [ ] Vérification dans la CI que le nombre de tests ne baisse pas.
- [ ] Désactiver Swagger et api-docs en production (`springdoc.api-docs.enabled=false`).
- [ ] Supprimer l'utilisateur généré par Spring (message au démarrage).
- [ ] Unifier le format d'erreur (les 400 produits par Spring n'utilisent pas notre format).
- [ ] `docker-compose.prod.yml` : variables PayTech, `PAYMENT_PROVIDER=paytech`, URL de notification en HTTPS, `PAYTECH_ENV=prod` seulement après activation du compte.
- [ ] `.env.example` : documenter les variables de paiement avec des valeurs vides.
- [ ] Logs structurés, sauvegardes de la base (`pg_dump` planifié), HTTPS (Caddy prêt).
- [ ] Activer le déploiement SSH (`DEPLOY_ENABLED`, voir `docs/CICD.md`), avec approbation manuelle sur l'environnement `production`.
- [ ] Upload des photos de terrain (S3 ou MinIO), laissé de côté jusqu'ici.

---

## Frontend

### F0 · Socle
**Branche** `feature/F0-frontend-setup`
1. `npm create vite@latest frontend -- --template react-ts`
2. Installer : `react-router-dom @tanstack/react-query axios react-hook-form zod @hookform/resolvers tailwindcss vite-plugin-pwa react-i18next`
3. `vite-plugin-pwa` : manifeste, icônes 192 et 512, mise à jour automatique.
4. Client axios avec le jeton, redirection sur 401, gestion du format d'erreur de l'API.
5. `frontend/Dockerfile` (build Node puis Nginx), service `web` dans `docker-compose.yml`.
6. Recréer `.github/workflows/frontend-ci.yml`. Attention : `hashFiles` ne fonctionne qu'au niveau des étapes, pas du job.
- Critères : `npm run dev` fonctionne, la PWA est installable, un appel à `/actuator/health` passe (CORS).

### F1 · Authentification
Pages d'inscription et de connexion, routes protégées par rôle, stockage du jeton.

### F2 · Parcours client
Recherche et filtres, fiche terrain, calendrier de créneaux (statuts `AVAILABLE`, `BOOKED`, `PAST`, `NO_PRICE`), réservation, redirection vers la page de paiement PayTech, retour (`success_url` / `cancel_url`), mes réservations, annulation.

### F3 · Espace propriétaire
Complexes et terrains, horaires, tarifs, calendrier des réservations, blocage de créneaux, tableau de bord.

### F4 · PWA
Installation, consultation hors ligne, performances (Lighthouse).

### F5 · Tests et CI
Vitest, Playwright, intégration à la CI.
