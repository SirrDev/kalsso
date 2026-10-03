# Contribuer

1. Prendre une tache dans les issues (ou `docs/GUIDED_TASKS.md`).
2. Creer une branche : `git checkout -b feature/B2-venue-crud`
3. Developper en petits commits (Conventional Commits, voir `docs/REPOSITORY_STRUCTURE.md`).
4. Lancer les tests : `cd backend && mvn verify`
5. Ouvrir une PR vers `main` en remplissant le modele ; la CI doit etre verte.
6. Cocher la tache dans `DEVELOPMENT_STEPS.txt`.

## Regles d'or
- Ne jamais modifier une migration Flyway deja appliquee : creer `V{n+1}__description.sql`.
- Ne jamais commiter `.env` ni de secrets.
- Chaque endpoint proprietaire verifie que la ressource appartient bien a l'utilisateur connecte.
- Chaque nouvelle regle metier a un test.
