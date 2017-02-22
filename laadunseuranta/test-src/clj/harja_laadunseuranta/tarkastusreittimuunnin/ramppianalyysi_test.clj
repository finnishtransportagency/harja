(ns harja-laadunseuranta.tarkastusreittimuunnin.ramppianalyysi-test
  (:require [clojure.test :refer :all]
            [harja-laadunseuranta.tarkastusreittimuunnin.ramppianalyysi :as ramppianalyysi]
            [harja-laadunseuranta.tarkastusreittimuunnin.testityokalut :as tyokalut]
            [harja.testi :refer :all]
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
                        :mobiili-laadunseuranta
                        (component/using
                          (harja-laadunseuranta/->Laadunseuranta)
                          [:db :http-palvelin])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures tietokanta-fixture jarjestelma-fixture))

;; HOX! Tässä tehdään testejä kannassa löytyville ajoille, joilla on tietty id.
;; Ajojen tekstuaalisen selityksen löydät: testidata/tarkastusajot.sql
;; Tai käytä #tr näkymää piirtämään pisteet kartalle.

(deftest ramppien-indeksit-ja-merkkinat-toimii
  (let [tarkastusajo-id 665
        merkinnat (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                         {:tarkastusajo tarkastusajo-id
                                                          :laheiset_tiet_threshold 100})
        merkinnat (ramppianalyysi/lisaa-merkintoihin-ramppitiedot merkinnat)
        indeksit (ramppianalyysi/maarittele-alkavien-ramppien-indeksit merkinnat)]
    (is (= indeksit [3]))))

