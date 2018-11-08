-- Aikaleimoja välimuistitauluihin (muokkaaja on aina järjestelmä itse)
ALTER TABLE urakan_materiaalin_kaytto_hoitoluokittain
  ADD COLUMN muokattu TIMESTAMP;

ALTER TABLE sopimuksen_kaytetty_materiaali
  ADD COLUMN muokattu TIMESTAMP;
