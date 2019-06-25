-- Täydennä pohjavesialue-taulua tr-osoitteen tiedoilla.
-- Integraatio luo taulun aina tyhjästä. Muokkaussarakkeet kuitenkin, jotta mahdolliset manuaaliset muutokset voi päivätä.
-- Vaihdetaan aineiston muuttumispäivän sisältävän sarakkeen nimi.

ALTER TABLE pohjavesialue RENAME COLUMN muokattu TO muuttunut_pvm;

ALTER TABLE pohjavesialue
ADD COLUMN tr_numero INTEGER,
ADD COLUMN tr_alkuosa INTEGER,
ADD COLUMN tr_alkuetaisyys INTEGER,
ADD COLUMN tr_loppuosa INTEGER,
ADD COLUMN tr_loppuetaisyys INTEGER,
ADD COLUMN tr_ajorata INTEGER,
ADD COLUMN tr_kaista INTEGER,
ADD COLUMN luotu TIMESTAMP,
ADD COLUMN luoja INTEGER,
ADD COLUMN muokattu TIMESTAMP,
ADD COLUMN muokkaaja INTEGER,
ADD COLUMN aineisto_id TEXT;

-- Päivitetään pohjavesinäkymiin uudet kentät. Muokkaustiedot ongelmanselvittämistä varten.
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
     WHERE tyyppi = 'hallintayksikko' :: organisaatiotyyppi AND ST_CONTAINS(o.alue, p.alue)) AS hallintayksikko,
    p.luotu,
    p.luoja,
    p.muokattu,
    p.muokkaaja
FROM pohjavesialue p;

