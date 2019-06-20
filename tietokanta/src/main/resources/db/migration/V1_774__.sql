CREATE OR REPLACE FUNCTION pohjavesialue_yhdistaminen (yhdistetyt_pohjavesialueet pohjavesialue[], pva pohjavesialue)
    RETURNS pohjavesialue[] AS $$
DECLARE
    edellinen_yhdistetty_pohjavesialue pohjavesialue;
    tyostettava_pohjavesialue pohjavesialue;
    edellisen_pohjavesialueen_loppuetaisyys INTEGER;
    edellisen_pohjavesialueen_seuraava_osa INTEGER;
BEGIN
    edellinen_yhdistetty_pohjavesialue = yhdistetyt_pohjavesialueet[array_length(yhdistetyt_pohjavesialueet, 1)];
    tyostettava_pohjavesialue = edellinen_yhdistetty_pohjavesialue;
    IF edellinen_yhdistetty_pohjavesialue.tr_loppuosa = pva.tr_alkuosa THEN
        IF (edellinen_yhdistetty_pohjavesialue.tr_loppuetaisyys >= pva.tr_alkuetaisyys) THEN
            tyostettava_pohjavesialue.tr_loppuetaisyys = pva.tr_loppuetaisyys;
            tyostettava_pohjavesialue.alue = ST_LineMerge(ST_CollectionExtract(ST_Collect(tyostettava_pohjavesialue.alue, pva.alue), 2));
            RETURN array_append(yhdistetyt_pohjavesialueet[1:(array_length(yhdistetyt_pohjavesialueet, 1) - 1)],
                                tyostettava_pohjavesialue);
        ELSE
            RETURN array_append(yhdistetyt_pohjavesialueet, pva);
        END IF;
    ELSE
        edellisen_pohjavesialueen_loppuetaisyys = (SELECT (pituudet ->> 'pituus')::INTEGER + (pituudet ->> 'tr-alkuetaisyys')::INTEGER
                                        FROM tr_tiedot
                                        WHERE "tr-numero"=edellinen_yhdistetty_pohjavesialue.tr_numero AND
                                                "tr-osa"=edellinen_yhdistetty_pohjavesialue.tr_loppuosa);
        edellisen_pohjavesialueen_seuraava_osa = (SELECT "tr-osa"
                                       FROM tr_osoitteet
                                       WHERE "tr-numero"=edellinen_yhdistetty_pohjavesialue.tr_numero AND
                                               "tr-osa">edellinen_yhdistetty_pohjavesialue.tr_loppuosa
                                       ORDER BY "tr-osa" ASC
                                       LIMIT 1);
        IF (edellinen_yhdistetty_pohjavesialue.tr_loppuetaisyys = edellisen_pohjavesialueen_loppuetaisyys AND
            pva.tr_alkuetaisyys = 0 AND
            edellisen_pohjavesialueen_seuraava_osa = pva.tr_alkuosa) THEN
            tyostettava_pohjavesialue.tr_loppuetaisyys = pva.tr_loppuetaisyys;
            tyostettava_pohjavesialue.tr_loppuosa = pva.tr_loppuosa;
            tyostettava_pohjavesialue.alue = ST_LineMerge(ST_CollectionExtract(ST_Collect(tyostettava_pohjavesialue.alue, pva.alue), 2));
            RETURN array_append(yhdistetyt_pohjavesialueet[1:(array_length(yhdistetyt_pohjavesialueet, 1) - 1)],
                                tyostettava_pohjavesialue);
        ELSE
            RETURN array_append(yhdistetyt_pohjavesialueet, pva);
        END IF;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE AGGREGATE yhtenaiset_pohjavesialueet (pohjavesialue) (
  SFUNC = pohjavesialue_yhdistaminen,
  STYPE = pohjavesialue[],
  initcond = '{}'
);

CREATE OR REPLACE FUNCTION pohjavesialue_factory(kentta_arvo jsonb)
    RETURNS pohjavesialue AS $$
DECLARE
    pva pohjavesialue%ROWTYPE;
