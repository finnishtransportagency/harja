(ns harja.palvelin.integraatiot.velho.varusteet-test
  (:require [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [clojure.test :refer :all]
            [clojure.walk :as walk]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.kyselyt.velho-nimikkeistot :as q-nimikkeistot]
            [harja.palvelin.integraatiot.velho.varusteet :as varusteet]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho-integraatio]
            [harja.palvelin.integraatiot.velho.yhteiset :as velho-yhteiset]
            [harja.palvelin.integraatiot.velho.yhteiset-test :as yhteiset-test]
            [harja.pvm :as pvm]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.testi :refer [i jarjestelma laajenna-integraatiojarjestelmafixturea q-map u]])
  (:import (net.postgis.jdbc PGgeometry)))

(def kayttaja "jvh")

(def +velho-paallystystoteumat-url+ "http://localhost:1234/paallystystoteumat")
(def +velho-token-url+ "http://localhost:1234/token")

(def +velho-api-juuri+ "http://localhost:1234")

(def +velho-toimenpiteet-oid-url+ (re-pattern (str +velho-api-juuri+ "/toimenpiderekisteri/api/v1/tunnisteet/[^/]+/[^/]+")))
(def +velho-toimenpiteet-kohde-url+ (re-pattern (str +velho-api-juuri+ "/toimenpiderekisteri/api/v1/historia/kohteet")))

(def +velho-nimikkeisto-url+ (re-pattern (str +velho-api-juuri+ "/metatietopalvelu/api/v2/metatiedot/kohdeluokka/[^/]+/[^/]+")))
(def +tienvarsikalusteet-nimikkeisto-url+ (str +velho-api-juuri+ "/metatietopalvelu/api/v2/metatiedot/kohdeluokka/varusteet/tienvarsikalusteet"))

(def +velho-varusteet-hakurajapinta-url+ (re-pattern (str +velho-api-juuri+ velho-yhteiset/hakupalvelu-url)))

(def +urakan-velho-oid+ "urakan-velho-oid")

(def +tienvarsikaluste-oid+ "1.2.345.678.9.0.12.345.678901234")
(def +aita-oid+ "1.2.345.678.9.0.12.345.678909999")

(defn- varusteen-historian-hakupalvelu-vastaus []
  (json/write-str {:osumat (reverse (json/read-str (slurp "test/resurssit/velho/varusteet/varusteen-historia.json")))}))

(defn- valimaisen-varusteen-historian-hakupalvelu-vastaus []
  (slurp "test/resurssit/velho/varusteet/varusteiden-hakurajapinta-vastaus-projektivaruste.json"))

(defn- valimaisen-toimenpiteen-hakupalvelu-vastaus []
  (slurp "test/resurssit/velho/varusteet/valimaisten-varustetoimenpiteiden-vastaus-urakalle-korjaus.json"))

(defn- pyyntobody->string [body]
  (cond
    (string? body) body
    (nil? body) nil
    :else (slurp body)))

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :velho-integraatio (component/using
                         (velho-integraatio/->Velho {:paallystetoteuma-url +velho-paallystystoteumat-url+
                                                     :token-url +velho-token-url+
                                                     :kayttajatunnus "abc-123"
                                                     :salasana "blabla"
                                                     :varuste-api-juuri-url +velho-api-juuri+
                                                     :varuste-toimenpiteet-oid-url +velho-toimenpiteet-oid-url+
                                                     :varuste-toimenpiteet-kohteet-url +velho-toimenpiteet-kohde-url+
                                                     :varuste-client-id "feffefef"
                                                     :varuste-client-secret "puppua"})
                         [:db :integraatioloki])))

(defn testidatan-lisays-fixture [testit]
  (i "INSERT INTO velho_nimikkeisto (versio, tyyppi_avain, kohdeluokka, nimiavaruus, nimi, otsikko) VALUES (1, 'tienvarsikalustetyyppi', 'tienvarsikalusteet', 'varusteet', 'tvkttest', 'Testikalustetyyppi'), (1, 'kuntoluokka', '', 'kohdeluokka', 'kltest', 'Testikuntoluokka'), (1, 'varustetoimenpide', '', 'varustetoimenpide', 'vtptest', 'Testivarustetoimenpide'), (1, 'varustetoimenpide', '', 'varustetoimenpide', 'korjaustest', 'Korjaus')")
  (testit)
  (u "DELETE FROM velho_nimikkeisto WHERE nimi ILIKE '%test'"))

