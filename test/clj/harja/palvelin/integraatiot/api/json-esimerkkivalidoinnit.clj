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
  (let [skeemapolku-esimerkkipolku [[skeemat/+pistetoteuman-kirjaus+ esimerkit/+pistetoteuman-kirjaus+]]
        skeemapolku-esimerkkidata (mapv
                                    (fn [pari]
                                      (let [skeemapolku (first pari)
                                            esimerkkipolku (second pari)
                                            esimerkkidata (slurp (io/resource esimerkkipolku))]
                                        [skeemapolku esimerkkidata]))
                                    skeemapolku-esimerkkipolku)]
    (doseq [validoitava skeemapolku-esimerkkidata]
      (json-validointi/validoi (first validoitava) (second validoitava)))))