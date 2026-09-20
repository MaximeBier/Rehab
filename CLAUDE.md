# Rehab — consignes projet

- Commits : messages `type: description` en français. **Aucun trailer `Co-Authored-By`** : l'auteur est uniquement Maxime (préférence explicite, 2026-09-20).
- Spec : `docs/superpowers/specs/2026-09-19-rehab-v1-design.md`. Plan : `docs/superpowers/plans/2026-09-19-rehab-v1.md`.
- Environnement : JDK 17 dans `%LOCALAPPDATA%\Programs\jdk17`, SDK Android dans `%LOCALAPPDATA%\Android\Sdk` (pas d'Android Studio). `JAVA_HOME`/`ANDROID_HOME` exportés par `~/.bashrc`.
- Modules `domain` et `rules` : Kotlin JVM pur, aucun import `android.*`.

## Reprendre le chantier V1

1. Lire le ledger `.superpowers/sdd/2026-09-19-rehab-v1/progress.md` (position, rulings, minors parqués). Ne jamais re-dispatcher une tâche marquée `complete`.
2. Invoquer `superpowers:subagent-driven-development` avec le plan `docs/superpowers/plans/2026-09-19-rehab-v1.md` ; les briefs sont déjà extraits dans le même dossier que le ledger.
3. Travail directement sur `main`, push après chaque lot revu. À partir de la tâche 22, `./gradlew :app:installDebug` sur le Pixel après chaque tâche revue.
4. Shell non-login : `export JAVA_HOME="$LOCALAPPDATA/Programs/jdk17"; export PATH="$JAVA_HOME/bin:$LOCALAPPDATA/Android/Sdk/platform-tools:$PATH"` avant Gradle/adb. Pixel : USB `29081JEGR09520` ou `adb connect 192.168.1.10:5555`.
