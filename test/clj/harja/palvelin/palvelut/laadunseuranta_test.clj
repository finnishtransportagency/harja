(ns harja.palvelin.palvelut.laadunseuranta-test
  (:require [clojure.test :refer :all]
            [slingshot.slingshot :refer [throw+]]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [slingshot.slingshot :refer [try+]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.laadunseuranta :as ls]
            [harja.palvelin.palvelut.laadunseuranta.bonus-konfiguraatio :as ls-bonus-konfig]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [harja.jms-test :refer [feikki-jms]]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.komponentit.fim-test :refer [+testi-fim+]]
            [harja.palvelin.integraatiot.sms.sms-test :refer [+testi-sms-url+]]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.vayla-rest.sahkoposti :as sahkoposti-api]
            [harja.palvelin.integraatiot.sms.sms-komponentti :as sms]
            [clojure.java.io :as io]
            [harja.palvelin.integraatiot.jms :as jms]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset :as lyv-yhteiset]
            [harja.fmt :as fmt]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [clojure.string :as str]
            [harja.kyselyt.sanktiot :as sanktiot-q]
            [harja.kyselyt.bonus-konfiguraatio :as bonus-konfiguraatio-q]
            [harja.palvelin.palvelut.laadunseuranta.sanktio-konfiguraatio :as ls-sanktio-konfiguraatio]
            [harja.kyselyt.konversio :as konv]
            [harja.tyokalut.testidatan-kaytto :as testidatan-kaytto])
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
                 (fim/->FIM {:url +testi-fim+})
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
          :itmf (feikki-jms "itmf")
          :api-sahkoposti (component/using
                            (sahkoposti-api/->ApiSahkoposti {:api-sahkoposti {:suora? false
                                                                              :sahkoposti-lahetys-url "/harja/api/sahkoposti/xml"
                                                                              :sahkoposti-ja-liite-lahetys-url "/harja/api/sahkoposti-ja-liite/xml"
                                                                              :palvelin "http://localhost:8084"
                                                                              :vastausosoite "harja-ala-vastaa@vayla.fi"}
                                                             :tloik {:toimenpidekuittausjono "Harja.HarjaToT-LOIK.Ack"}})
                            [:http-palvelin :db :integraatioloki :itmf])
          :sms (component/using (sms/luo-tekstiviesti-komponentti
                                  {:url +testi-sms-url+ :apiavain "testiapiavain"})
                 [:http-palvelin :db :integraatioloki])
          :laadunseuranta (component/using
                            (ls/->Laadunseuranta)
                            [:http-palvelin :db :fim :api-sahkoposti :sms])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each jarjestelma-fixture)

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
    (testidatan-kaytto/poista-sanktio-perustelulla "Testi")))

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

(defn palvelukutsu-poista-suorasanktio [kayttaja sanktio-id urakka-id]
  (kutsu-http-palvelua
    :poista-suorasanktio kayttaja {:id sanktio-id
                                   :urakka-id urakka-id}))

(deftest tallenna-suorasanktio-paallystysurakassa-sakko-ja-bonus
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        perustelu "ABC kissa kävelee"
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
                        :aika (pvm/->pvm-aika "1.1.2017 08:00:00"), :urakka urakka-id,
                        :yllapitokohde (hae-muhoksen-paallystysurakan-testikohteen-id)}
        hk-alkupvm (pvm/->pvm "1.1.2017")
        hk-loppupvm (pvm/->pvm "31.12.2017")]
    (testing "Päällystysurakan suorasanktion ja bonuksen tallennus"
      (let [sanktio-id (palvelukutsu-tallenna-suorasanktio
                         +kayttaja-jvh+ sakko laatupoikkeama hk-alkupvm hk-loppupvm)
            sanktiot-ja-bonukset-sakon-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                                 :hae-urakan-sanktiot-ja-bonukset +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                                  :alku hk-alkupvm
                                                                                                  :loppu hk-loppupvm})
            _ (palvelukutsu-tallenna-suorasanktio
                +kayttaja-jvh+ bonus laatupoikkeama hk-alkupvm hk-loppupvm)
            sanktiot-ja-bonukset-bonuksen-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                                    :hae-urakan-sanktiot-ja-bonukset +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                                     :alku hk-alkupvm
                                                                                                     :loppu hk-loppupvm})
            muistutus-id (palvelukutsu-tallenna-suorasanktio
                           +kayttaja-jvh+ muistutus laatupoikkeama hk-alkupvm hk-loppupvm)
            sanktiot-ja-bonukset-muistutuksen-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :hae-urakan-sanktiot-ja-bonukset +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                                         :alku hk-alkupvm
                                                                                                         :loppu hk-loppupvm})
            lisatty-sakko (first (filter #(= -1234.0 (:summa %)) sanktiot-ja-bonukset-sakon-jalkeen))
            lisatty-bonus (first (filter #(= 4321.0 (:summa %)) sanktiot-ja-bonukset-bonuksen-jalkeen))
            lisatty-muistutus (first (filter #(and (= nil (:summa %))) sanktiot-ja-bonukset-muistutuksen-jalkeen))]
        (is (number? (:id lisatty-sakko)) "Tallennus palauttaa uuden id:n")
        (is (= :yllapidon_sakko (:laji lisatty-sakko)) "Päällystysurakan bonuksen oikea sanktiolaji")
        (is (= "Ylläpidon sakko" (:nimi (:tyyppi lisatty-sakko))) "Päällystysurakan sakon oikea sanktiotyyppi")
        (is (= :yllapidon_bonus (:laji lisatty-bonus)) "Päällystysurakan bonuksen oikea bonuslaji")
        (is (= "Ylläpidon muistutus" (:nimi (:tyyppi lisatty-muistutus))) "Päällystysurakan muistutuksen oikea sanktiotyyppi")
        (is (= -1234.0 (:summa lisatty-sakko)) "Päällystysurakan sakon oikea summa")
        (is (= 4321.0 (:summa lisatty-bonus)) "Päällystysurakan bonuksen oikea summa")
        (is (= nil (:summa lisatty-muistutus)) "Päällystysurakan bonuksen oikea summa")
        (is (= (hae-urakan-id-nimella "Muhoksen päällystysurakka") (get-in lisatty-sakko [:laatupoikkeama :urakka])) "Päällystysurakan sanktiorunko oikea summa")
        (is (= perustelu (get-in lisatty-sakko [:laatupoikkeama :paatos :perustelu])) "Päällystysurakan sanktiorunko oikea summa")))

    ;; Siivoa roskat
    (testidatan-kaytto/poista-sanktio-perustelulla perustelu)))

(deftest tallenna-suorasanktio-hoidon-urakassa-sakko
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        perustelu "ABC gorilla gävelee hoidon urakka-alueella"
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
                        :aika (pvm/->pvm-aika "1.1.2017 08:00:00"), :urakka urakka-id}
        hk-alkupvm (pvm/->pvm "1.1.2017")
        hk-loppupvm (pvm/->pvm "31.12.2017")]
    (testing "Hoitourakan suorasanktion tallennus"
      (let [sanktio-id (palvelukutsu-tallenna-suorasanktio
                         +kayttaja-jvh+ hoidon-sakko laatupoikkeama hk-alkupvm hk-loppupvm)
            sanktiot-sakon-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :hae-urakan-sanktiot-ja-bonukset +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                      :alku hk-alkupvm
                                                                                      :loppu hk-loppupvm})
            muistutus-id (palvelukutsu-tallenna-suorasanktio
                           +kayttaja-jvh+ hoidon-muistutus laatupoikkeama hk-alkupvm hk-loppupvm)
            sanktiot-muistutuksen-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                            :hae-urakan-sanktiot-ja-bonukset +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                             :alku hk-alkupvm
                                                                                             :loppu hk-loppupvm})
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
        (is (= perustelu (get-in lisatty-hoidon-sakko [:laatupoikkeama :paatos :perustelu])) "Hoitourakan sanktiorunko-hoito oikea summa")

        (testing "Poista suorasanktio ja siihen liittyvä laatupoikkeama :poista-suorasanktio-rajapinnan kautta"
          (let [poistettu-sanktio-id (palvelukutsu-poista-suorasanktio
                                       +kayttaja-jvh+ (:id lisatty-hoidon-sakko) (hae-oulun-alueurakan-2014-2019-id))
                poistettu-suorasanktio-kannassa (q-sanktio-leftjoin-laatupoikkeama poistettu-sanktio-id)]
            (is (= true (:poistettu poistettu-suorasanktio-kannassa)))
            (is (= true (:lp_poistettu poistettu-suorasanktio-kannassa)))))))


    ;; Siivoa roskat
    (testidatan-kaytto/poista-sanktio-perustelulla perustelu)))

(deftest tallenna-suorasanktio-2021-alkavassa-mhu-urakassa-sakko
  (let [urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
        perustelu "ABC koira kävelee MHU-alueella"
        tpi-id-iin-talvihoito (ffirst (q "SELECT id FROM toimenpideinstanssi where nimi = 'Iin MHU 2021-2026 Talvihoito TP';"))
        ;; asetetaan tähän indeksi jolla on arvo, jotta varmistetaan että backendin varmistus toimii, indeksi pitää siis tässä tapauksessa nillata
        sanktio {:suorasanktio true, :laji :A, :summa 777, :indeksi "MAKU 2015", :toimenpideinstanssi tpi-id-iin-talvihoito, :perintapvm #inst "2021-10-02T21:00:00.000-00:00"
                 :tyyppi {:id (ffirst (q (str "SELECT id FROM sanktiotyyppi where koodi = 13;"))),
                          :nimi "Talvihoito, päätiet", :toimenpidekoodi 618}}
        laatupoikkeama {:tekijanimi "Max Power"
                        :paatos {:paatos "sanktio", :kasittelyaika (pvm/->pvm-aika "2.10.2021 22:00:00"), :kasittelytapa :kommentit, :perustelu perustelu}
                        :aika (pvm/->pvm-aika "1.10.2021 08:00:00"), :urakka urakka-id}
        hk-alkupvm (pvm/->pvm "1.10.2021")
        hk-loppupvm (pvm/->pvm "31.12.2021")
        sanktio-id (palvelukutsu-tallenna-suorasanktio
                     +kayttaja-jvh+ sanktio laatupoikkeama hk-alkupvm hk-loppupvm)
        sanktiot-sakon-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae-urakan-sanktiot-ja-bonukset +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                  :alku hk-alkupvm
                                                                                  :loppu hk-loppupvm})
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
    (testidatan-kaytto/poista-sanktio-perustelulla perustelu)))

(deftest tallenna-suorasanktio-validoi-aktiivisen-sanktio-konfiguraation
  (let [urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
        perustelu "ABC aktiivinen sanktio-konfiguraatio"
        tpi-id-iin-talvihoito (ffirst (q "SELECT id FROM toimenpideinstanssi where nimi = 'Iin MHU 2021-2026 Talvihoito TP';"))
        sanktiotyyppi-id (ffirst (q (str "SELECT id FROM sanktiotyyppi where koodi = 13;")))
        sanktio {:suorasanktio true
                 :laji :A
                 :summa 777
                 :toimenpideinstanssi tpi-id-iin-talvihoito
                 :perintapvm #inst "2021-10-02T21:00:00.000-00:00"
                 :tyyppi {:id sanktiotyyppi-id
                          :nimi "Talvihoito, päätiet"
                          :toimenpidekoodi 618}}
        laatupoikkeama {:tekijanimi "Max Power"
                        :paatos {:paatos "sanktio"
                                 :kasittelyaika (pvm/->pvm-aika "2.10.2021 22:00:00")
                                 :kasittelytapa :kommentit
                                 :perustelu perustelu}
                        :aika (pvm/->pvm-aika "1.10.2021 08:00:00")
                        :urakka urakka-id}
        hk-alkupvm (pvm/->pvm "1.10.2021")
        hk-loppupvm (pvm/->pvm "31.12.2021")
        validointikutsu (atom nil)]
    (try
      (with-redefs [ls-sanktio-konfiguraatio/vaadi-sallittu-sanktiokonfiguraatiorivi
                    (fn [_ params]
                      (reset! validointikutsu params))]
        (palvelukutsu-tallenna-suorasanktio
          +kayttaja-jvh+ sanktio laatupoikkeama hk-alkupvm hk-loppupvm))
      (finally
        (testidatan-kaytto/poista-sanktio-perustelulla perustelu)))
    (is (= {:urakka-id urakka-id
            :hoitovuosi 1
            :soveltuvuuskonteksti :urakka
            :laji :A
            :sanktiotyyppi-id sanktiotyyppi-id}
           @validointikutsu)
      "Suorasanktion tallennuksen pitää validoida aktiivinen sanktio-konfiguraatio oikeassa kontekstissa")))

