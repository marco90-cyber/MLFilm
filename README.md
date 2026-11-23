# MLFilm

Editor di foto a pellicola per Android (parte delle MLapps). Scegli una foto, poi **camera + obiettivo + pellicola**
e vedi l'effetto in anteprima; la foto finale viene salvata in `Pictures/MLFilm`.

- Schermata iniziale: **Scegli foto** (anche più foto di fila), oppure condividi una foto verso MLFilm / "Apri con"
- Pellicole come LUT 3D (voce **Nessuna** per lasciare la foto com'è), grana, alone, light leak
- Voce REGOLA: esposizione, contrasto, alte luci, ombre, bianchi, neri, temperatura, tinta, saturazione, vibranza, nitidezza, chiarezza, dehaze
- 28 camere: le 11 classiche più 17 ispirate a modelli reali (digitali, reflex e compatte a pellicola, istantanee), con look stimati
  (Depth Anything V2 Small), forme bokeh, tocco per rifocalizzare, pennello profondità
- Effetti: flare, starburst, soft focus, colore, fuoco sfuggente
- Tieni premuto sulla foto per vedere l'originale; data arancione opzionale; salva o condividi

Build: GitHub Actions sul ramo `main`, APK nelle Release.

## Pellicole (LUT)
20 look di pellicola (i più noti) come LUT 3D in `app/src/main/assets/luts` (origine: Film-Luts / preset G'MIC). Rigenerabili con `tools/make_luts.py`. Dettagli e licenze in `assets/luts/NOTICE.txt`.
