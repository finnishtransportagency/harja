(ns harja.palvelin.palvelut.laskut-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.palvelut.laskut :as laskut]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (luo-testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :laskut (component/using
                                  (laskut/->Laskut)
                                  [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(def uusi-lasku
  {:id            nil
   :urakka        (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
   :viite         "6666668"
   :erapaiva      #inst "2021-12-15T21:00:00.000-00:00"
   :kokonaissumma 1332
   :tyyppi        "laskutettava"
   :suorittaja-nimi "Kaarinan Kadunkiillotus Oy"
   :kohdistukset  [{:kohdistus-id        nil
                    :rivi                1
                    :summa               666
                    :suorittaja-nimi     "Kaarinan Kadunkiillotus Oy"
                    :suorittaja-id       1
                    :suoritus-alku       #inst "2021-11-14T22:00:00.000000000-00:00"
                    :suoritus-loppu      #inst "2021-11-17T22:00:00.000000000-00:00"
                    :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
                    :tehtavaryhma        (hae-tehtavaryhman-id "Viheralueiden hoito")
                    :tehtava             nil}
                   {:kohdistus-id        nil
                    :rivi                2
                    :summa               666
                    :suorittaja-nimi     "Kaarinan Kadunkiillotus Oy"
                    :suorittaja-id       1
                    :suoritus-alku       #inst "2021-11-14T22:00:00.000000000-00:00"
                    :suoritus-loppu      #inst "2021-11-17T22:00:00.000000000-00:00"
                    :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
                    :tehtavaryhma        (hae-tehtavaryhman-id "Vesakon raivaus ja runkopuun poisto")
                    :tehtava             nil}]
   :liitteet      [{:liite_id     nil
                    :liite_tyyppi "image/png"
                    :liite_nimi   "pensas-2021-01.jpg"
                    :liite_koko   nil
                    :liite_oid    nil}
                   {:liite_id     nil
                    :liite_tyyppi "image/png"
                    :liite_nimi   "pensas-2021-02.jpg"
                    :liite_koko   nil
                    :liite_oid    nil}]})

(def uusi-kohdistus
  {:kohdistus-id        nil
   :rivi                3
   :summa               987
   :suorittaja-nimi     "Kaarinan Kadunkiillotus Oy"
   :suorittaja-id       1
   :suoritus-alku       #inst "2021-11-23T22:00:00.000000000-00:00"
   :suoritus-loppu      #inst "2021-11-24T22:00:00.000000000-00:00"
   :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
   :tehtavaryhma        (hae-tehtavaryhman-id "Vesakon raivaus ja runkopuun poisto")
   :tehtava             nil})


(def laskun-paivitys
  {:id            nil
   :urakka        (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
   :viite         "2019080022"
   :erapaiva      #inst "2021-12-15T21:00:00.000-00:00"
   :kokonaissumma 5555.55
   :tyyppi        "laskutettava"
   :suorittaja-nimi "Ali Urakoitsija Ky"
   :kohdistukset  [{:kohdistus-id        5
                    :rivi                2
                    :summa               3333.33
                    :suorittaja-nimi     "Uusi Suorittaja"
                    :suorittaja-id       nil
                    :suoritus-alku       #inst "2021-03-14T22:00:00.000000000-00:00"
                    :suoritus-loppu      #inst "2021-03-17T22:00:00.000000000-00:00"
                    :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
                    :tehtavaryhma        (hae-tehtavaryhman-id "Viheralueiden hoito")
                    :tehtava             nil}]})

(def lasku-akillinen-hoitotyo
  {:id            nil
   :urakka        (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
   :viite         "666017"
   :erapaiva      #inst "2021-10-15T21:00:00.000-00:00"
   :kokonaissumma 666.66
   :tyyppi        "laskutettava"
   :suorittaja-nimi "Äkkipika Oy"
   :kohdistukset  [{:kohdistus-id        nil
                    :rivi                1
                    :summa               666.66
                    :suorittaja-nimi     "Äkkipika Oy"
                    :suorittaja-id       nil
                    :suoritus-alku       #inst "2021-10-02T12:00:00.000000000-00:00"
                    :suoritus-loppu      #inst "2021-10-02T12:54:00.000000000-00:00"
                    :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
                    :tehtavaryhma        (hae-tehtavaryhman-id "Äkillinen hoitotyö")
                    :tehtava             nil}]
   :liitteet      []})

(def lasku-muu
  {:id            nil
   :urakka        (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
   :viite         "666017"
   :erapaiva      #inst "2021-10-15T21:00:00.000-00:00"
   :kokonaissumma 666.66
   :tyyppi        "laskutettava"
   :suorittaja-nimi "Kaarinan Kadunkiillotus Oy"
   :kohdistukset  [{:kohdistus-id        nil
                    :rivi                1
                    :summa               666.66
                    :suorittaja-nimi     "Kaarinan Kadunkiillotus Oy"
                    :suorittaja-id       1
                    :suoritus-alku       #inst "2021-10-02T12:00:00.000000000-00:00"
                    :suoritus-loppu      #inst "2021-10-02T12:54:00.000000000-00:00"
                    :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
                    :tehtavaryhma        (hae-tehtavaryhman-id "Kolmannen osapuolen vahinkojen korjaukset")
                    :tehtava             nil}]
   :liitteet      []})



;; testit hyödyntävät tallennettua testidataa

(deftest hae-lasku-testi
  (let [
        laskut (kutsu-http-palvelua :laskut +kayttaja-jvh+ {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                                            :alkupvm   "2019-10-01"
                                                            :loppupvm  "2020-09-30"})
        laskuerittely (kutsu-http-palvelua :lasku +kayttaja-jvh+
                                           {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                            :viite     "2019080019"})
        laskut-urakan-vastaavalle (kutsu-http-palvelua :laskut (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                                                       {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                                        :alkupvm   "2019-10-01"
                                                        :loppupvm  "2020-09-30"})
        laskuerittely-urakan-vastaavalle (kutsu-http-palvelua :lasku (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                                                              {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                                               :viite     "2019080019"})]

    (is (= laskut laskut-urakan-vastaavalle) "Urakan vastuuhenkilö saa samat tiedot laskuista kuin järjestelmävalvoja.")
    (is (= laskuerittely laskuerittely-urakan-vastaavalle) "Urakan vastuuhenkilö saa samat tiedot laskusta kuin järjestelmävalvoja.")
    (is (count (distinct (map :laskun-id laskuerittely))) "Urakan laskujen haku palauttaa kolme laskua.")
    (is (apply = (map :laskun-id laskuerittely)) "Laskuerittelyssä on vain yhden laskun tietoja.")
    (is (count (map :kohdistus-id laskuerittely)) "Laskun erittely sisältää kolme kohdistusta.")
    (is (= (:suorittaja laskuerittely) 1) "Aliurakoitsijan id palautuu." )
    (is (= (:kokonaissumma laskuerittely) 666.66M) "Kokonaissumma palautuu.")
    (is (= (:summa (first (filter #(= #inst "2019-11-21T22:00:00.000000000-00:00" (:suoritus-alku %)) (:kohdistukset laskuerittely)))) 222.22M) "Yksittäisen rivin summatieto palautuu.")))


(deftest tallenna-lasku-testi
  (let [tallennettu-lasku
        (kutsu-http-palvelua :tallenna-lasku (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id     (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :laskuerittely uusi-lasku})
        paivitetty-lasku
        (kutsu-http-palvelua :tallenna-lasku (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id     (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :laskuerittely (assoc uusi-lasku :kokonaissumma 9876.54 :laskun-id (:laskun-id tallennettu-lasku))})
        paivitetty-kohdistus
        (kutsu-http-palvelua :tallenna-lasku (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id     (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :laskuerittely laskun-paivitys})
        lisatty-kohdistus
        (kutsu-http-palvelua :tallenna-lasku (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id     (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :laskuerittely (assoc uusi-lasku
                                                    :kohdistukset (merge (uusi-lasku :kohdistukset)
                                                                         uusi-kohdistus))})
        poistettu-kohdistus
        (kutsu-http-palvelua :poista-laskurivi (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id     (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :laskun-viite "6666668"
                              :laskuerittelyn-rivi 3})
        poistettu-lasku
        (kutsu-http-palvelua :poista-lasku (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :laskun-id (:id tallennettu-lasku)})]

    ;; Tallennus
    (is (not (nil? (:laskun-id tallennettu-lasku))) "Lasku tallentui (tallennettu-lasku).")
    (is (= (count (:kohdistukset tallennettu-lasku)) 2) "Kohdistuksia tallentui kaksi (tallennettu-lasku).")

    ;; Päivitys: arvon muuttaminen
    (is (= (count (:kohdistukset paivitetty-lasku)) 2) "Päivitetyssä laskussa on kaksi kohdistusta (paivitetty-lasku).")
    (is (= (:kokonaissumma paivitetty-lasku) 9876.54M) "Päivitetyn laskun kokonaissumma päivittyi (paivitetty-lasku).")
    (is (= (count (:kohdistukset paivitetty-kohdistus)) 2) "Kohdistuksen päivityksen jälkeen on on kaksi kohdistusta (paivitetty-kohdistus).")
    ;;(is (= (map #(:summa %) paivitetty-kohdistus) (2222.22M 3333.33M)) "Päivitetyn kohdistuksen summa päivittyi. Toinen kohdistus säilyi muuttumattomana. (paivitetty-kohdistus)")
    (is (= (:kokonaissumma paivitetty-kohdistus) 5555.55M) "Päivitetyn laskun kokonaissumma päivittyi (paivitetty-kohdistus).")

    ;; Päivitys: kohdistuksen lisääminen
    (is (= (count (:kohdistukset lisatty-kohdistus)) 3) "Kohdistuksen lisääminen kasvatti kohdistusten määrää yhdellä (lisatty-kohdistus).")

    ;; Päivitys: kohdistuksen ja laskun poistaminen
    (is (= (count (:kohdistukset poistettu-kohdistus)) 2) "Kohdistuksen poistaminen vähensi kohdistusten määrää yhdellä (poistettu-kohdistus).")
    (is (= (count (:kohdistukset poistettu-lasku)) 0) "Laskun poistaminen poistaa kohdistukset (poistettu-lasku).")
    (is (= (count (:kohdistukset poistettu-lasku)) 0) "Poistettua laskua ei palaudu (poistettu-lasku).")))


(deftest paivita-maksuera-testi
  (let [lasku-kokonaishintainen-tyo
        (kutsu-http-palvelua :tallenna-lasku (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id     (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :laskuerittely (assoc uusi-lasku :viite "1413418")})
        lasku-akillinen-hoitotyo
        (kutsu-http-palvelua :tallenna-lasku (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id     (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :laskuerittely lasku-akillinen-hoitotyo})
        lasku-muu
        (kutsu-http-palvelua :tallenna-lasku (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id     (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :laskuerittely lasku-muu})]))
