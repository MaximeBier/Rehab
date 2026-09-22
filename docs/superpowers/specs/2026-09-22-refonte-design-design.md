# Rehab — refonte visuelle V1 (spec d'implémentation)

Date : 2026-09-22. Source visuelle : `docs/design/DESIGN.md` + `docs/design/mockups/*.dc.html` (valeurs exactes) + `docs/design/screens/*.png` (rendu, polices de substitution). Cette spec ne répète pas DESIGN.md : elle fixe le périmètre, les décisions de cadrage prises avec Maxime et le découpage technique.

## 1. Périmètre

Seuls les 13 écrans listés dans `docs/design/mockups/canvas.json` font foi : DS, Main, Accueil-Bloque, Accueil-Alertes, Reglages, Journal, Debug, Overlay-Nuit, Overlay-Quota, Overlay-Relapse, Overlay-HorsRecord, Overlay-Proto, Etats-Bouton. Les autres fichiers du dossier (`R1a*`, `R1b…R4`, `DS-A…F`, `Accueil-B/C/E/F`, `B2…B4`) sont des explorations abandonnées, à ignorer.

Tout est implémenté : thème, composants, les quatre onglets, l'overlay (avec l'animation d'appui long), l'onboarding (non dessiné, dérivé des composants), et les fonctionnalités visibles sur les maquettes mais absentes de l'app (§4).

## 2. Décisions de cadrage (Maxime, 2026-09-22)

