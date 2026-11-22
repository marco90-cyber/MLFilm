# MLFilm

Editor di foto a pellicola per Android (parte delle MLapps). Scegli una foto, poi **camera + obiettivo + pellicola**
e vedi l'effetto in anteprima; la foto finale viene salvata in `Pictures/MLFilm`.

- Schermata iniziale: **Scegli foto** (anche più foto di fila), oppure condividi una foto verso MLFilm / "Apri con"
- Pellicole come LUT 3D (voce **Nessuna** per lasciare la foto com'è), grana, alone, light leak
- 12 camere (difetti tipici di ognuna), 41 obiettivi (profili reali Lensfun, CC BY-SA 3.0: vedi NOTICE-LENSFUN.md), elenchi a categorie, pellicole a carosello, apertura F1.4–F16 con bokeh dalla mappa di profondità
  (Depth Anything V2 Small), forme bokeh, tocco per rifocalizzare, pennello profondità
- Effetti: flare, starburst, soft focus, colore, fuoco sfuggente
- Tieni premuto sulla foto per vedere l'originale; data arancione opzionale; salva o condividi

Build: GitHub Actions sul ramo `main`, APK nelle Release.

## Pellicole (LUT)
296 look di pellicola come LUT 3D in `app/src/main/assets/luts` (origine: Film-Luts / preset G'MIC). Rigenerabili con `tools/make_luts.py`. Dettagli e licenze in `assets/luts/NOTICE.txt`.
