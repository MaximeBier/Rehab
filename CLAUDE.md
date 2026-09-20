# Rehab — consignes projet

- Commits : messages `type: description` en français. **Aucun trailer `Co-Authored-By`** : l'auteur est uniquement Maxime (préférence explicite, 2026-09-20).
- Spec : `docs/superpowers/specs/2026-09-19-rehab-v1-design.md`. Plan : `docs/superpowers/plans/2026-09-19-rehab-v1.md`.
- Environnement : JDK 17 dans `%LOCALAPPDATA%\Programs\jdk17`, SDK Android dans `%LOCALAPPDATA%\Android\Sdk` (pas d'Android Studio). `JAVA_HOME`/`ANDROID_HOME` exportés par `~/.bashrc`.
- Modules `domain` et `rules` : Kotlin JVM pur, aucun import `android.*`.
