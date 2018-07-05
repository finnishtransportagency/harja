INSERT INTO integraatio (jarjestelma, nimi) VALUES ('sonja','sahkoposti-ja-liite-lahetys');
ALTER TABLE tietyoilmoituksen_email_lahetys ADD CONSTRAINT uniikki_lahetysid UNIQUE (lahetysid);
DELETE FROM integraatio
WHERE jarjestelma='tloik' AND
      nimi IN ('tietyoilmoituksen-lahetys', 'tietyoilmoituksen-vastaanotto');
