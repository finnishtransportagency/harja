UPDATE tehtava
SET yksikko            = 'tiem',
    suunnitteluyksikko = 'tiem',
    kasin_lisattava_maara = true,
    "raportoi-tehtava?" = true,
    hinnoittelu = '{kokonaishintainen}',
    muokkaaja          = (select id from kayttaja where kayttajanimi = 'Integraatio'),
    muokattu           = current_timestamp
WHERE nimi = 'Sorateitä kaventava ojitus';
