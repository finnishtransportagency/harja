-- Siirretään sähköpostiviestintään liittyvät rajapinnat integraatiolokilla omaan osioonsa
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('sahkoposti', 'sahkoposti-lahetys');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('sahkoposti', 'sahkoposti-ja-liite-lahetys');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('sahkoposti', 'sahkoposti-vastaanotto');
