-- Rajataan elinvoimakeskukset toistaiseksi pois.
DROP MATERIALIZED VIEW pohjavesialueet_hallintayksikoittain;
CREATE MATERIALIZED VIEW pohjavesialueet_hallintayksikoittain AS
SELECT
    p.id,
    p.nimi,
    p.tunnus,
    p.alue,
    p.suolarajoitus,
    p.tr_numero,
    p.tr_alkuosa,
    p.tr_alkuetaisyys,
    p.tr_loppuosa,
    p.tr_loppuetaisyys,
    p.tr_ajorata,
    (SELECT id
     FROM organisaatio o
     WHERE tyyppi = 'hallintayksikko' :: organisaatiotyyppi AND ST_CONTAINS(o.alue, p.alue) AND elinvoimakeskusnumero is null) AS hallintayksikko,
    p.luotu,
    p.luoja,
    p.muokattu,
    p.muokkaaja
FROM pohjavesialue p;

