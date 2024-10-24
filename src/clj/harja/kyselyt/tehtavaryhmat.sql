-- name: tehtavaryhmaotsikot
SELECT tro.id, tro.otsikko
  FROM tehtavaryhmaotsikko tro
 ORDER BY tro.otsikko ASC;

-- name: hae-tehtavaryhma
SELECT tro.id        AS tehtavaryhmaotsikko_id,
       tro.otsikko,
       tr.id         AS tehtavaryhma_id,
       tr.nimi,
       tr.voimassaolo_alkuvuosi,
       tr.voimassaolo_loppuvuosi,
       tr.jarjestys,
       tr.nakyva,
       tr.versio,
       tr.yksiloiva_tunniste,
       JSONB_AGG(ROW_TO_JSON(ROW (t.id, t.nimi, t.yksikko, t.jarjestys, t.api_seuranta, t.suoritettavatehtava,
           t.piilota, t.api_tunnus, t."mhu-tehtava?", t.yksiloiva_tunniste,
           t.voimassaolo_alkuvuosi, t.voimassaolo_loppuvuosi, t.kasin_lisattava_maara,
           t."raportoi-tehtava?", t.materiaaliluokka_id, t.materiaalikoodi_id, t.aluetieto))) AS tehtavat
  FROM tehtavaryhmaotsikko tro
           JOIN tehtavaryhma tr ON tro.id = tr.tehtavaryhmaotsikko_id AND tr.id = :id
           LEFT JOIN tehtava t ON t.tehtavaryhma = tr.id AND "mhu-tehtava?" = TRUE
  GROUP BY tro.id, tr.id, tro.otsikko;


-- name: hae-mhu-tehtavaryhmaotsikot-tehtavaryhmat-ja-tehtavat
SELECT tro.id        AS tehtavaryhmaotsikko_id,
       tro.otsikko,
       tr.id         AS tehtavaryhma_id,
       tr.nimi,
       tr.voimassaolo_alkuvuosi,
       tr.voimassaolo_loppuvuosi,
       tr.jarjestys,
       tr.nakyva,
       tr.versio,
       tr.yksiloiva_tunniste,
       JSONB_AGG(ROW_TO_JSON(ROW (t.id, t.nimi, t.yksikko, t.jarjestys, t.api_seuranta, t.suoritettavatehtava,
           t.piilota, t.api_tunnus, t."mhu-tehtava?", t.yksiloiva_tunniste,
           t.voimassaolo_alkuvuosi, t.voimassaolo_loppuvuosi, t.kasin_lisattava_maara,
           t."raportoi-tehtava?", t.materiaaliluokka_id, t.materiaalikoodi_id, t.aluetieto))) AS tehtavat
  FROM tehtavaryhmaotsikko tro
           JOIN tehtavaryhma tr ON tro.id = tr.tehtavaryhmaotsikko_id
           LEFT JOIN tehtava t ON t.tehtavaryhma = tr.id AND "mhu-tehtava?" = TRUE
 GROUP BY tro.id, tr.id, tro.otsikko
 ORDER BY tro.otsikko ASC;

-- name: paivita-tehtavaryhma!
-- Tällä hetkellä voi päivittää vain voimassaoloa. Nimen ja muiden muutokset mahdollistetaan, jos niitä joskus tarvitaan.
UPDATE tehtavaryhma
   SET voimassaolo_alkuvuosi  = :voimassaolo_alkuvuosi,
       voimassaolo_loppuvuosi = :voimassaolo_loppuvuosi
 WHERE id = :tehtavaryhma_id;

