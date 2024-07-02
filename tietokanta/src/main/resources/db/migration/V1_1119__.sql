-- Nyt kun hoitourakat ovat päättyneet, siivotaan turhat epäsynkat tehtävä-taulun sarakkeista yksikkö ja suunnittelu_yksikkö.

-- Muutoksen jälkeen jää jäljelle nämä tehtävät, joissa yksikkö ei ole sama kuin suunnitteluyksikkö:

-- A. Tehtävälle suunnitellaan materiaalimääriä, toteutuneet määrät kirjataan materiaalitoteumina, tehtävätoteumia ei synny
-- -- Liukkaudentorjunta hiekoituksella (materiaali) (Tehtävätoteumat tehtäville: Pistehiekoitus, Linjahiekoitus)
-- -- Kalium- tai natriumformiaatin käyttö liukkaudentorjuntaan (materiaali) (Tehtävätoteumat tehtävälle: Suolaus)

-- B. Tehtävälle suunnitellaan materiaalimääriä, toteutuneet määrät kirjataan materiaalitoteumina, tehtävätoteumina syntyy työkoneen reittikirjauksia
-- -- Suolaus
-- -- Liikenteen varmistaminen kelirikkokohteessa
-- -- Sorateiden pölynsidonta (materiaali)

-- C. Tehtävälle suunnitellaan matkaa, tehtävällä ei ole toteumakirjauksia MH-urakoissa, mutta hoitourakoissa on muutama. Yksikkö epäselvä.
-- -- Katupölynsidonta


-- Liukkaudentorjunta hiekoituksella
-- Tehtävälle ei kirjata toteumia, ainoastaan suunnitellaan materiaaleja => yksikön voi synkata suunnitteluyksikön kanssa, ei tarvitse apikirjausmahdollisuutta.
-- Toteumat kirjataan tehtäville: pistehiekoitus, linjahiekoitus.
UPDATE tehtava
SET yksikko      = 'tonni',
    nimi         = 'Liukkaudentorjunta hiekoituksella (materiaali)',
    api_seuranta = false,
    muokattu     = current_timestamp,
    muokkaaja    = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE nimi = 'Liukkaudentorjunta hiekoituksella';

-- Nopeusnäyttötaulujen ylläpito, käyttö ja siirto
-- Ei käytössä hoitourakoissa, vain käsinkirjauksia MH-urakoissa => yksikön voi synkata suunnitteluyksikön kanssa, ei tarvitse apikirjausmahdollisuutta
UPDATE tehtava
SET yksikko      = 'kpl',
    api_seuranta = false,
    muokattu     = current_timestamp,
    muokkaaja    = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE nimi = 'Nopeusnäyttötaulujen ylläpito, käyttö ja siirto';

-- Meluesteiden siisteydestä huolehtiminen
-- Lisää yksikkö
UPDATE tehtava
SET yksikko      = 'jm',
    muokattu     = current_timestamp,
    muokkaaja    = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE nimi = 'Meluesteiden siisteydestä huolehtiminen';



