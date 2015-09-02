(ns harja.palvelin.integraatiot.api.json-esimerkkivalidoinnit
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.tyokalut.json_validointi :as json-validointi]
            [harja.palvelin.integraatiot.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.esimerkit :as esimerkit]
            [clojure.java.io :as io]))

(deftest validoi-jsonit
  (let [skeemapolku-esimerkkipolku [[skeemat/+urakan-haku-vastaus+ esimerkit/+urakan-haku-vastaus+]
                                    [skeemat/+urakoiden-haku-vastaus+ esimerkit/+urakoiden-haku-vastaus+]
                                    #_[skeemat/+havainnon-kirjaus+ esimerkit/+havainnon-kirjaus+] ; FIXME RIKKI
                                    #_[skeemat/+pistetoteuman-kirjaus+ esimerkit/+pistetoteuman-kirjaus+]; FIXME RIKKI "JSON ei ole validia: /pistetoteuma/toteuma: Ylimääräisiä kenttiä: selite"
                                    [skeemat/+reittitoteuman-kirjaus+ esimerkit/+reittitoteuman-kirjaus+]
                                    [skeemat/+tiestotarkastuksen-kirjaus+ esimerkit/+tiestotarkastuksen-kirjaus+]
                                    [skeemat/+soratietarkastuksen-kirjaus+ esimerkit/+soratietarkastuksen-kirjaus+]
                                    [skeemat/+talvihoitotarkastuksen-kirjaus+ esimerkit/+talvihoitotarkastuksen-kirjaus+]
                                    [skeemat/+paivystajatietojen-kirjaus+ esimerkit/+paivystajatietojen-kirjaus+]
                                    [skeemat/+tyokoneenseuranta-kirjaus+ esimerkit/+tyokoneenseuranta-kirjaus+]]
        skeemapolku-esimerkkidata (mapv
                                    (fn [[skeemapolku esimerkkipolku]]
                                      (let [esimerkkidata (slurp (io/resource esimerkkipolku))]
                                        [skeemapolku esimerkkidata]))
                                    skeemapolku-esimerkkipolku)]
    (doseq [validoitava skeemapolku-esimerkkidata]
      (json-validointi/validoi (first validoitava) (second validoitava)))))