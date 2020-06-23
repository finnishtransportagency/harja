(ns harja.palvelin.palvelut.maarien_toteumat_test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [taoensso.timbre :as log]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.palvelin.palvelut.toteumat :as toteumat]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [harja.domain.tierekisteri.varusteet :as varusteet-domain]))

(def +testi-tierekisteri-url+ "harja.testi.tierekisteri")
(def +oikea-testi-tierekisteri-url+ "https://harja-test.solitaservices.fi/harja/integraatiotesti/tierekisteri")

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (let [tietokanta (tietokanta/luo-tietokanta testitietokanta)]
                      (component/start
                        (component/system-map
                          :db tietokanta
                          :db-replica tietokanta
                          :http-palvelin (testi-http-palvelin)
                          :karttakuvat (component/using
                                         (karttakuvat/luo-karttakuvat)
                                         [:http-palvelin :db])
                          :integraatioloki (component/using
                                             (integraatioloki/->Integraatioloki nil)
                                             [:db])
                          :tierekisteri (component/using
                                          (tierekisteri/->Tierekisteri +testi-tierekisteri-url+ nil)
                                          [:db :integraatioloki])
                          :toteumat (component/using
                                      (toteumat/->Toteumat)
                                      [:http-palvelin :db :db-replica :karttakuvat :tierekisteri]))))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

;; Hae määrien totteumat
(deftest maarien-toteumat-ilman-rajoituksia
  (let [maarien-toteumat (kutsu-palvelua (:http-palvelin jarjestelma)
                            :urakan-maarien-toteumat +kayttaja-jvh+
                            {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id})
        _ (println "maarien-toteumat-ilman-rajoituksia :: maarien-toteumat" (pr-str maarien-toteumat ))
        oulun-mhu-urakan-maarien-toteuma-lkm (ffirst (q
                                              (str "SELECT count(*)
                                                       FROM urakka_tehtavamaara
                                                      WHERE urakka = " @oulun-maanteiden-hoitourakan-2019-2024-id)))]
    (is (= (count maarien-toteumat) oulun-mhu-urakan-maarien-toteuma-lkm) "Määrien toteumien määrä")))

