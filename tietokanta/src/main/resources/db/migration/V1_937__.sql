-- Luodaan raporttia varten uusi materiaalikoodi - Hox päivitä tämä
ALTER TYPE materiaalityyppi ADD VALUE 'erityisalue';
ALTER TYPE materiaalityyppi ADD VALUE 'formiaatti';
ALTER TYPE materiaalityyppi ADD VALUE 'kesasuola';
ALTER TYPE materiaalityyppi ADD VALUE 'hiekoitushiekka';
ALTER TYPE materiaalityyppi ADD VALUE 'murske';

UPDATE materiaalikoodi SET materiaalityyppi  = 'erityisalue'::materiaalityyppi WHERE nimi = 'Erityisalueet CaCl2-liuos';
UPDATE materiaalikoodi SET materiaalityyppi  = 'erityisalue'::materiaalityyppi WHERE nimi = 'Erityisalueet NaCl';
UPDATE materiaalikoodi SET materiaalityyppi  = 'erityisalue'::materiaalityyppi WHERE nimi = 'Erityisalueet NaCl-liuos';
UPDATE materiaalikoodi SET materiaalityyppi  = 'talvisuola'::materiaalityyppi WHERE nimi = 'Hiekoitushiekan suola';
UPDATE materiaalikoodi SET materiaalityyppi  = 'formiaatti'::materiaalityyppi WHERE nimi = 'Kaliumformiaatti';
UPDATE materiaalikoodi SET materiaalityyppi  = 'formiaatti'::materiaalityyppi WHERE nimi = 'Natriumformiaatti';
UPDATE materiaalikoodi SET materiaalityyppi  = 'formiaatti'::materiaalityyppi WHERE nimi = 'Natriumformiaattiliuos';
UPDATE materiaalikoodi SET materiaalityyppi  = 'kesasuola'::materiaalityyppi WHERE nimi = 'Kesäsuola (pölynsidonta)';
UPDATE materiaalikoodi SET materiaalityyppi  = 'kesasuola'::materiaalityyppi WHERE nimi = 'Kesäsuola (sorateiden kevätkunnostus)';
UPDATE materiaalikoodi SET materiaalityyppi  = 'hiekoitushiekka'::materiaalityyppi WHERE nimi = 'Hiekoitushiekka';
UPDATE materiaalikoodi SET materiaalityyppi  = 'muu'::materiaalityyppi WHERE nimi = 'Jätteet kaatopaikalle';
UPDATE materiaalikoodi SET materiaalityyppi  = 'murske'::materiaalityyppi WHERE nimi = 'Murskeet';
UPDATE materiaalikoodi SET materiaalityyppi  = 'muu'::materiaalityyppi WHERE nimi = 'Rikkaruohojen torjunta-aineet';

