ALTER TABLE ilmoitus
ADD column "valitetty-urakkaan" TIMESTAMP;

COMMENT ON COLUMN ilmoitus.ilmoitettu IS E'Ajankohta jolloin ilmoitus on tehty Tieliikennekeskukseen tai Palautejärjestelmään.';
COMMENT ON COLUMN ilmoitus.valitetty IS E'Ajankohta jolloin ilmoitus on välitetty T-LOIKista HARJAan. Aikaleima saadaan T-LOIKin lähettämästä sanomasta.';
COMMENT ON COLUMN ilmoitus."vastaanotettu-alunperin" IS E'Ajankohta jolloin ilmoitus on ensimmäistä kertaa vastaanotettu Harjaan eli tallennettu Harjan tietokantaan.';
COMMENT ON COLUMN ilmoitus."vastaanotettu" IS E'Ajankohta jolloin ilmoitus tai sen päivitys on viimeksi vastaanotettu Harjaan eli tallennettu Harjan tietokantaan.';
COMMENT ON COLUMN ilmoitus."valitetty-urakkaan" IS E'Ajankohta jolloin ilmoitukseen linkitetty urakka on saanut tiedon ilmoituksen saapumisesta.';

