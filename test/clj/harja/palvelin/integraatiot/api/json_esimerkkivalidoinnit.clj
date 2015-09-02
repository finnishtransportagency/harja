(ns harja.palvelin.integraatiot.api.json_esimerkkivalidoinnit
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.tyokalut.json_validointi :as json-validointi]
            [harja.palvelin.integraatiot.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.esimerkit :as esimerkit]
            [clojure.java.io :as io]))

(defn validoi [skeemapolku esimerkkipolku]
  (json-validointi/validoi skeemapolku (slurp (io/resource esimerkkipolku))))

(deftest validoi-jsonit
  (is (nil? (validoi skeemat/+urakan-haku-vastaus+ esimerkit/+urakan-haku-vastaus+)))
  (is (nil? (validoi skeemat/+urakoiden-haku-vastaus+ esimerkit/+urakoiden-haku-vastaus+)))

  (is (nil? (validoi skeemat/+havainnon-kirjaus+ esimerkit/+havainnon-kirjaus+)))

  (is (nil? (validoi skeemat/+ilmoituskuittauksen-kirjaaminen+ esimerkit/+ilmoituskuittauksen-kirjaaminen+)))
  (is (nil? (validoi skeemat/+ilmoitusten-haku+ esimerkit/+ilmoitusten-haku+)))
  (is (nil? (validoi skeemat/+tietyoilmoituksen-kirjaus+ esimerkit/+tietyoilmoituksen-kirjaus+)))

  (is (nil? (validoi skeemat/+pistetoteuman-kirjaus+ esimerkit/+pistetoteuman-kirjaus+)))
  (is (nil? (validoi skeemat/+reittitoteuman-kirjaus+ esimerkit/+reittitoteuman-kirjaus+)))

  (is (nil? (validoi skeemat/+turvallisuuspoikkeamien-kirjaus+ esimerkit/+turvallisuuspoikkeamien-kirjaus+)))

  (is (nil? (validoi skeemat/+tielupien-haku+ esimerkit/+tielupien-haku+)))
  (is (nil? (validoi skeemat/+tielupien-haku-vastaus+ esimerkit/+tielupien-haku-vastaus+)))

  (is (nil? (validoi skeemat/+tietolajien-haku+ esimerkit/+tietolajien-haku+)))

  (is (nil? (validoi skeemat/+varusteen-haku-vastaus+ esimerkit/+varusteen-haku-vastaus+)))
  (is (nil? (validoi skeemat/+varusteiden-haku-vastaus+ esimerkit/+varusteiden-haku-vastaus+)))
  (is (nil? (validoi skeemat/+varusteen-lisays+ esimerkit/+varusteen-lisays+)))
  (is (nil? (validoi skeemat/+varusteen-paivitys+ esimerkit/+varusteen-paivitys+)))
  (is (nil? (validoi skeemat/+varusteen-poisto+ esimerkit/+varusteen-poisto+)))
  (is (nil? (validoi skeemat/+varustetoteuman-kirjaus+ esimerkit/+varustetoteuman-kirjaus+)))

  (is (nil? (validoi skeemat/+siltatarkastuksen-kirjaus+ esimerkit/+siltatarkastuksen-kirjaus+)))
  (is (nil? (validoi skeemat/+tiestotarkastuksen-kirjaus+ esimerkit/+tiestotarkastuksen-kirjaus+)))
  (is (nil? (validoi skeemat/+soratietarkastuksen-kirjaus+ esimerkit/+soratietarkastuksen-kirjaus+)))
  (is (nil? (validoi skeemat/+talvihoitotarkastuksen-kirjaus+ esimerkit/+talvihoitotarkastuksen-kirjaus+)))

  (is (nil? (validoi skeemat/+paivystajatietojen-kirjaus+ esimerkit/+paivystajatietojen-kirjaus+)))
  (is (nil? (validoi skeemat/+paivystajatietojen-haku+ esimerkit/+paivystajatietojen-haku+)))
  (is (nil? (validoi skeemat/+paivystajatietojen-haku-vastaus+ esimerkit/+paivystajatietojen-haku-vastaus+)))

  (is (nil? (validoi skeemat/+tyokoneenseuranta-kirjaus+ esimerkit/+tyokoneenseuranta-kirjaus+))))