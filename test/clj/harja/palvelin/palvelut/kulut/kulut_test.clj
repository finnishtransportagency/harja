(ns harja.palvelin.palvelut.kulut.kulut-test
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as tyokalut-virheet]
            [slingshot.slingshot :refer [try+]]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.palvelut.kulut.kulut :as kulut]
            [harja.kyselyt.valikatselmus :as valikatselmus-kyselyt]
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
                      :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
                      :tehtavaryhma        (hae-tehtavaryhman-id "Vesakonraivaukset ja puun poisto (V)")
                      :tehtava             nil
                      :tavoitehintainen :true
                      :tyyppi "hankintakulu"}
                     {:kohdistus-id        nil
                      :rivi                2
                      :summa               666
                      :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
                      :tehtavaryhma        (hae-tehtavaryhman-id "Vesakonraivaukset ja puun poisto (V)")
                      :tehtava             nil
                      :tavoitehintainen :true
                      :tyyppi "hankintakulu"}]
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
   :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
   :tehtavaryhma        (hae-tehtavaryhman-id "Vesakonraivaukset ja puun poisto (V)")
   :tehtava             nil
   :tavoitehintainen :true
   :tyyppi "hankintakulu"})


(def kulun-paivitys
  {:id              nil
   :urakka          (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
   :viite           "2019080022"
   :erapaiva        #inst "2021-12-15T21:00:00.000-00:00"
   :kokonaissumma   3999.33
   :tyyppi          "laskutettava"
   :kohdistukset    [{:kohdistus-id        nil
                       :rivi                1
                       :summa               666
                       :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
                       :tehtavaryhma        (hae-tehtavaryhman-id "Vesakonraivaukset ja puun poisto (V)")
                       :tehtava             nil
                      :tavoitehintainen :true
                      :tyyppi "hankintakulu"}
                     {:kohdistus-id        nil
                      :rivi                2
                      :summa               3333.33
                      :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
                      :tehtavaryhma        (hae-tehtavaryhman-id "Äkilliset hoitotyöt, Liikenneympäristön hoito (T1)")
                      :tehtava             nil
                      :tavoitehintainen :true
                      :tyyppi "hankintakulu"}]
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
                      :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
                      :tehtavaryhma        (hae-tehtavaryhman-id "Äkilliset hoitotyöt, Liikenneympäristön hoito (T1)")
                      :tehtava             nil
                      :tavoitehintainen :true
                      :tyyppi "hankintakulu"
                      :rahavaraus (hae-rahavaraus-nimella "Rahavaraus B - Äkilliset hoitotyöt")}]
   :liitteet        []
   :koontilaskun-kuukausi "lokakuu/3-hoitovuosi"})

