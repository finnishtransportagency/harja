(ns harja.palvelin.integraatiot.tierekisteri.tierekisterikomponentti-test
  (:require [clojure.test :refer [deftest is use-fixtures compose-fixtures]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [harja.testi :refer :all]
            [clojure.java.io :as io]
            [slingshot.slingshot :refer [try+]]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet])
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
      (let [tierekisteriosoitevali {:numero 1
                                    :aet    1
                                    :aosa   1
                                    :let    1
                                    :losa   1
                                    :ajr    1
                                    :puoli  1}
            vastausdata (tierekisteri/hae-tietueet (:tierekisteri jarjestelma) tierekisteriosoitevali "tl506" "2015-05-25")]
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
                                                            :alkupvm nil}},
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
                                                          :alkupvm nil}},
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


(deftest tarkista-tietueen-lisays
  (let [vastaus-xml (slurp (io/resource "xsd/tierekisteri/examples/ok-vastaus-response.xml"))]
    (with-fake-http
      [(str +testi-tierekisteri-url+ "/lisaatietue") vastaus-xml]
      (let [tietue {:lisaaja {:henkilo      "Keijo Käsittelijä"
                              :jarjestelma  "FastMekka"
                              :organisaatio "Asfaltia Oy"
                              :yTunnus      "1234567-8"}
                    :tietue  {:tunniste    "HARJ951547ZK"
                              :alkupvm     "2015-05-22"
                              :loppupvm    nil
                              :karttapvm   nil
                              :piiri       nil
                              :kuntoluokka nil
                              :urakka      nil
                              :sijainti    {:tie {:numero  "89"
                                                  :aet     "12"
                                                  :aosa    "1"
                                                  :let     nil
                                                  :losa    nil
                                                  :ajr     "0"
                                                  :puoli   "1"
                                                  :alkupvm nil}}
                              :tietolaji   {:tietolajitunniste "tl505"
                                            :arvot             "HARJ951547ZK        2                           HARJ951547ZK          01  "}}
                    :lisatty "2015-05-26+03:00"}
            vastausdata (tierekisteri/lisaa-tietue (:tierekisteri jarjestelma) tietue)]
        (is (true? (:onnistunut vastausdata)))))))


(deftest tarkista-tietueen-muokkaus
  (let [vastaus-xml (slurp (io/resource "xsd/tierekisteri/examples/ok-vastaus-response.xml"))]
    (with-fake-http
      [(str +testi-tierekisteri-url+ "/paivitatietue") vastaus-xml]
      (let [tietue {:paivittaja {:henkilo      "Keijo Käsittelijä"
                                 :jarjestelma  "FastMekka"
                                 :organisaatio "Asfaltia Oy"
                                 :yTunnus      "1234567-8"}
                    :tietue     {:tunniste    "HARJ951547ZK"
                                 :alkupvm     "2015-05-22"
                                 :loppupvm    nil
                                 :karttapvm   nil
                                 :piiri       nil
                                 :kuntoluokka nil
                                 :urakka      nil
                                 :sijainti    {:tie {:numero  "89"
                                                     :aet     "12"
                                                     :aosa    "1"
                                                     :let     nil
                                                     :losa    nil
                                                     :ajr     "0"
                                                     :puoli   "1"
                                                     :alkupvm nil}}
                                 :tietolaji   {:tietolajitunniste "tl505"
                                               :arvot             "HARJ951547ZK        2                           HARJ951547ZK          01  "}}

                    :paivitetty "2015-05-26+03:00"}
            vastausdata (tierekisteri/paivita-tietue (:tierekisteri jarjestelma) tietue)]
        (is (true? (:onnistunut vastausdata)))))))

(deftest tarkista-tietueen-poisto
  (let [vastaus-xml (slurp (io/resource "xsd/tierekisteri/examples/ok-vastaus-response.xml"))]
    (with-fake-http
      [(str +testi-tierekisteri-url+ "/poistatietue") vastaus-xml]
      (let [tiedot {:poistaja          {:henkilo      "Keijo Käsittelijä"
                                        :jarjestelma  "FastMekka"
                                        :organisaatio "Asfaltia Oy"
                                        :yTunnus      "1234567-8"}
                    :tunniste          "HARJ951547ZK"
                    :tietolajitunniste "tl505"
                    :poistettu         "2015-05-26+03:00"}
            vastausdata (tierekisteri/poista-tietue (:tierekisteri jarjestelma) tiedot)]
        (is (true? (:onnistunut vastausdata)))))))

(deftest tarkista-virhevastauksen-kasittely
  (let [vastaus-xml (slurp (io/resource "xsd/tierekisteri/examples/virhe-vastaus-tietolajia-ei-loydy-response.xml"))]
    (with-fake-http
      [(str +testi-tierekisteri-url+ "/haetietolajit") vastaus-xml]
      (try+
        (tierekisteri/hae-tietolajit (:tierekisteri jarjestelma) "tl506" nil)
        (is false "Pitäisi tapahtua poikkeus")
        (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
          (let [virhe (first virheet)]
            (is (= :poikkeus (:koodi virhe)))))))))

