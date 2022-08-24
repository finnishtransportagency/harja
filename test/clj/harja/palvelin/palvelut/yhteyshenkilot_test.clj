(ns harja.palvelin.palvelut.yhteyshenkilot-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yhteyshenkilot :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konv]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :hae (component/using
                      (->Yhteyshenkilot)
                      [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(deftest urakan-yhteyshenkiloiden-haku-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-yhteyshenkilot +kayttaja-jvh+ 1)]

    (is (not (nil? vastaus)))
    (is (>= (count vastaus) 1))))

(deftest urakan-paivystajien-haku-toimii
  (u "INSERT INTO paivystys (vastuuhenkilo, varahenkilo, alku, loppu, urakka, yhteyshenkilo) VALUES (true, false, '2005-10-10','2030-06-06', 1, 1)")
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-paivystajat +kayttaja-jvh+ 1)]
    (log/info "VASTAUS: " vastaus)
    (is (not (nil? vastaus)))
    (is (>= (count vastaus) 1))
    (mapv (fn [yhteyshenkilo] (do
                                (is (string? (:etunimi yhteyshenkilo)))
                                (is (string? (:sukunimi yhteyshenkilo))))) vastaus)))

(deftest urakan-paivystajien-tallennus-toimii
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        paivystaja-count (ffirst (q "SELECT count (*) FROM paivystys;"))
        ismon-paivystys-id (ffirst (q "SELECT id FROM paivystys where yhteyshenkilo = (SELECT id from YHTEYSHENKILO where sahkoposti = 'ismoyit@example.org');"))
        uusi-sallittu-paivystaja {:sahkoposti "uusi.tyyppi@example.org"
                                  :etunimi "Uusi"
                                  :sukunimi "Päivystäjä"
                                  :id -1
                                  :matkapuhelin "+358234567"
                                  :tyopuhelin "+358765432"
                                  :vastuuhenkilo true
                                  :alku (pvm/nyt)
                                  :loppu (pvm/->pvm "1.1.2030")
                                  :organisaatio {:nimi "YIT Rakennus Oy" :id (hae-yit-rakennus-id) :ytunnus "1565583-5"}}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-urakan-paivystajat
                                +kayttaja-jvh+
                                {:urakka-id urakka-id
                                 :paivystajat [uusi-sallittu-paivystaja]
                                 :poistettu []})
        vastauksen-uusi-paivystaja (first (filter #(= (:sahkoposti %) "uusi.tyyppi@example.org") vastaus))
        vastauksen-vanha-paivystaja (first (filter #(= (:sahkoposti %) "ismoyit@example.org") vastaus))]
    (is (not (nil? vastaus)))
    (is (= (count vastaus) 2))
    (is (pvm/valissa? (pvm/nyt) (:alku vastauksen-uusi-paivystaja) (:loppu vastauksen-uusi-paivystaja)) "Uusi päivystäjä voimassa")
    (is (pvm/valissa? (pvm/nyt) (:alku vastauksen-vanha-paivystaja) (:loppu vastauksen-vanha-paivystaja)) "Vanha päivystäjä voimassa")
    (is (= (select-keys vastauksen-uusi-paivystaja [:organisaatio :vastuuhenkilo :varahenkilo :matkapuhelin :tyopuhelin :id])
           {:organisaatio  {:id (hae-yit-rakennus-id)
                            :nimi "YIT Rakennus Oy"
                            :tyyppi :urakoitsija}
            :vastuuhenkilo true :varahenkilo false :matkapuhelin "+358234567" :tyopuhelin "+358765432" :id (+ 1 paivystaja-count)}))

    (is (= (select-keys vastauksen-vanha-paivystaja [:organisaatio :vastuuhenkilo :varahenkilo :matkapuhelin :tyopuhelin :id])
           {:id ismon-paivystys-id
            :matkapuhelin "0400123456"
            :organisaatio {:id (hae-yit-rakennus-id)
                           :nimi "YIT Rakennus Oy"
                           :tyyppi :urakoitsija}
            :tyopuhelin "000"
            :varahenkilo true
            :vastuuhenkilo false}))))

(deftest uudelle-paivystajalle-ei-saa-asettaa-alkupvmaa-menneisyyteen
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        uusi-kielletty-paivystaja {:sahkoposti "uusi.tyyppi@example.org"
                                  :etunimi "Uusi"
                                  :sukunimi "Päivystäjä"
                                  :id -1
                                  :matkapuhelin "+358234567"
                                  :tyopuhelin "+358765432"
                                  :vastuuhenkilo true
                                  :alku (pvm/->pvm "1.1.2022")
                                  :loppu (pvm/->pvm "1.1.2030")
                                  :organisaatio {:nimi "YIT Rakennus Oy" :id (hae-yit-rakennus-id) :ytunnus "1565583-5"}}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-urakan-paivystajat
                                +kayttaja-jvh+
                                {:urakka-id urakka-id
                                 :paivystajat [uusi-kielletty-paivystaja]
                                 :poistettu []})]
    (is (= vastaus
           {:body "[\"^ \",\"~:virhe\",\"Päivystäjän Uusi Päivystäjä päivystysvuoroa ei saa asettaa alkamaan ennen tätä päivää.\"]"
            :headers {"Content-Type" "application/transit+json"}
            :status 400
            :vastaus {:virhe "Päivystäjän Uusi Päivystäjä päivystysvuoroa ei saa asettaa alkamaan ennen tätä päivää."}}))))

