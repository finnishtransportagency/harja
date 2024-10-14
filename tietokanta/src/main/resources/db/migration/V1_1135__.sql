-- Lisätään uusi suunnittelu_osio kustannusten suunnitteluun
ALTER TYPE SUUNNITTELU_OSIO ADD VALUE 'tavoitehintaiset-rahavaraukset';

-- Rahavaraukset olikin vielä väärässä järjestyksessä
UPDATE rahavaraus
   SET jarjestys = 10
 WHERE nimi = 'Tunneleiden hoito';
UPDATE rahavaraus
   SET jarjestys = 11
 WHERE nimi = 'Tilaajan rahavaraus kannustinjärjestelmään';
UPDATE rahavaraus
   SET jarjestys = 12
 WHERE nimi = 'Varalaskupaikat';
UPDATE rahavaraus
   SET jarjestys = 13
 WHERE nimi = 'Muut tavoitehintaan vaikuttavat rahavaraukset';
