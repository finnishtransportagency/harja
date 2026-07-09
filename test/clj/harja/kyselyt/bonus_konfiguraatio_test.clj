(ns harja.kyselyt.bonus-konfiguraatio-test
  (:require [clojure.test :refer :all]
            [harja.kyselyt.bonus-konfiguraatio :as bonus-konfiguraatio]))

(defn- admin-raakarivi
  "Edustava admin-kyselyn (hae-bonus-profiilin-rivit-admin) palauttama raakarivi,
  jossa sarakkeet ovat litteit\u00e4 alaviiva-avaimia."
  []
  {:profiili_id 7
   :profiili_nimi "MHU26 bonusprofiili"
   :profiili_urakkatyyppi "teiden-hoito"
   :profiili_hoitovuosi_alku 1
   :profiili_hoitovuosi_loppu 20
   :profiili_alkupvm #inst "2026-10-01T00:00:00.000-00:00"
   :profiili_loppupvm nil
   :profiili_aktiivinen true
   :laji_id 42
   :laji_koodi "asiakastyytyvaisyysbonus"
   :laji_nimi "Asiakastyytyv\u00e4isyysbonus"
   :laji_esitystiedot_nimi "Bonus poikkeavalla nimell\u00e4"
   :laji_esitystiedot_kuvaus "Kuvaus"
   :laji_jarjestys 1
   :laji_kirjaustapa "automaattinen"
   :laji_automaattinen true
   :profiilirivi_id 99
   :profiilirivi_jarjestys 1
   :profiilirivi_toimenpiderajauksen_tyyppi "t2-koodi"
   :profiilirivi_toimenpide_t2_koodi "23150"
   :profiilirivi_urakkarajausten_maara 2
   :profiilirivi_urakat ["Iin MHU" "Raahen MHU"]})

(defn- ei-admin-raakarivi
  "Edustava ei-admin-kyselyn (hae-bonus-profiilin-rivit) palauttama raakarivi.
  Ei sis\u00e4ll\u00e4 urakkarajaus- eik\u00e4 urakat-sarakkeita."
  []
  {:profiili_id 7
   :profiili_nimi "MHU26 bonusprofiili"
   :profiili_urakkatyyppi "teiden-hoito"
   :profiili_hoitovuosi_alku 1
   :profiili_hoitovuosi_loppu 20
   :profiili_alkupvm #inst "2026-10-01T00:00:00.000-00:00"
   :profiili_loppupvm nil
   :profiili_aktiivinen true
   :laji_id 42
   :laji_koodi "asiakastyytyvaisyysbonus"
   :laji_nimi "Asiakastyytyv\u00e4isyysbonus"
   :laji_esitystiedot_nimi nil
   :laji_esitystiedot_kuvaus nil
   :laji_jarjestys 1
   :laji_kirjaustapa "automaattinen"
   :laji_automaattinen false
   :profiilirivi_id 99
   :profiilirivi_jarjestys 1
   :profiilirivi_toimenpiderajauksen_tyyppi "kaikki"
   :profiilirivi_toimenpide_t2_koodi nil})

(deftest muunna-bonus-konfiguraatiorivi-admin-rivi
  (let [tulos (bonus-konfiguraatio/muunna-bonus-konfiguraatiorivi (admin-raakarivi))]
    (testing "profiili muodostuu sis\u00e4kk\u00e4iseksi rakenteeksi ja urakkatyyppi keywordiksi"
      (is (= {:id 7
              :nimi "MHU26 bonusprofiili"
              :urakkatyyppi :teiden-hoito
              :hoitovuosi {:alku 1 :loppu 20}
              :alkupvm #inst "2026-10-01T00:00:00.000-00:00"
              :loppupvm nil
              :aktiivinen true}
            (:profiili tulos))))
    (testing "laji muodostuu sis\u00e4kk\u00e4iseksi rakenteeksi ja koodi keywordiksi"
      (is (= {:id 42
              :koodi :asiakastyytyvaisyysbonus
              :nimi "Asiakastyytyv\u00e4isyysbonus"
              :esitystiedot {:nimi "Bonus poikkeavalla nimell\u00e4" :kuvaus "Kuvaus"}
              :jarjestys 1
              :kirjaustapa "automaattinen"
              :automaattinen true}
            (:laji tulos))))
    (testing "profiilirivi litistyy ja toimenpiderajauksen-tyyppi muuttuu keywordiksi"
      (is (= {:id 99
              :jarjestys 1
              :toimenpiderajauksen-tyyppi :t2-koodi
              :toimenpide-t2-koodi "23150"
              :urakkarajausten-maara 2
              :urakat ["Iin MHU" "Raahen MHU"]}
            (:profiilirivi tulos))))))

(deftest muunna-bonus-konfiguraatiorivi-ei-admin-rivi
  (let [tulos (bonus-konfiguraatio/muunna-bonus-konfiguraatiorivi (ei-admin-raakarivi))]
    (testing "ei-admin-rivilt\u00e4 puuttuvat urakkarajaus- ja urakat-tiedot, toimenpide-t2-koodi on nil"
      (is (= {:id 99
              :jarjestys 1
              :toimenpiderajauksen-tyyppi :kaikki
              :toimenpide-t2-koodi nil}
            (:profiilirivi tulos))))
    (testing "laji-esitystiedot s\u00e4ilyv\u00e4t nil-arvoisina"
      (is (= {:nimi nil :kuvaus nil}
            (get-in tulos [:laji :esitystiedot]))))))
