# Rehab

Application Android personnelle qui limite le *scroll infini* sur Instagram et X sans couper les messages : tout Instagram et tout X sont comptés et bloquables, **sauf les DM** (liste des conversations et conversation ouverte).

Le blocage est conditionnel : une **plage nocturne** réglable jour par jour, et un **quota glissant** (par défaut 5 min sur 30 min et 30 min sur 6 h). Un **bouton unique à appui long** (10 s) donne un joker de 5 minutes, deux fois par jour, puis devient un *relapse* de 15 minutes qui casse le **compteur de jours sans relapse**. La nuit, tout appui est un relapse. Un onglet **Stats** compare le temps passé avant et après Rehab.

## Comment ça marche

Un `AccessibilityService` filtré sur Instagram et X lit la structure visible de l'écran, la compare à des **règles déclaratives** (identifiants de vue, libellés) et, si l'app est bloquée hors DM, affiche un overlay opaque par-dessus, en laissant la barre d'onglets de l'app accessible pour rejoindre les messages. Aucune donnée ne quitte le téléphone.

```
domain/    Kotlin JVM pur : horaires, quota glissant, streak, joker/relapse, verrous d'édition
rules/     Kotlin JVM pur : détection d'écrans sur un snapshot plat de l'arbre d'accessibilité
app/       Android : service d'accessibilité, overlay Compose, Room, UI
licenses/  Licences des polices embarquées (Chivo, Chivo Mono — OFL)
```

## Construire

Prérequis : JDK 17 et le SDK Android (platform 37, build-tools 36). Puis :

```
./gradlew :domain:test :rules:test :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:installDebug
```
