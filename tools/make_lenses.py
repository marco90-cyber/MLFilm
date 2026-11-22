#!/usr/bin/env python3
"""Genera LensCatalog.kt da profili reali del database Lensfun (CC BY-SA 3.0).

Uso: python3 tools/make_lenses.py <cartella con i file .xml di Lensfun> > app/src/main/java/com/marco/mlfilm/LensCatalog.kt
Distorsione, aberrazione cromatica e vignettatura vengono dai profili di calibrazione Lensfun.
Forma del bokeh, swirl, morbidezza e bagliore sono stime nostre, NON dati Lensfun.
"""
import glob, re, sys
import xml.etree.ElementTree as ET

def num(x, d=0.0):
    try: return float(x)
    except Exception: return d

# (id, nome, gruppo, regex modello Lensfun, focale mm, apertura max, shape, swirl, soft, bloom, dist_manuale)
W = [
 ("ef85_12","Canon EF 85mm f/1.2L II","RITRATTO",r"Canon EF 85mm f/1\.2L II",85,1.2,0,0,0,.10,None),
 ("nz85_12","Nikkor Z 85mm f/1.2 S","RITRATTO",r"Nikkor Z 85mm f/1\.2 S",85,1.2,0,0,0,.10,None),
 ("fuji56","Fujifilm XF 56mm f/1.2","RITRATTO",r"^XF56mmF1\.2 R$",56,1.2,0,0,0,.08,None),
 ("n85_18","Nikkor AF-S 85mm f/1.8G","RITRATTO",r"Nikon AF-S Nikkor 85mm f/1\.8G",85,1.8,0,0,0,0,None),
 ("sf85","Sony FE 85mm f/1.8","RITRATTO",r"^FE 85mm f/1\.8$",85,1.8,0,0,0,0,None),
 ("zeiss135","Zeiss Sonnar 135mm f/1.8","RITRATTO",r"Sonnar T\* 135mm F1\.8",135,1.8,0,0,0,.05,None),
 ("ef50_18","Canon EF 50mm f/1.8 STM","STANDARD",r"^Canon EF 50mm f/1\.8 STM",50,1.8,0,0,0,0,None),
 ("ef50_14","Canon EF 50mm f/1.4 USM","STANDARD",r"^Canon EF 50mm f/1\.4 USM",50,1.4,0,0,0,.08,None),
 ("n50_14","Nikkor AF-S 50mm f/1.4G","STANDARD",r"Nikon AF-S Nikkor 50mm f/1\.4G",50,1.4,0,0,0,.08,None),
 ("nz50","Nikkor Z 50mm f/1.8 S","STANDARD",r"Nikkor Z 50mm f/1\.8 S",50,1.8,0,0,0,0,None),
 ("sf50gm","Sony FE 50mm f/1.4 GM","STANDARD",r"FE 50mm f/1\.4 GM",50,1.4,0,0,0,.05,None),
 ("sf50_18","Sony FE 50mm f/1.8","STANDARD",r"^FE 50mm f/1\.8$",50,1.8,0,0,0,0,None),
 ("zeiss50","Zeiss Planar T* 50mm f/1.4","STANDARD",r"Carl Zeiss Planar T\* 1\.4/50 ZF\.2",50,1.4,0,0,.05,.12,None),
 ("lei43","Leica APO-Summicron 43mm f/2","STANDARD",r"APO-Summicron 1:2/43",43,2,0,0,0,0,None),
 ("ef35_14","Canon EF 35mm f/1.4L","STANDARD",r"^Canon EF 35mm f/1\.4L USM",35,1.4,0,0,0,.08,None),
 ("s35a","Sigma 35mm f/1.4 DG HSM Art","STANDARD",r"Sigma 35mm f/1\.4 DG HSM \| A",35,1.4,0,0,0,.05,None),
 ("sf35gm","Sony FE 35mm f/1.4 GM","STANDARD",r"FE 35mm f/1\.4 GM",35,1.4,0,0,0,.05,None),
 ("n35_18","Nikkor AF-S 35mm f/1.8G","STANDARD",r"Nikon AF-S Nikkor 35mm f/1\.8G ED",35,1.8,0,0,0,0,None),
 ("zeiss35","Zeiss Distagon 35mm f/2","STANDARD",r"Carl Zeiss Distagon T\* 2/35 ZF\.2",35,2,0,0,0,0,None),
 ("lei35","Leica Summilux 35mm f/1.4","STANDARD",r"Summilux-TL 1:1\.4/35",35,1.4,0,0,0,.05,None),
 ("m43_25","Leica DG Summilux 25mm f/1.4","STANDARD",r"Leica DG Summilux 25mm f/1\.4 II",25,1.4,0,0,0,.05,None),
 ("sam14","Samyang 14mm f/2.8","GRANDANGOLO",r"Samyang AF 14mm f/2\.8$",14,2.8,0,0,0,0,None),
 ("c1635","Canon EF 16-35mm f/2.8L III","ZOOM",r"Canon EF 16-35mm f/2\.8L III USM",24,2.8,0,0,0,0,None),
 ("s1635gm","Sony FE 16-35mm f/2.8 GM","ZOOM",r"^FE 16-35mm f/2\.8 GM$",24,2.8,0,0,0,0,None),
 ("z2470","Canon EF 24-70mm f/2.8L II","ZOOM",r"Canon EF 24-70mm f/2\.8L II USM",50,2.8,0,0,0,0,None),
 ("n2470","Nikkor Z 24-70mm f/2.8 S","ZOOM",r"Nikkor Z 24-70mm f/2\.8 S$",50,2.8,0,0,0,0,None),
 ("s2470gm","Sony FE 24-70mm f/2.8 GM","ZOOM",r"^FE 24-70mm f/2\.8 GM$",50,2.8,0,0,0,0,None),
 ("c70200","Canon EF 70-200mm f/2.8L IS II","ZOOM",r"^Canon EF 70-200mm f/2\.8L IS II USM$",135,2.8,0,0,0,0,None),
 ("n70200","Nikkor AF-S 70-200mm f/2.8G VR II","ZOOM",r"Nikon AF-S Nikkor 70-200mm f/2\.8G ED VR II",135,2.8,0,0,0,0,None),
 ("c100400","Canon EF 100-400mm f/4.5-5.6L IS","ZOOM",r"Canon EF 100-400mm f/4\.5-5\.6L IS USM$",200,5.6,0,0,0,0,None),
 ("ef100m","Canon EF 100mm f/2.8L Macro","MACRO",r"Canon EF 100mm f/2\.8L Macro IS USM",100,2.8,0,0,0,0,None),
 ("takumar","Pentax SMC Takumar 50mm f/1.4","VINTAGE",r"Pentax SMC Takumar 50mm f/1\.4",50,1.4,1,0.5,.25,.35,None),
 ("sam8","Samyang 8mm f/2.8 Fisheye","GRANDANGOLO",r"Samyang 8mm f/2\.8 UMC Fish-eye",8,2.8,0,0,0,0,0.95),
 ("c815","Canon EF 8-15mm f/4L Fisheye","GRANDANGOLO",r"Canon EF 8-15mm f/4L Fisheye USM",12,4,0,0,0,0,0.9),
]

