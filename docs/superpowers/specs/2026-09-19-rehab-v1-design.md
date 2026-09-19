# Rehab V1 — Spécification de design

Date : 2026-09-19
Statut : validé en brainstorming, en attente de relecture finale

## 1. Objectif

Rehab est une application Android personnelle qui bloque les surfaces de « scroll infini » de certaines apps (Instagram, X) sans bloquer les apps elles-mêmes. Le blocage est conditionnel (plage nocturne, quota glissant) et peut être levé par un mécanisme de relapse volontairement lent. Un compteur de jours sans relapse mesure la progression.

### Cibles V1

| TargetId | App | Écran ciblé |
|---|---|---|
| `InstagramReels` | Instagram | Viewer Reels (onglet Reels ou Reel ouvert depuis ailleurs) |
| `InstagramSuggested` | Instagram | Onglet Accueil **quand un post « Suggéré pour vous » est visible** |
| `TwitterHome` | X | Écran d'accueil entier (onglets « Pour vous » et « Abonnements ») |

Tout le reste de ces apps (DM, Stories, recherche, profils, notifications) n'est ni bloqué ni compté.

### Hors périmètre V1

Statistiques et graphiques, autres apps, synchronisation, Play Store, protection anti-désinstallation ou anti-désactivation, règles téléchargeables, thèmes, iOS, web.

### Contexte et contraintes

- Utilisateur unique (l'auteur), installation par APK sideloadé. Pas de revue Google.
- Appareil : Pixel, Android stock, Android 14+. `minSdk = 34`.
- Interface Instagram/X en français ; règles de détection en français avec l'anglais en secours.
- Aucun accès réseau. Aucune télémétrie.
- L'utilisateur se fait confiance : désactiver le service n'entraîne aucune pénalité.

## 2. Comportement fonctionnel

### 2.1 Conditions de blocage

Un écran cible visible est **bloqué** si l'une des conditions suivantes est vraie, évaluée dans cet ordre :

1. **Déblocage actif** (joker ou relapse en cours, `unlockUntil > now`) → **Autorisé**, court-circuite tout le reste. Le temps consommé est journalisé.
2. **Plage nocturne** : `now` est entre l'heure de coucher et l'heure de lever configurées pour le jour courant → **Bloqué (Nuit)**, déblocage à l'heure de lever.
3. **Quota glissant** : au moins une fenêtre `(durée, plafond)` est dépassée → **Bloqué (Quota)**, déblocage au premier instant où toutes les fenêtres repassent sous leur plafond.
4. Sinon → **Autorisé**.

Les trois cibles partagent exactement les mêmes conditions. Hors nuit et sous quota, elles sont librement accessibles et ce temps consomme le quota.

### 2.2 Plage nocturne

Sept couples `(coucher, lever)`, un par jour de la semaine, en heure locale. La plage du jour J s'étend de `coucher(J)` à `lever(J+1)`. Une plage qui traverse minuit est découpée en `[coucher, 24h[` du jour J et `[0h, lever[` du jour J+1. Si `coucher == lever`, pas de plage ce jour-là.

Défauts : 23:00 → 07:30 en semaine, 00:30 → 09:00 vendredi et samedi soir.

Un changement de réglage est appliqué à l'évaluation suivante, sans rétroactivité sur les journaux.

### 2.3 Quota glissant

Liste ordonnée de fenêtres `(durée, plafond)`. Pour chaque fenêtre, on somme les intervalles d'usage (toutes cibles confondues) intersectant `[now - durée, now]`. Dépassement si `somme ≥ plafond`.

Défauts :

| Fenêtre | Plafond |
|---|---|
| 30 min | 5 min |
| 6 h | 30 min |

`unlockAt` par fenêtre : premier instant `t ≥ now` tel que la somme sur `[t - durée, t]` repasse strictement sous le plafond. Calcul par balayage des bornes de début d'intervalles (la somme ne décroît qu'à ces instants). `unlockAt` global = max des fenêtres en dépassement.

