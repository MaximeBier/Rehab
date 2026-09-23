# Rehab — checklist de test manuel

Ce document sert à valider l'application sur le téléphone avant de considérer une version comme
fiable. Il ne demande aucune connaissance de programmation : chaque commande est à copier telle
quelle dans un terminal (après avoir exécuté la ligne d'environnement ci-dessous une fois par
session de terminal).

## Préparation

```bash
export JAVA_HOME="$LOCALAPPDATA/Programs/jdk17"
export PATH="$JAVA_HOME/bin:$LOCALAPPDATA/Android/Sdk/platform-tools:$PATH"
./gradlew :app:installDebug
adb logcat -s Rehab:*
```

Le Pixel doit être joignable (USB `29081JEGR09520` ou `adb connect 192.168.1.10:5555`).

Garder la fenêtre `adb logcat -s Rehab:*` ouverte pendant toute la session : Rehab n'y écrit
qu'en cas de problème réel (tag `Rehab`, niveau erreur). Deux messages à surveiller :
- `Erreur moteur` (avec une pile Java en dessous) : le moteur de détection a rencontré une
  exception pendant l'analyse de l'écran — le cas le plus probable est un scroll rapide qui
  invalide l'arbre d'accessibilité en cours de lecture. **Une seule ligne isolée qui n'empêche
  pas la suite de fonctionner n'est pas bloquant** (elle est aussi enregistrée dans le Journal
  sous « Erreur : … », preuve que le rattrapage a fonctionné) ; des lignes répétées ou suivies
  d'un blocage de l'app sont un vrai défaut à signaler.
- `Overlay impossible` : le système a refusé d'afficher la fenêtre de blocage (permission overlay
  retirée par exemple). Le service revient alors sur l'écran précédent de l'app bloquée au lieu
  d'afficher un overlay cassé — normal, mais à vérifier que ça n'arrive jamais en usage courant.

Avant de commencer, remettre les réglages à leurs valeurs par défaut si une session précédente les
a modifiés (onglet Réglages) : nuits/quotas normaux, joker 5 min, relapse 15 min, 2 jokers/jour,
appui 10 s.

---

## 1. Priorité absolue : l'overlay ne doit jamais rester bloqué à l'écran

C'est le pire défaut possible du projet : un overlay qui reste affiché sans qu'on puisse
l'enlever rendrait le téléphone inutilisable. Provoquer un blocage (quota ou nuit, voir sections
3 et 5) puis, à chaque fois, vérifier que l'overlay **disparaît de lui-même** :

- [ ] **Désactiver le service pendant un blocage affiché** : Réglages Android > Accessibilité >
  Rehab > désactiver, overlay visible. L'overlay doit disparaître immédiatement (ou au plus dans
  la seconde qui suit).
- [ ] **Tuer l'app bloquée pendant un blocage affiché** : depuis la vue multitâche, glisser
  Instagram (ou X) pour le fermer pendant que l'overlay est visible. L'overlay doit disparaître
  dans la seconde qui suit (le service revérifie l'app au premier plan chaque seconde tant qu'un
  blocage est actif).
- [ ] **Éteindre l'écran pendant un blocage affiché** : bouton power, rallumer. L'overlay ne doit
  pas réapparaître tel quel : soit l'écran de verrouillage s'affiche normalement, soit on retombe
  sur le blocage recalculé à l'instant présent (pas figé sur l'ancien compte à rebours).
- [ ] Dans les trois cas, réactiver le service et confirmer qu'un nouveau blocage fonctionne
  normalement ensuite (pas de service resté dans un état incohérent).

## 2. Pas d'erreur moteur en scroll rapide

- [ ] Instagram > Reels : scroller très vite (doigt rapide, plusieurs écrans à la suite) pendant
  30 s. Aucune ligne « Erreur moteur » répétée dans logcat, l'app Instagram ne plante pas,
  l'overlay (si un quota est actif) réagit normalement dans la seconde qui suit l'arrêt du scroll.
- [ ] X > fil « Pour vous » : même test.
- [ ] Vérifier dans le Journal (onglet Journal) qu'il n'y a pas de multiplication de lignes
  « Erreur : … » sur cette période.

## 3. Détection d'écran

