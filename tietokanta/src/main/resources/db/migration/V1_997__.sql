-- Otettu analytiikka toteumien rajapinnoissa materiaalien timestampit huomioon
ALTER TABLE analytiikka_toteumat ADD COLUMN toteuma_materiaali_luotu timestamp;
ALTER TABLE analytiikka_toteumat ADD COLUMN toteuma_materiaali_muokattu timestamp;

-- Uusi indeksi materiaaleille
create index analytiikka_materiaalit_muokattu_luotu_index
    on analytiikka_toteumat (toteuma_materiaali_muokattu, toteuma_materiaali_luotu);
