-- Sallitaan arvonvähennyksen tallennus MHU26-profiilin urakka- ja laatupoikkeamakontekstissa.
WITH integraatio AS (
    SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'Integraatio'
)
INSERT INTO sanktio_profiili_rivi (sanktio_profiili_id,
                                   sanktio_laji_id,
                                   sanktiotyyppi_id,
                                   soveltuvuuskonteksti,
                                   jarjestys,
                                   aktiivinen,
                                   lisametatiedot,
                                   luoja,
                                   luotu,
                                   muokkaaja,
                                   muokattu)
SELECT sp.id,
       sl.id,
       st.id,
       konteksti.soveltuvuuskonteksti,
       1,
       TRUE,
       NULL,
       integraatio.id,
       CURRENT_TIMESTAMP,
       integraatio.id,
       CURRENT_TIMESTAMP
  FROM sanktio_profiili sp
       JOIN sanktio_laji sl
         ON sl.koodi = 'arvonvahennyssanktio'
       JOIN sanktiotyyppi st
         ON st.koodi = 0
       CROSS JOIN integraatio
       CROSS JOIN (VALUES ('urakka'), ('laatupoikkeama')) AS konteksti(soveltuvuuskonteksti)
 WHERE sp.nimi = 'teiden-hoito-mhu2026'
ON CONFLICT (sanktio_profiili_id, sanktio_laji_id, sanktiotyyppi_id, soveltuvuuskonteksti)
DO NOTHING;