- [ ] Instagram > Reels : Debug > Dernière détection = écran `instagram.reels`, cible
  `InstagramReels`.
- [ ] Instagram > Accueil, haut du fil : écran `instagram.home`, cible `aucune`, Inconnu = false.
- [ ] Instagram > Accueil, scroller jusqu'à un post « Suggestions pour vous » ou un bouton
  « Suivre » sur un post : cible `InstagramSuggested`.
- [ ] Instagram > DM, profil, recherche : cible `aucune`, Inconnu = false (écrans reconnus mais
  non bloqués).
- [ ] X > Accueil (onglets « Pour vous » et « Abonnements ») : cible `TwitterHome`.
- [ ] X > Recherche, Messages, Notifications, Grok : cible `aucune`, Inconnu = false.

## 4. Débounce et ticker

- [ ] Scroller en continu dans Reels pendant 10 s sans s'arrêter : Debug > Dernière détection ne
  doit pas se rafraîchir à chaque frame (le débounce de 300 ms attend une pause dans les
  événements avant de relancer l'analyse) — la date affichée avance par à-coups, pas en continu.
- [ ] Un blocage actif (quota ou nuit) : le compte à rebours de l'overlay avance chaque seconde
  tant qu'on reste sur l'écran bloqué (ticker).
- [ ] Quitter l'écran bloqué (revenir à l'accueil du téléphone, ou changer d'onglet Instagram vers
  un écran libre) : l'overlay disparaît et rien ne doit plus tourner en arrière-plan de façon
  visible (voir aussi la vérification batterie en section 8).

## 5. Quota

- [ ] Réglages : première fenêtre à 1 min sur 5 min, enregistrer.
- [ ] Reels pendant 1 min : overlay « Quota atteint · 4 min xx s », compte à rebours vivant.
- [ ] La barre d'onglets Instagram reste visible et cliquable sous l'overlay ; taper « Profil »
  fait disparaître l'overlay (l'overlay ne couvre que la zone au-dessus de la barre du bas).
