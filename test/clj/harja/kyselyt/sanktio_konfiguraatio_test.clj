(ns harja.kyselyt.sanktio-konfiguraatio-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [harja.kyselyt.sanktio-konfiguraatio :as sanktio-konfiguraatio]))

(def ^:private sanktio-profiili-rivi-summamaaritys-migraatio
  (slurp "tietokanta/src/main/resources/db/migration/V1_1261__.sql"))

(deftest v1-1261-vaatii-vapaalle-ohjetekstille-tekstin-ilman-euromaaraa
  (is (str/includes? sanktio-profiili-rivi-summamaaritys-migraatio
        "(maaritystapa = 'vapaa_ohjeteksti' AND summa_euroina IS NULL AND ohjeteksti IS NOT NULL)")
    "Migraation pitää estää vapaan ohjetekstin rivit, joilla on samanaikaisesti euromäärä."))

(deftest muunna-sanktio-konfiguraatiorivi-sailyttaa-yhteensopivan-lukittujen-summien-vektorin
  (let [rivi (sanktio-konfiguraatio/muunna-sanktio-konfiguraatiorivi
               {:profiili_urakkatyyppi "teiden-hoito"
                :soveltuvuuskonteksti "laatupoikkeama"
                :laji_koodi "A"
                :profiilirivi_summamaaritykset
  "[{\"maaritystapa\":\"kiintea_euromaara\",\"summa_euroina\":6000.00,\"ohjeteksti\":\"alkavalta viikolta\",\"jarjestys\":1},{\"maaritystapa\":\"vapaa_ohjeteksti\",\"summa_euroina\":null,\"ohjeteksti\":\"tai sopimuksen mukaan\",\"jarjestys\":2}]"})]
    (is (= [{:maaritystapa :kiintea-euromaara
             :summa-euroina 6000M
             :ohjeteksti "alkavalta viikolta"
             :jarjestys 1}
            {:maaritystapa :vapaa-ohjeteksti
       :summa-euroina nil
             :ohjeteksti "tai sopimuksen mukaan"
             :jarjestys 2}]
          (get-in rivi [:profiilirivi :summamaaritykset])))
    (is (= [6000M]
          (get-in rivi [:profiilirivi :lukitut-summat])))))
