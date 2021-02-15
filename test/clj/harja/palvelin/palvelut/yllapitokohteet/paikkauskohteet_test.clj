(ns harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet :as paikkauskohteet]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :paikkauskohteet (component/using
                                           (paikkauskohteet/->Paikkauskohteet)
                                           [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(def default-paikkauskohde {:nimi "testinimi"
                            :alkuaika (pvm/->pvm-aika "01.01.2020 00:00:00" )
                            :loppuaika (pvm/->pvm-aika "01.02.2020 00:00:00" )
                            :paikkauskohteen-tila "valmis"
                            :tie 22
                            :aosa 1
                            :losa 2
                            :aet 10
                            :let 20})

(deftest paikkauskohteet-urakalle-testi
  (let [_ (hae-kemin-alueurakan-2019-2023-id)
        urakka-id @kemin-alueurakan-2019-2023-id
        paikkauskohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :paikkauskohteet-urakalle
                                        +kayttaja-jvh+
                                        {:urakka-id urakka-id})]
    (is (> (count paikkauskohteet) 0))))

(deftest muokkaa-paikkauskohdetta-testi
  (let [_ (hae-kemin-alueurakan-2019-2023-id)
        urakka-id @kemin-alueurakan-2019-2023-id
        paikkauskohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :paikkauskohteet-urakalle
                                        +kayttaja-jvh+
                                        {:urakka-id urakka-id})
        ;; Otetaan eka
        kohde (first paikkauskohteet)
        ;; Muokataan nimi, tierekisteriosoite, alkuaika, loppuaika, tila
        kohde (-> kohde
                  (assoc :nimi "testinimi")
                  (assoc :tie "22")
                  (assoc :alkuaika (pvm/->pvm-aika "01.01.2020 00:00:00" ))
                  (assoc :loppuaika (pvm/->pvm-aika "01.02.2020 00:00:00" ))
                  (assoc :paikkauskohteen-tila "valmis"))
        muokattu-kohde (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-paikkauskohde-urakalle
                                       +kayttaja-jvh+
                                       kohde)]
    (is (= muokattu-kohde kohde))))

(deftest luo-uusi-paikkauskohde-testi
  (let [urakka-id @kemin-alueurakan-2019-2023-id
        kohde (merge {:urakka-id urakka-id}
                     default-paikkauskohde)

        kohde-id (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :tallenna-paikkauskohde-urakalle
                                 +kayttaja-jvh+
                                 kohde)
        paikkauskohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :paikkauskohteet-urakalle
                                        +kayttaja-jvh+
                                        {:urakka-id urakka-id})]
    (is (> (count paikkauskohteet) 3))))

(deftest luo-uusi-paikkauskohde-virheellisin-tiedoin-testi
  (let [urakka-id @kemin-alueurakan-2019-2023-id
        ;; Poistetaan kohteelta nimi
        kohde (dissoc (merge {:urakka-id urakka-id} default-paikkauskohde)
                      :nimi )]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-paikkauskohde-urakalle
                                           +kayttaja-jvh+
                                           kohde))
        "Poikkeusta ei heitetty! Tallennus onnistui vaikka ei oli saanut")))