(use-fixtures :each
  jarjestelma-fixture
  testidatan-lisays-fixture)

(deftest tuo-velho-nimikkeisto-test
  (testing "Velhon nimikkeistön tuonti onnistuu"
    (with-fake-http [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
                     +tienvarsikalusteet-nimikkeisto-url+ (slurp "test/resurssit/velho/varusteet/metatietopalvelu/kohdeluokka.json")
                     +velho-nimikkeisto-url+ "{}"]
      (u "DELETE FROM velho_nimikkeisto")
      (varusteet/tuo-velho-nimikkeisto (:velho-integraatio jarjestelma))

      (let [nimikkeisto (q-map "SELECT versio, tyyppi_avain, kohdeluokka, nimiavaruus, nimi, otsikko FROM velho_nimikkeisto")
            odotettu-nimikkeisto [{:versio 1 :tyyppi_avain "tienvarsikalustetyyppi" :kohdeluokka "tienvarsikalusteet" :nimiavaruus "varusteet" :nimi "tvkt1234" :otsikko "Eka kalustetyyppi"}
                                  {:versio 1 :tyyppi_avain "tienvarsikalustetyyppi" :kohdeluokka "tienvarsikalusteet" :nimiavaruus "varusteet" :nimi "tvkt4321" :otsikko "Toka kalustetyyppi"}
                                  {:versio 1 :tyyppi_avain "kuntoluokka" :kohdeluokka "" :nimiavaruus "kuntoluokka" :nimi "kl1234" :otsikko "Eka otsikko"}
                                  {:versio 1 :tyyppi_avain "kuntoluokka" :kohdeluokka "" :nimiavaruus "kuntoluokka" :nimi "kl4321" :otsikko "Toka otsikko"}
                                  {:versio 1 :tyyppi_avain "varustetoimenpide" :kohdeluokka "" :nimiavaruus "varustetoimenpide" :nimi "vtp1234" :otsikko "Eka varustetoimenpide"}
                                  {:versio 1 :tyyppi_avain "varustetoimenpide" :kohdeluokka "" :nimiavaruus "varustetoimenpide" :nimi "vtp4321" :otsikko "Toka varustetoimenpide"}]]
        (is (= nimikkeisto odotettu-nimikkeisto)) "Mock-datasta ei saatu luettua nimikkeistöä oikein!")))

  (testing "Velhon nimikkeistön tuonti epäonnistuu oikein"
    (with-fake-http [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
                     +tienvarsikalusteet-nimikkeisto-url+ :deny
                     +velho-nimikkeisto-url+ "{}"]
      (is (thrown? Error
            (varusteet/tuo-velho-nimikkeisto (:velho-integraatio jarjestelma)))))))

(def odotettu-varuste
  {:alkupvm #inst "2022-10-15T00:00:00.000000000-00:00"
   :kohdeluokka "tienvarsikalusteet"
   :kohdevarusteen-kohdeluokka "tienvarsikalusteet"
   :kohdevarusteen-oid "1.2.345.678.9.0.12.345.678901234"
   :kuntoluokka "Testikuntoluokka"
   :lisatieto ""
   :loppupvm nil
   :muokattu nil
   :muokkaaja "MUOKKAAJA"
   :rivi-id "1.2.345.678.9.0.12.345.678901234"
   :rivityyppi :tavallinen-varusterivi
   :sijainti (PGgeometry. "POINT(6839198.670452601 638694.7440636739)")
   :toimenpide "Lisätty"
   :toimenpide-oid nil
   :tr-alkuetaisyys 101
   :tr-alkuosa 1
   :tr-loppuetaisyys nil
   :tr-loppuosa nil
   :tr-numero 1
   :tyyppi "Testikalustetyyppi"
   :ulkoinen-oid "1.2.345.678.9.0.12.345.678901234"})

