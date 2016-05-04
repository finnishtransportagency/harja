-- Ylläpitokohteelle keskimääräinen vuorokausiliikenne ja nykyinen päällyste (YHA-integraatio)

ALTER TABLE yllapitokohde ADD COLUMN keskimaarainen_vuorokausiliikenne INTEGER;
ALTER TABLE yllapitokohde ADD COLUMN nykyinen_paallyste INTEGER;

ALTER TABLE yllapitokohdeosa DROP COLUMN kvl;
ALTER TABLE yllapitokohdeosa DROP COLUMN nykyinen_paallyste;

-- YHA:sta tulleilla kohteilla voi olla kohdenumero tyhjä, joten vanha uniikkius-indeksi (urakka, sopimus, kohdenumero) ei enää päde.
DROP INDEX index_paallystyskohde;