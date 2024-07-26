-- Lisätään rahavarausten ja toimenpiteiden analytiikan integraation tyypit integraatio tauluun
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('analytiikka', 'analytiikka-hae-toimenpiteet');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('analytiikka', 'analytiikka-hae-rahavaraukset');

-- Koska analytiikkaan liittyviä integraatioita on useita, päivitetään ne kaikki kerralla analytiikka järjestelmän alle
UPDATE integraatio
   SET jarjestelma = 'analytiikka'
 WHERE nimi IN ('analytiikka-hae-tehtavat', 'analytiikka-hae-paikkaukset',
                'analytiikka-hae-paikkauskohteet', 'analytiikka-hae-hoidon-paikkaukset',
                'analytiikka-hae-paallystysilmoitukset',
                'analytiikka-hae-paallystyskohteiden-aikataulut', 'analytiikka-hae-paallystyskohteet',
                'analytiikka-hae-paallystysurakat', 'analytiikka-hae-toteumat',
                'analytiikka-hae-turvallisuuspoikkeamat', 'analytiikka-hae-suunnitellut-tehtavamaarat',
                'analytiikka-hae-suunnitellut-materiaalimaarat', 'analytiikka-hae-organisaatiot',
                'analytiikka-hae-urakat', 'analytiikka-hae-tehtavat', 'analytiikka-hae-materiaalikoodit'
     );
