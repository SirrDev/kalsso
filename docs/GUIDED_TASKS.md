# Taches guidees

Chaque tache = une branche + une PR. Suivre l'ordre : les taches dependent des precedentes.
Format : **objectif, etapes, criteres d'acceptation, test manuel**.

Etat : B0 et B1 sont termines (voir `DEVELOPMENT_STEPS.txt`).

---

## B2-1 · Entites Venue et Field
**Branche** `feature/B2-1-entities` · **Labels** `backend` `B2` `mvp`

**Objectif** Mapper les tables `venue` et `field` (deja creees par la migration V1).

**Etapes**
1. Creer les enums `VenueStatus` (PENDING, ACTIVE, SUSPENDED), `FieldFormat` (FIVE, SEVEN, ELEVEN), `FieldSurface` (SYNTHETIC, NATURAL, INDOOR) dans les packages `venue/` et `field/`.
2. Creer l'entite `Venue` (champs = colonnes de la table, `ownerId` en `UUID`, pas de relation JPA vers `User` pour rester decouple).
3. Creer l'entite `Field` avec `venueId`.
4. Creer `VenueRepository` (`findByOwnerId`) et `FieldRepository` (`findByVenueId`).
5. Verifier le demarrage : `ddl-auto: validate` doit passer sans erreur.

**Criteres d'acceptation**
- [ ] L'application demarre, Hibernate valide le schema
- [ ] Les enums sont stockes en `STRING`

---

## B2-2 · CRUD Venue (proprietaire)
**Branche** `feature/B2-2-venue-crud`

**Objectif** Un `OWNER` cree et gere ses complexes.

**Etapes**
1. `VenueDtos` : `CreateVenueRequest` (name, address, city, description, phone, latitude, longitude avec validations) et `VenueResponse`.
2. `VenueService` :
   - `create(ownerId, req)` : statut initial `PENDING`
   - `listMine(ownerId)`
   - `getOwned(ownerId, venueId)` : **leve 404** si le complexe n'appartient pas a l'utilisateur (ne pas reveler son existence)
   - `update`, `delete` (refuser si des reservations futures existent)
3. `VenueController` sous `/api/owner/venues`, protege par `@PreAuthorize("hasRole('OWNER')")`.
4. Recuperer l'id utilisateur depuis `Authentication.getName()` (c'est le `subject` du JWT).
5. Ajouter `/api/owner/**` dans `SecurityConfig` (hasAnyRole OWNER, STAFF au besoin).

**Criteres d'acceptation**
- [ ] OWNER A ne peut ni lire ni modifier le complexe de OWNER B (404)
- [ ] CLIENT recoit 403 sur `/api/owner/venues`
- [ ] Validation : nom vide -> 400 avec details par champ

**Test manuel** : inscrire 2 owners, creer un complexe avec le token 1, tenter de le lire avec le token 2.

---

## B2-3 · CRUD Field
**Branche** `feature/B2-3-field-crud`

**Etapes**
1. `FieldDtos` (name, format, surface, slotMinutes 30/60/90/120).
2. `FieldService` : verifier a chaque appel que `venue.ownerId == currentUser` (reutiliser `VenueService.getOwned`).
3. `FieldController` : `/api/owner/venues/{venueId}/fields` (POST, GET) et `/api/owner/fields/{fieldId}` (GET, PUT, DELETE).
4. Suppression douce : mettre `active = false` plutot que supprimer si des reservations existent.

**Criteres d'acceptation**
- [ ] Impossible de creer un terrain dans le complexe d'un autre
- [ ] `slotMinutes <= 0` refuse

---

## B2-4 · Horaires d'ouverture et jours de fermeture
**Branche** `feature/B2-4-opening-hours`

**Etapes**
1. Entites `OpeningHours`, `ClosedDay` + repositories.
2. Endpoint `PUT /api/owner/fields/{id}/opening-hours` : remplace la semaine complete (liste de `{dayOfWeek, openTime, closeTime}`).
3. Validation metier : `closeTime > openTime`, pas de chevauchement dans un meme jour.
4. Endpoints `POST/DELETE /api/owner/fields/{id}/closed-days`.

