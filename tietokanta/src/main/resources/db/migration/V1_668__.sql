-- Merkitse kanavien kolme kokonaishintaista toimenpidekoodia kuulumaan myös muutos-/lisätöihin
UPDATE toimenpidekoodi
SET hinnoittelu = ARRAY['kokonaishintainen'::hinnoittelutyyppi, 'muutoshintainen'::hinnoittelutyyppi]
WHERE nimi = 'Määräaikaishuolto' AND emo = (SELECT id
                                                                FROM toimenpidekoodi
                                                                WHERE koodi='24104');

UPDATE toimenpidekoodi
SET hinnoittelu = ARRAY['kokonaishintainen'::hinnoittelutyyppi, 'muutoshintainen'::hinnoittelutyyppi]
WHERE nimi = 'Muu huolto' AND emo = (SELECT id
                                         FROM toimenpidekoodi
                                         WHERE koodi='24104');

UPDATE toimenpidekoodi
SET hinnoittelu = ARRAY['kokonaishintainen'::hinnoittelutyyppi, 'muutoshintainen'::hinnoittelutyyppi]
WHERE nimi = 'Muu toimenpide' AND emo = (SELECT id
                                         FROM toimenpidekoodi
                                         WHERE koodi='24104');