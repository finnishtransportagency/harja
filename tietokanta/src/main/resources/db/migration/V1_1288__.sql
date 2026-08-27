-- Päivitetään muutama tehtävä niin, että urakoitsijajärjestelmästä tullutta tehtävätoteumamäärää ei lasketa yhteen
-- käsin kirjatun määrän kanssa Harjan Toteumat > Tehtävät-näkymässä. Järjestelmäkirjaukseen liittyvää reittiä voi
-- jatkossakin tarkastella Tilannekuvassa.

-- Syitä on kaksi:
-- 1. Tehtävässä seurataan muuta yksikköä, ei matkaa minkä urakoitsijajärjestelmä Harjaan tehtävätoteumassa välittää.
-- 2. Tehtävälle koneelta kirjautuva metri- tai kilometrimäärä ei kerro todellisen työn määrästä, ainoastaan koneen liikkeistä.
-- Ei haluta että tällaiset luvut sotkevat yhteenlaskettuja summia.

-- Yksikköön liittyvä rajaus on ollut käytössä jo muuta kautta, tämä muutos vaikuttaa käytännössä
-- palteenpoistotehtävien summiin ed. mainitussa näkymässä.
UPDATE tehtava
SET "laske-api-maara-mukaan?" = FALSE,
    muokattu                  = TRUE,
    muokkaaja                 = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE "mhu-tehtava?" IS TRUE
  AND "api_seuranta" IS TRUE
  AND kasin_lisattava_maara IS TRUE
  AND (yksikko in ('tonni', 'h', 'kpl', 'm2') OR
       nimi in ('Reunapalteen poisto kaiteen alta', 'Päällystettyjen teiden palteiden poisto'));

-- Määritellään näille tehtäville, että järjestelmästä tullut matka lasketaan yhteen käsin kirjatun toteuman kanssa,
-- jos tehtäville Harjassa käsin kirjattuja toteumia löytyy.
UPDATE tehtava
SET "laske-api-maara-mukaan?" = TRUE,
    muokattu                  = TRUE,
    muokkaaja                 = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE "mhu-tehtava?" IS TRUE
  AND "api_seuranta" IS TRUE
  AND kasin_lisattava_maara IS TRUE
  AND nimi in ('Meluesteiden siisteydestä huolehtiminen', 'Sohjo-ojien teko'));

-- Osalle tehtävistä "laske-api-maara-mukaan?"-sarakkeen arvo jää nulliksi.
-- Määrittelyä on voi täydentää tarvittaessa.
