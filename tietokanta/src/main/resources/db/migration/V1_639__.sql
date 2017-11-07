ALTER TYPE organisaatiotyyppi ADD VALUE 'tilaajan-konsultti';

-- Carement ja Roadmaster ovat tilaajan laadunvalvontaorganisaatioita, eivät urakoitsijoita
UPDATE organisaatio
SET tyyppi = 'tilaajan-konsultti'::organisaatiotyyppi
WHERE nimi IN ('Carement Oy', 'West Coast Road Masters Oy');

-- Oletuksena tilaajan tekemät tarkastukset eivät näy urakoitsijoille
UPDATE tarkastus
SET nayta_urakoitsijalle = FALSE
WHERE luoja IN (select id from kayttaja where kayttajanimi IN ('carement-harja', 'roadmasters-harja'));