(ns harja.palvelin.palvelut.laadunseuranta-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.laadunseuranta :as ls]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [harja.jms-test :refer [feikki-sonja]]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.komponentit.fim-test :refer [+testi-fim+]]
            [harja.palvelin.integraatiot.labyrintti.sms-test :refer [+testi-sms-url+]]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sahkoposti]
            [harja.palvelin.integraatiot.labyrintti.sms :as labyrintti]
            [clojure.java.io :as io]
            [harja.palvelin.komponentit.sonja :as sonja])
  (:use org.httpkit.fake))

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
                        :fim (component/using
                               (fim/->FIM +testi-fim+)
                               [:db :integraatioloki])
                        :integraatioloki (component/using
                                           (integraatioloki/->Integraatioloki nil)
                                           [:db])
                        :sonja (feikki-sonja)
                        :sonja-sahkoposti (component/using
                                            (sahkoposti/luo-sahkoposti "foo@example.com"
                                                                       {:sahkoposti-sisaan-jono "email-to-harja"
                                                                        :sahkoposti-ulos-jono "harja-to-email"
                                                                        :sahkoposti-ulos-kuittausjono "harja-to-email-ack"})
                                            [:sonja :db :integraatioloki])
                        :labyrintti (component/using (labyrintti/->Labyrintti +testi-sms-url+
                                                                              "testi" "testi" (atom #{}))
                                                     [:db :integraatioloki :http-palvelin])
                        :laadunseuranta (component/using
                                          (ls/->Laadunseuranta)
                                          [:http-palvelin :db :fim :sonja-sahkoposti :labyrintti])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

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
                        :kohde "Kohde"}

        olemassa-olevan-sanktion-id 1
        olemassa-oleva-sanktio {:id olemassa-olevan-sanktion-id
                                :perintapvm #inst "2016-09-15T09:00:01.000-00:00"
                                :laji :A
                                :tyyppi 12
                                :summa 100
                                :indeksi "MAKU 2010"
                                :suorasanktio false
                                :toimenpideinstanssi 4
                                :vakiofraasi nil}
        uusi-sanktio {:perintapvm #inst "2016-09-15T09:00:01.000-00:00"
                      :laji :A
                      :tyyppi 12
                      :summa 100
                      :indeksi "MAKU 2010"
                      :suorasanktio false
                      :toimenpideinstanssi 4
                      :vakiofraasi nil}
        paatos {:paatos :sanktio
                :kasittelytapa :puhelin
                :kasittelyaika #inst "2016-09-15T09:00:01.000-00:00"
                :perustelu "Testi"}]

    (testing "Laatupoikkeaman tallennus"
      (let [vastaus (kutsu-http-palvelua :tallenna-laatupoikkeama
                                         +kayttaja-jvh+
                                         laatupoikkeama)
            id (:id vastaus)]

        (is (number? id) "Tallennus palauttaa uuden id:n")))

    (testing "sanktiollisen-laatupoikkeaman-tallennus"
      (let [vastaus (kutsu-http-palvelua :tallenna-laatupoikkeama
                                         +kayttaja-jvh+
                                         (assoc laatupoikkeama
                                           :paatos paatos
                                           :sanktiot [uusi-sanktio]))]
        (is (number? (:id vastaus)) "Tallennus palauttaa uuden id:n")
        (is (= 1 (count (:sanktiot vastaus))) "Uudella laatupoikkeamalla pitäisi olla yksi sanktio")
        (is (number? (get-in vastaus [:sanktiot 0 :id])) "Uudelle sanktiolle luodaan uusi id")))

    (testing "Sanktion päivittäminen laatupoikkeamaan"
      ;; Tässä testissä olemassaoleva sanktio siirretään uuteen laatupoikkeamaan
      ;; tämä ei ole varsinaisesti toivottu mahdollisuus, eikä ole mahdollista normaalisti,
      ;; mutta tämä on vaan helppo tapa testata sanktion päivittymistä
      (let [vastaus (kutsu-http-palvelua :tallenna-laatupoikkeama
                                         +kayttaja-jvh+
                                         (assoc laatupoikkeama
                                           :paatos paatos
                                           :sanktiot [olemassa-oleva-sanktio
                                                      uusi-sanktio]))]
        (is (number? (:id vastaus)) "Tallennus palauttaa uuden id:n")
        (is (= 2 (count (:sanktiot vastaus))) "Laatupoikkeamalla pitäisi olla kaksi sanktiota")

        (is (every? number? (map :id (:sanktiot vastaus))))
        (is (some #(= olemassa-olevan-sanktion-id %) (map :id (:sanktiot vastaus))))))

    (testing "Laatupoikkeaman luominen epäonnistuu jos sanktio ei kuulu urakkaan"
      (is (thrown? SecurityException
                   (kutsu-http-palvelua :tallenna-laatupoikkeama
                                        +kayttaja-jvh+
                                        (assoc laatupoikkeama
                                          :urakka 1
                                          :paatos paatos
                                          :sanktiot [olemassa-oleva-sanktio])))))))

(deftest laatupoikkeaman-selvityspyynnosta-lahtee-sms
  (let [laatupoikkeama {:sijainti {:type :point
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
                        :selvitys-pyydetty true
                        :tekija :tilaaja
                        :kohde "Kohde"}
        tekstiviesti-valitetty (atom false)
        fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-oulun-hoidon-urakan-kayttajat.xml"))]

    (with-fake-http
      [+testi-fim+ fim-vastaus
       +testi-sms-url+ (fn [_ _ _]
                         (reset! tekstiviesti-valitetty true)
                         "ok")]
      (kutsu-http-palvelua :tallenna-laatupoikkeama +kayttaja-jvh+ laatupoikkeama)
      (odota-ehdon-tayttymista #(true? @tekstiviesti-valitetty) "Tekstiviesti lähetettiin" 5000)
      (is (true? @tekstiviesti-valitetty) "Tekstiviesti lähetettiin"))))

(deftest laatupoikkeaman-selvityspyynnosta-lahtee-sahkoposti
  (let [laatupoikkeama {:sijainti {:type :point
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
                        :selvitys-pyydetty true
                        :tekija :tilaaja
                        :kohde "Kohde"}
        sahkoposti-valitetty (atom false)
        fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-oulun-hoidon-urakan-kayttajat.xml"))]

    (sonja/kuuntele! (:sonja jarjestelma) "harja-to-email" (fn [_] (reset! sahkoposti-valitetty true)))

    (with-fake-http
      [+testi-fim+ fim-vastaus
       +testi-sms-url+ "ok"]
      (kutsu-http-palvelua :tallenna-laatupoikkeama +kayttaja-jvh+ laatupoikkeama))

    (odota-ehdon-tayttymista #(true? @sahkoposti-valitetty) "Sähköposti lähetettiin" 5000)
    (is (true? @sahkoposti-valitetty) "Sähköposti lähetettiin")))

(defn palvelukutsu-tallenna-suorasanktio [kayttaja s lp hk-alkupvm hk-loppupvm]
  (kutsu-http-palvelua
    :tallenna-suorasanktio kayttaja {:sanktio s
                                     :laatupoikkeama lp
                                     :hoitokausi [hk-alkupvm hk-loppupvm]}))

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
        laatupoikkeama {:tekijanimi "Järjestelmä Vastaava"
                        :paatos {:paatos "sanktio", :kasittelyaika (pvm/->pvm-aika "2.1.2017 22:00:00"), :kasittelytapa :kommentit, :perustelu perustelu}
                        :aika (pvm/->pvm-aika "1.1.2017 08:00:00"), :urakka (hae-muhoksen-paallystysurakan-id),
                        :yllapitokohde (hae-muhoksen-paallystysurakan-testikohteen-id)}
        hk-alkupvm (pvm/->pvm "1.1.2017")
        hk-loppupvm (pvm/->pvm "31.12.2017")]
    (testing "Päällystysurakan suorasanktion tallennus"
      (let [sanktiot-sakon-jalkeen (palvelukutsu-tallenna-suorasanktio
                                     +kayttaja-jvh+ sakko laatupoikkeama hk-alkupvm hk-loppupvm)
            sanktiot-bonuksen-jalkeen (palvelukutsu-tallenna-suorasanktio
                                        +kayttaja-jvh+ bonus laatupoikkeama hk-alkupvm hk-loppupvm)
            sanktiot-muistutuksen-jalkeen (palvelukutsu-tallenna-suorasanktio
                                            +kayttaja-jvh+ muistutus laatupoikkeama hk-alkupvm hk-loppupvm)
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
        sanktiorunko-hoito {:suorasanktio true
                            :toimenpideinstanssi (hae-oulun-alueurakan-talvihoito-tpi-id)
                            :perintapvm perintapvm}
        hoidon-sakko (merge sanktiorunko-hoito {:laji :A :summa 665.9 :tyyppi {:id 2 :nimi "Talvihoito"}})
        hoidon-muistutus (merge sanktiorunko-hoito {:laji :muistutus :summa nil :tyyppi {:id 2 :nimi "Talvihoito"}})
        laatupoikkeama {:tekijanimi "Järjestelmä Vastaava"
                        :paatos {:paatos "sanktio", :kasittelyaika (pvm/->pvm-aika "2.1.2017 22:00:00"), :kasittelytapa :kommentit, :perustelu perustelu}
                        :aika (pvm/->pvm-aika "1.1.2017 08:00:00"), :urakka (hae-oulun-alueurakan-2014-2019-id)}
        hk-alkupvm (pvm/->pvm "1.1.2017")
        hk-loppupvm (pvm/->pvm "31.12.2017")]

    (testing "Hoitourakan suorasanktion tallennus"
      (let [sanktiot-sakon-jalkeen (palvelukutsu-tallenna-suorasanktio
                                     +kayttaja-jvh+ hoidon-sakko laatupoikkeama hk-alkupvm hk-loppupvm)
            sanktiot-muistutuksen-jalkeen (palvelukutsu-tallenna-suorasanktio
                                            +kayttaja-jvh+ hoidon-muistutus laatupoikkeama hk-alkupvm hk-loppupvm)
            lisatty-hoidon-sakko (first (filter #(= 665.9 (:summa %)) sanktiot-sakon-jalkeen))
            lisatty-hoidon-muistutus (first (filter #(= nil (:summa %)) sanktiot-muistutuksen-jalkeen))]
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

(deftest tallenna-suorasanktio-ei-salli-vaaran-urakkatyypin-sanktiolajia
  (let [perustelu "ABC gorilla gävelee"
        perintapvm (pvm/->pvm-aika "3.1.2017 22:00:00")
        sanktiorunko {:suorasanktio true
                      :perintapvm perintapvm}
        hoidon-sakko (merge sanktiorunko {:laji :yllapidon_sakko :summa 665.9 :tyyppi {:id 2 :nimi "Talvihoito"}})
        paallystys-sakko (merge sanktiorunko {:laji :A :summa 1234 :tyyppi {:id 4 :nimi "Ylläpidon sakko"}})
        laatupoikkeama-hoito {:tekijanimi "Järjestelmä Vastaava"
                              :paatos {:paatos "sanktio", :kasittelyaika (pvm/->pvm-aika "2.1.2017 22:00:00"), :kasittelytapa :kommentit, :perustelu perustelu}
                              :aika (pvm/->pvm-aika "1.1.2017 08:00:00"), :urakka (hae-oulun-alueurakan-2014-2019-id)}
        laatupoikkeama-paallystys {:tekijanimi "Järjestelmä Vastaava"
                                   :paatos {:paatos "sanktio", :kasittelyaika (pvm/->pvm-aika "2.1.2017 22:00:00"), :kasittelytapa :kommentit, :perustelu perustelu}
                                   :aika (pvm/->pvm-aika "1.1.2017 08:00:00"), :urakka (hae-muhoksen-paallystysurakan-id),
                                   :yllapitokohde (hae-muhoksen-paallystysurakan-testikohteen-id)}
        hk-alkupvm (pvm/->pvm "1.1.2017")
        hk-loppupvm (pvm/->pvm "31.12.2017")]

    (testing "tallenna-suorasanktio-ei-salli-vaaran-urakkatyypin-sanktiolajia"
      (is (thrown? Exception (palvelukutsu-tallenna-suorasanktio
                               +kayttaja-jvh+ hoidon-sakko laatupoikkeama-hoito hk-alkupvm hk-loppupvm)))

      (is (thrown? Exception (palvelukutsu-tallenna-suorasanktio
                               +kayttaja-jvh+ paallystys-sakko laatupoikkeama-paallystys hk-alkupvm hk-loppupvm))))))

(deftest paivita-eri-urakan-suorasanktiota
  (let [perustelu "ABC möhöfantti kävelee"
        perintapvm (pvm/->pvm-aika "3.1.2017 22:00:00")
        sanktiorunko {:suorasanktio true
                      :perintapvm perintapvm
                      :id 1} ;sanktio 1 kuuluu urakkaan 4
        paallystys-sakko (merge sanktiorunko {:laji :A :summa 1234 :tyyppi {:id 4 :nimi "Ylläpidon sakko"}})
        laatupoikkeama-paallystys {:tekijanimi "Järjestelmä Vastaava"
                                   :paatos {:paatos "sanktio", :kasittelyaika (pvm/->pvm-aika "2.1.2017 22:00:00"), :kasittelytapa :kommentit, :perustelu perustelu}
                                   :aika (pvm/->pvm-aika "1.1.2017 08:00:00"), :urakka (hae-muhoksen-paallystysurakan-id),
                                   :yllapitokohde (hae-muhoksen-paallystysurakan-testikohteen-id)}
        hk-alkupvm (pvm/->pvm "1.1.2017")
        hk-loppupvm (pvm/->pvm "31.12.2017")]

    (testing "paivita-eri-urakan-suorasanktiota"
      (is (thrown? Exception (palvelukutsu-tallenna-suorasanktio
                               +kayttaja-jvh+ paallystys-sakko laatupoikkeama-paallystys hk-alkupvm hk-loppupvm))))))

(deftest suorasanktion-poistaminen-vs-laatupoikkeamaan-liitetyn-sanktion-poistaminen
  (let [perustelu "ABC lehmä lepäilee"
        lp-aika (pvm/->pvm-aika "1.1.2017 08:00:00")
        lp-aika2 (pvm/->pvm-aika "1.1.2017 09:00:00")
        perintapvm (pvm/->pvm "3.1.2017")
        sanktiorunko {:toimenpideinstanssi (hae-oulun-alueurakan-talvihoito-tpi-id)
                      :perintapvm perintapvm}
        hoidon-sakko-suorasanktio (merge sanktiorunko {:suorasanktio true :laji :A :summa 637.27 :tyyppi {:id 2 :nimi "Talvihoito"}})
        hoidon-sakko-laatupoikkeamaan-liittyva (merge sanktiorunko {:suorasanktio false :laji :A :summa 200.27 :tyyppi {:id 2 :nimi "Talvihoito"}})
        laatupoikkeama-ss {:tekijanimi "Järjestelmä Vastaava" :kuvaus "Suorasanktion laatupoikkeama joka pitää poistua kun suorasanktio poistetaan"
                           :paatos {:paatos "sanktio", :kasittelyaika (pvm/->pvm-aika "2.1.2017 22:00:00"), :kasittelytapa :kommentit, :perustelu perustelu}
                           :aika lp-aika, :urakka (hae-oulun-alueurakan-2014-2019-id)}
        laatupoikkeama-lp {:tekijanimi "Järjestelmä Vastaava" :kuvaus "Laatupoikkeaman laatupoikkeama joka ei saa poistua."
                           :paatos {:paatos "sanktio", :kasittelyaika (pvm/->pvm-aika "3.1.2017 12:00:00"), :kasittelytapa :kommentit, :perustelu perustelu}
                           :aika lp-aika2, :urakka (hae-oulun-alueurakan-2014-2019-id)}
        hk-alkupvm (pvm/->pvm "1.10.2016")
        hk-loppupvm (pvm/->pvm "30.09.2017")
        sanktiot-suorasanktion-jalkeen (palvelukutsu-tallenna-suorasanktio
                                         +kayttaja-jvh+ hoidon-sakko-suorasanktio laatupoikkeama-ss hk-alkupvm hk-loppupvm)
        sanktiot-lp-liittyvan-sanktion-jalkeen (palvelukutsu-tallenna-suorasanktio
                                                 +kayttaja-jvh+ hoidon-sakko-laatupoikkeamaan-liittyva laatupoikkeama-lp hk-alkupvm hk-loppupvm)
        lisatty-hoidon-sakko (first (filter #(= 637.27 (:summa %)) sanktiot-suorasanktion-jalkeen))
        lisatyn-sanktion-id (:id lisatty-hoidon-sakko)
        lisatyn-laatupoikkeaman-id (:id (:laatupoikkeama lisatty-hoidon-sakko))
        lisatty-hoidon-sakko-lp (first (filter #(= 200.27 (:summa %)) sanktiot-lp-liittyvan-sanktion-jalkeen))
        lisatyn-sanktion-id-lp (:id lisatty-hoidon-sakko-lp)
        lisatyn-laatupoikkeaman-id-lp (:id (:laatupoikkeama lisatty-hoidon-sakko-lp))]
    (let [sanktiot-suorasanktion-poistamisen-jalkeen (palvelukutsu-tallenna-suorasanktio
                                                       +kayttaja-jvh+
                                                       (merge hoidon-sakko-suorasanktio {:id lisatyn-sanktion-id
                                                                                         :poistettu true})
                                                       (merge laatupoikkeama-ss {:id lisatyn-laatupoikkeaman-id}) hk-alkupvm hk-loppupvm)
          sanktiot-lp-sanktion-poistamisen-jalkeen (palvelukutsu-tallenna-suorasanktio
                                                     +kayttaja-jvh+
                                                     (merge hoidon-sakko-laatupoikkeamaan-liittyva {:id lisatyn-sanktion-id-lp
                                                                                                    :poistettu true})
                                                     (merge laatupoikkeama-lp {:id lisatyn-laatupoikkeaman-id-lp}) hk-alkupvm hk-loppupvm)
          poistettu-suorasanktio-kannassa (q-sanktio-leftjoin-laatupoikkeama lisatyn-sanktion-id)
          poistettu-lp-sanktio-kannassa (q-sanktio-leftjoin-laatupoikkeama lisatyn-sanktion-id-lp)
          poistettu-hoidon-sakko (first (filter #(= 637.27 (:summa %)) sanktiot-suorasanktion-poistamisen-jalkeen))]
      (testing "poista-suorasanktio-hoitourakassa"
        (is (and (number? (:id lisatty-hoidon-sakko)) (number? (:id poistettu-suorasanktio-kannassa))) "Tallennus palauttaa uuden id:n")
        (is (= :A (:laji lisatty-hoidon-sakko) (keyword (:laji poistettu-suorasanktio-kannassa))) "Hoitourakan bonuksen oikea sanktiolaji")
        (is (not= true (:poistettu lisatty-hoidon-sakko)) "Sakkoa ei poistettu")
        (is (nil? poistettu-hoidon-sakko) "Sakko poistettu")
        (is (and (= true (:poistettu poistettu-suorasanktio-kannassa))
                 (= true (:lp_poistettu poistettu-suorasanktio-kannassa))))
        (is (= lp-aika (get-in lisatty-hoidon-sakko [:laatupoikkeama :aika]) (:lp_aika poistettu-suorasanktio-kannassa)))
        (is (= "Talvihoito" (:nimi (:tyyppi lisatty-hoidon-sakko))) "Hoitourakan sakon oikea sanktiotyyppi")
        (is (=marginaalissa? 637.27 (:summa lisatty-hoidon-sakko) (:summa poistettu-suorasanktio-kannassa)) "Hoitourakan sakon oikea summa")
        (is (= (hae-oulun-alueurakan-2014-2019-id) (get-in lisatty-hoidon-sakko [:laatupoikkeama :urakka])) "Hoitourakan sanktiorunko-hoito oikea summa")
        (is (= perustelu (get-in lisatty-hoidon-sakko [:laatupoikkeama :paatos :perustelu])) "Hoitourakan sanktiorunko-hoito oikea summa"))

      (testing "laatupoikkeamaan-liitetyn-sanktion-poistaminen-ei-poista-laatupoikkeamaa"
        (is (and (= true (:poistettu poistettu-lp-sanktio-kannassa))
                 (= false (:lp_poistettu poistettu-lp-sanktio-kannassa))))))))


(deftest hae-laatupoikkeaman-tiedot
  (let [urakka-id (hae-oulun-alueurakan-2005-2012-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-laatupoikkeaman-tiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                                            :laatupoikkeama-id 1})]
    (is (not (empty? vastaus)))
    (is (string? (:kuvaus vastaus)))
    (is (>= (count (:kuvaus vastaus)) 10))))

(deftest hae-urakan-laatupoikkeamat
  (let [urakka-id (hae-oulun-alueurakan-2005-2012-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-laatupoikkeamat +kayttaja-jvh+
                                {:listaus :kaikki
                                 :urakka-id urakka-id
                                 :alku (pvm/luo-pvm (+ 1900 100) 9 1)
                                 :loppu (pvm/luo-pvm (+ 1900 110) 8 30)})]
    (is (not (empty? vastaus)))
    (is (>= (count vastaus) 1))))

(deftest hae-urakan-sanktiot
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-sanktiot +kayttaja-jvh+ {:urakka-id urakka-id
                                                                     :alku (pvm/luo-pvm 2015 10 1)
                                                                     :loppu (pvm/luo-pvm 2016 10 30)})]
    (is (not (empty? vastaus)))
    (is (>= (count vastaus) 8))))

(deftest hae-sanktiotyypit
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-sanktiotyypit +kayttaja-jvh+)]
    (is (not (empty? vastaus)))
    (is (>= (count vastaus) 9))))

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
         :alku (pvm/luo-pvm 2015 10 1)
         :loppu (pvm/luo-pvm 2016 10 30)
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

(deftest vaadi-sanktio-kuuluu-urakkaan-testit
  (let [kohde-urakka 4
        kuuluva-sanktio 1
        kuulumaton-sanktio 9
        kutsu (partial ls/vaadi-sanktio-kuuluu-urakkaan (:db jarjestelma) kohde-urakka)]
    (testing "Olemattomat id:t"
      (is (nil? (kutsu -1)) "Uutta sanktiota ei pitäisi validoida")
      (is (nil? (kutsu nil)) "Uutta sanktiota ei pitäisi validoida"))

    (testing "Kuulumaton sanktio"
      (is (thrown? SecurityException (kutsu kuulumaton-sanktio)) (str "Sanktio " kuulumaton-sanktio " ei kuulu urakkaan " kohde-urakka)))

    (testing "Kuuluva sanktio"
      (is (nil? (kutsu kuuluva-sanktio)) (str "Sanktio " kuuluva-sanktio " kuuluu urakkaan " kohde-urakka ", eli validoinnin pitäisi vastata nil")))))
