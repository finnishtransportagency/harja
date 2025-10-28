(ns harja.palvelin.ajastetut-tehtavat.kustannusarvioiden-toteumat-siirto-test
  (:require [clojure.test :refer :all]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.palvelin.ajastetut-tehtavat.kustannusarvioiden-toteumat :as kustannusarvioiden-toteumat]
            [harja.kyselyt.toteutuneet-kustannukset :as toteutuneet-kustannukset-kyselyt]
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
          :db (tietokanta/luo-tietokanta testitietokanta)))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once jarjestelma-fixture)

(deftest siirra-kustanukset-toimii-idempotentisti
  (let [testitietokanta (:db jarjestelma)
        toimiva-vuosi (inc (pvm/vuosi (pvm/nyt)))
        pvm (luo-pvm toimiva-vuosi 3 8)
        merkkaa-kaikki-siirtamattomaksi (fn []
                                          (u "UPDATE kustannusarvioitu_tyo SET \"siirretty?\" = false;")
                                          (u "UPDATE johto_ja_hallintokorvaus SET \"siirretty?\" = false;"))
        siirtamattomat-aluksi (toteutuneet-kustannukset-kyselyt/hae-siirtamattomat-kustannukset testitietokanta {:pvm pvm})
        _ (merkkaa-kaikki-siirtamattomaksi)
        siirtamattomat (toteutuneet-kustannukset-kyselyt/hae-siirtamattomat-kustannukset testitietokanta {:pvm pvm})
        _ (kustannusarvioiden-toteumat/siirra-kustannukset testitietokanta pvm)
        siirtamattomat-siirron-jalkeen (toteutuneet-kustannukset-kyselyt/hae-siirtamattomat-kustannukset testitietokanta {:pvm pvm})
        uusi-pvm (luo-pvm toimiva-vuosi 6 15)
        _ (kustannusarvioiden-toteumat/siirra-kustannukset testitietokanta uusi-pvm)
        siirtamattomat-myohemmin (toteutuneet-kustannukset-kyselyt/hae-siirtamattomat-kustannukset testitietokanta {:pvm (luo-pvm toimiva-vuosi 12 15)})
        _ (kustannusarvioiden-toteumat/siirra-kustannukset testitietokanta pvm)]

    (is (not= siirtamattomat siirtamattomat-aluksi))  ;; Siirron jälkeen siirtämättömien määrä muuttui
    (is (< 0M siirtamattomat))  ;; Siirretty? = false merkinnän jälkeen siirtämättömiä löytyy
    ;; Varmistetaan, että kaikkia ei ole siirretty, vaan että päivämäärä valinta toimii
    (is (< siirtamattomat-siirron-jalkeen siirtamattomat-myohemmin))))

