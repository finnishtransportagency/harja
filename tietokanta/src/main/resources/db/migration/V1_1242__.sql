
ALTER TABLE urakka_tavoite
    DROP COLUMN IF EXISTS tavoitehinta_siirretty,
    DROP COLUMN IF EXISTS tavoitehinta_siirretty_indeksikorjattu;