### 2.4 Journal d'usage

Intervalle = `(targetId, startUtc, endUtc?)`. Ouvert à la première détection d'une cible en mode Autorisé. Fermé quand : la cible détectée change, l'app au premier plan change, l'écran s'éteint, ou la décision passe à Bloqué. Un intervalle fermé de moins de 2 s est supprimé (bruit de navigation). Le temps passé pendant un joker ou un relapse est compté normalement.

Au redémarrage du service, un intervalle resté ouvert est fermé à l'heure du dernier tick connu (persisté toutes les 10 s pendant qu'un intervalle est ouvert).

Rétention : 30 jours, purge quotidienne.

### 2.5 Journée Rehab

`dayOf(now)` = date locale de `now` si `now ≥ lever(date)`, sinon la date précédente. Sert au reset des jokers et au calcul du streak. Un relapse à 01:00 appartient à la journée qu'on termine.

### 2.6 Bouton unique à appui long : joker ou relapse

Un seul bouton sur l'overlay, appui maintenu 10 s (configurable) avec jauge. Relâcher avant la fin annule sans effet.

Effet à la fin de l'appui :

| Situation | Événement | Déblocage | Streak |
|---|---|---|---|
| Hors nuit **et** `jokersUsed(dayOf(now)) < 2` | `Joker` | 5 min | intact |
| Nuit, **ou** jokers du jour épuisés | `Relapse` | 15 min | cassé |

Le déblocage lève **toutes** les conditions sur **toutes** les cibles pendant sa durée. À expiration, le blocage reprend selon l'état courant (nuit ou quota), donc très probablement immédiatement après un relapse puisque le temps a été compté.

L'overlay annonce **avant** l'appui ce qui va se produire : « Maintenir 10 s · Joker +5 min · 1 restant » ou, en rouge, « Maintenir 10 s · RELAPSE · ton streak de 12 jours tombe ».

Valeurs configurables : durée joker (5 min), durée relapse (15 min), jokers par jour (2), durée d'appui (10 s).

### 2.7 Streak

Nombre de journées Rehab consécutives sans événement `Relapse`, en remontant depuis `dayOf(now)` incluse. La journée courante compte comme acquise tant qu'aucun relapse n'y figure. Le premier jour compte depuis la date d'installation. `Record` = maximum historique, persisté, jamais décrémenté.

Jokers, quota atteint, désactivation du service : aucun effet sur le streak.

### 2.8 Mode dégradé (fail-closed)

Chaque jeu de règles porte une plage de versions testées de l'app cible. Si la version installée est hors plage, **ou** si le détecteur renvoie `Unknown` pendant plus de 30 s consécutives alors que l'onglet Accueil Instagram est sélectionné, la cible `InstagramSuggested` devient « tout l'onglet Accueil Instagram » jusqu'à mise à jour des règles. Une notification silencieuse, une fois par version d'app, signale « Règles à mettre à jour pour Instagram 3xx ». Le même mécanisme de version s'applique à X (cible déjà entière, seule la notification change).

### 2.9 Cas limites tranchés

- **Changement d'heure système / DST / voyage** : journaux en `Instant` UTC ; conversion locale uniquement pour la plage nocturne et `dayOf`. Un saut d'heure peut offrir ou coûter jusqu'à une heure de quota une fois par an, accepté.
- **Redémarrage du téléphone** : journaux persistés, quota et déblocage en cours survivent.
- **Service désactivé puis réactivé** : événements `ServiceOff` / `ServiceOn` journalisés, bandeau dans l'app, aucune pénalité.
- **Écran cible visible pendant la transition Autorisé → Bloqué** : overlay affiché au tick suivant (≤ 1 s).

## 3. Architecture

Projet Gradle Kotlin multi-module. Pas de dépendance réseau. DI manuelle via un `AppGraph` (pas de Hilt).

