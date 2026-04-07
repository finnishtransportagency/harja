(ns harja.palvelin.integraatiot.velho.varusteet-valimaiset-test
  (:require [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [clojure.test :refer :all]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.velho.varusteet :as varusteet]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho-integraatio]
            [harja.palvelin.integraatiot.velho.yhteiset :as velho-yhteiset]
            [harja.palvelin.integraatiot.velho.yhteiset-test :as yhteiset-test]
            [harja.testi :refer [i jarjestelma laajenna-integraatiojarjestelmafixturea q-map u]]))

(def kayttaja "jvh")

(def +velho-paallystystoteumat-url+ "http://localhost:1234/paallystystoteumat")
(def +velho-token-url+ "http://localhost:1234/token")
(def +velho-api-juuri+ "http://localhost:1234")

(def +velho-toimenpiteet-oid-url+ (re-pattern (str +velho-api-juuri+ "/toimenpiderekisteri/api/v1/tunnisteet/[^/]+/[^/]+")))
(def +velho-toimenpiteet-kohde-url+ (re-pattern (str +velho-api-juuri+ "/toimenpiderekisteri/api/v1/historia/kohteet")))
(def +velho-varusteet-hakurajapinta-url+ (re-pattern (str +velho-api-juuri+ velho-yhteiset/hakupalvelu-url)))

(def +urakan-velho-oid+ "urakan-velho-oid")
(def +projektin-velho-oid+ "projektin-velho-oid")

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
  (let [alkuperainen-velho-oid (:velho_oid (first (q-map "SELECT velho_oid FROM urakka WHERE id = 123")))]
    (i "INSERT INTO velho_nimikkeisto (versio, tyyppi_avain, kohdeluokka, nimiavaruus, nimi, otsikko) VALUES (1, 'tienvarsikalustetyyppi', 'tienvarsikalusteet', 'varusteet', 'tvkttest', 'Testikalustetyyppi'), (1, 'kuntoluokka', '', 'kohdeluokka', 'kltest', 'Testikuntoluokka'), (1, 'varustetoimenpide', '', 'varustetoimenpide', 'vtptest', 'Testivarustetoimenpide'), (1, 'varustetoimenpide', '', 'varustetoimenpide', 'vtp2test', 'Toinen testivarustetoimenpide'), (1, 'varustetoimenpide', '', 'varustetoimenpide', 'korjaustest', 'Korjaus')")
    (u (str "UPDATE urakka SET velho_oid = '" +urakan-velho-oid+ "' WHERE id = 123"))
    (testit)
    (u "DELETE FROM velho_nimikkeisto WHERE nimi ILIKE '%test'")
    (if alkuperainen-velho-oid
      (u (str "UPDATE urakka SET velho_oid = '" alkuperainen-velho-oid "' WHERE id = 123"))
      (u "UPDATE urakka SET velho_oid = NULL WHERE id = 123"))))

(use-fixtures :each
  jarjestelma-fixture
  testidatan-lisays-fixture)

(defn- pyyntobody->string [body]
  (cond
    (string? body) body
    (nil? body) nil
    :else (slurp body)))

(defn- lue-fixture [tiedostonimi]
  (slurp (str "test/resurssit/velho/varusteet/" tiedostonimi)))

(defn- lue-fixture-datana [tiedostonimi]
  (json/read-str (lue-fixture tiedostonimi) :key-fn keyword))

