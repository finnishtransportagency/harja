(ns harja.palvelin.palvelut.laadunseuranta-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.laadunseuranta :as ls]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                       :db (tietokanta/luo-tietokanta testitietokanta)
                       :http-palvelin (testi-http-palvelin)
                       :karttakuvat (component/using
                                     (karttakuvat/luo-karttakuvat)
                                     [:http-palvelin :db])
                       :laadunseuranta (component/using
                                        (ls/->Laadunseuranta)
                                        [:http-palvelin :db :karttakuvat])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(def soratietarkastus                                       ;; soratietarkastus
  {:uusi?          true
   :aika           #inst "2006-07-06T09:43:00.000-00:00"
   :tarkastaja     "Jalmari Järjestelmävastuuhenkilö"
   :sijainti       nil
   :tr             {:alkuosa 2, :numero 1, :alkuetaisyys 3, :loppuetaisyys 5, :loppuosa 4}
   :tyyppi         :soratie
   :soratiemittaus {:polyavyys     4
                    :hoitoluokka   1
                    :sivukaltevuus 5
                    :tasaisuus     1
                    :kiinteys      3}
   :havainnot     "kuvaus tähän"
   :laadunalitus true})

(use-fixtures :once jarjestelma-fixture)

(deftest tallenna-ja-paivita-soratietarkastus
  (let [urakka-id (hae-oulun-alueurakan-2005-2012-id)
        kuvaus (str "kuvaus nyt " (System/currentTimeMillis))
        soratietarkastus (assoc-in soratietarkastus [:havainnot] kuvaus)
        hae-tarkastukset #(kutsu-http-palvelua :hae-urakan-tarkastukset +kayttaja-jvh+
                                               {:urakka-id urakka-id
                                                :alkupvm   #inst "2005-10-01T00:00:00.000-00:00"
                                                :loppupvm  #inst "2006-09-30T00:00:00.000-00:00"
                                                :tienumero %
                                                :vain-laadunalitukset? false})
        tarkastuksia-ennen-kaikki (count (hae-tarkastukset nil))
        tarkastuksia-ennen-tie1 (count (hae-tarkastukset 1))
        tarkastuksia-ennen-tie2 (count (hae-tarkastukset 2))
        tarkastus-id (atom nil)]

    (testing "Soratietarkastuksen tallennus"
      (let [vastaus (kutsu-http-palvelua :tallenna-tarkastus +kayttaja-jvh+
                                         {:urakka-id urakka-id
                                          :tarkastus soratietarkastus})
            id (:id vastaus)]

        (is (number? id) "Tallennus palauttaa uuden id:n")

        ;; kaikki ja tie 1 listauksissa määrä kasvanut yhdellä
        (is (= (count (hae-tarkastukset nil)) (inc tarkastuksia-ennen-kaikki)))

        (let [listaus-tie1 (hae-tarkastukset 1)]
          (is (= (count listaus-tie1) (inc tarkastuksia-ennen-tie1)))
          (is (= :soratie
                 (:tyyppi (first (filter #(= (:id %) id) listaus-tie1))))))


        ;; tie 2 tarkastusmäärä ei ole kasvanut
        (is (= (count (hae-tarkastukset 2)) tarkastuksia-ennen-tie2))

        (reset! tarkastus-id id)))

    (testing "Tarkastuksen haku ja muokkaus"
      (let [tarkastus (kutsu-http-palvelua :hae-tarkastus +kayttaja-jvh+
                                           {:urakka-id    urakka-id
                                            :tarkastus-id @tarkastus-id})]
        (is (= kuvaus (:havainnot tarkastus)))

        (testing "Muokataan tarkastusta"
          (let [muokattu-tarkastus (kutsu-http-palvelua :tallenna-tarkastus +kayttaja-jvh+
                                                        {:urakka-id urakka-id
                                                         :tarkastus (-> tarkastus
                                                                        (assoc-in [:soratiemittaus :tasaisuus] 5)
                                                                        (assoc-in [:havainnot] "MUOKATTU KUVAUS"))})]

            ;; id on edelleen sama
            (is (= (:id muokattu-tarkastus) @tarkastus-id))

            ;; muokatut kentät tallentuivat
            (is (= "MUOKATTU KUVAUS" (get-in muokattu-tarkastus [:havainnot])))
            (is (= 5 (get-in muokattu-tarkastus [:soratiemittaus :tasaisuus])))))))))
; FIXME Siivoa tallennettu data

(deftest tallenna-laatupoikkeama
  (let [laatupoikkeama {:yllapitokohde nil
                        :sijainti {:type :point
                                   :coordinates [382554.0523636384 6675978.549765582]}
                        :kuvaus "Kuvaus"
                        :aika #inst "2016-09-15T09:00:01.000-00:00"
                        :tr {:alkuosa 1
                             :numero 1
                             :alkuetaisyys 1
                             :loppuetaisyys 2
                             :loppuosa 2}
                        :urakka (hae-oulun-alueurakan-2014-2019-id)
                        :sanktiot nil
                        :tekija :tilaaja
                        :kohde "Kohde"}]

    (testing "Laatupoikkeaman tallennus"
      (let [vastaus (kutsu-http-palvelua :tallenna-laatupoikkeama
                                         +kayttaja-jvh+
                                         laatupoikkeama)
            id (:id vastaus)]

        (is (number? id) "Tallennus palauttaa uuden id:n")))))

(deftest tallenna-suorasanktio-paallystysurakassa-sakko-ja-bonus
  (let [perustelu "ABC kissa kävelee"
        perintapvm (pvm/->pvm-aika "3.1.2017 22:00:00")
        sanktiorunko {:suorasanktio true
                      :toimenpideinstanssi (hae-muhoksen-paallystysurakan-tpi-id)
                      :perintapvm perintapvm
                      :vakiofraasi :laadunvalvontaan-liittyvien-mittausten-ym-toimien-laiminlyonnit}
        sakko (merge sanktiorunko {:laji :yllapidon_sakko :summa 1234})
        bonus (merge sanktiorunko {:laji :yllapidon_bonus :summa -4321})
        muistutus (merge sanktiorunko {:laji :yllapidon_muistutus :summa nil})
        laatupoikkeama {:tekijanimi    "Järjestelmä Vastaava"
                        :paatos        {:paatos "sanktio", :kasittelyaika (pvm/->pvm-aika "2.1.2017 22:00:00"), :kasittelytapa :kommentit, :perustelu perustelu}
                        :aika          (pvm/->pvm-aika "1.1.2017 08:00:00"), :urakka (hae-muhoksen-paallystysurakan-id),
                        :yllapitokohde (hae-muhoksen-paallystysurakan-testikohteen-id)}
        hk-alkupvm (pvm/->pvm "1.1.2017")
        hk-loppupvm (pvm/->pvm "31.12.2017")]
    ;user sanktiorunko laatupoikkeama urakka [hk-alkupvm hk-loppupvm]
    (testing "Päällystysurakan suorasanktion tallennus"
      (let [sanktiot-sakon-jalkeen (kutsu-http-palvelua
                                          :tallenna-suorasanktio
                                          +kayttaja-jvh+
                                          {:sanktio        sakko
                                           :laatupoikkeama laatupoikkeama
                                           :hoitokausi     [hk-alkupvm hk-loppupvm]})
            sanktiot-bonuksen-jalkeen (kutsu-http-palvelua
                                     :tallenna-suorasanktio
                                     +kayttaja-jvh+
                                     {:sanktio        bonus
                                      :laatupoikkeama laatupoikkeama
                                      :hoitokausi     [hk-alkupvm hk-loppupvm]})
            sanktiot-muistutuksen-jalkeen (kutsu-http-palvelua
                                            :tallenna-suorasanktio
                                            +kayttaja-jvh+
                                            {:sanktio        muistutus
                                             :laatupoikkeama laatupoikkeama
                                             :hoitokausi     [hk-alkupvm hk-loppupvm]})
            lisatty-sakko (first (filter #(= 1234.0 (:summa %)) sanktiot-sakon-jalkeen))
            lisatty-bonus (first (filter #(= -4321.0 (:summa %)) sanktiot-bonuksen-jalkeen))
            lisatty-muistutus (first (filter #(and (= nil (:summa %))) sanktiot-muistutuksen-jalkeen))]
        (is (number? (:id lisatty-sakko)) "Tallennus palauttaa uuden id:n")
        (is (= :yllapidon_sakko (:laji lisatty-sakko)) "Päällystysurakan bonuksen oikea sanktiolaji")
        (is (= "Ylläpidon sakko" (:nimi (:tyyppi lisatty-sakko))) "Päällystysurakan sakon oikea sanktiotyyppi")
        (is (= :yllapidon_bonus (:laji lisatty-bonus)) "Päällystysurakan bonuksen oikea sanktiolaji")
        (is (= "Ylläpidon bonus" (:nimi (:tyyppi lisatty-bonus))) "Päällystysurakan bonuksen oikea sanktiotyyppi")
        (is (= "Ylläpidon muistutus" (:nimi (:tyyppi lisatty-muistutus))) "Päällystysurakan muistutuksen oikea sanktiotyyppi")
        (is (= 1234.0 (:summa lisatty-sakko)) "Päällystysurakan sakon oikea summa")
        (is (= -4321.0 (:summa lisatty-bonus)) "Päällystysurakan bonuksen oikea summa")
        (is (= nil (:summa lisatty-muistutus)) "Päällystysurakan bonuksen oikea summa")
        (is (= (hae-muhoksen-paallystysurakan-id) (get-in lisatty-sakko [:laatupoikkeama :urakka])) "Päällystysurakan sanktiorunko oikea summa")
        (is (= perustelu (get-in lisatty-sakko [:laatupoikkeama :paatos :perustelu])) "Päällystysurakan sanktiorunko oikea summa")))))

(deftest tallenna-suorasanktio-hoidon-urakassa-sakko
  (let [perustelu "ABC gorilla gävelee"
        perintapvm (pvm/->pvm-aika "3.1.2017 22:00:00")
        sanktiorunko-hoito {:suorasanktio        true
                            :toimenpideinstanssi (hae-oulun-alueurakan-talvihoito-tpi-id)
                            :perintapvm          perintapvm}
        hoidon-sakko (merge sanktiorunko-hoito {:laji :A :summa 665.9 :tyyppi {:id 2 :nimi "Talvihoito"}})
        hoidon-muistutus (merge sanktiorunko-hoito {:laji :muistutus :summa nil :tyyppi {:id 2 :nimi "Talvihoito"}})
        laatupoikkeama {:tekijanimi    "Järjestelmä Vastaava"
                        :paatos        {:paatos "sanktio", :kasittelyaika (pvm/->pvm-aika "2.1.2017 22:00:00"), :kasittelytapa :kommentit, :perustelu perustelu}
                        :aika          (pvm/->pvm-aika "1.1.2017 08:00:00"), :urakka (hae-oulun-alueurakan-2014-2019-id)}
        hk-alkupvm (pvm/->pvm "1.1.2017")
        hk-loppupvm (pvm/->pvm "31.12.2017")]

    (testing "Hoitourakan suorasanktion tallennus"
      (let [sanktiot-sakon-jalkeen (kutsu-http-palvelua
                                     :tallenna-suorasanktio
                                     +kayttaja-jvh+
                                     {:sanktio        hoidon-sakko
                                      :laatupoikkeama laatupoikkeama
                                      :hoitokausi     [hk-alkupvm hk-loppupvm]})
            sanktiot-muistutuksen-jalkeen (kutsu-http-palvelua
                                        :tallenna-suorasanktio
                                        +kayttaja-jvh+
                                        {:sanktio        hoidon-muistutus
                                         :laatupoikkeama laatupoikkeama
                                         :hoitokausi     [hk-alkupvm hk-loppupvm]})
            lisatty-hoidon-sakko (first (filter #(= 665.9 (:summa %)) sanktiot-sakon-jalkeen))
            lisatty-hoidon-muistutus (first (filter #(= nil (:summa %)) sanktiot-muistutuksen-jalkeen))]
        (log/debug "lisätty hoidon-sakko " lisatty-hoidon-sakko)
        (log/debug "lisätty hoidon-muistutus " lisatty-hoidon-muistutus)
        (is (number? (:id lisatty-hoidon-sakko)) "Tallennus palauttaa uuden id:n")
        (is (number? (:id lisatty-hoidon-muistutus)) "Tallennus palauttaa uuden id:n")
        (is (= :A (:laji lisatty-hoidon-sakko)) "Hoitourakan bonuksen oikea sanktiolaji")
        (is (= "Talvihoito" (:nimi (:tyyppi lisatty-hoidon-sakko))) "Hoitourakan sakon oikea sanktiotyyppi")
        (is (= :muistutus (:laji lisatty-hoidon-muistutus)) "Hoitourakan muistutuksen oikea sanktiolaji")
        (is (= "Talvihoito" (:nimi (:tyyppi lisatty-hoidon-muistutus))) "Hoitourakan bonuksen oikea sanktiotyyppi")
        (is (= 665.9 (:summa lisatty-hoidon-sakko)) "Hoitourakan sakon oikea summa")
        (is (= nil (:summa lisatty-hoidon-muistutus)) "Hoitourakan bonuksen oikea summa")
        (is (= (hae-oulun-alueurakan-2014-2019-id) (get-in lisatty-hoidon-sakko [:laatupoikkeama :urakka])) "Hoitourakan sanktiorunko-hoito oikea summa")
        (is (= perustelu (get-in lisatty-hoidon-sakko [:laatupoikkeama :paatos :perustelu])) "Hoitourakan sanktiorunko-hoito oikea summa")))))

(deftest hae-laatupoikkeaman-tiedot
  (let [urakka-id (hae-oulun-alueurakan-2005-2012-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-laatupoikkeaman-tiedot +kayttaja-jvh+ {:urakka-id   urakka-id
                                                                            :laatupoikkeama-id 1})]
    (is (not (empty? vastaus)))
    (is (string? (:kuvaus vastaus)))
    (is (>= (count (:kuvaus vastaus)) 10))))

(deftest hae-urakan-laatupoikkeamat
  (let [urakka-id (hae-oulun-alueurakan-2005-2012-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-laatupoikkeamat +kayttaja-jvh+
                                {:listaus   :kaikki
                                 :urakka-id urakka-id
                                 :alku      (pvm/luo-pvm (+ 1900 100) 9 1)
                                 :loppu     (pvm/luo-pvm (+ 1900 110) 8 30)})]
    (is (not (empty? vastaus)))
    (is (>= (count vastaus) 1))))

(deftest hae-urakan-sanktiot
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-sanktiot +kayttaja-jvh+ {:urakka-id urakka-id
                                                                     :alku      (pvm/luo-pvm 2015 10 1)
                                                                     :loppu     (pvm/luo-pvm 2016 10 30)})]
    (is (not (empty? vastaus)))
    (is (>= (count vastaus) 8))))

(deftest hae-sanktiotyypit
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-sanktiotyypit +kayttaja-jvh+)]
    (is (not (empty? vastaus)))
    (is (>= (count vastaus) 9))))

(deftest hae-urakan-tarkastukset
  (let [urakka-id (hae-oulun-alueurakan-2005-2012-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-tarkastukset +kayttaja-jvh+
                                {:urakka-id urakka-id
                                 :alkupvm   (pvm/luo-pvm (+ 1900 100) 9 1)
                                 :loppupvm  (pvm/luo-pvm (+ 1900 110) 8 30)
                                 :tienumero nil
                                 :tyyppi    nil
                                 :vain-laadunalitukset? false})]
    (is (not (empty? vastaus)))
    (is (>= (count vastaus) 1))
    (let [tarkastus (first vastaus)]
      (is (= #{:ok? :jarjestelma :havainnot :laadunalitus :vakiohavainnot :aika :soratiemittaus
               :tr :tekija :id :tyyppi :tarkastaja :yllapitokohde :nayta-urakoitsijalle :liitteet}
             (into #{} (keys tarkastus)))))))

(deftest hae-urakan-tarkastukset-urakoitsijalle
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-tarkastukset +kayttaja-urakan-vastuuhenkilo+
                                {:urakka-id urakka-id
                                 :alkupvm   (pvm/luo-pvm (+ 1900 100) 9 1)
                                 :loppupvm  (pvm/luo-pvm (+ 1900 130) 8 30)
                                 :tienumero nil
                                 :tyyppi    nil
                                 :vain-laadunalitukset? false})]
    (is (not (empty? vastaus)))
    (is (= (count vastaus) 1))))

(deftest hae-tarkastus
  (let [urakka-id (hae-oulun-alueurakan-2005-2012-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-tarkastus +kayttaja-jvh+ {:urakka-id    urakka-id
                                                               :tarkastus-id 1})]
    (is (not (empty? vastaus)))
    (is (>= (count vastaus) 1))))

(deftest hae-tarkastus-joka-ei-nay-urakoitsijalle
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-tarkastus +kayttaja-urakan-vastuuhenkilo+
                                {:urakka-id urakka-id
                                 :tarkastus-id (ffirst (q "SELECT id FROM tarkastus
                                                           WHERE havainnot != 'Tämä tarkastus näkyy myös urakoitsijalle';"))})]
    (is (empty? vastaus))))

(deftest hae-tarkastus-joka-nakyy-urakoitsijalle
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-tarkastus +kayttaja-urakan-vastuuhenkilo+
                                {:urakka-id urakka-id
                                 :tarkastus-id (ffirst (q "SELECT id FROM tarkastus
                                                           WHERE havainnot = 'Tämä tarkastus näkyy myös urakoitsijalle';"))})]
    (is (not (empty? vastaus)))
    (is (= (:havainnot vastaus) "Tämä tarkastus näkyy myös urakoitsijalle"))))

(deftest hae-urakan-sanktiot-test
  (is (oikeat-sarakkeet-palvelussa?
        [:id :perintapvm :summa :laji :indeksi :suorasanktio :toimenpideinstanssi
         [:laatupoikkeama :id] [:laatupoikkeama :kohde] [:laatupoikkeama :aika] [:laatupoikkeama :tekija] [:laatupoikkeama :urakka]
         [:laatupoikkeama :tekijanimi] [:laatupoikkeama :kuvaus] [:laatupoikkeama :sijainti] [:laatupoikkeama :tarkastuspiste]
         [:laatupoikkeama :selvityspyydetty] [:laatupoikkeama :selvitysannettu]

         [:laatupoikkeama :paatos :kasittelyaika] [:laatupoikkeama :paatos :paatos] [:laatupoikkeama :paatos :kasittelytapa]
         [:laatupoikkeama :paatos :muukasittelytapa] [:laatupoikkeama :paatos :perustelu]

         [:laatupoikkeama :tr :numero] [:laatupoikkeama :tr :alkuosa] [:laatupoikkeama :tr :loppuosa]
         [:laatupoikkeama :tr :alkuetaisyys] [:laatupoikkeama :tr :loppuetaisyys]]
        :hae-urakan-sanktiot
        {:urakka-id (hae-oulun-alueurakan-2014-2019-id)
         :alku      (pvm/luo-pvm 2015 10 1)
         :loppu    (pvm/luo-pvm 2016 10 30)
         :tpi 1})))

(deftest hae-urakkatyypin-sanktiolajit
  (let [hoidon (kutsu-palvelua (:http-palvelin jarjestelma)
                               :hae-urakkatyypin-sanktiolajit +kayttaja-urakan-vastuuhenkilo+
                               {:urakkatyyppi :hoito})
        paallystyksen (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :hae-urakkatyypin-sanktiolajit +kayttaja-urakan-vastuuhenkilo+
                                      {:urakkatyyppi :paallystys})
        paikkauksen (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :hae-urakkatyypin-sanktiolajit +kayttaja-urakan-vastuuhenkilo+
                                    {:urakkatyyppi :paikkaus})
        tiemerkinnan (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :hae-urakkatyypin-sanktiolajit +kayttaja-urakan-vastuuhenkilo+
                                     {:urakkatyyppi :tiemerkinta})
        valaistuksen (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :hae-urakkatyypin-sanktiolajit +kayttaja-urakan-vastuuhenkilo+
                                     {:urakkatyyppi :valaistus})]
    (is (not (empty? hoidon)))
    (is (= #{:A :B :C :muistutus} hoidon) "Hoidon sanktiolajit")
    (is (= #{:yllapidon_sakko :yllapidon_bonus :yllapidon_muistutus}
           paallystyksen paikkauksen tiemerkinnan valaistuksen) "Ylläpidon sanktiolajit")))