BEGIN
    pva = '(,,,,,,,,,,,,,,,,,)'::pohjavesialue;
    IF kentta_arvo ? 'id'
    THEN
        pva.id = (kentta_arvo ->> 'id')::INTEGER;
    END IF;
    IF kentta_arvo ? 'nimi'
    THEN
        pva.nimi = (kentta_arvo ->> 'nimi')::VARCHAR(128);
    END IF;
    IF kentta_arvo ? 'tunnus'
    THEN
        pva.tunnus = (kentta_arvo ->> 'tunnus')::VARCHAR(16);
    END IF;
    IF kentta_arvo ? 'alue'
    THEN
        pva.alue = (kentta_arvo ->> 'alue')::GEOMETRY;
    END IF;
    IF kentta_arvo ? 'muuttunut_pvm'
    THEN
        pva.muuttunut_pvm = (kentta_arvo ->> 'muuttunut_pvm')::TIMESTAMP;
    END IF;
    IF kentta_arvo ? 'suolarajoitus'
    THEN
        pva.suolarajoitus = (kentta_arvo ->> 'suolarajoitus')::BOOLEAN;
    END IF;
    IF kentta_arvo ? 'tr_numero'
    THEN
        pva.tr_numero = (kentta_arvo ->> 'tr_numero')::INTEGER;
    END IF;
    IF kentta_arvo ? 'tr_alkuosa'
    THEN
        pva.tr_alkuosa = (kentta_arvo ->> 'tr_alkuosa')::INTEGER;
    END IF;
    IF kentta_arvo ? 'tr_alkuetaisyys'
    THEN
        pva.tr_alkuetaisyys = (kentta_arvo ->> 'tr_alkuetaisyys')::INTEGER;
    END IF;
    IF kentta_arvo ? 'tr_loppuosa'
    THEN
        pva.tr_loppuosa = (kentta_arvo ->> 'tr_loppuosa')::INTEGER;
    END IF;
    IF kentta_arvo ? 'tr_loppuetaisyys'
    THEN
        pva.tr_loppuetaisyys = (kentta_arvo ->> 'tr_loppuetaisyys')::INTEGER;
    END IF;
    IF kentta_arvo ? 'tr_ajorata'
    THEN
        pva.tr_ajorata = (kentta_arvo ->> 'tr_ajorata')::INTEGER;
    END IF;
    IF kentta_arvo ? 'tr_kaista'
    THEN
        pva.tr_kaista = (kentta_arvo ->> 'tr_kaista')::INTEGER;
    END IF;
    IF kentta_arvo ? 'luotu'
    THEN
        pva.luotu = (kentta_arvo ->> 'luotu')::TIMESTAMP;
    END IF;
    IF kentta_arvo ? 'luoja'
    THEN
        pva.luoja = (kentta_arvo ->> 'luoja')::INTEGER;
    END IF;
    IF kentta_arvo ? 'muokattu'
    THEN
        pva.muokattu = (kentta_arvo ->> 'muokattu')::TIMESTAMP;
    END IF;
    IF kentta_arvo ? 'muokkaaja'
    THEN
        pva.muokkaaja = (kentta_arvo ->> 'muokkaaja')::INTEGER;
    END IF;
    IF kentta_arvo ? 'aineisto_id'
    THEN
        pva.aineisto_id = (kentta_arvo ->> 'aineisto_id')::TEXT;
    END IF;
    RETURN pva;
END;
$$ LANGUAGE plpgsql;

DROP MATERIALIZED VIEW pohjavesialue_kooste;
CREATE MATERIALIZED VIEW pohjavesialue_kooste AS (
SELECT (yhtenainen_pva).nimi AS nimi,
       (yhtenainen_pva).tunnus AS tunnus,
       (yhtenainen_pva).alue AS alue,
       st_length((yhtenainen_pva).alue) AS pituus,
       (yhtenainen_pva).tr_numero AS tie,
       (yhtenainen_pva).tr_alkuosa AS alkuosa,
       (yhtenainen_pva).tr_alkuetaisyys AS alkuet,
       (yhtenainen_pva).tr_loppuosa AS loppuosa,
       (yhtenainen_pva).tr_loppuetaisyys AS loppuet
FROM (SELECT unnest(ypva) AS yhtenainen_pva
      FROM (SELECT yhtenaiset_pohjavesialueet(pva) AS ypva
            FROM (SELECT pohjavesialue_factory((('{"tr_numero": ' || tr_numero || '}')::jsonb ||
                                                ('{"tr_alkuosa": ' || tr_alkuosa || '}')::jsonb ||
                                                ('{"tr_alkuetaisyys": ' || tr_alkuetaisyys || '}')::jsonb ||
                                                ('{"tr_loppuosa": ' || tr_loppuosa || '}')::jsonb ||
                                                ('{"tr_loppuetaisyys": ' || tr_loppuetaisyys || '}')::jsonb ||
                                                ('{"tunnus": "' || tunnus || '"}')::jsonb ||
                                                ('{"nimi": "' || nimi || '"}')::jsonb ||
                                                ('{"alue": "' || alue::TEXT || '"}')::jsonb)) AS pva,
                         tr_numero
                  FROM pohjavesialue
                  ORDER BY tr_numero, tr_alkuosa, tr_alkuetaisyys) AS pohjavesialueet
            GROUP BY tr_numero) AS tien_osat_taulu) AS yhtenaiset_pvat
);