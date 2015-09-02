(ns harja.palvelin.integraatiot.api.json-esimerkkivalidoinnit
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.tyokalut.json_validointi :as json-validointi]
            [harja.palvelin.integraatiot.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.esimerkit :as esimerkit]
            [clojure.java.io :as io]))

(deftest validoi-jsonit
  (let [skeemapolku-esimerkkipolku
        [[skeemat/+urakan-haku-vastaus+ esimerkit/+urakan-haku-vastaus+]
         [skeemat/+urakoiden-haku-vastaus+ esimerkit/+urakoiden-haku-vastaus+]

         [skeemat/+havainnon-kirjaus+ esimerkit/+havainnon-kirjaus+]

         [skeemat/+ilmoituskuittauksen-kirjaaminen+ esimerkit/+ilmoituskuittauksen-kirjaaminen+]
         [skeemat/+ilmoitusten-haku+ esimerkit/+ilmoitusten-haku+]
         [skeemat/+tietyoilmoituksen-kirjaus+ esimerkit/+tietyoilmoituksen-kirjaus+]

         [skeemat/+pistetoteuman-kirjaus+ esimerkit/+pistetoteuman-kirjaus+]
         [skeemat/+reittitoteuman-kirjaus+ esimerkit/+reittitoteuman-kirjaus+]

         [skeemat/+poikkeamien-kirjaus+ esimerkit/+poikkeamien-kirjaus+]
         [skeemat/+turvallisuuspoikkeamien-kirjaus+ esimerkit/+turvallisuuspoikkeamien-kirjaus+]

         [skeemat/+tielupien-haku+ esimerkit/+tielupien-haku+]
         [skeemat/+tielupien-haku-vastaus+ esimerkit/+tielupien-haku-vastaus+]

         [skeemat/+tietolajien-haku+ esimerkit/+tietolajien-haku+]

         [skeemat/+varusteen-haku-vastaus+ esimerkit/+varusteen-haku-vastaus+]
         [skeemat/+varusteiden-haku-vastaus+ esimerkit/+varusteiden-haku-vastaus+]
         [skeemat/+varusteen-lisays+ esimerkit/+varusteen-lisays+]
         [skeemat/+varusteen-paivitys+ esimerkit/+varusteen-paivitys+]
         [skeemat/+varusteen-poisto+ esimerkit/+varusteen-poisto+]
         [skeemat/+varustetoteuman-kirjaus+ esimerkit/+varustetoteuman-kirjaus+]

         [skeemat/+siltatarkastuksen-kirjaus+ esimerkit/+siltatarkastuksen-kirjaus+]
         [skeemat/+tiestotarkastuksen-kirjaus+ esimerkit/+tiestotarkastuksen-kirjaus+]
         [skeemat/+soratietarkastuksen-kirjaus+ esimerkit/+soratietarkastuksen-kirjaus+]
         [skeemat/+talvihoitotarkastuksen-kirjaus+ esimerkit/+talvihoitotarkastuksen-kirjaus+]

         [skeemat/+paivystajatietojen-kirjaus+ esimerkit/+paivystajatietojen-kirjaus+]
         [skeemat/+paivystajatietojen-haku+ esimerkit/+paivystajatietojen-haku+]
         [skeemat/+paivystajatietojen-haku-vastaus+ esimerkit/+paivystajatietojen-haku-vastaus+]

         [skeemat/+tyokoneenseuranta-kirjaus+ esimerkit/+tyokoneenseuranta-kirjaus+]]
        skeemapolku-esimerkkidata (mapv
                                    (fn [[skeemapolku esimerkkipolku]]
                                      (let [esimerkkidata (slurp (io/resource esimerkkipolku))]
                                        [skeemapolku esimerkkidata]))
                                    skeemapolku-esimerkkipolku)]
    (doseq [validoitava skeemapolku-esimerkkidata]
      (json-validointi/validoi (first validoitava) (second validoitava)))))