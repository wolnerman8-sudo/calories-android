package com.wolnerman.calories

// FICHIER GÉNÉRÉ par tools/gen_foods.py — ne pas modifier à la main.
// Valeurs pour 100 g : Anses. 2025. Table de composition nutritionnelle des aliments Ciqual 2025.
// https://doi.org/10.57745/RDMHWY (Licence Ouverte Etalab 2.0). Recettes = calcul à partir des ingrédients Ciqual.
// Colonnes : nom|catégorie|kcal|glucides|protéines|lipides|fibres|sucres|sel|AG saturés|calcium mg|fer mg|vit C mg|vit D µg|
//            poids unitaire g|unité|alias (;)|drapeaux F/P|source|alcool g (v2.10). Champ vide = inconnu / 0.

internal object FoodData {
    // Œufs
    private const val C0 = """
Œuf entier (cru)|Œufs|140|0.1|12.8|9.8|0|0.1|0.31|2.6|77|1.75|0|2|50|œuf|oeuf;oeuf entier;oeuf cru;oeuf frais||Ciqual 22000|
Œuf dur|Œufs|134|0.5|13.5|8.6|0|0.5|0.31|3.2|41|1.72|0|1.12|50|œuf|oeuf cuit;oeuf bouilli;oeufs durs||Ciqual 22010|
Œuf à la coque|Œufs|142|1.1|12.2|9.8|0|0.8|0.2|3.1|150|1.9|0|1.28|50|œuf|oeuf coque||Ciqual 22014|
Œuf poché|Œufs|139|0.9|12.5|9.5|0|0.4|0.2|3|68|1.75|0|2|50|œuf|oeuf mollet;oeuf poche||Ciqual 22011|
Œuf au plat (avec matière grasse)|Œufs|225|1.3|13.3|18.5|0|0.3|0.38|4|48|1.82|0|2.09|55|œuf|oeuf au plat;oeuf sur le plat;oeuf frit;oeuf a la poele||recette calculée (ingrédients Ciqual)|
Œuf au plat (sans matière grasse)|Œufs|157|1.4|14.6|10.4|0|0.3|0.42|3.2|53|2|0|2.3|50|œuf|oeuf au plat sans huile||Ciqual 22505|
Œuf brouillé (avec matière grasse)|Œufs|167|0.4|11.9|13|0|0.4|0.48|4.5|59|1.94|0|1.8|60|œuf|oeufs brouilles||Ciqual 22502|
Omelette nature|Œufs|196|0.9|13.8|15.3|0|0.3|0.6|3.3|80|1.8|0|0.53|120|omelette (2 œufs)|omelette||Ciqual 22500|
Omelette au fromage|Œufs|255|2.6|17.7|19.3|0|0.4|0.58|10.1||1.16|0|1.39|140|omelette|||Ciqual 22506|
Omelette aux lardons|Œufs|267|1.5|12.5|23.5|0|0.7|0.98|9.4|48|1.39|0|1.11|140|omelette|||Ciqual 22507|
Omelette aux champignons|Œufs|125|2.3|8.1|9.2|0.5|0.7|0.35|3|48|1.4||1.11|140|omelette|||Ciqual 22508|
Tortilla espagnole (omelette pommes de terre)|Œufs|165|11.7|5.6|9.9|2.7|1.2|1.06|1.6|26|0.64|2|0|150|part|tortilla espagnole||Ciqual 22510|
Blanc d'œuf (cru)|Œufs|48|0.7|10.9|0.2|0|0.7|0.42|0|7|0.08|0|0|33|blanc|blanc d oeuf;blancs d oeufs||Ciqual 22001|
Blanc d'œuf cuit|Œufs|47|1.1|10.3|0.2|0|0.7|0.39|0|7|0.07|0|0|33|blanc|||Ciqual 22008|
Jaune d'œuf (cru)|Œufs|307|1.1|15.5|26.7|0|0.6|0.05|9.6|50|0.7|0|2|17|jaune|jaune d oeuf||Ciqual 22002|
Œuf de caille|Œufs|154|0.4|13.1|11.1|0|0.4|0.35|3.6|64|3.65|0|1.4|10|œuf|||Ciqual 22050|
Flan aux œufs|Œufs|113|17.7|4.2|2.7|0.4|16|0.15|1.5|86|0.35|||100|pot|flan||Ciqual 19674|
Crème caramel|Œufs|155|21|4.8|5.7|0.3|19.3|0.15|3.5|85|0.32|0|0.07|100|pot|||Ciqual 39209|
"""
    // Viandes et volailles
    private const val C1 = """
Blanc de poulet grillé|Viandes et volailles|141|0|30.1|2|0|0|0.14|0.6|5|0.39|1.4|0|120|filet|blanc de poulet cuit;filet de poulet;escalope de poulet;poulet||Ciqual 36018|
Cuisse de poulet rôtie (sans peau)|Viandes et volailles|171|0|24.8|8|0|0|0.26|2.2|12|1.2|0|0.13|150|cuisse|cuisse de poulet cuite||Ciqual 36006|
Cuisse de poulet rôtie (avec peau)|Viandes et volailles|205|0|25.9|11.3|0|0|0.23|4.4|15|1.2|3|0|150|cuisse|||Ciqual 36004|
Cuisse de poulet bouillie (avec peau)|Viandes et volailles|187|0|26.1|9.1|0|0|0.18|2.6|15|0.96|0|0|150|cuisse|poulet bouilli||Ciqual 36031|
Poulet rôti (viande et peau)|Viandes et volailles|204|0|28.9|9.9|0|0|0.4|3.1|19|0.68|0|1.5|150|portion|poulet roti||Ciqual 36005|
Aile de poulet rôtie|Viandes et volailles|247|0|23.8|16.9|0|0|0.25|5|18|0.84|0|0.2|40|aile|ailes de poulet;wings||Ciqual 36033|
Dinde rôtie|Viandes et volailles|151|0|29.1|3.8|0|0|0.25|1.1|10|1.14|0|0.3|120|portion|dinde||Ciqual 36302|
Escalope de dinde grillée|Viandes et volailles|124|0|28.5|1.1|0||0.16|0.5|5|0.49|0.8|0|120|escalope|||Ciqual 36306|
Canard rôti (sans peau)|Viandes et volailles|194|0|23.3|11.2|0|0|0.39|4|7|3.35|0|0.1|150|portion|canard||Ciqual 36202|
Magret de canard grillé|Viandes et volailles|188|0|26.7|9.1|0|0|1.09|3.4|5|4.8|3.5|0.93|150|portion|magret||Ciqual 36205|
Lapin cuit|Viandes et volailles|167|0.5|20.5|9.2|0|0|0.16|3.4|14|2.27|0||150|portion|lapin||Ciqual 34002|
Caille cuite|Viandes et volailles|206|0|26.8|10.9|0|0|0.13|3.5|15|4.43|2.3|0.2|100|caille|||Ciqual 36102|
Steak de bœuf grillé|Viandes et volailles|128|0|27.6|1.9|0|0|0.16|1|7|2.21|||120|steak|steak;bifteck;boeuf grille||Ciqual 6200|
Steak haché 5% MG cuit|Viandes et volailles|155|0|25.5|5.8|0|0|0.16|2.9|7|2.83|0|0|100|steak haché|bœuf haché 5% cuit;steak hache;viande hachee||Ciqual 6251|
Steak haché 10% MG cuit|Viandes et volailles|210|0|26.1|11.8|0|0|0.17|4.6|13|2.71|0|0|100|steak haché|||Ciqual 6253|
Steak haché 20% MG cuit|Viandes et volailles|265|0|23|19.2|0|0|0.19|8.1|24|2.48|0|0|100|steak haché|||Ciqual 6257|
Entrecôte grillée|Viandes et volailles|198|0|25.5|10.7|0|0|0.15|4.8|12|2.55|0|0.1|200|entrecôte|entrecote||Ciqual 6100|
Rumsteck grillé|Viandes et volailles|138|0|29|2.1|0|0|0.16|0.8|6|2.6|0|0|150|portion|||Ciqual 6207|
Faux-filet rôti|Viandes et volailles|194|0|28.8|8.7|0|0||3.6||2.41|||150|portion|||Ciqual 6105|
Rôti de bœuf (rosbif)|Viandes et volailles|126|0.3|24.1|3.2|0|0|0|0.6|10|3.1||0|120|portion|rosbif||Ciqual 6210|
Bœuf bourguignon / pot-au-feu (viande cuite)|Viandes et volailles|172|0|34|4|0|0|0.15|2.8||4.3|||150|portion|boeuf a bouillir||Ciqual 6230|
Bœuf braisé|Viandes et volailles|240|0|32.1|12.4|0|0|0.15|5.7||5.9|||150|portion|||Ciqual 6101|
Escalope de veau cuite|Viandes et volailles|147|0|31|2.5|0|0|0.15|0.8|11|1|||120|escalope|veau||Ciqual 6520|
Rôti de veau cuit|Viandes et volailles|143|0|28.1|3.4|0|0|0.17|1.2|6|0.9|0||120|portion|||Ciqual 6551|
Côte de porc grillée|Viandes et volailles|211|0|29.6|10.3|0|0|0.14|4.1|7|0.7|0|0.43|150|côte|cotelette de porc;porc||Ciqual 28101|
Filet mignon de porc cuit|Viandes et volailles|168|0|26.1|7.1|0|0|0.13|2.6|7|1.1|||120|portion|||Ciqual 28203|
Rôti de porc cuit|Viandes et volailles|163|0.7|30.5|4.3|0|0|0.08|1.3|7|0.64|0|0.5|120|portion|||Ciqual 28301|
Travers de porc braisés|Viandes et volailles|332|0|23.1|26.6|0|0|0.12|10.8|30|1.2|0.6|0|150|portion|travers;ribs||Ciqual 28401|
Épaule de porc cuite|Viandes et volailles|253|0|30|14.8|0|0|0.12|5.6|18|1.58|0.6|1|150|portion|||Ciqual 28010|
Côtelette d'agneau grillée|Viandes et volailles|157|0|26.3|5.8|0|0||2.2||1.27|||70|côtelette|agneau||Ciqual 21524|
Gigot d'agneau rôti|Viandes et volailles|169|0|26.8|6.8|0|0|0.19|2.6|22|1.4|0||150|portion|||Ciqual 21503|
Viande de mouton / cabri cuite (moyenne agneau)|Viandes et volailles|193|0|28.5|8.8|0|0||3.1||1.56|||150|portion|cabri;mouton;colombo de cabri viande||Ciqual 21523|
Foie de volaille cuit|Viandes et volailles|164|0|25.8|6.4|0||0.25|2.5|7|12|0|1.29|100|portion|||Ciqual 40109|
Foie de veau cuit|Viandes et volailles|140|9.2|19|3|0||0.21|1|9|4.5|13.2|2.52|120|portion|||Ciqual 40107|
Escalope panée de poulet|Viandes et volailles|339|21.7|7.2|24|3.4|1.9|1.26|5.3|22|1.6||0|120|escalope|escalope milanaise||Ciqual 36036|
Nuggets de poulet|Viandes et volailles|247|18|13.1|13.2|1.7|0.2|1.2|2.4|16|0.6||0|20|nugget|nuggets;croquettes de poulet||Ciqual 25512|
Cordon bleu de volaille|Viandes et volailles|226|14.6|14|12.2|0.8|2.2|1.73|3.2|61|0.68|0|0|100|cordon bleu|||Ciqual 25089|
"""
    // Charcuterie
    private const val C2 = """
Jambon blanc (cuit supérieur)|Charcuterie|113|0.8|21|2.8|0|0.7|1.87|1.1|0|0.54|18.1|0|40|tranche|jambon;jambon cuit;jambon de paris||Ciqual 28900|
Jambon sec|Charcuterie|230|0.5|28.7|12.6|0|0.3|5.7|5|||||20|tranche|jambon cru||Ciqual 28812|
Blanc de dinde (tranche)|Charcuterie|99|0|20.9|1.7|0.4|0|1.9|0.5|7|0.35|26.3|0|30|tranche|||Ciqual 28964|
Blanc de poulet (tranche)|Charcuterie|105|1.4|20.7|1.8|0.5|1.1|1.86|0.5|6|0.34|17.9|0|30|tranche|||Ciqual 28963|
Lardons cuits|Charcuterie|324|2|23.8|24.5|0||3.8|12.6|8|0.5||0|||lardons||Ciqual 28504|
Chipolata cuite|Charcuterie|283|0.8|18.8|22.4|0|0.4|2.15|8.3|19|0.79|11|0.46|50|chipolata|||Ciqual 30005|
Saucisse de Toulouse cuite|Charcuterie|279|1.3|18.8|22.1|0|0.6|1.71|8.2|12|0.9|0|0|100|saucisse|||Ciqual 30011|
Saucisse de Francfort / knack|Charcuterie|279|3.4|13.7|23.4|0|1|2|9|16|0.91||0.26|35|saucisse|knacki;saucisse knack;hot dog saucisse||Ciqual 30134|
Saucisse de volaille type knack|Charcuterie|236|1.8|14|18.9|0|1|1.87|5.3|||||35|saucisse|||Ciqual 30131|
Merguez cuite|Charcuterie|283|1.8|19.8|21.8|0|1.6|2.33|12|38|2.04|0|0|50|merguez|||Ciqual 30155|
Saucisson sec|Charcuterie|418|5.5|24.2|33.1|0.7|2.5|4.75|11.3|14|1.27|5|0.5|10|rondelle|saucisson||Ciqual 30300|
Chorizo|Charcuterie|425|1.2|23.5|36.1|0.9|1.2|4.24|14.3|||||10|rondelle|||Ciqual 30315|
Pâté de campagne|Charcuterie|323|1.5|15.5|28.2|0|0.6|1.54|10.4|12|6.1|5.9|0.2|30|tartine|pate;terrine||Ciqual 8211|
Rillettes de porc|Charcuterie|413|0.1|16.7|38.3|0.2|0|0.97|15.2|7|0.91|0|0.48|30|tartine|rillettes||Ciqual 8001|
Boudin noir grillé|Charcuterie|246|3.8|11.9|20.3|0|2.9|1.31|7.5|27|22.8|0|0.75|100|boudin|||Ciqual 8704|
Boudin créole (antillais)|Charcuterie|231|9.2|11.5|16.5|0|5.1|1.22|6.9|||||50|boudin|boudin antillais;boudin creole||Ciqual 8742|
Boudin blanc grillé|Charcuterie|220|6.6|9.9|17.1|0|2|1.64|6.5|44|0.45|0|0.61|100|boudin|||Ciqual 8800|
Corned-beef|Charcuterie|191|0|23.2|10.9|0|0|2.8|4.5|14|2.24|0|0.2|50|tranche|||Ciqual 28505|
Cervelas|Charcuterie|278|1.6|11.9|24.8|0.4|0.6|1.95|9.4|33|0.5|18.1|0|100|cervelas|||Ciqual 30730|
Foie gras|Charcuterie|488|2.2|6.9|50.1|0|1.4|1.29|21.6||2.1|||40|tranche|||Ciqual 8331|
"""
    // Poissons et fruits de mer
    private const val C3 = """
Cabillaud cuit|Poissons et fruits de mer|100|0|23.3|0.8|0|0|0.2|0.2|14|0.2|0.5|0.63|150|filet|morue fraiche;cabillaud||Ciqual 25997|
Poisson blanc cuit|Poissons et fruits de mer|114|0.3|22|2.7|0|0|0.28|0.6|26|0.44|0.5|1.86|150|filet|poisson||Ciqual 26249|
Poisson cuit (moyenne)|Poissons et fruits de mer|142|0.3|22.5|5.6|0|0|0.25|1.2|28|0.6|0.8|3.67|150|portion|poisson grille;poisson grillé||Ciqual 26140|
Vivaneau cuit|Poissons et fruits de mer|121|0|26.3|1.7|0|0|0.14|0.4|40|0.82|1.6||150|portion|vivaneau;poisson rouge;sarde||Ciqual 26147|
Mahi-mahi / dorade coryphène cuit|Poissons et fruits de mer|109|1.5|23.7|0.9|0|0|0.28|0.2|19|1.45|0||150|portion|dorade coryphene;mahi mahi||Ciqual 25990|
Thon frais cuit|Poissons et fruits de mer|136|0|29.9|1.8|0|0|0.14|1|19|1.8||1.83|150|portion|thon||Ciqual 26041|
Thon au naturel (conserve)|Poissons et fruits de mer|143|0|26.8|3.9|0|0|0.74|1|6|0.76|0|5.08|100|boîte égouttée|thon en conserve (eau);thon en boite;thon naturel||Ciqual 26039|
Thon à l'huile (conserve)|Poissons et fruits de mer|227|0|23.4|14.8|0|0|0.85|1.6|11|1||1.21|100|boîte égouttée|||Ciqual 26245|
Saumon grillé|Poissons et fruits de mer|223|0|25.5|13.5|0|0|0.11|2.9|8|0.43|||130|pavé|saumon cuit;pave de saumon;saumon||Ciqual 26229|
Saumon fumé|Poissons et fruits de mer|184|0.1|22.2|10.5|0.3|0.1|2.86|1.8|6|0.16|0|5.45|30|tranche|||Ciqual 26037|
Sardines à l'huile (conserve)|Poissons et fruits de mer|217|0.3|23.3|13.7|0|0|0.88|3.1|333|1.97|0|7.56|25|sardine|sardine||Ciqual 26034|
Sardine grillée|Poissons et fruits de mer|194|0|25.1|10.4|0|0|0.35|3.2|130|1.7|0|12.3|50|sardine|||Ciqual 26136|
Maquereau cuit|Poissons et fruits de mer|228|0|21.5|15.8|0|0|0.21|4.4|13|1.57|0.4|7.72|150|portion|maquereau||Ciqual 26019|
Maquereau au naturel (conserve)|Poissons et fruits de mer|221|0|18.8|16.2|0|0|0.86|3.7|38|1.1|0|4.56|100|boîte|||Ciqual 26123|
Morue salée sèche|Poissons et fruits de mer|210|1.1|47.6|1.7|0|0|12.3|0.3|124|2.09|0|4|||morue seche||Ciqual 26098|
Morue dessalée cuite|Poissons et fruits de mer|113|0|26|1|0|0|5.8|0.1|65|0.35||1.25|||morue dessalée cuite;morue;morue cuite||Ciqual 26024|
Accras de morue / de poisson|Poissons et fruits de mer|275|15.1|12.4|17.6|3|2.5|1.24|2.3|55|1.1|0.5|0|20|accra|accras;accra de morue;acras||Ciqual 25433|
Lieu noir cuit|Poissons et fruits de mer|100|0|23.1|0.8|0|0|0.25|0.3|38|0.4|0|0|150|filet|||Ciqual 26015|
Merlan vapeur|Poissons et fruits de mer|98|0|23|0.7|0|0|0.28|0.1|21|0.1|0|0|150|filet|||Ciqual 26022|
Daurade grise rôtie|Poissons et fruits de mer|127|1.4|22.9|3.3|0|0||0.8|||||150|portion|||Ciqual 26222|
Dorade royale rôtie|Poissons et fruits de mer|148|0|23.6|5.9|0|0|0.18|1.3|13|0.2|0.6|5.71|150|portion|daurade||Ciqual 27031|
Bar / loup rôti|Poissons et fruits de mer|151|1|22.2|6.5|0|0|0.17|1.3|11|0.28||4.15|150|portion|||Ciqual 27030|
Espadon cuit|Poissons et fruits de mer|266|0|28.7|16.9|0|0|0.18|1.5|6|0.53|0|16.6|150|portion|||Ciqual 26093|
Truite vapeur|Poissons et fruits de mer|99|0|19|2.6|0|0||0.5|||||150|portion|truite||Ciqual 27007|
Hareng frit|Poissons et fruits de mer|201|0|23|12.1|0|0||3.5|||||100|portion|||Ciqual 26012|
Hareng fumé|Poissons et fruits de mer|167|0.5|16.5|11|0|0|3.98|2.8|62|1|0|22|50|filet|hareng saur||Ciqual 26013|
Poisson pané frit|Poissons et fruits de mer|194|13.5|11.9|10|0.8|0.4|0.84|2.1|15|0.44|0|0|100|filet|poisson pane;colin pane||Ciqual 26030|
Surimi|Poissons et fruits de mer|117|11.8|7.5|4.3|0.7|2.7|1.77|0.4|19|0.4|0|0|17|bâtonnet|||Ciqual 26046|
Crevettes cuites|Poissons et fruits de mer|100|0.6|22.5|0.8|0|0|1.66|0.3|56|0.58||0.34|||ouassous / crevettes cuites;crevettes;ouassous;z habitants||Ciqual 10101|
Langouste cuite|Poissons et fruits de mer|106|1.3|21.8|1.5|0|0|0.46|0.3|66|1.36|2.1|0.5|150|portion|langouste||Ciqual 10022|
Crabe cuit|Poissons et fruits de mer|124|1.8|19.5|4.3|0|0|1.23|0.6|53|1.62|3.6|0|||crabe;matoutou crabe||Ciqual 10025|
Crabe en conserve (miettes)|Poissons et fruits de mer|98|4.2|18.1|1|0|0|1.64|0.2|120|3.15|0|0|||||Ciqual 10005|
Moules cuites|Poissons et fruits de mer|108|5.1|17.2|2.1|0|0|0.8|0.4|59|3.99|5.9|0|||moules||Ciqual 10013|
Calmar / encornet cuit|Poissons et fruits de mer|149|1.6|32.5|1.4|0|0|1.86|0.2|180|5.77|8.5||||calamar;encornet||Ciqual 10037|
Calmar frit / à la romaine|Poissons et fruits de mer|173|8.4|17.9|7.5|0|0|0.77|1.9|39|1.01|4.2||||||Ciqual 10084|
Poulpe / chatrou cuit|Poissons et fruits de mer|156|4.4|29.8|2.1|0|0|1.15|0.5|106|9.54|8|0|150|portion|chatrou;poulpe;zourite||Ciqual 10079|
Lambi cuit|Poissons et fruits de mer|130|1.7|26|1|||||||||150|portion|lambi;strombe||valeur approximative (versions précédentes)|
Écrevisses cuites|Poissons et fruits de mer|74|0.6|16.3|0.7|0|0|0.21|0.2|73|0.65|0.5|0|||||Ciqual 10031|
"""
    // Plats antillais
    private const val C4 = """
Colombo de poulet|Plats antillais|134|4.3|12.3|7.2|1||0.09|1.6|13|0.55|1.6|0|300|assiette|colombo;colombo poulet||recette calculée (ingrédients Ciqual)|
Colombo de cabri / mouton|Plats antillais|137|4.3|13.4|7.1|1|||1.8||0.82|||300|assiette|colombo cabri||recette calculée (ingrédients Ciqual)|
Féroce d'avocat|Plats antillais|213|16.7|6.3|12.7|2.2||1.2||26|0.8|||150|portion|feroce;feroce avocat||recette calculée (ingrédients Ciqual)|
Riz-pois rouges (riz aux haricots rouges)|Plats antillais|155|25.5|5|2.6|4.4|0.3|0.04|0.4|21|0.7|0.1|0|250|assiette|riz pois rouges;riz et pois;riz haricots rouges;riz pois||recette calculée (ingrédients Ciqual)|
Riz-pois d'Angole|Plats antillais|155|25.1|7|2.7|||||||||250|assiette|riz pois d angole||recette calculée (ingrédients Ciqual)|
Court-bouillon de poisson|Plats antillais|106|1.1|16.1|4.1|0.3|0.9|0.09|0.6|29|0.65|5.8||300|assiette|court bouillon;court-bouillon||recette calculée (ingrédients Ciqual)|
Blaff de poisson|Plats antillais|68|0.2|14.5|1|0|0.1|0.08|0.2|26|0.47|1.1||300|assiette|blaff||recette calculée (ingrédients Ciqual)|
Chatrou (poulpe) en sauce|Plats antillais|136|3.7|18.2|5.3|0.3|0.8|0.7|0.7|69|5.88|9.5|0|250|assiette|fricassee de chatrou;chatrou en sauce||recette calculée (ingrédients Ciqual)|
Fricassée de lambi|Plats antillais|120|2.1|15.9|4.6|||||||||250|assiette|lambi en sauce||recette calculée (ingrédients Ciqual)|
Morue en sauce (rougail / chiquetaille)|Plats antillais|108|1.2|13.3|5.5|0.3|0.9|2.91|0.6|39|0.34||0.62|200|portion|chiquetaille;rougail de morue;morue en sauce||recette calculée (ingrédients Ciqual)|
Ouassous / crevettes en sauce|Plats antillais|102|1.4|13.8|4.5|0.3|0.8|1|0.6|39|0.5||0.2|250|assiette|ouassous en sauce;fricassee d ouassous||recette calculée (ingrédients Ciqual)|
Fricassée de poulet|Plats antillais|145|1.1|14.7|9|0.3|0.8|0.11|1.9|14|0.68|4.7|0|300|assiette|poulet en sauce;poulet boucane;fricassee||recette calculée (ingrédients Ciqual)|
Haricots rouges en sauce (pois rouges)|Plats antillais|116|10.8|6.6|3.5|7.9|0.7|0.02|0.5|39|1.55|0.5|0|200|portion|pois rouges;haricots rouges en sauce||recette calculée (ingrédients Ciqual)|
Banane plantain frite (alloco)|Plats antillais|218|25.4|0.7|12.2|2|12.3|0.01|1.4|2|0.51|9.6|0|100|portion|plantain frit;banane frite;alloco||recette calculée (ingrédients Ciqual)|
Blanc-manger coco (recette maison)|Plats antillais|216|32.9|2.5|7.7|2.1|32.5|0.07|6.5|88|0.67|0.6|0|120|part|blanc manger coco;blanc-manger||recette calculée (ingrédients Ciqual)|
Ti-punch|Plats antillais|235|14.8|0|0|0|14.6|0.01|0|13|0.21|0.2|0|40|verre|ti punch;tipunch;punch||recette calculée (ingrédients Ciqual)|25.1
Planteur|Plats antillais|112|12.5|0.3|0.1|0.2|12.4|0.01|0|3|0.11|20|0|200|verre|||recette calculée (ingrédients Ciqual)|8.3
Punch coco|Plats antillais|280|21.3|3.6|11.8|1.7|21|0.1|9.8|107|1.1|1.6|0.07|60|verre|||recette calculée (ingrédients Ciqual)|10
Pâté créole / pâté salé (feuilleté à la viande)|Plats antillais|354|27.3|9.6|22.6|1.6|1.2|1.55|10.6|16|1.65|||80|pâté|pate creole;pate sale;pate cochon||Ciqual 25402|
"""
    // Féculents
    private const val C5 = """
Riz blanc cuit|Féculents|155|33.2|3.1|0.7|1.4|0|0.04|0.2|6|0|0|0|200|assiette|riz;riz cuit;riz blanc||Ciqual 9104|
Riz thaï cuit|Féculents|143|31.4|2.4|0.8|0|0|0.04|0.1|6|0|0|0|200|assiette|riz thai||Ciqual 9124|
Riz basmati cuit|Féculents|148|32.9|3|0.4|0|0|0.04|0.1|7|0|0|0|200|assiette|riz basmati||Ciqual 9125|
Riz complet cuit|Féculents|187|37.6|3.9|1.8|2.1|0.1|0.04|0.4|29|0.44|0|0|200|assiette|riz complet||Ciqual 9103|
Riz étuvé cuit|Féculents|146|31.7|3|0.6|1.1|0.1|0.01|0.1|11|0|0|0|200|assiette|||Ciqual 9105|
Riz blanc cru|Féculents|350|77.5|7|0.8|1.5|0.1|0.01|0.2|13|1.94|0|0|||riz cru||Ciqual 9100|
Pâtes cuites|Féculents|167|31.4|6.1|1.1|2.2|0.9|0.05|0.2|18|0.68|0|0|220|assiette|pates;spaghetti;macaroni;coquillettes;penne;tagliatelles||Ciqual 9811|
Pâtes complètes cuites|Féculents|179|31.3|7.3|1.4|5.1|0.9|0.06|0.3|25|1.5|0|0|220|assiette|||Ciqual 9871|
Pâtes fraîches aux œufs cuites|Féculents|168|32|5.8|1.3|1.5|0|0.04||24|1.1||0|220|assiette|||Ciqual 9816|
Pâtes sèches crues|Féculents|364|72.7|12|1.6|2.9|2.6|0.02|0.3|21|1.3|0|0|||pates crues||Ciqual 9810|
Semoule / couscous cuit|Féculents|164|31|5.8|0.9|2.8|3.4|0.04|0.2|15|0.76|0|0|200|assiette|semoule;couscous graine;graine de couscous||Ciqual 9683|
Boulgour cuit|Féculents|131|24.1|4.3|1|3.7|0|0.03|0.2|31|0.67|0|0|200|assiette|boulgour||Ciqual 9691|
Quinoa cuit|Féculents|149|27.9|4.7|1.1|3.8|0.9|0.01|0.1|23|1.6|0|0|200|assiette|quinoa||Ciqual 9341|
Polenta cuite|Féculents|76|16.9|1.4|0|1.6|0.1|0|0|5|0.21|0||200|assiette|||Ciqual 9615|
Vermicelles de riz cuites|Féculents|87|20.1|1.4|0|0|0|0.02|0|5|0.08|0|0|||||Ciqual 9901|
Pomme de terre cuite à l'eau|Féculents|81|16.8|1.8|0.3|1.8|0.9|0.05|0.1|6|0.27|2.9|0|150|pomme de terre|pomme de terre cuite;pommes de terre;patates||Ciqual 4003|
Pomme de terre au four|Féculents|91|19|2.5|0.1|2.2|1.2|0.03|0|15|1.08|9.6|0|150|pomme de terre|||Ciqual 4026|
Pommes de terre sautées|Féculents|137|17.3|2.5|5.7|2.6|2.2|0.51|1.2|18|0.73|2||200|portion|pommes de terre sautees;pommes sautees||Ciqual 4015|
Purée de pommes de terre|Féculents|97|13|2.1|3.7|1.6|1.5|0.37|1.8|35|0.26|6|0.17|200|portion|puree||Ciqual 4047|
Frites (friteuse)|Féculents|258|32.7|3.3|11.9|3.9|0.6|0.07|2.9|24|0.9|19|0|150|portion|frites;frite||Ciqual 4032|
Frites au four|Féculents|213|32.6|3.8|6.6|4.2|0.2|0.39|0.8|11|0.67|2.4|0|150|portion|||Ciqual 4030|
Chips|Féculents|532|51|6|32.7|4.7|1.5|1.35|2.9|27|1.2|8.3|0|30|petit paquet|chips||Ciqual 4004|
Patate douce cuite (Martinique)|Féculents|126|25.2|1.5|0.1|9.2||||||12.5||150|portion|patate douce cuite;patate douce||Ciqual 20832|
Patate douce cuite (moyenne France)|Féculents|79|16.3|1.7|0.1|2.9|6.1|0.08|0|32|0.71|16.2|0|150|portion|||Ciqual 4102|
Igname cuite|Féculents|109|23.6|1.5|0.1|3.9|0.5|0.02|0|14|0.52|12.1|0|150|portion|igname;yam||Ciqual 53503|
Igname jaune cuite (Martinique)|Féculents|127|27.6|2|0.4|2.7||||||0.8||150|portion|||Ciqual 20824|
Igname Saint-Martin cuite (Martinique)|Féculents|93|18.4|2.2|0.3|3.9||||||22||150|portion|||Ciqual 20825|
Igname cousse-couche cuite (Martinique)|Féculents|89|17.9|1.6|0.3|3.9||||||0.9||150|portion|cousse couche||Ciqual 20823|
Banane plantain cuite|Féculents|125|28.9|0.8|0.2|2.3|14|0.01|0.1|2|0.58|10.9|0|150|banane (½ plantain)|plantain;banane plantain;banane jaune plantain||Ciqual 53101|
Banane plantain crue|Féculents|132|29.6|1.3|0.4|2.3||0.01|0.1|3|0.6|18.4|0|||||Ciqual 53100|
Dachine / madère (taro) cuit|Féculents|131|29.5|0.5|0.1|5.1|0.5|0.04|0|18|0.72|5|0|150|portion|dachine / madère cuite;dachine;madere;taro||Ciqual 53201|
Fruit à pain cuit|Féculents|90|21|1.3|0.1|||||||8.2||150|portion|fruit a pain;fouyapen||Ciqual 20813 (énergie calculée)|
Manioc cuit|Féculents|135|32.8|0.7|0|0.4||||12|0.6|13.1||150|portion|manioc;cassave racine||Ciqual 54034|
Farine de manioc / couac (gari)|Féculents|340|81.6|1.1|0.7|1.4||0.15||33|2.78|5||30|c. à soupe|couac;farine manioc;gari||Ciqual 54035|
Tapioca (cru)|Féculents|354|87.8|0.2|0|0.9|3.4|0|0|20|1.58|0|0|||||Ciqual 4000|
Maïs doux en épi cuit|Féculents|106|18.6|3.4|1.5|2.4|4.5|0|0.2|3|0.45|5.5|0|150|épi|maïs doux;mais;epi de mais||Ciqual 20049|
Pain blanc (baguette)|Féculents|287|58.3|8.3|1.4|2.7|2.3|1.25|0.5|22|1.2|0|0|30|tranche|pain (baguette);pain;baguette||Ciqual 7001|
Pain tradition|Féculents|279|56.7|8.2|1|3.8|2.1|1.27|0.3|20|1.2|0|0|30|tranche|tradition||Ciqual 7007|
Pain complet|Féculents|234|41.2|8.7|1.7|7.3|2.4|1.17|0.6|83|2|0|0.86|30|tranche|||Ciqual 7110|
Pain de campagne|Féculents|253|50|7.5|1.3|4.3|2.5|1.18|0.4|23|1.5|0|0|30|tranche|||Ciqual 7100|
Pain au levain|Féculents|261|53.1|7.4|1.3|2.1|2.4|1.25|0.2|19|1.3|||30|tranche|||Ciqual 7002|
Pain aux céréales|Féculents|269|46.9|9.6|3.2|5.2|2.2|1.13|0.5|40|1.6|0|0|30|tranche|||Ciqual 7255|
Pain de mie|Féculents|279|50.4|7.1|4.4|2.8|6.1|1.07|0.5|88|0.7|0|0|25|tranche|pain de mie||Ciqual 7200|0.2
Pain de mie complet|Féculents|257|41.8|7.3|4.1|7.3|5.6|1.07|0.6|42|1.5|0||25|tranche|||Ciqual 7111|0.8
Pain grillé (toast)|Féculents|317|64|9.5|1.3|3.7|1.4|1.51|0.5|20|0.97|0|0|25|tranche|toast||Ciqual 7004|
Pain burger / hot-dog|Féculents|283|47.2|8.2|4.9|3.5|6.5|1.13|0.8|63|0.82|0|0|55|pain|bun||Ciqual 7259|0.9
Pain pita|Féculents|249|48.8|7.5|1.5|3.7|2.5|1.2|0.2|53|1|||60|pain|||Ciqual 7180|
Tortilla de blé|Féculents|320|53|8|7.5|2.9|2.1|1.41|1.4|45|0.76|||40|galette|wrap galette;tortilla||Ciqual 7815|
Biscotte|Féculents|394|73.2|10|5.4|4.3|6.9|1.36|1.3|29|1.1|0|0|9|biscotte|biscottes||Ciqual 7300|
Galette de riz soufflé|Féculents|381|77.9|8.4|3|3.4|0.6|0.22|0.8|13|1.2|0||9|galette|||Ciqual 7352|
Croissant|Féculents|412|47.6|6.3|21.1|1.8|7.9|1.31|11.6|21|0.85|0|0|50|croissant|||Ciqual 7615|
Pain au chocolat|Féculents|423|45.4|7.5|22.5|3|12.8|0.98|14.7|23|4.1|0|0|65|pain au chocolat|chocolatine||Ciqual 7730|
Pain aux raisins|Féculents|345|53.4|5.8|11.5|1.4|20.9|0.81|5.7|32|0.77|0|0|90|pièce|||Ciqual 7720|
Brioche|Féculents|363|52.1|8.4|13|1.9|13.9|1.04|5.1|34|1.24|0|0|35|tranche|||Ciqual 7746|
Pain au lait|Féculents|357|50.5|8.7|12.3|3.5|11.5|1.06|4.1|49|0.85|0|0|35|pain|||Ciqual 7711|0.2
Corn-flakes (pétales de maïs)|Féculents|368|80|6.9|1|5.5|7.6|1.01|0.3|0|9.4|0|9|30|bol|cornflakes;corn flakes||Ciqual 32005|
Céréales chocolatées|Féculents|393|74.7|8.2|5.1|7.1|22.5|0.3|1.4|522|11|0|2.9|30|bol|cereales||Ciqual 32001|
Muesli|Féculents|433|63.4|8.9|14.3|6.9|19.4|0.44|3.4|48|3.44|2.4|0.68|45|bol|||Ciqual 32004|
Riz soufflé|Féculents|388|86.3|6.7|1.2|2|7.9|1|0.4|10|8||8.4|30|bol|||Ciqual 32006|
Flocons d'avoine|Féculents|369|57.7|10.6|7.8|11.2|1|0.01|1.4|52|3.8|0|0|40|portion|avoine;porridge sec|P|Ciqual 32140|
Porridge (flocons d'avoine cuits à l'eau)|Féculents|69|10.3|2.5|1.5|1.7|0.2|0.01|0.3|9|0.9|0|0|250|bol|porridge||Ciqual 9313|
Farine de blé|Féculents|350|73.7|9|1|3.2|1.5|0|0.2|23|1|0|0|||farine||Ciqual 9436|
Farine de maïs|Féculents|362|80.9|5.6|1.4|1.9|0.6|0|0.2|2|0.91|0|0|||||Ciqual 9545|
"""
    // Légumineuses
    private const val C6 = """
Haricots rouges cuits|Légumineuses|116|12.3|9.6|0.6|11.6|0.5|0.02|0.2|55|2.3|0|0|150|portion|haricots rouges;pois rouges cuits||Ciqual 20503|
Haricots blancs cuits|Légumineuses|112|12|6.8|1.1|13.8|0.3|0.02|0.2|120|1.7|0|0|150|portion|||Ciqual 20502|
Haricots blancs sauce tomate|Légumineuses|80|11.7|4.8|0.5|5|3|0.87|0.2|||||150|portion|baked beans||Ciqual 20194|
Lentilles cuites|Légumineuses|125|16.2|10.1|0.6|8.4|0.2|0.01|0.1|39|2.45|0|0|150|portion|lentilles|P|Ciqual 20360|
Lentilles corail cuites|Légumineuses|123|15|10.6|0.5|8.2|0.3|0|0.1|21|2.2|0|0|150|portion|||Ciqual 20589|
Pois chiches cuits|Légumineuses|148|17.7|8.3|3|8.2|0.3|0.03|0.5|72|1.3|0|0|150|portion|pois chiches|P|Ciqual 20507|
Pois cassés cuits|Légumineuses|129|16.3|8.6|1.5|8|3.9|0.01|0.2|25|2.09|0.8|0|150|portion|||Ciqual 20506|
Pois d'Angole cuits|Légumineuses|116|11.1|16.3|0.7|||||||||150|portion|pois d angole;pois de bois|P|Ciqual 20833 (énergie calculée)|
Flageolets cuits|Légumineuses|112|12|6.8|1.1|13.8|0.3|0.02|0.2|120|1.7|0|0|150|portion|||Ciqual 20513|
Fèves cuites|Légumineuses|83|9.3|8.1|0.8|3.1|0.4|0.03|0.2|37|1.5|0|0|150|portion|||Ciqual 20500|
Haricots mungo cuits|Légumineuses|96|11.9|7.5|0.6|6.4|2|0.02|0|53|1.75|1|0|150|portion|||Ciqual 20531|
Tofu nature|Légumineuses|147|2.9|13.4|8.5|0|0.6|0.03|1.4|100|2.4|0|0|100|portion|tofu||Ciqual 20904|
Houmous|Légumineuses|258|9|8.1|19.9|5.2|1.7|1.08|2|||||30|c. à soupe|hummus||Ciqual 25621|
"""
    // Légumes
    private const val C7 = """
Tomate|Légumes|18|3.4|0.5|0|1|3|0|0|7|0.17|19.9||120|tomate|tomate crue||Ciqual 20276|
Tomate cerise|Légumes|32|5.6|1.3|0|1.2|4.8|0|0|7|0.37|21.8|0|15|tomate|||Ciqual 20172|
Tomate cuite|Légumes|17|2.8|0.9|0.1|0.7|2.5|0.03|0|11|0.68|22.8|0|||||Ciqual 20242|
Carotte crue|Légumes|30|5.2|0.8|0|2.9|4.8|0.12|0|36|0|3.7|0|80|carotte|carotte;carottes rapees||Ciqual 20009|
Carotte cuite|Légumes|30|5.7|0.2|0|3.3|5.1|0.12|0|28|0.19|0.5||80|carotte|||Ciqual 20306|
Laitue / salade verte|Légumes|15|1.2|1.4|0.2|1.1|0.7|0.02|0|65|1.1|5.5|0|50|portion|salade verte;salade;laitue||Ciqual 20031|
Mesclun|Légumes|22|2.5|2|0|2|0|0.05|0|||||50|portion|||Ciqual 20272|
Roquette|Légumes|28|2.1|2.6|0.7|1.6|2|0.07|0.1|160|1.46|15|0|30|portion|||Ciqual 20217|
Concombre|Légumes|17|2.9|0.7|0.1|0.5|1.7|0.01|0|19|0.13|2.8|0|200|concombre|||Ciqual 20019|
Courgette crue|Légumes|17|1.8|1.2|0.3|1|1.7|0.02|0.1|16|0.37|17.9|0|200|courgette|courgette||Ciqual 20020|
Courgette cuite|Légumes|16|1.4|0.9|0.4|1.5|0|0.1|0.1|19|0.32|2.9|0|150|portion|||Ciqual 20021|
Aubergine crue|Légumes|23|2.7|1|0.2|3|2.7|0.01|0|9|0.23|2.2|0|250|aubergine|||Ciqual 20053|
Aubergine cuite|Légumes|27|4.2|0.8|0.2|2.5|3.2|0|0|20|0.25|1.3|0|150|portion|aubergine;melongene||Ciqual 20002|
Poivron cru|Légumes|23|3.5|0.8|0.3|1.5|1.5|0.02|0.1|8|0.4|121|0|150|poivron|poivron;piment doux||Ciqual 20041|
Poivron cuit|Légumes|33|5.4|1.1|0.3|2|5.1|0.01|0.1|9||82||150|portion|||Ciqual 19999|
Oignon cru|Légumes|39|6.2|1.1|0.6|1.7|4.8|0.1|0.2|25|0|3.9|0|100|oignon|oignon|P|Ciqual 20034|
Oignon cuit|Légumes|35|6.2|1.3|0.2|1.4|4|0.02|0|29|0.21|2.1|0|100|oignon||P|Ciqual 20035|
Cive / oignon pays (cébette)|Légumes|28|3.6|1.1|0.3|3.1|2.8|0|0.1|55|0.85|7.4||15|cive|cive;oignon pays||Ciqual 20323|
Ail cru|Légumes|109|18.6|5.3|0|5.8|1.2|0.02|0|11|0.63|0|0|5|gousse|ail|P|Ciqual 11000|
Poireau cru|Légumes|30|4.5|1.5|0.3|1.8|3.9|0.05|0|59|2.1|12|0|150|poireau||P|Ciqual 20039|
Poireau cuit|Légumes|25|2.2|2.6|0|3.4|1.8|0|0|35|0.45|3.8|0|150|portion|poireau|P|Ciqual 20327|
Brocoli cru|Légumes|32|2.1|2.9|0.4|2.6|1.7|0.07|0|48|0.81|91.2|0|||||Ciqual 20057|
Brocoli cuit|Légumes|38|2.5|4.1|0.7|2.2|1.5|0.04|0.2|36|0.58|20.7||150|portion|brocoli||Ciqual 20304|
Chou-fleur cuit|Légumes|21|1.6|1.6|0.5|2|0|0|0.1|19|0.31|9.7|0|150|portion|chou fleur||Ciqual 20017|
Chou blanc cru|Légumes|35|4.6|1.4|0.6|3.5|4.2|0.03|0.3|59|0.3|8.9|0|100|portion|chou;chou blanc||Ciqual 20116|
Chou blanc cuit|Légumes|21|3.2|1|0|2.7|2.8|0.02|0|42|0.19|13.3||150|portion|||Ciqual 20308|
Chou rouge cru|Légumes|26|4.3|1.1|0|2.8|3.9|0.04|0|36|0.25|18.7|0|100|portion|||Ciqual 20014|
Choux de Bruxelles cuits|Légumes|44|5.4|3.2|0|4.8|4.1|0.02|0|35|0.51|55.4||150|portion|||Ciqual 20309|
Épinards cuits|Légumes|27|0|3.4|0.7|3.5|0|0.03|0.1|240|1.3|0||150|portion|epinards||Ciqual 20336|
Pousses d'épinard (crues)|Légumes|17|0|2.1|0.4|2.4|0|0.06|0.1|110|1.8|6|0|50|portion|||Ciqual 20270|
Haricots verts cuits|Légumes|27|3.4|1.8|0|3.2|2.5|0|0|51|0.54|1.2||150|portion|haricots verts||Ciqual 20320|
Petits pois cuits|Légumes|81|9.9|6.4|0.6|4.8|1.6|0.02|0.1|38|1.2|11.4||150|portion|petits pois||Ciqual 20326|
Champignons crus|Légumes|24|2.2|2.4|0.3|1.6|1.7|0.01|0.1|8|0.5|2.1|0.2|100|portion|champignon||Ciqual 20010|
Champignons de Paris cuits|Légumes|29|3|2.2|0.5|2.2|2.3|0.01|0.1|6|1.74|4|0.2|100|portion|||Ciqual 20102|
Betterave rouge cuite|Légumes|42|7.1|1.4|0.4|2.5|6.7|0.23|0.1|24|0.29|0|0|100|portion|betterave||Ciqual 20003|
Navet cuit|Légumes|18|3.2|0.8|0|2.1|2.8|0.03|0|32|0.08|7.2||150|portion|||Ciqual 20321|
Radis|Légumes|11|1.5|0.9|0|1.4|1.1|0.04|0|15|0.4|12.2|0|10|radis|||Ciqual 20045|
Céleri branche|Légumes|15|2.4|0.6|0|2.2|1.3|0.2|0|46|0.06|5.5|0|40|branche|||Ciqual 20023|
Céleri-rave cuit|Légumes|36|4.6|1.3|0.9|2.6|2.8|0.15||52|0.32|2.7|0|150|portion|||Ciqual 20025|
Potiron cuit|Légumes|26|4.5|0.8|0.1|2|3.1|0|0|13|0.18|0.8|0|150|portion|potiron;citrouille||Ciqual 20096|
Giraumon cuit (Martinique)|Légumes|27|4.5|1.1|0.1|2|1.9|||||||150|portion|giraumon;giromon||Ciqual 20814 (énergie calculée)|
Courge butternut cuite|Légumes|29|5.2|1.1|0|1.8|4|0|0|17|0.15|2.3|0|150|portion|butternut;courge||Ciqual 20292|
Christophine cuite|Légumes|22|2.3|0.6|0.5|2.8||0|0.1|13|0.22|8|0|150|portion|christophine;chayotte;chouchou||Ciqual 13177|
Christophine crue|Légumes|19|2.8|0.8|0.1|1.7|1.7|0.01|0|17|0.34|7.7|0|||||Ciqual 13128|
Gombo cuit (Martinique)|Légumes|38|4.7|1.8|0.3|4.8||||||3||100|portion|gombo;gombos;okra||Ciqual 20821 (énergie calculée)|
Artichaut cuit|Légumes|33|1|2.5|0.3|8.3|1|0.15|0.1|43|0.67|9.1|0|150|portion|artichaut cuit;artichaut|P|Ciqual 20000|
Asperges vertes cuites|Légumes|25|1.7|2.7|0.3|2.2|1.3|0|0.1|21|0.64|4.7||100|portion|asperge;asperges|P|Ciqual 20299|
Topinambour cuit|Légumes|69|14.4|1.8|0|2.2|1.7|0.09|0|33|0.2|3.2|0|150|portion|topinambour|P|Ciqual 20050|
Endive crue|Légumes|17|2.8|1.2|0|1.1|2.4|0|0|20|0.12|2.1|0|100|endive|endive||Ciqual 20026|
Fenouil cru|Légumes|18|2.6|1|0|2.6|2.2|0.06|0|37|0.2|2.6|0|100|portion|||Ciqual 20028|
Cresson|Légumes|16|1.1|2.3|0.1|0.5|0.2|0.1|0|120|0.2|43|0|50|portion|||Ciqual 20022|
Persil frais|Légumes|42|4.1|3|0.6|4.3|0.8|0.59|0.1|138|6.2|133|0|5|c. à soupe|||Ciqual 11014|
Gingembre frais|Légumes|33|3.4|1.1|1.1|2.7|1|0.02|0.1|11|0.4|0|0|5|morceau|||Ciqual 11074|
Piment (cru)|Légumes|37|5.8|1.9|0.4|1.5|4|0.02|0|14|1.03|144|0|5|piment|piment;bondamanpiment;piment antillais||Ciqual 20151|
Avocat|Légumes|203|0|1.6|20.6|3.6|0.4|0.01|4.5|9|0.34|0|0|150|avocat (chair)|avocat;zaboka||Ciqual 13004|
Ratatouille|Légumes|57|5|1.2|3.1|2|3.9|0.94|0.5|16|0.38|25.2|0|200|portion|||Ciqual 25018|
Salade verte nature|Légumes|14|1.5|1|0.1|1.3|0.8|0.05|0|35|0.64|1.9|0|50|portion|||Ciqual 25604|
"""
    // Fruits
    private const val C8 = """
Banane|Fruits|88|19.7|1.1|0|2.7|15.6|0|0|5|0.2|7.2|0|120|banane|banane;banane dessert;figue banane||Ciqual 13005|
Pomme|Fruits|54|11.6|0.2|0.2|1.4|10.6|0|0.1|5|0.1|4.6|0|150|pomme|||Ciqual 13039|
Poire|Fruits|50|11.4|0.2|0|3.1|9.4|0|0|5|0.08|2||150|poire|||Ciqual 13397|
Orange|Fruits|42|8|0.8|0|2.7|7.6|0|0|66|0.57|47.5|0|150|orange|||Ciqual 13034|
Clémentine / mandarine|Fruits|46|9.2|0.8|0|1.7|8.6|0|0|23|0.09|49.2|0|60|clémentine|clementine;mandarine||Ciqual 13024|
Citron vert|Fruits|39|3.1|1.1|0|4.3|2.1|0.01|0|57|0.2|29.3|0|50|citron|citron vert;lime||Ciqual 13067|
Citron|Fruits|24|1.6|0.2|0|0|0.8|0|0|11|0.15|45|0|80|citron|citron||Ciqual 13009|
Mangue|Fruits|71|14.3|0.6|0|1.6|12.9|0|0|12|0.09|25|0|250|mangue (chair)|mangue||Ciqual 13025|
Ananas|Fruits|52|11.7|0.2|0|1.2|10.5|0|0|8|0.17|46.1|0|150|tranche épaisse|ananas||Ciqual 13002|
Papaye|Fruits|40|8.5|0.8|0|1.8|8.1|0|0|22|0.16|65.3|0|150|portion|papaye||Ciqual 13035|
Goyave|Fruits|68|9|2.5|0.9|5.4|8.9|0.01|0.3|18|0.26|228|0|80|goyave|goyave||Ciqual 13083|
Fruit de la passion (maracudja)|Fruits|102|10.9|2.1|3|6.8|8.5|0|0.5|8|0.56|25.6|0|20|fruit (pulpe)|maracudja;maracuja;fruit de la passion||Ciqual 13016|
Carambole|Fruits|28|3.8|1|0.3|2.8|3.8|0.01|0|3|0.08|34.4|0|90|carambole|||Ciqual 13054|
Pastèque|Fruits|35|8.3|0.7|0|0.5|7.9|0|0|6|0.16|4.3|0|300|tranche|pasteque;melon d eau||Ciqual 13036|
Melon|Fruits|60|14.8|1.1|0|1.3|10.6|0.06|0|11|0.21|8.1|0|200|½ melon|||Ciqual 13026|
Kiwi|Fruits|61|11|0.9|0.6|2.4|8.9|0|0.1|29|0.16|81.9|0|75|kiwi|||Ciqual 13021|
Fraises|Fruits|35|6|0.6|0|3.8|5.6|0|0|18|0.19|54|0|15|fraise|fraise||Ciqual 13014|
Framboises|Fruits|48|5.8|1.2|0.8|4.3|5.4|0|0.1|16|0.4|18.7|0|||||Ciqual 13015|
Myrtilles|Fruits|58|10.6|0.9|0.3|2.4|10|0|0|6|0.28|9.7|0|||||Ciqual 13028|
Raisin|Fruits|71|16.3|0.7|0.2|1.1|15.4|0|0|11|0.23|3|0|5|grain|raisin||Ciqual 13395|
Pêche|Fruits|38|7.5|0.9|0.2|1.5|6.8|0|0|7|0.15|6.6|0|150|pêche|||Ciqual 13043|
Nectarine|Fruits|50|11.3|0.7|0|0.5|8.8|0|0|5|0.13|3.2||150|nectarine|||Ciqual 13399|
Abricot|Fruits|44|9|0.8|0|1.7|6.7|0|0|15|0.19|2.5|0|40|abricot|||Ciqual 13000|
Cerises|Fruits|54|13|0.8|0|1.6|10|0|0|10|0.17|4.1|0|7|cerise|cerise||Ciqual 13008|
Prune|Fruits|52|9.9|0.7|0.3|1.4|9.9|0|0|6|0.17|9.5|0|40|prune|||Ciqual 13100|
Litchi|Fruits|78|16.1|1.1|0|2|15.7|0|0|4|0.26|19.2|0|15|litchi|litchis||Ciqual 13023|
Grenade|Fruits|81|14.3|1.4|1.2|2.3|13.3|0|0.1|10|0.17|9|0|150|½ grenade|||Ciqual 13018|
Figue fraîche|Fruits|68|13.5|1.2|0|4.1|12.2|0.01|0|57|0.27|0|0|50|figue|||Ciqual 13012|
Dattes sèches|Fruits|287|64.7|1.8|0.2|7.3|64.7|0.1|0.1|45|0.9|0|0|8|datte|datte||Ciqual 13011|
Raisins secs|Fruits|322|73.2|3|0.9|4.2|70.3|0.01|0.3|54|1.7|0|0|30|poignée|||Ciqual 13046|
Pruneaux|Fruits|229|55.4|1.6|0.4|5.1|38.1|0|0.2|50|0.38|0|0|10|pruneau|||Ciqual 13042|
Abricots secs|Fruits|239|59.1|2.9|0.5|8.3|34.3|0|0.2|71|1.4|0|0|8|abricot|||Ciqual 13001|
Compote de pomme|Fruits|107|25.8|0.2|0.2|1.5|22.6|0.03|0|4|0.11|14.5|0|100|pot|compote||Ciqual 13038|
Salade de fruits|Fruits|79|18|0.6|0.2|1.4|14.2|0|0|14|0.29|22.3|0|150|portion|||Ciqual 13134|
Noix de coco (chair fraîche)|Fruits|369|4.7|3.5|34.7|10.6|4.7|0.05|30.3|14|2.27|3.1|0|30|morceau|noix de coco (chair);coco;noix de coco||Ciqual 15006|
Noix de coco râpée (sèche)|Fruits|692|7.8|6.8|66.8|13.7|6.9|0.08|60.6|26|3.32|1.5|0|10|c. à soupe|coco rape||Ciqual 15007|
Eau de coco|Fruits|12|3.3|0.2|0|0|2.5|0.05|0|14|0|0|0|250|verre|eau de coco||Ciqual 18011|
Lait de coco|Fruits|199|4.3|1.9|19.2|0.8|2.5|0.07|17|18|3.3|1|0|100|portion|lait de coco||Ciqual 18041|
"""
    // Fruits à coque et graines
    private const val C9 = """
Amandes|Fruits à coque et graines|615|9.5|18.8|51.3|12.5|4.2|0|4.1|260|3.4|0|0|1.2|amande|amandes||Ciqual 15000|
Cacahuètes|Fruits à coque et graines|623|14.8|22.8|49.1|8.6|5.9|0.02|8.4|57|1.6|0|0|1|cacahuète|arachide;cacahuetes;pistaches de terre||Ciqual 15001|
Cacahuètes grillées salées|Fruits à coque et graines|635|14.2|22.9|50.8|8.1|4.2|1.26|8.6|47|1.2|0|0|1|cacahuète|||Ciqual 15002|
Noix de cajou|Fruits à coque et graines|618|21.3|17.4|48.1|8.4|6.6|0.01|8.2|39|5.4|0|0|1.5|noix|cajou||Ciqual 15054|
Noix (cerneaux)|Fruits à coque et graines|709|6.9|13.3|67.3|6.7|3|0|6.5|75|2.2|0.8|0|5|cerneau|noix||Ciqual 15005|
Noisettes|Fruits à coque et graines|632|7.2|14.4|56.9|11.6|4.9|0|4.8|120|3|0|0|1|noisette|||Ciqual 15004|
Pistaches|Fruits à coque et graines|631|14.9|18.4|51.6|10.2|7.8|0.01|6.5|107|4.03|3|0|0.7|pistache|||Ciqual 15044|
Beurre de cacahuète|Fruits à coque et graines|643|17.3|22.2|51.4|5|10.5|1.07|10.1|49|1.74|0|0|15|c. à soupe|pate d arachide;beurre d arachide||Ciqual 15202|
"""
    // Produits laitiers
    private const val C10 = """
Lait demi-écrémé|Produits laitiers|48|5|3.5|1.6|0|5|0.09|1|121|0|0|0|250|verre|lait demi-écrémé;lait;lait demi ecreme||Ciqual 19041|
Lait entier|Produits laitiers|65|4.8|3.5|3.5|0|4.6|0.09|2.1|119|0|0|0.13|250|verre|lait entier||Ciqual 19023|
Lait écrémé|Produits laitiers|34|5|3.6|0|0|4.7|0.09|0|119|0|0|0|250|verre|||Ciqual 19050|
Lait concentré sucré|Produits laitiers|327|56.5|7.7|7.9|0|56.5|0.21|5.2|290|0.19|2.6|0.2|20|c. à soupe|lait concentre||Ciqual 19027|
Lait concentré non sucré|Produits laitiers|119|9.9|6.4|5.9|0|9.5|0.24|4|292|0.02|1.9||20|c. à soupe|lait evapore||Ciqual 19026|
Yaourt nature|Produits laitiers|50|4.3|3.8|1.7|0|4|0.1|1.1|137|0|0|0|125|yaourt|yaourt nature;yaourt;yogourt|F|Ciqual 19593|
Yaourt nature sucré|Produits laitiers|82|10.7|2.9|2.8|0|10.1|0.07|1.8|114|0|0|0|125|yaourt||F|Ciqual 19599|
Yaourt nature 0%|Produits laitiers|39|4.9|4.2|0|0|4.6|0.11|0|152|0|0|0|125|yaourt||F|Ciqual 19594|
Yaourt aux fruits|Produits laitiers|89|12.6|3.2|2.4|0.6|11.6|0.1|1.6|118|0|0|0|125|yaourt||F|Ciqual 19587|
Yaourt aromatisé|Produits laitiers|86|12.7|3.1|2.2|0|12.5|0.08|1.4|123|0|0|0|125|yaourt||F|Ciqual 19575|
Yaourt à la grecque sur lit de fruits|Produits laitiers|126|14.4|2.3|6.5|0.3|13.5|0.1|4.3|104|0.07|0||125|pot|yaourt grec|F|Ciqual 19552|
Yaourt à boire|Produits laitiers|66|10.7|3.2|0.9|0.1|9.7|0.09|0.6|140|0.27|0|1.03|200|bouteille||F|Ciqual 19508|
Fromage blanc 3%|Produits laitiers|76|3.9|7.3|3.2|0|3.9|0.09|2.1|131|0|0|0|100|portion|fromage blanc||Ciqual 19646|
Fromage blanc 0%|Produits laitiers|48|4.2|7.3|0|0|3.3|0.09|0|150|0|0|1.28|100|portion|||Ciqual 19644|
Kéfir de lait|Produits laitiers|61|4.5|3.2|3.4|0|3.4|0.12|2.3|127|0.05|0.3|0.1|200|verre|kefir;kéfir de lait|F|Ciqual 19865|
Crème dessert chocolat|Produits laitiers|121|17.9|3.5|3.5|1.5|15.4|0.16|2.3|113|1.8|0|0|125|pot|creme dessert||Ciqual 39200|
Mousse au chocolat|Produits laitiers|155|20.3|5.1|5.5|1.8|19.3|0.18|3.6|149|4.64|0|0|60|pot|||Ciqual 39206|
Riz au lait|Produits laitiers|121|18.9|3|3.4|1.2|10.8|0.11|2.1|74|0|0|0|125|pot|||Ciqual 39212|
Crème fraîche épaisse 30%|Produits laitiers|285|1.4|2.3|29.9|0|1.4|0.07|19.5|78|0|0|0|15|c. à soupe|creme fraiche||Ciqual 19410|
Crème fraîche légère|Produits laitiers|173|4.3|3|16|0|4.2|0.15|9.4|105||||15|c. à soupe|creme legere||Ciqual 19436|
Crème liquide 30%|Produits laitiers|293|3.7|2.2|30|0|2.9|0.07|18.6|75|0|2.4|0|15|c. à soupe|creme liquide||Ciqual 19417|
Crème chantilly|Produits laitiers|305|10.6|2.1|28.2|0|10.4|0.07|18.9|101|0.05|0|0.4|10|c. à soupe|chantilly||Ciqual 19420|
"""
    // Fromages
    private const val C11 = """
Emmental|Fromages|373|0|27.9|28.8|0|0|0.61|19.7|978|0.15|0|0.78|30|portion|fromage (emmental);emmental;gruyere||Ciqual 12115|
Emmental râpé|Fromages|368|0.6|27.6|28.2|0|0|0.68|18.1|977|0|0|0|10|c. à soupe|fromage rape||Ciqual 12118|
Comté|Fromages|413|0|27.8|33.8|0|0|0.84|21.7|907|0.49|||30|portion|||Ciqual 12110|
Camembert|Fromages|280|0|19.5|22.5|0|0|1.45|15.8|439|0.22|0|0.4|30|portion|||Ciqual 12001|
Brie|Fromages|345|0.7|17.6|30.4|0.1|0.1|1.43|21.5|420|0.42|0|0.39|30|portion|||Ciqual 12020|
Mimolette|Fromages|331|0|28.9|24.2|0|0|2.44|16.1|844||||30|portion|||Ciqual 12735|
Gouda|Fromages|369|0|24.7|30.2|0|0|1.95|20.1|774|0.24|0|0.5|30|portion|||Ciqual 12736|
Edam|Fromages|320|0|25.7|24.4|0|0|2.22|16.7|812|0.12|0|0.5|30|portion|||Ciqual 12729|
Cheddar|Fromages|399|1.2|24.7|32.6|0|0.1|1.77|20.1|739|0|0|0|20|tranche|||Ciqual 12726|
Parmesan|Fromages|411|1.1|31.1|31|0|0|1.57|20.6|980|0.1|0|0|10|c. à soupe|||Ciqual 12120|
Mozzarella|Fromages|227|0.7|16.5|17.7|0|0.7|0.6|11.7|598|0.44|0|0.4|125|boule|||Ciqual 19590|
Feta|Fromages|284|0.5|15.1|24.3|0.2|0|2.27|16.8|220|0.08||0|30|portion|||Ciqual 12066|
Roquefort|Fromages|384|0|19.5|33.9|0|0|3.22|24.9|660|0.1|0||30|portion|||Ciqual 12500|
Reblochon|Fromages|322|0|20.3|26.9|0|0|1.38|19|471|0.32|||30|portion|||Ciqual 12045|
Raclette (fromage)|Fromages|342|0|23.4|27.5|0|0|1.69|18.5|658||||40|tranche|raclette||Ciqual 12749|
Fromage de chèvre frais|Fromages|194|2.5|12|15.2|0|2.2|1.15|10.7|170|0.26|0|0|30|portion|||Ciqual 12805|
Chèvre bûche|Fromages|285|0|18.8|23.3|0|0|1.58|16.2|190|0.22||0|30|rondelle|chevre||Ciqual 12812|
Fromage fondu (portion)|Fromages|238|8.5|11.2|17.6|0.1|5.8|1.84|12.3|543|0||0|17|portion|vache qui rit;fromage fondu||Ciqual 12303|
Fromage fondu en tranche|Fromages|246|5.3|14.1|18.5|0|5|2.46|12.3|436|0.12||0|20|tranche|||Ciqual 12300|
Fromage affiné au lait cru (camembert lait cru)|Fromages|267|0|20.7|20.3|0|0|1.45|14.1|380|0.09||0|30|portion|fromage affiné au lait cru|F|Ciqual 12006|
"""
    // Matières grasses et sauces
    private const val C12 = """
Beurre doux|Matières grasses et sauces|753|0.7|0.6|83|0|0.7|0.04|60|20|0|0|0|10|noisette|beurre||Ciqual 16400|
Beurre demi-sel|Matières grasses et sauces|725|0.5|0.8|80|0|0.5|1.9|56.5|18|0.13|0|1.13|10|noisette|||Ciqual 16402|
Margarine|Matières grasses et sauces|557|0.4|0.3|61.7|0|0.3|0.45|21.8|10|0.19|1|3.42|10|noisette|||Ciqual 16601|
Huile d'olive|Matières grasses et sauces|899|0|0.2|99.9|0|0|0|15.2|0|0|0|0|10|c. à soupe|huile d'olive;huile olive||Ciqual 17270|
Huile de tournesol|Matières grasses et sauces|900|0|0.2|100|0|0|0.03|11.1|0|0|0|0|10|c. à soupe|huile;huile tournesol||Ciqual 17440|
Huile de colza|Matières grasses et sauces|900|0|0|100|0|0|0|7.3|0|0.02|0|0|10|c. à soupe|||Ciqual 17130|
Huile d'arachide|Matières grasses et sauces|899|0|0.2|99.9|0|0|0|16.2|1|0|0|0|10|c. à soupe|||Ciqual 17040|
Huile de coco|Matières grasses et sauces|897|0.4|0|99.5|0|0|0|84.5|1|0.04|0|0|10|c. à soupe|||Ciqual 16040|
Mayonnaise|Matières grasses et sauces|692|3.4|1.3|74.5|0.3|1.2|1.4|6.8|18|0.34|0|0|15|c. à soupe|mayo||Ciqual 11054|
Vinaigrette|Matières grasses et sauces|475|2.9|0.6|51|0.8|2.1|2.2|5.5|20|0.64|0|0|15|c. à soupe|sauce salade||Ciqual 11110|
Ketchup|Matières grasses et sauces|108|23.7|1.2|0.2|1.8|21.1|2.35|0|21|0.77|10.2|0|15|c. à soupe|||Ciqual 11008|
Moutarde|Matières grasses et sauces|151|4.3|6.9|11.2|0|1.7|6.3|1.1|207|2.64||0|5|c. à café|||Ciqual 11013|
Sauce soja|Matières grasses et sauces|40|1.7|7.2|0|0.9|0|13.1|0|29|2.1|2.6|0|10|c. à soupe|||Ciqual 11104|
Sauce barbecue|Matières grasses et sauces|136|30.3|0.6|0.6|1.2|27.5|1.77|0.1|46|0.93|0|0|15|c. à soupe|||Ciqual 11100|
Sauce béchamel|Matières grasses et sauces|105|6.2|2.6|7.4|1.3|3.1|0.69|4.6|82|0.1|0|0|50|portion|||Ciqual 11101|
Sauce bolognaise|Matières grasses et sauces|94|7.3|4.2|4.8|2.3|4.7|1.18|1.8|23|0.87|7.5|0|100|portion|||Ciqual 11114|
Sauce tomate|Matières grasses et sauces|79|7.7|1.5|4.2|2.2|5.4|1.17|0.5|24|0.58|5.5|0|100|portion|coulis de tomate||Ciqual 11107|
Pesto|Matières grasses et sauces|370|6.6|3.9|35.4|2.5|3.2|2.64|4.4|150|0.59|92.6|0|15|c. à soupe|||Ciqual 11179|
Aïoli|Matières grasses et sauces|480|5.4|1.1|50.2|0.2|2.6|1.82|4.8|||||15|c. à soupe|||Ciqual 11168|
Sauce blanche kebab|Matières grasses et sauces|433|6.9|1.2|44.1|0.3|4.3|1.65|3.4|||||15|c. à soupe|sauce blanche||Ciqual 11203|
Sauce crudités|Matières grasses et sauces|303|6.2|0.6|30.5|0.6|4.2|2.33|2.5|28|0.19|0|0|15|c. à soupe|||Ciqual 11187|
Olives|Matières grasses et sauces|165|0.7|1.3|16.6|3.8|0|2.83|2.3|77|1.16|7.1|0|4|olive|olive||Ciqual 13184|
Cornichons|Matières grasses et sauces|16|0.8|1.1|0|1.5|0.6|1.72|0|65|0.25|0|0|10|cornichon|||Ciqual 11004|
"""
    // Sucré
    private const val C13 = """
Sucre blanc|Sucré|399|99.7|0|0|0|99.7|0.01|0|4|0.08|0|0|5|morceau|sucre;sucre en poudre||Ciqual 31016|
Sucre roux / de canne|Sucré|393|98.1|0.1|0|0|97|0.07|0|83|0.71|0|0|5|c. à café|sucre de canne;cassonade;sucre roux||Ciqual 31017|
Miel|Sucré|331|82.1|0.7|0|0|81.1|0.01|0|8|0.18|0.5|0|10|c. à café|||Ciqual 31008|
Confiture|Sucré|244|59.3|0.3|0.3|1.3|57.7|0.01|0.1|||16.2||15|c. à soupe|confiture||Ciqual 31006|
Pâte à tartiner chocolat-noisette|Sucré|549|57.9|5|32.4|3.2|56.2|0.12|9.2|100|3.85|0.8|0|15|c. à soupe|nutella;pate a tartiner||Ciqual 31032|
Chocolat noir|Sucré|521|53|6|29.6|8.3|46.3|0.04|19|40|3.5|0|0|10|carré|chocolat noir||Ciqual 31005|
Chocolat noir 70%|Sucré|591|26.9|10.4|46.3|12.8|17.9|0.02|28.7|62|11|0|2.16|10|carré|||Ciqual 31074|
Chocolat au lait|Sucré|550|58.9|7.5|30.8|2.9|56.3|0.2|18.7|220|3.6|1.6|1.5|10|carré|chocolat||Ciqual 31004|
Chocolat blanc|Sucré|563|57.1|6.2|34.2|1|56|0.22|21.3|281|0.2|0|0|10|carré|||Ciqual 31010|
Barre chocolatée biscuitée|Sucré|492|61.4|5.6|24.1|3.7|43.3|0.33|14|126|3.2|0|0|50|barre|twix||Ciqual 31000|
Barre chocolatée (type Mars/Snickers)|Sucré|496|66.4|3.7|24.1|0.6|47|0.5|14|100|2.49|0.5|0.09|50|barre|mars;snickers;barre chocolatee||Ciqual 31001|
Barre de céréales|Sucré|379|74.1|5.5|7.1|3.8|29.6|0.63|3.4|84|2.18|0|0|25|barre|barre cereales||Ciqual 31113|
Bonbons gélifiés|Sucré|339|76.9|4.9|0.5|1.5|58.4|0.02|0.2|0|0|0|0|5|bonbon|bonbons;haribo||Ciqual 31060|
Bonbons durs / sucettes|Sucré|384|95.3|0.2|0.2|0.9|70.4|0.09|0.1|3|0.3|0|0|5|bonbon|||Ciqual 31059|
Nougat|Sucré|437|69.3|7|13.9|3.3|60.8|0.04|1.2|||||30|barre|||Ciqual 31127|
Cacahuètes caramélisées (chouchous)|Sucré|544|51.4|12.9|29.7|6.1|44.7|0.01|4|||||30|sachet|chouchou||Ciqual 15058|
Petit-beurre|Sucré|450|73.4|7.8|13.3|2.5|22|1.04|8.6|34|0.82|0|0|8|biscuit|biscuits;petit beurre||Ciqual 24015|
Biscuits secs|Sucré|433|67.8|7.8|13.8|3|25|1.02|3.5|10|0.4|0|0|8|biscuit|biscuit||Ciqual 24000|
Cookie au chocolat|Sucré|493|61|6.4|23.8|4.3|31.4|0.86|11.4|39|6.1|0|0|15|cookie|cookie;cookies||Ciqual 24684|
Biscuit fourré chocolat (type Oreo)|Sucré|480|69.2|5.1|19.6|3.2|35.7|0.65|5.9|21|2.3|0|0|11|biscuit|oreo||Ciqual 24224|
Sablés / galettes au beurre|Sucré|481|66.8|6.8|20|3.2|22.2|0.96|12|22|0.85|0|0|8|biscuit|sable||Ciqual 24049|
Madeleine|Sucré|441|55.3|5.5|21.6|1.4|28.4|0.83|2.8|19|0.59|0|0.33|25|madeleine|madeleines||Ciqual 24632|
Gâteau au chocolat|Sucré|445|51|5.9|24|3.7|28.6|0.42|5.7|39|4.5|0|0|80|part|gateau chocolat;brownie||Ciqual 23585|
Gâteau au yaourt / cake|Sucré|409|52|5.9|19.1|2.7|27.7|0.89|2.2|||||60|part|cake;gateau au yaourt||Ciqual 23588|
Gâteau (moyenne)|Sucré|410|51.8|5.5|19.6|2.4|29.3|0.72|5.1|34|2.02|0.5|0.21|80|part|gateau||Ciqual 23000|0.1
Génoise / gâteau moelleux|Sucré|378|57.6|6.7|13.2|0.8|30.5|0.76|2.3|31||||60|part|||Ciqual 23594|
Tarte aux pommes|Sucré|227|32.7|2.5|8.8|3.3|20.7|0.31|4.7|4|0.55|5|0|120|part|||Ciqual 23490|
Tarte au citron|Sucré|319|43.1|5.6|13.6|1|34.1|0.3|7.7|25|1.2|13.4|0|120|part|||Ciqual 23485|
Tarte aux fruits|Sucré|245|34.1|3.2|10|2.4|21.6|0.26|5.5|23|0.87|5.8|0|120|part|tarte||Ciqual 23498|
Flan pâtissier|Sucré|211|33.2|4|6.8|0.5|17.5|0.2|4.1|72|0.35|0|0|120|part|flan patissier||Ciqual 23525|
Crêpe nature|Sucré|257|41.6|6.3|6.8|2.2|19.1|0.52|3.6|75|0.56|0|0|60|crêpe|crepe;crepes||Ciqual 23799|
Crêpe au chocolat|Sucré|388|54.3|7.8|15.1|2.2|20.4|0.61|6.1|73|2.43|0.2|0.49|80|crêpe|crepe nutella||Ciqual 23821|
Gaufre|Sucré|301|36.1|8|13.4|1.9|4.8|0.99|5.1|48|0.89|0|0.61|60|gaufre|||Ciqual 23850|
Pancake|Sucré|358|45.3|5.5|16.6|2.2|25.6|1.37|2.1|52|0.54|0|0|40|pancake|pancakes||Ciqual 23827|
Beignet / donut sucré|Sucré|404|39.4|7.2|23.7|2|10.7|1.11|9.5|24|0.68|0|0|60|beignet|donut;beignet||Ciqual 23880|
Muffin au chocolat|Sucré|444|52.5|5.6|23|2.4|30.2|0.93|5.3|46||||75|muffin|muffin||Ciqual 23952|
Pain d'épices|Sucré|318|71.8|3.1|1.1|3.8|40.8|0.62|0.2|4|1.1|0||30|tranche|||Ciqual 23200|
Glace / crème glacée|Sucré|249|32|2.7|11.9|1.1|25.3|0.14|8.4|84|1.36|1.1|0.05|50|boule|glace;creme glacee;sorbet||Ciqual 39500|0.3
Cornet de glace|Sucré|290|36.8|3.5|13.5|0|25.1|0.13|9.8|109|0.95|1|0|90|cornet|||Ciqual 39509|1
Glace bâtonnet enrobé chocolat|Sucré|329|33.3|3.3|20|1.2|27.8|0.17|14|94|3.22|0.4|0.13|70|bâtonnet|esquimau;magnum||Ciqual 39503|
Glace à l'eau|Sucré|84|20.5|0.2|0.2|0.3|19.4|0.02|0.1|6|0.01|2.4|0|50|bâtonnet|||Ciqual 39526|
"""
    // Boissons
    private const val C14 = """
Eau|Boissons|0|0|0|0|0|0|0.01|0|7|0.03|0|0|250|verre|eau du robinet||Ciqual 18066|
Eau minérale|Boissons|0|0|0|0|0|0|0|0|19|0|0|0|250|verre|||Ciqual 18044|
Soda cola|Boissons|40|10|0|0|0|10|0.01|0|2|0.11|0|0|330|canette|coca;cola;coca cola||Ciqual 18018|
Cola zéro / light|Boissons|1|0.2|0|0|0|0|0.01|0|2|0.07|0|0|330|canette|coca zero;coca light||Ciqual 18060|
Soda sucré (limonade, etc.)|Boissons|34|8.3|0|0|0|8.3|0.01|0|3|0.18|0|0|330|canette|soda sucré;soda;sprite;fanta||Ciqual 18026|
Soda aux fruits|Boissons|34|8.4|0|0|0|8.2|0.01|0|||||330|canette|orangina||Ciqual 18049|
Limonade|Boissons|33|8.2|0|0|0.1|8.2|0|0|7|0.01|0|0|250|verre|||Ciqual 18010|
Thé glacé sucré (ice tea)|Boissons|23|5.8|0|0|0.1|5.7|0.02|0|2|0.01|22.4|0|330|canette|ice tea;the glace||Ciqual 18075|
Sirop dilué dans l'eau|Boissons|31|7.7|0.1|0|0|7.7|0.01|0|8|0.03|1.3|0|250|verre|sirop a l eau;sirop||Ciqual 18058|
Sirop pur (à diluer)|Boissons|252|62.5|0.2|0|0|58.8|0.02|0|3|0.06|0||20|dose|||Ciqual 18017|
Jus d'orange|Boissons|45|9.6|0.6|0.1|0.3|9.6|0.01|0.1|0|0.07|37|0|200|verre|jus d'orange;jus orange||Ciqual 2070|
Jus de pomme|Boissons|49|11.4|0.2|0.1|0.2|10.9|0.02|0|4|0.3|14.5|0|200|verre|||Ciqual 2074|
Jus d'ananas|Boissons|53|12.1|0.4|0.1|0.2|12.1|0.01|0|8|0.18|14|0|200|verre|||Ciqual 2073|
Jus de fruits|Boissons|47|10.2|0.5|0.1|0.3|10|0.01|0|4|0.11|32.2|0|200|verre|jus;jus de fruit||Ciqual 2004|
Jus / nectar de goyave|Boissons|48|10.9|0.1|0.2|0.1|10.2|0|0|8|0.07|36|0|200|verre|jus de goyave;nectar goyave||Ciqual 2367|
Jus de maracudja (pur jus)|Boissons|62|14.3|0.7|0.2|0.2|14.3|0.01|0|4|0.36|18.2|0|200|verre|jus de fruit de la passion;jus maracuja||Ciqual 2024|
Nectar de maracudja|Boissons|68|15.2|0.3|0.1|0|13|0.03|0|2||17.4||200|verre|jus de maracudja sucre||Ciqual 2365|
Nectar de mangue|Boissons|52|11.3|0.2|0.1|0.2|11.3|0.01|0|8|0.08|8.2||200|verre|jus de mangue||Ciqual 2370|
Jus d'acérola (cerise pays)|Boissons|23|4.5|0.4|0.3|0.3|4.5|0.01|0.1|10|0.5|1600||200|verre|jus de cerise pays;acerola||Ciqual 2040|
Jus de citron vert|Boissons|26|1.7|0.2|0.2|0.5|1.7|0.03|0|12|0.23|6.4|0|10|c. à soupe|jus de citron;jus citron vert||Ciqual 2031|
Smoothie|Boissons|60|12.9|0.6|0.3|1.1|11.7|0.01|0.2|10|0.24|20.2||250|verre|||Ciqual 2500|
Café noir|Boissons|6|1.4|0.2|0|0|0|0.01|0|4|0|0|0|150|tasse|cafe||Ciqual 18004|
Expresso|Boissons|7|1.2|0.2|0.2|0|0|0.01|0.1|4|0|0|0|40|tasse|espresso||Ciqual 18071|
Café au lait / cappuccino|Boissons|22|2.3|1.4|0.8|0|2.3|0.06|0.5|62|0.13||0.03|200|tasse|cappuccino;cafe au lait||Ciqual 18151|
Thé|Boissons|0|0|0.1|0|0|0|0|0|1|0.01|0|0|250|tasse|the;tisane;infusion||Ciqual 18020|
Chocolat chaud|Boissons|66|9.5|3.8|1.4|0|8.7|0.12|0.8|97|0.87|0|0.08|250|tasse|chocolat chaud;cacao||Ciqual 18104|
Boisson au soja|Boissons|46|2.9|3.2|2|0.8|1.8|0.07|0.3|30|0.53|0|0.43|250|verre|lait de soja||Ciqual 18113|
Boisson à l'amande|Boissons|36|0.7|1.1|3.2|0|0|0.05|0.3|12|0.1|0|0|250|verre|lait d amande||Ciqual 18111|
Kombucha|Boissons|7|1.4|0.2|0|0|1.4|0.01|0|6|0|0|0|250|verre|kombucha|F|Ciqual 18025|
Bière|Boissons|39|3.1|0.4|0|0|0.1|0|0|6|0.01|0|0|330|canette|biere||Ciqual 5001|3.6
Bière forte|Boissons|62|4.6|0.5|0|0||0.02|0|4|0.02|0|0|330|canette|||Ciqual 5002|6
Bière sans alcool|Boissons|29|6.1|0.2|0.1|0|3.3|0.01|0|5|0.01||0|330|canette|||Ciqual 5030|0.6
Vin rouge|Boissons|76|0|0.2|0|0|0|0|0|6|0.36|0|0|125|verre|vin||Ciqual 5214|10.8
Vin blanc|Boissons|55|0.2|0.2|0|0|0.2|0.01|0|7|0.34|0|0|125|verre|||Ciqual 5215|7.8
Vin rosé|Boissons|69|1.4|0.1|0|0|1.4|0.01|0|12|0.95|0|0|125|verre|||Ciqual 5216|9
Champagne|Boissons|76|1.4|0.3|0|0|1.4|0|0|7|0.11|0|0|125|coupe|||Ciqual 5207|9.9
Rhum|Boissons|234|0|0|0|0|0|0|0|0|0.12|0|0|40|verre (4 cl)|rhum agricole;rhum blanc;rhum vieux||Ciqual 1004|33.4
Whisky|Boissons|252|0.1|0|0|0|0.1|0|0|0|0.02|0|0|40|verre (4 cl)|||Ciqual 1005|36
"""
    // Plats et snacks courants
    private const val C15 = """
Pizza|Plats et snacks courants|234|27.4|10|8.9|2.1|3.6|1.21|4.1|193|0.76|3.4|0.16|120|part|pizza||Ciqual 25516|
Pizza margherita|Plats et snacks courants|224|29.6|8.6|7.4|2.3|3.4|1.14|3.1|239|1.2|5.5|0.1|120|part|||Ciqual 25404|
Pizza royale (jambon, fromage, champignons)|Plats et snacks courants|208|25.9|9.5|6.9|2.2|3.4|1.23|3|155|0.7|4.8|0|120|part|||Ciqual 25548|
Quiche lorraine|Plats et snacks courants|269|21.5|8.4|16.4|1.4|2|1.01|9.2|116|0.63|0.7|1.21|150|part|quiche||Ciqual 25405|
Lasagnes bolognaise|Plats et snacks courants|134|13.5|6.3|5.7|1.9|2.8|0.97|2.5|45|0.71|2.5|0.08|300|portion|lasagnes||Ciqual 25081|
Pâtes bolognaise|Plats et snacks courants|116|13.6|5.3|4.1|1.9|2.6|0.83|1.4|14|0.79|4.2|0|300|assiette|spaghetti bolognaise||Ciqual 25085|
Pâtes carbonara|Plats et snacks courants|161|14.2|5.9|8.7|1.2|1.8|0.86|4.3|36|0.45|0.8|0.1|300|assiette|carbonara||Ciqual 25135|
Hachis parmentier|Plats et snacks courants|138|9.4|6|8.2|1.4|1.5|0.83|4.7|60|0.71|0.7|0.1|300|portion|||Ciqual 25009|
Gratin dauphinois|Plats et snacks courants|113|14.5|3.4|4.2|1.5|2.1|0.81|2.7|39|0.32|3.1|0|200|portion|gratin||Ciqual 25056|
Gratin de légumes|Plats et snacks courants|107|3.6|3.1|8.5|1.7|1.9|0.5|2.5|83|0.51|12.6|0.08|200|portion|||Ciqual 25238|
Couscous au poulet|Plats et snacks courants|130|15.6|7.5|3.9|1.5|2.9|0.79|1.2|18|0.86|4|0.01|350|assiette|couscous||Ciqual 25138|
Chili con carne|Plats et snacks courants|118|13|7.1|3.4|3.6|2.6|0.93|1.1|33|1.6|2.2|0|300|assiette|chili||Ciqual 25111|
Riz cantonais|Plats et snacks courants|164|23.6|5.5|4.9|1.7|1.3|0.86|0.8|17|0.4|1|0|300|assiette|riz frit||Ciqual 25088|
Nouilles sautées (bœuf/poulet/légumes)|Plats et snacks courants|140|18.8|4.8|4.3|2.8|2|1.2|0.5|||||300|assiette|nouilles||Ciqual 9085|
Poulet curry coco|Plats et snacks courants|125|4.1|10.6|6.9|1.1|1.9|1.07|3|18|0.51|2.5|0|300|assiette|poulet curry;curry||Ciqual 25174|
Blanquette de veau|Plats et snacks courants|113|3.1|15.4|4|0.8|0.3|0.74|2|15|0.92|0|0|300|assiette|||Ciqual 25001|
Pot-au-feu|Plats et snacks courants|78|3.2|10.9|2|1.5|1.5|0.32|0.8|24|1.69|||350|assiette|||Ciqual 25013|
Tajine de poulet|Plats et snacks courants|127|14.4|7.5|3.7|3|6.8|1.17|0.4|18|0.45|0|0|300|assiette|tajine||Ciqual 25204|
Cheeseburger|Plats et snacks courants|290|22.3|13.7|15.8|1.4|3.9|1.23|4.9|88|1.4|0|0|120|burger|cheeseburger||Ciqual 25414|
Hamburger|Plats et snacks courants|251|29.5|13.3|8.4|1.8|6|1.23|2.8|64|1.23|0.3|0.1|110|burger|burger||Ciqual 25413|
Burger au poulet|Plats et snacks courants|249|23.8|12.7|11|1.2|2.9|1.16|1.9|66|0.6|0|0|150|burger|||Ciqual 25502|
Sandwich kebab|Plats et snacks courants|232|17|15.2|10.9|2.3|1.1|1.23|4.3|34|0.95|||350|sandwich|kebab;grec||Ciqual 25428|
Sandwich jambon-beurre|Plats et snacks courants|285|35.1|11.6|10.5|1.9|2.4|1.78|6.2|23|0.95|3.1|0.21|200|sandwich|jambon beurre||Ciqual 25517|
Sandwich|Plats et snacks courants|280|30.3|12.4|11.7|2.1|2|1.56|5.3|66|1|2.6|0.36|200|sandwich|sandwich||Ciqual 25522|
Wrap poulet|Plats et snacks courants|222|23.6|9.6|9.2|2.9|2.2|1.18|1.7|||||200|wrap|wrap||Ciqual 25647|
Croque-madame|Plats et snacks courants|261|16.1|13.7|15.5|1.1|1.4|1.29|7.4|200|1.27|0|0.6|200|croque|||Ciqual 25542|
Croque-monsieur|Plats et snacks courants|253|26.1|10.6|11.4|2|2.8|1.58|5.5|251|0|1.7|0.7|150|croque|croque monsieur||Ciqual 25547|
Hot-dog|Plats et snacks courants|278|26.3|10.9|13.8|1.6|5.2|1.69|5.2|56|0.85|0|0|120|hot-dog|hot dog||Ciqual 25403|
Samoussa / bouchée frite|Plats et snacks courants|187|8.5|12.2|11.5|0.6|0.7|1.41|2.8|33|1.03|2.9|0.51|40|samoussa|samoussa;samossa||Ciqual 25438|
Sushi / maki|Plats et snacks courants|171|26.9|6.7|3.7|0.9|4.9|0.55|0.6|16|0.25|1.1|1.14|25|pièce|sushi;maki||Ciqual 25456|
Taboulé|Plats et snacks courants|179|23.7|4.9|6.7|2.5|4.9|1|0.7|24|0.88|6.3|0|150|portion|taboule||Ciqual 25608|
Salade de pommes de terre|Plats et snacks courants|127|9.9|2.7|8.2|1.3||1.32|1.4|19|0.65|10|0|150|portion|||Ciqual 25606|
Salade de riz|Plats et snacks courants|127|16.1|5.1|4.3|1.7|1|1.1|0.5|23|0.43|4.5|0|200|portion|||Ciqual 25614|
Salade César|Plats et snacks courants|151|4.9|8.1|10|2|1.7|0.75|2.4|100|0.5|1.1|0|250|portion|||Ciqual 25628|
Soupe de légumes|Plats et snacks courants|40|4.8|1.4|1.4|0.9|1.6|0.71|0.6|29|0.27|5.4||250|bol|soupe;potage||Ciqual 25969|
Soupe de poisson|Plats et snacks courants|50|2.7|4.1|2.4|0.8|0.9|0.91|0.8|130|0.61|0|0|250|bol|||Ciqual 25904|
Beignet de viande ou poisson|Plats et snacks courants|238|10|18.7|13.5|0.4|0.9|0.71|2.9|37|1.23|0.3|0.29|40|beignet|||Ciqual 25551|
Pop-corn salé|Plats et snacks courants|477|50.2|8.3|24.6|10.6|2|2.61|11.9|10|2.3||0|30|portion|pop corn||Ciqual 9230|
"""
    // Fermentés et prébiotiques (suivi microbiote)
    private const val C16 = """
Choucroute crue|Fermentés et prébiotiques (suivi microbiote)|20|3|1|0.1|||||||||100|portion|choucroute|F|valeur approximative (versions précédentes)|
Kimchi|Fermentés et prébiotiques (suivi microbiote)|25|3.5|1.5|0.5|||||||||50|portion||F|valeur approximative (versions précédentes)|
Miso|Fermentés et prébiotiques (suivi microbiote)|188|18.1|11.7|6|5.4|6.2|9.32|1.1|57|2.49|0|0|15|c. à soupe||F|Ciqual 20916|
Tempeh|Fermentés et prébiotiques (suivi microbiote)|157|7.9|16.1|4.7|6.2|0.2|0.1|0.6|88|1.7|0|0|100|portion||F|Ciqual 20917|
Banane peu mûre|Fermentés et prébiotiques (suivi microbiote)|90|20|1.1|0.3|||||||||120|banane|banane verte|P|valeur approximative (versions précédentes)|
Chicorée soluble (poudre)|Fermentés et prébiotiques (suivi microbiote)|323|57.2|3.3|2.5|29.2|21.7|0.39|0.8|109|5.5|0|0|3|c. à café|chicoree|P|Ciqual 18152|
"""
    val TSV: List<String> = listOf(C0, C1, C2, C3, C4, C5, C6, C7, C8, C9, C10, C11, C12, C13, C14, C15, C16)
}
