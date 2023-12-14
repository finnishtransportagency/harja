-- Poistetaan rajapinta, jonka kautta on voinut kirjata paikkaustoteumiin liittyviä kustannuksia.
-- Rajapintaa ei ole käytetty, eikä taustakoodi ole ajantasalla ja synkassa paikkauskustannusten kirjaamisen nykykäytännön kanssa.
DELETE from integraatio WHERE jarjestelma = 'api' AND nimi = 'kirjaa-paikkaustoteuma';
