-- Ylläpitokohteelle keskimääräinen vuorokausiliikenne ja nykyinen päällyste (YHA-integraatio)

ALTER TABLE yllapitokohde ADD COLUMN keskimaarainen_vuorokausiliikenne INTEGER;
ALTER TABLE yllapitokohde ADD COLUMN nykyinen_paallyste INTEGER;

ALTER TABLE yllapitokohdeosa DROP COLUMN kvl;
ALTER TABLE yllapitokohdeosa DROP COLUMN nykyinen_paallyste;