-- name: hae-suolasakot
SELECT nimi AS urakka_nimi,
       (ss).keskilampotila,
       (ss).pitkakeskilampotila,
       (ss).sallittu_suolankaytto,
       (ss).suolankayton_sakkoraja,
       (ss).suolankayton_bonusraja,
       (ss).kohtuullisuustarkistettu_sakkoraja / (ss).sallittu_suolankaytto AS kerroin,
       (ss).sakkoraja,
       (ss).suolankaytto,
  CASE WHEN (ss).suolankaytto > (ss).suolankayton_bonusraja THEN
    ((ss).suolankaytto - 1.05 * (ss).kohtuullisuustarkistettu_sakkoraja)
    ELSE
      ((ss).suolankayton_bonusraja - (ss).suolankaytto)
  END                                                                        AS erotus,
       (ss).maara, (ss).vainsakkomaara,
       (ss).suolasakko as suolasakko, (it).korotus as korotus, (it).korotettuna as korotettuna,
       elinvoimakeskus_id, elinvoimakeskus_nimi, elinvoimakeskus_evknumero
  FROM
    (SELECT r1.*,
            laske_urakan_suolasakon_indeksitarkistus(
	      id, EXTRACT(YEAR FROM :alkupvm::date)::integer, (ss).suolasakko) AS it
       FROM (SELECT u.nimi, u.id, hoitokauden_suolasakkorivi(u.id,
                                                             :alkupvm::date,
							     :loppupvm::date) AS ss,
                    evk.id AS elinvoimakeskus_id,
                    evk.nimi AS elinvoimakeskus_nimi,
                   right(cast(evk.elynumero as varchar), 2) AS elinvoimakeskus_evknumero
               FROM urakka u JOIN organisaatio evk ON u.elinvoimakeskus_id = evk.id
	      WHERE u.id in (:urakat) AND u.tyyppi = 'hoito' ORDER BY u.nimi) r1) r2;
