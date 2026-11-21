# Calories — calculateur de calories Android

Version actuelle : **1.2** (versionCode 3). Historique : 1.0 → première version ; 1.1 → suivi des prébiotiques ; 1.2 → correction du calcul des calories (remplissage automatique), aperçu en direct, 29 aliments en plus (dont plats et produits des Antilles), mise en page corrigée.

Application Android simple, **hors ligne et sans compte**, pour noter ce que tu manges et suivre tes calories de la journée.

## Plan / fonctionnalités (v1.0)

1. **Ajouter un aliment ou un repas**
   - Nom de l'aliment, quantité en grammes, kcal pour 100 g → calcul automatique : `kcal = grammes × kcal/100g ÷ 100`.
   - Liste intégrée de 79 aliments (riz blanc / complet, pâtes, pain, banane, poulet, œuf… et produits des Antilles : igname, banane plantain, christophine, fruit à pain, dachine/madère, morue, accras, goyave, coco, pois d'Angole, colombo… dont 8 fermentés et 12 riches en prébiotiques), avec kcal, macros et nutriments approximatifs, avec **autocomplétion** (la recherche ignore les accents).
   - **Remplissage automatique (v1.2)** : dès que le nom tapé correspond à un aliment de la liste (sans tenir compte des majuscules ni des accents ; nom exact en priorité, puis début de nom, puis mot contenu), les kcal, macros, nutriments et cases pro/prébiotiques se remplissent tout seuls, sans devoir toucher la suggestion. La ligne « Valeurs de la liste : … » indique l'aliment reconnu. Des valeurs tapées à la main ou venant d'un scan ne sont jamais écrasées.
   - **Aperçu en direct** : `= X kcal · G y g · P z g · L w g` pendant la saisie. Si l'aliment est inconnu et les kcal/100 g vides, message « Aliment inconnu : saisis les kcal pour 100 g ou scanne le code-barres » et le bouton Ajouter reste désactivé (plus d'ajout silencieux à 0 kcal).
   - **Saisie libre** possible pour n'importe quel aliment (tu tapes toi-même les kcal/100 g, lisibles sur l'étiquette).
2. **Total du jour** avec **objectif journalier** (2000 kcal par défaut, modifiable via le bouton « Objectif ») et **barre de progression** (rouge si dépassé).
3. **Historique** : entrées regroupées par jour (Aujourd'hui, Hier, puis dates), total par jour, **suppression** d'une entrée (icône corbeille + confirmation).
4. **Profil** : âge, poids (kg), taille (cm), sexe (homme/femme), niveau d'activité (sédentaire, léger, modéré, actif, très actif), enregistrés sur le téléphone.
   - Objectif suggéré = **métabolisme de base Mifflin-St Jeor × facteur d'activité** (1,2 / 1,375 / 1,55 / 1,725 / 1,9).
     - Homme : `10 × poids + 6,25 × taille − 5 × âge + 5`
     - Femme : `10 × poids + 6,25 × taille − 5 × âge − 161`
   - Bouton **« Utiliser comme objectif »** pour l'appliquer ; l'objectif reste modifiable à la main. Profil vide → 2000 kcal par défaut.

5. **Scanner de code-barres** (EAN-13, EAN-8, UPC-A, UPC-E) : bouton « 📷 Scanner » sur l'écran d'ajout.
   - Utilise le scanner Google (`play-services-code-scanner`) : **aucune permission caméra** demandée par l'appli, fonctionne sur les Samsung avec Google Play services (le module de scan est téléchargé automatiquement par Play services).
   - Champ « Code-barres » pour une **saisie manuelle** si le scan échoue.
   - Recherche du produit sur **Open Food Facts** (`/api/v2/product/{code}.json`) : pré-remplit le nom et les kcal/100 g (`energy-kcal_100g`, sinon `energy_100g` kJ ÷ 4,184), affiche protéines / glucides / lipides pour 100 g et la portion indiquée. Tu saisis ensuite les grammes et tu ajoutes.
   - Messages en français si le produit est introuvable, si les calories manquent ou s'il n'y a pas de connexion.
6. **Photos de repas** : boutons « 📸 Photo » (appareil photo, via `ActivityResultContracts.TakePicture`) et « 🖼 Galerie » (sélecteur de photos Android, `PickVisualMedia`).
   - Photos enregistrées dans le stockage privé de l'appli (`filesDir/photos`, partagé avec l'appareil photo via un `FileProvider`) : **aucune permission caméra ni stockage**.
   - Redimensionnées (1280 px max, rotation EXIF corrigée) et compressées en JPEG 80 %.
   - Miniature dans la liste du jour et l'historique ; un appui l'ouvre en plein écran. Supprimer une entrée supprime aussi sa photo.
   - La reconnaissance automatique des aliments sur photo n'est **pas** incluse en v1.0.
