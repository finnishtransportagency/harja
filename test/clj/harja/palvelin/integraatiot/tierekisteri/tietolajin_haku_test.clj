(ns harja.palvelin.integraatiot.tierekisteri.tietolajin_haku_test.clj
  (:require [clojure.test :refer [deftest is use-fixtures compose-fixtures]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [harja.testi :refer :all]
            [clojure.java.io :as io])
  (:use org.httpkit.fake))

(def +testi-tierekisteri-url+ "harja.testi.tierekisteri")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    nil
    :tierekisteri (component/using (tierekisteri/->Tierekisteri +testi-tierekisteri-url+) [:db :integraatioloki])))

(use-fixtures :once jarjestelma-fixture)

(deftest tarkista-tietolajin-haku
  (let [vastaus-xml (slurp (io/resource "xsd/tierekisteri/examples/hae-tietolaji-response.xml"))]
    (with-fake-http
      [(str +testi-tierekisteri-url+ "/haetietolajit") vastaus-xml]
      (let [vastausdata (tierekisteri/hae-tietolajit (:tierekisteri jarjestelma) "tl506" nil)]
        (is (true? (:onnistunut vastausdata)))
        (is "tl506" (get-in vastausdata [:tietolaji :tunniste]))
        (is 14 (count (get-in vastausdata [:tietolaji :ominaisuudet])))
        (let [ominaisuus (first (get-in vastausdata [:tietolaji :ominaisuudet]))
              odotettu-ominaisuus {:kenttatunniste  "LMNUMERO",
                                   :selite          "Liikennemerkin tieliikenneasetuksen mukainen numero on pakollinen tieto.",
                                   :jarjestysnumero 1,
                                   :koodisto        nil,
                                   :desimaalit      nil,
                                   :voimassaolo     {:alkupvm #inst "2015-05-25T21:00:00.000-00:00", :loppupvm nil},
                                   :alaraja         nil,
                                   :pakollinen      true,
                                   :tietotyyppi     :merkkijono,
                                   :pituus          12,
                                   :ylaraja         nil}]
          (is (= odotettu-ominaisuus ominaisuus)))))))

