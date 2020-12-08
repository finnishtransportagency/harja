(ns harja.palvelin.integraatiot.reimari.kyselyt.hae-toimepiteet-test
  (:require  [clojure.test :refer [deftest use-fixtures compose-fixtures is]]
             [harja.testi :as ht]
             [harja.palvelin.komponentit.tietokanta :as tietokanta]
             [com.stuartsierra.component :as component]
             [harja.kyselyt.vesivaylat.toimenpiteet :as q]
             [harja.domain.vesivaylat.toimenpide :as toimenpide]))

(defn jarjestelma-fixture [testit]
  (ht/pystyta-harja-tarkkailija!)
  (alter-var-root #'ht/jarjestelma
                  (fn [_]
                    (component/start
                     (component/system-map
                      :db (tietokanta/luo-tietokanta ht/testitietokanta)))))
  (testit)
  (alter-var-root #'ht/jarjestelma component/stop)
  (ht/lopeta-harja-tarkkailija!))


(use-fixtures :once (compose-fixtures
                     jarjestelma-fixture
                     ht/urakkatieto-fixture))
