-- Päivitä urakat paikalleen toteuma_materiaali tauluun
-- Ensimmäiset rivit ovat postgresql:n muistin lämmittämiseksi. Se nopeuttaa näiden suorittamista merkittävästi.
update toteuma_materiaali tm SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 1 offset 0) t2 WHERE tm.toteuma = t2.id;
update toteuma_materiaali tm SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 5 offset 1) t2 WHERE tm.toteuma = t2.id;
update toteuma_materiaali tm SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 10 offset 6) t2 WHERE tm.toteuma = t2.id;
update toteuma_materiaali tm SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc) t2 WHERE tm.toteuma = t2.id;
