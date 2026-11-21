import os, re, sys, json
import numpy as np
from PIL import Image
SRC = "/home/claude/yahiaangelo/film-luts/luts"
OUT = "/home/claude/mlfilm/app/src/main/assets/luts"
KT = "/home/claude/mlfilm/app/src/main/java/com/marco/mlfilm/FilmCatalog.kt"
CATS = {"bw":"B&W","colorslide":"Slide","fujixtransiii":"Fuji X","instant_consumer":"Instant","instant_pro":"Instant Pro",
        "negative_color":"Color Neg","negative_new":"Negative","negative_old":"Vintage Neg","print":"Print"}
os.makedirs(OUT, exist_ok=True)
items=[]
def pretty(stem):
    suf = ''
    m = re.search(r'_(\+\+|\+|-)$', stem)
    if m:
        suf = {'++': ' ++', '+': ' +', '-': ' \u2212'}[m.group(1)]
        stem = stem[:m.start()]
    return pretty0(stem) + suf
def pretty0(stem):
    s = re.sub(r'[_\-]+',' ',stem).strip()
    s = re.sub(r'\s+',' ',s)
    return ' '.join(w if w.isdigit() else w.capitalize() for w in s.split())
for cat in sorted(os.listdir(SRC)):
    for fn in sorted(os.listdir(f"{SRC}/{cat}")):
        if not fn.endswith('.cube'): continue
        n=None; rows=[]
        for line in open(f"{SRC}/{cat}/{fn}"):
            t=line.strip()
            if t.startswith('LUT_3D_SIZE'): n=int(t.split()[1])
            elif t and (t[0].isdigit() or t[0]=='-' or t[0]=='.'):
                p=t.split()
                if len(p)==3: rows.append([float(x) for x in p])
        if n!=13 or len(rows)!=n**3: print("SKIP",cat,fn,n,len(rows)); continue
        a=np.array(rows,dtype=np.float32).reshape(n,n,n,3)  # [b][g][r]
        a=np.clip(a,0,1)
        strip=np.zeros((n,n*n,3),dtype=np.uint8)
        for b in range(n):
            strip[:,b*n:(b+1)*n,:]=np.round(a[b]*255).astype(np.uint8)  # rows=g, cols=r
        stem=fn[:-5]
        os.makedirs(f"{OUT}/{cat}",exist_ok=True)
        Image.fromarray(strip,"RGB").save(f"{OUT}/{cat}/{stem}.png",optimize=True)
        m=re.search(r'(?<!\d)(25|50|64|100|125|160|200|250|320|400|500|640|800|1000|1600|3200)(?!\d)',stem)
        iso=int(m.group(1)) if m else 0
        items.append((f"{cat}/{stem}",pretty(stem),CATS.get(cat,cat),iso))
items.sort(key=lambda x:(list(CATS.values()).index(x[2]) if x[2] in CATS.values() else 99,x[1]))
with open(KT,"w") as f:
    f.write("package com.marco.mlfilm\n\n// Generato da tools/make_luts.py: look di pellicola come LUT 3D 13x13x13.\n")
    f.write("// Origine: raccolta Film-Luts (G'MIC film presets). Vedi assets/luts/NOTICE.txt.\n")
    f.write("val FILMS: List<FilmSpec> = listOf(\n")
    for id_,name,fam,iso in items:
        f.write(f'    FilmSpec("{id_}", "{name}", "{fam}", {iso}),\n')
    f.write(")\n")
print(len(items),"luts")
import collections; print(collections.Counter(i[2] for i in items))
