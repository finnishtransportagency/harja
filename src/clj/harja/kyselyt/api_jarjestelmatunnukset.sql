-- name: hae-jarjestelmatunnukset
SELECT k.id, k.kayttajanimi, k.kuvaus, k.luotu,
       o.nimi as organisaatio_nimi,
       o.id as organisaatio_id,
      (SELECT array_agg(u.nimi)
         FROM urakka u
        WHERE u.urakoitsija = o.id AND
	      u.alkupvm <= current_date AND
	      u.loppupvm >= current_date) as urakat
  FROM kayttaja k
       JOIN organisaatio o ON k.organisaatio = o.id
 WHERE jarjestelma = true AND
       poistettu = false