(deftest ramppianalyysi-korjaa-virheelliset-rampit-kun-yksi-piste-osuu-rampille
  (let [tarkastusajo-id 664
        merkinnat (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                         {:tarkastusajo tarkastusajo-id
                                                          :laheiset_tiet_threshold 100})]

    (is (> (count merkinnat) 1) "Ainakin yksi merkintä testidatassa")
    (is (= (count (filter #(tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie])) merkinnat))
           1)
        "Yksi testidatan merkinnöistä on rampilla")

    (let [korjatut-merkinnat (ramppianalyysi/korjaa-virheelliset-rampit merkinnat)]
      (is (= (count korjatut-merkinnat) (count merkinnat)))
      (is (not-any? #(tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie]))
                    korjatut-merkinnat))
      (is (every? #(or (= (get-in % [:tr-osoite :tie]) 4)
                       (nil? (get-in % [:tr-osoite :tie])))
                  korjatut-merkinnat)))))

(deftest ramppianalyysi-korjaa-virheelliset-rampit-kun-pari-pistetta-osuu-rampille
  (let [tarkastusajo-id 665
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
  (let [tarkastusajo-id 666
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

(deftest ramppianalyysi-korjaa-virheelliset-rampit-kun-iso-osa-epatarkkoja-pisteita-osuu-rampille
  (let [tarkastusajo-id 667
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

(deftest ramppianalyysi-ei-tee-mitaan-kun-iso-osa-tarkkoja-pisteita-osuu-rampille
  (let [tarkastusajo-id 667
        merkinnat (-> (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                             {:tarkastusajo tarkastusajo-id
                                                              :laheiset_tiet_threshold 100})
                      (tyokalut/aseta-merkintojen-tarkkuus 5))]

    (let [korjatut-merkinnat (ramppianalyysi/korjaa-virheelliset-rampit merkinnat)]
      (is (= (count korjatut-merkinnat) (count merkinnat)))
      (is (= korjatut-merkinnat merkinnat)))))

(deftest ramppianalyysi-ei-tee-mitaan-kun-iso-osa-melko-tarkkoja-pisteita-osuu-rampille
  (let [tarkastusajo-id 667
        merkinnat (-> (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                             {:tarkastusajo tarkastusajo-id
                                                              :laheiset_tiet_threshold 100})
                      (tyokalut/aseta-merkintojen-tarkkuus 13))]

    (let [korjatut-merkinnat (ramppianalyysi/korjaa-virheelliset-rampit merkinnat)]
      (is (= (count korjatut-merkinnat) (count merkinnat)))
      (is (= korjatut-merkinnat merkinnat)))))

(deftest ramppianalyysi-ei-tee-mitaan-kun-ajetaan-oikeasti-rampille
  (let [tarkastusajo-id 668
        merkinnat (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                         {:tarkastusajo tarkastusajo-id
                                                          :laheiset_tiet_threshold 100})]

    (is (> (count merkinnat) 1) "Ainakin yksi merkintä testidatassa")
    (is (some #(tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie])) merkinnat)
        "Osa testidatan merkinnöistä on rampilla")

    (let [korjatut-merkinnat (ramppianalyysi/korjaa-virheelliset-rampit merkinnat)]
      (is (= (count korjatut-merkinnat) (count merkinnat)))
      (is (= korjatut-merkinnat merkinnat)))))

(deftest ramppianalyysi-ei-tee-mitaan-kun-ajetaan-oikeasti-rampille-melko-epatarkalla-gpslla
  (let [tarkastusajo-id 668
        merkinnat (-> (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                             {:tarkastusajo tarkastusajo-id
                                                              :laheiset_tiet_threshold 100})
                      ;; Pisteet etenevät ramppia pitkin ja GPS:n tarkkuussäde on 30m.
                      ;; Tämä riittää varmistamaan, että pisteet ovat oikeasti sijoittuneet
                      ;; rampille eli rampilla ajo hyväksytään.
                      (tyokalut/aseta-merkintojen-tarkkuus 30))]

    (let [korjatut-merkinnat (ramppianalyysi/korjaa-virheelliset-rampit merkinnat)]
      (is (= (count korjatut-merkinnat) (count merkinnat)))
      (is (= korjatut-merkinnat merkinnat)))))

(deftest ramppianalyysi-korjaa-virheelliset-rampit-kun-iso-osa-erittain-epatarkkoja-pisteita-osuu-rampille
  (let [tarkastusajo-id 668
        merkinnat (-> (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                          {:tarkastusajo tarkastusajo-id
                                                           :laheiset_tiet_threshold 100})
                      ;; Erittäin epätarkka ajo rampilla, jota ei hyväksytä.
                      ;; Merkintöjen odotetaan projisoituvan takaisin moottoritielle.
                      (tyokalut/aseta-merkintojen-tarkkuus 80))]

    (let [korjatut-merkinnat (ramppianalyysi/korjaa-virheelliset-rampit merkinnat)]
      (is (= (count korjatut-merkinnat) (count merkinnat)))
      (is (not-any? #(tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie]))
                    korjatut-merkinnat))
      (is (every? #(or (= (get-in % [:tr-osoite :tie]) 4)
                       (nil? (get-in % [:tr-osoite :tie])))
                  korjatut-merkinnat)))))

(deftest ramppianalyysi-korjaa-virheelliset-rampit-oikeassa-ajossa-754
  (let [tarkastusajo-id 754
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

(deftest ramppianalyysi-korjaa-virheelliset-rampit-oikeassa-ajossa-1
  (let [tarkastusajo-id 1
        merkinnat (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                         {:tarkastusajo tarkastusajo-id
                                                          :laheiset_tiet_threshold 100})]

    (is (> (count merkinnat) 1) "Ainakin yksi merkintä testidatassa")
    (is (some #(tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie])) merkinnat)
        "Osa testidatan merkinnöistä on rampilla")

    (let [korjatut-merkinnat (ramppianalyysi/korjaa-virheelliset-rampit merkinnat)
          osa-1-tie-18637 (take 74 korjatut-merkinnat)
          osa-2-ramppi-28409 (take (- 92 77) (drop 77 korjatut-merkinnat))
          osa-3-tie-4 (take (- 301 92) (drop 92 korjatut-merkinnat))
          osa-4-ramppi-28402 (take (- 342 301) (drop 301 korjatut-merkinnat))
          osa-5-tie-22 (drop 343 korjatut-merkinnat)]

      (is (= (count korjatut-merkinnat) (count merkinnat)))

      ;; Osa 1: Ajetaan tietä 18637, joka ei ole ramppi
      (is (not-any? #(tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie]))
                    osa-1-tie-18637))
      (is (every? #(= (get-in % [:tr-osoite :tie]) 18637) osa-1-tie-18637))

      ;; Osa 2: Siirrytään rampille
      (is (every? #(tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie]))
                  osa-2-ramppi-28409))
      (is (every? #(= (get-in % [:tr-osoite :tie]) 28409) osa-2-ramppi-28409))

      ;; Osa 3: Ajetaan tietä 4 pitkän matkaa
      (is (not-any? #(tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie]))
                    osa-3-tie-4))

      ;; Tässä kohtaa kaikki pisteet osuvat tiehen 4 paitsi yksi, joka osuu eri tielle
      ;; yli-/alikulun kohdalla. Tämä hanskataan erikseen omassa testissä.

      ;; Osa 4: Taas ollaan rampilla
      (is (every? #(tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie]))
                  osa-4-ramppi-28402))
      (is (every? #(= (get-in % [:tr-osoite :tie]) 28402) osa-4-ramppi-28402))

      ;; Osa 5: Loppumatka tietä 22
      (is (not-any? #(tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie]))
                    osa-5-tie-22))
      (is (every? #(= (get-in % [:tr-osoite :tie]) 22) osa-5-tie-22)))))

