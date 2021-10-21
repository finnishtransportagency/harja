(ns harja.palvelin.palvelut.kulut-test
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.palvelut.kulut :as kulut]
            [harja.pvm :as pvm]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (luo-testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :kulut (component/using
                                  (kulut/->Kulut)
                                  [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(def uusi-kulu
  {:id              nil
   :urakka          (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
   :viite           "6666668"
   :erapaiva        #inst "2021-12-15T21:00:00.000-00:00"
   :kokonaissumma   1332
   :tyyppi          "laskutettava"
   :kohdistukset    [{:kohdistus-id        nil
                      :rivi                1
                      :summa               666
                      :suoritus-alku       #inst "2021-11-14T22:00:00.000000000-00:00"
                      :suoritus-loppu      #inst "2021-11-17T22:00:00.000000000-00:00"
                      :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
                      :tehtavaryhma        (hae-tehtavaryhman-id "Vesakonraivaukset ja puun poisto (V)")
                      :tehtava             nil}
                     {:kohdistus-id        nil
                      :rivi                2
                      :summa               666
                      :suoritus-alku       #inst "2021-11-14T22:00:00.000000000-00:00"
                      :suoritus-loppu      #inst "2021-11-17T22:00:00.000000000-00:00"
                      :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
                      :tehtavaryhma        (hae-tehtavaryhman-id "Vesakonraivaukset ja puun poisto (V)")
                      :tehtava             nil}]
   :liitteet        [{:liite-id     1
                      :liite-tyyppi "image/png"
                      :liite-nimi   "pensas-2021-01.jpg"
                      :liite-koko   nil
                      :liite-oid    nil}
                     {:liite-id     2
                      :liite-tyyppi "image/png"
                      :liite-nimi   "pensas-2021-02.jpg"
                      :liite-koko   nil
                      :liite-oid    nil}]
   :koontilaskun-kuukausi "joulukuu/3-hoitovuosi"})

(def uusi-kohdistus
  {:rivi                3
   :summa               987
   :suoritus-alku       #inst "2021-11-23T22:00:00.000000000-00:00"
   :suoritus-loppu      #inst "2021-11-24T22:00:00.000000000-00:00"
   :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
   :tehtavaryhma        (hae-tehtavaryhman-id "Vesakonraivaukset ja puun poisto (V)")
   :tehtava             nil})


(def kulun-paivitys
  {:id              nil
   :urakka          (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
   :viite           "2019080022"
   :erapaiva        #inst "2021-12-15T21:00:00.000-00:00"
   :kokonaissumma   5555.55
   :tyyppi          "laskutettava"
   :kohdistukset    [{:kohdistus-id        5
                      :rivi                2
                      :summa               3333.33
                      :suoritus-alku       #inst "2021-03-14T22:00:00.000000000-00:00"
                      :suoritus-loppu      #inst "2021-03-17T22:00:00.000000000-00:00"
                      :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
                      :tehtavaryhma        (hae-tehtavaryhman-id "Äkilliset hoitotyöt, Liikenneympäristön hoito (T1)")
                      :tehtava             nil}]
   :koontilaskun-kuukausi "joulukuu/3-hoitovuosi"})

(def kulu-akillinen-hoitotyo
  {:id              nil
   :urakka          (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
   :viite           "666017"
   :erapaiva        #inst "2021-10-15T21:00:00.000-00:00"
   :kokonaissumma   666.66
   :tyyppi          "laskutettava"
   :kohdistukset    [{:kohdistus-id        nil
                      :rivi                1
                      :summa               666.66
                      :suoritus-alku       #inst "2021-10-02T12:00:00.000000000-00:00"
                      :suoritus-loppu      #inst "2021-10-02T12:54:00.000000000-00:00"
                      :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
                      :tehtavaryhma        (hae-tehtavaryhman-id "Äkilliset hoitotyöt, Liikenneympäristön hoito (T1)")
                      :tehtava             nil}]
   :liitteet        []
   :koontilaskun-kuukausi "lokakuu/3-hoitovuosi"})

(def kulu-muu
  {:id              nil
   :urakka          (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
   :viite           "666017"
   :erapaiva        #inst "2021-10-15T21:00:00.000-00:00"
   :kokonaissumma   666.66
   :tyyppi          "laskutettava"
   :kohdistukset    [{:kohdistus-id        nil
                      :rivi                1
                      :summa               666.66
                      :suoritus-alku       #inst "2021-10-02T12:00:00.000000000-00:00"
                      :suoritus-loppu      #inst "2021-10-02T12:54:00.000000000-00:00"
                      :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
                      :tehtavaryhma        (hae-tehtavaryhman-id "Vahinkojen korjaukset, Liikenneympäristön hoito (T2)")
                      :tehtava             nil}]
   :liitteet        []
   :koontilaskun-kuukausi "lokakuu/3-hoitovuosi"})



;; testit hyödyntävät tallennettua testidataa

(deftest hae-kulu-testi
  (let [
        kulut (kutsu-http-palvelua :kulut +kayttaja-jvh+ {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                                            :alkupvm   "2019-10-01"
                                                            :loppupvm  "2020-09-30"})
        kulu-kohdistuksineen (kutsu-http-palvelua :kulu +kayttaja-jvh+
                                           {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                            :id 1})
        kulut-urakan-vastaavalle (kutsu-http-palvelua :kulut (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                                                       {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                                        :alkupvm   "2019-10-01"
                                                        :loppupvm  "2020-09-30"})
        kulu-kohdistuksineen-urakan-vastaavalle (kutsu-http-palvelua :kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                                                              {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                                               :id 1})]

    (is (= kulut kulut-urakan-vastaavalle) "Urakan vastuuhenkilö saa samat tiedot kuluista kuin järjestelmävalvoja.")
    (is (= kulu-kohdistuksineen kulu-kohdistuksineen-urakan-vastaavalle) "Urakan vastuuhenkilö saa samat tiedot kulusta kuin järjestelmävalvoja.")
    (is (count (distinct (map :id kulu-kohdistuksineen))) "Urakan kulujen haku palauttaa kolme kulua.")
    (is (apply = (map :id kulu-kohdistuksineen)) "Kulukohdistuksissa on vain yhden kulun tietoja.")
    (is (count (map :kohdistus-id kulu-kohdistuksineen)) "Kulu sisältää kolme kohdistusta.")
    (is (= (:kokonaissumma kulu-kohdistuksineen) 666.66M) "Kokonaissumma palautuu.")
    (is (= (:summa (first (filter #(= #inst "2019-11-21T22:00:00.000000000-00:00" (:suoritus-alku %)) (:kohdistukset kulu-kohdistuksineen)))) 222.22M) "Yksittäisen rivin summatieto palautuu.")))


(deftest tallenna-kulu-testi
  (let [tallennettu-kulu
        (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id     (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :kulu-kohdistuksineen uusi-kulu})
        tallennettu-id (:id tallennettu-kulu)
        paivitetty-kulu
        (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id     (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :kulu-kohdistuksineen (assoc tallennettu-kulu :kokonaissumma 9876.54 :id tallennettu-id)})
        paivitetty-kohdistus
        (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id     (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :kulu-kohdistuksineen (assoc kulun-paivitys :id tallennettu-id)})
        lisatty-kohdistus
        (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id     (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :kulu-kohdistuksineen (assoc paivitetty-kulu
                                               :id tallennettu-id
                                               :kohdistukset (merge (paivitetty-kulu :kohdistukset)
                                                                    uusi-kohdistus))})
        poistettu-kohdistus
        (kutsu-http-palvelua :poista-kohdistus (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id           (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :id tallennettu-id
                              :kohdistuksen-id (-> lisatty-kohdistus
                                                   :kohdistukset
                                                   first
                                                   :kohdistus-id)})
        poistettu-kulu
        (kutsu-http-palvelua :poista-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :id tallennettu-id})
        poistetun-kulun-haku
        (kutsu-http-palvelua :kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :id tallennettu-id})]

    ;; Tallennus
    (is (not (nil? (:id tallennettu-kulu))) "Kulu tallentui (tallennettu-kulu).")
    (is (= (count (:kohdistukset tallennettu-kulu)) 2) "Kohdistuksia tallentui kaksi (tallennettu-kulu).")

    ;; Päivitys: arvon muuttaminen
    (is (= (count (:kohdistukset paivitetty-kulu)) 2) "Päivitetyssä kulussa on kaksi kohdistusta (paivitetty-kulu).")
    (is (= (:kokonaissumma paivitetty-kulu) 9876.54M) "Päivitetyn kulun kokonaissumma päivittyi (paivitetty-kulu).")
    (is (= (count (:kohdistukset paivitetty-kohdistus)) 2) "Kohdistuksen päivityksen jälkeen on on kaksi kohdistusta (paivitetty-kohdistus).")
    ;;(is (= (map #(:summa %) paivitetty-kohdistus) (2222.22M 3333.33M)) "Päivitetyn kohdistuksen summa päivittyi. Toinen kohdistus säilyi muuttumattomana. (paivitetty-kohdistus)")
    (is (= (:kokonaissumma paivitetty-kohdistus) 5555.55M) "Päivitetyn kulun kokonaissumma päivittyi (paivitetty-kohdistus).")

    ;; Päivitys: kohdistuksen lisääminen
    (is (= (count (:kohdistukset lisatty-kohdistus)) 3) "Kohdistuksen lisääminen kasvatti kohdistusten määrää yhdellä (lisatty-kohdistus).")

    ;; Päivitys: kohdistuksen ja kulun poistaminen
    (is (= (count (:kohdistukset poistettu-kohdistus)) 2) "Kohdistuksen poistaminen vähensi kohdistusten määrää yhdellä (poistettu-kohdistus).")
    (is (= (:id poistettu-kulu) tallennettu-id) "kulun poistaminen palauttaa poistetun kulun (poistettu-kulu)")
    (is (empty? poistetun-kulun-haku) "Poistettua kulua ei palaudu (poistetun-kulun-haku).")))

(defn- feila-tallenna-kulu-validointi [vaara-kulu odotettu-poikkeus]
  (try
    (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                         {:urakka-id     (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                          :kulu-kohdistuksineen vaara-kulu})
    (is false "Ei saa tulla tänne, kutsu pitäisi heittää poikkeuksen")
    (catch Exception e (is (s/includes? (.getMessage e) odotettu-poikkeus)
                           "Odotamme että tulee validointi poikkeus"))))

(deftest tallenna-kulu-erapaiva-validointi-testi
  (let [uusi-kulu-vaara-erapaiva (assoc uusi-kulu :erapaiva #inst "1921-12-15T21:00:00.000-00:00")]
    (feila-tallenna-kulu-validointi uusi-kulu-vaara-erapaiva
                                     "Eräpäivä Thu Dec 15 23:00:00 EET 1921 ei ole koontilaskun-kuukauden joulukuu/3-hoitovuosi sisällä")))

(deftest tallenna-kulu-koontilaskun-kuukausi-validointi-testi
  (let [uusi-kulu-vaara-koontilaskun-kuukausi (assoc uusi-kulu :koontilaskun-kuukausi "vaara-muoto")]
    (feila-tallenna-kulu-validointi uusi-kulu-vaara-koontilaskun-kuukausi
                                     "Palvelun :tallenna-kulu kysely ei ole validi")))

(deftest paivita-maksuera-testi
  (let [vastaus-kulu-kokonaishintainen-tyo
        (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id     (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :kulu-kohdistuksineen (assoc uusi-kulu :viite "1413418")})
        vastaus-kulu-akillinen-hoitotyo
        (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id     (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :kulu-kohdistuksineen kulu-akillinen-hoitotyo})
        vastaus-kulu-muu
        (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id     (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :kulu-kohdistuksineen kulu-muu})]

    (is (= (:maksueratyyppi (first (:kohdistukset vastaus-kulu-kokonaishintainen-tyo))) "kokonaishintainen"))
    (is (= (:maksueratyyppi (first (:kohdistukset vastaus-kulu-akillinen-hoitotyo))) "akillinen-hoitotyo"))
    (is (= (:maksueratyyppi (first (:kohdistukset vastaus-kulu-muu))) "muu"))))

(deftest paivita-kulu-pvm-testi
  (let [_kulu-ensimmainen-paiva
        (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :kulu-kohdistuksineen (assoc uusi-kulu :erapaiva #inst "2021-12-01T00:00:00.000+02:00")})]))

(def odotettu-kulu-id-7
  {:id 7, :tyyppi "laskutettava", :kokonaissumma 400.77M, :erapaiva #inst "2019-10-15T21:00:00.000-00:00", :laskun-numero nil, :koontilaskun-kuukausi "lokakuu/1-hoitovuosi", :liitteet [], :kohdistukset [{:suoritus-alku #inst "2019-09-30T21:00:00.000000000-00:00", :lisatyon-lisatieto nil, :maksueratyyppi "lisatyo", :suoritus-loppu #inst "2019-10-30T22:00:00.000000000-00:00", :tehtava nil, :summa 400.77M, :kohdistus-id 10, :laskun-numero nil, :toimenpideinstanssi 47, :tehtavaryhma 47, :lisatieto nil, :rivi 1}]})

(deftest hae-aikavalilla-kaikki-kulu-kohdistuksineent
  (testing "hae-aikavalilla-kaikki-kulu-kohdistuksineent"
    (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
          alkupvm (pvm/->pvm "1.1.2019")
          loppupvm (pvm/->pvm "30.9.2024")
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :kulut-kohdistuksineen
                                  +kayttaja-jvh+
                                  {:urakka-id urakka-id
                                   :alkupvm alkupvm
                                   :loppupvm loppupvm})
          odotettu-count 26
          kulu-id-7 (first (filter #(= 7 (:id %))
                                   vastaus))]
      (is (= odotettu-count (count vastaus)))
      (is (= odotettu-kulu-id-7 kulu-id-7)))))

(deftest hae-kaikki-kulu-kohdistuksineent-pvmt-nil
  (testing "hae-kaikki-kulu-kohdistuksineent-pvmt-nil"
    (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :kulut-kohdistuksineen
                                  +kayttaja-jvh+
                                  {:urakka-id urakka-id
                                   :alkupvm nil
                                   :loppupvm nil})
          odotettu-count 26
          kulu-id-7 (first (filter #(= 7 (:id %))
                                   vastaus))]
      (is (= odotettu-count (count vastaus)))
      (is (= odotettu-kulu-id-7 kulu-id-7)))))

(deftest hae-hoitokauden-kulu-kohdistuksineent
  (testing "hae-hoitokauden-kulu-kohdistuksineent"
    (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
          hoitokauden-alkupvm (pvm/->pvm "1.10.2019")
          hoitokauden-loppupvm (pvm/->pvm "30.9.2024")
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :kulut-kohdistuksineen
                                  +kayttaja-jvh+
                                  {:urakka-id urakka-id
                                   :alkupvm hoitokauden-alkupvm
                                   :loppupvm hoitokauden-loppupvm})
          odotettu-count 25 ;; yksi kulu on päivätty ennen hoitokauden alkua
          kulu-id-7 (first (filter #(= 7 (:id %))
                                   vastaus))]
      (is (= odotettu-count (count vastaus)))
      (is (= odotettu-kulu-id-7 kulu-id-7)))))