(def kulu-rahavaraus
  {:id              nil
   :urakka          (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
   :viite           "666017"
   :erapaiva        #inst "2021-10-15T21:00:00.000-00:00"
   :kokonaissumma   666.66
   :tyyppi          "laskutettava"
   :kohdistukset    [{:kohdistus-id        nil
                      :rivi                1
                      :summa               666.66
                      :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
                      :tehtavaryhma        (hae-tehtavaryhman-id "Vahinkojen korjaukset, Liikenneympäristön hoito (T2)")
                      :tehtava             nil
                      :tavoitehintainen :true
                      :tyyppi "hankintakulu"
                      :rahavaraus (hae-rahavaraus-nimella "Rahavaraus C - Vahinkojen korjaukset")}]
   :liitteet        []
   :koontilaskun-kuukausi "lokakuu/3-hoitovuosi"})



(deftest kulu-formatteri-testi
  (let [munklattava-rakenne [{:id 1 :toimenpideinstanssi 1 :laskun-numero 666 :erapaiva (pvm/->pvm "11.10.2021") :summa 100}
                             {:id 2 :toimenpideinstanssi 1 :laskun-numero 667 :erapaiva (pvm/->pvm "10.10.2021") :summa 100}
                             {:id 3 :toimenpideinstanssi 2 :laskun-numero 667 :erapaiva (pvm/->pvm "10.10.2021") :summa 100}
                             {:id 4 :toimenpideinstanssi 3 :laskun-numero 667 :erapaiva (pvm/->pvm "10.10.2021") :summa 100}
                             {:id 5 :toimenpideinstanssi 3 :laskun-numero 667 :erapaiva (pvm/->pvm "10.10.2021") :summa 100}
                             {:id 6 :toimenpideinstanssi 3 :laskun-numero 665 :erapaiva (pvm/->pvm "12.09.2021") :summa 100}
                             {:id 7 :toimenpideinstanssi 6 :laskun-numero nil :erapaiva (pvm/->pvm "10.09.2021") :summa 100}
                             {:id 8 :toimenpideinstanssi 4 :laskun-numero 664 :erapaiva (pvm/->pvm "10.08.2021") :summa 100}
                             ]
        paha-rakenne [{:id 1 :toimenpideinstanssi 1 :laskun-numero 666 :erapaiva nil :summa 100}
                      {:id 2 :toimenpideinstanssi 1 :laskun-numero 667 :erapaiva (pvm/->pvm "10.10.2021") :summa 100}
                      {:id 3 :toimenpideinstanssi 2 :laskun-numero 667 :erapaiva (pvm/->pvm "10.10.2021") :summa 100}
                      {:id 4 :toimenpideinstanssi nil :laskun-numero 667 :erapaiva (pvm/->pvm "10.10.2021") :summa 100}
                      {:id 5 :toimenpideinstanssi 3 :laskun-numero 667 :erapaiva nil :summa nil}
                      {:id 6 :toimenpideinstanssi 3 :laskun-numero 665 :erapaiva (pvm/->pvm "12.09.2021") :summa 100}
                      {:id 7 :toimenpideinstanssi 6 :laskun-numero nil :erapaiva (pvm/->pvm "10.09.2021") :summa nil}
                      {:id 8 :toimenpideinstanssi nil :laskun-numero 664 :erapaiva (pvm/->pvm "10.08.2021") :summa 100}
                      ]
        odotettu-bad-case [[:pvm "2021/10" 300]                           
                           [:laskun-numero 667 300]
                           [:tpi 1 100
                            [{:id 2 :toimenpideinstanssi 1 :laskun-numero 667 
                              :erapaiva (pvm/->pvm "10.10.2021") :summa 100}]]
                           [:tpi 2 100
                            [{:id 3 :toimenpideinstanssi 2 :laskun-numero 667 
                              :erapaiva (pvm/->pvm "10.10.2021") :summa 100}]]
                           [:tpi nil 100
                            [{:id 4 :toimenpideinstanssi nil :laskun-numero 667 
                              :erapaiva (pvm/->pvm "10.10.2021") :summa 100}]]
                           [:pvm "2021/09" 100]
                           [:laskun-numero 665 100]
                           [:tpi 3 100
                            [{:id 6 :toimenpideinstanssi 3 :laskun-numero 665 
                              :erapaiva (pvm/->pvm "12.09.2021") :summa 100}]]
                           [:laskun-numero 0 0]
                           [:tpi 6 0
                            [{:id 7 :toimenpideinstanssi 6 :laskun-numero nil 
                              :erapaiva (pvm/->pvm "10.09.2021") :summa nil}]]
                           [:pvm "2021/08" 100]
                           [:laskun-numero 664 100]
                           [:tpi nil 100
                            [{:id 8 :toimenpideinstanssi nil :laskun-numero 664 
                              :erapaiva (pvm/->pvm "10.08.2021") :summa 100}]]
                           [:pvm nil 100]
                           [:laskun-numero 666 100]
                           [:tpi 1 100 [{:id 1 :toimenpideinstanssi 1 :laskun-numero 666 :erapaiva nil :summa 100}]]
                           [:laskun-numero 667 0]
                           [:tpi 3 0 [{:id 5 :toimenpideinstanssi 3 :laskun-numero 667 :erapaiva nil :summa nil}]]]
        odotettu-rakenne [[:pvm "2021/10" 500] ; :tagi, pvm, kokonaissumma
                          [:laskun-numero 666 100] ; :tagi, laskun-nro, kokonaissumma
                          [:tpi 1 100 ; :tagi, toimenpideinstanssin id, kokonaissumma
                           [{:id 1 :toimenpideinstanssi 1 :laskun-numero 666 
                             :erapaiva (pvm/->pvm "11.10.2021") :summa 100}]]
                          [:laskun-numero 667 400]
                          [:tpi 1 100
                           [{:id 2 :toimenpideinstanssi 1 :laskun-numero 667 
                             :erapaiva (pvm/->pvm "10.10.2021") :summa 100}]]
                          [:tpi 2 100
                           [{:id 3 :toimenpideinstanssi 2 :laskun-numero 667 
                             :erapaiva (pvm/->pvm "10.10.2021") :summa 100}]]
                          [:tpi 3 200
                           [{:id 4 :toimenpideinstanssi 3 :laskun-numero 667 
                             :erapaiva (pvm/->pvm "10.10.2021") :summa 100}
                            {:id 5 :toimenpideinstanssi 3 :laskun-numero 667 
                             :erapaiva (pvm/->pvm "10.10.2021") :summa 100}]]
                          [:pvm "2021/09" 200]
                          [:laskun-numero 665 100]
                          [:tpi 3 100
                           [{:id 6 :toimenpideinstanssi 3 :laskun-numero 665 
                             :erapaiva (pvm/->pvm "12.09.2021") :summa 100}]]
                          [:laskun-numero 0 100]
                          [:tpi 6 100
                           [{:id 7 :toimenpideinstanssi 6 :laskun-numero nil 
                             :erapaiva (pvm/->pvm "10.09.2021") :summa 100}]]
                          [:pvm "2021/08" 100]
                          [:laskun-numero 664 100]
                          [:tpi 4 100
                           [{:id 8 :toimenpideinstanssi 4 :laskun-numero 664 
                             :erapaiva (pvm/->pvm "10.08.2021") :summa 100}]]]
        odotettu-ryhmittely [["2021/10" 
                              {:rivit 
                               [[666 
                                 {:rivit 
                                  [[1 
                                    {:rivit [{:id 1 :toimenpideinstanssi 1 :laskun-numero 666 :erapaiva (pvm/->pvm "11.10.2021") :summa 100}]
                                     :summa 100}]]
                                  :summa 100}]
                                [667 {:rivit 
                                      [[1 {:rivit [{:id 2 :toimenpideinstanssi 1 :laskun-numero 667 :erapaiva (pvm/->pvm "10.10.2021") :summa 100}]
                                           :summa 100}]
                                       [2 {:rivit [{:id 3 :toimenpideinstanssi 2 :laskun-numero 667 :erapaiva (pvm/->pvm "10.10.2021") :summa 100}] 
                                           :summa 100}]
                                       [3 {:rivit [{:id 4 :toimenpideinstanssi 3 :laskun-numero 667 :erapaiva (pvm/->pvm "10.10.2021") :summa 100}
                                                   {:id 5 :toimenpideinstanssi 3 :laskun-numero 667 :erapaiva (pvm/->pvm "10.10.2021") :summa 100}] 
                                           :summa 200}]]
                                      :summa 400}]]                               
                               :summa 500}]
                             ["2021/09"
                              {:rivit 
                               [[665 
                                 {:rivit 
                                  [[3 
                                    {:rivit [{:id 6 :toimenpideinstanssi 3 :laskun-numero 665 :erapaiva (pvm/->pvm "12.09.2021") :summa 100}] 
                                     :summa 100}]]
                                  :summa 100}]
                                [0 {:rivit 
                                    [[6 {:rivit [{:id 7 :toimenpideinstanssi 6 :laskun-numero nil :erapaiva (pvm/->pvm "10.09.2021") :summa 100}]
                                         :summa 100}]]
                                    :summa 100}]]
                               :summa 200}]
                             ["2021/08"
                              {:rivit 
                               [[664 
                                 {:rivit 
                                  [[4 
                                    {:rivit [{:id 8 :toimenpideinstanssi 4 :laskun-numero 664 :erapaiva (pvm/->pvm "10.08.2021") 
                                              :summa 100}] 
                                     :summa 100}]] 
                                  :summa 100}]]
                               :summa 100}]]
        ryhmitelty-rakenne (kulut/ryhmittele-urakan-kulut munklattava-rakenne)
        valmis-rakenne (kulut/muodosta-naytettava-rakenne ryhmitelty-rakenne)
        bad-case (-> paha-rakenne kulut/ryhmittele-urakan-kulut kulut/muodosta-naytettava-rakenne)]
    (is (= odotettu-ryhmittely ryhmitelty-rakenne))
    (is (= odotettu-rakenne valmis-rakenne))
    (is (= odotettu-bad-case bad-case))))

;; testit hyödyntävät tallennettua testidataa

(deftest hae-kulu-testi
  (let [
        kulut (kutsu-http-palvelua :kulut +kayttaja-jvh+ {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                                            :alkupvm   "2019-10-01"
                                                            :loppupvm  "2020-09-30"})
        kulu-kohdistuksineen (kutsu-http-palvelua :hae-kulu +kayttaja-jvh+
                                           {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                            :id 3})
        kulut-urakan-vastaavalle (kutsu-http-palvelua :kulut (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                                                       {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                                        :alkupvm   "2019-10-01"
                                                        :loppupvm  "2020-09-30"})
        kulu-kohdistuksineen-urakan-vastaavalle (kutsu-http-palvelua :hae-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                                                              {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                                               :id 3})]

    (is (= kulut kulut-urakan-vastaavalle) "Urakan vastuuhenkilö saa samat tiedot kuluista kuin järjestelmävalvoja.")
    (is (= kulu-kohdistuksineen kulu-kohdistuksineen-urakan-vastaavalle) "Urakan vastuuhenkilö saa samat tiedot kulusta kuin järjestelmävalvoja.")
    (is (count (distinct (map :id kulu-kohdistuksineen))) "Urakan kulujen haku palauttaa kolme kulua.")
    (is (apply = (map :id kulu-kohdistuksineen)) "Kulukohdistuksissa on vain yhden kulun tietoja.")
    (is (count (map :kohdistus-id kulu-kohdistuksineen)) "Kulu sisältää kolme kohdistusta.")
    (is (= (:kokonaissumma kulu-kohdistuksineen) 666.66M) "Kokonaissumma palautuu.")
    (is (= (:summa (first (filter #(= 2 (:rivi %)) (:kohdistukset kulu-kohdistuksineen)))) 222.22M) "Yksittäisen rivin summatieto palautuu.")))


(deftest tallenna-kulu-testi
  (let [tallennettu-kulu
        (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id     (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :kulu-kohdistuksineen uusi-kulu})
        _ (println "tallennettu-kulu: " (pr-str tallennettu-kulu))
        tallennettu-id (:id tallennettu-kulu)
        paivitetty-kulu
        (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id     (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :kulu-kohdistuksineen (assoc tallennettu-kulu :lisatieto "lisätieto" :id tallennettu-id)})
        kohdistus-idt (map :kohdistus-id (:kohdistukset paivitetty-kulu))
        paivitetty-kohdistus
        (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id     (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :kulu-kohdistuksineen (-> (assoc kulun-paivitys :id tallennettu-id)
                                                      (assoc-in [:kulut 0 :kohdistus-id] (nth kohdistus-idt 0))
                                                      (assoc-in [:kulut 1 :kohdistus-id] (nth kohdistus-idt 1)))})
        lisatty-kohdistus
        (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id     (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :kulu-kohdistuksineen (assoc paivitetty-kohdistus
                                               :id tallennettu-id
                                               :kohdistukset (merge (:kohdistukset paivitetty-kohdistus)
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
        (kutsu-http-palvelua :hae-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :id tallennettu-id})]

    ;; Tallennus
    (is (not (nil? (:id tallennettu-kulu))) "Kulu tallentui (tallennettu-kulu).")
    (is (= (count (:kohdistukset tallennettu-kulu)) 2) "Kohdistuksia tallentui kaksi (tallennettu-kulu).")

    ;; Päivitys: arvon muuttaminen
    (is (= (count (:kohdistukset paivitetty-kulu)) 2) "Päivitetyssä kulussa on kaksi kohdistusta (paivitetty-kulu).")
    (is (= (:lisatieto paivitetty-kulu) "lisätieto") "Päivitetyn kulun lisätieto päivittyi (paivitetty-kulu).")
    (is (= (count (:kohdistukset paivitetty-kohdistus)) 2) "Kohdistuksen päivityksen jälkeen on on kaksi kohdistusta (paivitetty-kohdistus).")
    ;;(is (= (map #(:summa %) paivitetty-kohdistus) (2222.22M 3333.33M)) "Päivitetyn kohdistuksen summa päivittyi. Toinen kohdistus säilyi muuttumattomana. (paivitetty-kohdistus)")
    (is (= (:kokonaissumma paivitetty-kohdistus) 3999.33M) "Päivitetyn kulun kokonaissumma päivittyi (paivitetty-kohdistus).")

    ;; Päivitys: kohdistuksen lisääminen
    (is (= (count (:kohdistukset lisatty-kohdistus)) 3) "Kohdistuksen lisääminen kasvatti kohdistusten määrää yhdellä (lisatty-kohdistus).")

    ;; Päivitys: kohdistuksen ja kulun poistaminen
    (is (= (count (:kohdistukset poistettu-kohdistus)) 2) "Kohdistuksen poistaminen vähensi kohdistusten määrää yhdellä (poistettu-kohdistus).")
    (is (= (:id poistettu-kulu) tallennettu-id) "kulun poistaminen palauttaa poistetun kulun (poistettu-kulu)")
    (is (empty? poistetun-kulun-haku) "Poistettua kulua ei palaudu (poistetun-kulun-haku).")))

(defn- feilaa-tallenna-kulu-validointi [vaara-kulu odotettu-poikkeus]
  (try
    (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                         {:urakka-id     (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                          :kulu-kohdistuksineen vaara-kulu})
    (is false "Ei saa tulla tänne, kutsu pitäisi heittää poikkeuksen")
    (catch Exception e (is (s/includes? (.getMessage e) odotettu-poikkeus)
                           "Odotamme että tulee validointi poikkeus"))))

(deftest tallenna-kulu-erapaiva-validointi-testi
  (let [uusi-kulu-vaara-erapaiva (assoc uusi-kulu :erapaiva #inst "1921-12-15T21:00:00.000-00:00")]
    (feilaa-tallenna-kulu-validointi uusi-kulu-vaara-erapaiva
                                     "Eräpäivä Thu Dec 15 23:00:00 EET 1921 ei ole koontilaskun-kuukauden joulukuu/3-hoitovuosi sisällä")))

(deftest tallenna-kulu-koontilaskun-kuukausi-validointi-testi
  (let [uusi-kulu-vaara-koontilaskun-kuukausi (assoc uusi-kulu :koontilaskun-kuukausi "vaara-muoto")]
    (feilaa-tallenna-kulu-validointi uusi-kulu-vaara-koontilaskun-kuukausi
                                     "Palvelun :tallenna-kulu kysely ei ole validi")))

(deftest tallenna-kulu-toimii-testi
  (let [_kulu-ensimmainen-paiva
        (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
          {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
           :kulu-kohdistuksineen (assoc uusi-kulu :erapaiva #inst "2021-12-01T00:00:00.000+02:00")})]))

(deftest paivita-kulua-eri-erapaivalla-ennen-valikatselmusta
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        uusi-kulu-laskun-numerolla (assoc uusi-kulu :laskun-numero "1233333")
        ;; Tallenna alkuperäinen kulu
        alkuperainen (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                       {:urakka-id urakka-id
                        :kulu-kohdistuksineen uusi-kulu-laskun-numerolla})
        uusi-kulu-laskun-numerolla-eri-paiva (assoc uusi-kulu-laskun-numerolla
                                               :erapaiva #inst "2021-09-30T21:00:00.000-00:00"
                                               :koontilaskun-kuukausi "lokakuu/3-hoitovuosi")
        paivitetty (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                     {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                      :kulu-kohdistuksineen uusi-kulu-laskun-numerolla-eri-paiva})]
    (is (= (dissoc alkuperainen :id :kohdistukset :erapaiva :koontilaskun-kuukausi)
          (dissoc paivitetty :id :kohdistukset :erapaiva :koontilaskun-kuukausi))
      "Erapäivä muutetaan")
    (is (not= (:erapaiva alkuperainen) (:erapaiva paivitetty)) "Erapäivä muutetaan")
    (is (not= (:koontilaskun-kuukausi alkuperainen) (:koontilaskun-kuukausi paivitetty)) "Erapäivä muutetaan")))

(deftest saako-kulua-tallentaa?
  (let [erapaiva #inst "2020-09-30T23:00:00.000-00:00"
        urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        vanha-erapaiva nil
        ;; Feikkaa, että välikatselmus on pidetty
        ei-saa-koska-valikatselmus-pidetty (with-redefs [valikatselmus-kyselyt/onko-valikatselmus-pidetty? (fn [_ _] true)]
                (kulut/tarkista-saako-kulua-tallentaa (:db jarjestelma) urakka-id erapaiva vanha-erapaiva))
        ;; Feikkaa, että välikatselmusta ei ole vielä ehditty pitää
        saa-koska-valikatselmus-pitamatta (with-redefs [valikatselmus-kyselyt/onko-valikatselmus-pidetty? (fn [_ _] false)]
                                             (kulut/tarkista-saako-kulua-tallentaa (:db jarjestelma) urakka-id erapaiva vanha-erapaiva))]
    (is (= false ei-saa-koska-valikatselmus-pidetty))
    (is (= true saa-koska-valikatselmus-pitamatta))))

(deftest paivita-kulua-eri-erapaivalla-valikatselmuksen-jalkeen
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        uusi-kulu-laskun-numerolla (assoc uusi-kulu :laskun-numero "1233333")
        ;; Tallenna alkuperäinen kulu
        tallennettu-kulu (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                       {:urakka-id urakka-id
                        :kulu-kohdistuksineen uusi-kulu-laskun-numerolla})
        ;; Lisätään hoitokauden-alkuvuodelle 2020 uusi välikatselmus, jotta päivitys ei varmasti on
        _ (u (str "INSERT INTO urakka_paatos
                  (\"urakka-id\", luotu, \"luoja-id\", \"muokkaaja-id\", tyyppi, siirto, \"tilaajan-maksu\",
                  \"urakoitsijan-maksu\", \"hoitokauden-alkuvuosi\" ) VALUES
                  (" urakka-id ", NOW(), " (:id +kayttaja-jvh+) ", " (:id +kayttaja-jvh+) ", 'tavoitehinnan-alitus'::paatoksen_tyyppi,
        100000, 0, 0, 2020 );"))
        muokattu-kulu-eri-hoitokausi (assoc tallennettu-kulu
                                       :erapaiva #inst "2021-09-29T21:00:00.000-00:00"
                                       :koontilaskun-kuukausi "syyskuu/2-hoitovuosi")]

    (is (thrown? Exception (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :kulu-kohdistuksineen muokattu-kulu-eri-hoitokausi})))))

(deftest paivita-valikatselmuksen-jalkeen
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        uusi-kulu-laskun-numerolla (assoc uusi-kulu :laskun-numero "1234567")
        ;; Tallenna alkuperäinen kulu - Menee hoitokaudelle 2021
        tallennettu-kulu (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
            {:urakka-id urakka-id
             :kulu-kohdistuksineen uusi-kulu-laskun-numerolla})
        ;; Lisätään hoitokauden-alkuvuodelle 2021 uusi välikatselmus, jotta päivitys ei varmasti onnistu
        _ (u (str "INSERT INTO urakka_paatos
                  (\"urakka-id\", luotu, \"luoja-id\", \"muokkaaja-id\", tyyppi, siirto, \"tilaajan-maksu\",
                  \"urakoitsijan-maksu\", \"hoitokauden-alkuvuosi\" ) VALUES
                  (" urakka-id ", NOW(), " (:id +kayttaja-jvh+) ", " (:id +kayttaja-jvh+) ", 'tavoitehinnan-alitus'::paatoksen_tyyppi,
        1000, 0, 0, 2021 );"))
        ;; Ja koitetaan siirtää kulu seuraavalle hoitokaudelle, jolla ei ole välikatselmusta tehtynä
        muokattu-kulu-eri-hoitokausi (assoc tallennettu-kulu
                                       :erapaiva #inst "2022-09-29T21:00:00.000-00:00"
                                       :koontilaskun-kuukausi "syyskuu/3-hoitovuosi")]

    ;; Tallennus ei saa onnistua, koska välikatselmus on pidetty ja hoitoivuosi 2021 "lukittu"
    (is (thrown? Exception (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                             {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                              :kulu-kohdistuksineen muokattu-kulu-eri-hoitokausi})))))

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
                              :kulu-kohdistuksineen kulu-rahavaraus})]

    (is (= (:maksueratyyppi (first (:kohdistukset vastaus-kulu-kokonaishintainen-tyo))) "kokonaishintainen"))
    (is (= (:maksueratyyppi (first (:kohdistukset vastaus-kulu-akillinen-hoitotyo))) "akillinen-hoitotyo"))
    (is (= (:maksueratyyppi (first (:kohdistukset vastaus-kulu-muu))) "muu"))))

(defn riisu-kilkkeet 
  [kaikki rivi]
  (if (not= :tpi (first rivi))
    kaikki
    (let [[_ _ _ rivit] rivi] 
      (apply conj kaikki rivit))))

(def odotettu-kulu_kohdistus-id-9
  {:maksuera-numero 94 :id 9, :tyyppi "lisatyo",
   :kokonaissumma 400.77M, :erapaiva #inst "2019-10-15T21:00:00.000-00:00",
   :laskun-numero nil, :koontilaskun-kuukausi "lokakuu/1-hoitovuosi",
   :liitteet [], :lisatyon-lisatieto nil, :maksueratyyppi "lisatyo",
   :summa 400.77M, :kohdistus-id 12,
   :rahavaraus nil
   :toimenpideinstanssi (ffirst (q "SELECT id FROM toimenpideinstanssi WHERE nimi = 'Oulu MHU Soratien hoito TP'")),
   :tehtavaryhma 15, :lisatieto nil, :rivi 1})

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
          vastaus (reduce riisu-kilkkeet [] vastaus)
          odotettu-count 31
          kulu-id-9 (first (filter #(= 9 (:id %))
                                   vastaus))]
      (is (= odotettu-count (count vastaus)))
      (is (= odotettu-kulu_kohdistus-id-9 kulu-id-9)))))

(deftest hae-kaikki-kulu-kohdistuksineent-pvmt-nil
  (testing "hae-kaikki-kulu-kohdistuksineent-pvmt-nil"
    (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :kulut-kohdistuksineen
                                  +kayttaja-jvh+
                                  {:urakka-id urakka-id
                                   :alkupvm nil
                                   :loppupvm nil})
          vastaus (reduce riisu-kilkkeet [] vastaus)
          odotettu-count 31
          kulu-id-9 (first (filter #(= 9 (:id %))
                                   vastaus))]
      (is (= odotettu-count (count vastaus)))
      (is (= odotettu-kulu_kohdistus-id-9 kulu-id-9)))))

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
          vastaus (reduce riisu-kilkkeet [] vastaus)
          odotettu-count 30 ;; yksi kulu on päivätty ennen hoitokauden alkua
          kulu-id-9 (first (filter #(= 9 (:id %))
                                   vastaus))]
      (is (= odotettu-count (count vastaus)))
      (is (= odotettu-kulu_kohdistus-id-9 kulu-id-9)))))

(deftest tallenna-kulu-vaaralla-tehtavaryhmalla
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        ;; Päivitetään testiä varten ykden tehtäväryhmän voimassaolo niin, että se ei ole voimassa
        ;; Käytetään tehtäväryhmää: Talvihoito (A)
        tehtavaryhmaid (:id (first (q-map "SELECT id FROM tehtavaryhma WHERE nimi = 'Talvihoito (A)'")))
        talvihoito-toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "23104")
        _ (u (format "UPDATE tehtavaryhma SET voimassaolo_alkuvuosi = '2000', voimassaolo_loppuvuosi = '2001' WHERE id = %s;" tehtavaryhmaid))
        virhe  (try+ (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
                         {:urakka-id urakka-id
                          :kulu-kohdistuksineen (-> uusi-kulu
                                                  (assoc :erapaiva #inst "2021-12-01T00:00:00.000+02:00")
                                                  (assoc-in [:kohdistukset 0 :tehtavaryhma] tehtavaryhmaid)
                                                  (assoc-in [:kohdistukset 0 :toimenpideinstanssi] talvihoito-toimenpideinstanssi-id)
                                                  (assoc-in [:kohdistukset 1 :tehtavaryhma] tehtavaryhmaid)
                                                  (assoc-in [:kohdistukset 1 :toimenpideinstanssi] talvihoito-toimenpideinstanssi-id))})
                   (catch [:type tyokalut-virheet/+viallinen-kutsu+] {:keys [virheet]}
                     (:virhe (first virheet)))
                   (catch Exception e
                     (.getMessage e)))]

    (is (= "Kululle valittu tehtäväryhmä: 1, joka ei ole voimassa. Tehtäväryhmän voimassaolovuodet 2000 - 2001" virhe))))

(deftest koontilaskun-kuukausi->kuukausi-toimii
  (is (= 1 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "tammikuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "tammikuu ei muuttunut 1:ksi")
  (is (= 2 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "helmikuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "helmikuu ei muuttunut 2:ksi")
  (is (= 3 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "maaliskuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "maaliskuu ei muuttunut 3:ksi")
  (is (= 4 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "huhtikuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "huhtikuu ei muuttunut 4:ksi")
  (is (= 5 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "toukokuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "toukokuu ei muuttunut 5:ksi")
  (is (= 6 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "kesäkuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "kesäkuu ei muuttunut 6:ksi")
  (is (= 7 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "heinäkuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "heinäkuu ei muuttunut 7:ksi")
  (is (= 8 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "elokuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "elokuu ei muuttunut 8:ksi")
  (is (= 9 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "syyskuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "syyskuu ei muuttunut 9:ksi")
  (is (= 10 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "lokakuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "lokakuu ei muuttunut 10:ksi")
  (is (= 11 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "marraskuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "marraskuu ei muuttunut 11:ksi")
  (is (= 12 (harja.domain.kulut/koontilaskun-kuukausi->kuukausi "joulukuu/1-hoitovuosi" (pvm/->pvm "1.10.2022") (pvm/->pvm "30.9.2024"))) "joulukuu ei muuttunut 12:ksi"))

(deftest koontilaskun-kuukausi->vuosi-toimii
  (is (= 2023 (harja.domain.kulut/koontilaskun-kuukausi->vuosi "lokakuu/1-hoitovuosi" (pvm/->pvm "1.10.2023") (pvm/->pvm "30.9.2028"))) "vuosi ei muuttunut 2023:ksi")
  (is (= 2024 (harja.domain.kulut/koontilaskun-kuukausi->vuosi "marraskuu/2-hoitovuosi" (pvm/->pvm "1.10.2023") (pvm/->pvm "30.9.2028"))) "vuosi ei muuttunut 2024:ksi")
  (is (= 2025 (harja.domain.kulut/koontilaskun-kuukausi->vuosi "maaliskuu/2-hoitovuosi" (pvm/->pvm "1.10.2023") (pvm/->pvm "30.9.2028"))) "vuosi ei muuttunut 2025:ksi")
  (is (= 2025 (harja.domain.kulut/koontilaskun-kuukausi->vuosi "lokakuu/3-hoitovuosi" (pvm/->pvm "1.10.2023") (pvm/->pvm "30.9.2028"))) "vuosi ei muuttunut 2025:ksi")
  (is (= 2026 (harja.domain.kulut/koontilaskun-kuukausi->vuosi "lokakuu/4-hoitovuosi" (pvm/->pvm "1.10.2023") (pvm/->pvm "30.9.2028"))) "vuosi ei muuttunut 2026:ksi")
  (is (= 2027 (harja.domain.kulut/koontilaskun-kuukausi->vuosi "huhtikuu/4-hoitovuosi" (pvm/->pvm "1.10.2023") (pvm/->pvm "30.9.2028"))) "vuosi ei muuttunut 2026:ksi")
  (is (= 2028 (harja.domain.kulut/koontilaskun-kuukausi->vuosi "toukokuu/5-hoitovuosi" (pvm/->pvm "1.10.2023") (pvm/->pvm "30.9.2028"))) "vuosi ei muuttunut 2027:ksi"))
