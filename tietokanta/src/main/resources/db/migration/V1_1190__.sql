-- Siivoa pois vanhoja integraatiotietoja.
-- Jos vanhaa integraatiota yritettäisiin käyttää, se onnistuisi periaattessa ilman näitäkin (jos koodi olisi olemassa),
-- mutta käytännössä integraatiolokille kirjoittaminen aiheuttaisi virheen ja keskeyttäisi ajon.

-- Varustetoteumat kirjataan nykyään Velhoon. Harjassa ei ole enää varustetoteumarajapintaa.
DELETE FROM integraatio WHERE jarjestelma = 'api' AND nimi = 'lisaa-varustetoteuma';
DELETE FROM integraatio WHERE jarjestelma = 'api' AND nimi = 'poista-varustetoteuma';

-- Sampo-integraation on korvannut sampo-api-integraatio.
DELETE FROM integraatio WHERE jarjestelma = 'sampo';

-- Sonja-integraatiota ei enää ole
-- Sähköposteihin liittyvät toiminnot löytyvät nykyään api:n rajapinnoista
DELETE FROM integraatio WHERE jarjestelma = 'sonja';

-- Kohteita ei lähetetä Velhoon suoraan Harjasta, ne siirtyvät Velhoon YHAn kautta
DELETE FROM integraatio WHERE jarjestelma = 'velho' AND nimi = 'kohteiden-lahetys';

-- Reimari-integraatiota ei enää ole. Poistetaan kaikki Reimari-rajapintoihin liittyvät ohjaustiedot
-- ja poistetaan rajapinnat integraatiotaulusta.
DELETE FROM reimari_meta;
DELETE FROM integraatio WHERE jarjestelma = 'reimari';

-- Inspire-integraatiota ei enää ole. Geometriat saadaan Väyläviraston avoimen rajapinnan kautta.
DELETE FROM integraatio WHERE jarjestelma = 'inspire';
