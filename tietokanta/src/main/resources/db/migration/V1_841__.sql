-- Päivitetään toteuma_tehtava tauluun kaikki tarvittavat urakka_id:t.
-- Tämä täytyy tehdä osissa, koska se tulee muuten viemään vuorokausia.
-- Päivitys aloitetaan pienestä ja annetaan postgresin muistin pikkuhiljaa nopeuttaa päivitystä.
update toteuma_tehtava tt SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 1 offset 0) t2 WHERE tt.toteuma = t2.id;
update toteuma_tehtava tt SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 5 offset 1) t2 WHERE tt.toteuma = t2.id;
update toteuma_tehtava tt SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 10 offset 6) t2 WHERE tt.toteuma = t2.id;
update toteuma_tehtava tt SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 100 offset 16) t2 WHERE tt.toteuma = t2.id;
update toteuma_tehtava tt SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 1000 offset 116) t2 WHERE tt.toteuma = t2.id;
update toteuma_tehtava tt SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 10000 offset 1116) t2 WHERE tt.toteuma = t2.id;
update toteuma_tehtava tt SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 100000 offset 11116) t2 WHERE tt.toteuma = t2.id;
update toteuma_tehtava tt SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 1000000 offset 111116) t2 WHERE tt.toteuma = t2.id;
update toteuma_tehtava tt SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 10000000 offset 1111116) t2 WHERE tt.toteuma = t2.id;
update toteuma_tehtava tt SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 30000000 offset 11111116) t2 WHERE tt.toteuma = t2.id;
