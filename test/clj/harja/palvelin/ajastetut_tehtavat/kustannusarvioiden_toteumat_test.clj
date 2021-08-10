(ns harja.palvelin.ajastetut-tehtavat.kustannusarvioiden-toteumat-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.palvelin.ajastetut-tehtavat.kustannusarvioiden-toteumat :as kustannusarvioiden-toteumat]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.fim-test :as fim-test]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.pvm :refer [luo-pvm]]
            [clj-time.core :as t]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.palvelut.urakat :as urakat])
  (:use org.httpkit.fake))

(defn jarjestelma-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (alter-var-root
    #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :integraatioloki (component/using (integraatioloki/->Integraatioloki nil) [:db])
          :fim (component/using
                 (fim/->FIM fim-test/+testi-fim+)
                 [:db :integraatioloki])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once jarjestelma-fixture)

(deftest siirra-kustanukset-toimii
  (let [testitietokanta (:db jarjestelma)
        hae-maarat (fn []
                     [(first (first (q "SELECT count(*) FROM kustannusarvioitu_tyo
                                        WHERE \"siirretty?\" ")))
                      (first (first
                               (q "SELECT count(*) FROM johto_ja_hallintokorvaus
                                   WHERE \"siirretty?\" ")))])
        maarat-alussa (hae-maarat)
        _ (u "UPDATE kustannusarvioitu_tyo SET \"siirretty?\" = false;")
        _ (u "UPDATE johto_ja_hallintokorvaus SET \"siirretty?\" = false;")
        _ (kustannusarvioiden-toteumat/siirra-kustannukset testitietokanta (luo-pvm 2021 7 8))
        maarat-lopussa (hae-maarat)]
    (is (= maarat-alussa maarat-lopussa))))