**Criteres d'acceptation**
- [ ] Deux plages qui se chevauchent le meme jour -> 400
- [ ] Un jour sans plage = ferme ce jour-la

---

## B2-5 · Regles de tarification
**Branche** `feature/B2-5-pricing`

**Etapes**
1. Entite `PricingRule` (jour optionnel, plage horaire, prix, devise).
2. Endpoints de gestion sous `/api/owner/fields/{id}/pricing`.
3. Methode `PricingService.priceFor(fieldId, LocalDateTime start, int minutes)` : choisit la regle la plus specifique (jour precis > tous les jours).
4. Tests unitaires : semaine/week-end, creneau a cheval sur deux tranches (decider : prix du debut du creneau, le documenter).

**Criteres d'acceptation**
- [ ] Prix 5000 XOF en semaine 8h-17h, 8000 XOF le soir : le calcul renvoie le bon prix
- [ ] Aucun prix trouve -> erreur claire (le terrain n'est pas reservable)

---

## B3-1 · Calcul des creneaux disponibles
**Branche** `feature/B3-1-availability`

**Objectif** Coeur du produit : lister les creneaux libres d'un terrain pour une date.

**Etapes**
1. Creer `AvailabilityService.slots(fieldId, LocalDate date)`.
2. Algorithme :
   - a. Si la date est un `ClosedDay` -> liste vide
   - b. Recuperer les `OpeningHours` du jour de la semaine
   - c. Decouper chaque plage en creneaux de `slotMinutes`
   - d. Convertir en `Instant` avec le fuseau du complexe (ajouter `timezone` au venue, defaut `Africa/Dakar`, via migration V2)
   - e. Charger les `booking` actifs (PENDING non expires, CONFIRMED) du jour
   - f. Marquer chaque creneau `AVAILABLE`, `BOOKED` ou `PAST`, avec son prix
3. Endpoint public `GET /api/fields/{id}/availability?date=2026-10-10` (ajouter a `permitAll`).
4. Tests unitaires sur l'algorithme (sans base) : horaires 18h-23h, creneau 60 min, une reservation a 20h.

**Criteres d'acceptation**
- [ ] 5 creneaux generes, celui de 20h est `BOOKED`
- [ ] Jour de fermeture -> liste vide
- [ ] Creneaux passes marques `PAST`

---

## B3-2 · Recherche de terrains
**Branche** `feature/B3-2-search`

**Etapes**
1. Endpoint public `GET /api/fields/search?city=&format=&surface=&date=&maxPrice=`.
2. Pagination (`Pageable`) et tri.
3. Ne renvoyer que les terrains `active` dont le complexe est `ACTIVE`.
4. Index SQL sur `venue(city)` (existe deja) ; ajouter ce qui manque via V2.

---

## B4-1 · Creation de reservation avec hold
**Branche** `feature/B4-1-booking-create`

**Etapes**
1. Entite `Booking`, `BookingStatus`, `BookingSource`.
2. `BookingService.create(userId, fieldId, start)` dans une `@Transactional` :
   - verifier que le creneau est dans les creneaux generes par `AvailabilityService`
   - calculer le prix via `PricingService`
   - inserer en `PENDING` avec `expires_at = now + 10 min`
3. Capturer `DataIntegrityViolationException` et renvoyer **409** « Ce creneau vient d'etre pris » (la contrainte `booking_no_overlap` fait foi).
4. Endpoint `POST /api/bookings` (role CLIENT).

**Criteres d'acceptation**
- [ ] Deux requetes simultanees sur le meme creneau : une 201, une 409
- [ ] Creneau hors horaires -> 400

---

## B4-2 · Liberation des holds expires
**Branche** `feature/B4-2-hold-expiry`

**Etapes**
1. `@EnableScheduling` sur la classe principale.
2. Job `@Scheduled(fixedDelay = 60_000)` : passe en `CANCELLED` les `PENDING` dont `expires_at < now`.
3. Requete JPQL `@Modifying` dans `BookingRepository`.
4. Test d'integration avec horloge injectee (`Clock`) plutot que `Instant.now()`.

---

## B4-3 · Annulation, blocage manuel, consultation
**Branche** `feature/B4-3-booking-manage`

**Etapes**
1. `POST /api/bookings/{id}/cancel` (client : seulement ses reservations, delai minimum configurable).
2. `GET /api/bookings/mine`, `GET /api/owner/bookings?from=&to=&fieldId=`.
3. `POST /api/owner/fields/{id}/blocks` : cree un `Booking` source `BLOCK` (maintenance, reservation telephonique).
4. Calcul du remboursement selon la politique (placeholder jusqu'a B5).

---

## B4-4 · Test de concurrence
**Branche** `feature/B4-4-concurrency-test`

**Etapes**
1. Ajouter Testcontainers (`org.testcontainers:postgresql`, `junit-jupiter`) au `pom.xml` (scope test).
2. Test : 20 threads reservent le meme creneau (`ExecutorService` + `CountDownLatch`).
3. Assertion : exactement 1 succes, 19 conflits, 1 seule ligne en base.

---

## B5 · Paiement (resume)
1. **B5-1** Interface `PaymentProvider` + implementation du provider choisi (Wave / Orange Money / CinetPay).
2. **B5-2** `POST /api/bookings/{id}/pay` : cree un `payment` `INITIATED`, renvoie l'URL de paiement.
3. **B5-3** Webhook `POST /api/webhooks/{provider}` : verifier la signature, idempotence via `UNIQUE(provider, provider_ref)`, passer `booking` en `CONFIRMED`.
4. **B5-4** Commission plateforme et remboursements.

## B6 · Notifications (resume)
1. **B6-1** Interface `NotificationChannel`, ecriture dans la table `notification` (outbox) a chaque evenement.
2. **B6-2** Worker `@Scheduled` qui envoie les `PENDING`, avec reessais et backoff.
3. **B6-3** Canal WhatsApp (templates Meta : confirmation, rappel, annulation) + canal SMS + fallback.
4. **B6-4** Rappels J-1 et H-2, respect de `whatsapp_opt_in`.

## B7 · Dashboard et admin (resume)
1. Statistiques : revenus par periode, taux de remplissage par terrain.
2. Gestion du personnel (`STAFF` lie a un complexe).
3. `/api/admin/**` : validation des complexes (`PENDING` -> `ACTIVE`), suspension.

## B8 · Production (resume)
Rate limiting, logs structures, sauvegardes, HTTPS (Traefik/Nginx), CD vers le serveur.

---

## F0 · Socle frontend
**Branche** `feature/F0-frontend-setup`

**Etapes**
1. `npm create vite@latest frontend -- --template react-ts`
2. Installer : `react-router-dom @tanstack/react-query axios react-hook-form zod @hookform/resolvers tailwindcss vite-plugin-pwa react-i18next`
3. Configurer `vite-plugin-pwa` (manifest, icones 192/512, `registerType: 'autoUpdate'`).
4. Client axios avec intercepteur ajoutant `Authorization: Bearer`, redirection sur 401.
5. `frontend/Dockerfile` (build Node puis Nginx) et service `web` dans `docker-compose.yml`.

**Criteres d'acceptation**
- [ ] `npm run dev` affiche la page d'accueil
- [ ] Lighthouse detecte une PWA installable
- [ ] Un appel a `/actuator/health` depuis le front fonctionne (CORS ok)

## F1 · Authentification
Pages inscription / connexion, contexte d'auth, routes protegees par role (`RequireRole`), stockage du token (memoire + refresh plus tard).

## F2 · Parcours client
Recherche + filtres, fiche terrain, calendrier de creneaux, tunnel de reservation, mes reservations.

## F3 · Espace proprietaire
Gestion complexes/terrains, formulaire horaires et tarifs, calendrier FullCalendar des reservations, blocage de creneaux, dashboard.
