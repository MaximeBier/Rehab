# Mettre à jour les règles de détection

Instagram et X changent leur interface régulièrement. Quand ça arrive, Rehab ne reconnaît plus
un écran (Reels, l'accueil…) et ne le bloque plus, ou au contraire bloque un écran qu'il ne
devrait pas bloquer. Ce guide explique comment corriger ça seul, sans connaissance préalable
d'Android : capturer ce que voit le téléphone, comparer avec ce que Rehab attend, ajuster, puis
vérifier que rien d'autre ne casse.

**Symptômes qui doivent déclencher cette procédure :**
- Reels ou l'accueil X ne sont plus bloqués (le quota ou la nuit n'ont pourtant pas expiré).
- L'Accueil de Rehab affiche un bandeau « … hors plage testée : … est bloqué en entier en
  attendant une mise à jour des règles. ».
- L'écran Debug affiche « Écran : inconnu » sur un écran qui devrait pourtant être reconnu
  (accueil, DM, profil…).
- Une notification Android « Règles à mettre à jour pour Instagram/X … » apparaît.

## Comment fonctionnent les règles (pour comprendre ce qu'on va toucher)

Le module `rules` (`rules/src/main/kotlin/rehab/rules/`) contient, pour chaque application
surveillée, un objet qui décrit :
- les **plages de versions testées** (`testedVersions`, type `VersionRange`) : en dehors de ces
  plages, l'app passe en **mode dégradé** — c'est un choix délibéré de sécurité (« fail-closed »)
  plutôt que de deviner un comportement non vérifié ;
- les **écrans ciblés** (`targets`, des `TargetId` comme `InstagramReels` ou `TwitterHome`) à
  bloquer selon les règles de nuit/quota ;
- les **écrans connus mais non ciblés** (`knownScreens`, ex. `instagram.dm`, `instagram.profile`)
  qui prouvent que les règles fonctionnent encore même là où on ne bloque rien ;
- la **barre de navigation** (`navBarMatcher`) et l'**onglet Accueil** (`homeTabMatcher`), utilisés
  pour ne pas cacher la barre du bas sous l'overlay et pour savoir si l'utilisateur regarde le fil
  principal.

Ces objets (`InstagramRules.PACKAGE_RULES`, `TwitterRules.PACKAGE_RULES`) sont dans
`rules/src/main/kotlin/rehab/rules/catalog/InstagramRules.kt` et `TwitterRules.kt`, et sont
assemblés dans `rules/src/main/kotlin/rehab/rules/DefaultCatalog.kt` (`DefaultCatalog.create()`),
lu une seule fois au démarrage du service Rehab. **`DefaultCatalog` est le seul endroit où on
déclare quelles apps sont surveillées** — c'est lui qui construit le `RuleCatalog` utilisé par le
détecteur d'écran (`ScreenDetector`) et qui fixe la liste des packages écoutés par le service
(`RuleCatalog.packageNames`).

**Mode dégradé, en clair** : quand la version installée sort de `testedVersions`, ou quand un
écran reste « inconnu » pendant 30 secondes d'affilée (`DegradedModeTracker`, seuil câblé en dur),
Rehab considère qu'il ne peut plus faire confiance à ses propres règles pour cette application.
Une notification est envoyée (une seule fois par nouvelle version, cf. `VersionChecker`), et
l'Accueil affiche le message d'avertissement. C'est volontaire : mieux vaut prévenir que bloquer
au hasard un écran mal reconnu, ou pire, laisser passer un Reels non détecté sans le signaler.

## Piège n°1 : un matcher qui attrape le mauvais élément

