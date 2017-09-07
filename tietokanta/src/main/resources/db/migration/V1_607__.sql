-- Lisätään turvallisuuspoikkeaman juurisyyt

CREATE TYPE turvallisuuspoikkeama_juurisyy AS ENUM (
  'puutteelliset_henkilonsuojaimet',
  'puutteelliset_tyovalineet_tai_koneet',
  'puutteellinen_jarjestys_tai_siisteys',
  'puutteellinen_patevyys_tai_kelpoisuus',
  'puutteellinen_tai_puuttuva_ohjeistus_tai_perehdytys',
  'ohjeiden_vastainen_toiminta_tai_riskinotto',
  'tyomaan_ulkopuolinen_tekija_tai_olosuhde',
  'muu'
);

ALTER TABLE turvallisuuspoikkeama
  ADD COLUMN juurisyy1 turvallisuuspoikkeama_juurisyy,
  ADD COLUMN juurisyy1_selite text,
  ADD COLUMN juurisyy2 turvallisuuspoikkeama_juurisyy,
  ADD COLUMN juurisyy2_selite text,
  ADD COLUMN juurisyy3 turvallisuuspoikkeama_juurisyy,
  ADD COLUMN juurisyy3_selite text;
