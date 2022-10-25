(ns harja.palvelin.palvelut.laadunseuranta-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.laadunseuranta :as ls]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [harja.jms-test :refer [feikki-jms]]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.komponentit.fim-test :refer [+testi-fim+]]
            [harja.palvelin.integraatiot.labyrintti.sms-test :refer [+testi-sms-url+]]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.vayla-rest.sahkoposti :as sahkoposti-api]
            [harja.palvelin.integraatiot.labyrintti.sms :as labyrintti]
            [clojure.java.io :as io]
            [harja.palvelin.integraatiot.jms :as jms]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset :as lyv-yhteiset]
            [harja.fmt :as fmt]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [clojure.string :as str])
  (:import (java.util UUID))
  (:use org.httpkit.fake))

(defn jarjestelma-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
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
                        :pdf-vienti (component/using
                                      (pdf-vienti/luo-pdf-vienti)
                                      [:http-palvelin])
                        :raportointi (component/using
                                       (raportointi/luo-raportointi)
                                       [:db :pdf-vienti])
                        :raportit (component/using
                                    (raportit/->Raportit)
                                    [:http-palvelin :db :raportointi :pdf-vienti])
                        :sonja (feikki-jms "sonja")
                        :itmf (feikki-jms "itmf")
                        :api-sahkoposti (component/using
                                          (sahkoposti-api/->ApiSahkoposti {:api-sahkoposti {:suora? false
                                                                                            :sahkoposti-lahetys-url "/harja/api/sahkoposti/xml"
                                                                                            :sahkoposti-ja-liite-lahetys-url "/harja/api/sahkoposti-ja-liite/xml"
                                                                                            :palvelin "http://localhost:8084"
                                                                                            :vastausosoite "harja-ala-vastaa@vayla.fi"}
                                                                           :tloik {:toimenpidekuittausjono "Harja.HarjaToT-LOIK.Ack"}})
                                          [:http-palvelin :db :integraatioloki :itmf])
                        :labyrintti (component/using (labyrintti/->Labyrintti +testi-sms-url+
                                                                              "testi" "testi" (atom #{}))
                                                     [:db :integraatioloki :http-palvelin])
                        :laadunseuranta (component/using
                                          (ls/->Laadunseuranta)
                                          [:http-palvelin :db :fim :api-sahkoposti :labyrintti])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each jarjestelma-fixture)


;; Helpottaa testien roskien keruuta. Toisinaan kun omalla koneella ajaa kaikki testit useampaan kertaan,
;; jäävät siivoamattomat sanktiot testikantaan vääristämään tuloksia
(defn poista-sanktio-perustelulla
  "Poistaa laatupoikkeaman perustelukentän sisällön mukaan tunnistaen, ja siihen liittyvät sanktiot."
  [perustelu]
  (let [laatupoikkeama-idt (map first (q (str "SELECT id FROM laatupoikkeama where perustelu = '" perustelu "';")))
        sanktio-idt (when
                      (seq laatupoikkeama-idt)
                      (map first (q (str "SELECT id FROM sanktio where laatupoikkeama IN (" (str/join "," laatupoikkeama-idt) ");"))))]

    (when (seq sanktio-idt)
      (u "DELETE FROM sanktio WHERE id IN (" (str/join "," sanktio-idt) ");"))
    (when (seq laatupoikkeama-idt)
      (u "DELETE FROM laatupoikkeama WHERE id IN(" (str/join "," laatupoikkeama-idt) ");"))))

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

    (testing "Laatupoikkeaman luominen epäonnistuu jos sanktio ei kuulu urakkaan"
      (is (thrown? SecurityException
                   (kutsu-http-palvelua :tallenna-laatupoikkeama
                                        +kayttaja-jvh+
                                        (assoc laatupoikkeama
                                          :urakka 1
                                          :paatos paatos
                                          :sanktiot [olemassa-oleva-sanktio])))))

    ;; Siivoa roskat
    (poista-sanktio-perustelulla "Testi")))

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
        fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-oulun-hoidon-urakan-kayttajat.xml"))
        viesti-id (str (UUID/randomUUID))]

    (with-fake-http
      [+testi-fim+ fim-vastaus
       +testi-sms-url+ (fn [_ _ _]
                         (reset! tekstiviesti-valitetty true)
                         "ok")
       {:url "http://localhost:8084/harja/api/sahkoposti/xml" :method :post} (onnistunut-sahkopostikuittaus viesti-id)]
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
        fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-oulun-hoidon-urakan-kayttajat.xml"))
        viesti-id (str (UUID/randomUUID))]

    (with-fake-http
      [+testi-fim+ fim-vastaus
       +testi-sms-url+ "ok"
       {:url "http://localhost:8084/harja/api/sahkoposti/xml" :method :post} (onnistunut-sahkopostikuittaus viesti-id)]
      (kutsu-http-palvelua :tallenna-laatupoikkeama +kayttaja-jvh+ laatupoikkeama))

    (is (< 0 (count (hae-ulos-lahtevat-integraatiotapahtumat))) "Sähköposti lähetettiin")))

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
                        :aika (pvm/->pvm-aika "1.1.2017 08:00:00"), :urakka (hae-urakan-id-nimella "Muhoksen päällystysurakka"),
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
            lisatty-sakko (first (filter #(= -1234.0 (:summa %)) sanktiot-sakon-jalkeen))
            lisatty-bonus (first (filter #(= 4321.0 (:summa %)) sanktiot-bonuksen-jalkeen))
            lisatty-muistutus (first (filter #(and (= nil (:summa %))) sanktiot-muistutuksen-jalkeen))]
        (is (number? (:id lisatty-sakko)) "Tallennus palauttaa uuden id:n")
        (is (= :yllapidon_sakko (:laji lisatty-sakko)) "Päällystysurakan bonuksen oikea sanktiolaji")
        (is (= "Ylläpidon sakko" (:nimi (:tyyppi lisatty-sakko))) "Päällystysurakan sakon oikea sanktiotyyppi")
        (is (= :yllapidon_bonus (:laji lisatty-bonus)) "Päällystysurakan bonuksen oikea sanktiolaji")
        (is (= "Ylläpidon bonus" (:nimi (:tyyppi lisatty-bonus))) "Päällystysurakan bonuksen oikea sanktiotyyppi")
        (is (= "Ylläpidon muistutus" (:nimi (:tyyppi lisatty-muistutus))) "Päällystysurakan muistutuksen oikea sanktiotyyppi")
        (is (= -1234.0 (:summa lisatty-sakko)) "Päällystysurakan sakon oikea summa")
        (is (= 4321.0 (:summa lisatty-bonus)) "Päällystysurakan bonuksen oikea summa")
        (is (= nil (:summa lisatty-muistutus)) "Päällystysurakan bonuksen oikea summa")
        (is (= (hae-urakan-id-nimella "Muhoksen päällystysurakka") (get-in lisatty-sakko [:laatupoikkeama :urakka])) "Päällystysurakan sanktiorunko oikea summa")
        (is (= perustelu (get-in lisatty-sakko [:laatupoikkeama :paatos :perustelu])) "Päällystysurakan sanktiorunko oikea summa")))

    ;; Siivoa roskat
    (poista-sanktio-perustelulla perustelu)))

(deftest tallenna-suorasanktio-hoidon-urakassa-sakko
  (let [perustelu "ABC gorilla gävelee hoidon urakka-alueella"
        perintapvm (pvm/->pvm-aika "3.1.2017 22:00:00")
        sanktiorunko-hoito {:suorasanktio true
                            :toimenpideinstanssi (hae-oulun-alueurakan-talvihoito-tpi-id)
                            :perintapvm perintapvm}
        hoidon-sakko (merge sanktiorunko-hoito {:laji :A :summa 665.9
                                                :tyyppi {:id (ffirst (q (str "SELECT id FROM sanktiotyyppi where koodi = 13;")))
                                                         :nimi "Talvihoito, päätiet"}
                                                :indeksi "MAKU 2010"})
        hoidon-muistutus (merge sanktiorunko-hoito {:laji :muistutus :summa nil :tyyppi
                                                    {:id (ffirst (q (str "SELECT id FROM sanktiotyyppi where koodi = 13;")))
                                                     :nimi "Talvihoito, päätiet"}})
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
            lisatty-hoidon-sakko (first (filter #(= -665.9 (:summa %)) sanktiot-sakon-jalkeen))
            lisatty-hoidon-muistutus (first (filter #(= nil (:summa %)) sanktiot-muistutuksen-jalkeen))]
        (is (number? (:id lisatty-hoidon-sakko)) "Tallennus palauttaa uuden id:n")
        (is (number? (:id lisatty-hoidon-muistutus)) "Tallennus palauttaa uuden id:n")
        (is (= :A (:laji lisatty-hoidon-sakko)) "Hoitourakan bonuksen oikea sanktiolaji")
        (is (= "Talvihoito, päätiet" (:nimi (:tyyppi lisatty-hoidon-sakko))) "Hoitourakan sakon oikea sanktiotyyppi")
        (is (= :muistutus (:laji lisatty-hoidon-muistutus)) "Hoitourakan muistutuksen oikea sanktiolaji")
        (is (= "Talvihoito, päätiet" (:nimi (:tyyppi lisatty-hoidon-muistutus))) "Hoitourakan bonuksen oikea sanktiotyyppi")
        (is (= -665.9 (:summa lisatty-hoidon-sakko)) "Hoitourakan sakon oikea summa")
        (is (= "MAKU 2010" (:indeksi lisatty-hoidon-sakko)) "Indeksi oikein")
        (is (= nil (:summa lisatty-hoidon-muistutus)) "Hoitourakan bonuksen oikea summa")
        (is (= (hae-oulun-alueurakan-2014-2019-id) (get-in lisatty-hoidon-sakko [:laatupoikkeama :urakka])) "Hoitourakan sanktiorunko-hoito oikea summa")
        (is (= perustelu (get-in lisatty-hoidon-sakko [:laatupoikkeama :paatos :perustelu])) "Hoitourakan sanktiorunko-hoito oikea summa")))


    ;; Siivoa roskat
    (poista-sanktio-perustelulla perustelu)))

(deftest tallenna-suorasanktio-2021-alkavassa-mhu-urakassa-sakko
  (let [perustelu "ABC koira kävelee MHU-alueella"
        tpi-id-iin-talvihoito (ffirst (q "SELECT id FROM toimenpideinstanssi where nimi = 'Iin MHU 2021-2026 Talvihoito TP';"))
        ;; asetetaan tähän indeksi jolla on arvo, jotta varmistetaan että backendin varmistus toimii, indeksi pitää siis tässä tapauksessa nillata
        sanktio {:suorasanktio true, :laji :A, :summa 777, :indeksi "MAKU 2015", :toimenpideinstanssi tpi-id-iin-talvihoito, :perintapvm #inst "2021-10-02T21:00:00.000-00:00"
                 :tyyppi {:id (ffirst (q (str "SELECT id FROM sanktiotyyppi where koodi = 13;"))),
                          :nimi "Talvihoito, päätiet", :toimenpidekoodi 618}}
        laatupoikkeama {:tekijanimi "Max Power"
                        :paatos {:paatos "sanktio", :kasittelyaika (pvm/->pvm-aika "2.10.2021 22:00:00"), :kasittelytapa :kommentit, :perustelu perustelu}
                        :aika (pvm/->pvm-aika "1.10.2021 08:00:00"), :urakka (hae-iin-maanteiden-hoitourakan-2021-2026-id)}
        hk-alkupvm (pvm/->pvm "1.10.2021")
        hk-loppupvm (pvm/->pvm "31.12.2021")
        sanktiot-sakon-jalkeen (palvelukutsu-tallenna-suorasanktio
                                 +kayttaja-jvh+ sanktio laatupoikkeama hk-alkupvm hk-loppupvm)
        lisatty-hoidon-sakko (first (filter #(= (get-in % [:laatupoikkeama :paatos :perustelu]) perustelu)
                                            sanktiot-sakon-jalkeen))]
    (is (number? (:id lisatty-hoidon-sakko)) "Tallennus palauttaa uuden id:n")
    (is (= :A (:laji lisatty-hoidon-sakko)) "Hoitourakan bonuksen oikea sanktiolaji")
    (is (= "Talvihoito, päätiet" (:nimi (:tyyppi lisatty-hoidon-sakko))) "Hoitourakan sakon oikea sanktiotyyppi")
    (is (= -777.0 (:summa lisatty-hoidon-sakko)) "Hoitourakan sakon oikea summa")
    (is (nil? (:indeksi lisatty-hoidon-sakko)) "Indeksi oltava nil koska MHU jonka alkuvuosi > 2020")
    (is (= (hae-iin-maanteiden-hoitourakan-2021-2026-id) (get-in lisatty-hoidon-sakko [:laatupoikkeama :urakka])) "Hoitourakan sanktiorunko-hoito oikea summa")
    (is (= perustelu (get-in lisatty-hoidon-sakko [:laatupoikkeama :paatos :perustelu])) "Hoitourakan sanktiorunko-hoito oikea summa")


    ;; Siivoa roskat
    (poista-sanktio-perustelulla perustelu)))

(deftest tallenna-suorasanktio-ei-salli-vaaran-urakkatyypin-sanktiolajia
  (let [perustelu "ABC gorilla gävelee"
        perintapvm (pvm/->pvm-aika "3.1.2017 22:00:00")
        sanktiorunko {:suorasanktio true
                      :perintapvm perintapvm}
        hoidon-sakko (merge sanktiorunko {:laji :yllapidon_sakko :summa 1665.9 :tyyppi
                                          {:id (ffirst (q (str "SELECT id FROM sanktiotyyppi where koodi = 13;"))),
                                           :nimi "Talvihoito, päätiet"}})
        paallystys-sakko (merge sanktiorunko {:laji :A :summa 1234 :tyyppi {:id 4 :nimi "Ylläpidon sakko"}})
        laatupoikkeama-hoito {:tekijanimi "Järjestelmä Vastaava"
                              :paatos {:paatos "sanktio", :kasittelyaika (pvm/->pvm-aika "2.1.2017 22:00:00"), :kasittelytapa :kommentit, :perustelu perustelu}
                              :aika (pvm/->pvm-aika "1.1.2017 08:00:00"), :urakka (hae-oulun-alueurakan-2014-2019-id)}
        laatupoikkeama-paallystys {:tekijanimi "Järjestelmä Vastaava"
                                   :paatos {:paatos "sanktio", :kasittelyaika (pvm/->pvm-aika "2.1.2017 22:00:00"), :kasittelytapa :kommentit, :perustelu perustelu}
                                   :aika (pvm/->pvm-aika "1.1.2017 08:00:00"), :urakka (hae-urakan-id-nimella "Muhoksen päällystysurakka"),
                                   :yllapitokohde (hae-muhoksen-paallystysurakan-testikohteen-id)}
        hk-alkupvm (pvm/->pvm "1.1.2017")
        hk-loppupvm (pvm/->pvm "31.12.2017")]

    (testing "tallenna-suorasanktio-ei-salli-vaaran-urakkatyypin-sanktiolajia"
      (is (thrown? Exception (palvelukutsu-tallenna-suorasanktio
                               +kayttaja-jvh+ hoidon-sakko laatupoikkeama-hoito hk-alkupvm hk-loppupvm)))

      (is (thrown? Exception (palvelukutsu-tallenna-suorasanktio
                               +kayttaja-jvh+ paallystys-sakko laatupoikkeama-paallystys hk-alkupvm hk-loppupvm))))

    ;; Siivoa roskat
    (poista-sanktio-perustelulla perustelu)))

(deftest paivita-eri-urakan-suorasanktiota
  (let [perustelu "ABC möhöfantti kävelee"
        perintapvm (pvm/->pvm-aika "3.1.2017 22:00:00")
        sanktiorunko {:suorasanktio true
                      :perintapvm perintapvm
                      :id 1} ;sanktio 1 kuuluu urakkaan 4
        paallystys-sakko (merge sanktiorunko {:laji :A :summa 1234 :tyyppi {:id 4 :nimi "Ylläpidon sakko"}})
        laatupoikkeama-paallystys {:tekijanimi "Järjestelmä Vastaava"
                                   :paatos {:paatos "sanktio", :kasittelyaika (pvm/->pvm-aika "2.1.2017 22:00:00"), :kasittelytapa :kommentit, :perustelu perustelu}
                                   :aika (pvm/->pvm-aika "1.1.2017 08:00:00"), :urakka (hae-urakan-id-nimella "Muhoksen päällystysurakka"),
                                   :yllapitokohde (hae-muhoksen-paallystysurakan-testikohteen-id)}
        hk-alkupvm (pvm/->pvm "1.1.2017")
        hk-loppupvm (pvm/->pvm "31.12.2017")]

    (testing "paivita-eri-urakan-suorasanktiota"
      (is (thrown? Exception (palvelukutsu-tallenna-suorasanktio
                               +kayttaja-jvh+ paallystys-sakko laatupoikkeama-paallystys hk-alkupvm hk-loppupvm))))

    ;; Siivoa roskat
    (poista-sanktio-perustelulla perustelu)))

(deftest suorasanktion-poistaminen-vs-laatupoikkeamaan-liitetyn-sanktion-poistaminen
  (let [perustelu "ABC lehmä lepäilee"
        lp-aika (pvm/->pvm-aika "1.1.2017 08:00:00")
        lp-aika2 (pvm/->pvm-aika "1.1.2017 09:00:00")
        perintapvm (pvm/->pvm "3.1.2017")
        sanktiorunko {:toimenpideinstanssi (hae-oulun-alueurakan-talvihoito-tpi-id)
                      :perintapvm perintapvm}
        hoidon-sakko-suorasanktio (merge sanktiorunko {:suorasanktio true :laji :A :summa 637.27
                                                       :tyyppi {:id (ffirst (q (str "SELECT id FROM sanktiotyyppi where koodi = 13;")))
                                                                :nimi "Talvihoito, päätiet"}})
        hoidon-sakko-laatupoikkeamaan-liittyva (merge sanktiorunko {:suorasanktio false :laji :A :summa 200.27
                                                                    :tyyppi {:id (ffirst (q (str "SELECT id FROM sanktiotyyppi where koodi = 13;")))
                                                                             :nimi "Talvihoito, päätiet"}})
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
        lisatty-hoidon-sakko (first (filter #(= -637.27 (:summa %)) sanktiot-suorasanktion-jalkeen))
        lisatyn-sanktion-id (:id lisatty-hoidon-sakko)
        lisatyn-laatupoikkeaman-id (:id (:laatupoikkeama lisatty-hoidon-sakko))
        lisatty-hoidon-sakko-lp (first (filter #(= -200.27 (:summa %)) sanktiot-lp-liittyvan-sanktion-jalkeen))
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
          poistettu-hoidon-sakko (first (filter #(= -637.27 (:summa %)) sanktiot-suorasanktion-poistamisen-jalkeen))]
      (testing "poista-suorasanktio-hoitourakassa"
        (is (and (number? (:id lisatty-hoidon-sakko)) (number? (:id poistettu-suorasanktio-kannassa))) "Tallennus palauttaa uuden id:n")
        (is (= :A (:laji lisatty-hoidon-sakko) (keyword (:laji poistettu-suorasanktio-kannassa))) "Hoitourakan bonuksen oikea sanktiolaji")
        (is (not= true (:poistettu lisatty-hoidon-sakko)) "Sakkoa ei poistettu")
        (is (nil? poistettu-hoidon-sakko) "Sakko poistettu")
        (is (and (= true (:poistettu poistettu-suorasanktio-kannassa))
                 (= true (:lp_poistettu poistettu-suorasanktio-kannassa))))
        (is (= lp-aika (get-in lisatty-hoidon-sakko [:laatupoikkeama :aika]) (:lp_aika poistettu-suorasanktio-kannassa)))
        (is (= "Talvihoito, päätiet" (:nimi (:tyyppi lisatty-hoidon-sakko))) "Hoitourakan sakon oikea sanktiotyyppi")
        (is (=marginaalissa? -637.27 (:summa lisatty-hoidon-sakko) (:summa poistettu-suorasanktio-kannassa)) "Hoitourakan sakon oikea summa")
        (is (= (hae-oulun-alueurakan-2014-2019-id) (get-in lisatty-hoidon-sakko [:laatupoikkeama :urakka])) "Hoitourakan sanktiorunko-hoito oikea summa")
        (is (= perustelu (get-in lisatty-hoidon-sakko [:laatupoikkeama :paatos :perustelu])) "Hoitourakan sanktiorunko-hoito oikea summa"))

      (testing "laatupoikkeamaan-liitetyn-sanktion-poistaminen-ei-poista-laatupoikkeamaa"
        (is (and (= true (:poistettu poistettu-lp-sanktio-kannassa))
                 (= false (:lp_poistettu poistettu-lp-sanktio-kannassa))))))

    ;; Siivoa roskat
    (poista-sanktio-perustelulla perustelu)))


(deftest hae-laatupoikkeaman-tiedot
  (let [urakka-id (hae-urakan-id-nimella "Oulun alueurakka 2005-2012")
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-laatupoikkeaman-tiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                                            :laatupoikkeama-id 1})]
    (is (not (empty? vastaus)))
    (is (string? (:kuvaus vastaus)))
    (is (>= (count (:kuvaus vastaus)) 10))))

(deftest hae-urakan-laatupoikkeamat
  (let [urakka-id (hae-urakan-id-nimella "Oulun alueurakka 2005-2012")
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
                                :hae-urakan-sanktiot-ja-bonukset +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                 :alku (pvm/luo-pvm 2015 10 1)
                                                                                 :loppu (pvm/luo-pvm 2016 10 30)
                                                                                 :hae-bonukset? false})]
    (is (not (empty? vastaus)))
    (is (>= (count vastaus) 8))))

(def maarapaivan-ylitys-sanktiotyyppi (first (q-map "SELECT id, toimenpidekoodi, nimi, koodi FROM sanktiotyyppi WHERE nimi = 'Määräpäivän ylitys'")))

(def odotettu-urakan-jalkeinen-sanktio
  [{:yllapitokohde {:tr {:loppuetaisyys nil, :loppuosa nil, :numero nil, :alkuetaisyys nil, :alkuosa nil}, :numero nil, :id nil, :nimi nil}
    :suorasanktio false, :laji :C, :indeksikorjaus nil
    :laatupoikkeama {:sijainti {:type :point, :coordinates [418237.0 7207744.0]},
                     :kuvaus "Sanktion sisältävä laatupoikkeama 5b", :aika #inst "2019-10-10T21:06:06.370000000-00:00",
                     :tr {:alkuetaisyys 5, :loppuetaisyys 4, :numero 1, :loppuosa 3, :alkuosa 2}
                     :selvityspyydetty false, :urakka 4, :tekija "tilaaja", :kohde "Testikohde", :id 16, :tarkastuspiste 123, :tekijanimi " ", :selvitysannettu false,
                     :paatos {:paatos "hylatty", :perustelu "Ei tässä ole mitään järkeä", :kasittelyaika #inst "2019-10-10T21:06:06.370000000-00:00", :kasittelytapa :puhelin, :muukasittelytapa ""}}

    :summa -777.0, :indeksi "MAKU 2005", :toimenpideinstanssi 5, :id 7, :perintapvm #inst "2019-10-11T21:00:00.000-00:00", :tyyppi maarapaivan-ylitys-sanktiotyyppi, :vakiofraasi nil}])


(deftest hae-urakan-jalkeiset-sanktiot
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-sanktiot-ja-bonukset +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                 :alku (pvm/luo-pvm 2018 10 1)
                                                                                 :loppu (pvm/luo-pvm 2019 10 30)
                                                                                 :vain-yllapitokohteettomat? nil})]
    (is (= vastaus odotettu-urakan-jalkeinen-sanktio))))

;;  Tämä testi varmistaa, että hoidon alueurakoissa (urakan tyyppi = 'hoito'), jotka ovat alkaneet 2018 tai aiemmin, sanktioiden indeksilaskenta menee oikein ja samalla tavalla sanktiopalvelussa, laskutusyhteenvedossa ja sanktioraportissa.
(deftest urakka-alkaen-2018-tai-ennen-indeksikorotus-perintapvm-pisteluvun-mukaan
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        alkupvm (pvm/luo-pvm 2016 9 1)
        loppupvm (pvm/luo-pvm 2017 8 30)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-sanktiot-ja-bonukset +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                 :alku alkupvm
                                                                                 :loppu loppupvm
                                                                                 :vain-yllapitokohteettomat? nil})
        sanktio-100e (first (filter #(= -100.0 (:summa %)) vastaus))
        summat-yhteensa-hae-sanktiot-palvelusta (- (reduce + 0 (remove nil? (map :summa vastaus))))
        indeksikorotukset-yhteensa-hae-sanktiot-palvelusta (- (reduce + 0 (remove nil? (map :indeksikorjaus vastaus))))

        sanktioraportti (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :suorita-raportti
                                        +kayttaja-jvh+
                                        {:nimi :sanktioraportti
                                         :konteksti "urakka"
                                         :urakka-id urakka-id
                                         :parametrit {:urakkatyyppi :hoito
                                                      :alkupvm alkupvm
                                                      :loppupvm loppupvm}})
        sanktioraportti-yhteensa-rivi (last (last sanktioraportti))
        sanktioraportti-sakot-yhteensa (last (last (butlast sanktioraportti-yhteensa-rivi)))
        sanktioraportti-indeksit-yhteensa (last (last (butlast (butlast sanktioraportti-yhteensa-rivi))))

        laskutusyhteenvedosta-samat-sanktiot (map
                                               #(select-keys % [:sakot_laskutetaan
                                                                :sakot_laskutetaan_ind_korotus
                                                                :sakot_laskutetaan_ind_korotettuna])
                                               (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot
                                                 (:db jarjestelma)
                                                 +kayttaja-jvh+
                                                 {:urakka-id urakka-id
                                                  :alkupvm alkupvm
                                                  :loppupvm loppupvm}))
        sakkojen-indeksikorotukset-yhteensa-laskutusyhteenvedosta (reduce + 0 (remove nil? (map :sakot_laskutetaan_ind_korotus laskutusyhteenvedosta-samat-sanktiot)))]
    (is (= (count vastaus) 8) "Sanktioita odotettu määrä testikannassa.")

    (is (= (fmt/desimaaliluku summat-yhteensa-hae-sanktiot-palvelusta 2)
           (fmt/desimaaliluku sanktioraportti-sakot-yhteensa 2)
           "1900,67")
      "Sanktioiden summat palvelusta.")

    (is (= (fmt/desimaaliluku indeksikorotukset-yhteensa-hae-sanktiot-palvelusta 2)
           (fmt/desimaaliluku sanktioraportti-indeksit-yhteensa 2)
           "571,66")
      "Kaikki indeksikorotkset summattuna hae-sanktiot palvelusta")

    (is (= (fmt/desimaaliluku sakkojen-indeksikorotukset-yhteensa-laskutusyhteenvedosta 2) "−571,66")
      "Kaikki indeksikorotkset summattuna laskutusyhteenvedosta")

    (is (= (:summa sanktio-100e) -100.0) "sanktion summa palautuu oikein")
    (is (= (:indeksikorjaus sanktio-100e) -30.07662835249042) "sanktion indeksikorjaus laskettu oikein")))


;;  Tämä testi varmistaa, että hoidon MHU-urakoissa (urakan tyyppi = 'teiden-hoito'), jotka ovat alkaneet 2019 tai 2020, sanktioiden indeksilaskenta menee oikein ja samalla tavalla sanktiopalvelussa, laskutusyhteenvedossa ja sanktioraportissa.
(deftest urakka-2019-alkaen-ed-syyskuun-indeksikorotus
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        alkupvm (pvm/luo-pvm 2020 2 1)
        loppupvm (pvm/luo-pvm 2020 2 31)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakan-sanktiot-ja-bonukset +kayttaja-jvh+ {:urakka-id urakka-id
                                                                   :alku alkupvm
                                                                   :loppu loppupvm
                                                                   :vain-yllapitokohteettomat? nil
                                                                   ;; Haetaan vain sanktiot sanktioraporttiin
                                                                   ;; vertailua varten
                                                                   :hae-sanktiot? true
                                                                   :hae-bonukset? false})
        vastaus-bonuksineen (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-urakan-sanktiot-ja-bonukset +kayttaja-jvh+ {:urakka-id urakka-id
                                                                               :alku alkupvm
                                                                               :loppu loppupvm
                                                                               :vain-yllapitokohteettomat? nil
                                                                               :hae-sanktiot? true
                                                                               :hae-bonukset? true})
        ei-bonus-pred #(not (str/includes? (name (:laji %)) "bonus"))
        sanktio (first vastaus)
        summat-yhteensa-hae-sanktiot-palvelusta (reduce + 0 (remove nil? (map :summa vastaus)))
        indeksikorotukset-yhteensa-hae-sanktiot-palvelusta (reduce + 0 (remove nil? (map :indeksikorjaus vastaus)))

        sanktioraportti (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :suorita-raportti
                                        +kayttaja-jvh+
                                        {:nimi :sanktioraportti
                                         :konteksti "urakka"
                                         :urakka-id urakka-id
                                         :parametrit {:urakkatyyppi :teiden-hoito
                                                      :alkupvm alkupvm
                                                      :loppupvm loppupvm}})
        sanktioraportti-yhteensa-rivi (last (last sanktioraportti))
        sanktioraportti-sakot-yhteensa (last (last (butlast sanktioraportti-yhteensa-rivi)))
        sanktioraportti-indeksit-yhteensa (last (last (butlast (butlast sanktioraportti-yhteensa-rivi))))

        laskutusyhteenvedosta-samat-sanktiot (map
                                               :sakot_laskutetaan
                                               (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot
                                                 (:db jarjestelma)
                                                 +kayttaja-jvh+
                                                 {:urakka-id urakka-id
                                                  :alkupvm alkupvm
                                                  :loppupvm loppupvm
                                                  :urakkatyyppi "teiden-hoito"}))
        sakot-indeksikorotuksineen-laskutusyhteenvedosta (reduce + 0 (remove nil? laskutusyhteenvedosta-samat-sanktiot))]
    (is (= (count vastaus-bonuksineen) 3))
    (is (= (filter ei-bonus-pred vastaus-bonuksineen) vastaus))
    (is (= (count vastaus) 1))
    (is (= (:summa sanktio) -100.2) "sanktion summa palautuu oikein")
    (is (= (:indeksikorjaus sanktio) -8.1162) "sanktion indeksikorjaus laskettu oikein")
    (is (= (fmt/desimaaliluku (- summat-yhteensa-hae-sanktiot-palvelusta) 2)
          (fmt/desimaaliluku sanktioraportti-sakot-yhteensa 2)
          "100,20") "Sanktioiden summat palvelusta.")
    (is (= (fmt/desimaaliluku (- indeksikorotukset-yhteensa-hae-sanktiot-palvelusta) 2)
          (fmt/desimaaliluku sanktioraportti-indeksit-yhteensa 2)
          "8,12") "Kaikki indeksikorotkset summattuna hae-sanktiot palvelusta")
    (is (= (fmt/desimaaliluku (- sakot-indeksikorotuksineen-laskutusyhteenvedosta) 2) "108,32") "Kaikki indeksikorotkset summattuna laskutusyhteenvedosta")

    (is (= (fmt/desimaaliluku (+ (:summa sanktio) (:indeksikorjaus sanktio)) 3)
           (fmt/desimaaliluku (double sakot-indeksikorotuksineen-laskutusyhteenvedosta) 3)) "sanktiopalvelu ja laskutusyhteenveto antaa saman summat")))

;;  Tämä testi varmistaa, että hoidon MHU-urakoissa (urakan tyyppi = 'teiden-hoito'), jotka ovat alkaneet 2021 tai jälkeen, sanktioille EI TEHDÄ minkäänlaista indeksikorotusta. Varmistetaan että tämä menee oikein ja samalla tavalla sanktiopalvelussa, laskutusyhteenvedossa ja sanktioraportissa.
(deftest mhu-urakka-2021-alkaen-sanktioille-ei-indeksikorotusta
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        alkupvm (pvm/luo-pvm 2022 8 1)
        loppupvm (pvm/luo-pvm 2022 8 30)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-sanktiot-ja-bonukset +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                 :alku alkupvm
                                                                                 :loppu loppupvm
                                                                                 :vain-yllapitokohteettomat? nil})
        sanktio (first vastaus)
        summat-yhteensa-hae-sanktiot-palvelusta (- (reduce + 0 (remove nil? (map :summa vastaus))))
        indeksikorotukset-yhteensa-hae-sanktiot-palvelusta (- (reduce + 0 (remove nil? (map :indeksikorjaus vastaus))))

        sanktioraportti (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :suorita-raportti
                                        +kayttaja-jvh+
                                        {:nimi :sanktioraportti
                                         :konteksti "urakka"
                                         :urakka-id urakka-id
                                         :parametrit {:urakkatyyppi :teiden-hoito
                                                      :alkupvm alkupvm
                                                      :loppupvm loppupvm}})
        sanktioraportti-yhteensa-rivi (last (last sanktioraportti))
        sanktioraportti-sakot-yhteensa (last (last (butlast sanktioraportti-yhteensa-rivi)))
        sanktioraportti-indeksit-yhteensa (last (last (butlast (butlast sanktioraportti-yhteensa-rivi))))

        laskutusyhteenvedosta-samat-sanktiot (map
                                               :sakot_laskutetaan
                                               (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot
                                                 (:db jarjestelma)
                                                 +kayttaja-jvh+
                                                 {:urakka-id urakka-id
                                                  :alkupvm alkupvm
                                                  :loppupvm loppupvm
                                                  :urakkatyyppi "teiden-hoito"}))
        sakot-indeksikorotuksineen-laskutusyhteenvedosta (reduce + 0 (remove nil? laskutusyhteenvedosta-samat-sanktiot))]
    (is (= (count vastaus) 1))
    (is (= (:summa sanktio) -1000.0) "sanktion summa palautuu oikein")
    (is (= (:indeksikorjaus sanktio) 0.0) "sanktion indeksikorjaus laskettu oikein")
    (is (= (fmt/desimaaliluku summat-yhteensa-hae-sanktiot-palvelusta 2)
           (fmt/desimaaliluku sanktioraportti-sakot-yhteensa 2)
           "1000,00") "Sanktioiden summat palvelusta.")
    (is (= (fmt/desimaaliluku indeksikorotukset-yhteensa-hae-sanktiot-palvelusta 2)
           (fmt/desimaaliluku sanktioraportti-indeksit-yhteensa 2)
           "0,00") "Kaikki indeksikorotkset summattuna hae-sanktiot palvelusta")
    (is (= (fmt/desimaaliluku sakot-indeksikorotuksineen-laskutusyhteenvedosta 2) "−1000,00") "Kaikki indeksikorotkset summattuna laskutusyhteenvedosta")

    (is (= (fmt/desimaaliluku (+ (:summa sanktio) (:indeksikorjaus sanktio)) 3)
           (fmt/desimaaliluku (Math/abs (double sakot-indeksikorotuksineen-laskutusyhteenvedosta)) 3)) "sanktiopalvelu ja laskutusyhteenveto antaa saman summat")))

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
        :hae-urakan-sanktiot-ja-bonukset
        {:urakka-id (hae-oulun-alueurakan-2014-2019-id)
         :alku (pvm/luo-pvm 2015 10 1)
         :loppu (pvm/luo-pvm 2016 10 30)
         :tpi 1})))


