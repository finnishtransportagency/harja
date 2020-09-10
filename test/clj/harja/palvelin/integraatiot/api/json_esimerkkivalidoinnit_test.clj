(ns harja.palvelin.integraatiot.api.json-esimerkkivalidoinnit-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.tyokalut.json-validointi :refer [tee-validaattori]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.json-esimerkit :as json-esimerkit]
            [clojure.java.io :as io]))

(defn validoi [validaattori esimerkkipolku]
  (validaattori (slurp (io/resource esimerkkipolku))))

(deftest validoi-jsonit
  (is (nil? (validoi json-skeemat/urakan-haku-vastaus json-esimerkit/+urakan-haku-vastaus+)))
  (is (nil? (validoi json-skeemat/urakoiden-haku-vastaus json-esimerkit/+urakoiden-haku-vastaus+)))

  (is (nil? (validoi json-skeemat/laatupoikkeaman-kirjaus json-esimerkit/+laatupoikkeaman-kirjaus+)))

  (is (nil? (validoi json-skeemat/ilmoitustoimenpiteen-kirjaaminen json-esimerkit/+ilmoitustoimenpiteen-kirjaaminen+)))
  (is (nil? (validoi json-skeemat/ilmoitusten-haku json-esimerkit/+ilmoitusten-haku+)))
  (is (nil? (validoi json-skeemat/tietyoilmoituksen-kirjaus json-esimerkit/+tietyoilmoituksen-kirjaus+)))

  (is (nil? (validoi json-skeemat/pistetoteuman-kirjaus json-esimerkit/+pistetoteuman-kirjaus+)))
  (is (nil? (validoi json-skeemat/pistetoteuman-poisto json-esimerkit/+pistetoteuman-poisto+)))
  (is (nil? (validoi json-skeemat/reittitoteuman-kirjaus json-esimerkit/+reittitoteuman-kirjaus+)))
  (is (nil? (validoi json-skeemat/reittitoteuman-poisto json-esimerkit/+reittitoteuman-poisto+)))

  (is (nil? (validoi json-skeemat/turvallisuuspoikkeamien-kirjaus json-esimerkit/+turvallisuuspoikkeamien-kirjaus+)))

  (is (nil? (validoi json-skeemat/tielupien-haku json-esimerkit/+tielupien-haku+)))
  (is (nil? (validoi json-skeemat/tielupien-haku-vastaus json-esimerkit/+tielupien-haku-vastaus+)))

  (is (nil? (validoi json-skeemat/tietolajien-haku json-esimerkit/+tietolajien-haku+)))

  (is (nil? (validoi json-skeemat/varusteiden-haku-vastaus json-esimerkit/+varusteiden-haku-vastaus+)))
  (is (nil? (validoi json-skeemat/varusteen-lisays json-esimerkit/+varusteen-lisays+)))
  (is (nil? (validoi json-skeemat/varusteen-paivitys json-esimerkit/+varusteen-paivitys+)))
  (is (nil? (validoi json-skeemat/varusteen-poisto json-esimerkit/+varusteen-poisto+)))
  (is (nil? (validoi json-skeemat/varustetoteuman-kirjaus json-esimerkit/+varustetoteuman-kirjaus+)))

  (is (nil? (validoi json-skeemat/siltatarkastuksen-kirjaus json-esimerkit/+siltatarkastuksen-kirjaus+)))
  (is (nil? (validoi json-skeemat/siltatarkastuksen-poisto json-esimerkit/+siltatarkastuksen-poisto+)))
  (is (nil? (validoi json-skeemat/tiestotarkastuksen-kirjaus json-esimerkit/+tiestotarkastuksen-kirjaus+)))
  (is (nil? (validoi json-skeemat/tiestotarkastuksen-poisto json-esimerkit/+tiestotarkastuksen-poisto+)))
  (is (nil? (validoi json-skeemat/soratietarkastuksen-kirjaus json-esimerkit/+soratietarkastuksen-kirjaus+)))
  (is (nil? (validoi json-skeemat/soratietarkastuksen-poisto json-esimerkit/+soratietarkastuksen-poisto+)))
  (is (nil? (validoi json-skeemat/talvihoitotarkastuksen-kirjaus json-esimerkit/+talvihoitotarkastuksen-kirjaus+)))
  (is (nil? (validoi json-skeemat/talvihoitotarkastuksen-poisto json-esimerkit/+talvihoitotarkastuksen-poisto+)))

  (is (nil? (validoi json-skeemat/paivystajatietojen-kirjaus json-esimerkit/+paivystajatietojen-kirjaus+)))
  (is (nil? (validoi json-skeemat/paivystajatietojen-haku-vastaus json-esimerkit/+paivystajatietojen-haku-vastaus+)))

  (is (nil? (validoi json-skeemat/tyokoneenseuranta-kirjaus json-esimerkit/+tyokoneenseuranta-kirjaus+)))
  (is (nil? (validoi json-skeemat/tyokoneenseuranta-viivakirjaus json-esimerkit/+tyokoneenseuranta-viivakirjaus+)))

  (is (nil? (validoi json-skeemat/paivystyksen-poisto json-esimerkit/+paivystyksen-poisto-kirjaus+)))

  (is (nil? (validoi json-skeemat/urakan-yllapitokohteiden-haku-vastaus json-esimerkit/+urakan-yllapitokohteiden-haku+)))

  (is (nil? (validoi json-skeemat/paallystysilmoituksen-kirjaus json-esimerkit/+paallystysilmoituksen-kirjaus+)))

  (is (nil? (validoi json-skeemat/paallystyksen-aikataulun-kirjaus json-esimerkit/+paallystyksen-aikataulun-kirjaus+)))
  (is (nil? (validoi json-skeemat/tiemerkinnan-aikataulun-kirjaus json-esimerkit/+tiemerkinnan-aikataulun-kirjaus+)))

  (is (nil? (validoi json-skeemat/tietyomaan-kirjaus json-esimerkit/+tietyomaan-kirjaus+)))
  (is (nil? (validoi json-skeemat/tietyomaan-poisto json-esimerkit/+tietyomaan-poisto+)))

  (is (nil? (validoi json-skeemat/urakan-yhteystietojen-haku-vastaus json-esimerkit/+urakan-yhteystietoje-hakuvastaus+)))

  (is (nil? (validoi json-skeemat/urakan-yllapitokohteen-paivitys-request json-esimerkit/+urakan-yllapitokohteen-paivitys-request+)))

  (is (nil? (validoi json-skeemat/urakan-yllapitokohteen-maaramuutosten-kirjaus-request json-esimerkit/+urakan-yllapitokohteen-maaramuutosten-kirjaus+)))

  (is (nil? (validoi json-skeemat/urakan-yllapitokohteen-tarkastuksen-kirjaus-request json-esimerkit/+urakan-yllapitokohteen-tarkastuksen-kirjaus-request+)))
  (is (nil? (validoi json-skeemat/urakan-yllapitokohteen-tarkastuksen-poisto-request json-esimerkit/+yllapitokohteen-tarkastuksen-poisto-request+)))

  (is (nil? (validoi json-skeemat/urakan-tiemerkintatoteuman-kirjaus-request json-esimerkit/+urakan-tiemerkintatoteuman-kirjaus-request+)))
  (is (nil? (validoi json-skeemat/urakan-yllapitokohteen-tiemerkintatoteuman-kirjaus-request json-esimerkit/+urakan-yllapitokohteen-tiemerkintatoteuman-kirjaus-request+)))

  (is (nil? (validoi json-skeemat/urakan-tyotuntien-kirjaus-request json-esimerkit/+urakan-tyotuntien-kirjaus-request+)))

  (is (nil? (validoi json-skeemat/tieluvan-kirjaus-request json-esimerkit/+tieluvan-kirjaus-request+)))
  (is (nil? (validoi json-skeemat/tieluvan-kirjaus-request json-esimerkit/+tieluvan-kirjaus-request-johto-ja-kaapelilupa+)))
  (is (nil? (validoi json-skeemat/tieluvan-kirjaus-request json-esimerkit/+tieluvan-kirjaus-request-liittymalupa+)))
  (is (nil? (validoi json-skeemat/tieluvan-kirjaus-request json-esimerkit/+tieluvan-kirjaus-request-mainoslupa+)))
  (is (nil? (validoi json-skeemat/tieluvan-kirjaus-request json-esimerkit/+tieluvan-kirjaus-request-opastelupa+)))
  (is (nil? (validoi json-skeemat/tieluvan-kirjaus-request json-esimerkit/+tieluvan-kirjaus-request-suoja-alue-rakentamislupa+)))
  (is (nil? (validoi json-skeemat/tieluvan-kirjaus-request json-esimerkit/+tieluvan-kirjaus-request-tilapainen-myyntilupa+)))
  (is (nil? (validoi json-skeemat/tieluvan-kirjaus-request json-esimerkit/+tieluvan-kirjaus-request-tilapaisen-liikennemerkkijarjestely+)))
  (is (nil? (validoi json-skeemat/tieluvan-kirjaus-request json-esimerkit/+tieluvan-kirjaus-request-tyolupa+)))
  (is (nil? (validoi json-skeemat/tieluvan-kirjaus-request json-esimerkit/+tieluvan-kirjaus-request-vesihuoltolupa+)))

  (is (nil? (validoi json-skeemat/paikkausten-kirjaus-request json-esimerkit/+paikkausten-kirjaus-request+)))
  (is (nil? (validoi json-skeemat/paikkauskustannusten-kirjaus-request json-esimerkit/+paikkauskustannusten-kirjaus-request+))))