(defn- tee-hakurajapinta-vastaaja [pyynnot vastaustiedostot]
  (let [vastaukset (mapv lue-fixture vastaustiedostot)]
    (fn [& args]
      (let [{:keys [body]} (some #(when (map? %) %) args)
            payload (json/read-str (pyyntobody->string body) :key-fn keyword)
            indeksi (count @pyynnot)
            vastaus (get vastaukset indeksi)]
        (swap! pyynnot conj payload)
        (or vastaus
            (throw (ex-info "Mock-vastaus puuttuu pyynnolle" {:indeksi indeksi
                                                               :payload payload})))))))

(defn- aja-varustehaku [tiedot vastaustiedostot]
  (let [pyynnot (atom [])
        vastaaja (tee-hakurajapinta-vastaaja pyynnot vastaustiedostot)
        vastaus (with-fake-http [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
                                 +velho-varusteet-hakurajapinta-url+ vastaaja]
                  (with-redefs [q-urakat/hae-urakan-velho-oid (fn [_ _] +urakan-velho-oid+)]
                    (varusteet/hae-urakan-varustetoteumat (:velho-integraatio jarjestelma) tiedot)))]
    {:vastaus vastaus
     :pyynnot @pyynnot}))

(defn- aja-valimaisten-toimenpiteiden-haku-oideilla [oidit vastaustiedostot]
  (let [pyynnot (atom [])
        vastaaja (tee-hakurajapinta-vastaaja pyynnot vastaustiedostot)
        http-asetukset {:metodi :POST
                        :url (str +velho-api-juuri+ velho-yhteiset/hakupalvelu-url)}
        vastaus (with-fake-http [+velho-varusteet-hakurajapinta-url+ vastaaja]
                  (integraatiotapahtuma/suorita-integraatio (:db jarjestelma)
                    (:integraatioloki jarjestelma)
                    "velho"
                      "varustetoteumien-haku"
                    (fn [konteksti]
                      (varusteet/hae-valimaiset-varuste-toimenpiteet-oideille
                        (:db jarjestelma)
                        oidit
                        http-asetukset
                        konteksti
                        nil))))]
    {:vastaus vastaus
     :pyynnot @pyynnot}))

(defn- loytyyko-tarkka-ehto? [data odotus]
  (some #(= odotus %) (tree-seq coll? seq data)))

(defn- loytyyko-paivamaaraehto-alkuun? [payload operaattori]
  (some #(and (vector? %)
              (= operaattori (first %))
              (= ["yleiset/versioitu" "version-voimassaolo" "alku"] (second %)))
        (tree-seq coll? seq payload)))

(defn- loytyyko-paivamaaraehto-polulla? [payload operaattori polku]
  (some #(and (vector? %)
              (= operaattori (first %))
              (= polku (second %)))
        (tree-seq coll? seq payload)))

(deftest tavoitetilan-hakuketju-kutsuu-kaksi-pyyntoa-kun-valimaisia-toimenpiteita-ei-loydy
  (let [{:keys [vastaus pyynnot]} (aja-varustehaku {:urakka-id 123
                                                    :hoitokauden-alkuvuosi 2020}
                                                   ["hakurajapinta-vastaus-tyhja.json"
                                                    "varusteiden-hakurajapinta-vastaus.json"])
        [eka toinen] pyynnot]
    (is (= 2 (count pyynnot)) "Tavoitetilassa haku tekee välimäisten toimenpiteiden haun ja varsinaisen varustehaun, kun erillistä kohdevarustehakua ei tarvita")
    (is (= ["toimenpiteet/valimaiset-varustetoimenpiteet"]
          (:kohdeluokat eka))
      "Ensimmäinen pyyntö kohdistuu välimäisiin varustetoimenpiteisiin")
    (is (= true (get-in toinen [:asetukset :liitoshaku]))
      "Toinen pyyntö on varsinainen varustehaku liitoshaulla")
    (is (= 1 (count (:toteumat vastaus)))
      "Perustapaus säilyy onnistuneena myös uuden hakujärjestyksen jälkeen")))

(deftest tavoitetilan-hakuketju-rajaa-valimaiset-toimenpiteet-muutoksen-lahde-oidilla
  (let [{:keys [pyynnot]} (aja-varustehaku {:urakka-id 123
                                            :hoitokauden-alkuvuosi 2020}
                                           ["hakurajapinta-vastaus-tyhja.json"
                                            "varusteiden-hakurajapinta-vastaus.json"])
        eka (first pyynnot)]
    (is (loytyyko-tarkka-ehto? eka
          ["joukossa" ["yleiset/perustiedot" "muutoksen-lahde-oid"] [+urakan-velho-oid+]])
      "Välimäisten toimenpiteiden sisäänmeno rajataan niiden omaan muutoksen-lahde-oidiin")
    (is (= ["toimenpiteet/valimaiset-varustetoimenpiteet"]
          (:kohdeluokat eka))
      "Ensimmäinen hakureitti lähtee suoraan välimäisistä varustetoimenpiteistä")))

(deftest tavoitetilan-hakuketju-ankkuroi-aikarajauksen-toimenpiteen-ja-varusteen-alkuun
  (let [{:keys [pyynnot]} (aja-varustehaku {:urakka-id 123
                                            :hoitokauden-alkuvuosi 2020
                                            :hoitovuoden-kuukausi 10}
                                           ["hakurajapinta-vastaus-tyhja.json"
                                            "varusteiden-hakurajapinta-vastaus.json"])
        [eka toinen] pyynnot]
    (is (loytyyko-paivamaaraehto-alkuun? eka "pvm-suurempi-kuin")
      "Välimäisten toimenpiteiden haku kohdistaa alkurajauksen version-voimassaolo/alku-kenttään")
    (is (loytyyko-paivamaaraehto-alkuun? eka "pvm-pienempi-kuin")
      "Välimäisten toimenpiteiden haku kohdistaa loppurajauksen version-voimassaolo/alku-kenttään")
    (is (loytyyko-paivamaaraehto-alkuun? toinen "pvm-suurempi-kuin")
      "Myös normaali varustehaku käyttää samaa alkukenttää")
    (is (loytyyko-paivamaaraehto-alkuun? toinen "pvm-pienempi-kuin")
      "Myös normaalin varustehaun yläraja ankkuroidaan samaan kenttään")))

(deftest tavoitetilan-hakuketju-fallbackaa-version-alusta-alkaen-kenttaan
  (let [{:keys [pyynnot]} (aja-varustehaku {:urakka-id 123
                                            :hoitokauden-alkuvuosi 2020
                                            :hoitovuoden-kuukausi 10}
                                           ["hakurajapinta-vastaus-tyhja.json"
                                            "varusteiden-hakurajapinta-vastaus.json"])
        [eka toinen] pyynnot]
    (is (loytyyko-paivamaaraehto-polulla? eka "pvm-suurempi-kuin" ["yleiset/perustiedot" "alkaen"])
      "Välimäisten toimenpiteiden haussa alarajan pitää fallbackata myös alkaen-kenttään")
    (is (loytyyko-paivamaaraehto-polulla? eka "pvm-pienempi-kuin" ["yleiset/perustiedot" "alkaen"])
      "Välimäisten toimenpiteiden haussa ylärajan pitää fallbackata myös alkaen-kenttään")
    (is (loytyyko-paivamaaraehto-polulla? toinen "pvm-suurempi-kuin" ["yleiset/perustiedot" "alkaen"])
      "Normaalissa varustehaussa alarajan pitää fallbackata myös alkaen-kenttään")
    (is (loytyyko-paivamaaraehto-polulla? toinen "pvm-pienempi-kuin" ["yleiset/perustiedot" "alkaen"])
      "Normaalissa varustehaussa ylärajan pitää fallbackata myös alkaen-kenttään")))


(deftest rajatapauksen-raakatoimenpide-kohdistuu-projektille-kirjattuun-varusteeseen
  (let [raakavaruste (lue-fixture-datana "varusteiden-hakurajapinta-vastaus-projektivaruste.json")
        projektivaruste (get-in raakavaruste [:osumat 0])
        projektivaruste-oid (:oid projektivaruste)
        {:keys [vastaus pyynnot]} (aja-valimaisten-toimenpiteiden-haku-oideilla [projektivaruste-oid]
                                  ["valimaisten-varustetoimenpiteiden-vastaus-urakalle.json"])
        toimenpide (first vastaus)]
    (is (= "varusteet/aidat" (:kohdeluokka projektivaruste))
      "Rajatapauksen kohdevaruste on fixtureissä välimäinen kohdeluokka")
    (is (= +projektin-velho-oid+ (:muutoksen-lahde-oid projektivaruste))
      "Rajatapauksen kohdevaruste on fixtureissä kirjattu projektille")
    (is (= +urakan-velho-oid+ (:muutoksen-lahde-oid toimenpide))
      "Samalle OID-ketjulle löytyvä välimäinen toimenpide on fixtureissä kirjattu urakalle")
    (is (= projektivaruste-oid (get-in toimenpide [:ominaisuudet :toimenpiteen-kohde]))
      "Urakalle kirjattu välimäinen toimenpide kohdistuu samaan projektille kirjattuun varusteeseen")
    (is (= 1 (count pyynnot))
      "Rajatapauksen raakatoimenpide haetaan testissä oikeasti mockatun HTTP-kutsun läpi")
    (is (= [projektivaruste-oid]
          (get-in (first pyynnot) [:lauseke 1 2]))
      "Raakatoimenpidehaun payload lukitsee saman projektille kirjatun varusteen OID:n")))

(deftest rajatapauksen-raakadata-ja-palautettu-tulos-vastaavat-toisiaan
  (let [raakavaruste (lue-fixture-datana "varusteiden-hakurajapinta-vastaus-projektivaruste.json")
        raakatoimenpiteet (lue-fixture-datana "valimaisten-varustetoimenpiteiden-vastaus-urakalle.json")
        raakakohde-oid (get-in raakatoimenpiteet [:osumat 0 :ominaisuudet :toimenpiteen-kohde])
        {:keys [vastaus]} (aja-varustehaku {:urakka-id 123
                                            :hoitokauden-alkuvuosi 2020}
                                           ["valimaisten-varustetoimenpiteiden-vastaus-urakalle.json"
                                            "varusteiden-hakurajapinta-vastaus-projektivaruste.json"
                                            "hakurajapinta-vastaus-tyhja.json"])
        palautetut-oidit (map :ulkoinen-oid (:toteumat vastaus))]
    (is (= "varusteet/aidat"
          (get-in raakavaruste [:osumat 0 :kohdeluokka]))
      "Rajatapauksen kohdevaruste pysyy fixtureissä välimäisenä kohdeluokkana")
    (is (= 1 (:osumia raakatoimenpiteet))
      "Raakadatassa on olemassa yksi välimäinen varustetoimenpide")
    (is (some #{raakakohde-oid} palautetut-oidit)
      "Korjattu Harja-palautus sisältää raakadatassa näkyvän välimäisen toimenpiteen kohdevarusteen")))

(deftest tavoitetilan-haku-loytaa-projektivarusteen-urakan-valimaisella-toimenpiteella
  (let [projektivaruste-oid (get-in (lue-fixture-datana "varusteiden-hakurajapinta-vastaus-projektivaruste.json")
                             [:osumat 0 :oid])
        {:keys [vastaus pyynnot]} (aja-varustehaku {:urakka-id 123
                                                    :hoitokauden-alkuvuosi 2020}
                                                   ["valimaisten-varustetoimenpiteiden-vastaus-urakalle.json"
                                                    "varusteiden-hakurajapinta-vastaus-projektivaruste.json"
                                                    "hakurajapinta-vastaus-tyhja.json"])
        [eka toinen kolmas] pyynnot]
    (is (= 3 (count pyynnot))
      "Tavoitetilassa haku tekee välimäisten toimenpiteiden haun, kohdevarusteiden OID-haun ja normaalin varustehaun")
    (is (= ["toimenpiteet/valimaiset-varustetoimenpiteet"]
          (:kohdeluokat eka))
      "Ensimmäinen pyyntö hakee välimäiset varustetoimenpiteet suoraan urakan lähde-OID:lla")
    (is (loytyyko-tarkka-ehto? eka
          ["joukossa" ["yleiset/perustiedot" "muutoksen-lahde-oid"] [+urakan-velho-oid+]])
      "Välimäisten toimenpiteiden haku rajataan toimenpiteen omaan muutoksen-lahde-oidiin")
    (is (loytyyko-paivamaaraehto-alkuun? eka "pvm-suurempi-kuin")
      "Välimäisten toimenpiteiden hakua rajataan pienimmän turvallisen oletuksen mukaisesti toimenpiteen version alkuhetkellä")
    (is (loytyyko-tarkka-ehto? toinen
          ["joukossa" ["yleiset/perustiedot" "oid"] [projektivaruste-oid]])
      "Toinen pyyntö hakee välimäisen toimenpiteen kohdevarusteen suoraan OID:lla")
    (is (not-any? #(= ["yleiset/perustiedot" "muutoksen-lahde-oid"] %)
          (tree-seq coll? seq toinen))
      "OID-pohjainen kohdevarustehaku ei saa rajata pois projektille kirjattua varustetta lähde-OID:lla")
    (is (loytyyko-tarkka-ehto? kolmas
          ["joukossa" ["yleiset/perustiedot" "muutoksen-lahde-oid"] [+urakan-velho-oid+]])
      "Normaali varustehaku pysyy edelleen urakan omalla lähde-OID-rajauksella")
    (is (= 1 (count (:toteumat vastaus)))
      "Lopputulokseen tulee rajatapauksen projektivaruste välimäisen toimenpiteen kautta")
    (is (= projektivaruste-oid
          (:ulkoinen-oid (first (:toteumat vastaus))))
      "Palautettu varuste on sama, johon urakan välimäinen toimenpide kohdistuu")
    (is (= "Testivarustetoimenpide"
          (:toimenpide (first (:toteumat vastaus))))
      "Välimäinen toimenpide yhdistyy kohdevarusteeseen toimenpiteen-kohde OID:n kautta")))

(deftest tavoitetilan-oid-haku-ei-saa-rajata-kohdevarustetta-toimenpiteen-tieosoitteella
  (let [projektivaruste-oid (get-in (lue-fixture-datana "varusteiden-hakurajapinta-vastaus-projektivaruste.json")
                             [:osumat 0 :oid])
        tieosoite-ehto ["kohteen-tieosoite" {:tie 99 :osa 5 :etaisyys 555}]
        {:keys [pyynnot]} (aja-varustehaku {:urakka-id 123
                                            :hoitokauden-alkuvuosi 2020
                                            :tie 99
                                            :aosa 5
                                            :aeta 555}
                                           ["valimaisten-varustetoimenpiteiden-vastaus-urakalle-eri-tieosoitteella.json"
                                            "varusteiden-hakurajapinta-vastaus-projektivaruste.json"
                                            "hakurajapinta-vastaus-tyhja.json"])
        [eka toinen kolmas] pyynnot]
    (is (= 3 (count pyynnot))
      "Hakuketjussa on edelleen kolme vaihetta myös tieosoitteella rajattaessa")
    (is (loytyyko-tarkka-ehto? eka
          tieosoite-ehto)
      "Välimäisten toimenpiteiden ensimmäisen haun pitää rajata toimenpiteet käyttäjän tieosoitteella")
    (is (loytyyko-tarkka-ehto? kolmas
          tieosoite-ehto)
      "Tavallista varustehakua saa edelleen rajata käyttöliittymän tieosoitteella")
    (is (loytyyko-tarkka-ehto? toinen
          ["joukossa" ["yleiset/perustiedot" "oid"] [projektivaruste-oid]])
      "Kohdevarusteen OID-haku kohdistuu edelleen oikeaan kohteeseen")
    (is (not-any? #(= tieosoite-ehto %)
          (tree-seq coll? seq toinen))
      "Kohdevarusteen OID-haku ei saa käyttää toimenpiteen tieosoitesuodatinta, koska kohdevarusteen osoite voi olla eri")))

(deftest tavoitetilan-haku-loytaa-projektivarusteen-eksplisiittisella-toimenpidesuodatuksella
  (let [projektivaruste-oid (get-in (lue-fixture-datana "varusteiden-hakurajapinta-vastaus-projektivaruste.json")
                             [:osumat 0 :oid])
        {:keys [vastaus pyynnot]} (aja-varustehaku {:urakka-id 123
                                                    :hoitokauden-alkuvuosi 2020
                                                    :toimenpide :korjaus}
                                                   ["valimaisten-varustetoimenpiteiden-vastaus-urakalle-korjaus.json"
                                                    "varusteiden-hakurajapinta-vastaus-projektivaruste.json"
                                                    "hakurajapinta-vastaus-tyhja.json"])
        [eka toinen kolmas] pyynnot]
    (is (= 3 (count pyynnot))
      "Eksplisiittinen toimenpidesuodatus käyttää edelleen koko projektivaruste-polun hakuketjua")
    (is (loytyyko-tarkka-ehto? eka
          ["joukossa"
           ["toimenpiteet/valimaiset-varustetoimenpiteet" "ominaisuudet" "toimenpide"]
           ["varustetoimenpide/korjaustest"]])
      "Ensimmäinen pyyntö rajaa välimäiset toimenpiteet eksplisiittisellä toimenpidesuodatuksella")
    (is (= ["ja"
            ["joukossa"
             ["yleiset/perustiedot" "oid"]
             [projektivaruste-oid]]]
          (:lauseke toinen))
      "Kohdevarusteiden OID-haku käyttää samaa yksinkertaista lausekemuotoa kuin tunnetusti toimiva olemassa oleva OID-haku")
    (is (loytyyko-tarkka-ehto? kolmas
          ["joukossa"
           ["yleiset/perustiedot" "oid"]
           [projektivaruste-oid]])
      "Varsinainen varustehaku säilyttää OID-pohjaisen liitoksen eksplisiittisen toimenpidesuodatuksen kanssa")
    (is (= projektivaruste-oid
          (:ulkoinen-oid (first (:toteumat vastaus))))
      "Eksplisiittinen toimenpidesuodatus palauttaa edelleen oikean projektivarusteen")
    (is (= "Korjaus"
          (:toimenpide (first (:toteumat vastaus))))
      "Eksplisiittinen toimenpidesuodatus säilyttää välimäisen toimenpiteen yhdistymisen kohdevarusteeseen")))

(deftest valimainen-toimenpiderivi-palautuu-omana-rivinaan-uudella-tunnisteella
  (let [toimenpide-oid (get-in (lue-fixture-datana "valimaisten-varustetoimenpiteiden-vastaus-urakalle.json")
                        [:osumat 0 :oid])
        kohdevaruste-oid (get-in (lue-fixture-datana "varusteiden-hakurajapinta-vastaus-projektivaruste.json")
                          [:osumat 0 :oid])
        {:keys [vastaus]} (aja-varustehaku {:urakka-id 123
                                            :hoitokauden-alkuvuosi 2020}
                                           ["valimaisten-varustetoimenpiteiden-vastaus-urakalle.json"
                                            "varusteiden-hakurajapinta-vastaus-projektivaruste.json"
                                            "hakurajapinta-vastaus-tyhja.json"])
        rivi (first (:toteumat vastaus))]
    (is (= 1 (count (:toteumat vastaus)))
      "Yksi välimäinen toimenpide tuottaa yhden rivin")
    (is (= :valimainen-toimenpiderivi (:rivityyppi rivi))
      "Välimäinen toimenpide erotellaan omaksi rivityypikseen")
    (is (= toimenpide-oid (:rivi-id rivi))
      "Sekamuotoisen listan tunniste on välimäisellä rivillä toimenpide-oid")
    (is (= toimenpide-oid (:toimenpide-oid rivi))
      "Välimäisellä rivillä säilyy eksplisiittinen toimenpide-oid")
    (is (= kohdevaruste-oid (:ulkoinen-oid rivi))
      "Yhteensopivuussyistä ulkoinen-oid säilyy kohdevarusteen oidina")
    (is (= kohdevaruste-oid (:kohdevarusteen-oid rivi))
      "Historia- ja navigaatiopolulle palautetaan eksplisiittinen kohdevarusteen oid")
    (is (= "aidat" (:kohdevarusteen-kohdeluokka rivi))
      "Historia- ja navigaatiopolulle palautetaan eksplisiittinen kohdevarusteen kohdeluokka")
    (is (= "Testivarustetoimenpide" (:toimenpide rivi))
      "Toimenpiderivin toimenpide muodostuu välimäisen toimenpiteen nimikkeestä")))

(deftest valimainen-toimenpiderivi-kayttaa-toimenpiteen-tieosoitetta
  (let [{:keys [vastaus]} (aja-varustehaku {:urakka-id 123
                                            :hoitokauden-alkuvuosi 2020}
                                           ["valimaisten-varustetoimenpiteiden-vastaus-urakalle-eri-tieosoitteella.json"
                                            "varusteiden-hakurajapinta-vastaus-projektivaruste.json"
                                            "hakurajapinta-vastaus-tyhja.json"])
        rivi (first (:toteumat vastaus))]
    (is (= 1 (count (:toteumat vastaus)))
      "Rajatapauksessa palautuu edelleen yksi välimäinen toimenpiderivi")
    (is (= 99 (:tr-numero rivi))
      "Välimäisen toimenpiderivin tieosoitteen pitää tulla toimenpiteeltä, ei kohdevarusteelta")
    (is (= 5 (:tr-alkuosa rivi))
      "Välimäisen toimenpiderivin alkuosan pitää tulla toimenpiteeltä")
    (is (= 555 (:tr-alkuetaisyys rivi))
      "Välimäisen toimenpiderivin alkuetäisyyden pitää tulla toimenpiteeltä")
    (is (nil? (:tr-loppuosa rivi))
      "Pistemäisellä toimenpiteellä ei pidä syntyä väkisin loppuosaa kohdevarusteelta")
    (is (nil? (:tr-loppuetaisyys rivi))
      "Pistemäisellä toimenpiteellä ei pidä syntyä väkisin loppuetäisyyttä kohdevarusteelta")))

(deftest samalla-kohdevarusteella-voi-olla-useita-valimaisia-toimenpiteita
  (let [toimenpiteet (get (lue-fixture-datana "valimaisten-varustetoimenpiteiden-vastaus-urakalle-kaksi-samalla-kohdevarusteella.json")
                      :osumat)
        kohdevaruste-oid (get-in (lue-fixture-datana "varusteiden-hakurajapinta-vastaus-projektivaruste.json")
                          [:osumat 0 :oid])
        kohdevarusteen-kohdeluokka "aidat"
        odotetut-rivi-idt (set (map :oid toimenpiteet))
        {:keys [vastaus]} (aja-varustehaku {:urakka-id 123
                                            :hoitokauden-alkuvuosi 2020}
                                           ["valimaisten-varustetoimenpiteiden-vastaus-urakalle-kaksi-samalla-kohdevarusteella.json"
                                            "varusteiden-hakurajapinta-vastaus-projektivaruste.json"
                                            "hakurajapinta-vastaus-tyhja.json"])
        rivit (:toteumat vastaus)]
    (is (= 2 (count rivit))
      "Samalle kohdevarusteelle kirjatuista välimäisistä toimenpiteistä pitää muodostua kaksi riviä")
    (is (= #{kohdevaruste-oid} (set (map :ulkoinen-oid rivit)))
      "Kaikkien rivien pitää viitata samaan kohdevarusteeseen")
    (is (= odotetut-rivi-idt (set (map :rivi-id rivit)))
      "Jokaisella välimäisellä toimenpiteellä pitää olla oma rivi-id")
    (is (= odotetut-rivi-idt (set (map :toimenpide-oid rivit)))
      "Jokaisella välimäisellä toimenpiteellä pitää säilyä oma toimenpide-oid")
    (is (= #{kohdevaruste-oid} (set (map :kohdevarusteen-oid rivit)))
      "Kaikkien välimäisten toimenpiderivien pitää viitata samaan kohdevarusteen OID:iin")
    (is (= #{kohdevarusteen-kohdeluokka} (set (map :kohdevarusteen-kohdeluokka rivit)))
      "Kaikkien välimäisten toimenpiderivien pitää viitata samaan kohdevarusteen kohdeluokkaan")
    (is (every? (fn [{:keys [rivi-id toimenpide-oid]}]
                  (= rivi-id toimenpide-oid))
          rivit)
      "Välimäisillä riveillä rivi-id:n pitää vastata toimenpide-oidia")
    (is (= 2 (count (set (map :toimenpide rivit))))
      "Samalle kohdevarusteelle palautuvien välimäisten rivien pitää lukita eri toimenpidearvot eksplisiittisesti")
    (is (= #{"Testivarustetoimenpide" "Toinen testivarustetoimenpide"}
          (set (map :toimenpide rivit)))
      "Monitoimenpidetestin pitää erottaa myös toimenpiteen tekstiarvo, ei vain eri OID:eja")))

(deftest tavallinen-varusterivi-saa-yhteisen-rivitunnisteen-ilman-uutta-mallinnusta
  (let [{:keys [vastaus]} (aja-varustehaku {:urakka-id 123
                                            :hoitokauden-alkuvuosi 2020}
                                           ["hakurajapinta-vastaus-tyhja.json"
                                            "varusteiden-hakurajapinta-vastaus.json"])
        rivi (first (:toteumat vastaus))]
    (is (= 1 (count (:toteumat vastaus)))
      "Perustapauksen tavallinen varusterivi säilyy mukana")
    (is (= :tavallinen-varusterivi (:rivityyppi rivi))
      "Tavallinen varusterivi erotellaan omaksi rivityypikseen")
    (is (= (:ulkoinen-oid rivi) (:rivi-id rivi))
      "Tavallisen varusterivin yhteinen tunniste on edelleen ulkoinen-oid")
    (is (nil? (:toimenpide-oid rivi))
      "Tavalliselle varusteriville ei lisätä turhaa toimenpide-oidia")
    (is (= (:ulkoinen-oid rivi) (:kohdevarusteen-oid rivi))
      "Tavallisella varusterivillä eksplisiittinen kohdevarusteen oid osoittaa samaan kohteeseen")
    (is (= (:kohdeluokka rivi) (:kohdevarusteen-kohdeluokka rivi))
      "Tavallisella varusterivillä eksplisiittinen kohdevarusteen kohdeluokka osoittaa samaan kohteeseen")))