def load(d):
    out = []
    for fn in sorted(glob.glob(d + '/*.xml')):
        try: root = ET.parse(fn).getroot()
        except Exception: continue
        for l in root.iter('lens'):
            cal = l.find('calibration')
            if cal is None: continue
            out.append(dict(
                model=(l.findtext('model') or '').strip(), crop=num(l.findtext('cropfactor'), 1.0),
                dist=[dict(x.attrib) for x in cal.findall('distortion')],
                tca=[dict(x.attrib) for x in cal.findall('tca')],
                vig=[dict(x.attrib) for x in cal.findall('vignetting')]))
    return out

def nearest(lst, focal):
    return min(lst, key=lambda e: abs(num(e.get('focal')) - focal))

def g_dist(e, r):
    m = e.get('model')
    if m == 'ptlens':
        a, b, c = num(e.get('a')), num(e.get('b')), num(e.get('c'))
        return a*r**3 + b*r**2 + c*r + 1 - a - b - c
    if m == 'poly3':
        k1 = num(e.get('k1')); return 1 - k1 + k1*r*r
    if m == 'poly5':
        return 1 + num(e.get('k1'))*r**2 + num(e.get('k2'))*r**4
    return 1.0

def f(x): return ("%.4f" % x).rstrip('0').rstrip('.') + "f"

