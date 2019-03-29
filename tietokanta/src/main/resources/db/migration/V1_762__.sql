--  Tuotemuutosjumppaan liittyvä nimimuutos ja kanavatoimenpiteen säätö
UPDATE toimenpidekoodi SET nimi = 'Varuste ja laite korjaus laaja TPI', tuotenumero = 205, muokattu = CURRENT_TIMESTAMP, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE koodi = '20179' and taso = 3;
UPDATE toimenpidekoodi SET nimi = 'Vesiliikenteen käyttöpalvelut laaja TPI', tuotenumero = 203, muokattu = CURRENT_TIMESTAMP, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE koodi = '27105' and taso = 3;