7. **Macronutriments** : glucides / protéines / lipides pour 100 g (pré-remplis depuis la liste ou Open Food Facts, facultatifs en saisie libre).
   - Chaque entrée affiche ses grammes de G/P/L ; l'écran du jour montre les totaux et une barre de répartition des kcal (4 kcal/g glucides et protéines, 9 kcal/g lipides) ; l'historique montre les totaux par jour.
8. **Nutriments** (onglet 🥦) : fibres, sucres, sel, graisses saturées, calcium, fer, vitamine C, vitamine D (pour 100 g, champs facultatifs « Nutriments… » ; pré-remplis depuis la liste ou Open Food Facts, minéraux et vitamines convertis de g en mg / µg).
   - Totaux du jour avec barres de progression vs repères adultes : fibres 30 g, calcium 950 mg, fer 11 mg (homme) / 16 mg (femme, ou si sexe non renseigné), vitamine C 110 mg, vitamine D 15 µg ; limites : sucres < 10 % des kcal (50 g pour 2000 kcal), sel < 5 g, graisses saturées < 10 % des kcal.
   - Moyenne sur 7 jours (sur les jours ayant des entrées) avec indication douce « apport faible » (< 70 % du repère) ou « au-dessus de la limite ».
   - Mention « données incomplètes » quand des entrées n'ont pas certaines valeurs. Indicatif, ne remplace pas un avis médical. Aucune notification.
9. **Probiotiques** : case « Fermenté / probiotique » sur chaque entrée (pré-cochée pour les aliments fermentés de la liste, et automatiquement après un scan si les catégories / labels / nom Open Food Facts évoquent yaourt, kéfir, kimchi, choucroute, probiotique, kombucha…).
   - Carte « Probiotiques » dans Nutriments : nombre de jours sur 7 avec au moins un aliment fermenté, suggestion douce si 0 à 2 jours, fiche d'info (sources, effet de la pasteurisation/cuisson, sucres du kombucha, consulter un médecin avant des compléments, surtout si immunodéprimé). Pas de suivi par souche.

Les données sont stockées localement (SharedPreferences + JSON). La seule connexion Internet sert à interroger Open Food Facts quand tu scannes ou saisis un code-barres (permission INTERNET). Les photos restent sur le téléphone. Si tu désinstalles l'appli, les données et photos sont supprimées.
Les valeurs caloriques sont approximatives et l'objectif suggéré n'est pas un avis médical.

10. **Prébiotiques** (v1.1) : case « Riche en prébiotiques » sur chaque entrée (les anciennes entrées valent « non »).
    - Pré-cochée pour : ail, oignon, poireau, asperge, artichaut, topinambour, banane peu mûre, flocons d'avoine, lentilles, pois chiches, chicorée soluble.
    - Après un scan, cochée automatiquement si le nom, les catégories ou les ingrédients Open Food Facts mentionnent inuline, FOS, GOS, chicorée, ou s'il s'agit d'un produit à base de légumineuses ou d'avoine (décochable).
    - Carte « Prébiotiques » dans Nutriments : jours sur 7 avec au moins un aliment prébiotique, ligne **Synergie** (jours avec à la fois un probiotique et un prébiotique), suggestion douce si 0 à 2 jours, fiche d'info (inuline, FOS, GOS, amidon résistant ; sources ; féculents cuits puis refroidis ; augmenter progressivement ; prudence FODMAP en cas de SII ; pas un avis médical).
    - Icônes dans les listes : 🦠 = fermenté/probiotique, 🌱 = prébiotique.

