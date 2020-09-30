-- Lisätään uuid tehtäväryhmän muutamalle lisätyöhön liittyvälle ryhmälle, jotta nimen vaihto
-- ei sotke tehtäväryhmän käyttöä hakulausekkeissa.
UPDATE tehtavaryhma SET yksiloiva_tunniste = '91896f23-1d4a-4385-8e1f-28a8f0c8ba80'
    WHERE nimi = 'Lisätyöt' AND otsikko = '7.0 LISÄTYÖT';
UPDATE tehtavaryhma SET yksiloiva_tunniste = '0b65b36d-e84e-40b6-a0ad-e4f539f05227'
    WHERE nimi = 'Välitaso Lisätyöt' AND otsikko = '7.0 LISÄTYÖT';
UPDATE tehtavaryhma SET yksiloiva_tunniste = '6a2f1000-bb92-48d7-af80-7510c532115a'
    WHERE nimi = 'Alataso Lisätyöt' AND otsikko = '7.0 LISÄTYÖT';
