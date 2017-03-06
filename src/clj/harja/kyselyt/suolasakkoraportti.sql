-- name: hae-suolasakot
SELECT nimi as urakka_nimi, (ss).keskilampotila, (ss).pitkakeskilampotila,
       (ss).sallittu_suolankaytto, (ss).suolankayton_sakkoraja,  (ss).suolankayton_bonusraja,
       (ss).kohtuullisuustarkistettu_sakkoraja / (ss).sallittu_suolankaytto as kerroin,
       (ss).sakkoraja, (ss).suolankaytto,
  CASE WHEN (ss).suolankaytto > (ss).suolankayton_bonusraja THEN
    ((ss).suolankaytto - 1.05 * (ss).kohtuullisuustarkistettu_sakkoraja)
    ELSE
      ((ss).suolankayton_bonusraja - (ss).suolankaytto)
  END AS erotus,
       (ss).maara, (ss).vainsakkomaara,
       (ss).suolasakko as suolasakko, (it).korotus as korotus, (it).korotettuna as korotettuna,
       hallintayksikko_id, hallintayksikko_nimi, hallintayksikko_elynumero
  FROM
    (SELECT r1.*,
            laske_urakan_suolasakon_indeksitarkistus(
	      id, EXTRACT(YEAR FROM :alkupvm::date)::integer, (ss).suolasakko) AS it
       FROM (SELECT u.nimi, u.id, hoitokauden_suolasakkorivi(u.id,
                                                             :alkupvm::date,
							     :loppupvm::date) AS ss,
		    hy.id AS hallintayksikko_id,
		    hy.nimi AS hallintayksikko_nimi,
                   lpad(cast(hy.elynumero as varchar), 2, '0') AS hallintayksikko_elynumero
               FROM urakka u JOIN organisaatio hy ON u.hallintayksikko = hy.id
	      WHERE u.id in (:urakat)) r1) r2;
