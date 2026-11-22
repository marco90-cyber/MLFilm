package com.marco.mlfilm

// 20 pellicole scelte tra le più note (generate da tools/make_luts.py): look come LUT 3D 13x13x13.
// Origine: raccolta Film-Luts (G'MIC film presets). Vedi assets/luts/NOTICE.txt.
val FILMS: List<FilmSpec> = listOf(
    FilmSpec("none", "Nessuna", "Nessuna", 0),
    FilmSpec("negative_new/kodak_portra_160", "Kodak Portra 160", "Negative", 160),
    FilmSpec("negative_new/kodak_portra_400", "Kodak Portra 400", "Negative", 400),
    FilmSpec("negative_new/kodak_portra_800", "Kodak Portra 800", "Negative", 800),
    FilmSpec("negative_color/kodak_ektar_100", "Kodak Ektar 100", "Color Neg", 100),
    FilmSpec("negative_new/fuji_400h", "Fuji Pro 400H", "Negative", 400),
    FilmSpec("negative_old/fuji_superia_400", "Fuji Superia 400", "Vintage Neg", 400),
    FilmSpec("negative_color/fuji_superia_reala_100", "Fuji Reala 100", "Color Neg", 100),
    FilmSpec("negative_color/agfa_vista_200", "Agfa Vista 200", "Color Neg", 200),
    FilmSpec("colorslide/fuji_velvia_50", "Fuji Velvia 50", "Slide", 50),
    FilmSpec("colorslide/fuji_provia_100f", "Fuji Provia 100F", "Slide", 100),
    FilmSpec("colorslide/fuji_astia_100f", "Fuji Astia 100F", "Slide", 100),
    FilmSpec("colorslide/kodak_kodachrome_64", "Kodak Kodachrome 64", "Slide", 64),
    FilmSpec("colorslide/kodak_e-100_gx_ektachrome_100", "Kodak Ektachrome 100", "Slide", 100),
    FilmSpec("fujixtransiii/fuji_xtrans_iii_classic_chrome", "Fuji Classic Chrome", "Fuji X", 0),
    FilmSpec("bw/kodak_tri-x_400", "Kodak Tri-X 400", "B&W", 400),
    FilmSpec("bw/kodak_t-max_400", "Kodak T-Max 400", "B&W", 400),
    FilmSpec("bw/ilford_hp_5_plus_400", "Ilford HP5 Plus 400", "B&W", 400),
    FilmSpec("bw/ilford_delta_400", "Ilford Delta 400", "B&W", 400),
    FilmSpec("bw/fuji_neopan_acros_100", "Fuji Acros 100", "B&W", 100),
    FilmSpec("instant_pro/polaroid_669", "Polaroid 669", "Instant Pro", 0),
)
