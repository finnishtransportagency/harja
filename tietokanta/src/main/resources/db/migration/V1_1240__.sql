-- Päivitä tehtävän suoritettavatehtava-sarake
UPDATE tehtava
SET suoritettavatehtava = 'sorateiden pinnan hoito'::suoritettavatehtava,
muokattu = CURRENT_TIMESTAMP,
muokkaaja = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
WHERE nimi = 'Sorateiden pinnan hoito';