(deftest tallenna-suorasanktio-ei-kayta-enaa-legacy-validointia-profiilipohjaisessa-polussa
  (let [urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
        perustelu "ABC ei legacy-validointia suorasanktiossa"
        tpi-id-iin-talvihoito (ffirst (q "SELECT id FROM toimenpideinstanssi where nimi = 'Iin MHU 2021-2026 Talvihoito TP';"))
        sanktiotyyppi-id (ffirst (q (str "SELECT id FROM sanktiotyyppi where koodi = 13;")))
        sanktio {:suorasanktio true
                 :laji :A
                 :summa 777
                 :toimenpideinstanssi tpi-id-iin-talvihoito
                 :perintapvm #inst "2021-10-02T21:00:00.000-00:00"
                 :tyyppi {:id sanktiotyyppi-id
                          :nimi "Talvihoito, päätiet"
                          :toimenpidekoodi 618}}
        laatupoikkeama {:tekijanimi "Max Power"
                        :paatos {:paatos "sanktio"
                                 :kasittelyaika (pvm/->pvm-aika "2.10.2021 22:00:00")
                                 :kasittelytapa :kommentit
                                 :perustelu perustelu}
                        :aika (pvm/->pvm-aika "1.10.2021 08:00:00")
                        :urakka urakka-id}
        hk-alkupvm (pvm/->pvm "1.10.2021")
        hk-loppupvm (pvm/->pvm "31.12.2021")
        legacy-validointeja (atom 0)]
    (try
      (with-redefs [ls/vaadi-sanktiolaji-ja-sanktiotyyppi-yhteensopivat
                    (fn [& _]
                      (swap! legacy-validointeja inc))
                    ls-sanktio-konfiguraatio/vaadi-sallittu-sanktiokonfiguraatiorivi
                    (fn [_ _])]
        (palvelukutsu-tallenna-suorasanktio
          +kayttaja-jvh+ sanktio laatupoikkeama hk-alkupvm hk-loppupvm))
      (finally
        (testidatan-kaytto/poista-sanktio-perustelulla perustelu)))
    (is (zero? @legacy-validointeja)
      "Suorasanktion profiilipohjainen write-path ei saa käyttää legacy-validointia")))

(deftest tallenna-laatupoikkeama-validoi-aktiivisen-sanktio-konfiguraation-laatupoikkeama-kontekstissa
  (let [urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
        perustelu "ABC laatupoikkeaman aktiivinen sanktio-konfiguraatio"
        tpi-id-iin-talvihoito (ffirst (q "SELECT id FROM toimenpideinstanssi where nimi = 'Iin MHU 2021-2026 Talvihoito TP';"))
        sanktiotyyppi-id (ffirst (q (str "SELECT id FROM sanktiotyyppi where koodi = 13;")))
        sanktio {:perintapvm #inst "2021-10-02T21:00:00.000-00:00"
                 :laji :A
                 :tyyppi {:id sanktiotyyppi-id
                          :nimi "Talvihoito, päätiet"
                          :toimenpidekoodi 618}
                 :summa 777
                 :suorasanktio false
                 :toimenpideinstanssi tpi-id-iin-talvihoito
                 :vakiofraasi nil}
        laatupoikkeama {:tekijanimi "Max Power"
                        :aika (pvm/->pvm-aika "1.10.2021 08:00:00")
                        :urakka urakka-id
                        :tekija :tilaaja
                        :kuvaus "Kuvaus"
                        :kohde "Kohde"
                        :paatos {:paatos :sanktio
                                 :kasittelytapa :kommentit
                                 :kasittelyaika (pvm/->pvm-aika "2.10.2021 22:00:00")
                                 :perustelu perustelu}
                        :sanktiot [sanktio]}
        validointikutsu (atom nil)]
    (try
      (with-redefs [ls-sanktio-konfiguraatio/vaadi-sallittu-sanktiokonfiguraatiorivi
                    (fn [_ params]
                      (reset! validointikutsu params))]
        (kutsu-http-palvelua :tallenna-laatupoikkeama +kayttaja-jvh+ laatupoikkeama))
      (finally
        (testidatan-kaytto/poista-sanktio-perustelulla perustelu)))
    (is (= {:urakka-id urakka-id
            :hoitovuosi 1
            :soveltuvuuskonteksti :laatupoikkeama
            :laji :A
            :sanktiotyyppi-id sanktiotyyppi-id}
           @validointikutsu)
      "Laatupoikkeaman sanktion tallennuksen pitää validoida aktiivinen sanktio-konfiguraatio laatupoikkeama-kontekstissa")))

(deftest tallenna-laatupoikkeama-ei-kayta-enaa-legacy-validointia-profiilipohjaisessa-polussa
  (let [urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
        perustelu "ABC ei legacy-validointia laatupoikkeamassa"
        tpi-id-iin-talvihoito (ffirst (q "SELECT id FROM toimenpideinstanssi where nimi = 'Iin MHU 2021-2026 Talvihoito TP';"))
        sanktiotyyppi-id (ffirst (q (str "SELECT id FROM sanktiotyyppi where koodi = 13;")))
        sanktio {:perintapvm #inst "2021-10-02T21:00:00.000-00:00"
                 :laji :A
                 :tyyppi {:id sanktiotyyppi-id
                          :nimi "Talvihoito, päätiet"
                          :toimenpidekoodi 618}
                 :summa 777
                 :suorasanktio false
                 :toimenpideinstanssi tpi-id-iin-talvihoito
                 :vakiofraasi nil}
        laatupoikkeama {:tekijanimi "Max Power"
                        :aika (pvm/->pvm-aika "1.10.2021 08:00:00")
                        :urakka urakka-id
                        :tekija :tilaaja
                        :kuvaus "Kuvaus"
                        :kohde "Kohde"
                        :paatos {:paatos :sanktio
                                 :kasittelytapa :kommentit
                                 :kasittelyaika (pvm/->pvm-aika "2.10.2021 22:00:00")
                                 :perustelu perustelu}
                        :sanktiot [sanktio]}
        legacy-validointeja (atom 0)]
    (try
      (with-redefs [ls/vaadi-sanktiolaji-ja-sanktiotyyppi-yhteensopivat
                    (fn [& _]
                      (swap! legacy-validointeja inc))
                    ls-sanktio-konfiguraatio/vaadi-sallittu-sanktiokonfiguraatiorivi
                    (fn [_ _])]
        (kutsu-http-palvelua :tallenna-laatupoikkeama +kayttaja-jvh+ laatupoikkeama))
      (finally
        (testidatan-kaytto/poista-sanktio-perustelulla perustelu)))
    (is (zero? @legacy-validointeja)
      "Laatupoikkeaman profiilipohjainen write-path ei saa käyttää legacy-validointia")))

(deftest tallenna-suorasanktio-ei-validoi-yllapidon-bonusta-aktiivista-sanktio-konfiguraatiota-vasten
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        perustelu "ABC yllapidon bonus ilman write-path-validointia"
        perintapvm (pvm/->pvm-aika "3.1.2017 22:00:00")
        bonus {:suorasanktio true
               :laji :yllapidon_bonus
               :summa -4321
               :toimenpideinstanssi (hae-muhoksen-paallystysurakan-tpi-id)
               :perintapvm perintapvm
               :vakiofraasi :laadunvalvontaan-liittyvien-mittausten-ym-toimien-laiminlyonnit}
        laatupoikkeama {:tekijanimi "Järjestelmä Vastaava"
                        :paatos {:paatos "sanktio"
                                 :kasittelyaika (pvm/->pvm-aika "2.1.2017 22:00:00")
                                 :kasittelytapa :kommentit
                                 :perustelu perustelu}
                        :aika (pvm/->pvm-aika "1.1.2017 08:00:00")
                        :urakka urakka-id
                        :yllapitokohde (hae-muhoksen-paallystysurakan-testikohteen-id)}
        hk-alkupvm (pvm/->pvm "1.1.2017")
        hk-loppupvm (pvm/->pvm "31.12.2017")
        validointeja (atom 0)]
    (try
      (with-redefs [ls-sanktio-konfiguraatio/vaadi-sallittu-sanktiokonfiguraatiorivi
                    (fn [_ _]
                      (swap! validointeja inc))]
        (palvelukutsu-tallenna-suorasanktio
          +kayttaja-jvh+ bonus laatupoikkeama hk-alkupvm hk-loppupvm))
      (finally
        (testidatan-kaytto/poista-sanktio-perustelulla perustelu)))
    (is (zero? @validointeja)
      "Ylläpidon bonus jätetään ensimmäisellä kierroksella write-path-validoinnin ulkopuolelle")))

(deftest tallenna-suorasanktio-hylkaa-profiilin-vastaisen-yhdistelman-domain-virheena
  (let [urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
        perustelu "ABC profiilin vastainen suorasanktio"
        tpi-id-iin-talvihoito (ffirst (q "SELECT id FROM toimenpideinstanssi where nimi = 'Iin MHU 2021-2026 Talvihoito TP';"))
        profiilin-vastainen-tyyppi-id (ffirst (q (str "SELECT id FROM sanktiotyyppi where koodi = 10;")))
        sanktio {:suorasanktio true
                 :laji :A
                 :summa 777
                 :toimenpideinstanssi tpi-id-iin-talvihoito
                 :perintapvm #inst "2021-10-02T21:00:00.000-00:00"
                 :tyyppi {:id profiilin-vastainen-tyyppi-id
                          :nimi "Hallinnolliset laiminlyönnit"
                          :toimenpidekoodi nil}}
        laatupoikkeama {:tekijanimi "Max Power"
                        :paatos {:paatos "sanktio"
                                 :kasittelyaika (pvm/->pvm-aika "2.10.2021 22:00:00")
                                 :kasittelytapa :kommentit
                                 :perustelu perustelu}
                        :aika (pvm/->pvm-aika "1.10.2021 08:00:00")
                        :urakka urakka-id}
        hk-alkupvm (pvm/->pvm "1.10.2021")
        hk-loppupvm (pvm/->pvm "31.12.2021")]
    (try+
      (palvelukutsu-tallenna-suorasanktio +kayttaja-jvh+ sanktio laatupoikkeama hk-alkupvm hk-loppupvm)
      (is false "Suorasanktion tallennuksen pitäisi hylätä profiilin vastainen yhdistelmä")
      (catch [:type :sanktio-kirjausvirhe] {:keys [virheet sanktio-kirjausvirhe]}
        (is (= :sanktiotyyppi-ei-sallittu (:koodi (first virheet))))
        (is (= :sanktiotyyppi-ei-sallittu (:koodi sanktio-kirjausvirhe)))
        (is (= (str "Sanktiolaji: A ei mahdollinen sanktiotyypille id: " profiilin-vastainen-tyyppi-id)
               (:viesti (first virheet)))))
      (catch SecurityException virhe
        (is false (str "Väärä virhetyyppi vuoti ulos legacy-validoinnista: " (.getMessage virhe)))))
    (testidatan-kaytto/poista-sanktio-perustelulla perustelu)))

