-- Tee materiaalin maks. määrästä unique per hk

-- Poista duplikaatit ennen constraintin luontia (jätetään pienimmällä id:llä oleva)

DELETE
  FROM materiaalin_kaytto
 WHERE id IN (SELECT id
               FROM (SELECT  id, ROW_NUMBER() OVER (partition BY alkupvm,loppupvm,materiaali,urakka,sopimus
                                                    ORDER BY id) AS rnum
	               FROM materiaalin_kaytto) t
              WHERE t.rnum > 1);

-- Luodaan unique constraint

ALTER TABLE materiaalin_kaytto
 ADD CONSTRAINT materiaali_kaytto_uniikki UNIQUE (alkupvm,loppupvm,materiaali,urakka,sopimus);