(def odotettu-historiavaruste
  (assoc odotettu-varuste :toimenpide "Päivitetty"))

  (deftest muodosta-varusteen-historian-hakupalvelu-payload-test
    (let [payload (#'varusteet/muodosta-varusteen-historian-hakupalvelu-payload varusteet/tienvarsikalusteet +tienvarsikaluste-oid+)]
      (is (= {:tyyppi "kohdeluokkahaku"
      :liitoshaku true
      :oid-haku true}
        (select-keys (:asetukset payload) [:tyyppi :liitoshaku :oid-haku])))
      (is (= ["varusteet/tienvarsikalusteet"] (:kohdeluokat payload)))
      (is (= ["joukossa" ["yleiset/perustiedot" "oid"] [+tienvarsikaluste-oid+]]
        (:lauseke payload)))
      (is (some #{["yleiset/perustiedot" "oid"]}
        (get-in payload [:asetukset :palautettavat-kentat])))
          (is (some #{["yleiset/versioitu" "version-voimassaolo" "alku"]}
        (get-in payload [:asetukset :palautettavat-kentat])))
          (is (some #{["varusteet/tienvarsikalusteet" "sijainti"]}
            (get-in payload [:asetukset :palautettavat-kentat])))
          (is (some #{["varusteet/tienvarsikalusteet" "ominaisuudet" "toiminnalliset-ominaisuudet" "asetusnumero"]}
        (get-in payload [:asetukset :palautettavat-kentat])))
          (is (some #{["varusteet/tienvarsikalusteet" "ominaisuudet" "toiminnalliset-ominaisuudet" "lakinumero"]}
        (get-in payload [:asetukset :palautettavat-kentat])))
          (is (some #{["varusteet/tienvarsikalusteet" "ominaisuudet" "toiminnalliset-ominaisuudet" "lisatietoja"]}
        (get-in payload [:asetukset :palautettavat-kentat])))))

  (deftest jarjesta-varusteen-historiaversiot-test
    (let [historiaversiot [{:oid "c"
            :version-voimassaolo {:alku "2022-10-15"}
            :muokattu "2023-01-01T00:00:00Z"}
           {:oid "a"
            :version-voimassaolo {:alku "2022-10-05"}}
           {:oid "d"
            :version-voimassaolo {:alku "2022-10-15"}
            :muokattu "2023-01-02T00:00:00Z"}
           {:oid "b"
            :version-voimassaolo {:alku "2022-10-15"}
            :muokattu "2023-01-01T00:00:00Z"}]]
          (is (= ["d" "c" "b" "a"]
        (mapv :oid (#'varusteet/jarjesta-varusteen-historiaversiot historiaversiot))))))

  (deftest hae-varusteen-historia-kayttaa-oid-hakua-test
    (let [pyynnot (atom [])
      tallenna-pyynto! (fn [& args]
             (let [{:keys [body]} (some #(when (map? %) %) args)]
               (swap! pyynnot conj (json/read-str (pyyntobody->string body) :key-fn keyword)))
             (varusteen-historian-hakupalvelu-vastaus))]
      (with-fake-http [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
           +velho-varusteet-hakurajapinta-url+ tallenna-pyynto!]
    (let [vastaus (varusteet/hae-varusteen-historia (:velho-integraatio jarjestelma) {:ulkoinen-oid +tienvarsikaluste-oid+
                                  :kohdeluokka "tienvarsikalusteet"})
      pyynto (first @pyynnot)]
      (is (= 1 (count @pyynnot)))
      (is (= true (get-in pyynto [:asetukset :oid-haku])))
      (is (= ["varusteet/tienvarsikalusteet"] (:kohdeluokat pyynto)))
      (is (= ["joukossa" ["yleiset/perustiedot" "oid"] [+tienvarsikaluste-oid+]]
        (:lauseke pyynto)))
      (is (= 2 (count vastaus)))
      (is (= ["Päivitetty" "Lisätty"] (mapv :toimenpide vastaus)))
      (is (apply = (map #(dissoc % :alkupvm :loppupvm :tr-alkuetaisyys :toimenpide) vastaus)))
      (is (= odotettu-historiavaruste (first vastaus)))))))

  (deftest hae-varusteen-historia-palauttaa-tyhjan-listan-kun-hakupalvelu-ei-palauta-osumia-test
    (with-fake-http [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
         +velho-varusteet-hakurajapinta-url+ (json/write-str {:osumat []})]
      (let [vastaus (varusteet/hae-varusteen-historia (:velho-integraatio jarjestelma) {:ulkoinen-oid +tienvarsikaluste-oid+
                                :kohdeluokka "tienvarsikalusteet"})]
        (is (= [] vastaus)))))

(deftest hae-varusteen-historia-sisaltaa-valimaisen-toimenpiteen-valimaiselle-kohteelle-test
  (let [pyynnot (atom [])
        vastaaja (fn [& args]
                   (let [{:keys [body]} (some #(when (map? %) %) args)
                         payload (json/read-str (pyyntobody->string body) :key-fn keyword)
                         indeksi (count @pyynnot)]
                     (swap! pyynnot conj payload)
                     (case indeksi
                       0 (valimaisen-varusteen-historian-hakupalvelu-vastaus)
                       1 (valimaisen-toimenpiteen-hakupalvelu-vastaus)
                       (throw (ex-info "Liikaa mockattuja historian hakupyyntoja" {:indeksi indeksi :payload payload})))))]
    (with-fake-http [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
                     +velho-varusteet-hakurajapinta-url+ vastaaja]
      (let [vastaus (varusteet/hae-varusteen-historia (:velho-integraatio jarjestelma) {:ulkoinen-oid +aita-oid+
                                                                                        :kohdeluokka "aidat"})
            valimainen-rivi (first vastaus)
            tavallinen-rivi (second vastaus)]
        (is (= 2 (count @pyynnot)) "Valimaisen kohteen historiassa haetaan sekä versiot että välimäiset toimenpiteet")
        (is (= 2 (count vastaus)) "Historiaan palautuu myös välimäinen toimenpide omana rivinään")
        (is (= :valimainen-toimenpiderivi (:rivityyppi valimainen-rivi)))
        (is (= :tavallinen-varusterivi (:rivityyppi tavallinen-rivi)))
        (is (= +aita-oid+ (:ulkoinen-oid valimainen-rivi)))
        (is (= +aita-oid+ (:kohdevarusteen-oid valimainen-rivi)))
        (is (= "aidat" (:kohdevarusteen-kohdeluokka valimainen-rivi)))
        (is (= "Korjaus" (:toimenpide valimainen-rivi)))
        (is (= ["toimenpiteet/valimaiset-varustetoimenpiteet"]
              (:kohdeluokat (second @pyynnot)))
          "Toinen pyyntö kohdistuu saman kohdevarusteen välimäisiin toimenpiteisiin")
        (is (= ["ja"
                ["joukossa"
                 ["toimenpiteet/valimaiset-varustetoimenpiteet" "ominaisuudet" "toimenpiteen-kohde"]
                 [+aita-oid+]]]
              (:lauseke (second @pyynnot)))
          "Välimäiset toimenpiteet haetaan kohdevarusteen oidilla ilman ylimääräisiä suodattimia")))))

(deftest hae-urakan-varustetoteumat-test
  (with-fake-http [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
                   +velho-varusteet-hakurajapinta-url+ (slurp "test/resurssit/velho/varusteet/varusteiden-hakurajapinta-vastaus.json")]
    (with-redefs [urakat-q/hae-urakan-velho-oid (constantly +urakan-velho-oid+)]
      (let [vastaus (varusteet/hae-urakan-varustetoteumat (:velho-integraatio jarjestelma) {:urakka-id 123
                                                                                            :hoitokauden-alkuvuosi 2020})]
        (is (= 1 (count (:toteumat vastaus))))
        (is (= odotettu-varuste (first (:toteumat vastaus))))))))

(deftest hae-urakan-varustetoteumat-ilman-hoitovuosirajausta-test
  (let [pyynnot (atom [])
        tallenna-pyynto! (fn [& args]
                           (let [{:keys [body]} (some #(when (map? %) %) args)]
                             (swap! pyynnot conj (json/read-str (pyyntobody->string body) :key-fn keyword)))
                           (slurp "test/resurssit/velho/varusteet/varusteiden-hakurajapinta-vastaus.json"))]
    (with-fake-http [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
                     +velho-varusteet-hakurajapinta-url+ tallenna-pyynto!]
      (with-redefs [urakat-q/hae-urakan-velho-oid (constantly +urakan-velho-oid+)]
        (let [vastaus (varusteet/hae-urakan-varustetoteumat (:velho-integraatio jarjestelma) {:urakka-id 123
                                                                                              :hoitokauden-alkuvuosi nil})]
          (is (= 1 (count (:toteumat vastaus))) "Ei-rajatun haun pitää edelleen palauttaa varusteet")
          (is (= 2 (count @pyynnot)) "Haun pitää tehdä välimäisten toimenpiteiden haku ja varsinainen varustehaku silloin kun erillisiä kohdevarusteita ei löydy")
          (is (every? #(not-any? #{"pvm-suurempi-kuin" "pvm-pienempi-kuin"}
                         (tree-seq coll? seq (walk/stringify-keys %)))
                @pyynnot)
            "Ei-rajatun haun payloadiin ei saa päätyä aikarajausehtoja"))))))

(deftest hae-urakan-varustetoteumat-ohittaa-kuukauden-ilman-hoitovuotta-test
  (let [pyynnot (atom [])
        tallenna-pyynto! (fn [& args]
                           (let [{:keys [body]} (some #(when (map? %) %) args)]
                             (swap! pyynnot conj (json/read-str (pyyntobody->string body) :key-fn keyword)))
                           (slurp "test/resurssit/velho/varusteet/varusteiden-hakurajapinta-vastaus.json"))]
    (with-fake-http [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
                     +velho-varusteet-hakurajapinta-url+ tallenna-pyynto!]
      (with-redefs [urakat-q/hae-urakan-velho-oid (constantly +urakan-velho-oid+)]
        (varusteet/hae-urakan-varustetoteumat (:velho-integraatio jarjestelma) {:urakka-id 123
                                                                                :hoitokauden-alkuvuosi nil
                                                                                :hoitovuoden-kuukausi 10})
        (is (= 2 (count @pyynnot)) "Kuukausen normalisointi ei saa estää välimäisten toimenpiteiden ja varsinaisen varustehaun ketjua")
        (is (every? #(not-any? #{"pvm-suurempi-kuin" "pvm-pienempi-kuin"}
                       (tree-seq coll? seq (walk/stringify-keys %)))
              @pyynnot)
          "Kuukausi ilman hoitovuotta ei saa lisätä payloadiin aikarajaa")))))

;; Testit tyhjien OID-listojen käsittelylle (ei saa tuottaa tyhjiä OID-listoja payloadiin)
(deftest lisaa-oid-haku-jos-tarvitaan-test
  (testing "Lisää OID-haun kun oidit on annettu"
    (let [varsinainen-haku ["ja" ["kohdeluokka" "test"] ["olemassa" "kentta"]]
          oidit ["oid-1" "oid-2" "oid-3"]
          tulos (#'varusteet/lisaa-oid-haku-jos-tarvitaan varsinainen-haku oidit)]
      (is (= ["tai"
              ["ja" ["kohdeluokka" "test"] ["olemassa" "kentta"]]
              ["joukossa" ["yleiset/perustiedot" "oid"] ["oid-1" "oid-2" "oid-3"]]]
             tulos))
      (is (not (some #{[]} (flatten tulos))) "Ei saa sisältää tyhjiä vektoreita")))

  (testing "Ei lisää OID-hakua kun oidit on tyhjä lista"
    (let [varsinainen-haku ["ja" ["kohdeluokka" "test"] ["olemassa" "kentta"]]
          oidit []
          tulos (#'varusteet/lisaa-oid-haku-jos-tarvitaan varsinainen-haku oidit)]
      (is (= varsinainen-haku tulos) "Tyhjällä OID-listalla pitää palauttaa vain varsinainen haku")
      (is (not (some #{[]} (flatten tulos))) "Ei saa sisältää tyhjiä vektoreita")))

  (testing "Ei lisää OID-hakua kun oidit on nil"
    (let [varsinainen-haku ["ja" ["kohdeluokka" "test"] ["olemassa" "kentta"]]
          oidit nil
          tulos (#'varusteet/lisaa-oid-haku-jos-tarvitaan varsinainen-haku oidit)]
      (is (= varsinainen-haku tulos) "nil OID-listalla pitää palauttaa vain varsinainen haku")
      (is (not (some #{[]} (flatten tulos))) "Ei saa sisältää tyhjiä vektoreita"))))

(deftest toimenpide-parametrit-ei-sisalla-tyhja-oid-listoja-test
  (testing "tee-toimenpide-lisatty-parametri ei tuota tyhjiä OID-listoja"
    (let [tulos-tyhja (#'varusteet/tee-toimenpide-lisatty-parametri [])
          tulos-nil (#'varusteet/tee-toimenpide-lisatty-parametri nil)
          tulos-oidit (#'varusteet/tee-toimenpide-lisatty-parametri ["oid-1" "oid-2"])]
      (is (not (some #{[]} (flatten tulos-tyhja))) "Tyhjä lista ei saa tuottaa tyhjiä vektoreita")
      (is (not (some #{[]} (flatten tulos-nil))) "nil ei saa tuottaa tyhjiä vektoreita")
      (is (not (some #{[]} (flatten tulos-oidit))) "OID-lista ei saa tuottaa tyhjiä vektoreita")
      (is (some #(= ["joukossa" ["yleiset/perustiedot" "oid"] ["oid-1" "oid-2"]] %)
            (tree-seq coll? seq tulos-oidit))
        "OID-listan kanssa pitää sisältää OID-haku")))

  (testing "tee-kohteen-poisto-parametri ei tuota tyhjiä OID-listoja"
    (let [tulos-tyhja (#'varusteet/tee-kohteen-poisto-parametri [])
          tulos-nil (#'varusteet/tee-kohteen-poisto-parametri nil)
          tulos-oidit (#'varusteet/tee-kohteen-poisto-parametri ["oid-1"])]
      (is (not (some #{[]} (flatten tulos-tyhja))) "Tyhjä lista ei saa tuottaa tyhjiä vektoreita")
      (is (not (some #{[]} (flatten tulos-nil))) "nil ei saa tuottaa tyhjiä vektoreita")
      (is (not (some #{[]} (flatten tulos-oidit))) "OID-lista ei saa tuottaa tyhjiä vektoreita")))

  (testing "tee-muut-varustetoimenpiteet-parametri ei tuota tyhjiä OID-listoja"
    (with-redefs [q-nimikkeistot/hae-muut-varustetoimenpide-nimikkeet
                  (constantly [{:nimiavaruus "varustetoimenpide" :nimi "vtp01"}])]
      (let [tulos-tyhja (#'varusteet/tee-muut-varustetoimenpiteet-parametri nil [])
            tulos-nil (#'varusteet/tee-muut-varustetoimenpiteet-parametri nil nil)
            tulos-oidit (#'varusteet/tee-muut-varustetoimenpiteet-parametri nil ["oid-1"])]
        (is (not (some #{[]} (flatten tulos-tyhja))) "Tyhjä lista ei saa tuottaa tyhjiä vektoreita")
        (is (not (some #{[]} (flatten tulos-nil))) "nil ei saa tuottaa tyhjiä vektoreita")
        (is (not (some #{[]} (flatten tulos-oidit))) "OID-lista ei saa tuottaa tyhjiä vektoreita"))))

  (testing "tee-varustetoimenpide-parametri ei tuota tyhjiä OID-listoja"
    (with-redefs [q-nimikkeistot/hae-nimike-otsikolla
                  (constantly "vtp01")]
      (let [tulos-tyhja (#'varusteet/tee-varustetoimenpide-parametri nil "Korjaus" [])
            tulos-nil (#'varusteet/tee-varustetoimenpide-parametri nil "Korjaus" nil)
            tulos-oidit (#'varusteet/tee-varustetoimenpide-parametri nil "Korjaus" ["oid-1"])]
        (is (not (some #{[]} (flatten tulos-tyhja))) "Tyhjä lista ei saa tuottaa tyhjiä vektoreita")
        (is (not (some #{[]} (flatten tulos-nil))) "nil ei saa tuottaa tyhjiä vektoreita")
        (is (not (some #{[]} (flatten tulos-oidit))) "OID-lista ei saa tuottaa tyhjiä vektoreita")))))

(deftest varusteen-toimenpide-fallbackaa-raakaan-koodiin-jos-nimikkeisto-puuttuu
  (let [db (:db jarjestelma)]
    (is (= "varustetoimenpide/vtp-tuntematon"
           (#'varusteet/varusteen-toimenpide
            db
            {:ominaisuudet {:toimenpiteet ["varustetoimenpide/vtp-tuntematon"]}}))
      "Jos nimikkeistöstä ei löydy otsikkoa, toimenpiteen pitää fallbackata raakakoodiin merkkijonona")
    (is (= "varustetoimenpide/vtp-a,varustetoimenpide/vtp-b"
           (#'varusteet/yhdista-valimaiset-toimenpiteet-stringiksi
            db
            ["varustetoimenpide/vtp-a" "varustetoimenpide/vtp-b"]))
      "Myös välimäisten toimenpiteiden yhdistetyn tekstin pitää fallbackata raakakoodeihin")))

(deftest historiarivien-oletustoimenpide-kayttaa-vanhinta-implisiittista-versiota
  (let [historiaversiot [{:ominaisuudet {:toimenpiteet []}}
                         {:ominaisuudet {:toimenpiteet ["varustetoimenpide/korjaustest"]}}
                         {:ominaisuudet {:toimenpiteet []}}]
        historiarivit [{:toimenpide "Lisätty"}
                        {:toimenpide "Korjaus"}
                        {:toimenpide "Lisätty"}]
        tulos (#'varusteet/paivita-historiarivien-oletustoimenpiteet historiarivit historiaversiot)]
    (is (= ["Päivitetty" "Korjaus" "Lisätty"] (mapv :toimenpide tulos)))
    (is (= "Lisätty" (:toimenpide (last tulos)))
      "Ensimmäinen implisiittinen historiaversio pitää tulkita lisäykseksi, vaikka vanhempi eksplisiittinen versio olisi olemassa")))

(deftest varusteen-toimenpide-tunnistaa-poiston-vasta-kun-paattymispaiva-on-mennyt
  (with-redefs [pvm/nyt-suomessa (constantly (pvm/iso-8601->pvm "2026-04-02"))]
    (is (= "Poistettu"
          (#'varusteet/varusteen-toimenpide nil
            {:paattyen "2026-04-01"
             :ominaisuudet {:toimenpiteet []}}))
      "Menneisyydessä päättynyt kohde pitää tulkita poistetuksi")
    (is (= "Päivitetty"
          (#'varusteet/varusteen-toimenpide nil
            {:paattyen "2026-04-03"
             :ominaisuudet {:toimenpiteet []}}))
      "Tulevaisuudessa päättyvää kohdetta ei pidä tulkita vielä poistetuksi")))

(deftest historiaversio-tulevalla-paattymispaivalla-ei-ole-viela-eksplisiittinen-poisto
  (with-redefs [pvm/nyt-suomessa (constantly (pvm/iso-8601->pvm "2026-04-02"))]
    (is (false?
          (#'varusteet/historiaversiolla-eksplisiittinen-toimenpide?
            {:paattyen "2026-04-03"
             :ominaisuudet {:toimenpiteet []}}))
      "Tulevaisuudessa päättyvä historiaversio ei saa vielä näyttää eksplisiittiseltä poistotapahtumalta")))