(deftest tallenna-laatupoikkeama-hylkaa-profiilin-vastaisen-yhdistelman-domain-virheena
  (let [urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
        perustelu "ABC profiilin vastainen laatupoikkeama"
        tpi-id-iin-talvihoito (ffirst (q "SELECT id FROM toimenpideinstanssi where nimi = 'Iin MHU 2021-2026 Talvihoito TP';"))
        profiilin-vastainen-tyyppi-id (ffirst (q (str "SELECT id FROM sanktiotyyppi where koodi = 10;")))
        sanktio {:perintapvm #inst "2021-10-02T21:00:00.000-00:00"
                 :laji :A
                 :tyyppi {:id profiilin-vastainen-tyyppi-id
                          :nimi "Hallinnolliset laiminlyönnit"
                          :toimenpidekoodi nil}
                 :summa 777
                 :suorasanktio false
                 :toimenpideinstanssi tpi-id-iin-talvihoito
                 :vakiofraasi nil}
        laatupoikkeama {:tekijanimi "Max Power"
                        :aika (pvm/->pvm-aika "1.10.2021 08:00:00")
                        :urakka urakka-id
                        :tekija :tilaaja
                        :kuvaus "Kuvaus"
                        :kohde "Kohde"
                        :paatos {:paatos :sanktio
                                 :kasittelytapa :kommentit
                                 :kasittelyaika (pvm/->pvm-aika "2.10.2021 22:00:00")
                                 :perustelu perustelu}
                        :sanktiot [sanktio]}]
    (try+
      (kutsu-http-palvelua :tallenna-laatupoikkeama +kayttaja-jvh+ laatupoikkeama)
      (is false "Laatupoikkeaman tallennuksen pitäisi hylätä profiilin vastainen yhdistelmä")
      (catch [:type :sanktio-kirjausvirhe] {:keys [virheet sanktio-kirjausvirhe]}
        (is (= :sanktiotyyppi-ei-sallittu (:koodi (first virheet))))
        (is (= :sanktiotyyppi-ei-sallittu (:koodi sanktio-kirjausvirhe)))
        (is (= (str "Sanktiolaji: A ei mahdollinen sanktiotyypille id: " profiilin-vastainen-tyyppi-id)
               (:viesti (first virheet)))))
      (catch SecurityException virhe
        (is false (str "Väärä virhetyyppi vuoti ulos legacy-validoinnista: " (.getMessage virhe)))))
    (testidatan-kaytto/poista-sanktio-perustelulla perustelu)))

(deftest vaadi-sallittu-sanktiokonfiguraatiorivi-heittaa-paikallisen-domain-virheen
  (try+
    (with-redefs [ls-sanktio-konfiguraatio/hae-sanktio-profiilin-rivit-kontekstissa
                  (fn [_ _]
                    {:rivit []})]
      (ls-sanktio-konfiguraatio/vaadi-sallittu-sanktiokonfiguraatiorivi
        nil
        {:urakka-id 36
         :hoitovuosi 1
         :soveltuvuuskonteksti :urakka
         :laji :A
         :sanktiotyyppi-id 16})
      (is false "Validaattorin pitäisi heittää domain-virhe, jos laji puuttuu profiilista"))
    (catch [:type :sanktio-kirjausvirhe] {:keys [virheet sanktio-kirjausvirhe]}
      (is (= :sanktiolaji-ei-sallittu (:koodi (first virheet)))
        "Virheen pitää käyttää paikallista domain-koodia, jotta UI voi näyttää ymmärrettävän viestin")
      (is (= :sanktiolaji-ei-sallittu (:koodi sanktio-kirjausvirhe))
        "Lisätiedoissa pitää säilyttää sama domain-koodi myöhempää käyttöä varten")
      (is (= "Sanktiolaji A ei ole sallittu urakan sanktio-konfiguraatiossa."
             (:viesti (first virheet)))
        "Virheen pitää sisältää käyttäjälle näytettävä viesti"))))

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
    (testidatan-kaytto/poista-sanktio-perustelulla perustelu)))

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
    (testidatan-kaytto/poista-sanktio-perustelulla perustelu)))

(deftest suorasanktion-poistaminen-vs-laatupoikkeamaan-liitetyn-sanktion-poistaminen
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        perustelu "ABC lehmä lepäilee"
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
        lisatyn-sanktion-id (palvelukutsu-tallenna-suorasanktio
                              +kayttaja-jvh+ hoidon-sakko-suorasanktio laatupoikkeama-ss hk-alkupvm hk-loppupvm)
        sanktiot-suorasanktion-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :hae-urakan-sanktiot-ja-bonukset +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                          :alku hk-alkupvm
                                                                                          :loppu hk-loppupvm})
        lp-sanktio-id (palvelukutsu-tallenna-suorasanktio
                        +kayttaja-jvh+ hoidon-sakko-laatupoikkeamaan-liittyva laatupoikkeama-lp hk-alkupvm hk-loppupvm)
        sanktiot-lp-liittyvan-sanktion-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                                 :hae-urakan-sanktiot-ja-bonukset +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                                  :alku hk-alkupvm
                                                                                                  :loppu hk-loppupvm})
        lisatty-hoidon-sakko (first (filter #(= -637.27 (:summa %)) sanktiot-suorasanktion-jalkeen))
        lisatyn-laatupoikkeaman-id (:id (:laatupoikkeama lisatty-hoidon-sakko))
        lisatty-hoidon-sakko-lp (first (filter #(= -200.27 (:summa %)) sanktiot-lp-liittyvan-sanktion-jalkeen))
        lisatyn-sanktion-id-lp (:id lisatty-hoidon-sakko-lp)
        lisatyn-laatupoikkeaman-id-lp (:id (:laatupoikkeama lisatty-hoidon-sakko-lp))]
    (let [poistettu-sanktio-id (palvelukutsu-tallenna-suorasanktio
                                 +kayttaja-jvh+
                                 (merge hoidon-sakko-suorasanktio {:id lisatyn-sanktion-id
                                                                   :poistettu true})
                                 (merge laatupoikkeama-ss {:id lisatyn-laatupoikkeaman-id}) hk-alkupvm hk-loppupvm)
          sanktiot-suorasanktion-poistamisen-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                                       :hae-urakan-sanktiot-ja-bonukset +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                                        :alku hk-alkupvm
                                                                                                        :loppu hk-loppupvm})
          poistetun-suorasanktion-id (palvelukutsu-tallenna-suorasanktio
                                       +kayttaja-jvh+
                                       (merge hoidon-sakko-laatupoikkeamaan-liittyva {:id lisatyn-sanktion-id-lp
                                                                                      :poistettu true})
                                       (merge laatupoikkeama-lp {:id lisatyn-laatupoikkeaman-id-lp}) hk-alkupvm hk-loppupvm)
          sanktiot-lp-sanktion-poistamisen-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                                     :hae-urakan-sanktiot-ja-bonukset +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                                      :alku hk-alkupvm
                                                                                                      :loppu hk-loppupvm})
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
    (testidatan-kaytto/poista-sanktio-perustelulla perustelu)))


(deftest hae-laatupoikkeaman-tiedot
  (let [urakka-id (hae-urakan-id-nimella "Oulun alueurakka 2005-2012")
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-laatupoikkeaman-tiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                              :laatupoikkeama-id 3})]
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
  [{:yllapitokohde {:tr {:loppuetaisyys nil, :loppuosa nil, :numero nil, :alkuetaisyys nil, :alkuosa nil}, :numero nil, :id nil, :nimi nil :yhaid nil}
    :suorasanktio false, :laji :C, :maarattypvm #inst"2019-10-10T21:00:00.000-00:00" :indeksikorjaus nil
    :laatupoikkeama {:sijainti {:type :point, :coordinates [418237.0 7207744.0]},
                     :kuvaus "Sanktion sisältävä laatupoikkeama 5b", :aika #inst "2019-10-10T21:06:06.370000000-00:00",
                     :tr {:alkuetaisyys 5, :loppuetaisyys 4, :numero 1, :loppuosa 3, :alkuosa 2}
                     :selvityspyydetty false, :urakka 4, :tekija "tilaaja", :kohde "Testikohde", :id 18, :tarkastuspiste 123, :tekijanimi " ", :selvitysannettu false,
                     :paatos {:paatos "hylatty", :perustelu "Ei tässä ole mitään järkeä", :kasittelyaika #inst "2019-10-10T21:06:06.370-00:00", :kasittelytapa :puhelin, :muukasittelytapa ""}}

    :summa -777.0, :indeksi "MAKU 2005", :toimenpideinstanssi 5,, :kasittelyaika (konv/java-date #inst "2019-10-10T21:06:06.370-00:00") :id 9,
    :perintapvm #inst "2019-10-11T21:00:00.000-00:00",
    :tyyppi maarapaivan-ylitys-sanktiotyyppi, :vakiofraasi nil}])


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
        sanktiotaulukko (nth sanktioraportti 4)
        sanktioraportti-sakot-ilman-indeksia-yhteensa (last (:rivi (nth (last sanktiotaulukko) 36)))
        sanktioraportti-indeksit-yhteensa (last (:rivi (nth (last sanktiotaulukko) 37)))
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
           (fmt/desimaaliluku sanktioraportti-sakot-ilman-indeksia-yhteensa 2)
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
        sanktiotaulukko (nth sanktioraportti 4)
        sanktioraportti-sakot-ilman-indeksia-yhteensa (last (:rivi (nth (last sanktiotaulukko) 35)))
        sanktioraportti-indeksit-yhteensa (last (:rivi (nth (last sanktiotaulukko) 36)))
        sanktioraportti-sakot-yhteensa (last (:rivi (nth (last sanktiotaulukko) 37)))
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
           (fmt/desimaaliluku sanktioraportti-sakot-ilman-indeksia-yhteensa 2)
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
        sanktiotaulukko (nth sanktioraportti 4)
        sanktioraportti-indeksit-yhteensa (last (:rivi (nth (last sanktiotaulukko) 36)))
        sanktioraportti-sakot-yhteensa (last (:rivi (nth (last sanktiotaulukko) 37)))
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
           (fmt/desimaaliluku (double sakot-indeksikorotuksineen-laskutusyhteenvedosta) 3)) "sanktiopalvelu ja laskutusyhteenveto antaa saman summat")))

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

(defn- legacy-rivin-tyyppi [laji]
  (cond
    (#{:muistutus :yllapidon_muistutus :vesivayla_muistutus} laji) :muistutukset
    (= :arvonvahennyssanktio laji) :arvonvahennykset
    (= :yllapidon_bonus laji) :bonukset
    :else :sanktiot))

(defn- legacy-sanktio-konfiguraatio-odotus [db urakka soveltuvuuskonteksti]
  (let [kaikki-sanktiotyypit (sanktiot-q/hae-sanktiotyypit db)
        lajit (case soveltuvuuskonteksti
                :laatupoikkeama (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi (keyword (:tyyppi urakka))
                                                                              :alkupvm (:alkupvm urakka)})
                (sanktio-domain/urakan-sanktiolajit {:tyyppi (keyword (:tyyppi urakka))}))]
    (mapv (fn [jarjestys laji]
            {:laji laji
             :rivin-tyyppi (legacy-rivin-tyyppi laji)
             :jarjestys jarjestys
             :sanktiotyypit (mapv (fn [{:keys [koodi]}]
                                    {:koodi koodi})
                              (sanktio-domain/sanktiolaji->sanktiotyypit laji kaikki-sanktiotyypit (:alkupvm urakka)))})
      (iterate inc 1)
      lajit)))

(defn- supista-sanktio-konfiguraatio [vastaus]
  (mapv (fn [{:keys [laji rivin-tyyppi jarjestys sanktiotyypit]}]
          {:laji laji
           :rivin-tyyppi rivin-tyyppi
           :jarjestys jarjestys
           :sanktiotyypit (mapv (fn [{:keys [koodi]}]
                                  {:koodi koodi})
                            sanktiotyypit)})
    (:sanktio-lajit vastaus)))

