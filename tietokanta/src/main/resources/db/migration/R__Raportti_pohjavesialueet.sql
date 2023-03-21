CREATE OR REPLACE FUNCTION hoitokauden_alkuvuosi(aika TIMESTAMP) RETURNS INT AS
$$
DECLARE
    kuukausi INTEGER;
    vuosi INTEGER;
BEGIN
    kuukausi = date_part('month', aika);
    vuosi = date_part('year', aika);
    RETURN CASE
               WHEN (kuukausi < 10) THEN vuosi - 1
               WHEN (kuukausi >= 10) THEN vuosi
        END;
END;
$$ LANGUAGE plpgsql;

DROP MATERIALIZED VIEW IF EXISTS raportti_pohjavesialueiden_suolatoteumat;
DROP MATERIALIZED VIEW IF EXISTS pohjavesialue_kooste;

CREATE MATERIALIZED VIEW pohjavesialue_kooste AS (
                                            SELECT (yhtenainen_pva).nimi AS nimi,
                                                   (yhtenainen_pva).tunnus AS tunnus,
                                                   (yhtenainen_pva).alue AS alue,
                                                   (yhtenainen_pva).suolarajoitus AS suolarajoitus,
                                                   pva_ts.talvisuolaraja AS talvisuolaraja,
                                                   pva_ts.hoitokauden_alkuvuosi AS rajoituksen_alkuvuosi,
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
                                                                                                  ('{"suolarajoitus": "' || suolarajoitus || '"}')::jsonb ||
                                                                                                  ('{"alue": "' || alue::TEXT || '"}')::jsonb)) AS pva,
                                                                           tr_numero
                                                                      FROM pohjavesialue
                                                                     ORDER BY tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_ajorata) AS pohjavesialueet
                                                             GROUP BY tr_numero) AS tien_osat_taulu) AS yhtenaiset_pvat
                                                       LEFT JOIN pohjavesialue_talvisuola pva_ts ON (pva_ts.pohjavesialue = (yhtenainen_pva).tunnus AND
                                                                                                     pva_ts.tie = (yhtenainen_pva).tr_numero));

CREATE INDEX pohjavesialue_kooste_tunnus_rajoituksen_alkuvuosi ON pohjavesialue_kooste (tunnus, rajoituksen_alkuvuosi);

CREATE MATERIALIZED VIEW raportti_pohjavesialueiden_suolatoteumat AS
SELECT t.urakka                             AS "urakka-id",
       date_trunc('day', rp.aika)           AS paiva,
       SUM(rp.maara)                        AS yhteensa,
       pva_k.tie,
       pva_k.alkuosa,
       pva_k.alkuet,
       pva_k.loppuosa,
       pva_k.loppuet,
       (array_agg(pva_k.pituus))[1]         AS pituus, -- Tuossa pituudessa on vain yksi arvo
       (array_agg(pva_k.tunnus))[1]         AS tunnus,
       (array_agg(pva_k.talvisuolaraja))[1] AS kayttoraja
  FROM suolatoteuma_reittipiste rp
           LEFT JOIN toteuma t ON t.id = rp.toteuma AND t.poistettu = FALSE
           JOIN LATERAL (SELECT *
                           FROM pohjavesialue_kooste pva_k
                          WHERE pva_k.tunnus = rp.pohjavesialue
                            AND (pva_k.rajoituksen_alkuvuosi IS NULL
                              OR pva_k.rajoituksen_alkuvuosi = hoitokauden_alkuvuosi(rp.aika))
                          ORDER BY ST_Distance84(pva_k.alue, ST_Point(rp.sijainti[0], rp.sijainti[1]))
                          LIMIT 1) AS pva_k ON TRUE
 WHERE rp.pohjavesialue IS NOT NULL
 GROUP BY t.urakka, paiva, pva_k.tie, pva_k.alkuosa, pva_k.alkuet, pva_k.loppuosa, pva_k.loppuet;


CREATE OR REPLACE FUNCTION paivita_raportti_pohjavesialueiden_suolatoteumat()
    RETURNS VOID
    SECURITY DEFINER
AS $$
BEGIN
    REFRESH MATERIALIZED VIEW raportti_pohjavesialueiden_suolatoteumat;
    RETURN;
END;
$$ LANGUAGE plpgsql;
