(ns harja.kyselyt.sanktio-konfiguraatio-test
  (:require [clojure.test :refer [deftest is]]
            [harja.kyselyt.sanktio-konfiguraatio :as sanktio-konfiguraatio]))


(deftest muunna-sanktio-konfiguraatiorivi-sailyttaa-yhteensopivan-lukittujen-summien-vektorin
  (let [rivi (sanktio-konfiguraatio/muunna-sanktio-konfiguraatiorivi
               {:profiili_urakkatyyppi "teiden-hoito"
                :soveltuvuuskonteksti "laatupoikkeama"
                :laji_koodi "A"
                :profiilirivi_summamaaritykset
                "[{\"maaritystapa\":\"automaattinen\",\"summa_euroina\":6000.00,\"ohjeteksti\":\"alkavalta viikolta\",\"jarjestys\":1},{\"maaritystapa\":\"manuaalinen\",\"summa_euroina\":null,\"ohjeteksti\":\"tai sopimuksen mukaan\",\"jarjestys\":2}]"})]
    (is (= [{:maaritystapa :automaattinen
             :summa-euroina 6000M
             :ohjeteksti "alkavalta viikolta"
             :jarjestys 1}
            {:maaritystapa :manuaalinen
             :summa-euroina nil
             :ohjeteksti "tai sopimuksen mukaan"
             :jarjestys 2}]
           (get-in rivi [:profiilirivi :summamaaritykset])))
    (is (= [6000M]
           (get-in rivi [:profiilirivi :lukitut-summat])))))