(deftest hae-urakan-sanktio-konfiguraatio-vastaa-legacy-odotusta
  (let [tapaukset [{:nimi "Oulun alueurakka 2014-2019"
                    :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                    :soveltuvuuskontekstit [:urakka :laatupoikkeama]}
                   {:nimi "Iin MHU 2021-2026"
                    :urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
                    :soveltuvuuskontekstit [:urakka :laatupoikkeama]}
                   {:nimi "Muhoksen päällystysurakka"
                    :urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
                    :soveltuvuuskontekstit [:urakka :laatupoikkeama]}]]
    (doseq [{:keys [nimi urakka-id soveltuvuuskontekstit]} tapaukset
            soveltuvuuskonteksti soveltuvuuskontekstit]
      (let [urakka (first (q-map (format "SELECT id, tyyppi, alkupvm FROM urakka WHERE id = %s" urakka-id)))
            odotettu (legacy-sanktio-konfiguraatio-odotus (:db jarjestelma) urakka soveltuvuuskonteksti)
            toteutunut (ls-sanktio-konfiguraatio/hae-urakan-sanktio-konfiguraatio
                         (:db jarjestelma)
                         +kayttaja-jvh+
                         {:urakka-id urakka-id
                          :hoitovuosi 1
                          :soveltuvuuskonteksti soveltuvuuskonteksti})]
        (is (= odotettu (supista-sanktio-konfiguraatio toteutunut))
          (str "Uuden resolverin pitää vastata legacy-odotusta tapauksessa " nimi
            " / " soveltuvuuskonteksti))))))

(deftest hae-urakan-sanktio-konfiguraatio-rajapinta-palauttaa-seedatun-profiilin
  (let [urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakan-sanktio-konfiguraatio
                  +kayttaja-jvh+
                  {:urakka-id urakka-id
                   :hoitovuosi 1
                   :soveltuvuuskonteksti :urakka})
        muistutus (first (:sanktio-lajit vastaus))]
    (is (= :teiden-hoito (:urakkatyyppi (:profiili vastaus))))
    (is (= :urakka (:soveltuvuuskonteksti vastaus)))
    (is (= :muistutus (:laji muistutus)))
    (is (= [13 14 17 10]
           (mapv :koodi (:sanktiotyypit muistutus))))))

(deftest hae-urakan-sanktio-konfiguraatio-validoi-soveltuvuuskontekstin
  (let [urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)]
    (testing "Virheellinen soveltuvuuskonteksti aiheuttaa eksplisiittisen validointivirheen"
      (is (thrown-with-msg? IllegalArgumentException
            #"Virheellinen soveltuvuuskonteksti"
            (ls-sanktio-konfiguraatio/hae-urakan-sanktio-konfiguraatio
              (:db jarjestelma)
              +kayttaja-jvh+
              {:urakka-id urakka-id
               :hoitovuosi 1
               :soveltuvuuskonteksti :virheellinen}))))

    (testing "Puuttuva soveltuvuuskonteksti ei saa johtaa hiljaiseen onnistumiseen"
      (is (thrown-with-msg? IllegalArgumentException
            #"Virheellinen soveltuvuuskonteksti"
            (ls-sanktio-konfiguraatio/hae-urakan-sanktio-konfiguraatio
              (:db jarjestelma)
              +kayttaja-jvh+
              {:urakka-id urakka-id
               :hoitovuosi 1
               :soveltuvuuskonteksti nil}))))

    (testing "Oikeustarkistus tapahtuu edelleen ennen datan palautusta"
      (is (thrown-with-msg? Exception
            #"EiOikeutta"
            (kutsu-palvelua (:http-palvelin jarjestelma)
              :hae-urakan-sanktio-konfiguraatio
              +kayttaja-ulle+
              {:urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
               :hoitovuosi 1
               :soveltuvuuskonteksti :urakka}))))))

(deftest hae-urakan-sanktio-konfiguraatio-epaonnistuu-jos-profiili-puuttuu
  (let [urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)]
    (is (thrown-with-msg? IllegalArgumentException
          #"Sanktio-profiilia ei loytynyt"
          (ls-sanktio-konfiguraatio/hae-urakan-sanktio-konfiguraatio
            (:db jarjestelma)
            +kayttaja-jvh+
            {:urakka-id urakka-id
             :hoitovuosi 99
             :soveltuvuuskonteksti :urakka})))))

(deftest hae-urakan-sanktio-konfiguraatio-epaonnistuu-jos-profiileja-on-useita
  (let [urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
        integraatio-id (ffirst (q "SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'"))
        profiili-id (get-in (ls-sanktio-konfiguraatio/hae-urakan-sanktio-konfiguraatio
                              (:db jarjestelma)
                              +kayttaja-jvh+
                              {:urakka-id urakka-id
                               :hoitovuosi 1
                               :soveltuvuuskonteksti :urakka})
                      [:profiili :id])
        profiili (first (q-map (format "SELECT urakkatyyppi, hoitovuosi_alku, hoitovuosi_loppu, alkupvm, loppupvm FROM sanktio_profiili WHERE id = %s" profiili-id)))]
    (try
      (jdbc/insert! (:db jarjestelma) :sanktio_profiili
        {:nimi "iin-mhu-duplikaatti"
         :urakkatyyppi (:urakkatyyppi profiili)
         :hoitovuosi_alku (:hoitovuosi_alku profiili)
         :hoitovuosi_loppu (:hoitovuosi_loppu profiili)
         :alkupvm (:alkupvm profiili)
         :loppupvm (:loppupvm profiili)
         :aktiivinen true
         :luoja integraatio-id
         :muokkaaja integraatio-id})
      (is (thrown-with-msg? IllegalArgumentException
            #"Useita aktiivisia sanktio-profiileja"
            (ls-sanktio-konfiguraatio/hae-urakan-sanktio-konfiguraatio
              (:db jarjestelma)
              +kayttaja-jvh+
              {:urakka-id urakka-id
               :hoitovuosi 1
               :soveltuvuuskonteksti :urakka})))
      (finally
        (u "DELETE FROM sanktio_profiili WHERE nimi = 'iin-mhu-duplikaatti'")))))

(deftest hae-sanktio-profiilit-admin-palauttaa-profiilikeskeisen-listan
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-sanktio-profiilit-admin
                  +kayttaja-jvh+
                  nil)
        profiili (first vastaus)]
    (is (seq vastaus) "Admin-listan pitää palauttaa ainakin seedattu profiili")
    (is (contains? profiili :aktiivinen) "Listassa pitää näkyä aktiivisuustieto")
    (is (contains? profiili :yhteenveto) "Listassa pitää näkyä käyttöä tukeva yhteenveto")
    (is (contains? profiili :soveltuvuuskontekstit) "Listassa pitää näkyä profiilin kontekstit")
    (is (not (contains? profiili :profiilirivi-id)) "Lista ei saa vuotaa sisäistä rivitason tietoa frontendille")
    (is (string? (:yhteenveto profiili)) "Yhteenvedon pitää olla frontendille valmis teksti")))

(deftest hae-bonus-profiilit-admin-palauttaa-profiilikeskeisen-listan
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-bonus-profiilit-admin
                  +kayttaja-jvh+
                  nil)
        profiili (first vastaus)]
    (is (seq vastaus) "Admin-listan pitää palauttaa ainakin seedattu bonusprofiili")
    (is (contains? profiili :aktiivinen) "Listassa pitää näkyä aktiivisuustieto")
    (is (contains? profiili :yhteenveto) "Listassa pitää näkyä käyttöä tukeva yhteenveto")
    (is (not (contains? profiili :profiilirivi-id)) "Lista ei saa vuotaa sisäistä rivitason tietoa frontendille")
    (is (string? (:yhteenveto profiili)) "Yhteenvedon pitää olla frontendille valmis teksti")))

(deftest hae-bonus-profiilin-tiedot-admin-palauttaa-hierarkkisen-rakenteen
  (let [profiili-id (ffirst (q "SELECT id FROM bonus_profiili WHERE nimi = 'teiden-hoito-bonus-2021-2024'"))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-bonus-profiilin-detalji-admin
                  +kayttaja-jvh+
                  {:bonus-profiili-id profiili-id})
        lajit (:lajit vastaus)
        laji (first lajit)
        ensimmainen-rivi (first (:rivit laji))]
    (is (= profiili-id (get-in vastaus [:profiili :id])))
    (is (string? (get-in vastaus [:profiili :yhteenveto])) "Profiilin yhteenvedon pitää tulla backendista valmiina")
    (is (= :asiakastyytyvaisyysbonus (:laji laji)) "Seedatun bonuslajin pitää löytyä palautuksesta")
    (is (= "Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta" (:nimi laji))
      "MHU21-24 profiilissa asiakastyytyväisyysbonus pitää näyttää uudella nimellä")
    (is (= #{:asiakastyytyvaisyysbonus :alihankintabonus} (into #{} (map :laji lajit)))
      "MHU21-24 profiilissa pitää olla vain specin mukaiset bonuslajit")
    (is (= :t2-koodi (:toimenpiderajauksen-tyyppi ensimmainen-rivi))
      "Teiden-hoidon seedatun bonusrivin pitää näkyä eksplisiittisesti t2-koodiin rajattuna")
    (is (contains? ensimmainen-rivi :toimenpide-t2-koodi) "Palautetun rakenteen alimman tason pitää näyttää bonusprofiilirivin t2-koodi")
    (is (= "23150" (:toimenpide-t2-koodi ensimmainen-rivi)) "Teiden-hoidon seedatun bonusrivin pitää näyttää 23150-haara")))

(deftest hae-bonus-profiilin-tiedot-admin-palauttaa-kaikki-rajauksen-ilman-sentinelia
  (let [profiili-id (ffirst (q "SELECT id FROM bonus_profiili WHERE nimi = 'hoito-bonus-2021-ja-uudemmat'"))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-bonus-profiilin-detalji-admin
                  +kayttaja-jvh+
                  {:bonus-profiili-id profiili-id})
        ensimmainen-rivi (-> vastaus :lajit first :rivit first)]
    (is (= :kaikki (:toimenpiderajauksen-tyyppi ensimmainen-rivi))
      "Hoitoprofiilin seedatun bonusrivin pitää näkyä eksplisiittisesti kaikki-rajauksena")
    (is (nil? (:toimenpide-t2-koodi ensimmainen-rivi))
      "Kaikki-rajauksessa t2-koodin pitää olla aidosti puuttuva, ei sentinel-merkkijono")
    (is (= "Kaikki" (:toimenpideinstanssi-teksti ensimmainen-rivi))
      "Backendin pitää muodostaa admin-näkymälle valmiiksi tulkittu teksti kaikki-rajauksesta")))

(deftest hae-bonus-profiilin-tiedot-admin-palauttaa-mhu2025-profiilin-mukaiset-bonuslajit
  (let [profiili-id (ffirst (q "SELECT id FROM bonus_profiili WHERE nimi = 'teiden-hoito-bonus-mhu2025'"))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-bonus-profiilin-detalji-admin
                  +kayttaja-jvh+
                  {:bonus-profiili-id profiili-id})
        lajit (:lajit vastaus)
        laji (first lajit)]
    (is (= profiili-id (get-in vastaus [:profiili :id])))
    (is (= 1 (count lajit)) "MHU25 profiilissa pitää olla vain yksi manuaalinen bonuslaji")
    (is (= :asiakastyytyvaisyysbonus (:laji laji)))
    (is (= "Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta" (:nimi laji))
      "MHU25 profiilissa sama looginen bonus pitää näyttää uudella nimellä")
    (is (= "Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta" (:masterdatan-nimi laji))
      "MHU25 profiilissa masterdatan nimen pitää vastata uutta kanonista nimeä")
    (is (= false (:uudelleennimetty laji))
      "MHU25 profiilissa kanoninen nimi ei saa näkyä uudelleennimeämisenä")
    (is (nil? (:uudelleennimeaminen laji))
      "MHU25 profiilin ei pidä näyttää uudelleennimeämisen kuvausta, kun nimi tulee masterdatasta")))

