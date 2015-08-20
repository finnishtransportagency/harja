-- Geneerinen taulu eri näkymien lukitsemiseen, jotta yhtäaikainen muokkaus ei onnistu

CREATE TABLE muokkauslukko (
  id VARCHAR(1024) PRIMARY KEY, -- Yksilöi lukon tiettyyn näkymään
  kayttaja integer REFERENCES kayttaja (id), -- Kuka lukitsi
  aikaleima TIMESTAMP -- Lukon viimeisin virkistysaika
)