```
rehab/
├── domain/   Kotlin pur (JVM). Politique, quota, horaires, streak. Zéro import Android.
├── rules/    Kotlin pur (JVM). Définitions déclaratives des écrans cibles + détecteur.
├── app/      Android. Service d'accessibilité, overlay, UI Compose, Room, DataStore.
└── docs/     Spec, plan, checklist de test manuel, guide de mise à jour des règles.
```

Dépendances : `app → rules → domain`. `rules` ne dépend de `domain` que pour `TargetId`.

### 3.1 `domain`

- `Clock` (interface) : `now(): Instant`, `zone(): ZoneId`. Injecté partout.
- `PolicyEngine.evaluate(target: TargetId, now: Instant, settings, usage: UsageLog, events: EventLog): Decision`
  `Decision = Allow | Block(reason: Night | Quota, unlockAt: Instant)`.
- `Schedule` : calcul de la plage nocturne et de `dayOf`.
- `SlidingQuota` : somme par fenêtre et `unlockAt`.
- `Streak` : `current(now)`, `best()`.
- `UnlockPolicy.onLongPressCompleted(now) → Joker | Relapse` et application.
- Ports : `UsageLog`, `EventLog`, `SettingsRepo`, `StreakRecordRepo`.

### 3.2 `rules`

```kotlin
data class TargetRule(
    val id: TargetId,
    val packageName: String,
    val testedVersions: VersionRange,
    val screenMatchers: List<Matcher>,        // tous requis
    val triggerMatchers: List<Matcher> = emptyList(), // au moins un requis s'il y en a
    val navBarMatcher: Matcher? = null,
    val priority: Int,                        // Reels > Feed
)
sealed interface Matcher {
    data class ViewId(val idSuffix: String) : Matcher
    data class ContentDesc(val regex: Regex) : Matcher
    data class Text(val regex: Regex) : Matcher
    data class ClassName(val name: String) : Matcher
    data class Selected(val inner: Matcher) : Matcher
}
```

- `Snapshot` : liste plate de `Node(id, className, text, contentDesc, bounds, selected)`, plus `packageName`, `appVersion`. Limité à 400 nœuds et 12 niveaux de profondeur. Sérialisable en JSON.
- `ScreenDetector.detect(snapshot): Detection(targetId: TargetId?, navBarBounds: Rect?, unknownScreen: Boolean)`. Évalue les règles du package par priorité décroissante ; première qui matche gagne. `unknownScreen = true` si aucun écran connu (même non ciblé) n'est reconnu, utilisé pour le fail-closed.
- Règles initiales : `InstagramRules.kt` (Reels, Suggested, plus les écrans « connus non ciblés » : DM, profil, recherche, pour le fail-closed), `TwitterRules.kt` (Home). Les identifiants de vue exacts sont relevés sur l'appareil à la première capture ; la spec les considère comme hypothèses à valider.
- Chaque règle combine au moins un `ViewId` et un `ContentDesc` pour l'écran (l'un suffit) ; le déclencheur « suggéré » exige le libellé : `Suggéré(e)?s? pour vous|Suggestions pour vous|Suggested for you`.

### 3.3 `app`

- `RehabAccessibilityService`
  - `packageNames` déclarés dans la config XML : Instagram, X uniquement. Aucun événement des autres apps.
  - Événements écoutés : `TYPE_WINDOW_STATE_CHANGED`, `TYPE_WINDOW_CONTENT_CHANGED`, `TYPE_VIEW_SCROLLED`. Débounce 300 ms.
  - Pipeline : événement → `SnapshotBuilder` → `ScreenDetector` → `UsageTracker` → `PolicyEngine` → `OverlayController`.
  - Ticker 1 s actif uniquement tant qu'une cible est détectée (pour lever le blocage à l'heure ou à l'expiration sans nouvel événement, et pour mettre à jour le compte à rebours).
  - Écoute `ACTION_SCREEN_OFF` pour fermer l'intervalle et retirer l'overlay.
  - Toute exception dans le traitement est attrapée et journalisée ; le service ne meurt jamais sur un événement.
