UPDATE tehtava
SET api_tunnus = id,
    muokattu   = current_timestamp,
    muokkaaja  = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE nimi IN ('Ryhmävaihto', 'Huoltokierros', 'Muut toimenpiteet')
  AND emo = (SELECT id from toimenpide WHERE koodi = 'VALA_YKSHINT');
