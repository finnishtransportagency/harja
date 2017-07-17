-- Lisää ilmoitustoimenpiteelle tarvittavat kentät lähetyksien hallitsemiseksi
ALTER TABLE tietyoilmoitus
  ADD COLUMN tila LAHETYKSEN_TILA,
  ADD COLUMN lahetetty TIMESTAMP,
  ADD COLUMN lahetysid VARCHAR(255);

-- Lisää lokiin uudet integraatiot
INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('tloik', 'tietyoilmoituksen-lahetys');

INSERT INTO integraatio (jarjestelma, nimi )
values ('tloik', 'tietyoilmoituksen-vastaanotto')

    
