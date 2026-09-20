# Rehab

Application Android personnelle qui bloque les surfaces de *scroll infini* sans bloquer les apps elles-mêmes :

- les **Reels** Instagram,
- les **posts suggérés** du feed Instagram,
- l'**accueil de X** (Pour vous et Abonnements).

Le blocage est conditionnel : une **plage nocturne** réglable jour par jour, et un **quota glissant** (par défaut 5 min sur 30 min et 30 min sur 6 h). Un **bouton unique à appui long** (10 s) donne un joker de 5 minutes, deux fois par jour, puis devient un *relapse* de 15 minutes qui casse le **compteur de jours sans relapse**. La nuit, tout appui est un relapse.

## Comment ça marche

Un `AccessibilityService` filtré sur Instagram et X lit la structure de l'écran, la compare à des **règles déclaratives** (identifiants de vue, libellés) et, si une cible est visible et bloquée, affiche un overlay opaque par-dessus, en laissant la barre d'onglets de l'app accessible. Aucune donnée ne quitte le téléphone.

```
domain/   Kotlin JVM pur : horaires, quota glissant, streak, joker/relapse, verrous d'édition
rules/    Kotlin JVM pur : détection d'écrans sur un snapshot plat de l'arbre d'accessibilité
app/      Android : service d'accessibilité, overlay Compose, Room, UI de réglages
docs/     Spécification et plan d'implémentation
```

## État

En cours de développement (V1). Cible : Pixel, Android 14+, installation par APK. Voir `docs/superpowers/specs/` pour la spécification complète et `docs/superpowers/plans/` pour le plan.

## Construire

Prérequis : JDK 17 et le SDK Android (platform 37, build-tools 36). Puis :

```
./gradlew :domain:test :rules:test :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:installDebug
```
