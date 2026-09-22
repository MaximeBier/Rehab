# Rehab — spécification de design (V1)

Version du 2026-09-22. Source de vérité visuelle : le canevas Claude Design « Rehab ». Ce dossier en est l'export pour l'implémentation Compose. Il complète `docs/superpowers/specs/2026-09-19-rehab-v1-design.md` (comportement) sans la remplacer ; en cas de conflit sur le comportement, la spec fonctionnelle gagne, sauf pour les décisions listées en §6 qui l'amendent explicitement.

Contenu du dossier :

- `DESIGN.md` — ce fichier : tokens, composants, écrans, comportements.
- `screens/*.png` — rendu de chaque écran (390×844 @2x). **Polices de substitution** : les PNG ont été rendus sans Chivo / Chivo Mono ; se fier au HTML pour la typo.
- `mockups/*.dc.html` — source HTML de chaque écran, styles inline, valeurs exactes (px, hex). `Overlay-Proto.dc.html` contient la logique JS de l'animation d'appui long. `canvas.json` est l'index (titres, tailles).

---

## 1. Direction

Sombre, chaud, utilitaire. Aucune image, aucun texte de motivation, aucune gradation décorative. Une seule couleur d'accent (bronze mat) réservée à ce qui est **actif** ou **en record**. Le rouge n'apparaît que pour le relapse, l'ambre que pour les alertes système.

Le compteur de jours n'est jamais présenté comme un objectif à atteindre : l'état normal est « record en cours », l'anneau est alors plein. Hors record, l'anneau reste plein mais gris et l'étiquette devient « Série ».

## 2. Tokens

### Couleurs

| Token | Hex | Usage |
|---|---|---|
| `bg` | `#171513` | Fond de tous les écrans, overlay compris |
| `panel` | `#24211D` | Cartes, lignes de réglage, barre de navigation, piste de l'anneau |
| `text` | `#EDE7DB` | Texte principal, bouton « Quitter » |
| `muted` | `#A0978A` | Libellés, texte secondaire, onglets inactifs |
| `line` | `#33302B` | Filets, piste neutre du bouton d'appui |
| `accent` (bronze) | `#B98457` | Anneau record, jauges, état Libre, onglet actif, bouton joker terminé |
| `accent-hover` | `#CD9A6C` | Survol/pression des liens |
| `danger` (relapse) | `#E4614F` | Jauge pleine, événement relapse, jauge et texte du bouton relapse |
| `danger-deep` | `#6B211B` | Disque du bouton relapse au repos |
| `danger-track` | `#9A3A30` | Piste du bouton relapse |
| `danger-text` | `#F6E4E0` | Texte sur `danger-deep` |
| `warn` (alerte) | `#E2B45A` | Texte des bandeaux d'alerte |
| `warn-bg` | `#2A2418` | Fond des bandeaux d'alerte |
| `ring-off` | `#4A443C` | Anneau hors record |

Contrastes vérifiés : `muted` sur `bg` ≈ 5.6:1, `accent` sur `bg` ≈ 5.3:1, `bg` sur `accent` (onglet actif) ≈ 5.3:1.

### Typographie

- **Chivo** (Google Fonts) — interface. Poids 300 / 400 / 500 / 700.
- **Chivo Mono** — tout ce qui est temps, durée, compteur, valeur numérique alignée. Poids 300 pour les grands chiffres.

| Rôle | Police | Taille / graisse | Casse |
|---|---|---|---|
| Grand compteur (anneau Accueil) | Chivo Mono | 84 px / 300 | — |
| Compteur overlay | Chivo Mono | 64 px / 300 | — |
| Titre overlay (raison) | Chivo | 26 px / 500 | — |
| Marque « Rehab » | Chivo | 13 px / 500, espacement 0.16em | MAJUSCULES |
| Libellé de section | Chivo | 11 px, espacement 0.14em, `muted` | MAJUSCULES |
| Libellé de jauge / pastille | Chivo | 12 px, espacement 0.08em, `muted` | MAJUSCULES |
| Étiquette « RECORD » / « SÉRIE » | Chivo | 11 px / 700, espacement 0.2em | MAJUSCULES |
| Corps | Chivo | 13–15 px / 400 | — |
| Valeur mono en ligne | Chivo Mono | 13–16 px | — |
| Onglet de navigation | Chivo | 11 px, espacement 0.1em (actif : 700) | MAJUSCULES |

