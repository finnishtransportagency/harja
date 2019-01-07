-- POT-lomakkeeseen tehtiin muutos, jonka myötä alustatoimenpiteille pitää antaa ajorata ja kaista. Nämä tiedot ovat pakollisia,
-- kun ne lähetetään YHA:an. Tässä laitetaan kaikille niille pot-lomakkeille, joita ei ole lähetetty YHA:an, alustatoimien ajorata ja kaista samaksi kuin
-- pääkohteen ajorata ja kaista.
DO $$
DECLARE
  paallystysilmoitusrivi RECORD;
  alustatoimet JSONB;
  paivitetty_alustatoimet JSONB := '[]';
BEGIN
  <<ulkoinen_loop>>
  FOR paallystysilmoitusrivi IN (SELECT pi.ilmoitustiedot AS ilmoitustiedot, yk.id AS yk_id, pi.id AS pi_id
                                 FROM paallystysilmoitus pi
                                   JOIN yllapitokohde yk ON pi.paallystyskohde = yk.id
                                 WHERE (jsonb_array_length(pi.ilmoitustiedot #> '{alustatoimet}') <> 0 AND
                                        (SELECT NOT EXISTS(SELECT *
                                                           FROM (SELECT alustatoimet.jsonb_array_elements #> '{tr-ajorata}' AS ajorata
                                                                 FROM (SELECT jsonb_array_elements(
                                                                     pi.ilmoitustiedot #> '{alustatoimet}')) AS alustatoimet) AS ajorataiset
                                                           WHERE ajorataiset.ajorata IS NOT NULL)) AND
                                        (SELECT NOT EXISTS(SELECT *
                                                           FROM (SELECT alustatoimet.jsonb_array_elements #> '{tr-kaista}' AS ajorata
                                                                 FROM (SELECT jsonb_array_elements(
                                                                     pi.ilmoitustiedot #> '{alustatoimet}')) AS alustatoimet) AS ajorataiset
                                                           WHERE ajorataiset.ajorata IS NOT NULL))) AND
                                       yk.vuodet @> ARRAY[2018] AND
                                       yk.lahetys_onnistunut IS FALSE) LOOP
    paivitetty_alustatoimet = '[]'::JSONB;
    <<sisainen_loop>>
    FOR alustatoimet IN (SELECT jsonb_array_elements(paallystysilmoitusrivi.ilmoitustiedot->'alustatoimet')) LOOP
      paivitetty_alustatoimet = paivitetty_alustatoimet || (jsonb_build_array(alustatoimet || jsonb_build_object('tr-ajorata', (SELECT tr_ajorata FROM yllapitokohde WHERE id = paallystysilmoitusrivi.yk_id),
                                                                                                                 'tr-kaista', (SELECT tr_kaista FROM yllapitokohde WHERE id = paallystysilmoitusrivi.yk_id))));
    END LOOP sisainen_loop;
    UPDATE paallystysilmoitus
    SET ilmoitustiedot = jsonb_set(ilmoitustiedot, '{alustatoimet}', paivitetty_alustatoimet)
    WHERE id = paallystysilmoitusrivi.pi_id;
  END LOOP ulkoinen_loop;
END $$;