(deftest vaadi-sanktio-kuuluu-urakkaan-testit
  (let [kohde-urakka (hae-urakan-id-nimella "Oulun alueurakka 2014-2019")
        kuuluva-sanktio (ffirst (q (str "SELECT id FROM sanktio where maara = 1000 AND perintapvm = '2016-10-12';")))
        kuulumaton-sanktio (ffirst (q (str "SELECT id FROM sanktio where maara = 10000 AND perintapvm = '2011-10-12';")))
        kutsu (partial ls/vaadi-sanktio-kuuluu-urakkaan (:db jarjestelma) kohde-urakka)]
    (testing "Olemattomat id:t"
      (is (nil? (kutsu -1)) "Uutta sanktiota ei pitäisi validoida")
      (is (nil? (kutsu nil)) "Uutta sanktiota ei pitäisi validoida"))

    (testing "Kuulumaton sanktio"
      (is (thrown? SecurityException (kutsu kuulumaton-sanktio)) (str "Sanktio " kuulumaton-sanktio " ei kuulu urakkaan " kohde-urakka)))

    (testing "Kuuluva sanktio"
      (is (nil? (kutsu kuuluva-sanktio)) (str "Sanktio " kuuluva-sanktio " kuuluu urakkaan " kohde-urakka ", eli validoinnin pitäisi vastata nil")))))
