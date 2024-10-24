(ns harja.domain.kayttaja-test
  (:require [clojure.test :refer [deftest is]]
            [harja.domain.kayttaja :refer [kayttaja-ilman-henkilotietoja]]))

(deftest kayttaja-ilman-henkilotietoja-toimii
    (let [kayttaja {:id 1234
                    :organisaatio {:id 111
                                   :nimi "Oy Urakoitsija Ab"
                                   :tyyppi "urakoitsija"}
                    :etunimi "Matti"
                    :sukunimi "Meikäläinen"
                    :kayttajanimi "matti"
                    :sahkoposti "matti@example.com"
                    :puhelin "040-1234567"
                    :roolit {},
                    :organisaatioroolit {}}
          puhdistettu (kayttaja-ilman-henkilotietoja kayttaja)]
      
      ; Tarkista, että henkilötiedot on poistettu
      (is (nil? (:etunimi puhdistettu)))
      (is (nil? (:sukunimi puhdistettu)))
      (is (nil? (:sahkoposti puhdistettu)))
      (is (nil? (:puhelin puhdistettu)))
      (is (= "matti" (:kayttajanimi puhdistettu)))))