### Formes et espacements

- Rayon des cartes et lignes : **16 px** ; pastilles et pilule de navigation : 22 px (pilule) / 16 px (onglet actif) ; boutons : 14–18 px.
- Marges latérales : 24 px (écrans), 28 px (overlay), 16 px pour la pilule de navigation.
- Padding des cartes : 18 px. Gap entre cartes : 12 px.
- Jauge horizontale : 8 px de haut, rayon 4, piste `bg` sur carte `panel`.
- Anneau du record : 220 px Ø, trait 12 px (Accueil) ; 176 px Ø, trait 10 px (overlay). Toujours **plein** (cercle complet), jamais une progression.
- Cibles tactiles ≥ 44 px partout ; bouton d'appui long 96 px ; « Quitter » 60 px de haut.

## 3. Composants

### Barre de navigation (4 onglets)
Pilule `panel` pleine largeur (marge 16 px), padding 6 px, rayon 22. Quatre cellules égales, texte seul en majuscules, 14 px de padding vertical. Onglet actif : fond `accent`, texte `bg`, 700, rayon 16. Inactifs : `muted`. Pas d'icônes.

### Pastille d'état (en-tête, à droite)
Fond `panel`, rayon 16, padding 6×12, point 8 px + libellé 12 px majuscules. Couleur = état : Libre → `accent` ; Bloqué → `muted` ; Service inactif → `warn`.

### Anneau du record (Accueil)
Cercle plein `accent` (ou `ring-off` hors record). Au centre, empilés : « RECORD » (ou « SÉRIE ») 11 px, le nombre 84 px mono 300, « JOURS » 11 px `muted`. Sous l'anneau : « Record en cours depuis N jour(s) » puis « ANCIEN RECORD 23 · JOKERS 2/2 » (12 px majuscules `muted`, valeurs en mono). Hors record : « Sans relapse depuis 12 jours · record 23 ». Aucun effet visuel supplémentaire pour l'état record : l'anneau plein bronze + l'étiquette suffisent.

### Carte de jauge (fenêtre de quota)
Carte `panel`, libellé « FENÊTRE 30 MIN » à gauche, valeur mono « 3 / 5 min » à droite, jauge 8 px dessous. Fenêtre dépassée : valeur et jauge en `danger`.

