# MLFilm

Fotocamera a pellicola per Android (parte delle MLapps). Scegli **camera + obiettivo + pellicola**
e vedi l'effetto in anteprima live; ogni rullo ha 36 pose.

- 12 camere, 15 obiettivi, 120 pellicole (tutto dati, un solo shader OpenGL)
- Anteprima live con CameraX + OpenGL, stesso shader usato per la foto finale
- Rullo da 36 pose, data stamp arancione, grana, vignettatura, light leak
- Half Frame (2 scatti in una foto, oppure foto singola)
- Album interno con condivisione, importazione di foto dalla galleria
- Le foto vengono salvate in `Pictures/MLFilm`

Build: GitHub Actions sul ramo `main`, APK nelle Release.