## Installer sur ton Samsung (APK)

1. Télécharge `Calories-v1.0.apk` sur le téléphone.
2. Ouvre-le depuis l'appli **Mes fichiers** (ou la notification de téléchargement).
3. Si Android bloque : **Paramètres → Applications → (navigateur ou Mes fichiers) → Installer des applis inconnues → Autoriser**.
4. Appuie sur **Installer**. Si Play Protect affiche un avertissement (appli non publiée sur le Play Store), choisis « Installer quand même ».
5. **Mise à jour** : installe simplement le nouvel APK (ex. `Calories-v1.2.apk`) par-dessus, sans désinstaller. Même signature, donc tes données sont conservées.
6. Les mises à jour futures seront signées avec la même clé : elles s'installeront par-dessus sans perdre tes données.

## Structure du projet

```
calorie-app/
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── gradlew, gradle/wrapper/         ← Gradle 8.7 (wrapper)
└── app/
    ├── build.gradle.kts             ← config Android, signature release
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/wolnerman/calories/
        │   ├── MainActivity.kt      ← interface Jetpack Compose (Aujourd'hui, Historique, Nutriments, Profil)
        │   ├── FoodMatch.kt         ← recherche d'aliment (accents, partiel) et calcul kcal/macros
        │   ├── Foods.kt             ← liste intégrée d'aliments, nutriments, repères journaliers
        │   └── Storage.kt           ← stockage local, profil, calcul Mifflin-St Jeor
        └── res/                     ← icône, nom de l'appli, xml/file_paths.xml (FileProvider)
```

Technique : Kotlin, Jetpack Compose (Material 3), minSdk 24 (Android 7), targetSdk 34, AGP 8.5.2, Kotlin 1.9.24, JDK 17.
Package : `com.wolnerman.calories`.

Tests unitaires (JVM) : `app/src/test/java/com/wolnerman/calories/FoodMatchTest.kt`, lancés avec `./gradlew testReleaseUnitTest`.

## Recompiler

Prérequis (déjà installés sur la machine de build) : JDK 17 (`/home/box/jdk-17`), SDK Android (`/home/box/android-sdk`).

```bash
export JAVA_HOME=/home/box/jdk-17
export ANDROID_HOME=/home/box/android-sdk
./gradlew assembleRelease
# APK signé : app/build/outputs/apk/release/app-release.apk
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

Note : avec minSdk 24, Gradle ne signe qu'en v2 (suffisant pour Android 7+). Les APK livrés (`Calories-v1.0.apk`, `Calories-v1.1.apk`, `Calories-v1.2.apk`) ont été re-signés avec `apksigner sign --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true` pour inclure aussi v1 et v3.

La signature lit `/home/box/android-keys/keystore.properties` (ou le chemin dans la variable `CALORIES_KEYSTORE_PROPS`).
**Garde le keystore et son mot de passe en sécurité** : sans eux, impossible de publier une mise à jour qui s'installe par-dessus l'appli existante. Ils ne doivent jamais être mis sur GitHub (déjà exclus par `.gitignore`).
Pour une nouvelle version : augmente `versionCode` et `versionName` dans `app/build.gradle.kts`.

## Alternative Termux (aperçu)

Compiler directement sur le téléphone est possible mais plus fragile (les outils Android officiels sont prévus pour x86_64) :
- `pkg install openjdk-17 gradle git`, puis récupérer un `aapt2` compatible ARM (paquet `aapt2` de Termux) et le déclarer via `android.aapt2FromMavenOverride` dans `gradle.properties`.
- Plus simple : pousser le projet sur GitHub et laisser **GitHub Actions** compiler l'APK (keystore stocké dans les *secrets* du dépôt), puis télécharger l'APK depuis le téléphone. Termux sert alors juste à `git pull/push`.
