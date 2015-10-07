(ns harja.palvelin.integraatiot.api.json_esimerkkivalidoinnit
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.tyokalut.json_validointi :as json-validointi]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.json-esimerkit :as json-esimerkit]
            [clojure.java.io :as io]))

(defn validoi [skeemapolku esimerkkipolku]
  (json-validointi/validoi skeemapolku (slurp (io/resource esimerkkipolku))))

(deftest validoi-jsonit
  (is (nil? (validoi json-skeemat/+urakan-haku-vastaus+ json-esimerkit/+urakan-haku-vastaus+)))
  (is (nil? (validoi json-skeemat/+urakoiden-haku-vastaus+ json-esimerkit/+urakoiden-haku-vastaus+)))

  (is (nil? (validoi json-skeemat/+havainnon-kirjaus+ json-esimerkit/+havainnon-kirjaus+)))

  (is (nil? (validoi json-skeemat/+ilmoituskuittauksen-kirjaaminen+ json-esimerkit/+ilmoituskuittauksen-kirjaaminen+)))
  (is (nil? (validoi json-skeemat/+ilmoitusten-haku+ json-esimerkit/+ilmoitusten-haku+)))
  (is (nil? (validoi json-skeemat/+tietyoilmoituksen-kirjaus+ json-esimerkit/+tietyoilmoituksen-kirjaus+)))

  (is (nil? (validoi json-skeemat/+pistetoteuman-kirjaus+ json-esimerkit/+pistetoteuman-kirjaus+)))
  (is (nil? (validoi json-skeemat/+reittitoteuman-kirjaus+ json-esimerkit/+reittitoteuman-kirjaus+)))

  (is (nil? (validoi json-skeemat/+turvallisuuspoikkeamien-kirjaus+ json-esimerkit/+turvallisuuspoikkeamien-kirjaus+)))

  (is (nil? (validoi json-skeemat/+tielupien-haku+ json-esimerkit/+tielupien-haku+)))
  (is (nil? (validoi json-skeemat/+tielupien-haku-vastaus+ json-esimerkit/+tielupien-haku-vastaus+)))

  (is (nil? (validoi json-skeemat/+tietolajien-haku+ json-esimerkit/+tietolajien-haku+)))

  (is (nil? (validoi json-skeemat/+varusteen-haku-vastaus+ json-esimerkit/+varusteen-haku-vastaus+)))
  (is (nil? (validoi json-skeemat/+varusteiden-haku-vastaus+ json-esimerkit/+varusteiden-haku-vastaus+)))
  (is (nil? (validoi json-skeemat/+varusteen-lisays+ json-esimerkit/+varusteen-lisays+)))
  (is (nil? (validoi json-skeemat/+varusteen-paivitys+ json-esimerkit/+varusteen-paivitys+)))
  (is (nil? (validoi json-skeemat/+varusteen-poisto+ json-esimerkit/+varusteen-poisto+)))
  (is (nil? (validoi json-skeemat/+varustetoteuman-kirjaus+ json-esimerkit/+varustetoteuman-kirjaus+)))

  (is (nil? (validoi json-skeemat/+siltatarkastuksen-kirjaus+ json-esimerkit/+siltatarkastuksen-kirjaus+)))
  (is (nil? (validoi json-skeemat/+tiestotarkastuksen-kirjaus+ json-esimerkit/+tiestotarkastuksen-kirjaus+)))
  (is (nil? (validoi json-skeemat/+soratietarkastuksen-kirjaus+ json-esimerkit/+soratietarkastuksen-kirjaus+)))
  (is (nil? (validoi json-skeemat/+talvihoitotarkastuksen-kirjaus+ json-esimerkit/+talvihoitotarkastuksen-kirjaus+)))

  (is (nil? (validoi json-skeemat/+paivystajatietojen-kirjaus+ json-esimerkit/+paivystajatietojen-kirjaus+)))
  (is (nil? (validoi json-skeemat/+paivystajatietojen-haku+ json-esimerkit/+paivystajatietojen-haku+)))
  (is (nil? (validoi json-skeemat/+paivystajatietojen-haku-vastaus+ json-esimerkit/+paivystajatietojen-haku-vastaus+)))

  (is (nil? (validoi json-skeemat/+tyokoneenseuranta-kirjaus+ json-esimerkit/+tyokoneenseuranta-kirjaus+))))