def main(d):
    L = load(d)
    rows = []
    for (id_, name, grp, rx, focal, fmax, shape, swirl, soft, bloom, dman) in W:
        c = [l for l in L if re.search(rx, l['model']) and l['dist']]
        if not c:
            print("// MANCA nel database:", id_, file=sys.stderr); continue
        c.sort(key=lambda l: (abs(l['crop'] - 1.0) > 0.1, -len(l['vig']), -len(l['tca'])))
        l = c[0]
        g = g_dist(nearest(l['dist'], focal), 1.67)       # r=1 = mezza altezza; angolo ~1.67
        dist = dman if dman is not None else (1/g - 1)/0.39 if g > 0.3 else 0.0
        ca = 0.0
        if l['tca']:
            t = nearest(l['tca'], focal)
            ca = abs(num(t.get('kr'), 1) - num(t.get('kb'), 1))/2 if t.get('model') == 'linear' else abs(num(t.get('vr'), 1) - num(t.get('vb'), 1))/2
        vig = 0.0
        vs = [v for v in l['vig'] if v.get('model') == 'pa']
        if vs:
            fm = min(abs(num(v['focal']) - focal) for v in vs)
            vs = [v for v in vs if abs(num(v['focal']) - focal) == fm]
            am = min(num(v['aperture']) for v in vs)
            vs = [v for v in vs if num(v['aperture']) == am]
            v = vs[len(vs)//2]
            gain = 1 + num(v['k1']) + num(v['k2']) + num(v['k3'])   # pa: Cd = Cs*(1+k1 r^2+k2 r^4+k3 r^6), r=1 all'angolo
            vig = max(0.0, 1 - gain)
        rows.append((id_, name, grp, focal, fmax, max(-0.6, min(1.2, dist)), min(ca, 0.01), min(0.5, vig*0.5), shape, swirl, soft, bloom))
    print("package com.marco.mlfilm\n")
    print("// Generato da tools/make_lenses.py.")
    print("// Distorsione, aberrazione cromatica e vignettatura: profili reali del database Lensfun (CC BY-SA 3.0, https://lensfun.github.io/).")
    print("// Forma del bokeh, swirl, morbidezza e bagliore: stime nostre, non dati Lensfun.")
    print("// Vedi anche NOTICE-LENSFUN.md.\n")
    print("val LENSES: List<LensSpec> = listOf(")
    print('    LensSpec("phone", "Smartphone", 1.0f, f = 0f, group = "BASE"),')
    for (id_, name, grp, focal, fmax, dist, ca, vig, shape, swirl, soft, bloom) in rows:
        z = "%.2ff" % max(1.0, focal/29.0)
        print('    LensSpec("%s", "%s", %s, f = %s, focal = %d, group = "%s", real = true, dist = %s, ca = %s, vig = %s%s%s%s%s),' % (
            id_, name, z, f(fmax), focal, grp, f(round(dist, 3)), f(round(ca, 4)), f(round(vig, 2)),
            ", shape = %d" % shape if shape else "", ", swirl = %sf" % swirl if swirl else "",
            ", soft = %s" % f(soft) if soft else "", ", bloom = %s" % f(bloom) if bloom else ""))
    # lenti "di carattere": parametri nostri
    print('    LensSpec("swirl", "Swirl 58mm f/1.4", 2.0f, f = 1.4f, focal = 58, group = "SPECIALI", swirl = 1f, vig = 0.24f, ca = 0.003f),')
    print('    LensSpec("cat", "Occhio di gatto 50mm f/1.2", 1.7f, f = 1.2f, focal = 50, group = "SPECIALI", cat = 1f, vig = 0.25f, bloom = 0.1f),')
    print('    LensSpec("soft", "Soft 90mm f/2.8", 3.1f, f = 2.8f, focal = 90, group = "SPECIALI", soft = 0.7f, bloom = 0.45f),')
    print('    LensSpec("500", "Mirror 500mm f/8", 17f, f = 8f, focal = 500, group = "SPECIALI", shape = 7, vig = 0.26f, soft = 0.1f),')
    print('    LensSpec("fish", "Fisheye 16mm f/8", 1.28f, f = 8f, focal = 16, group = "SPECIALI", dist = 0.95f, ca = 0.004f, vig = 0.15f),')
    print('    LensSpec("tilt", "Tilt-Shift 45mm f/5.6", 1.35f, f = 5.6f, focal = 45, group = "SPECIALI", tilt = 1f),')
    print(")")
main(sys.argv[1])
