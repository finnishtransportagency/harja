--
-- Testidatan tehtävät nollataan (tänne ajetaan 2025 marraskuun tuotantodumppi)
-- Joten tänne voi lisäillä tehtävä / toimenpide päivityksiä 1222 migraation jälkeen, mikäli niiden halutaan pysyvän ajan tasalla
UPDATE tehtava
SET   materiaalikoodi_id   = (SELECT id
                                FROM materiaalikoodi
                               WHERE yksiloiva_tunniste = '378bc7d7-4ec2-4fb9-96ca-29584cfd09fe'), -- MATERIAALI: Hiekoitushiekka, ennalta arvaamattomien kuljetusten avustaminen
      materiaaliluokka_id  = (SELECT id
                                FROM materiaaliluokka
                               WHERE materiaalityyppi = 'hiekoitushiekka'),
      muokattu             = current_timestamp,
      muokkaaja            = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
WHERE yksiloiva_tunniste   = 'ae67d2b5-a9d9-4880-a7ee-b3870737a177'; -- TEHTÄVÄ: Ennalta arvaamattomien kuljetusten avustaminen (materiaali)