- [ ] Revenir sur Reels : overlay immédiat (moins d'une seconde).
- [ ] Attendre la fin du compte à rebours : overlay disparaît seul.
- [ ] Accueil Rehab : la ligne de quota reflète l'usage (« 1 / 1 min sur 5 min »).

## 6. Bouton unique — joker et relapse

- [ ] Sur l'overlay, libellé « Maintenir 10 s · Joker +5 min · 1 restant ».
- [ ] Appuyer 5 s puis relâcher : la jauge revient à zéro, rien ne se passe.
- [ ] Appuyer 10 s en continu : overlay disparaît, Reels visibles, Accueil Rehab « Déblocage en
  cours jusqu'à … », Journal « Joker (+5 min) ».
- [ ] Refaire un blocage puis un appui 10 s : libellé « Maintenir 10 s · Joker +5 min · dernier »
  (2ᵉ joker de la journée).
- [ ] Un 3ᵉ blocage : libellé rouge « Maintenir 10 s · RELAPSE · ton streak de N jours tombe ».
- [ ] Appui 10 s sur ce libellé rouge : Journal « RELAPSE (+15 min) », Accueil streak = 0, le
  record affiché est conservé (pas remis à zéro).

## 7. Nuit

- [ ] Réglages : ligne du jour courant avec coucher = heure actuelle + 1 min, lever = +5 min.
  Enregistrer.
- [ ] À l'heure du coucher : Reels → overlay « Nuit · déblocage à HH:mm ». Le bouton propose
  directement RELAPSE (pas de joker la nuit — voir `UnlockPolicy` : la nuit interdit le joker).
- [ ] Pendant que la nuit est en cours, retourner dans Réglages : un cadenas 🔒 apparaît sur la
  ligne du jour verrouillé, avec le message « Plage en cours : seulement allongeable. Modifiable à
  partir de HH:mm. » ; essayer de raccourcir la plage → refus (message avec l'heure à partir de
  laquelle ce sera modifiable) ; l'allonger → accepté ; modifier un autre jour → accepté.
- [ ] Au lever : overlay disparaît seul.
- [ ] Remettre les horaires normaux du jour testé.

## 8. Cycle de vie

- [ ] Reels visibles (libre), éteindre l'écran, rallumer : le Journal montre un intervalle
  d'usage fermé au moment de l'extinction (pas un intervalle qui continue à courir).
- [ ] Redémarrer le téléphone : sans ouvrir Rehab, aller sur Reels après quota déjà atteint →
  overlay s'affiche (le service est revenu seul après le redémarrage, sans action de
  l'utilisateur).
- [ ] Désactiver le service dans les réglages Android : Journal « Service désactivé », bandeau
  rouge « Rehab est inactif : active le service d'accessibilité. » sur l'Accueil, l'écran
  d'onboarding réapparaît au relancement de l'app.
- [ ] Rester 30 min hors Instagram/X (app fermée) :
  `adb shell dumpsys batterystats --checkin | grep rehab.app`
  ne doit montrer ni wakelock actif ni consommation CPU notable (le service ne traite que les
  événements des deux apps surveillées et s'arrête dès qu'on les quitte).

## 9. Écran Réglages

- [ ] Saisir une valeur invalide (ex. lettres dans un champ minutes, heure `25:99`) : refus avec
  un message d'erreur en français, rien n'est enregistré.
- [ ] Inverser par erreur les champs « Max » et « Sur » d'une fenêtre de quota : les valeurs vont
  bien dans les bonnes cases (le champ « Max » est le plafond, « Sur » la durée de la fenêtre).
- [ ] Les verrous 🔒 (nuit ou quota en cours) apparaissent sans qu'il soit nécessaire de quitter
  l'écran et d'y revenir : rester sur Réglages au moment où une nuit ou un quota démarre, le
  cadenas doit apparaître dans la seconde qui suit.

## 10. Écran Journal

- [ ] Les événements (Joker, RELAPSE, Service activé/désactivé, erreurs) et les intervalles
  d'usage apparaissent triés dans l'ordre chronologique.
- [ ] « Service activé » est bien présent après une activation du service d'accessibilité.
- [ ] Le Journal ne se rafraîchit qu'à l'ouverture de l'onglet : changer d'onglet puis revenir sur
  Journal pour voir les événements les plus récents.

## 11. Écran Debug

- [ ] « Capturer dans 5 s », puis ouvrir un écran cible dans Instagram/X avant l'échéance : un
  fichier JSON est écrit (visible via « Dernière capture : … ») dans `files/captures` de
  l'application.
- [ ] « Partager » : le sélecteur de partage Android s'ouvre (test avec Drive ou Mail par
  exemple) et l'envoi aboutit.
- [ ] « Afficher un overlay de test (5 s) » : un overlay factice s'affiche par-dessus Rehab
  lui-même et disparaît tout seul après 5 s, sans action de l'utilisateur.

## 12. Onboarding

- [ ] Case « Service d'accessibilité activé » : l'activer dans les réglages Android, revenir dans
  Rehab (bouton Retour ou multitâche) → la case se coche automatiquement au retour au premier
  plan, sans avoir besoin de relancer l'app.
- [ ] Bouton « Autoriser » sur la ligne notifications déclenche bien la demande de permission
  système.
- [ ] Désactiver le service d'accessibilité alors que l'onboarding est déjà passé (app en cours
  d'utilisation normale) : l'écran d'onboarding réapparaît au prochain retour au premier plan (le
  bouton « Continuer » redevient nécessaire).

**Piège connu, pas un bug** : les lignes Instagram/X de l'onboarding (« Instagram reconnue »,
« Instagram hors plage testée », etc.) restent vides tant que le service d'accessibilité n'a
jamais été activé (ou pendant le bref instant où le système est en train de le connecter) — ces
informations viennent d'une vérification de version faite une fois à la connexion du service
(`VersionChecker.checkAll()`, appelée depuis `onServiceConnected()`), une simple lecture du
gestionnaire de paquets Android, sans rapport avec l'ouverture d'Instagram ou de X. Elles
apparaissent dès l'activation du service, sans qu'il soit nécessaire d'ouvrir les deux apps.

## 13. Refonte visuelle

