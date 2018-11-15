-- Päivitä kanavien toimenpiteen puuttuva tuotekoodi
UPDATE toimenpidekoodi SET
  tuotenumero = 201,
  muokattu = current_timestamp,
  muokkaaja = 425967 where
koodi ='27100' and nimi = 'Vesiliikenteen käyttöpalvelut';

