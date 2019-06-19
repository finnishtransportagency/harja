CREATE TYPE tr_paaluvali AS (tie INTEGER, aosa INTEGER, aet INTEGER, losa INTEGER, let INTEGER);

CREATE OR REPLACE FUNCTION tr_paaluvali_yhdistaminen (yhdistetyt_paaluvalit tr_paaluvali[], paaluvali tr_paaluvali)
    RETURNS tr_paaluvali[] AS $$
DECLARE
    edellinen_yhdistetty_paaluvali tr_paaluvali;
    tyostettava_paaluvali tr_paaluvali;
    edellisen_osan_loppuetaisyys INTEGER;
    edellisen_osan_seuraava_osa INTEGER;
BEGIN
    edellinen_yhdistetty_paaluvali = yhdistetyt_paaluvalit[array_length(yhdistetyt_paaluvalit, 1)];
    tyostettava_paaluvali = edellinen_yhdistetty_paaluvali;
    IF edellinen_yhdistetty_paaluvali.losa = paaluvali.aosa THEN
        IF (edellinen_yhdistetty_paaluvali.let >= paaluvali.aet) THEN
            tyostettava_paaluvali.let = paaluvali.let;
            RETURN array_append(yhdistetyt_paaluvalit[1:(array_length(yhdistetyt_paaluvalit, 1) - 1)],
                                tyostettava_paaluvali);
        ELSE
            RETURN array_append(yhdistetyt_paaluvalit, paaluvali);
        END IF;
    ELSE
        edellisen_osan_loppuetaisyys = (SELECT (pituudet ->> 'pituus')::INTEGER + (pituudet ->> 'tr-alkuetaisyys')::INTEGER
                                        FROM tr_tiedot
                                        WHERE "tr-numero"=edellinen_yhdistetty_paaluvali.tie AND
                                                "tr-osa"=edellinen_yhdistetty_paaluvali.losa);
        edellisen_osan_seuraava_osa = (SELECT "tr-osa"
                                       FROM tr_osoitteet
                                       WHERE "tr-numero"=edellinen_yhdistetty_paaluvali.tie AND
                                               "tr-osa">edellinen_yhdistetty_paaluvali.losa
                                       ORDER BY "tr-osa" ASC
                                       LIMIT 1);
        IF (edellinen_yhdistetty_paaluvali.let = edellisen_osan_loppuetaisyys AND
            paaluvali.aet = 0 AND
            edellisen_osan_seuraava_osa = paaluvali.aosa) THEN
            tyostettava_paaluvali.let = paaluvali.let;
            tyostettava_paaluvali.losa = paaluvali.losa;
            RETURN array_append(yhdistetyt_paaluvalit[1:(array_length(yhdistetyt_paaluvalit, 1) - 1)],
                                tyostettava_paaluvali);
        ELSE
            RETURN array_append(yhdistetyt_paaluvalit, paaluvali);
        END IF;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE AGGREGATE tr_yhtenaiset_paaluvalit (tr_paaluvali) (
  SFUNC = tr_paaluvali_yhdistaminen,
  STYPE = tr_paaluvali[],
  initcond = '{}'
)

SELECT unnest(tien_osat)
FROM (SELECT tr_yhtenaiset_paaluvalit(paaluvali) AS tien_osat
      FROM (SELECT ROW(tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys)::tr_paaluvali AS paaluvali,
                   tr_numero
            FROM pohjavesialue
            ORDER BY tr_numero, tr_alkuosa, tr_alkuetaisyys) AS tr_paaluvalit
      GROUP BY tr_numero) AS tien_osat_taulu