-- Poistetaan Labyrintti/LinkSMS integraatio, joka on korvattu uudella SMS-integraatiolla ('sms'-järjestelmä)
-- Integraatiolokin historiaa ei ole tarvetta säilyttää, ja lokin tapahtumat poistetaan muutenkin automaattisesti

-- Poistetaan kaikki labyrinttiin liittyvät integraatioviestit
DELETE
  FROM integraatioviesti
 WHERE integraatiotapahtuma IN (SELECT id
                                  FROM integraatiotapahtuma
                                 WHERE integraatio IN (SELECT id
                                                         FROM integraatio
                                                        WHERE jarjestelma = 'labyrintti'));

-- Poistetaan kaikki labyrinttiin liittyvät tapahtumat
DELETE
  FROM integraatiotapahtuma
 WHERE integraatio IN (SELECT id
                         FROM integraatio
                        WHERE jarjestelma = 'labyrintti');

-- Lopuksi poistetaan itse labyrintti-järjestelmä
DELETE
  FROM integraatio
 WHERE jarjestelma = 'labyrintti';
