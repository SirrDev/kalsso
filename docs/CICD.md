# CI/CD pas a pas

```
Pull request ──► backend-ci  (build + tests + build Docker)
Merge sur main ─► cd:  test ──► publish (image ghcr.io) ──► deploy (SSH, optionnel)
```

Chaque etape fonctionne seule : tu peux t'arreter a l'etape 2 tant que tu n'as pas de serveur.

---

## Etape 1 · CI (aucune configuration requise)

Fichier : `.github/workflows/backend-ci.yml`

1. Pousse le depot sur GitHub (voir README).
2. Cree une branche, ouvre une PR : le job `build-test` se lance.
3. Dans **Settings > Branches > Add rule** sur `main` :
   - Require a pull request before merging
   - Require status checks : selectionne `build-test`
4. **Settings > Actions > General > Workflow permissions** : "Read repository contents".

Verification : une PR avec un test casse doit etre bloquee.

Dependabot (`.github/dependabot.yml`) propose chaque semaine les mises a jour Maven, Docker et Actions.

---

## Etape 2 · Publication de l'image (aucun secret a creer)

Fichier : `.github/workflows/cd.yml`, jobs `test` et `publish`.

A chaque merge sur `main` touchant le backend, l'image est publiee sur
`ghcr.io/<owner>/<repo>/api` avec les tags `latest` et `sha-<commit>`.

Verification : onglet **Packages** du depot. Le package est prive par defaut ;
c'est ce qu'on veut. Le serveur s'y connecte avec le token du workflow.

Deploiement manuel de secours : `docker pull ghcr.io/<owner>/<repo>/api:latest`.

---

## Etape 3 · Deploiement automatique sur un serveur (VPS)

### 3.1 Preparer le serveur (Ubuntu, une seule fois)

```bash
# en root
curl -fsSL https://get.docker.com | sh
adduser --disabled-password deploy
usermod -aG docker deploy
mkdir -p /opt/footbooking && chown deploy:deploy /opt/footbooking
ufw allow OpenSSH && ufw allow 80 && ufw allow 443 && ufw enable
```

### 3.2 Cle SSH dediee au deploiement

Sur ta machine :
```bash
ssh-keygen -t ed25519 -f deploy_key -C "github-actions" -N ""
ssh-copy-id -i deploy_key.pub deploy@IP_DU_SERVEUR
```
La cle **privee** (`deploy_key`) ira dans GitHub ; supprime-la de ta machine ensuite.

### 3.3 Fichier d'environnement sur le serveur

```bash
cp .env.prod.example /opt/footbooking/.env   # puis le remplir
```
DNS : cree un enregistrement `A` de `api.exemple.com` vers l'IP du serveur
(Caddy obtient le certificat HTTPS tout seul).

### 3.4 Configurer GitHub

**Settings > Secrets and variables > Actions**

| Type | Nom | Valeur |
|---|---|---|
| Secret | `DEPLOY_HOST` | IP ou nom du serveur |
| Secret | `DEPLOY_USER` | `deploy` |
| Secret | `DEPLOY_SSH_KEY` | contenu de `deploy_key` (cle privee) |
| Secret | `DEPLOY_PORT` | optionnel (22 par defaut) |
| Variable | `DEPLOY_PATH` | optionnel (`/opt/footbooking`) |
| Variable | `DEPLOY_ENABLED` | `true` (**active** le job deploy) |

**Settings > Environments > New environment : `production`**
Option conseillee : "Required reviewers" pour valider chaque mise en production.

### 3.5 Premier deploiement

Onglet **Actions > cd > Run workflow**. Puis :
```bash
curl https://api.exemple.com/health     # {"status":"UP"}
```

### Retour arriere (rollback)

Sur le serveur, remettre un ancien tag :
```bash
cd /opt/footbooking
echo "API_IMAGE=ghcr.io/<owner>/<repo>/api:sha-<ancien_commit>" > .image.env
docker compose --env-file .env --env-file .image.env -f docker-compose.prod.yml up -d
```
Attention : les migrations Flyway ne sont pas annulees. Ecrire des migrations
compatibles avec la version precedente (ajouter avant de supprimer).

---

## Etape 4 · A ajouter plus tard
- Job `frontend` dans cd.yml quand F0 existe (build + image Nginx).
- Environnement `staging` deploye depuis `develop`.
- Sauvegarde quotidienne : `pg_dump` en cron vers un stockage externe.
- Notification d'echec (Slack/email) dans cd.yml.
