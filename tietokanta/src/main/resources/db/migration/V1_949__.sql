-- Analytiikka apia muutetaan toimimaan muokattu ja luotu timestamppien perusteella.
-- Niille tarvitaan uusi indeksi
create index analytiikka_toteumat_muokattu_luotu_index
    on analytiikka_toteumat (toteuma_muutostiedot_muokattu, toteuma_muutostiedot_luotu);
