(ns harja.palvelin.integraatiot.tierekisteri.tietolajin_haku_test.clj
  (:require [clojure.test :refer [deftest is use-fixtures compose-fixtures]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [harja.testi :refer :all]
            [clojure.java.io :as io]
            [clojure.data :refer [diff]])
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

(deftest tarkista-tietueiden-haku
  (let [vastaus-xml (slurp (io/resource "xsd/tierekisteri/examples/hae-tietueet-response.xml"))]
    (with-fake-http
      [(str +testi-tierekisteri-url+ "/haetietueet") vastaus-xml]
      (let [tierekisteriosoitevali {:numero  1
                                    :aet     1
                                    :aosa    1
                                    :let     1
                                    :losa    1
                                    :ajr     1
                                    :puoli   1
                                    :alkupvm "2015-05-25"}
            vastausdata (tierekisteri/hae-tietueet (:tierekisteri jarjestelma) tierekisteriosoitevali "tl506" nil)]
        (is (true? (:onnistunut vastausdata)))
        (is (= 3 (count (:tietueet vastausdata))))


        (let [tietue (:tietue (first (:tietueet vastausdata)))
              odotettu-tietue {:sijainti    {:koordinaatit {:x 0.0,
                                                            :y 0.0,
                                                            :z 0.0},
                                             :linkki       {:id    1,
                                                            :marvo 10},
                                             :tie          {:numero  1,
                                                            :aet     1,
                                                            :aosa    1,
                                                            :let     1,
                                                            :losa    1,
                                                            :ajr     1,
                                                            :puoli   1,
                                                            :alkupvm #inst "2017-03-02T22:00:00.000-00:00"}},
                               :loppupvm    #inst "2015-03-02T22:00:00.000-00:00",
                               :piiri       "1",
                               :karttapvm   #inst "2015-03-02T22:00:00.000-00:00",
                               :urakka      100,
                               :tietolaji   {:tietolajitunniste "tl506",
                                             :arvot             "9987 2 2 0 1 0 1 1 Testiliikennemerkki Omistaja O K 123456789 40"},
                               :kuntoluokka "1",
                               :alkupvm     #inst "2015-03-02T22:00:00.000-00:00",
                               :tunniste    "1245rgfsd"}]
          (is (= odotettu-tietue tietue)))))))

(deftest tarkista-tietueen-haku
  (let [vastaus-xml (slurp (io/resource "xsd/tierekisteri/examples/hae-tietue-response.xml"))]
    (with-fake-http
      [(str +testi-tierekisteri-url+ "/haetietue") vastaus-xml]
      (let [vastausdata (tierekisteri/hae-tietue (:tierekisteri jarjestelma) "asdf" "tl506")
            odotettu-tietue {:sijainti    {:koordinaatit {:x 0.0,
                                                          :y 0.0,
                                                          :z 0.0},
                                           :linkki       {:id    1,
                                                          :marvo 10},
                                           :tie          {:numero  1,
                                                          :aet     1,
                                                          :aosa    1,
                                                          :let     1,
                                                          :losa    1,
                                                          :ajr     1,
                                                          :puoli   1,
                                                          :alkupvm #inst "2017-03-02T22:00:00.000-00:00"}},
                             :loppupvm    #inst "2015-03-02T22:00:00.000-00:00",
                             :piiri       "1",
                             :karttapvm   #inst "2015-03-02T22:00:00.000-00:00",
                             :urakka      100,
                             :tietolaji   {:tietolajitunniste "tl506",
                                           :arvot             "9987 2 2 0 1 0 1 1 Testiliikennemerkki Omistaja O K 123456789 40"},
                             :kuntoluokka "1",
                             :alkupvm     #inst "2015-03-02T22:00:00.000-00:00",
                             :tunniste    "1245rgfsd"}
            tietue (:tietue vastausdata)]
        (is (true? (:onnistunut vastausdata)))
        (is (= odotettu-tietue tietue))))))

