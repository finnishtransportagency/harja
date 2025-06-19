(ns harja.palvelin.palvelut.lampotilat-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.lampotilat :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [org.httpkit.fake :refer [with-fake-http]]))

(def +ilmatieteenlaitos-url+ "http://localhost:1234")

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :integraatioloki (component/using
                             (integraatioloki/->Integraatioloki nil)
                             [:db])
          :lampotilat (component/using
                        (->Lampotilat (str +ilmatieteenlaitos-url+ "/tieindeksi2") "")
                        [:http-palvelin :db :integraatioloki])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      tietokanta-fixture
                      (compose-fixtures jarjestelma-fixture urakkatieto-fixture)))


(def +urakoiden-lampotilat-1971-2000+ (slurp "test/resurssit/ilmatieteenlaitos/urakoiden-lampotilat-1971-2000.xml"))
(def +urakoiden-lampotilat-1981-2010+ (slurp "test/resurssit/ilmatieteenlaitos/urakoiden-lampotilat-1981-2010.xml"))
(def +urakoiden-lampotilat-1991-2020+ (slurp "test/resurssit/ilmatieteenlaitos/urakoiden-lampotilat-1991-2020.xml"))


(deftest hae-teiden-hoitourakoiden-lampotilat-ilmatieteenlaitokselta
  (let [odotettu-vastaus {42 {:nimi "Raahen MHU 2023-2028" :kohde "Raahe_Ylivieska" :alueurakkanro "1649" :id 42
                              :keskilampotila-1971-2000 -6.1 :keskilampotila-1991-2020 -6.1 :keskilampotila-1981-2010 -6.1
                              :ilmastollinen-ylaraja -1.2 :keskilampotilan-ilm-ka-erotus -2.5 :hanke 16 :ilmastollinen-alaraja -12.1
                              :keskilampotila -8.6 :pitkakeskilampotila -6.1}}]
    (testing "teiden hoitourakoiden lämpötilat ilmatieteenlaitokselta (alueurakkanumero ilman nollaa)"
      (let [urakka (first (q-map (str "SELECT id, urakkanro, alkupvm, loppupvm, hanke
                                       FROM urakka
                                      WHERE nimi = 'Raahen MHU 2023-2028'")))
            alueurakkanumero (str (:urakkanro urakka))]
        (with-fake-http [{:url (str +ilmatieteenlaitos-url+ "/tieindeksi") :method :post :query-params {:season "2024-2025", :climatology nil, :newversion 1}}
                         {:body (str/replace +urakoiden-lampotilat-1971-2000+ "<alueurakkanumero>" alueurakkanumero)
                          :headers {:content-type "text/xml"}}

                         {:url (str +ilmatieteenlaitos-url+ "/tieindeksi2") :method :post :query-params {:season "2024-2025", :climatology "1981-2010", :newversion 1}}
                         {:body (str/replace +urakoiden-lampotilat-1981-2010+ "<alueurakkanumero>" alueurakkanumero)
                          :headers {:content-type "text/xml"}}

                         {:url (str +ilmatieteenlaitos-url+ "/tieindeksi2") :method :post :query-params {:season "2024-2025", :climatology "1991-2020", :newversion 1}}
                         {:body (str/replace +urakoiden-lampotilat-1991-2020+ "<alueurakkanumero>" alueurakkanumero)
                          :headers {:content-type "text/xml"}}]
          (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                          :hae-lampotilat-ilmatieteenlaitokselta +kayttaja-jvh+
                          {:vuosi 2024})]
            (is (= vastaus odotettu-vastaus))))))

    (testing "teiden hoitourakoiden lämpötilat ilmatieteenlaitokselta (alueurakkanumero nollalla)"
      (let [urakka (first (q-map (str "SELECT id, urakkanro, alkupvm, loppupvm, hanke
                                       FROM urakka
                                      WHERE nimi = 'Raahen MHU 2023-2028'")))
            ;; Alueurakkanumero alkunollilla. Nollat tulisi poistaa ja käsitellä alueurakkanumero kuten ilman nollia.
            alueurakkanumero (str "000" (:urakkanro urakka))]
        (with-fake-http [{:url (str +ilmatieteenlaitos-url+ "/tieindeksi") :method :post :query-params {:season "2024-2025", :climatology nil, :newversion 1}}
                         {:body (str/replace +urakoiden-lampotilat-1971-2000+ "<alueurakkanumero>" alueurakkanumero)
                          :headers {:content-type "text/xml"}}

                         {:url (str +ilmatieteenlaitos-url+ "/tieindeksi2") :method :post :query-params {:season "2024-2025", :climatology "1981-2010", :newversion 1}}
                         {:body (str/replace +urakoiden-lampotilat-1981-2010+ "<alueurakkanumero>" alueurakkanumero)
                          :headers {:content-type "text/xml"}}

                         {:url (str +ilmatieteenlaitos-url+ "/tieindeksi2") :method :post :query-params {:season "2024-2025", :climatology "1991-2020", :newversion 1}}
                         {:body (str/replace +urakoiden-lampotilat-1991-2020+ "<alueurakkanumero>" alueurakkanumero)
                          :headers {:content-type "text/xml"}}]
          (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                          :hae-lampotilat-ilmatieteenlaitokselta +kayttaja-jvh+
                          {:vuosi 2024})]
            (is (= vastaus odotettu-vastaus))))))))

(deftest hae-teiden-hoitourakoiden-lampotilat-test
  (testing "teiden hoitourakoiden lämpötilojen haku"
    (let [hoitokauden-alkupvm (pvm/->pvm "1.10.2011")
          hoitokauden-loppupvm (pvm/->pvm "30.9.2012")
          hoitokausi [hoitokauden-alkupvm hoitokauden-loppupvm]
          lampotilat (vals (kutsu-palvelua (:http-palvelin jarjestelma)
                             :hae-teiden-hoitourakoiden-lampotilat
                             +kayttaja-jvh+
                             {:hoitokausi hoitokausi}))
          lampotila-pudussa (first (filter #(= (:nimi %) "Pudasjärven alueurakka 2007-2012") lampotilat))]
      (is (some? (:lampotilaid lampotila-pudussa)) ":lampotilaid")
      (is (some? (:urakka lampotila-pudussa)) "urakka")
      (is (= (:keskilampotila lampotila-pudussa) -8.20M) "keskilampotila")
      (is (= (:keskilampotila-1981-2010 lampotila-pudussa) -9.00M) "pitkakeskilampotila")
      (is (= (:alkupvm lampotila-pudussa) hoitokauden-alkupvm) "hoitokauden-alkupvm")
      (is (= (:loppupvm lampotila-pudussa) hoitokauden-loppupvm) "hoitokauden-loppupvm")))

  (testing "uuden lämpötilan luonti"
    (let [hoitokauden-alkupvm (pvm/->pvm "1.10.2015")
          hoitokauden-loppupvm (pvm/->pvm "30.9.2016")
          hoitokausi [hoitokauden-alkupvm hoitokauden-loppupvm]
          urakka-id @oulun-alueurakan-2014-2019-id

          lampotilat [{:lampotilaid nil :keskilampotila -26.2
                       :keskilampotila-1981-2010 -8.3 :urakka urakka-id
                       :alkupvm hoitokauden-alkupvm :loppupvm hoitokauden-loppupvm}]

          lampotilat-kutsun-jalkeen (vals (kutsu-palvelua (:http-palvelin jarjestelma)
                                            :tallenna-teiden-hoitourakoiden-lampotilat
                                            +kayttaja-jvh+
                                            {:hoitokausi hoitokausi
                                             :lampotilat lampotilat}))
          lampotila-oulussa (first (filter #(= (:urakka %) urakka-id) lampotilat-kutsun-jalkeen))]
      (is (some? (:lampotilaid lampotila-oulussa)) ":lampotilaid")
      (is (some? (:urakka lampotila-oulussa)) "urakka")
      (is (= (:keskilampotila lampotila-oulussa) -26.20M) "keskilampotila")
      (is (= (:keskilampotila-1981-2010 lampotila-oulussa) -8.30M) "pitkakeskilampotila")
      (is (= (:alkupvm lampotila-oulussa) hoitokauden-alkupvm) "hoitokauden-alkupvm")
      (is (= (:loppupvm lampotila-oulussa) hoitokauden-loppupvm) "hoitokauden-loppupvm")))

  (testing "olemassaolevan lämpötilan päivitys"
    (let [hoitokauden-alkupvm (pvm/->pvm "1.10.2014")
          hoitokauden-loppupvm (pvm/->pvm "30.9.2015")
          hoitokausi [hoitokauden-alkupvm hoitokauden-loppupvm]
          urakka-id @oulun-alueurakan-2014-2019-id
          lampotila-id @oulun-alueurakan-lampotila-hk-2014-2015
          lampotilat [{:lampotilaid lampotila-id :keskilampotila -26.2
                       :keskilampotila-1981-2010 -8.3 :urakka urakka-id
                       :alkupvm hoitokauden-alkupvm :loppupvm hoitokauden-loppupvm}]

          lampotilat-kutsun-jalkeen (vals (kutsu-palvelua (:http-palvelin jarjestelma)
                                            :tallenna-teiden-hoitourakoiden-lampotilat
                                            +kayttaja-jvh+
                                            {:hoitokausi hoitokausi
                                             :lampotilat lampotilat}))
          lampotila-oulussa (first (filter #(= (:urakka %) urakka-id) lampotilat-kutsun-jalkeen))]
      (is (= (:lampotilaid lampotila-oulussa) lampotila-id) ":lampotilaid")
      (is (some? (:urakka lampotila-oulussa)) "urakka")
      (is (= (:keskilampotila lampotila-oulussa) -26.20M) "keskilampotila")
      (is (= (:keskilampotila-1981-2010 lampotila-oulussa) -8.30M) "pitkakeskilampotila")
      (is (= (:alkupvm lampotila-oulussa) hoitokauden-alkupvm) "hoitokauden-alkupvm")
      (is (= (:loppupvm lampotila-oulussa) hoitokauden-loppupvm) "hoitokauden-loppupvm"))))