- `OverlayController` : fenêtre `TYPE_ACCESSIBILITY_OVERLAY` hébergeant un `ComposeView`. Non focusable, ne capte pas les gestes système. Couvre l'écran sauf `navBarBounds` si connu. Apparition immédiate sans animation, disparition en fondu court. En cas d'échec d'affichage : `GLOBAL_ACTION_BACK` puis retry au tick suivant.
- `UsageTracker` : gère l'intervalle ouvert, persiste le dernier tick toutes les 10 s.
- `VersionChecker` : lit `PackageManager` pour les versions Instagram/X, compare aux `testedVersions`, émet l'événement et la notification.
- Persistance : Room (`usage_intervals`, `events`, `streak_record`) et DataStore Proto (réglages). Schéma versionné dès la V1 ; migrations destructives interdites.
- UI Compose : Accueil, Réglages, Journal, Debug, Onboarding.

### 3.4 Flux nominal

```
Événement A11y (Instagram)
  → debounce 300 ms
  → SnapshotBuilder (≤ 400 nœuds)
  → ScreenDetector → Detection(InstagramReels, navBar)
  → UsageTracker.onDetected(InstagramReels, now)     // ouvre/maintient l'intervalle
  → PolicyEngine.evaluate(InstagramReels, now, …)
      → Allow  : OverlayController.hide()
      → Block  : UsageTracker.close(now) ; OverlayController.show(reason, unlockAt, streak, buttonLabel)
  → Ticker 1 s tant que la cible reste détectée
```

## 4. Interface utilisateur

### 4.1 Overlay de blocage

De haut en bas : raison et heure/durée de déblocage (« Nuit · déblocage à 07:30 », « Quota atteint · 18 min »), compte à rebours vivant, streak (« 12 jours · record 23 »), bouton **Retour** (`GLOBAL_ACTION_BACK`), bouton unique à appui long avec jauge circulaire et libellé calculé (section 2.6). Fond opaque uni : sombre pour la nuit, neutre pour le quota, rouge lorsque l'appui aboutira à un relapse. Aucune image, aucun texte de motivation.

### 4.2 Écrans de l'app

1. **Accueil** : streak + record ; état courant (Libre / Bloqué jusqu'à… / Joker ou Relapse en cours jusqu'à…) ; consommation par fenêtre (« 3 / 5 min sur 30 min ») ; jokers restants ; bandeaux d'alerte : service inactif, app cible hors plage de versions.
2. **Réglages** : 7 lignes coucher/lever ; liste des fenêtres de quota (ajout, suppression, édition) ; durée joker ; durée relapse ; jokers par jour ; durée d'appui. Application immédiate.
3. **Journal** : liste chronologique des événements et intervalles d'usage. Brut. Base des futures statistiques.
4. **Debug** : « Capturer la structure de l'écran » avec délai (5 s par défaut) : à l'échéance, le service sérialise le prochain `Snapshot` d'une app cible en JSON dans `files/captures/` ; « Dernière détection » (package, version, `targetId`, `unknownScreen`) en direct ; export des captures (partage système) ; « Afficher un overlay de test ».
5. **Onboarding** : checklist affichée au premier lancement et tant qu'un prérequis manque : activer le service d'accessibilité ; exclure Rehab de l'optimisation batterie ; vérifier la présence et la version d'Instagram et X. Chaque ligne a un bouton ouvrant l'écran système correspondant.

### 4.3 Notifications

Une seule catégorie, silencieuse : « Règles à mettre à jour pour <app> <version> », une fois par version. Aucune autre notification.

## 5. Consommation et cycle de vie

