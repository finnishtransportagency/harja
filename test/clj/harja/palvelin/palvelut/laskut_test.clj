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


(def lasku-ja-erittelyt
      {:lasku-id      nil
       :viite         "6666668"
       :erapaiva      #inst "2021-12-15T21:00:00.000-00:00"
       :kokonaissumma 1332
       :tyyppi        :laskutettava
       :kohdistukset  [{:kohdistus-id        nil
                        :summa               666
                        :suorittaja-nimi     "Kaarinan Kadunkiillotus Oy"
                        :suorittaja-id       1
                        :suoritus-alku       #inst "2021-11-14T22:00:00.000000000-00:00"
                        :suoritus-loppu      #inst "2021-11-17T22:00:00.000000000-00:00"
                        :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
                        :tehtavaryhma        (hae-tehtavaryhman-id "Viheralueiden hoito")
                        :tehtava             nil}
                       {:kohdistus-id        nil
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

(def lasku-akillinen-hoitotyo
      {:lasku-id      nil
       :viite         "666017"
       :erapaiva      #inst "2021-10-15T21:00:00.000-00:00"
       :kokonaissumma 666.66
       :tyyppi        :laskutettava
       :kohdistukset  [{:kohdistus-id        nil
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
      {:lasku-id      nil
       :viite         "666017"
       :erapaiva      #inst "2021-10-15T21:00:00.000-00:00"
       :kokonaissumma 666.66
       :tyyppi        :laskutettava
       :kohdistukset  [{:kohdistus-id        nil
                        :summa               666.66
                        :suorittaja-nimi     "Kaarinan Kadunkiillotus Oy"
                        :suorittaja-id       nil
                        :suoritus-alku       #inst "2021-10-02T12:00:00.000000000-00:00"
                        :suoritus-loppu      #inst "2021-10-02T12:54:00.000000000-00:00"
                        :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
                        :tehtavaryhma        (hae-tehtavaryhman-id "Kolmannen osapuolen vahinkojen korjaukset")
                        :tehtava             nil}]
       :liitteet      []})

(def uusi-kohdistus
      {:kohdistus-id        nil
       :summa               333
       :suorittaja-nimi     "Kaarinan Kadunkiillotus Oy"
       :suorittaja-id       1
       :suoritus-alku       #inst "2021-11-23T22:00:00.000000000-00:00"
       :suoritus-loppu      #inst "2021-11-24T22:00:00.000000000-00:00"
       :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
       :tehtavaryhma        (hae-tehtavaryhman-id "Vesakon raivaus ja runkopuun poisto")
       :tehtava             nil})


;; testit hyödyntävät tallennettua testidataa
(deftest hae-lasku-testi
  (let [laskut (kutsu-http-palvelua :laskut +kayttaja-jvh+
                                    {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                     :alkupvm   "2019-10-01"
                                     :loppupvm  "2019-10-01"})
        laskuerittely (kutsu-http-palvelua :lasku +kayttaja-jvh+
                                           {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                            :viite     "2019080019"})
        laskut-urakan-vastaavalle (kutsu-http-palvelua :laskut (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                                                       {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                                        :alkupvm   "2019-10-01"
                                                        :loppupvm  "2019-10-01"})
        laskuerittely-urakan-vastaavalle (kutsu-http-palvelua :lasku (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                                                              {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                                               :viite     "2019080019"})]

    (is (= laskut laskut-urakan-vastaavalle) "Urakan vastuuhenkilö saa samat tiedot laskuista kuin järjestelmävalvoja.")
    (is (= laskuerittely laskuerittely-urakan-vastaavalle) "Urakan vastuuhenkilö saa samat tiedot laskusta kuin järjestelmävalvoja.")
    (is (count (distinct (map :laskun-id laskuerittely))) "Urakan laskujen haku palauttaa kolme laskua.")
    (is (apply = (map :laskun-id laskuerittely)) "Laskuerittelyssä on vain yhden laskun tietoja.")
    (is (count (map :kohdistus-id laskuerittely)) "Laskun erittely sisältää kolme kohdistusta.")
    (is (= (:suorittaja-nimi (first laskuerittely)) "Kaarinan Kadunkiillotus Oy") "Aliurakoitsijan nimi palautuu.")
    (is (= (:kokonaissumma (first laskuerittely)) 666.66M) "Kokonaissumma palautuu.")
    (is (= (:summa (first (filter #(= #inst "2019-11-21T22:00:00.000000000-00:00" (:suoritus-alku %)) laskuerittely))) 222.22M) "Yksittäisen rivin summatieto palautuu.")))




(deftest tallenna-lasku-testi
  (let [laskut (kutsu-http-palvelua :laskut +kayttaja-jvh+
                                    {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                     :alkupvm   "2019-10-01"
                                     :loppupvm  "2019-10-01"})
        laskuerittely (kutsu-http-palvelua :lasku +kayttaja-jvh+
                                           {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                            :viite     "2019080019"})
        laskut-urakan-vastaavalle (kutsu-http-palvelua :laskut (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                                                       {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                                        :alkupvm   "2019-10-01"
                                                        :loppupvm  "2019-10-01"})
        laskuerittely-urakan-vastaavalle (kutsu-http-palvelua :lasku (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                                                              {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                                               :viite     "2019080019"})]

    (is (= laskut laskut-urakan-vastaavalle) "Urakan vastuuhenkilö saa samat tiedot laskuista kuin järjestelmävalvoja.")
    (is (= laskuerittely laskuerittely-urakan-vastaavalle) "Urakan vastuuhenkilö saa samat tiedot laskusta kuin järjestelmävalvoja.")
    (is (count (distinct (map :laskun-id laskuerittely))) "Urakan laskujen haku palauttaa kolme laskua.")
    (is (apply = (map :laskun-id laskuerittely)) "Laskuerittelyssä on vain yhden laskun tietoja.")
    (is (count (map :kohdistus-id laskuerittely)) "Laskun erittely sisältää kolme kohdistusta.")
    (is (= (:suorittaja-nimi (first laskuerittely)) "Kaarinan Kadunkiillotus Oy") "Aliurakoitsijan nimi palautuu.")
    (is (= (:kokonaissumma (first laskuerittely)) 666.66) "Kokonaissumma palautuu.")
    (is (= (:summa (filter #(= #inst "2019-11-21T22:00:00.000000000-00:00" (:suoritus-alku %)) laskuerittely)) 222.22) "Yksittäisen rivin summatieto palautuu.")

    (println "LLL " laskut)
    (println "L " (filter #(= #inst "2019-11-21T22:00:00.000000000-00:00" (:suoritus-alku %)) laskuerittely))

    ))
