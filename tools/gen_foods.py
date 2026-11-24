#!/usr/bin/env python3
"""Génère app/src/main/java/com/wolnerman/calories/FoodData.kt depuis foods_spec.txt et la table Ciqual 2025 (JSON).
Source : Anses. 2025. Table de composition nutritionnelle des aliments Ciqual 2025. https://doi.org/10.57745/RDMHWY
Usage : python3 gen_foods.py /chemin/ciqual_2025_complete.json"""
import json, sys, re, os
CIQ = sys.argv[1] if len(sys.argv) > 1 else '/workspace/ciqual/ciqual_2025_complete.json'
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'app/src/main/java/com/wolnerman/calories/FoodData.kt')
d = {x['alim_code']: x for x in json.load(open(CIQ))}
COL = {
 'kcal': 'Energie, Règlement UE N° 1169/2011 (kcal/100 g)',
 'G': 'Glucides (g/100 g)', 'P': 'Protéines, N x facteur de Jones (g/100 g)', 'L': 'Lipides (g/100 g)',
 'fiber': 'Fibres alimentaires (g/100 g)', 'sugars': 'Sucres (g/100 g)', 'salt': 'Sel chlorure de sodium (g/100 g)',
 'satfat': 'AG saturés (g/100 g)', 'calcium': 'Calcium (mg/100 g)', 'iron': 'Fer (mg/100 g)',
 'vitc': 'Vitamine C (mg/100 g)', 'vitd': 'Vitamine D (µg/100 g)',
 'alcohol': 'Alcool (éthanol) (g/100 g)',
}
NUT = ['fiber', 'sugars', 'salt', 'satfat', 'calcium', 'iron', 'vitc', 'vitd']
def val(x, k):
    v = x['composition'].get(COL[k], {}).get('valeur', '-').strip()
    if v in ('-', ''): return None
    if v.lower() == 'traces' or v.startswith('<'): return 0.0   # sous le seuil de quantification
    return float(v.replace(',', '.'))
def ciq(code):
    x = d[code]
    r = {k: val(x, k) for k in COL}
    src = f'Ciqual {code}'
    if r['kcal'] is None and None not in (r['G'], r['P'], r['L']):
        r['kcal'] = 4*r['G'] + 4*r['P'] + 9*r['L'] + 2*(r['fiber'] or 0)
        src += ' (énergie calculée)'
    return r, src, x['alim_nom_fr']
OLD = {}  # X: valeurs des versions précédentes
LAMBI = None
def recipe(spec):
    parts = [p.split(':') for p in spec.split(',')]
    tot = sum(float(g) for _, g in parts)
    out = {k: 0.0 for k in COL}
    for code, g in parts:
        r = LAMBI if code == 'LAMBI' else ciq(code)[0]
        for k in COL:
            if out[k] is None: continue
            if r.get(k) is None: out[k] = None
            else: out[k] += r[k] * float(g) / tot
    return out, 'recette calculée (ingrédients Ciqual)'
rows, cat, names = [], None, {}
for line in open(os.path.join(HERE, 'foods_spec.txt'), encoding='utf-8'):
    line = line.rstrip('\n')
    if not line.strip() or line.startswith('# '): continue
    if line.startswith('## '): cat = line[3:].strip(); continue
    f = [c.strip() for c in line.split('|')]
    while len(f) < 6: f.append('')
    key, name, unit, ulabel, aliases, flags = f[:6]
    if key.startswith('R:'): r, src = recipe(key[2:])
    elif key.startswith('X:'):
        k, g, p, l = map(float, key[2:].split(','))
        r = {kk: None for kk in COL}; r.update(kcal=k, G=g, P=p, L=l); src = 'valeur approximative (versions précédentes)'
        if name == 'Lambi cuit': LAMBI = r
    else: r, src, _ = ciq(key)
    if r['kcal'] is None: raise SystemExit(f'kcal inconnues : {line}')
    if name in names: raise SystemExit(f'doublon : {name}')
    names[name] = 1
    rows.append((cat, name, r, unit, ulabel, aliases, flags, src))
def n(v, dec):
    if v is None: return ''
    s = f'{v:.{dec}f}'
    if '.' in s: s = s.rstrip('0').rstrip('.')
    return '0' if s == '-0' else s
def ndec(k): return {'calcium': 0, 'vitc': 1, 'iron': 2, 'vitd': 2, 'salt': 2}.get(k, 1)
cats = []
for c in dict.fromkeys(r[0] for r in rows): cats.append(c)
out = ['package com.wolnerman.calories', '',
 '// FICHIER GÉNÉRÉ par tools/gen_foods.py — ne pas modifier à la main.',
 '// Valeurs pour 100 g : Anses. 2025. Table de composition nutritionnelle des aliments Ciqual 2025.',
 '// https://doi.org/10.57745/RDMHWY (Licence Ouverte Etalab 2.0). Recettes = calcul à partir des ingrédients Ciqual.',
 '// Colonnes : nom|catégorie|kcal|glucides|protéines|lipides|fibres|sucres|sel|AG saturés|calcium mg|fer mg|vit C mg|vit D µg|',
 '//            poids unitaire g|unité|alias (;)|drapeaux F/P|source|alcool g (v2.10). Champ vide = inconnu / 0.', '',
 'internal object FoodData {']
for i, c in enumerate(cats):
    out.append(f'    // {c}')
    out.append(f'    private const val C{i} = """')
    for (cc, name, r, unit, ul, al, fl, src) in rows:
        if cc != c: continue
        vals = [str(round(r['kcal'])), n(r['G'], 1), n(r['P'], 1), n(r['L'], 1)] + [n(r[k], ndec(k)) for k in NUT]
        line = '|'.join([name, c] + vals + [unit, ul, al, fl, src, n(r['alcohol'], 1) if (r['alcohol'] or 0) > 0 else ''])
        assert '"""' not in line and '$' not in line
        out.append(line)
    out.append('"""')
out.append('    val TSV: List<String> = listOf(' + ', '.join(f'C{i}' for i in range(len(cats))) + ')')
out.append('}')
open(OUT, 'w', encoding='utf-8').write('\n'.join(out) + '\n')
print(len(rows), 'aliments,', len(cats), 'catégories ->', os.path.normpath(OUT))
nomac = [r[1] for r in rows if None in (r[2]['G'], r[2]['P'], r[2]['L'])]
print('sans macros complètes :', len(nomac), nomac)
nonut = [r[1] for r in rows if all(r[2][k] is None for k in NUT)]
print('sans aucun nutriment :', len(nonut))