(deftest hae-bonus-profiilin-tiedot-admin-palauttaa-mhu2026-profiilin-uudet-bonuslajit
  (let [profiili-id (ffirst (q "SELECT id FROM bonus_profiili WHERE nimi = 'teiden-hoito-bonus-mhu2026'"))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-bonus-profiilin-detalji-admin
                  +kayttaja-jvh+
                  {:bonus-profiili-id profiili-id})
        lajit (:lajit vastaus)
        liikennevahinko-laji (first (filter #(= :liikennevahinkojen_aiheuttajien_selvitysbonus (:laji %)) lajit))]
    (is (= profiili-id (get-in vastaus [:profiili :id])))
    (is (= #{:asiakastyytyvaisyysbonus
             :alihankkijatyytyvaisyyskyselybonus
             :maaraaikaan_tehtavien_toiden_aiempi_toteutusbonus
             :liikennevahinkojen_aiheuttajien_selvitysbonus}
           (into #{} (map :laji lajit)))
      "MHU26 profiilissa pitää olla specin mukaiset uudet bonuslajit")
    (is (= "Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta"
           (:nimi (first (filter #(= :asiakastyytyvaisyysbonus (:laji %)) lajit))))
      "MHU26 profiilissa vanha asiakastyytyväisyysbonus pitää näyttää uudella nimellä")
    (is (= 1 (count (:rivit liikennevahinko-laji))) "Urakkakohtainen bonus kuvataan yhtenä profiilirivinä")
    (is (contains? (first (:rivit liikennevahinko-laji)) :urakkarajausten-maara)
      "Urakkakohtaisen bonusrivin pitää palauttaa urakkarajausten määrä adminiin")
    (is (contains? (first (:rivit liikennevahinko-laji)) :urakat)
      "Admin-rajapinnan palautuksen pitää sisältää myös urakkarajausten nimet visualisointia varten")))

(deftest hae-bonus-profiilin-rivit-admin-palauttaa-urakkarajaukset-nimilla
  (let [integraatio-id (ffirst (q "SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'"))
        bonus-laji-id (ffirst (q "SELECT id FROM bonus_laji WHERE koodi = 'asiakastyytyvaisyysbonus'"))
        urakat (q-map "SELECT id, COALESCE(lyhyt_nimi, nimi) AS nimi
                         FROM urakka
                        WHERE nimi IN ('Iin MHU 2021-2026', 'Raahen MHU 2023-2028')
                     ORDER BY COALESCE(lyhyt_nimi, nimi)")
        urakka-idt (mapv :id urakat)
        odotetut-urakat (mapv :nimi urakat)
        profiili-id (-> (jdbc/insert! (:db jarjestelma) :bonus_profiili
                          {:nimi "bonus-admin-urakkarajaus-visualisointi-testi"
                           :urakkatyyppi "teiden-hoito"
                           :hoitovuosi_alku 1
                           :hoitovuosi_loppu 20
                           :alkupvm #inst "2026-10-01T00:00:00.000-00:00"
                           :loppupvm nil
                           :aktiivinen true
                           :luoja integraatio-id
                           :muokkaaja integraatio-id})
                      first
                      :id)]
    (try
      (u (str "INSERT INTO bonus_profiili_rivi (bonus_profiili_id, bonus_laji_id, toimenpiderajauksen_tyyppi, toimenpide_t2_koodi, jarjestys, aktiivinen, luoja, luotu, muokkaaja, muokattu) VALUES ("
           profiili-id ", " bonus-laji-id ", 't2-koodi', '23150', 1, TRUE, "
           integraatio-id ", CURRENT_TIMESTAMP, " integraatio-id ", CURRENT_TIMESTAMP)"))
      (let [profiilirivi-id (ffirst (q (str "SELECT id FROM bonus_profiili_rivi WHERE bonus_profiili_id = " profiili-id)))]
        (doseq [urakka-id urakka-idt]
          (u (str "INSERT INTO bonus_profiili_rivi_urakka (bonus_profiili_rivi_id, urakka_id, luoja, luotu, muokkaaja, muokattu) VALUES ("
               profiilirivi-id ", " urakka-id ", " integraatio-id ", CURRENT_TIMESTAMP, " integraatio-id ", CURRENT_TIMESTAMP)")))
        (let [rivit (bonus-konfiguraatio-q/hae-bonus-profiilin-rivit-admin (:db jarjestelma) {:bonus_profiili_id profiili-id})
              rivi (first rivit)]
          (is (= 1 (count rivit))
            "Custom-profiilin kyselyn pitää palauttaa yksi bonusprofiilirivi")
          (is (= 2 (get-in rivi [:profiilirivi :urakkarajausten-maara]))
            "Urakkarajausten määrä pitää näkyä kyselykerroksessa oikein")
          (is (= (set odotetut-urakat) (set (get-in rivi [:profiilirivi :urakat])))
            "Kyselykerroksen pitää palauttaa rajatut urakat nimillä")))
      (finally
        (u (str "DELETE FROM bonus_profiili_rivi_urakka WHERE bonus_profiili_rivi_id IN (SELECT id FROM bonus_profiili_rivi WHERE bonus_profiili_id = " profiili-id ")"))
        (u (str "DELETE FROM bonus_profiili_rivi WHERE bonus_profiili_id = " profiili-id))
        (u (str "DELETE FROM bonus_profiili WHERE id = " profiili-id))))))

(deftest hae-bonus-profiilin-tiedot-admin-kayttaa-profiilin-lajin-nayttonimea
  (let [integraatio-id (ffirst (q "SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'"))
        bonus-laji-id (ffirst (q "SELECT id FROM bonus_laji WHERE koodi = 'asiakastyytyvaisyysbonus'"))
        nayttonimi "Bonus testiprofiilin poikkeavalla nimella"
        profiili-id (-> (jdbc/insert! (:db jarjestelma) :bonus_profiili
                          {:nimi "bonus-admin-nayttonimi-testi"
                           :urakkatyyppi "teiden-hoito"
                           :hoitovuosi_alku 1
                           :hoitovuosi_loppu 20
                           :alkupvm #inst "2025-10-01T00:00:00.000-00:00"
                           :loppupvm nil
                           :aktiivinen true
                           :luoja integraatio-id
                           :muokkaaja integraatio-id})
                      first
                      :id)]
    (try
      (u (str "INSERT INTO bonus_profiili_rivi (bonus_profiili_id, bonus_laji_id, toimenpiderajauksen_tyyppi, toimenpide_t2_koodi, jarjestys, aktiivinen, luoja, luotu, muokkaaja, muokattu) VALUES ("
           profiili-id ", "
           bonus-laji-id ", 't2-koodi', '23150', 1, TRUE, "
           integraatio-id ", CURRENT_TIMESTAMP, "
           integraatio-id ", CURRENT_TIMESTAMP)"))
      (u (str "INSERT INTO bonus_profiili_laji_esitystiedot (bonus_profiili_id, bonus_laji_id, nimi, luoja, luotu, muokkaaja, muokattu) VALUES ("
           profiili-id ", "
           bonus-laji-id ", 'Bonus testiprofiilin poikkeavalla nimella', "
           integraatio-id ", CURRENT_TIMESTAMP, "
           integraatio-id ", CURRENT_TIMESTAMP)"))
      (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                      :hae-bonus-profiilin-detalji-admin
                      +kayttaja-jvh+
                      {:bonus-profiili-id profiili-id})
            laji (first (:lajit vastaus))]
        (is (= :asiakastyytyvaisyysbonus (:laji laji)) "Testiprofiilin pitää palauttaa sama looginen bonuslaji")
        (is (= nayttonimi (:nimi laji)) "Profiilirivin näyttönimen pitää ohittaa bonuslajin masterdatan nimi admin-palautuksessa")
        (is (= "Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta" (:masterdatan-nimi laji)) "Admin-palautuksen pitää näyttää myös bonuslajin masterdatan nimi")
        (is (= true (:uudelleennimetty laji)) "Erillinen otsikko pitää tunnistaa uudelleennimeämiseksi")
        (is (= "Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta -> Bonus testiprofiilin poikkeavalla nimella"
               (:uudelleennimeaminen laji))
          "Admin-palautuksen pitää kertoa mistä nimestä bonus on nimetty uudelleen"))
      (finally
        (u (str "DELETE FROM bonus_profiili_laji_esitystiedot WHERE bonus_profiili_id = " profiili-id))
        (u (str "DELETE FROM bonus_profiili_rivi WHERE bonus_profiili_id = " profiili-id))
        (u (str "DELETE FROM bonus_profiili WHERE id = " profiili-id))))))

(deftest hae-bonus-profiilin-tiedot-admin-kayttaa-masterdatan-nimea-jos-esitystiedot-puuttuvat
  (let [integraatio-id (ffirst (q "SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'"))
        bonus-laji-id (ffirst (q "SELECT id FROM bonus_laji WHERE koodi = 'asiakastyytyvaisyysbonus'"))
        profiili-id (-> (jdbc/insert! (:db jarjestelma) :bonus_profiili
                          {:nimi "bonus-admin-tyhja-nayttonimi-testi"
                           :urakkatyyppi "teiden-hoito"
                           :hoitovuosi_alku 1
                           :hoitovuosi_loppu 20
                           :alkupvm #inst "2025-10-01T00:00:00.000-00:00"
                           :loppupvm nil
                           :aktiivinen true
                           :luoja integraatio-id
                           :muokkaaja integraatio-id})
                      first
                      :id)]
    (try
      (u (str "INSERT INTO bonus_profiili_rivi (bonus_profiili_id, bonus_laji_id, toimenpiderajauksen_tyyppi, toimenpide_t2_koodi, jarjestys, aktiivinen, luoja, luotu, muokkaaja, muokattu) VALUES ("
           profiili-id ", "
           bonus-laji-id ", 't2-koodi', '23150', 1, TRUE, "
           integraatio-id ", CURRENT_TIMESTAMP, "
           integraatio-id ", CURRENT_TIMESTAMP)"))
      (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                      :hae-bonus-profiilin-detalji-admin
                      +kayttaja-jvh+
                      {:bonus-profiili-id profiili-id})
            laji (first (:lajit vastaus))]
        (is (= "Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta" (:nimi laji)) "Puuttuvat esitystiedot eivät saa ohittaa masterdatan nimeä")
        (is (= "Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta" (:masterdatan-nimi laji)) "Masterdatan nimi pitää säilyttää näkyvissä")
        (is (= false (:uudelleennimetty laji)) "Puuttuvat esitystiedot eivät saa näkyä uudelleennimeämisenä")
        (is (nil? (:uudelleennimeaminen laji)) "Uudelleennimeämisen kuvausta ei pidä muodostaa, jos esitystiedot puuttuvat"))
      (finally
        (u (str "DELETE FROM bonus_profiili_rivi WHERE bonus_profiili_id = " profiili-id))
        (u (str "DELETE FROM bonus_profiili WHERE id = " profiili-id))))))

(defn hae-toimenpideinstanssin-id-23150
  "Hakee ensimmäisen toimenpideinstanssin ID:n, joka kuuluu urakkaan ja jonka
   toimenpideen t2-koodi on '23150'."
  [urakka-id]
  (ffirst (q (str "SELECT tpi.id\n"
                  "  FROM toimenpideinstanssi tpi\n"
                  "       JOIN toimenpide t3 ON t3.id = tpi.toimenpide\n"
                  "       JOIN toimenpide t2 ON t2.id = t3.emo\n"
                  " WHERE tpi.urakka = " urakka-id "\n"
                  "   AND t2.koodi = '23150'\n"
                  " ORDER BY tpi.id\n"
                  " LIMIT 1"))))

(deftest hae-urakan-bonus-konfiguraatio-rajapinta-palauttaa-seedatun-mhu-profiilin
  (let [urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
        toimenpideinstanssi-id (hae-toimenpideinstanssin-id-23150 urakka-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakan-bonus-konfiguraatio
                  +kayttaja-jvh+
                  {:urakka-id urakka-id
                   :hoitovuosi 1
                   :toimenpideinstanssi-id toimenpideinstanssi-id})]
    (is (= :teiden-hoito (:urakkatyyppi (:profiili vastaus))))
    (is (= [:asiakastyytyvaisyysbonus :alihankintabonus]
           (mapv :laji (:bonus-lajit vastaus))))
    (is (= ["Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta"
            "Alihankintasopimusten maksuehtobonus"]
           (mapv :nimi (:bonus-lajit vastaus))))))

(deftest hae-urakan-bonus-konfiguraatio-epaonnistuu-jos-profiileja-on-useita
  (let [urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
        toimenpideinstanssi-id (hae-toimenpideinstanssin-id-23150 urakka-id)
        integraatio-id (ffirst (q "SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'"))
        profiili-id (get-in (ls-bonus-konfig/hae-urakan-bonus-konfiguraatio
                              (:db jarjestelma)
                              +kayttaja-jvh+
                              {:urakka-id urakka-id
                               :hoitovuosi 1
                               :toimenpideinstanssi-id toimenpideinstanssi-id})
                      [:profiili :id])
        profiili (first (q-map (format "SELECT urakkatyyppi, hoitovuosi_alku, hoitovuosi_loppu, alkupvm, loppupvm FROM bonus_profiili WHERE id = %s" profiili-id)))]
    (try
      (jdbc/insert! (:db jarjestelma) :bonus_profiili
        {:nimi "iin-mhu-bonus-duplikaatti"
         :urakkatyyppi (:urakkatyyppi profiili)
         :hoitovuosi_alku (:hoitovuosi_alku profiili)
         :hoitovuosi_loppu (:hoitovuosi_loppu profiili)
         :alkupvm (:alkupvm profiili)
         :loppupvm (:loppupvm profiili)
         :aktiivinen true
         :luoja integraatio-id
         :muokkaaja integraatio-id})
      (is (thrown-with-msg? IllegalArgumentException
            #"Useita aktiivisia bonus-profiileja"
            (ls-bonus-konfig/hae-urakan-bonus-konfiguraatio
              (:db jarjestelma)
              +kayttaja-jvh+
              {:urakka-id urakka-id
               :hoitovuosi 1
               :toimenpideinstanssi-id toimenpideinstanssi-id})))
      (finally
        (u "DELETE FROM bonus_profiili WHERE nimi = 'iin-mhu-bonus-duplikaatti'")))))

(deftest hae-urakan-bonus-konfiguraatio-epaonnistuu-jos-kontekstissa-ei-ole-riveja
  (let [urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)]
    (is (thrown-with-msg? IllegalArgumentException
          #"puuttuu rivit toimenpideinstanssiin"
          (ls-bonus-konfig/hae-urakan-bonus-konfiguraatio
            (:db jarjestelma)
            +kayttaja-jvh+
            {:urakka-id urakka-id
             :hoitovuosi 1
             :toimenpideinstanssi-id 999999999})))))

(deftest bonus-profiilin-lajin-esitystiedot-eivat-salli-kahta-rivia-samalle-lajille
  (let [integraatio-id (ffirst (q "SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'"))
        bonus-laji-id (ffirst (q "SELECT id FROM bonus_laji WHERE koodi = 'asiakastyytyvaisyysbonus'"))
        profiili-id (-> (jdbc/insert! (:db jarjestelma) :bonus_profiili
                          {:nimi "bonus-admin-ristiriita-nayttonimi-testi"
                           :urakkatyyppi "teiden-hoito"
                           :hoitovuosi_alku 1
                           :hoitovuosi_loppu 20
                           :alkupvm #inst "2025-10-01T00:00:00.000-00:00"
                           :loppupvm nil
                           :aktiivinen true
                           :luoja integraatio-id
                           :muokkaaja integraatio-id})
                      first
                      :id)]
    (try
      (u (str "INSERT INTO bonus_profiili_rivi (bonus_profiili_id, bonus_laji_id, toimenpiderajauksen_tyyppi, toimenpide_t2_koodi, jarjestys, aktiivinen, luoja, luotu, muokkaaja, muokattu) VALUES ("
           profiili-id ", "
           bonus-laji-id ", 't2-koodi', '23150', 1, TRUE, "
           integraatio-id ", CURRENT_TIMESTAMP, "
           integraatio-id ", CURRENT_TIMESTAMP)"))
      (u (str "INSERT INTO bonus_profiili_laji_esitystiedot (bonus_profiili_id, bonus_laji_id, nimi, luoja, luotu, muokkaaja, muokattu) VALUES ("
           profiili-id ", "
           bonus-laji-id ", 'Ensimmäinen nimi', "
           integraatio-id ", CURRENT_TIMESTAMP, "
           integraatio-id ", CURRENT_TIMESTAMP)"))
      (is (thrown? Exception
            (u (str "INSERT INTO bonus_profiili_laji_esitystiedot (bonus_profiili_id, bonus_laji_id, nimi, luoja, luotu, muokkaaja, muokattu) VALUES ("
                 profiili-id ", "
                 bonus-laji-id ", 'Toinen nimi', "
                 integraatio-id ", CURRENT_TIMESTAMP, "
                 integraatio-id ", CURRENT_TIMESTAMP)")))
        "Tietomallin pitää estää kaksi esitystietoriviä samalle profiili+laji-yhdistelmälle")
      (finally
        (u (str "DELETE FROM bonus_profiili_laji_esitystiedot WHERE bonus_profiili_id = " profiili-id))
        (u (str "DELETE FROM bonus_profiili_rivi WHERE bonus_profiili_id = " profiili-id))
        (u (str "DELETE FROM bonus_profiili WHERE id = " profiili-id))))))

(deftest hae-sanktio-profiilin-tiedot-admin-palauttaa-hierarkkisen-rakenteen
  (let [profiili-id (ffirst (q "SELECT id FROM sanktio_profiili WHERE nimi = 'teiden-hoito-2021-ja-uudemmat'"))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-sanktio-profiilin-detalji-admin
                  +kayttaja-jvh+
                  {:sanktio-profiili-id profiili-id})
        urakka-konteksti (first (filter #(= :urakka (:soveltuvuuskonteksti %)) (:sisalto vastaus)))
        muistutuslaji (first (filter #(= :muistutus (:laji %)) (:lajit urakka-konteksti)))
        ensimmainen-rivi (first (:rivit muistutuslaji))]
    (is (= profiili-id (get-in vastaus [:profiili :id])))
    (is (string? (get-in vastaus [:profiili :yhteenveto])) "Profiilin yhteenvedon pitää tulla backendista valmiina")
    (is (= #{:urakka :laatupoikkeama}
           (into #{} (map :soveltuvuuskonteksti (:sisalto vastaus))))
      "Palautuksen pitää ryhmitellä sisältö vähintään soveltuvuuskontekstin mukaan")
    (is (= :muistutus (:laji muistutuslaji)) "Urakkakontekstin muistutuslajin pitää löytyä ryhmittelystä")
    (is (contains? ensimmainen-rivi :sanktiotyyppi) "Palautuksen alimman tason pitää olla profiilirivi eikä sisäinen join-tulos")
    (is (contains? (:sanktiotyyppi ensimmainen-rivi) :toimenpidekoodi) "Sanktiotyypin tarpeellinen tieto pitää tulla backendin palautuksessa")))

(deftest hae-sanktio-profiilin-tiedot-admin-jarjestaa-profiilirivit-jarjestyksen-mukaan
  (let [integraatio-id (ffirst (q "SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'"))
        laji-id (ffirst (q "SELECT id FROM sanktio_laji WHERE koodi = 'muistutus'"))
        sanktiotyyppi-13-id (ffirst (q "SELECT id FROM sanktiotyyppi WHERE koodi = 13"))
        sanktiotyyppi-14-id (ffirst (q "SELECT id FROM sanktiotyyppi WHERE koodi = 14"))
        profiili-id (-> (jdbc/insert! (:db jarjestelma) :sanktio_profiili
                          {:nimi "admin-jarjestys-testi"
                           :urakkatyyppi "teiden-hoito"
                           :hoitovuosi_alku 1
                           :hoitovuosi_loppu 20
                           :alkupvm #inst "2021-01-01T00:00:00.000-00:00"
                           :loppupvm nil
                           :aktiivinen true
                           :luoja integraatio-id
                           :muokkaaja integraatio-id})
                      first
                      :id)]
    (try
      (jdbc/insert! (:db jarjestelma) :sanktio_profiili_rivi
        {:sanktio_profiili_id profiili-id
         :sanktio_laji_id laji-id
         :sanktiotyyppi_id sanktiotyyppi-13-id
         :soveltuvuuskonteksti "urakka"
         :jarjestys 2
         :aktiivinen true
         :luoja integraatio-id
         :muokkaaja integraatio-id})
      (jdbc/insert! (:db jarjestelma) :sanktio_profiili_rivi
        {:sanktio_profiili_id profiili-id
         :sanktio_laji_id laji-id
         :sanktiotyyppi_id sanktiotyyppi-14-id
         :soveltuvuuskonteksti "urakka"
         :jarjestys 1
         :aktiivinen true
         :luoja integraatio-id
         :muokkaaja integraatio-id})
      (let [vastaus (ls-sanktio-konfiguraatio/hae-sanktio-profiilin-detalji-admin
                      (:db jarjestelma)
                      +kayttaja-jvh+
                      {:sanktio-profiili-id profiili-id})
            rivit (-> vastaus :sisalto first :lajit first :rivit)]
        (is (= [1 2] (mapv :jarjestys rivit))
          "Admin-palautuksen pitää käyttää profiilirivin liiketoimintajärjestystä, ei rivi-id:tä")
        (is (= [14 13] (mapv #(get-in % [:sanktiotyyppi :koodi]) rivit))
          "Sanktiotyypit pitää palauttaa jarjestys-kentän määräämässä järjestyksessä"))
      (finally
        (u (format "DELETE FROM sanktio_profiili_rivi WHERE sanktio_profiili_id = %s" profiili-id))
        (u (format "DELETE FROM sanktio_profiili WHERE id = %s" profiili-id))))))

(deftest hae-sanktio-profiilin-tiedot-admin-ei-palauta-tyhjalle-profiilille-nil-kontekstia
  (let [integraatio-id (ffirst (q "SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'"))
        profiili-id (-> (jdbc/insert! (:db jarjestelma) :sanktio_profiili
                          {:nimi "admin-tyhja-profiili-testi"
                           :urakkatyyppi "teiden-hoito"
                           :hoitovuosi_alku 1
                           :hoitovuosi_loppu 20
                           :alkupvm #inst "2021-01-01T00:00:00.000-00:00"
                           :loppupvm nil
                           :aktiivinen true
                           :luoja integraatio-id
                           :muokkaaja integraatio-id})
                      first
                      :id)]
    (try
      (let [vastaus (ls-sanktio-konfiguraatio/hae-sanktio-profiilin-detalji-admin
                      (:db jarjestelma)
                      +kayttaja-jvh+
                      {:sanktio-profiili-id profiili-id})]
        (is (= [] (:sisalto vastaus))
          "Tyhjän profiilin palautuksen sisällön pitää olla tyhjä lista, ei nil-kontekstia sisältävä rakenne"))
      (finally
        (u (format "DELETE FROM sanktio_profiili WHERE id = %s" profiili-id))))))

(deftest sanktio-profiilien-admin-rajapinnat-vaativat-hallinnan-luvun
  (let [profiili-id (ffirst (q "SELECT id FROM sanktio_profiili WHERE nimi = 'teiden-hoito-2021-ja-uudemmat'"))]
    (is (thrown-with-msg? Exception
          #"EiOikeutta"
          (kutsu-palvelua (:http-palvelin jarjestelma)
            :hae-sanktio-profiilit-admin
            +kayttaja-ulle+
            nil)))
    (is (thrown-with-msg? Exception
          #"EiOikeutta"
          (kutsu-palvelua (:http-palvelin jarjestelma)
            :hae-sanktio-profiilin-detalji-admin
            +kayttaja-ulle+
            {:sanktio-profiili-id profiili-id})))))

(deftest hae-sanktio-profiilin-tiedot-admin-palauttaa-rivin-metadatan
  (let [integraatio-id (ffirst (q "SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'"))
        laji-id (ffirst (q "SELECT id FROM sanktio_laji WHERE koodi = 'A'"))
        profiilirivin-sanktiotyyppi-id (ffirst (q "SELECT id FROM sanktiotyyppi WHERE koodi = 0"))
        profiili-id (-> (jdbc/insert! (:db jarjestelma) :sanktio_profiili
                          {:nimi "admin-rivimetadata-testi"
                           :urakkatyyppi "teiden-hoito"
                           :hoitovuosi_alku 30
                           :hoitovuosi_loppu 30
                           :alkupvm #inst "2021-01-01T00:00:00.000-00:00"
                           :loppupvm nil
                           :aktiivinen true
                           :luoja integraatio-id
                           :muokkaaja integraatio-id})
                      first
                      :id)
        profiilirivi-id (-> (jdbc/insert! (:db jarjestelma) :sanktio_profiili_rivi
                              {:sanktio_profiili_id profiili-id
                               :sanktio_laji_id laji-id
                               :sanktiotyyppi_id profiilirivin-sanktiotyyppi-id
                               :soveltuvuuskonteksti "laatupoikkeama"
                               :jarjestys 1
                               :aktiivinen true
                               :voi_puolittaa_omailmoituksella true
                               :luoja integraatio-id
                               :muokkaaja integraatio-id})
                          first
                          :id)]
    (try
      (jdbc/insert! (:db jarjestelma) :sanktio_profiili_rivi_lukittu_summa
        {:sanktio_profiili_rivi_id profiilirivi-id
         :summa_euroina 6000M
         :jarjestys 1
         :luoja integraatio-id
         :muokkaaja integraatio-id})
      (jdbc/insert! (:db jarjestelma) :sanktio_profiili_rivi_lukittu_summa
        {:sanktio_profiili_rivi_id profiilirivi-id
         :summa_euroina 12000M
         :jarjestys 2
         :luoja integraatio-id
         :muokkaaja integraatio-id})
      (let [vastaus (ls-sanktio-konfiguraatio/hae-sanktio-profiilin-detalji-admin
                      (:db jarjestelma)
                      +kayttaja-jvh+
                      {:sanktio-profiili-id profiili-id})
            rivi (-> vastaus :sisalto first :lajit first :rivit first)]
        (is (= true (:voi-puolittaa-omailmoituksella rivi))
          "Admin-palautuksen pitää näyttää profiilirivin 50 % -sääntö")
        (is (= [6000M 12000M] (:lukitut-summat rivi))
          "Admin-palautuksen pitää näyttää profiiliriviin kytketyt lukitut summat"))
      (finally
        (u (format "DELETE FROM sanktio_profiili_rivi_lukittu_summa WHERE sanktio_profiili_rivi_id = %s" profiilirivi-id))
        (u (format "DELETE FROM sanktio_profiili_rivi WHERE id = %s" profiilirivi-id))
        (u (format "DELETE FROM sanktio_profiili WHERE id = %s" profiili-id))))))

(deftest hae-urakan-sanktio-konfiguraatio-palauttaa-rivin-metadatan
  (let [urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
        integraatio-id (ffirst (q "SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'"))
        laji-id (ffirst (q "SELECT id FROM sanktio_laji WHERE koodi = 'A'"))
        profiilirivin-sanktiotyyppi-id (ffirst (q "SELECT id FROM sanktiotyyppi WHERE koodi = 0"))
        profiili-id (-> (jdbc/insert! (:db jarjestelma) :sanktio_profiili
                          {:nimi "konfiguraatio-rivimetadata-testi"
                           :urakkatyyppi "teiden-hoito"
                           :hoitovuosi_alku 30
                           :hoitovuosi_loppu 30
                           :alkupvm #inst "2021-01-01T00:00:00.000-00:00"
                           :loppupvm nil
                           :aktiivinen true
                           :luoja integraatio-id
                           :muokkaaja integraatio-id})
                      first
                      :id)
        profiilirivi-id (-> (jdbc/insert! (:db jarjestelma) :sanktio_profiili_rivi
                              {:sanktio_profiili_id profiili-id
                               :sanktio_laji_id laji-id
                               :sanktiotyyppi_id profiilirivin-sanktiotyyppi-id
                               :soveltuvuuskonteksti "laatupoikkeama"
                               :jarjestys 1
                               :aktiivinen true
                               :voi_puolittaa_omailmoituksella true
                               :luoja integraatio-id
                               :muokkaaja integraatio-id})
                          first
                          :id)]
    (try
      (jdbc/insert! (:db jarjestelma) :sanktio_profiili_rivi_lukittu_summa
        {:sanktio_profiili_rivi_id profiilirivi-id
         :summa_euroina 6000M
         :jarjestys 1
         :luoja integraatio-id
         :muokkaaja integraatio-id})
      (jdbc/insert! (:db jarjestelma) :sanktio_profiili_rivi_lukittu_summa
        {:sanktio_profiili_rivi_id profiilirivi-id
         :summa_euroina 12000M
         :jarjestys 2
         :luoja integraatio-id
         :muokkaaja integraatio-id})
      (let [vastaus (ls-sanktio-konfiguraatio/hae-urakan-sanktio-konfiguraatio
                      (:db jarjestelma)
                      +kayttaja-jvh+
                      {:urakka-id urakka-id
                       :hoitovuosi 30
                       :soveltuvuuskonteksti :laatupoikkeama})
            sanktiotyyppi (-> vastaus :sanktio-lajit first :sanktiotyypit first)]
        (is (= true (:voi-puolittaa-omailmoituksella sanktiotyyppi))
          "Konfiguraatiohaun pitää palauttaa profiilirivin 50 % -metadata sanktiotyypin yhteydessä")
        (is (= [6000M 12000M] (:lukitut-summat sanktiotyyppi))
          "Konfiguraatiohaun pitää palauttaa profiiliriviin sidotut lukitut summat sanktiotyypin yhteydessä"))
      (finally
        (u (format "DELETE FROM sanktio_profiili_rivi_lukittu_summa WHERE sanktio_profiili_rivi_id = %s" profiilirivi-id))
        (u (format "DELETE FROM sanktio_profiili_rivi WHERE id = %s" profiilirivi-id))
        (u (format "DELETE FROM sanktio_profiili WHERE id = %s" profiili-id))))))

(deftest hae-sanktio-profiilin-tiedot-admin-palauttaa-seedatun-mhu2026-metadatan
  (let [profiili-id (ffirst (q "SELECT id FROM sanktio_profiili WHERE nimi = 'teiden-hoito-mhu2026'"))
        vastaus (when profiili-id
                  (ls-sanktio-konfiguraatio/hae-sanktio-profiilin-detalji-admin
                    (:db jarjestelma)
                    +kayttaja-jvh+
                    {:sanktio-profiili-id profiili-id}))
        kaikki-rivit (mapcat :rivit (mapcat :lajit (:sisalto vastaus)))
        metadataa-sisaltavat-rivit (filter #(or (:voi-puolittaa-omailmoituksella %)
                                              (seq (:lukitut-summat %)))
                                     kaikki-rivit)]
    (is profiili-id "Seedatyn MHU2026-profiilin pitää olla olemassa admin-testausta varten")
    (is (seq metadataa-sisaltavat-rivit)
      "Seedatusta MHU2026-profiilista pitää löytyä ainakin yksi rivi, jossa uusi metadata näkyy admin-näkymässä")))

(deftest hae-sanktio-profiilin-tiedot-admin-palauttaa-seedatut-mhu2026-lajikohtaiset-otsikot
  (let [profiili-id (ffirst (q "SELECT id FROM sanktio_profiili WHERE nimi = 'teiden-hoito-mhu2026'"))
        odotetut-otsikot (into {}
                           (map (juxt :laji_koodi :nimi))
                           (q-map (str "SELECT sl.koodi AS laji_koodi, splet.nimi\n"
                                    "  FROM sanktio_profiili_laji_esitystiedot splet\n"
                                    "       JOIN sanktio_laji sl ON sl.id = splet.sanktio_laji_id\n"
                                    " WHERE splet.sanktio_profiili_id = " profiili-id "\n"
                                    "   AND sl.koodi IN ('A', 'B', 'C')")))
        vastaus (when profiili-id
                  (ls-sanktio-konfiguraatio/hae-sanktio-profiilin-detalji-admin
                    (:db jarjestelma)
                    +kayttaja-jvh+
                    {:sanktio-profiili-id profiili-id}))
        urakka-konteksti (first (filter #(= :urakka (:soveltuvuuskonteksti %)) (:sisalto vastaus)))
        abc-lajit (filterv #(contains? #{:A :B :C} (:laji %)) (:lajit urakka-konteksti))]
    (is profiili-id "Seedatyn MHU2026-profiilin pitää olla olemassa lajikohtaisten otsikoiden testausta varten")
    (is (= #{"A" "B" "C"} (set (keys odotetut-otsikot)))
      "Seedatun MHU2026-profiilin pitää sisältää lajikohtaiset otsikot A/B/C-lajeille")
    (is (= #{:A :B :C} (set (map :laji abc-lajit)))
      "Admin-palautuksen pitää sisältää A/B/C-lajit MHU2026-profiilista")
    (doseq [laji abc-lajit]
      (let [laji-koodi (name (:laji laji))
            odotettu-nimi (get odotetut-otsikot laji-koodi)]
        (is (= odotettu-nimi (:nimi laji))
          (str "MHU2026-profiilin lajin " laji-koodi " pitää käyttää seedattua lajikohtaista otsikkoa"))
        (is (= true (:uudelleennimetty laji))
          (str "MHU2026-profiilin lajin " laji-koodi " pitää näkyä uudelleennimettynä"))
        (is (not= (:masterdatan-nimi laji) (:nimi laji))
          (str "MHU2026-profiilin lajin " laji-koodi " erillinen otsikko ei saa palautua masterdatan nimenä"))
        (is (= (str (:masterdatan-nimi laji) " -> " odotettu-nimi)
               (:uudelleennimeaminen laji))
          (str "MHU2026-profiilin lajin " laji-koodi " pitää palauttaa läpinäkyvä uudelleennimeäminen"))))))

(deftest hae-sanktio-profiilin-tiedot-admin-rajaa-mhu2026-laatupoikkeaman-vain-ryhmalajeihin
  (let [profiili-id (ffirst (q "SELECT id FROM sanktio_profiili WHERE nimi = 'teiden-hoito-mhu2026'"))
        vastaus (when profiili-id
                  (ls-sanktio-konfiguraatio/hae-sanktio-profiilin-detalji-admin
                    (:db jarjestelma)
                    +kayttaja-jvh+
                    {:sanktio-profiili-id profiili-id}))
        laatupoikkeama-konteksti (first (filter #(= :laatupoikkeama (:soveltuvuuskonteksti %)) (:sisalto vastaus)))
        lajit (mapv :laji (:lajit laatupoikkeama-konteksti))]
    (is profiili-id "Seedatyn MHU2026-profiilin pitää olla olemassa laatupoikkeama-testausta varten")
    (is (= [:muistutus :A :B :C] lajit)
      "MHU2026-profiilin laatupoikkeama-kontekstin pitää sisältää vain ryhmälajit nykyisen speksitiedon perusteella")))

(deftest hae-sanktio-profiilin-tiedot-admin-kayttaa-vanhemmissa-profiileissa-masterdatan-nimia
  (let [profiili-id (ffirst (q "SELECT id FROM sanktio_profiili WHERE nimi = 'teiden-hoito-2021-ja-uudemmat'"))
        vastaus (when profiili-id
                  (ls-sanktio-konfiguraatio/hae-sanktio-profiilin-detalji-admin
                    (:db jarjestelma)
                    +kayttaja-jvh+
                    {:sanktio-profiili-id profiili-id}))
        urakka-konteksti (first (filter #(= :urakka (:soveltuvuuskonteksti %)) (:sisalto vastaus)))
        abc-lajit (filterv #(contains? #{:A :B :C} (:laji %)) (:lajit urakka-konteksti))]
    (is profiili-id "Vertailtavan aiemman MHU21-24-profiilin pitää olla olemassa")
    (is (= #{:A :B :C} (set (map :laji abc-lajit)))
      "Vanhemman profiilin pitää palauttaa A/B/C-lajit ilman erillisiä otsikoita")
    (doseq [laji abc-lajit]
      (is (= (:masterdatan-nimi laji) (:nimi laji))
        (str "Vanhemman profiilin lajin " (name (:laji laji)) " pitää käyttää masterdatan nimeä"))
      (is (= false (:uudelleennimetty laji))
        (str "Vanhemman profiilin laji " (name (:laji laji)) " ei saa näkyä turhaan uudelleennimettynä"))
      (is (nil? (:uudelleennimeaminen laji))
        (str "Vanhemman profiilin laji " (name (:laji laji)) " ei saa palauttaa uudelleennimeämisen kuvausta")))))

(deftest hae-sanktio-profiilin-tiedot-admin-kayttaa-masterdatan-nimea-jos-esitystiedot-puuttuvat
  (let [integraatio-id (ffirst (q "SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'"))
        laji-id (ffirst (q "SELECT id FROM sanktio_laji WHERE koodi = 'A'"))
        sanktiotyyppi-id (ffirst (q "SELECT id FROM sanktiotyyppi WHERE koodi = 18"))
        profiili-id (-> (jdbc/insert! (:db jarjestelma) :sanktio_profiili
                          {:nimi "sanktio-admin-masterdata-nayttonimi-testi"
                           :urakkatyyppi "teiden-hoito"
                           :hoitovuosi_alku 1
                           :hoitovuosi_loppu 20
                           :alkupvm #inst "2026-10-01T00:00:00.000-00:00"
                           :loppupvm nil
                           :aktiivinen true
                           :luoja integraatio-id
                           :muokkaaja integraatio-id})
                      first
                      :id)]
    (try
      (jdbc/insert! (:db jarjestelma) :sanktio_profiili_rivi
        {:sanktio_profiili_id profiili-id
         :sanktio_laji_id laji-id
         :sanktiotyyppi_id sanktiotyyppi-id
         :soveltuvuuskonteksti "urakka"
         :jarjestys 1
         :aktiivinen true
         :luoja integraatio-id
         :muokkaaja integraatio-id})
      (let [vastaus (ls-sanktio-konfiguraatio/hae-sanktio-profiilin-detalji-admin
                      (:db jarjestelma)
                      +kayttaja-jvh+
                      {:sanktio-profiili-id profiili-id})
            laji (->> vastaus :sisalto first :lajit first)]
        (is (= "A-ryhmä (tehtäväkohtainen sanktio)" (:nimi laji))
          "Puuttuvat esitystiedot eivät saa ohittaa sanktio-lajin masterdatan nimeä")
        (is (= "A-ryhmä (tehtäväkohtainen sanktio)" (:masterdatan-nimi laji))
          "Jos esitystiedot puuttuvat, masterdatan nimen pitää näkyä erikseenkin oikein")
        (is (= false (:uudelleennimetty laji))
          "Puuttuvat esitystiedot eivät saa näkyä uudelleennimeämisenä")
        (is (nil? (:uudelleennimeaminen laji))
          "Uudelleennimeämisen kuvausta ei pidä muodostaa, jos esitystiedot puuttuvat"))
      (finally
        (u (str "DELETE FROM sanktio_profiili_rivi WHERE sanktio_profiili_id = " profiili-id))
        (u (str "DELETE FROM sanktio_profiili WHERE id = " profiili-id))))))

(deftest sanktio-profiilin-lajin-esitystiedot-eivat-salli-kahta-rivia-samalle-lajille
  (let [integraatio-id (ffirst (q "SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'"))
        laji-id (ffirst (q "SELECT id FROM sanktio_laji WHERE koodi = 'A'"))
        profiili-id (-> (jdbc/insert! (:db jarjestelma) :sanktio_profiili
                          {:nimi "sanktio-admin-ristiriita-nayttonimi-testi"
                           :urakkatyyppi "teiden-hoito"
                           :hoitovuosi_alku 1
                           :hoitovuosi_loppu 20
                           :alkupvm #inst "2026-10-01T00:00:00.000-00:00"
                           :loppupvm nil
                           :aktiivinen true
                           :luoja integraatio-id
                           :muokkaaja integraatio-id})
                      first
                      :id)]
    (try
      (u (str "INSERT INTO sanktio_profiili_laji_esitystiedot (sanktio_profiili_id, sanktio_laji_id, nimi, luoja, luotu, muokkaaja, muokattu) VALUES ("
           profiili-id ", "
           laji-id ", 'Ensimmäinen nimi', "
           integraatio-id ", CURRENT_TIMESTAMP, "
           integraatio-id ", CURRENT_TIMESTAMP)"))
      (is (thrown? Exception
            (u (str "INSERT INTO sanktio_profiili_laji_esitystiedot (sanktio_profiili_id, sanktio_laji_id, nimi, luoja, luotu, muokkaaja, muokattu) VALUES ("
                 profiili-id ", "
                 laji-id ", 'Toinen nimi', "
                 integraatio-id ", CURRENT_TIMESTAMP, "
                 integraatio-id ", CURRENT_TIMESTAMP)")))
        "Tietomallin pitää estää kaksi esitystietoriviä samalle profiili+laji-yhdistelmälle")
      (finally
        (u (str "DELETE FROM sanktio_profiili_laji_esitystiedot WHERE sanktio_profiili_id = " profiili-id))
        (u (str "DELETE FROM sanktio_profiili WHERE id = " profiili-id))))))

(deftest hae-sanktio-profiilin-tiedot-admin-palauttaa-seedatun-mhu2025-profiilin-ja-jarjestyksen
  (letfn [(lajit->yhteenveto [lajit]
            (mapv (fn [{:keys [laji rivit]}]
                    {:laji laji
                     :sanktiotyyppi-koodit (mapv #(get-in % [:sanktiotyyppi :koodi]) rivit)})
              lajit))
          (vastaus->yhteenveto [vastaus]
            (into {}
              (map (fn [{:keys [soveltuvuuskonteksti lajit]}]
                     [soveltuvuuskonteksti (lajit->yhteenveto lajit)]))
              (:sisalto vastaus)))
          (muodosta-odotettu-mhu25-yhteenveto [yhteenveto]
            (update yhteenveto :urakka
              (fn [lajit]
                (let [lajit-ilman-testikeskiarvoa
                      (->> lajit
                           (remove #(= :testikeskiarvo-sanktio (:laji %)))
                           vec)]
                  (conj lajit-ilman-testikeskiarvoa
                    {:laji :laskutus_yli_laskutusrajan
                     :sanktiotyyppi-koodit [0]})))))]
    (let [mhu21-24-profiili-id (ffirst (q "SELECT id FROM sanktio_profiili WHERE nimi = 'teiden-hoito-2021-ja-uudemmat'"))
          mhu25-profiili-id (ffirst (q "SELECT id FROM sanktio_profiili WHERE nimi = 'teiden-hoito-mhu2025'"))
          mhu21-24-vastaus (when mhu21-24-profiili-id
                             (ls-sanktio-konfiguraatio/hae-sanktio-profiilin-detalji-admin
                               (:db jarjestelma)
                               +kayttaja-jvh+
                               {:sanktio-profiili-id mhu21-24-profiili-id}))
          mhu25-vastaus (when mhu25-profiili-id
                          (ls-sanktio-konfiguraatio/hae-sanktio-profiilin-detalji-admin
                            (:db jarjestelma)
                            +kayttaja-jvh+
                            {:sanktio-profiili-id mhu25-profiili-id}))
          mhu21-24-yhteenveto (vastaus->yhteenveto mhu21-24-vastaus)
          mhu25-yhteenveto (vastaus->yhteenveto mhu25-vastaus)
          odotettu-mhu25-yhteenveto (muodosta-odotettu-mhu25-yhteenveto mhu21-24-yhteenveto)
          mhu25-urakka-konteksti (first (filter #(= :urakka (:soveltuvuuskonteksti %)) (:sisalto mhu25-vastaus)))
          mhu25-laatupoikkeama-konteksti (first (filter #(= :laatupoikkeama (:soveltuvuuskonteksti %)) (:sisalto mhu25-vastaus)))
          laskutus-laji (first (filter #(= :laskutus_yli_laskutusrajan (:laji %)) (:lajit mhu25-urakka-konteksti)))]
      (is mhu21-24-profiili-id "Vertailun pohjana käytettävän MHU21-24-profiilin pitää olla olemassa")
      (is mhu25-profiili-id "Seedatyn MHU2025-profiilin pitää olla olemassa admin-testausta varten")
      (is (= odotettu-mhu25-yhteenveto mhu25-yhteenveto)
        "MHU25-profiilin koko admin-palautuksen pitää vastata MHU21-24-profiilia ja sisältää lisäksi laskutusrajalaji vain urakkakontekstissa")
      (is (= [0] (mapv #(get-in % [:sanktiotyyppi :koodi]) (:rivit laskutus-laji)))
        "MHU25-profiilin laskutusrajalajin pitää käyttää koodi-0-sanktiotyyppiä")
      (is (not-any? #(= :testikeskiarvo-sanktio (:laji %)) (:lajit mhu25-urakka-konteksti))
        "MHU25-profiilin urakka-kontekstissa ei pidä olla testikeskiarvo-sanktiota")
      (is (not-any? #(= :laskutus_yli_laskutusrajan (:laji %)) (:lajit mhu25-laatupoikkeama-konteksti))
        "MHU25-profiilin laatupoikkeama-kontekstissa ei pidä olla laskutusrajalajia"))))

(deftest vaadi-talvisuolan-ylitys-ehto
  (let [urakan-tiedot {:loppupvm (pvm/->pvm "30.09.2026")}]
    (is (nil? (ls/vaadi-talvisuolan-ylitys-ehto urakan-tiedot (pvm/->pvm "15.09.2026"))))
    (is (nil? (ls/vaadi-talvisuolan-ylitys-ehto urakan-tiedot (pvm/->pvm "15.10.2025"))))
    (is (thrown? SecurityException (ls/vaadi-talvisuolan-ylitys-ehto urakan-tiedot (pvm/->pvm "15.09.2022"))))
    (is (thrown? SecurityException (ls/vaadi-talvisuolan-ylitys-ehto urakan-tiedot (pvm/->pvm "15.10.2026"))))))


(deftest suorasanktio-talvisuolan-ylitys-toimii
  (let [urakka-id (hae-urakan-id-nimella "Kittilän MHU 2025-2030")
        perustelu "testaus-perustelu"
        perintapvm (pvm/->pvm-aika "2.6.2030 22:00:00")
        sanktio {:suorasanktio true
                 :laji :talvisuolan_ylitys
                 :summa 5000
                 :perintapvm perintapvm}
        laatupoikeama {:tekijanimi "testaus-tekija"
                       :paatos {:paatos "sanktio"
                                :kasittelyaika (pvm/->pvm-aika "2.6.2030 22:00:00")
                                :kasittelytapa :kommentit
                                :perustelu perustelu}
                       :aika (pvm/->pvm-aika "2.6.2030 08:00:00")
                       :urakka urakka-id}
        hk-alkupvm (pvm/->pvm "01.10.2029")
        hk-loppupvm (pvm/->pvm "30.09.2030")]
    (testing "Talvisuolan ylitys -sanktio saadaan tallennettua"
      (let [sanktio-id (palvelukutsu-tallenna-suorasanktio
                         +kayttaja-jvh+ sanktio laatupoikeama hk-alkupvm hk-loppupvm)]
        (is (number? sanktio-id) "Sanktion id:n tulee olla numero")))))


(deftest suorasanktio-talvisuolan-ylitys-ei-toimi
  (let [urakka-id (hae-urakan-id-nimella "Kittilän MHU 2025-2030")
        perustelu "testaus-perustelu"
        perintapvm (pvm/->pvm-aika "2.6.2028 22:00:00")
        sanktio {:suorasanktio true
                 :laji :talvisuolan_ylitys
                 :summa 5000
                 :perintapvm perintapvm}
        laatupoikeama {:tekijanimi "testaus-tekija"
                       :paatos {:paatos "sanktio"
                                :kasittelyaika (pvm/->pvm-aika "1.6.2028 22:00:00")
                                :kasittelytapa :kommentit
                                :perustelu perustelu}
                       :aika (pvm/->pvm-aika "01.6.2029 08:00:00")
                       :urakka urakka-id}
        hk-alkupvm (pvm/->pvm "01.10.2029")
        hk-loppupvm (pvm/->pvm "30.09.2030")]
    (testing "Talvisuolan ylitys antaa virheen vääränä hoitovuotena"
      (is (thrown? SecurityException (palvelukutsu-tallenna-suorasanktio
                                       +kayttaja-jvh+ sanktio laatupoikeama hk-alkupvm hk-loppupvm))
        "Sanktion tallennus ei onnistu"))))
