(ns harja.palvelin.raportointi.tiemerkinnan-kustannusyhteenveto-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.palvelin.raportointi.raportit.tiemerkinnan-kustannusyhteenveto :as raportti]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.raportointi.testiapurit :as apurit]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :pdf-vienti (component/using
                                      (pdf-vienti/luo-pdf-vienti)
                                      [:http-palvelin])
                        :raportointi (component/using
                                       (raportointi/luo-raportointi)
                                       [:db :pdf-vienti])
                        :raportit (component/using
                                    (raportit/->Raportit)
                                    [:http-palvelin :db :raportointi :pdf-vienti])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest raportin-suoritus-urakalle-toimii
  (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
        sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
        tiemerkinnan-tpi (:id (first (q-map "SELECT id FROM toimenpideinstanssi WHERE urakka = " urakka-id " LIMIT 1")))]

    ;; Tyhjennetään testattavan urakan kaikki raportilla näkyvät tiedot ja luodaan tyhjästä omat.
    ;; Nähdään, että raportissa käytetty data on laskettu oikein
    (u (str "DELETE FROM kokonaishintainen_tyo WHERE toimenpideinstanssi IN
  (SELECT id FROM toimenpideinstanssi WHERE urakka = " urakka-id ");"))

    (u (str "INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus)
    VALUES (2017, 10, 3500, '2017-10-15', " tiemerkinnan-tpi ", " sopimus-id ");"))
    (u (str "INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus)
    VALUES (2017, 11, 100, '2017-10-15', " tiemerkinnan-tpi ", " sopimus-id ");"))
    (u (str "INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus)
    VALUES (2007, 1, 666, '2017-10-15', " tiemerkinnan-tpi ", " sopimus-id ");")) ; Ei aikavälillä

    (let [{:keys [kokonaishintaiset-tyot yksikkohintaiset-toteumat
                  muut-tyot sakot bonukset toteumat-yhteensa kk-vali?]
           :as raportin-tiedot}
          (raportti/hae-raportin-tiedot {:db (:db jarjestelma)
                                         :urakka-id urakka-id
                                         :alkupvm (pvm/luo-pvm 2010 1 1)
                                         :loppupvm (pvm/luo-pvm 2080 1 1)})]
      (is (map? raportin-tiedot))
      (is (== kokonaishintaiset-tyot 3600)))))