(deftest olemassaolevan-paivystajan-vuoron-alkupvmaa-voi-siirtaa-eteenpain
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        ismon-paivystys-id (ffirst (q "SELECT id FROM paivystys where yhteyshenkilo = (SELECT id from YHTEYSHENKILO where sahkoposti = 'ismoyit@example.org');"))
        olemassaoleva-kielletty-paivystaja {:sahkoposti "ismoyit@example.org"
                                            :etunimi "Ismo"
                                            :sukunimi "Laitela"
                                            :id ismon-paivystys-id
                                            :matkapuhelin "+358234567"
                                            :tyopuhelin "+358765432"
                                            :vastuuhenkilo true
                                            :alku (pvm/->pvm "1.1.2022")
                                            :loppu (pvm/->pvm "1.1.2030")
                                            :organisaatio {:nimi "YIT Rakennus Oy" :id (hae-yit-rakennus-id) :ytunnus "1565583-5"}}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-urakan-paivystajat
                                +kayttaja-jvh+
                                {:urakka-id urakka-id
                                 :paivystajat [olemassaoleva-kielletty-paivystaja]
                                 :poistettu []})
        vastauksen-vanha-paivystaja (first (filter #(= (:sahkoposti %) "ismoyit@example.org") vastaus))]
    (is (= (select-keys vastauksen-vanha-paivystaja [:organisaatio :vastuuhenkilo :varahenkilo :matkapuhelin :tyopuhelin :id])
           {:id ismon-paivystys-id
            :matkapuhelin "+358234567"
            :organisaatio {:id 14
                           :nimi "YIT Rakennus Oy"
                           :tyyppi :urakoitsija}
            :tyopuhelin "+358765432"
            :varahenkilo false
            :vastuuhenkilo true}))
    (is (= (pvm/->pvm "1.1.2022")
           (konv/java-date (:alku vastauksen-vanha-paivystaja))))
    (is (= (pvm/->pvm "1.1.2030")
           (konv/java-date (:loppu vastauksen-vanha-paivystaja))))))

(deftest olemassaolevan-paivystan-vuoron-alkupvmaa-ei-saa-siirtaa-taaksepain
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        ismon-paivystys-id (ffirst (q "SELECT id FROM paivystys where yhteyshenkilo = (SELECT id from YHTEYSHENKILO where sahkoposti = 'ismoyit@example.org');"))
        olemassaoleva-kielletty-paivystaja {:sahkoposti "ismoyit@example.org"
                                            :etunimi "Ismo"
                                            :sukunimi "Laitela"
                                            :id ismon-paivystys-id
                                            :matkapuhelin "+358234567"
                                            :tyopuhelin "+358765432"
                                            :vastuuhenkilo true
                                            :alku (pvm/->pvm "1.1.2014") ;; vanha alkupvm kannassa 1.11.2015
                                            :loppu (pvm/->pvm "1.1.2030")
                                            :organisaatio {:nimi "YIT Rakennus Oy" :id (hae-yit-rakennus-id) :ytunnus "1565583-5"}}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-urakan-paivystajat
                                +kayttaja-jvh+
                                {:urakka-id urakka-id
                                 :paivystajat [olemassaoleva-kielletty-paivystaja]
                                 :poistettu []})]
    (is (= vastaus
           {:body "[\"^ \",\"~:virhe\",\"Olemassaolevan päivystäjän Ismo Laitela päivystysvuoron alkua ei saa takautuvasti siirtää kauemmaksi menneisyyteen.\"]"
            :headers {"Content-Type" "application/transit+json"}
            :status 400
            :vastaus {:virhe "Olemassaolevan päivystäjän Ismo Laitela päivystysvuoron alkua ei saa takautuvasti siirtää kauemmaksi menneisyyteen."}}))))

(deftest olemassaolevan-paivystan-vuoron-alkupvmaa-saa-siirtaa-taaksepain-jos-tulevaisuudessa
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        aseta-tulevaisuuteen (u "UPDATE paivystys SET alku = '2029-01-01' WHERE yhteyshenkilo = (SELECT id from YHTEYSHENKILO where sahkoposti = 'ismoyit@example.org');")
        ismon-paivystys-id (ffirst (q "SELECT id FROM paivystys where yhteyshenkilo = (SELECT id from YHTEYSHENKILO where sahkoposti = 'ismoyit@example.org');"))
        olemassaoleva-kielletty-paivystaja {:sahkoposti "ismoyit@example.org"
                                            :etunimi "Ismo"
                                            :sukunimi "Laitela"
                                            :id ismon-paivystys-id
                                            :matkapuhelin "+358234567"
                                            :tyopuhelin "+358765432"
                                            :vastuuhenkilo true
                                            :alku (pvm/->pvm "1.1.2028") ;; vanha alkupvm kannassa 1.11.2015
                                            :loppu (pvm/->pvm "1.1.2030")
                                            :organisaatio {:nimi "YIT Rakennus Oy" :id (hae-yit-rakennus-id) :ytunnus "1565583-5"}}
        vastaus (first (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :tallenna-urakan-paivystajat
                                 +kayttaja-jvh+
                                 {:urakka-id urakka-id
                                  :paivystajat [olemassaoleva-kielletty-paivystaja]
                                  :poistettu []}))]
    (is (=  (select-keys vastaus [:organisaatio :vastuuhenkilo :varahenkilo :matkapuhelin :tyopuhelin :id :etunimi :sukunimi :sahkoposti])
            {:etunimi "Ismo"
             :id ismon-paivystys-id
             :matkapuhelin "+358234567"
             :organisaatio {:id (hae-yit-rakennus-id)
                            :nimi "YIT Rakennus Oy"
                            :tyyppi :urakoitsija}
             :sahkoposti "ismoyit@example.org"
             :sukunimi "Laitela"
             :tyopuhelin "+358765432"
             :varahenkilo false
             :vastuuhenkilo true}))))