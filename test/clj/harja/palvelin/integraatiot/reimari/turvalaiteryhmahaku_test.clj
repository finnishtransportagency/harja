(ns harja.palvelin.integraatiot.reimari.turvalaiteryhmahaku-test
  (:require [harja.palvelin.integraatiot.reimari.turvalaiteryhmahaku :as tlrhaku]
            [harja.palvelin.integraatiot.reimari.reimari-komponentti :as reimari]
            [harja.domain.vesivaylat.turvalaiteryhma :as turvalaiteryhma]
            [com.stuartsierra.component :as component]
            [specql.core :as specql]
            [harja.testi :as ht]
            [clojure.test :as t]
            [namespacefy.core :refer [unnamespacefy]]))

(def jarjestelma-fixture
  (ht/laajenna-integraatiojarjestelmafixturea
    "yit"
    :reimari (component/using
               (reimari/->Reimari "https://www.example.com/reimari/" "reimarikayttaja" "reimarisalasana")
               [:db :integraatioloki])))

(t/use-fixtures :each (t/compose-fixtures ht/tietokanta-fixture jarjestelma-fixture))


;; Tietueet xml-sanomasta turvalaiteryhmat_vastaus.xml
(def referenssi-turvalaiteryhma-tietue
  {:harja.domain.vesivaylat.turvalaiteryhma/tunnus 1234,
   :harja.domain.vesivaylat.turvalaiteryhma/nimi "Merireimari"
   :harja.domain.vesivaylat.turvalaiteryhma/kuvaus "1234: Merireimari"
   :harja.domain.vesivaylat.turvalaiteryhma/turvalaitteet
   [5678 5679 5670 5671]})

(def toinen-turvalaiteryhma-tietue
  {:harja.domain.vesivaylat.turvalaiteryhma/tunnus 1235,
   :harja.domain.vesivaylat.turvalaiteryhma/nimi "Järvireimari"
   :harja.domain.vesivaylat.turvalaiteryhma/kuvaus "1235: Järvireimari"
   :harja.domain.vesivaylat.turvalaiteryhma/turvalaitteet
   [4678 4679 4670 4671]})

;; Tietueet turvalaiteryhmän CRUD-toimintojen testaamiseen
(def testi-turvalaiteryhma-tietue
  {:harja.domain.vesivaylat.turvalaiteryhma/tunnus 1236,
   :harja.domain.vesivaylat.turvalaiteryhma/nimi "Testireimari"
   :harja.domain.vesivaylat.turvalaiteryhma/kuvaus "1236: Testireimari"
   :harja.domain.vesivaylat.turvalaiteryhma/turvalaitteet
   [3678 3679 3670 3671]})

(def testi-turvalaiteryhma-tietue-muutettu
  {:harja.domain.vesivaylat.turvalaiteryhma/tunnus 1236,
   :harja.domain.vesivaylat.turvalaiteryhma/nimi "Muuttoreimari - oli testi"
   :harja.domain.vesivaylat.turvalaiteryhma/kuvaus "1236: Muuttoreimari"
   :harja.domain.vesivaylat.turvalaiteryhma/turvalaitteet
   [3678 3679 3670 3671]})


(t/deftest kasittele-vastaus-kantatallennus
  "Testaa funktion kasittele-vastaus-kantatallennus"
  (let [db (:db ht/jarjestelma)
        tarkista-fn #(ht/tarkista-map-arvot
                       (unnamespacefy referenssi-turvalaiteryhma-tietue)
                       (first (tlrhaku/kasittele-turvalaiteryhma-vastaus db (slurp "resources/xsd/reimari/haeturvalaiteryhmat-vastaus.xml"))))]

    (t/testing "Tarkista map"
      (tarkista-fn))

    (t/testing "Kahden testissä tallennetun turvalaiteryhmän haku tunnuksilla toimii."
      (t/is (= 2
               (count (specql/fetch db ::turvalaiteryhma/reimari-turvalaiteryhma
                                    #{::turvalaiteryhma/nimi}
                                    {::turvalaiteryhma/tunnus (specql.op/in #{(::turvalaiteryhma/tunnus referenssi-turvalaiteryhma-tietue) (::turvalaiteryhma/tunnus toinen-turvalaiteryhma-tietue)})})))))

    (t/testing "Testissä tallennetun turvalaiteryhmän haku palauttaa oikeaa dataa."
      (t/is (= referenssi-turvalaiteryhma-tietue
               (first (specql/fetch db ::turvalaiteryhma/reimari-turvalaiteryhma
                                    #{::turvalaiteryhma/tunnus ::turvalaiteryhma/nimi ::turvalaiteryhma/kuvaus ::turvalaiteryhma/turvalaitteet}
                                    {::turvalaiteryhma/tunnus (::turvalaiteryhma/tunnus referenssi-turvalaiteryhma-tietue)})))))
    (tarkista-fn)))


(t/deftest turvalaiteryhma-crud-operaatiot
  "Testaa turvalaiteryhman CRUD-toiminnot"

  (t/testing "Turvalaiteryhmän insert toimii."
    (t/is (= testi-turvalaiteryhma-tietue
             (specql/insert! (:db ht/jarjestelma) ::turvalaiteryhma/reimari-turvalaiteryhma
                             testi-turvalaiteryhma-tietue))))

  (t/testing "Turvalaiteryhmän nimen päivitys (update) ryhmän tunnuksella toimii."
    (t/is (= 1
             (specql/update! (:db ht/jarjestelma) ::turvalaiteryhma/reimari-turvalaiteryhma
                             {::turvalaiteryhma/nimi "Päivitetty nimi"}
                             {::turvalaiteryhma/tunnus (::turvalaiteryhma/tunnus testi-turvalaiteryhma-tietue)}))))

  (t/testing "Turvalaiteryhmän delete toimii."
    (t/is (= 1
             (specql/delete! (:db ht/jarjestelma) ::turvalaiteryhma/reimari-turvalaiteryhma
                             {::turvalaiteryhma/tunnus (::turvalaiteryhma/tunnus testi-turvalaiteryhma-tietue)}))))

  (t/testing "Turvalaiteryhmän upsert toimii, kun riviä ei ole kannassa."
    (t/is (= testi-turvalaiteryhma-tietue
             (specql/upsert! (:db ht/jarjestelma) ::turvalaiteryhma/reimari-turvalaiteryhma
                             testi-turvalaiteryhma-tietue
                             {::turvalaiteryhma/tunnus (::turvalaiteryhma/tunnus testi-turvalaiteryhma-tietue)}))))

  (t/testing "Turvalaiteryhmän upsert toimii, kun rivi löytyy kannasta."
    (t/is (= testi-turvalaiteryhma-tietue-muutettu
             (specql/upsert! (:db ht/jarjestelma) ::turvalaiteryhma/reimari-turvalaiteryhma
                             #{::turvalaiteryhma/tunnus}
                             testi-turvalaiteryhma-tietue-muutettu)))))