1. **Accueil hors record : arc partiel**, comme `Accueil-Alertes` (et non l'anneau plein gris de DESIGN.md §1/§3). Anneau 180 px, trait 12, piste `panel`, arc `accent` de longueur `série / record` (borné à [0, 1], départ à 12 h, sens horaire), pas d'étiquette au-dessus du nombre (68 px), « JOURS » dessous, puis la ligne « RECORD 23 · JOKERS 2/2 ». Série à 0 : aucun arc (pas de point). L'overlay hors record garde sa maquette (`Overlay-HorsRecord` : anneau plein `ring-off`, « SÉRIE », « Sans relapse depuis 12 jours · record 23 »).
2. **Réglages verrouillés : non cliquables**, exactement la maquette (texte `muted`, cadenas, raison sous la valeur, pas de chevron, aucun éditeur). La raison inclut la cause (DESIGN §7) : « Nuit en cours — modifiable à partir de 07:30 », « Quota dépassé — modifiable dans 18 min ».
3. **Blocages journalisés (début + fin)** : nouvel événement de domaine `Event.Block(at, reason, until)`, écrit à l'apparition de l'overlay pour un blocage donné (une seule fois par blocage, même si l'utilisateur sort et revient). Le Journal en tire deux lignes : « Blocage quota » (à `at`, détail `→ 22:18`) et « Fin du blocage quota » (à `until`, seulement si `until ≤ now`, détail `—`).
4. **Déroulé** : tout d'une traite, spec + plan commités pour trace, pas de validation intermédiaire.

## 3. Décisions techniques prises sans question

- **Polices embarquées** : `Chivo[wght].ttf` et `ChivoMono[wght].ttf` (Google Fonts, licence OFL, fichiers variables) dans `app/src/main/res/font/` (`chivo.ttf`, `chivo_mono.ttf`), `FontVariation.weight(...)` par graisse. Licence copiée dans `docs/design/fonts/OFL.txt`. Pas de polices téléchargeables (dépendance aux services Google, rendu de repli au premier lancement).
- **Thème sombre unique**, pas de mode clair. `MaterialTheme(darkColorScheme(...))` alimenté par les tokens pour que les composants Material 3 standards (dialogues, sélecteur d'heure, champs) soient cohérents. Thème de manifeste sombre avec fond `#171513` (pas de flash blanc au lancement), edge-to-edge, icônes de barre d'état claires.
- **Tailles** : 1 px de maquette = 1 dp ; tailles de police en `sp`.
- **Réglages « application immédiate »** : plus de bouton « Enregistrer ». Chaque éditeur (dialogue) construit les `Settings` proposés = réglages persistés avec un seul champ changé, les valide via `vm.saveSettings` (donc `SettingsGuard`) et les persiste tout de suite ; un refus ou une saisie invalide s'affiche dans le dialogue, qui reste ouvert.
- **Overlay, titre statique** : « Quota atteint · 18 min » est calculé une fois à l'apparition de l'overlay (au plafond de la minute) et ne bouge plus tant qu'il reste affiché. Nuit : « Nuit · déblocage à 07:30 ».
- **Overlay, état « terminé »** : à la fin de l'appui, l'événement est enregistré tout de suite, mais l'overlay reste affiché **1,5 s** dans son état final (bouton plein « +5 / MIN » ou « +15 / MIN », fond rouge figé pour le relapse) avant de disparaître. Les demandes de masquage et les mises à jour d'état arrivant pendant ces 1,5 s sont différées (le moteur continue de tourner).
- **« Quitter »** : `GLOBAL_ACTION_HOME` (DESIGN §6.2).
- **Record** : on appelle « record en cours » l'état où la série actuelle dépasse strictement toutes les séries passées terminées. « Ancien record » = la plus longue série passée terminée ; « Record en cours depuis N jours » avec N = série − ancien record. Égalité avec un ancien record = hors record.
- **Captures Debug** : fichiers nommés `<ig|x>_<écran>_<yyyy-MM-dd'T'HH-mm-ss>.json` (écran = partie après le point du `screenId` détecté, `feed` pour `instagram.home`, `inconnu` sinon). Toucher une ligne partage ce fichier ; « Exporter » partage toutes les captures (`ACTION_SEND_MULTIPLE`). Délai de capture saisissable (1–60 s, 5 par défaut).
- **Journal** : 30 derniers jours (même rétention que les usages), groupé par journée Rehab (`Schedule.dayOf`), en-têtes « AUJOURD'HUI · DIM. 20 SEPT. », « HIER · SAM. 19 SEPT. », puis « VEN. 18 SEPT. ». Les jokers affichent le nombre restant après eux ce jour-là, calculé avec le `jokersPerDay` courant.
- **Bandeaux d'alerte** : texte véridique (règle de la revue de la tâche 28 : un message qui ment sur ce que fait l'app est un défaut). « l'onglet Accueil entier est bloqué » n'est affiché que pour Instagram, et seulement si le code confirme que le mode dégradé « version » bloque bien tout l'onglet Accueil ; sinon formulation honnête. Action « ACTIVER » → réglages d'accessibilité ; « DEBUG » → onglet Debug.
- **Icône de lanceur** : fond passé à `#171513` (cohérence, hors maquette, changement d'une couleur).

## 4. Fonctionnalités nouvelles (présentes sur les maquettes, absentes de l'app)

| Écran | Ajout |
|---|---|
| Accueil | Pastille d'état (Libre / Bloqué · quota / Bloqué · nuit / Service inactif / Joker · jusqu'à HH:MM / Relapse · jusqu'à HH:MM) ; anneau record ; ancien record ; « Record en cours depuis N jours » ; jauges par fenêtre (rouge si dépassée) ; « PROCHAINE NUIT · 23:00 → 07:30 » (« NUIT EN COURS · → 07:30 » pendant la nuit) ; ligne d'état sous l'anneau pendant un blocage ou un déblocage ; actions dans les bandeaux. |
| Réglages | Lignes éditables par dialogue, application immédiate, raisons de verrou, sous-titre « Toutes cibles confondues ». |
| Journal | Groupement par jour, typologie colorée, noms lisibles des cibles (« Instagram · Reels », « Instagram · Suggéré », « X · Accueil »), détails (durée, plage du relapse, jokers restants, « notifié »), événements de blocage. |
| Debug | Délai de capture, décision en direct (« Allow · quota 3/5 min », « Block · nuit »), « Il y a N s », liste des captures avec taille, export groupé, nommage lisible. |
| Overlay | Détail de la raison (plage nocturne du jour, fenêtre dépassée, jokers épuisés), anneau record, bouton rond joker/relapse, animation relapse complète, état terminé, « Quitter » vers l'accueil du téléphone. |
| Onboarding | Restylage : en-tête, groupe de lignes (service d'accessibilité, batterie, notifications, Instagram, X) avec statut et bouton « OUVRIR », bouton primaire « CONTINUER ». |

## 5. Architecture

- **Domaine** (`domain`, Kotlin pur) : `Event.Block` ; `Streak.summary(now)` → `StreakSummary(current, best, previousBest, inRecord)` (persiste le record comme `recordAndGetBest`) ; `Schedule.nextNight(now)` (nuit active ou prochaine). Aucune migration Room : `Event.Block` réutilise la table `events` (`type = "BLOCK"`, raison dans `message`, fin dans `unlock_until_utc`).
- **Thème et composants** (`app/.../ui/theme/`, `app/.../ui/components/`) : tokens, typographie, `RehabTheme`, puis composants sans état et sans I/O : en-tête, pastille, anneau (Canvas), carte de jauge, groupe et ligne de réglage (verrouillable), bandeau, libellé de section, boutons primaire/secondaire/bronze, barre de navigation en pilule, rangée clé/valeur.
- **Écrans** : chaque écran garde son couple `XxxScreen` (Composable) + `XxxText` (textes purs, testés en JVM). Toute lecture Room reste dans `RehabViewModel` sur `Dispatchers.IO` ; aucun Composable ne touche `graph` (règles établies en V1, voir le commentaire de tête de `RehabViewModel`).
- **Overlay** : `OverlayState` enrichi (textes calculés par `OverlayText`, état de record), `BlockOverlay` redessiné, `HoldButton` circulaire, effets du relapse en fonctions pures (`RelapseFx`), `OverlayController` gère l'état terminé et le report du masquage. Le service journalise `Event.Block` et remplace `GLOBAL_ACTION_BACK` par `GLOBAL_ACTION_HOME`.

## 6. Tests

TDD sur tout ce qui est pur : domaine (Block, summary, nextNight), `RoomEventLog` (aller-retour BLOCK), `HomeText`, `SettingsText`/constructeurs de réglages proposés, `JournalText` (groupement, libellés), nommage des captures, `OverlayText`, `RelapseFx`, report du masquage dans `OverlayController`. Pas de tests de capture d'écran. Vérification visuelle sur le Pixel après chaque tâche (`./gradlew :app:installDebug`).

## 7. Hors périmètre

Mode clair, animations de transition entre onglets, retour haptique (refusé par DESIGN §5), nouvelles cibles de détection, nettoyage de l'historique des captures personnelles (sujet séparé, voir mémoire du 2026-09-22).