- Filtre de packages à la source : zéro événement hors Instagram/X.
- Débounce 300 ms ; lecture d'arbre bornée.
- Ticker actif uniquement pendant qu'une cible est visible.
- Pas de service au premier plan ni de notification permanente : sur Android stock, un service d'accessibilité activé est relancé par le système au boot et n'est pas tué par l'optimisation batterie. L'exclusion batterie est demandée par précaution.
- Le service démarre sans lancer l'app Rehab.

## 6. Persistance

| Table | Colonnes | Rétention |
|---|---|---|
| `usage_intervals` | `id, target_id, start_utc, end_utc?` (index `start_utc`) | 30 jours |
| `events` | `id, type, at_utc, payload_json` — types : `JOKER, RELAPSE, SERVICE_ON, SERVICE_OFF, RULES_OUT_OF_RANGE, ERROR` | illimitée (`ERROR` plafonné à 500 lignes) |
| `streak_record` | `best_days` (une ligne) | illimitée |

Réglages en DataStore Proto avec les défauts de la section 2. Base corrompue : erreur affichée, aucun effacement automatique.

## 7. Gestion d'erreur

- Exception dans le traitement d'un événement : attrapée, journalisée (Logcat + `events.ERROR`), le service continue.
- Arbre indisponible ou dépassant les bornes : `Detection(null, unknownScreen = true)`, pas de blocage sur cet événement, réévaluation au suivant. Contribue au compteur des 30 s du fail-closed.
- Overlay impossible : `GLOBAL_ACTION_BACK` puis retry.
- App cible non installée : règles ignorées, bandeau d'information.

## 8. Tests

- **`domain`** (TDD, JVM, `FakeClock`) : plage traversant minuit ; `coucher == lever` ; quota exactement au plafond ; `unlockAt` avec deux fenêtres ; `dayOf` une minute avant et après le lever ; streak avec relapse à 01:00 ; 3ᵉ appui du jour ; appui de nuit avec jokers restants ; intervalle chevauchant la borne de fenêtre ; DST.
- **`rules`** (JVM, fixtures JSON capturées sur l'appareil) : Reels → `InstagramReels` ; feed sans suggéré → `null` ; feed avec suggéré → `InstagramSuggested` ; DM → `null` et `unknownScreen = false` ; X accueil → `TwitterHome` ; X recherche → `null` ; arbre inconnu → `unknownScreen = true`.
- **`app`** (Robolectric) : `UsageTracker` (ouverture, fermeture, suppression < 2 s, reprise après redémarrage) ; `SnapshotBuilder` (bornes 400 nœuds / 12 niveaux) ; mapping Room ↔ ports du domaine.
- **Manuel sur appareil** : checklist dans `docs/manual-test-checklist.md` (overlay sur Reels, barre de nav cliquable, appui long annulé, joker puis relapse, blocage nocturne, expiration du quota, survie au redémarrage, capture Debug).

## 9. Extensibilité prévue

- **Nouvelle app** : un fichier `XxxRules.kt` + fixtures + ajout du package dans la config du service. Aucune modification du domaine ni de l'overlay.
- **Statistiques** : `usage_intervals` et `events` contiennent déjà tout ; un écran Stats lit ces tables. Rétention de 30 jours ajustable.
- **Autres conditions de blocage** : `PolicyEngine` évalue une liste ordonnée de `BlockRule` ; en ajouter une (par exemple plage de travail) = une implémentation de plus.
- **Règles téléchargeables** : `TargetRule` est une data class sérialisable ; un chargeur JSON peut remplacer le catalogue Kotlin sans toucher au détecteur.

## 10. Prérequis de développement

- Android Studio (JDK, SDK, `adb`, Gradle). Aucun n'est installé sur le poste au moment de la spec.
- Pixel avec débogage USB pour : relever les identifiants de vue Instagram/X (`adb shell uiautomator dump` puis captures Debug), installer l'APK, lire `logcat`.
