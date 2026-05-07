-- Kommenttirivi ei vaikuta tarkistukseen
select id,
       nimi
  from urakka
 where id = :id
   and poistettu IS NOT TRUE;