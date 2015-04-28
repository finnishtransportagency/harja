-- name: listaa-urakan-toteumat
-- Listaa kaikki urakan toteumat
SELECT id, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi
FROM   toteuma
WHERE  urakka = :urakka

