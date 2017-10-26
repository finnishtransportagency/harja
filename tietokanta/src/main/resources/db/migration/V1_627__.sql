CREATE TABLE reimari_meta (
  integraatio       INTEGER
                    NOT NULL
                    REFERENCES integraatio(id),
  enimmaishakuvali  INTERVAL
                    NOT NULL,
  aikakursori       TIMESTAMP
                    NOT NULL);

COMMENT ON TABLE reimari_meta IS E'Kirjanpito, per Reimarin rajapinta, milloin viimeksi on haettu tietoja ja paljonko niitä saa hakea kerralla.';

COMMENT ON COLUMN reimari_meta.enimmaishakuvali IS E'Halutaan rajoittaa
Reimarille aiheutuvaa kuormaa, myöskin liian hitaat kyselyt
aikakatkaistaan Reimarin päässä. Tämä kertoo, kuinka pitkältä
aikaväliltä voidaan hakea tapahtumia yhdessä pyynnössä. Jos tähän
rajaan törmätään, haetaan vanhimmasta päästä tapahtumia jotta
tapahtumat päätyvät Harjaan aikajärjestyksessä.';

COMMENT ON COLUMN reimari_meta.aikakursori IS E'Päivämäärä, jota myöhempiä tietoja ei ole vielä haettu Harjaan.';

INSERT INTO reimari_meta (integraatio, enimmaishakuvali, aikakursori)
   VALUES (
      (SELECT id FROM integraatio WHERE jarjestelma = 'reimari' AND nimi = 'hae-toimenpiteet'),
       '6 months',
       '2017-10-01T12:12:12Z');

INSERT INTO reimari_meta (integraatio, enimmaishakuvali, aikakursori)
    VALUES (
      (SELECT id FROM integraatio WHERE jarjestelma = 'reimari' AND nimi = 'hae-turvalaitekomponentit'),
       '6 months',
       '2017-10-01T12:12:12Z');

INSERT INTO reimari_meta (integraatio, enimmaishakuvali, aikakursori)
    VALUES (
      (SELECT id FROM integraatio WHERE jarjestelma = 'reimari' AND nimi = 'hae-komponenttityypit'),
       '6 months',
       '2017-10-01T12:12:12Z');

INSERT INTO reimari_meta (integraatio, enimmaishakuvali, aikakursori)
    VALUES (
      (SELECT id FROM integraatio WHERE jarjestelma = 'reimari' AND nimi = 'hae-viat'),
       '6 months',
       '2017-10-01T12:12:12Z');
