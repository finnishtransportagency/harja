-- Sähköposti-integraation lokitus siirrettiin omalle järjestelmälle.
-- Poista tarpeettomat
DELETE FROM integraatio WHERE jarjestelma = 'api' and nimi = 'sahkoposti-lahetys';
DELETE FROM integraatio WHERE jarjestelma = 'api' and nimi = 'sahkoposti-ja-liite-lahetys';
DELETE FROM integraatio WHERE jarjestelma = 'api' and nimi = 'sahkoposti-vastaanotto';