Sur X, la barre de navigation du bas et un sélecteur d'onglets en haut de l'écran (« Pour
vous »/« Abonnements » sur l'accueil, ou le sélecteur de la recherche) se ressemblent du point de
vue de l'arbre d'accessibilité : les deux ont un élément `selected=true` avec le nom de l'onglet
en `content-desc`. Un premier matcher qui cherchait juste « un élément sélectionné contenant ce
libellé » attrapait donc aussi bien la barre du bas que le sélecteur du haut.

La correction : `Matcher.NearBottom` (dans `rules/src/main/kotlin/rehab/rules/Matcher.kt`) ne
retient un nœud que si le haut de ses bounds est dans la fraction basse de l'écran (75 % par
défaut, mesuré à partir de la hauteur du nœud racine du snapshot, jamais du maximum des bounds de
tous les nœuds — un nœud scrollable hors-écran fausserait ce maximum). `TwitterRules.kt` combine
`Matcher.Selected(Matcher.Any)` et `Matcher.NearBottom()` avec `Matcher.AllOf`, puis vérifie que
l'élément qui porte le bon libellé est bien **contenu dans** ce conteneur sélectionné bas de page
(`Matcher.Within`). Retenir : **un matcher qui « matche trop » (deux zones d'écran au lieu
d'une) est un vrai bug, même s'il matche bien la bonne zone au passage** — c'est ce genre d'écart
qu'une capture de la mauvaise page (ex. la recherche) permet de repérer.

## Piège n°2 : pas d'identifiant de conteneur en Compose côté X

L'app X est écrite avec Jetpack Compose, ce qui veut dire qu'il n'existe **aucun `resource-id`**
sur la barre de navigation ni sur ses onglets (contrairement à Instagram, en vues classiques,
dont les identifiants comme `clips_tab` ou `tab_bar` sont stables). Sur X, il faut donc identifier
les éléments uniquement par leur **position** (`Matcher.NearBottom`) et leur **contenu visible**
(`Matcher.ContentDesc`, ex. « Accueil », « Explorer », « Messages », « Notifications », « Grok »).
Conséquences pratiques :
- Une règle Compose est plus fragile face à un changement de traduction ou de libellé que face à
  une refonte de mise en page ; garder les regex des `ContentDesc`/`Text` volontairement larges
  (accents, FR/EN) plutôt que de figer une chaîne exacte.
- `navBarMatcher` de X n'a pas non plus d'identifiant : on utilise l'icône « Accueil » elle-même
  comme repère (`Matcher.ContentDesc(Regex("^(Accueil|Home)$"))`), avec un commentaire dans le
  code qui explique pourquoi la partie haute de la barre reste visible sous l'overlay (elle laisse
  la zone tactile de la barre cliquable, cf. checklist section 5).

## Procédure

### 1. Capturer les écrans

Dans Rehab, onglet Debug : « Capturer dans 5 s », puis ouvrir l'écran concerné dans Instagram ou
X avant la fin du compte à rebours. Le premier écran affiché après l'échéance est enregistré en
JSON dans `files/captures/` de l'application (visible ensuite via « Dernière capture : … »).
Répéter pour **chaque écran** de la section « Détection » de
[`docs/manual-test-checklist.md`](manual-test-checklist.md) : Reels, accueil (haut de fil et
« Suggestions pour vous »), DM, profil, recherche côté Instagram ; accueil, recherche, messages
côté X.

### 2. Récupérer les fichiers sur l'ordinateur

Par le bouton « Partager » de l'écran Debug (vers un mail ou un Drive), ou directement en USB :

```bash
adb shell run-as rehab.app cat files/captures/<fichier>.json > rules/src/test/resources/captures/raw/<nom>.json
```

