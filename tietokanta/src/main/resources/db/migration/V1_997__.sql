--Otettu analytiikka toteumien rajapinnoissa materiaalien timestampit huomioon
ALTER TABLE analytiikka_toteumat ADD COLUMN toteuma_materiaali_luotu timestamp;
ALTER TABLE analytiikka_toteumat ADD COLUMN toteuma_materiaali_muokattu timestamp;
