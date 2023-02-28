--- Lisää virhe-sarake päällystysilmoitusten tallennusvirheitä varten

ALTER TABLE paallystysilmoitus
ADD COLUMN virhe TEXT;
