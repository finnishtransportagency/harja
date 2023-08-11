(ns harja.palvelin.integraatiot.tierekisteri.tierekisterikomponentti-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [harja.testi :refer :all]
            [clojure.java.io :as io]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.palvelin.integraatiot.tierekisteri.tietolajit :as tietolajit]
            [clj-time.core :as t])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(def +testi-tierekisteri-url+ "harja.testi.tierekisteri")

(def kayttaja "destia")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :tierekisteri (component/using (tierekisteri/->Tierekisteri +testi-tierekisteri-url+ nil) [:db :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(deftest tarkista-tietolajin-haku
  (let [vastaus-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/hae-tietolaji-response.xml"))]
    (with-fake-http
      [(str +testi-tierekisteri-url+ "/haetietolaji") vastaus-xml]
      (let [vastausdata (tierekisteri/hae-tietolaji (:tierekisteri jarjestelma) "tl506" nil)]
        (is (true? (:onnistunut vastausdata)))
        (is (= "tl506" (get-in vastausdata [:tietolaji :tunniste])))
        (is (= 15 (count (get-in vastausdata [:tietolaji :ominaisuudet]))))
        (is (every? :jarjestysnumero (get-in vastausdata [:tietolaji :ominaisuudet])))
        (let [ominaisuus (first (get-in vastausdata [:tietolaji :ominaisuudet]))
              odotettu-ominaisuus {:alaraja nil
                                   :desimaalit nil
                                   :jarjestysnumero 1
                                   :kenttatunniste "tunniste"
                                   :koodisto nil
                                   :muutospvm nil
                                   :pakollinen false
                                   :pituus 20
                                   :selite "Tunniste"
                                   :tietotyyppi :merkkijono
                                   :voimassaolo {:alkupvm #inst "2008-09-01T21:00:00.000-00:00"
                                                 :loppupvm nil}
                                   :ylaraja nil}]
          (is (= odotettu-ominaisuus ominaisuus)))))))

(deftest tarkista-tietolajin-haku-cachesta
  (tietolajit/tyhjenna-tietolajien-kuvaukset-cache)
  (let [vastaus-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/hae-tietolaji-response.xml"))]
    ;; Cache on tyhjä, joten vastaus haetaan tierekisteristä HTTP-kutsulla
    (with-fake-http
      [(str +testi-tierekisteri-url+ "/haetietolaji") vastaus-xml]
      (let [vastausdata (tierekisteri/hae-tietolaji (:tierekisteri jarjestelma) "tl506" nil)]
        (is (true? (:onnistunut vastausdata)))))

    ;; Tehdään kysely uudestaan, vastauksen täytyy palautua cachesta eli HTTP-requestia ei lähde
    (with-fake-http
      []
      (let [vastausdata (tierekisteri/hae-tietolaji (:tierekisteri jarjestelma) "tl506" nil)]
        (is (true? (:onnistunut vastausdata)))
        (is (= "tl506" (get-in vastausdata [:tietolaji :tunniste]))))

      ;; Haetaan eri parametreilla, joten vastaus ei saa tulla cachesta (tulee virhe, koska http-kutsut on estetty)
      (is (thrown? Exception (tierekisteri/hae-tietolaji (:tierekisteri jarjestelma) "tl506" (t/now)))))))