- [ ] **Polices** : Chivo (texte courant) et Chivo Mono (chiffres — anneau, jauges, minuteur de
  l'overlay) bien visibles partout, pas de retour à une police système.
- [ ] **Taille de police Android à 130 %** (Réglages Android > Affichage > Taille de police) :
  overlay (titre, légende deux lignes complètes, bouton), barre d'onglets (les quatre libellés,
  « RÉGLAGES » compris, ne sont jamais coupés), Réglages, Journal — rien de coupé ni chevauché.
  Remettre la taille par défaut ensuite.
- [ ] **Accueil** : pastille de statut, anneau record (plein) ou arc hors record (partiel avec
  ancien record en fond), jauges de quota, ligne « prochaine nuit », bandeaux ACTIVER (service
  inactif) et DEBUG (app hors plage/écrans non reconnus) s'affichent correctement.
- [ ] **Réglages** : un changement est appliqué immédiatement (pas de bouton « Enregistrer »
  global) ; la ligne verrouillée (nuit en cours ou quota en cours) n'est pas cliquable et affiche
  sa raison ; un refus de saisie (ex. raccourcir une plage en cours) est affiché dans le dialogue
  d'édition lui-même, pas seulement en toast.
- [ ] **Journal** : les événements sont groupés par journée Rehab (pas par jour calendaire) et les
  lignes de blocage (Joker/RELAPSE) apparaissent avec leur typologie colorée.
- [ ] **Debug** : le délai de capture programmé s'affiche et compte à rebours ; l'export/partage
  d'une capture fonctionne.
- [ ] **Overlay** : un appui court (relâché avant la fin) n'a aucun effet et relâcher ramène la
  jauge à zéro instantanément ; en relapse, le fond rougit et l'écran tremble à l'approche de la
  fin de l'appui ; l'état terminé (bouton plein) reste affiché 1,5 s avant de disparaître ou de se
  réinitialiser ; « Quitter » ramène bien à l'écran d'accueil du téléphone (pas un simple retour
  arrière).

## 14. Onglet Stats (v0.4.0)

- [ ] **Permission absente** : après une première installation (ou révocation manuelle dans
  Réglages Android > Applications > Accès spécial > Accès aux données d'utilisation), l'onglet
  Stats affiche un bandeau « Autoriser l'accès aux données d'utilisation » avec action OUVRIR, et
  les lignes « Avant Rehab »/« Maintenant » affichent « accès requis » (la ligne « Dont … » reste
  affichée, mesurée par Rehab indépendamment de cette permission).
- [ ] **Accorder la permission** : taper OUVRIR ouvre bien l'écran système d'accès aux données
  d'utilisation ; y activer Rehab, revenir dans l'app (bouton Retour) → l'onglet Stats se
  rafraîchit sans avoir besoin de changer d'onglet, le bandeau disparaît et les valeurs Android
  s'affichent.
- [ ] **Cartes Instagram/X** : « Avant Rehab » (historique Android sur 28 jours avant
  l'installation, ou la saisie manuelle si renseignée dans Réglages), « Maintenant » (7 derniers
  jours, app entière), « Dont fil et Reels » pour Instagram / « Dont le fil » pour X (mesure
  Rehab — X n'a pas de Reels), « Écart » en pourcentage (signe « − » ou « + ») — vert/accent si
  baisse, rouge/danger si hausse, « — » si l'avant est inconnu. Sur ces lignes, seuls les chiffres
  sont en Chivo Mono (« Android »/« saisi » en texte courant).
- [ ] **Bloc Total** : somme Instagram + X, même forme que les cartes par app, ligne « Dont fils et
  Reels ».
- [ ] **Réglages > Avant Rehab** : une ligne par app (« 45 min/j » si saisie, « 38 min/j · Android »
  si déduite de l'historique, « à renseigner » sinon) ; dialogue numérique (minutes/jour), vide =
  revenir à la valeur Android ; la valeur saisie se reflète immédiatement dans l'onglet Stats.
- [ ] **Barre d'onglets à 5 entrées** : à 100 % et 130 % de police système, les 5 libellés
  (Accueil, Réglages, Journal, Stats, Debug) restent lisibles, aucun chevauchement ni coupure.

---

Notes de session : consigner ici les écarts trouvés, avec la commande logcat/adb correspondante,
pour faciliter le passage au [guide de mise à jour des règles](rules-update-guide.md) si l'écart
vient d'une détection d'écran.
