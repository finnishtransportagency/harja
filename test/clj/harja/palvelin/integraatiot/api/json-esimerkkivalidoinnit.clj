(ns harja.palvelin.integraatiot.api.json-esimerkkivalidoinnit
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.urakat :as api-urakat]
            [com.stuartsierra.component :as component]
            [harja.tyokalut.json_validointi :as json-validointi]
            [clojure.string :as str]
            [harja.palvelin.integraatiot.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.esimerkit :as esimerkit]
            [clojure.java.io :as io]
            [cheshire.core :as cheshire]))

(deftest validoi-jsonit
  (let [skeemapolku-esimerkkipolku [[skeemat/+urakan-haku-vastaus+ esimerkit/+urakan-haku-vastaus+]
                                    [skeemat/+urakoiden-haku-vastaus+ esimerkit/+urakoiden-haku-vastaus+]
                                    [skeemat/+havainnon-kirjaus+ esimerkit/+havainnon-kirjaus+]
                                    [skeemat/+pistetoteuman-kirjaus+ esimerkit/+pistetoteuman-kirjaus+]
                                    [skeemat/+reittitoteuman-kirjaus+ esimerkit/+reittitoteuman-kirjaus+]
                                    [skeemat/+tiestotarkastuksen-kirjaus+ esimerkit/+tiestotarkastuksen-kirjaus+]
                                    [skeemat/+soratietarkastuksen-kirjaus+ esimerkit/+soratietarkastuksen-kirjaus+]
                                    [skeemat/+talvihoitotarkastuksen-kirjaus+ esimerkit/+talvihoitotarkastuksen-kirjaus+]
                                    [skeemat/+paivystajatietojen-kirjaus+ esimerkit/+paivystajatietojen-kirjaus+]
                                    [skeemat/+tyokoneenseuranta-kirjaus+ esimerkit/+tyokoneenseuranta-kirjaus+]]
        skeemapolku-esimerkkidata (mapv
                                    (fn [pari]
                                      (let [skeemapolku (first pari)
                                            esimerkkipolku (second pari)
                                            esimerkkidata (slurp (io/resource esimerkkipolku))]
                                        [skeemapolku esimerkkidata]))
                                    skeemapolku-esimerkkipolku)]
    (doseq [validoitava skeemapolku-esimerkkidata]
      (json-validointi/validoi (first validoitava) (second validoitava)))))