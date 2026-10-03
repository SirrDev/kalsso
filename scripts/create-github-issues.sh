#!/usr/bin/env bash
# Cree les labels et les issues principales sur GitHub.
# Prerequis : GitHub CLI installe (https://cli.github.com) et `gh auth login` fait.
# Usage : ./scripts/create-github-issues.sh
set -euo pipefail

echo "Creation des labels..."
for l in backend frontend infra mvp bug docs blocked \
         B2 B3 B4 B5 B6 B7 B8 F0 F1 F2 F3; do
  gh label create "$l" --force >/dev/null
done

create() { # titre | labels | corps
  gh issue create --title "$1" --label "$2" --body "$3"
}

REF="Voir les etapes detaillees dans docs/GUIDED_TASKS.md"

create "B2-1 Entites Venue et Field"           "backend,B2,mvp" "$REF (B2-1)"
create "B2-2 CRUD Venue (proprietaire)"        "backend,B2,mvp" "$REF (B2-2)"
create "B2-3 CRUD Field"                       "backend,B2,mvp" "$REF (B2-3)"
create "B2-4 Horaires et jours de fermeture"   "backend,B2,mvp" "$REF (B2-4)"
create "B2-5 Regles de tarification"           "backend,B2,mvp" "$REF (B2-5)"
create "B3-1 Calcul des creneaux disponibles"  "backend,B3,mvp" "$REF (B3-1)"
create "B3-2 Recherche de terrains"            "backend,B3,mvp" "$REF (B3-2)"
create "B4-1 Creation de reservation avec hold" "backend,B4,mvp" "$REF (B4-1)"
create "B4-2 Liberation des holds expires"     "backend,B4,mvp" "$REF (B4-2)"
create "B4-3 Annulation, blocage, consultation" "backend,B4,mvp" "$REF (B4-3)"
create "B4-4 Test de concurrence"              "backend,B4,mvp" "$REF (B4-4)"
create "B5 Paiement"                           "backend,B5,mvp" "$REF (B5)"
create "B6 Notifications WhatsApp et SMS"      "backend,B6,mvp" "$REF (B6)"
create "B7 Dashboard et administration"        "backend,B7"     "$REF (B7)"
create "B8 Mise en production"                 "backend,infra,B8" "$REF (B8)"
create "F0 Socle frontend React PWA"           "frontend,F0,mvp" "$REF (F0)"
create "F1 Authentification frontend"          "frontend,F1,mvp" "$REF (F1)"
create "F2 Parcours client"                    "frontend,F2,mvp" "$REF (F2)"
create "F3 Espace proprietaire"                "frontend,F3,mvp" "$REF (F3)"

echo "Termine."
