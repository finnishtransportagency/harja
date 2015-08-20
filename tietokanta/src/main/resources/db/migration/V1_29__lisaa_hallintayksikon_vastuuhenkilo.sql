DROP INDEX kayttajan_ajantasaiset_roolit;
DROP INDEX kayttajan_ajantasaiset_urakka_roolit;

ALTER TYPE kayttajarooli rename to _TEMP_kayttajarooli;

CREATE TYPE kayttajarooli AS ENUM  (
   -- Yleinen käyttäjärooli, joka ei ole linkitetty mihinkään (korkeintaan väylämuoto)
   'jarjestelmavastuuhenkilo', 'vaylamuodon vastuuhenkilo', 'liikennepaivystaja', 'tilaajan asiantuntija',

   -- Rooli, joka on hallintayksikössä (LISÄTTY: hallintayksikon vastuuhenkilo)
   'tilaajan kayttaja', 'hallintayksikon vastuuhenkilo',

   -- Tilaajan urakkaan sidottu rooli
   'urakanvalvoja', 'tilaajan laadunvalvontakonsultti',

   -- Urakoitsijan urakkaan sidottu rooli 
   'urakoitsijan paakayttaja',
   'urakoitsijan urakan vastuuhenkilo',
   'urakoitsijan kayttaja',
   'urakoitsijan laatuvastaava'
);

ALTER TABLE kayttaja_rooli RENAME COLUMN rooli TO _rooli;
ALTER TABLE kayttaja_rooli ADD rooli kayttajarooli;
UPDATE kayttaja_rooli SET rooli=_rooli::text::kayttajarooli;
ALTER TABLE kayttaja_rooli DROP COLUMN _rooli;

ALTER TABLE kayttaja_organisaatio_rooli RENAME COLUMN rooli TO _rooli;
ALTER TABLE kayttaja_organisaatio_rooli ADD rooli kayttajarooli;
UPDATE kayttaja_organisaatio_rooli SET rooli=_rooli::text::kayttajarooli;
ALTER TABLE kayttaja_organisaatio_rooli DROP COLUMN _rooli;

ALTER TABLE kayttaja_urakka_rooli RENAME COLUMN rooli TO _rooli;
ALTER TABLE kayttaja_urakka_rooli ADD rooli kayttajarooli;
UPDATE kayttaja_urakka_rooli SET rooli=_rooli::text::kayttajarooli;
ALTER TABLE kayttaja_urakka_rooli DROP COLUMN _rooli;



DROP TYPE _TEMP_kayttajarooli;

CREATE INDEX kayttajan_ajantasaiset_roolit
          ON kayttaja_rooli (kayttaja, rooli)
       WHERE poistettu=false;

CREATE INDEX kayttajan_ajantasaiset_urakka_roolit
          ON kayttaja_urakka_rooli (kayttaja,urakka,rooli)
       WHERE poistettu=false;
