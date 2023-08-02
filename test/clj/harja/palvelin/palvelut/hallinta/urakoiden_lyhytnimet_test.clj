(ns harja.palvelin.palvelut.hallinta.urakoiden-lyhytnimet-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.palvelut.hallinta.urakoiden-lyhytnimet :as urakoiden-lyhytnimet]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja
             [testi :refer :all]]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :lyhytnimien-hallinta (component/using
                                  (urakoiden-lyhytnimet/->UrakkaLyhytnimienHallinta)
                                  [:db :http-palvelin])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(defn- hakuparametrit-kaikki []
  {:urakkatyyppi :kaikki :vain-puuttuvat false :urakan-tila :kaikki})

(defn- tallenna-parametrit [urakkaid]
  {:urakat [{:id urakkaid,
             :nimi "Aktiivinen Oulu Testi",
             :lyhyt_nimi "test123"}]
   :haku-parametrit (hakuparametrit-kaikki)})

(deftest paivita-lyhytnimet
  (let [urakkaid (hae-urakan-id-nimella "Aktiivinen Oulu Testi")
        tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                :hae-urakoiden-nimet +kayttaja-jvh+
                (hakuparametrit-kaikki))
        vaihdettu-nimi (kutsu-palvelua (:http-palvelin jarjestelma)
                         :tallenna-urakoiden-lyhytnimet +kayttaja-jvh+
                         (tallenna-parametrit urakkaid))]
    (is (= (:lyhyt_nimi (first (filter (comp #{urakkaid} :id) tulos))) "Oulun lyhyt nimi"))
    (is (= (:lyhyt_nimi (first (filter (comp #{urakkaid} :id) vaihdettu-nimi))) "test123"))))

(deftest hae-urakat-joilta-lyhytnimi-puuttuu
  (let [hakutulos (kutsu-palvelua (:http-palvelin jarjestelma)
                    :hae-urakoiden-nimet +kayttaja-jvh+
                    (assoc (hakuparametrit-kaikki) :vain-puuttuvat true))]
    (is (every? #(nil? (:lyhyt_nimi %)) hakutulos))))

(deftest hae-urakat-urakkatyypilla-ja-lyhytnimi-puuttuu
  (let [hakuehdot-hoito (assoc (hakuparametrit-kaikki) :urakkatyyppi :hoito)
        hakutulos-hoito (kutsu-palvelua (:http-palvelin jarjestelma)
                    :hae-urakoiden-nimet +kayttaja-jvh+
                    hakuehdot-hoito)
        hoitoidt (into #{} (map :id hakutulos-hoito))
        hakutulos-hoito-vain-puuttuvat (kutsu-palvelua (:http-palvelin jarjestelma)
                          :hae-urakoiden-nimet +kayttaja-jvh+
                          (assoc hakuehdot-hoito :vain-puuttuvat true))
        hoito-vain-puuttuvat-idt (into #{} (map :id hakutulos-hoito-vain-puuttuvat))]

    (is (some #(= (:lyhyt_nimi %) "Oulun lyhyt nimi") hakutulos-hoito))
    (is (every? #(nil? (:lyhyt_nimi %)) hakutulos-hoito-vain-puuttuvat))
    (is (every? #(contains? hoitoidt %) hoito-vain-puuttuvat-idt))))

(deftest hae-urakat-urakkatyypilla-ja-tila-ehdolla
  (let [hakuehdot-hoito (assoc (hakuparametrit-kaikki) :urakkatyyppi :hoito)
        hakutulos-hoito (kutsu-palvelua (:http-palvelin jarjestelma)
                          :hae-urakoiden-nimet +kayttaja-jvh+
                          hakuehdot-hoito)
        hoitoidt (into #{} (map :id hakutulos-hoito))
        hakutulos-hoito-paattyneet (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :hae-urakoiden-nimet +kayttaja-jvh+
                                         (assoc hakuehdot-hoito :urakan-tila :paattyneet))
        hoito-paattyneet-idt (into #{} (map :id hakutulos-hoito-paattyneet))]

    (is (not-empty hakutulos-hoito-paattyneet))
    (is (every? #(contains? hoitoidt %) hoito-paattyneet-idt))))