-- name: tehtavat-tehtavaryhmaotsikoittain
-- Listaa kaikki tehtävät ja niille suunnitellut ja toteutuneet määrät tehtäväryhmäotsikon perusteella ryhmiteltynä.
-- Äkillisille hoitotöille on ihan oma tehtäväryhmä ja tätä ei voida käyttää siihen
SELECT tk.id                                     AS id,
       tk.nimi                                   AS tehtava,
       tk.suunnitteluyksikko                     AS yksikko,
       COALESCE(NULLIF(ru.urakkakohtainen_nimi,''), r.nimi) AS rahavaraus,
       -- Ei voi olla sekä rahavaraus, että käsin lisättävä tehtävä. Rahavarauksille toteumat on euroja ja ne lisätään kuluista.
       CASE
           WHEN (tk.kasin_lisattava_maara AND r.nimi IS NULL) THEN TRUE
           ELSE FALSE END                        AS kasin_lisattava_maara
  FROM tehtava tk
           JOIN urakka u ON :urakka = u.id
           JOIN tehtavaryhma tr_alataso ON tr_alataso.id = tk.tehtavaryhma -- Alataso on linkitetty toimenpidekoodiin
                                            AND (tr_alataso.voimassaolo_alkuvuosi IS NULL OR tr_alataso.voimassaolo_alkuvuosi <= DATE_PART('year', u.alkupvm)::INTEGER)
                                            AND (tr_alataso.voimassaolo_loppuvuosi IS NULL OR tr_alataso.voimassaolo_loppuvuosi >= DATE_PART('year', u.alkupvm)::INTEGER)

           JOIN tehtavaryhmaotsikko o ON tr_alataso.tehtavaryhmaotsikko_id = o.id
                                         AND (:otsikko::TEXT IS NULL OR o.otsikko = :otsikko)
           LEFT JOIN rahavaraus_tehtava rt on rt.tehtava_id = tk.id
           LEFT JOIN rahavaraus_urakka ru
                     ON rt.rahavaraus_id = ru.rahavaraus_id
                         AND ru.urakka_id = :urakka
           LEFT JOIN rahavaraus r ON ru.rahavaraus_id = r.id
 WHERE (tk.voimassaolo_alkuvuosi IS NULL OR tk.voimassaolo_alkuvuosi <= DATE_PART('year', u.alkupvm)::INTEGER)
   AND (tk.voimassaolo_loppuvuosi IS NULL OR tk.voimassaolo_loppuvuosi >= DATE_PART('year', u.alkupvm)::INTEGER)
   AND tk.poistettu IS NOT TRUE
   -- Rajataan pois hoitoluokka- eli aluetiedot paitsi, jos niihin saa kirjata toteumia käsin
   AND (tk.aluetieto = FALSE OR (tk.aluetieto = TRUE AND tk.kasin_lisattava_maara = TRUE))
   -- haetaan Lisää toteuma-listaan vain MH-urakoissa käytössä olevat tehtävät, ei samaa tarkoittavia alueurakoiden tehtäviä.
   AND tk."mhu-tehtava?" = TRUE
   -- rajataan pois tehtävät joilla ei ole suunnitteluyksikköä ja tehtävät joiden yksikkö on euro
   -- mutta otetaan mukaan Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen ja lisätyöt
   AND ((tk.suunnitteluyksikko IS NOT NULL AND tk.suunnitteluyksikko != 'euroa') OR
        tk.yksiloiva_tunniste IN ('49b7388b-419c-47fa-9b1b-3797f1fab21d',
                                  '63a2585b-5597-43ea-945c-1b25b16a06e2',
                                  'b3a7a210-4ba6-4555-905c-fef7308dc5ec',
                                  'e32341fc-775a-490a-8eab-c98b8849f968',
                                  '0c466f20-620d-407d-87b0-3cbb41e8342e',
                                  'c058933e-58d3-414d-99d1-352929aa8cf9'))
 ORDER BY tk.jarjestys;

-- name: hae-tehtavaryhma-tunnisteella
SELECT tr.id, tr.nimi
  FROM tehtavaryhma tr
 WHERE tr.yksiloiva_tunniste = :yksiloiva_tunniste::UUID;
