UPDATE tehtava
SET "maaramitattava?" = TRUE,
    muokattu          = current_timestamp,
    muokkaaja         = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE id IN (SELECT id
             FROM tehtava
             WHERE NIMI in ('Reunapalkin ja päällysteen väl. sauman tiivistäminen',
                            'Reunapalkin liikuntasauman tiivistäminen',
                            'Sillan kannen päällysteen päätysauman korjaukset',
                            'Sillan päällysteen halkeaman avarrussaumaus',
                            'Soratien runkokelirikkokorjaukset',
                            'Osallistuminen tilaajalle kuuluvien viranomaistehtävien hoitoon')
               AND poistettu is not true
               AND "mhu-tehtava?" is true);