### Ligne de réglage
Carte `panel` empilée (les lignes d'un groupe partagent une carte : rayon 16 en haut de la première et en bas de la dernière, filet `bg` 1 px entre). Libellé à gauche, valeur mono + chevron `muted` à droite. **Verrouillée** : texte `muted`, cadenas 14 px avant le libellé, sous la valeur la raison en 11 px (« Modifiable à partir de 07:30 », « Modifiable dans 18 min »), pas de chevron.

### Bandeau d'alerte
Fond `warn-bg`, texte `warn` 13 px, action à droite en 11 px majuscules 700 (« ACTIVER », « DEBUG »). Rayon 14. Empilables en haut de l'Accueil.

### Boutons
- Primaire (« Quitter ») : fond `text`, texte `bg` 14 px 700 majuscules 0.1em, 60 px, rayon 18. Pressé : fond `muted`.
- Secondaire : contour 1 px `muted`, texte `text`, 48 px, rayon 14.
- Action bronze (Debug « Capturer ») : fond `accent`, texte `bg`, 50 px.

### Bouton d'appui long (overlay) — voir §5
Disque 96 px. Deux variantes, aucune autre :
- **Joker** : disque `bg` (sur fond `bg`, il se lit par sa piste), piste `line` 8 px, libellé « JOKER » 12 px `text`. Pendant l'appui : jauge `accent`, décompte mono 22 px au centre (« 4 » / « SEC »). Terminé : disque `accent` plein, « +5 / MIN » en `bg`.
- **Relapse** : disque `danger-deep`, piste `danger-track`, libellé « RELAPSE » 12 px `danger-text`. Pendant l'appui : jauge `danger`. Terminé : disque `danger` plein, « +15 / MIN » en `bg`.
- Au repos la jauge est **absente** (ne pas dessiner un arc de longueur 0 avec des extrémités rondes : ça fait un point).
- Relâché avant la fin : jauge vidée immédiatement, aucun effet.

## 4. Écrans

Tous les écrans partagent : padding haut 56 px (laisser la barre d'état système), fond `bg`, en-tête « REHAB » + pastille d'état, navigation en bas. Pas de faux éléments système.

### Accueil (`Main`, `Accueil-Bloque`, `Accueil-Alertes`)
De haut en bas : en-tête ; anneau du record + lignes sous l'anneau ; carte jauge 30 min ; carte jauge 6 h ; ligne « PROCHAINE NUIT · 23:00 → 07:30 » ; navigation.
- **Libre, record** : pastille Libre bronze, anneau bronze.
- **Bloqué (quota)** : pastille « Bloqué · quota » grise ; sous l'anneau « Quota atteint · déblocage dans 18 min (22:18) » ; jauge dépassée en rouge ; l'anneau reste bronze (le record tient).
- **Alertes** : bandeaux au-dessus de l'anneau (service désactivé → Activer ; app hors plage de versions → Debug). Cet écran montre aussi l'état hors record (anneau réduit gris, 12 jours, record 23).
- Non dessiné : état « Joker / Relapse en cours jusqu'à HH:MM » — reprendre la pastille et la ligne sous l'anneau.

### Réglages (`Reglages`, hauteur 1240, défile)
Sections « PLAGE NOCTURNE » (7 lignes Lundi→Dimanche, format `23:00 → 07:30` mono ; note sous le groupe), « QUOTA GLISSANT » (une ligne par fenêtre : « Sur 30 min · 5 min max » ; bouton secondaire « Ajouter une fenêtre »), « DÉBLOCAGE » (durée joker, durée relapse, jokers par jour, durée d'appui). Lignes verrouillées selon §2.2/2.3 de la spec, avec la raison affichée. Les éditeurs (sélecteur d'heure, durée/plafond) ne sont **pas dessinés** : utiliser des composants Material 3 standard stylés avec les tokens.

### Journal (`Journal`)
Groupé par journée Rehab (« AUJOURD'HUI · DIM. 20 SEPT. »). Chaque ligne : heure mono `muted` (52 px) · point de couleur + libellé + type en 11 px · détail mono à droite. Couleur du point : relapse `danger`, joker `accent`, service/règles `warn`, usage `muted`. Brut, sans filtre.

### Debug (`Debug`, hauteur 1000)
« CAPTURE DE LA STRUCTURE » : ligne avec champ délai (s) + bouton bronze « Capturer le prochain écran cible » + note. « DERNIÈRE DÉTECTION · EN DIRECT » : paires clé/valeur (package, version, targetId en bronze, unknownScreen, décision, il y a N s). « CAPTURES · N » : liste fichier/taille. Deux boutons secondaires : Exporter, Overlay de test.

### Onboarding
Non dessiné (spec §4.2.5). À faire : checklist trois lignes (service d'accessibilité, optimisation batterie, versions Instagram/X) avec bouton « Ouvrir » par ligne, mêmes composants que Réglages.

## 5. Overlay de blocage

**Même fond et même structure quelle que soit la condition.** Seuls changent la variante du bouton (joker / relapse) et le texte sous le bouton. Aucun compte à rebours : on ne montre pas le temps qui reste, pour ne pas inviter à attendre.

De haut en bas (padding haut 64 px, marges 28 px) :
1. « REHAB · BLOCAGE » 12 px `muted`.
2. Raison, 26 px : « Nuit · déblocage à 07:30 » ou « Quota atteint · 18 min ». Dessous, 14 px `muted`, le détail (« Plage nocturne du dimanche : 23:00 → 07:30 », « 5 min sur les 30 dernières minutes, toutes cibles », « Jokers du jour épuisés (2/2) »).
3. Anneau du record (176 px), « Record en cours depuis N jour » — ou version hors record.
4. Espace flexible.
5. Bouton d'appui long 96 px, puis le texte d'annonce dans une **boîte de hauteur fixe 40 px** (deux lignes) pour que rien ne bouge quand le texte change :
   - joker : « Maintenir 10 s · **Joker +5 min** · 1 restant aujourd'hui » (Joker en `accent`), texte `text` ;
   - relapse : « Maintenir 10 s · **RELAPSE** · ton streak de 24 jours tombe », texte `danger` (hors record : « ta série de 12 jours tombe »).
6. **« Quitter »** : bouton primaire pleine largeur, 60 px, marges 20 px — c'est l'action principale, dans la zone du pouce. Il ramène à l'écran d'accueil du téléphone (`GLOBAL_ACTION_HOME`), pas `BACK` (amendement, voir §6).
7. La barre de navigation système n'est pas couverte (48 px réservés en bas).

Décision joker / relapse = spec §2.6 : hors nuit et jokers restants → joker ; nuit ou jokers épuisés → relapse.

### Animation d'appui long (relapse) — référence `mockups/Overlay-Proto.dc.html`

`p` = progression 0→1 sur la durée d'appui (10 s par défaut). Tout est piloté par `p`, mis à jour toutes les ~40 ms. Relâcher : retour à `p = 0` instantané, sans transition de « récompense ».

| Élément | Comportement |
|---|---|
| Fond de l'écran | interpolation linéaire `#171513` → `#5A1A15` |
| Anneau bronze du record | opacité `1 − p` |
| Anneau rouge par-dessus | arc de longueur `p × circonférence`, opacité 0.9 dès `p > 0` |
| Nombre de jours | `round(24 × (1 − p))`, passe en `danger` à `p > 0.5` |
| Ligne sous l'anneau | `p > 0.5` : « Tu es en train de le perdre. » |
| Bouton | échelle `1 + 0.18 p` ; à partir de `p > 0.3`, pulsation (scale 1→1.06→1) de période `0.9 − 0.5 p` s |
| Jauge du bouton | arc rouge `p × circonférence` ; centre = secondes restantes (mono 22) / « SEC » |
| Texte sous le bouton | « Encore N s et ton streak de 24 jours tombe » ; échelle `1 + 0.12 p`, graisse 700 à `p > 0.5` |
| Écran entier | à partir de `p > 0.6`, tremblement ±2 px de période `0.14 − 0.2 (p − 0.6)` s (s'accélère) |
| Fin (`p = 1`) | fond rouge figé, bouton `danger` plein « +15 / MIN », « Relapse enregistré · déblocage 15 min », série à 0, record conservé |

Joker : même mécanique de jauge et de décompte, **sans** changement de fond, sans tremblement, sans pulsation ; à la fin, bouton `accent` plein « +5 / MIN ». Pas de retour haptique (décision explicite).

## 6. Décisions prises pendant le design (amendements à la spec)

1. **Pas de compte à rebours sur l'overlay** (spec §4.1 en prévoyait un). La raison et l'heure/durée de déblocage restent affichées une fois, statiques.
2. **« Quitter » remplace « Retour »** et déclenche le retour à l'accueil du téléphone plutôt que `GLOBAL_ACTION_BACK`, qui peut ramener sur une cible et refaire apparaître l'overlay aussitôt.
3. Le bouton d'appui long porte le nom de son effet (« Joker » / « Relapse »), jamais « Maintenir ».
4. Fond d'overlay unique (le thème de l'app) — pas de fond sombre/neutre/rouge selon la condition. Le rouge n'arrive qu'avec l'appui relapse, progressivement.
5. Hiérarchie : Quitter primaire et gros, bouton d'appui secondaire et petit.
6. Le joker n'annonce pas la reprise du blocage après ses 5 minutes (choix assumé).

## 7. Points ouverts

- Onboarding à dessiner.
- Éditeurs de Réglages (heures, fenêtres) : composants standard, non maquettés.
- Verrous de Réglages : ajouter la cause avant le délai (« Quota dépassé — modifiable dans 18 min »).
- État Accueil « déblocage en cours » (joker / relapse actif).
- Écran Accueil en état record : l'étiquette « RECORD » est à 11 px sur les overlays et 10 px sur l'Accueil — harmoniser à 11.
