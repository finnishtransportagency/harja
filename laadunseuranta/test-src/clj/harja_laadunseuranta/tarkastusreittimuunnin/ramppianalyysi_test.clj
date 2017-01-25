(ns harja-laadunseuranta.tarkastusreittimuunnin.ramppianalyysi-test
  (:require [clojure.test :refer :all]
            [harja-laadunseuranta.tarkastusreittimuunnin.ramppianalyysi :as ramppianalyysi]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja-laadunseuranta.kyselyt :as q]
            [com.stuartsierra.component :as component]
            [harja.domain.tierekisteri :as tr-domain]
            [harja-laadunseuranta.core :as harja-laadunseuranta]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :harja-laadunseuranta
                        (component/using
                          (harja-laadunseuranta/->Laadunseuranta nil)
                          [:db :http-palvelin])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(deftest maarittele-alkavien-ramppien-idneksit
  (let [tarkastusajo-id 665 ;; Osa tiellä 4 olevista pisteistä projisoituu virheellisesti rampeille
        merkinnat (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                         {:tarkastusajo tarkastusajo-id
                                                          :laheiset_tiet_threshold 100})
        merkinnat (ramppianalyysi/lisaa-merkintoihin-ramppitiedot merkinnat)
        indeksit (ramppianalyysi/maarittele-alkavien-ramppien-indeksit merkinnat)]
    (is (= indeksit [3]))))

(deftest ramppianalyysi-korjaa-virheelliset-rampit-kun-pari-pistetta-osuu-rampille
  (let [tarkastusajo-id 665 ;; Pari pisteistä sijaitsee virheellisesti rampilla
        merkinnat (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                         {:tarkastusajo tarkastusajo-id
                                                          :laheiset_tiet_threshold 100})]

    (is (> (count merkinnat) 1) "Ainakin yksi merkintä testidatassa")
    (is (some #(tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie])) merkinnat)
        "Osa testidatan merkinnöistä on rampilla")

    (let [korjatut-merkinnat (ramppianalyysi/korjaa-virheelliset-rampit merkinnat)]
      (is (= (count korjatut-merkinnat) (count merkinnat)))
      (is (not-any? #(tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie]))
                    korjatut-merkinnat))
      (is (every? #(or (= (get-in % [:tr-osoite :tie]) 4)
                       (nil? (get-in % [:tr-osoite :tie])))
                  korjatut-merkinnat))
      (is (not-any? :piste-rampilla? korjatut-merkinnat) "Merkinnät eivät sisällä analyysin sisäistä avainta"))))

(deftest ramppianalyysi-korjaa-virheelliset-rampit-kun-osa-pisteista-osuu-rampille
  (let [tarkastusajo-id 666 ;; Iso osa pisteistä sivuaa rampin reunaa
        merkinnat (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                         {:tarkastusajo tarkastusajo-id
                                                          :laheiset_tiet_threshold 100})]

    (is (> (count merkinnat) 1) "Ainakin yksi merkintä testidatassa")
    (is (some #(tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie])) merkinnat)
        "Osa testidatan merkinnöistä on rampilla")

    (let [korjatut-merkinnat (ramppianalyysi/korjaa-virheelliset-rampit merkinnat)]
      (is (= (count korjatut-merkinnat) (count merkinnat)))
      (is (not-any? #(tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie]))
                    korjatut-merkinnat))
      (is (every? #(or (= (get-in % [:tr-osoite :tie]) 4)
                       (nil? (get-in % [:tr-osoite :tie])))
                  korjatut-merkinnat)))))

(deftest ramppianalyysi-korjaa-virheelliset-rampit-kun-iso-osa-pisteista-osuu-rampille
  (let [tarkastusajo-id 667 ;; Iso osa pisteistä sijoittuu pitkästi rampille, joskin epätarkasti
        merkinnat (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                         {:tarkastusajo tarkastusajo-id
                                                          :laheiset_tiet_threshold 100})]

    (is (> (count merkinnat) 1) "Ainakin yksi merkintä testidatassa")
    (is (some #(tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie])) merkinnat)
        "Osa testidatan merkinnöistä on rampilla")

    (let [korjatut-merkinnat (ramppianalyysi/korjaa-virheelliset-rampit merkinnat)]
      (is (= (count korjatut-merkinnat) (count merkinnat)))
      (is (not-any? #(tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie]))
                    korjatut-merkinnat))
      (is (every? #(or (= (get-in % [:tr-osoite :tie]) 4)
                       (nil? (get-in % [:tr-osoite :tie])))
                  korjatut-merkinnat)))))

(deftest ramppianalyysi-ei-tee-mitaan-kun-ajetaan-oikeasti-rampille
  (let [tarkastusajo-id 668 ;; Ajetaan tieltä 4 rampille ja takaisin tielle 4
        merkinnat (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                         {:tarkastusajo tarkastusajo-id
                                                          :laheiset_tiet_threshold 100})]

    (is (> (count merkinnat) 1) "Ainakin yksi merkintä testidatassa")
    (is (some #(tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie])) merkinnat)
        "Osa testidatan merkinnöistä on rampilla")

    (let [korjatut-merkinnat (ramppianalyysi/korjaa-virheelliset-rampit merkinnat)]
      (is (= (count korjatut-merkinnat) (count merkinnat)))
      (is (= korjatut-merkinnat merkinnat)))))

(deftest ramppianalyysi-korjaa-virheelliset-rampit-oikeassa-ajossa
  (let [tarkastusajo-id 754 ;; Oikea ajo, jossa osa tiellä 4 olevista pisteistä projisoituu virheellisesti rampeille
        merkinnat (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                         {:tarkastusajo tarkastusajo-id
                                                          :laheiset_tiet_threshold 100})]

    (is (> (count merkinnat) 1) "Ainakin yksi merkintä testidatassa")
    (is (some #(tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie])) merkinnat)
        "Osa testidatan merkinnöistä on rampilla")

    (let [korjatut-merkinnat (ramppianalyysi/korjaa-virheelliset-rampit merkinnat)]
      (is (= (count korjatut-merkinnat) (count merkinnat)))
      ;; Alussa ajettiin oikeasti rampilla, nämä pisteet ovat edelleen mukana
      (is (some #(tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie]))
                    (take 40 korjatut-merkinnat)))
      ;; Loppumatkan rampit on projisoitu uudelleen
      (is (not-any? #(tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie]))
                    (drop 40 korjatut-merkinnat))))))