Nommer le fichier comme les fixtures existantes dans `rules/src/test/resources/captures/raw/`
(`ig_reels.xml`, `ig_feed_suggested.xml`, `ig_dm.xml`, `ig_profile.xml`, `ig_search.xml`,
`x_home_foryou.xml`, `x_home_following.xml`, `x_dm.xml`, `x_search.xml`…). Les fixtures actuelles
sont au format `.xml` (dumps `uiautomator`) ; les nouvelles captures faites depuis l'écran Debug
sont en `.json` (format `Snapshot` interne). Les deux formats sont acceptés :
`Fixtures.load` (`rules/src/test/kotlin/rehab/rules/Fixtures.kt`) choisit le décodeur JSON ou XML
selon l'extension du fichier. Mettre à jour `rules/src/test/resources/captures/raw/VERSIONS.md`
avec la nouvelle version observée (appareil, date, version d'app).

### 3. Regarder ce que contient la capture

Ces deux commandes marchent sur un fichier `.json` (adapter le grep pour un `.xml`, les noms des
attributs `id`/`text`/`content-desc` diffèrent légèrement) :

```bash
grep -o '"id": "[^"]*"' <capture>.json | sort | uniq -c | sort -rn | head
grep -E '"(text|contentDesc)": "[^"]+"' <capture>.json | sort -u
```

La première liste les identifiants de vue présents (utile pour Instagram, en vues classiques) ;
la seconde liste les libellés visibles ou lus à voix haute (utile pour X, en Compose, qui n'a pas
d'identifiants — voir piège n°2 ci-dessus).

### 4. Mettre à jour les tests

Dans `rules/src/test/kotlin/rehab/rules/catalog/InstagramRulesTest.kt` ou
`TwitterRulesTest.kt`, faire pointer les tests concernés vers les nouveaux fichiers de capture (ou
les ajouter à côté des anciens si les deux versions d'interface doivent rester supportées en même
temps, par exemple pendant un déploiement progressif de la nouvelle version d'Instagram/X).

### 5. Corriger les matchers

Modifier `rules/src/main/kotlin/rehab/rules/catalog/InstagramRules.kt` ou `TwitterRules.kt`
jusqu'à ce que la commande suivante passe entièrement au vert :

```bash
export JAVA_HOME="$LOCALAPPDATA/Programs/jdk17"
export PATH="$JAVA_HOME/bin:$LOCALAPPDATA/Android/Sdk/platform-tools:$PATH"
./gradlew :rules:test
```

Les briques disponibles dans `Matcher.kt` : `ViewId` (identifiant de vue), `ContentDesc`/`Text`
(libellé visible ou lu, avec une expression régulière), `ClassName`, `Selected` (onglet actif),
`AnyOf`/`AllOf` (au moins une / toutes les conditions), `Within` (un nœud contenu dans un autre),
`NearBottom` (position en bas d'écran, voir piège n°1). Un `TargetRule` combine
`screenMatchers` (tous doivent matcher pour reconnaître l'écran) et, optionnellement,
`triggerMatchers` (au moins un doit matcher pour activer réellement le blocage — sert par exemple
à ne bloquer un post « Suggestions pour vous » que quand ce libellé ou ce bouton « Suivre » est
visible, pas tout l'accueil).

### 6. Étendre `testedVersions`

Une fois les tests verts, élargir la plage de versions couverte dans `PACKAGE_RULES` (champ
`testedVersions = VersionRange(min, maxExclusif)`) pour inclure la version réellement observée sur
le téléphone, et documenter cette version dans
`rules/src/test/resources/captures/raw/VERSIONS.md`. **Toujours monter la borne, jamais la
descendre** : une version déjà couverte ne doit pas redevenir « hors plage » par erreur.

### 7. Vérifier sur le téléphone et commiter

```bash
./gradlew :app:installDebug
```

Ouvrir chaque écran corrigé et vérifier dans Debug > Dernière détection que l'écran et la cible
attendus s'affichent. Puis commiter (voir la convention du dépôt : message `type: description` en
français, sans trailer).

## Principes à respecter en toute circonstance

- Toujours prévoir au moins un `ViewId` **et** une alternative `ContentDesc`/`Text` par règle
  quand c'est possible (Instagram le permet ; X, en Compose, s'appuie uniquement sur le contenu
  visible et la position, voir piège n°2).
- **Jamais modifier un test pour le faire passer** sans avoir vérifié que la règle correspond
  vraiment à la capture — un test modifié à la légère cache un vrai problème de détection.
- Une règle qui matche **trop** (par exemple, une conversation privée détectée comme le fil
  principal) est pire qu'une règle qui ne matche pas du tout : le mode dégradé (fail-closed)
  couvre déjà le second cas en avertissant l'utilisateur, alors que le premier bloquerait ou
  débloquerait le mauvais écran silencieusement.

## Ajouter une application

1. Capturer ses écrans cibles et non cibles comme ci-dessus (section « Capturer les écrans »).
2. Créer `rules/src/main/kotlin/rehab/rules/catalog/<App>Rules.kt` sur le modèle de
   `TwitterRules.kt` (le plus simple des deux catalogues), avec un `TargetId` par écran à bloquer.
3. L'ajouter à la liste de `DefaultCatalog.create()` (`rules/src/main/kotlin/rehab/rules/DefaultCatalog.kt`).
4. Ajouter le nom du package dans `android:packageNames` de
   `app/src/main/res/xml/accessibility_service_config.xml`, et dans les `<queries>` du
   `AndroidManifest.xml` (nécessaire pour que `VersionChecker` puisse lire sa version installée).
5. Écrire les tests sur fixtures (`rules/src/test/kotlin/rehab/rules/catalog/`), puis dérouler la
   checklist manuelle sur le nouvel écran. Aucun changement n'est nécessaire dans `domain`,
   l'overlay ou le reste de l'interface : c'est tout l'intérêt de la séparation en modules — une
   nouvelle application n'ajoute que des règles de détection.
