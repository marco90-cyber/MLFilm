# MLFilm

Editor di foto a pellicola per Android (parte delle MLapps). Scegli una foto, poi **camera + obiettivo + pellicola**
e vedi l'effetto in anteprima; la foto finale viene salvata in `Pictures/MLFilm`.

- Schermata iniziale: **Scegli foto** (anche più foto di fila), oppure condividi una foto verso MLFilm / "Apri con"
- Pellicole come LUT 3D (voce **Nessuna** per lasciare la foto com'è), grana, alone, light leak
- Voce REGOLA: esposizione, contrasto, alte luci, ombre, bianchi, neri, temperatura, tinta, saturazione, vibranza, nitidezza, chiarezza, dehaze
- 27 camere (9 digitali, 4 reflex e compatte a pellicola, 2 istantanee e le classiche), con look stimati (difetti tipici di ognuna), 41 obiettivi (profili reali Lensfun, CC BY-SA 3.0: vedi NOTICE-LENSFUN.md), elenchi a categorie, 20 pellicole a carosello, apertura F1.4–F16 con bokeh dalla mappa di profondità
  (Depth Anything V2 Small), forme bokeh, tocco per rifocalizzare, pennello profondità
- Effetti: flare, starburst, soft focus, colore, fuoco sfuggente
- Tieni premuto sulla foto per vedere l'originale; data arancione opzionale; salva o condividi

Build: GitHub Actions sul ramo `main`, APK nelle Release.

## Pellicole (LUT)
20 look di pellicola (i più noti) come LUT 3D in `app/src/main/assets/luts` (origine: Film-Luts / preset G'MIC). Rigenerabili con `tools/make_luts.py`. Dettagli e licenze in `assets/luts/NOTICE.txt`.
