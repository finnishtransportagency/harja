-- Päivitä kanavien toimenpiteen puuttuva tuotekoodi
UPDATE toimenpidekoodi SET
  tuotenumero = 201,
  muokattu = current_timestamp,
  muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') where
koodi ='27100' and nimi = 'Vesiliikenteen käyttöpalvelut';




