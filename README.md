# Photo Parallax IA — Prototype offline (Android)

Application prototype qui transforme une photo en courte vidéo animée grâce à
un **effet parallax 3D piloté par une IA de profondeur (MiDaS)**, entièrement
**hors ligne** : aucune connexion réseau requise à l'exécution.

## Comment ça marche

1. L'utilisateur choisit une photo.
2. Un modèle **MiDaS v2 small** (TensorFlow Lite) tourne localement sur le
   téléphone pour estimer la profondeur de chaque pixel.
3. L'image est découpée en 6 "tranches" de profondeur. Pendant le rendu vidéo,
   chaque tranche est décalée proportionnellement à sa profondeur pendant que
   la "caméra virtuelle" décrit un léger mouvement circulaire → illusion de
   parallax / mouvement de caméra 3D.
4. Les frames sont encodées directement en MP4 via `MediaCodec` +
   `MediaMuxer` (API Android native, pas de FFmpeg).

Tout se passe sur l'appareil : pas d'appel API, pas de cloud.

## ⚠️ Étape manquante à faire toi-même : le modèle IA

Je n'ai pas pu embarquer le fichier binaire du modèle dans ce projet. Il faut :

1. Télécharger un modèle **MiDaS v2 small quantifié TFLite** (~16 Mo). Sources possibles :
   - Le dépôt officiel Intel ISL MiDaS (github.com/isl-org/MiDaS) propose des
     exports ONNX/TFLite selon les versions.
   - Ou reconvertir toi-même un modèle MiDaS via `tf.lite.TFLiteConverter`
     depuis un export ONNX/TensorFlow.
2. Renommer le fichier `midas_small.tflite`.
3. Le placer dans `app/src/main/assets/midas_small.tflite`.

Sans ce fichier, l'app plante au lancement de `DepthEstimator` (le
`context.assets.openFd(...)` échouera).

## Build local

```bash
./gradlew assembleDebug
```

Ouvre simplement le dossier dans Android Studio (Koala ou plus récent), il
détectera le projet Gradle automatiquement. `minSdk = 26`.

## Build automatique via GitHub Actions (sans Android Studio)

Le projet inclut `.github/workflows/build.yml`, qui compile l'APK
automatiquement sur les serveurs de GitHub à chaque push.

**Étapes :**

1. Crée un nouveau dépôt sur GitHub (public ou privé).
2. Pousse le projet :
   ```bash
   cd PhotoParallaxAI
   git init
   git add .
   git commit -m "Premier commit"
   git branch -M main
   git remote add origin https://github.com/<ton-utilisateur>/<ton-repo>.git
   git push -u origin main
   ```
3. ⚠️ Pense à ajouter `app/src/main/assets/midas_small.tflite` (le modèle IA,
   voir section précédente) **avant** de pousser, sinon l'app compilera mais
   plantera au lancement.
4. Va dans l'onglet **Actions** de ton dépôt GitHub : le workflow
   "Build APK" se lance automatiquement.
5. Une fois le workflow terminé (icône verte ✅), clique dessus puis
   descends jusqu'à **Artifacts** → télécharge `app-debug-apk.zip`.
6. Dézippe : tu obtiens `app-debug.apk`, installable directement sur un
   téléphone Android (active "Sources inconnues" dans les paramètres pour
   l'installer hors Play Store).

**Notes :**
- Ce build produit un **APK debug non signé pour le Play Store** (signature
  debug automatique) — suffisant pour tester sur ton téléphone.
- Pour une release signée (Play Store), il faudrait ajouter un keystore et
  des secrets GitHub (`signingConfigs` + `secrets.KEYSTORE_*`) — dis-moi si
  tu veux que je l'ajoute.
- Le fichier modèle `.tflite` (~16 Mo) peut être commité normalement dans un
  petit dépôt ; au-delà de 100 Mo GitHub demande Git LFS.

## Limitations connues (prototype)

- **Artefacts de bord** : la séparation en tranches de profondeur peut créer
  de légers trous/dédoublements sur les contours à forte discontinuité de
  profondeur (typique des approches parallax simples sans inpainting). Une
  version plus avancée utiliserait un maillage déformable (mesh warp) rendu
  en OpenGL plutôt que des tranches plates.
- **Qualité dépendante de la photo** : les photos avec un sujet net au
  premier plan et un arrière-plan flou/éloigné donnent les meilleurs
  résultats (portraits, paysages avec profondeur de champ).
- **Performance** : l'inférence MiDaS + le rendu de 90 frames (3s à 30fps)
  peuvent prendre plusieurs secondes sur un milieu de gamme. Le delegate GPU
  est activé automatiquement si le device le supporte.
- **Pas de génération "texte → vidéo"** : ceci reste hors de portée du
  offline mobile actuel (modèles de diffusion vidéo = plusieurs Go de VRAM).

## Pistes d'évolution

- Remplacer le parallax par tranches par un **mesh warp** (grille déformée
  selon la depth map, rendue en OpenGL ES) pour un rendu plus propre.
- Ajouter de l'**inpainting léger** sur les zones découvertes par le
  déplacement de calque.
- Exposer les réglages (durée, amplitude du mouvement, trajectoire) dans
  l'UI.
- Tester d'autres modèles de depth-estimation plus légers (ex. variantes
  distillées de Depth Anything) si la taille/latence de MiDaS small pose
  problème sur bas de gamme.
