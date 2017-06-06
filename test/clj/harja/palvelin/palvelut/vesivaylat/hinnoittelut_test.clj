(ns harja.palvelin.palvelut.vesivaylat.toimenpiteet.hinnoittelut-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.domain.toteuma :as tot]
            [harja.domain.vesivaylat.toimenpide :as toi]
            [harja.domain.urakka :as u]
            [harja.palvelin.palvelut.vesivaylat.toimenpiteet.apurit :as apurit]
            [harja.palvelin.palvelut.yllapito-toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [harja.palvelin.palvelut.vesivaylat.toimenpiteet.yksikkohintaiset :as yks]
            [harja.kyselyt.vesivaylat.hinnoittelut :as q]
            [clojure.spec.alpha :as s]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :vv-yksikkohintaiset (component/using
                                               (yks/->YksikkohintaisetToimenpiteet)
                                               [:db :http-palvelin])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))