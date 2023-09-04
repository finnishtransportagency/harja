(ns harja.palvelin.integraatiot.velho.varusteet-test
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clj-time.coerce :refer [to-date-time]]
            [ring.util.codec :refer [form-decode]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.velho.varusteet :as varusteet]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho-integraatio]
            [harja.palvelin.integraatiot.velho.yhteiset :as velho-yhteiset]
            [harja.palvelin.integraatiot.velho.yhteiset-test :as yhteiset-test]
            [harja.palvelin.integraatiot.velho.sanomat.varuste-vastaanottosanoma :refer [velho-aika->aika]]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [org.httpkit.fake :refer [with-fake-http]]
            [clojure.test :refer :all])
  (:import (org.postgis PGgeometry)))

(def kayttaja "jvh")

(def +velho-paallystystoteumat-url+ "http://localhost:1234/paallystystoteumat")
(def +velho-token-url+ "http://localhost:1234/token")

(def +velho-api-juuri+ "http://localhost:1234")

(def +varuste-tunnisteet-regex+
  (re-pattern
    (str +velho-api-juuri+ "/(varusterekisteri|tiekohderekisteri|sijaintipalvelu)/api/v[0-9]/tunnisteet/[^/]+/[^/]+")))

(def +varuste-kohteet-regex+
  (re-pattern
    (str +velho-api-juuri+ "/(varusterekisteri|tiekohderekisteri|sijaintipalvelu)/api/v[0-9]/historia/kohteet")))

(def +velho-urakka-oid-url+ (str +velho-api-juuri+ "/hallintorekisteri/api/v1/tunnisteet/urakka/maanteiden-hoitourakka"))
(def +velho-urakka-kohde-url+ (str +velho-api-juuri+ "hallintorekisteri/api/v1/kohteet"))

(def +velho-toimenpiteet-oid-url+ (re-pattern (str +velho-api-juuri+ "/toimenpiderekisteri/api/v1/tunnisteet/[^/]+/[^/]+")))
(def +velho-toimenpiteet-kohde-url+ (re-pattern (str +velho-api-juuri+ "/toimenpiderekisteri/api/v1/historia/kohteet")))

(def +ylimaarainen-54321-kohde+ "[{\"kohdeluokka\":\"varusteet/kaiteet\",{\"sijainti\":{\"tie\":22,\"osa\":5,\"etaisyys\":4139},
\"oid\":\"5.4.3.2.1\"},{\"kohdeluokka\":\"varusteet/kaiteet\",{\"sijainti\":{\"tie\":22,\"enkoodattu\":1682900006324,\"osa\":5,\"etaisyys\":4139},
\"oid\":\"1.2.3.4.5\"}]")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :velho-integraatio (component/using
                         (velho-integraatio/->Velho {:paallystetoteuma-url +velho-paallystystoteumat-url+
                                                     :token-url +velho-token-url+
                                                     :kayttajatunnus "abc-123"
                                                     :salasana "blabla"
                                                     :varuste-api-juuri-url +velho-api-juuri+
                                                     :varuste-urakka-oid-url +velho-urakka-oid-url+
                                                     :varuste-urakka-kohteet-url +velho-urakka-kohde-url+
                                                     :varuste-toimenpiteet-oid-url +velho-toimenpiteet-oid-url+
                                                     :varuste-toimenpiteet-kohteet-url +velho-toimenpiteet-kohde-url+
                                                     :varuste-client-id "feffefef"
                                                     :varuste-client-secret "puppua"})
                         [:db :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(defn fake-tunnisteet-yleinen [odotettu-oidit-vastaus]
  (fn [_ _ _]
    {:status 200 :body (json/write-str odotettu-oidit-vastaus) :headers {:content-type "application/json"}}))

(defn fake-tunnisteet-yleinen-oidien-lisayksella [odotettu-oidit-vastaus 
                                                  odotettu-ei-tyhja-oid-vastaus 
                                                  saatu-ei-tyhja-oid-vastaus] 
    (swap! odotettu-ei-tyhja-oid-vastaus + 2) ;; Toimenpiteistä palautuu kaksi oidia
    (swap! saatu-ei-tyhja-oid-vastaus inc)
    (fake-tunnisteet-yleinen odotettu-oidit-vastaus))

(defn fake-kohteet-yleinen [odotettu-oidit-vastaus odotettu-kohteet-vastaus]
  (fn [_ {:keys [body headers _]} _]
    (is (= (set odotettu-oidit-vastaus)
           (set (json/read-str body)))
        "Odotettiin kohteiden hakua samalla oid-listalla kuin hae-oid antoi")
    (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
    {:status 200 :body odotettu-kohteet-vastaus :headers {:content-type "application/x-ndjson"}}))

(defn fake-ei-saa-kutsua-fn [syy-teksti]
  (fn [_ {:keys [_ headers url]} _]
    (is false (str "Ei saa kutsua: '" syy-teksti "' otsikot: " headers " url: " url))
    {:status 400 :body ""}))

(def toimenpide-oidit-yleinen ["1.2.246.578.12.2.2153926759.4181769970"])
(def toimenpide-kohteet-yleinen (slurp "test/resurssit/velho/varusteet/toimenpiderekisteri/toimenpiderekisteri_api_v1_historia_kohteet_toimenpiteet_valimaiset-varustetoimenpiteet.jsonl"))


(defn kutsu-ja-palauta-varusteiden-loki
  "Kutsuu `testattava-funktio`:ta ja palauttaa varusteiden `lokita-ja-tallenna-hakuvirhe` funktion saamat viestit rivinvaihdoilla erotettuna."
  [testattava-funktio]
  (let [loki (atom "")
        tallentava-fn (fn [alkuperainen-fn db kohde viesti]
                        (swap! loki #(str % "\n" viesti))
                        (alkuperainen-fn db kohde viesti))
        vanha-funktio velho-yhteiset/lokita-ja-tallenna-hakuvirhe]
    (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+]
                  velho-yhteiset/lokita-ja-tallenna-hakuvirhe (partial tallentava-fn vanha-funktio)]
      (testattava-funktio))
    @loki))

(defn kutsu-ja-laske-jasennykset
  [testattava-funktio]
  (let [jasennys-lkm (atom 0)
        laskuri-fn (fn [alkuperainen-fn db kohde]
                     (swap! jasennys-lkm inc)
                     (alkuperainen-fn db kohde))
        vanha-fn varusteet/jasenna-ja-tarkasta-varustetoteuma
        loki (with-redefs [varusteet/virhe-oidit (fn [db]
                                                   (varusteet/virhe-oidit-set db))
                           varusteet/jasenna-ja-tarkasta-varustetoteuma (partial laskuri-fn vanha-fn)]
               (testattava-funktio))]
    {:loki loki :jasennykset @jasennys-lkm}))

(defn feikkaa-ja-kutsu-varusteintegraatiota
  "Feikkaa http-palvelut ja kutsuu `tuo-uudet-varustetoteumat-velhosta`"
  ([oidit-vastaus kohteet-vastaus]
   (let [fake-tunnisteet (fn [_ {:keys [_ headers _]} _]
                           (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                           {:status 200 :body oidit-vastaus})
         fake-kohteet (fn [_ {:keys [body headers _]} _]
                        (is (= (json/read-str oidit-vastaus) (json/read-str body))
                            "Odotettiin kohteiden hakua samalla oid-listalla kuin hae-oid antoi")
                        (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                        {:status 200 :body kohteet-vastaus})]
     (feikkaa-ja-kutsu-varusteintegraatiota {:fake-token-fn yhteiset-test/fake-token-palvelin
                                             :fake-tunnisteet-fn fake-tunnisteet
                                             :fake-kohteet-fn fake-kohteet})))
  ([{:keys [fake-token-fn fake-kohteet-fn fake-tunnisteet-fn]}]
   (with-fake-http
     [{:url +velho-token-url+ :method :post} fake-token-fn
      {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet-fn
      {:url +varuste-kohteet-regex+ :method :post} fake-kohteet-fn
      {:url +velho-toimenpiteet-oid-url+ :method :get} (fake-tunnisteet-yleinen toimenpide-oidit-yleinen)
      {:url +velho-toimenpiteet-kohde-url+ :method :post} (fake-kohteet-yleinen toimenpide-oidit-yleinen toimenpide-kohteet-yleinen)]
     (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma)))))

(defn kutsu-ja-palauta-urakoiden-loki
  "Kaappaa talteen varusteiden urakka-virhelokitekstit.

   Kutsuu `kutsuttava-fn` funktiota.

   Palauttaa kaikki `varusteet/lokita-urakkahakuvirhe` funktion parametrina saamat
     tekstit rivinvaihdoilla erotettuna.

   Kutsuu alkuperäistä varusteet/lokita-urakkahakuvirhe funktiota sivuvaikutusten vuoksi."
  [kutsuttava-fn]
  (let [loki (atom "")
        tallentava-fn (fn [alkuperainen-fn viesti]
                        (swap! loki #(str % "\n" viesti))
                        (alkuperainen-fn viesti))
        alkuperainen-fn varusteet/lokita-urakkahakuvirhe]
    (with-redefs [varusteet/lokita-urakkahakuvirhe (partial tallentava-fn alkuperainen-fn)]
      (kutsuttava-fn))
    @loki))

(defn feikkaa-ja-kutsu-paivita-urakat
  "Feikkaa http-palvelut ja kutsuu `paivita-mhu-urakka-oidt-velhosta`
  Toisessa kutsumuodossa voi antaa fake-funktiot parametrinä."
  ([odotettu-oidit-vastaus odotettu-kohteet-vastaus]
   (feikkaa-ja-kutsu-paivita-urakat {:fake-oid-fn (fake-tunnisteet-yleinen odotettu-oidit-vastaus)
                                     :fake-kohteet-fn (fake-kohteet-yleinen odotettu-oidit-vastaus odotettu-kohteet-vastaus)}))
  ([{:keys [fake-oid-fn fake-kohteet-fn]}]
   (with-fake-http
     [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
      {:url +velho-urakka-oid-url+ :method :get} fake-oid-fn
      {:url +velho-urakka-kohde-url+ :method :post} fake-kohteet-fn]
     (velho-integraatio/paivita-mhu-urakka-oidt-velhosta (:velho-integraatio jarjestelma)))))

(defn kaikki-varustetoteumat []
  (q-map "SELECT * FROM varustetoteuma_ulkoiset"))

(defn kaikki-varustetoteuma-oidt []
  (->> (q-map "SELECT ulkoinen_oid FROM varustetoteuma_ulkoiset")
       (map :ulkoinen_oid)
       set))

(defn kaikki-virheet []
  (q-map "SELECT * FROM varustetoteuma_ulkoiset_virhe"))

(deftest varuste-token-epaonnistunut-ei-saa-kutsua-palvelua-test
  (yhteiset-test/tyhjenna-velho-tokenit-atomi)
  (let [fake-feilava-token (fn [_ _ _]
                             "{\"error\":\"invalid_client\"}")
        kieletty (fake-ei-saa-kutsua-fn "Ei ole saanut tokenia")]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} fake-feilava-token
       {:url +varuste-tunnisteet-regex+ :method :get} kieletty
       {:url +varuste-kohteet-regex+ :method :post} kieletty
       {:url +velho-toimenpiteet-oid-url+ :method :get} (fake-tunnisteet-yleinen toimenpide-oidit-yleinen)
       {:url +velho-toimenpiteet-kohde-url+ :method :post} (fake-kohteet-yleinen toimenpide-oidit-yleinen toimenpide-kohteet-yleinen)]
      (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+]]
        (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma))))))

(deftest varuste-oid-hakeminen-epaonnistunut-ala-rajahda-test
  (let [fake-feilava-tunnisteet (fn [_ {:keys [_ headers]} _]
                                  (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                                  {:status 500 :body "{\n    \"viesti\": \"Sisäinen palvelukutsu epäonnistui: palvelinvirhe\"\n}"})
        kieletty (fake-ei-saa-kutsua-fn "Ei ole oikeita oid-tunnuksia")]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-feilava-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} kieletty
       {:url +velho-toimenpiteet-oid-url+ :method :get} (fake-tunnisteet-yleinen toimenpide-oidit-yleinen)
       {:url +velho-toimenpiteet-kohde-url+ :method :post} (fake-kohteet-yleinen toimenpide-oidit-yleinen toimenpide-kohteet-yleinen)]
      (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+]]
        (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma))
        (is (= 1 (count (kaikki-virheet))))
        (when (= 1 (count (kaikki-virheet)))
          (is (str/includes? (:virhekuvaus (first (kaikki-virheet))) "järjestelmä palautti statuskoodin: 500")))))))

(deftest varuste-velho-tunnisteet-palauttaa-rikkinaisen-vastauksen-test
  (let [fake-feilava-tunnisteet (fn [_ {:keys [_ headers]} _]
                                  (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                                  {:status 200 :body "[\n    \"1.2.246.578.4.3.1.501.120103774\",\n    \"1.2.246.578.4.3.1.501.120103775\",\n"})
        kieletty (fake-ei-saa-kutsua-fn "Ei ole oikeita oid-tunnuksia")]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-feilava-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} kieletty
       {:url +velho-toimenpiteet-oid-url+ :method :get} (fake-tunnisteet-yleinen toimenpide-oidit-yleinen)
       {:url +velho-toimenpiteet-kohde-url+ :method :post} (fake-kohteet-yleinen toimenpide-oidit-yleinen toimenpide-kohteet-yleinen)]
      (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+]]
        (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma))
        (is (= 1 (count (kaikki-virheet))))
        (when (= 1 (count (kaikki-virheet)))
          (is (str/includes? (:virhekuvaus (first (kaikki-virheet))) "end-of-file inside array")))))))

(deftest varuste-velho-kohteet-palauttaa-500-test
  (let [fake-tunnisteet (fn [_ {:keys [_ headers]} _]
                          (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                          {:status 200 :body "[\n    \"1.2.246.578.4.3.1.501.120103774\",\n    \"1.2.246.578.4.3.1.501.120103775\"]"})
        fake-failaava-kohteet (fn [_ {:keys [_ headers]} _]
                                (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                                {:status 500 :body (slurp "test/resurssit/velho/varusteet/varusterekisteri_api_v1_kohteet_500_fail.jsonl")})]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-failaava-kohteet
       {:url +velho-toimenpiteet-oid-url+ :method :get} (fake-tunnisteet-yleinen toimenpide-oidit-yleinen)
       {:url +velho-toimenpiteet-kohde-url+ :method :post} (fake-kohteet-yleinen toimenpide-oidit-yleinen toimenpide-kohteet-yleinen)]
      (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+]]
        (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma))
        (is (= 2 (count (kaikki-virheet))))
        (when (= 2 (count (kaikki-virheet)))
          (is (str/includes? (:virhekuvaus (first (kaikki-virheet))) "Ulkoinen käsittelyvirhe")))))))

(deftest varuste-velho-kohteet-palauttaa-rikkinaisen-vastauksen-test
  (u "DELETE FROM varustetoteuma_ulkoiset")
  (u "DELETE FROM varustetoteuma_ulkoiset_virhe")
  (let [odotettu-kohderivien-lukumaara 0
        fake-tunnisteet (fn [_ {:keys [_ headers]} _]
                          (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                          {:status 200 :body "[\n    \"1.2.246.578.4.3.1.501.120103774\",\n    \"1.2.246.578.4.3.1.501.120103775\"]"})
        fake-failaava-kohteet (fn [_ {:keys [_ headers]} _]
                                (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                                {:status 200 :body "[{\"kohdeluokka\":\"varusteet/kaiteet\",\"alkusijainti\""})]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-failaava-kohteet
       {:url +velho-toimenpiteet-oid-url+ :method :get} (fake-tunnisteet-yleinen toimenpide-oidit-yleinen)
       {:url +velho-toimenpiteet-kohde-url+ :method :post} (fake-kohteet-yleinen toimenpide-oidit-yleinen toimenpide-kohteet-yleinen)]
      (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+]]
        (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma))
        (is (= odotettu-kohderivien-lukumaara (count (kaikki-varustetoteumat))) "Ei saa lisätä kohderiviä")
        (is (= 1 (count (kaikki-virheet))))
        (when (= 1 (count (kaikki-virheet)))
          (is (str/includes? (:virhekuvaus (first (kaikki-virheet))) "end-of-file inside object")))))))

(deftest varuste-velho-kohteet-palauttaa-vaaraa-tietoa-test
  (let [fake-tunnisteet (fn [_ {:keys [_ headers]} _]
                          (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                          {:status 200 :body "[\"1.2.3.4.5\"]"})
        fake-failaava-kohteet (fn [_ {:keys [_ headers]} _]
                                (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                                {:status 200 :body +ylimaarainen-54321-kohde+})]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-failaava-kohteet
       {:url +velho-toimenpiteet-oid-url+ :method :get} (fake-tunnisteet-yleinen toimenpide-oidit-yleinen)
       {:url +velho-toimenpiteet-kohde-url+ :method :post} (fake-kohteet-yleinen toimenpide-oidit-yleinen toimenpide-kohteet-yleinen)]
      (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+]]
        (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma))))))

(defn testi-tiedosto-oideille [{:keys [palvelu api-versio kohdeluokka]} testitunniste]
  (let [kohdeluokka-tiedostonimessa (str/replace kohdeluokka "/" "_")]
    (str "test/resurssit/velho/varusteet/" testitunniste "/" palvelu "_api_" api-versio "_tunnisteet_" kohdeluokka-tiedostonimessa ".jsonl")))

(defn testi-tiedosto-kohteille [{:keys [palvelu api-versio kohdeluokka]} testitunniste]
  (let [kohdeluokka-tiedostonimessa (str/replace kohdeluokka "/" "_")]
    (str "test/resurssit/velho/varusteet/" testitunniste "/" palvelu "_api_" api-versio "_historia_kohteet_" kohdeluokka-tiedostonimessa ".jsonl")))

(defn olemassa-testi-tiedostot? [lahde testitunniste]
  (let [oid-tiedostonimi (testi-tiedosto-oideille lahde testitunniste)
        kohde-tiedostonimi (testi-tiedosto-kohteille lahde testitunniste)]
    (and (.exists (io/file oid-tiedostonimi)) (.exists (io/file kohde-tiedostonimi)))))

(defn lahde-oid-urlista [url]
  (let [url-osat (str/split url #"[/\\?]")
        palvelu (nth url-osat 3)
        api-versio (nth url-osat 5)
        kohdeluokka (str (nth url-osat 7) "/" (nth url-osat 8))]
    {:palvelu palvelu :api-versio api-versio :kohdeluokka kohdeluokka}))

(deftest varuste-tuonti-kayttaa-osajoukkoja-test
  (u "DELETE FROM varustetoteuma_ulkoiset")
  ; ASETA
  (let [testitunniste "osajoukkoja-test"
        osajoukkojen-koko 2
        odotettu-syotetiedostoparien-maara 3                ;Tämä varmistaa, ettei testisyötteitä jää käyttämättä
        odotettu-kohteet-vastaus (atom {})
        odotettu-oidit-vastaus (atom {})
        odotettu-ei-tyhja-oid-vastaus (atom 0)
        saatu-ei-tyhja-oid-vastaus (atom 0)
        odotettu-tyhja-oid-vastaus (atom 0)
        saatu-tyhja-oid-vastaus (atom 0)
        kohteiden-kutsukerta (atom 0)
        fake-kohteet-kutsujen-maara (atom 0)
        laske-oid-vastaukset (fn [raportoi-oid-haku-fn oidit url]
                               (if (= 0 (count oidit))
                                 (swap! saatu-tyhja-oid-vastaus inc)
                                 (swap! saatu-ei-tyhja-oid-vastaus inc))
                               (raportoi-oid-haku-fn oidit url))
        fake-tunnisteet (fn [_ {:keys [_ headers url]} _]
                          (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                          (let [lahde (lahde-oid-urlista url)]
                            (if (olemassa-testi-tiedostot? lahde testitunniste)
                              (let [oidit-vastaus (slurp (testi-tiedosto-oideille lahde testitunniste))
                                    kohteet-vastaus (slurp (testi-tiedosto-kohteille lahde testitunniste))]
                                (reset! odotettu-oidit-vastaus oidit-vastaus)
                                (reset! odotettu-kohteet-vastaus kohteet-vastaus)
                                (reset! kohteiden-kutsukerta 0)
                                (swap! odotettu-ei-tyhja-oid-vastaus inc)
                                {:status 200 :body @odotettu-oidit-vastaus})
                              (do (reset! odotettu-oidit-vastaus nil)
                                  (reset! odotettu-kohteet-vastaus nil)
                                  (swap! odotettu-tyhja-oid-vastaus inc)
                                  {:status 200 :body "[]"}))))
        fake-kohteet (fn [_ {:keys [body headers _]} _]
                       (let [oidit-pyynnosta (json/read-str body)
                             oidit-lahtojoukko (json/read-str @odotettu-oidit-vastaus)
                             vastauksen-kohteiden-rivit (str/split-lines @odotettu-kohteet-vastaus)
                             vastauksen-oid-joukko (as-> oidit-lahtojoukko x
                                                         (partition osajoukkojen-koko osajoukkojen-koko nil x)
                                                         (nth x @kohteiden-kutsukerta))
                             vastauksen-kohteet (as-> vastauksen-kohteiden-rivit x
                                                      (partition osajoukkojen-koko osajoukkojen-koko nil x)
                                                      (nth x @kohteiden-kutsukerta)
                                                      (str/join "\n" x))]
                         (is (= vastauksen-oid-joukko oidit-pyynnosta)
                             "Odotettiin kohteiden hakua samalla oid-listalla kuin hae-oid antoi")
                         (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                         (swap! kohteiden-kutsukerta inc)
                         (swap! fake-kohteet-kutsujen-maara inc)
                         {:status 200 :body vastauksen-kohteet}))]
    ; SUORITA
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-kohteet
       {:url +velho-toimenpiteet-oid-url+ :method :get} (fake-tunnisteet-yleinen-oidien-lisayksella 
                                                          toimenpide-oidit-yleinen 
                                                          odotettu-ei-tyhja-oid-vastaus 
                                                          saatu-ei-tyhja-oid-vastaus) 
       {:url +velho-toimenpiteet-kohde-url+ :method :post} (fake-kohteet-yleinen toimenpide-oidit-yleinen toimenpide-kohteet-yleinen)]
      (let [raportoi-oid-haku-fn varusteet/lokita-oid-haku]
        (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+]
                      varusteet/lokita-oid-haku (partial laske-oid-vastaukset raportoi-oid-haku-fn)
                      varusteet/+kohde-haku-maksimi-koko+ osajoukkojen-koko]
          (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma)))))
    ; TARKASTA
    (is (= @odotettu-ei-tyhja-oid-vastaus @saatu-ei-tyhja-oid-vastaus) "Odotettiin samaa määrää ei-tyhjiä oid-listoja, kuin fake-velho palautti.")
    (is (= odotettu-syotetiedostoparien-maara @saatu-ei-tyhja-oid-vastaus)
        "Testitiedostoja on eri määrä kuin fake-tunnisteissa on haettu. Kaikki testitiedostot on käytettävä testissä.")

    (is (= @odotettu-tyhja-oid-vastaus @saatu-tyhja-oid-vastaus) "Odotettiin samaa määrää tyhjiä oid-listoja, kuin fake-velho palautti.")

    (let [kaikki-varustetoteumat (kaikki-varustetoteumat)   ; TODO tarkista, että kannassa oid-lista vastaa testissä syötettyjä
          expected-varustetoteuma-maara 3]
      (is (= expected-varustetoteuma-maara (count kaikki-varustetoteumat))
          (str "Odotettiin " expected-varustetoteuma-maara " varustetoteumaa tietokannassa testin jälkeen")))))


(deftest varuste-tuonti-onnistuneet-test
  (u "DELETE FROM varustetoteuma_ulkoiset")
  ; ASETA
  (let [testitunniste "onnistuneet-test"
        odotettu-syotetiedostoparien-maara 4                ;Tämä varmistaa, ettei testisyötteitä jää käyttämättä
        odotettu-kohteet-vastaus (atom {})
        odotettu-oidit-vastaus (atom {})
        odotettu-ei-tyhja-oid-vastaus (atom 0)
        saatu-ei-tyhja-oid-vastaus (atom 0)
        odotettu-tyhja-oid-vastaus (atom 0)
        saatu-tyhja-oid-vastaus (atom 0)
        laske-oid-vastaukset (fn [raportoi-oid-haku-fn oidit url]
                               (if (= 0 (count oidit))
                                 (swap! saatu-tyhja-oid-vastaus inc)
                                 (swap! saatu-ei-tyhja-oid-vastaus inc))
                               (raportoi-oid-haku-fn oidit url))
        fake-tunnisteet (fn [_ {:keys [_ headers url]} _]
                          (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                          (let [lahde (lahde-oid-urlista url)]
                            (if (olemassa-testi-tiedostot? lahde testitunniste)
                              (let [oidit-vastaus (slurp (testi-tiedosto-oideille lahde testitunniste))
                                    kohteet-vastaus (slurp (testi-tiedosto-kohteille lahde testitunniste))]
                                (reset! odotettu-oidit-vastaus oidit-vastaus)
                                (reset! odotettu-kohteet-vastaus kohteet-vastaus)
                                (swap! odotettu-ei-tyhja-oid-vastaus inc)
                                {:status 200 :body @odotettu-oidit-vastaus})
                              (do (reset! odotettu-oidit-vastaus nil)
                                  (reset! odotettu-kohteet-vastaus nil)
                                  (swap! odotettu-tyhja-oid-vastaus inc)
                                  {:status 200 :body "[]"}))))
        fake-kohteet (fn [_ {:keys [body headers _]} _]
                       (is (= (json/read-str @odotettu-oidit-vastaus) (json/read-str body))
                           "Odotettiin kohteiden hakua samalla oid-listalla kuin hae-oid antoi")
                       (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                       {:status 200 :body @odotettu-kohteet-vastaus})]
    ; SUORITA
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-kohteet
       {:url +velho-toimenpiteet-oid-url+ :method :get} (fake-tunnisteet-yleinen-oidien-lisayksella
                                                          toimenpide-oidit-yleinen
                                                          odotettu-ei-tyhja-oid-vastaus
                                                          saatu-ei-tyhja-oid-vastaus)
       {:url +velho-toimenpiteet-kohde-url+ :method :post} (fake-kohteet-yleinen toimenpide-oidit-yleinen toimenpide-kohteet-yleinen)]
      (let [raportoi-oid-haku-fn varusteet/lokita-oid-haku]
        (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+ varusteet/+tl506+]
                      varusteet/lokita-oid-haku (partial laske-oid-vastaukset raportoi-oid-haku-fn)]
          (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma)))))
    ; TARKASTA
    (is (= @odotettu-ei-tyhja-oid-vastaus @saatu-ei-tyhja-oid-vastaus) "Odotettiin samaa määrää ei-tyhjiä oid-listoja, kuin fake-velho palautti.")
    (is (= odotettu-syotetiedostoparien-maara @saatu-ei-tyhja-oid-vastaus)
        "Testitiedostoja on eri määrä kuin fake-tunnisteissa on haettu. Kaikki testitiedostot on käytettävä testissä.")

    (is (= @odotettu-tyhja-oid-vastaus @saatu-tyhja-oid-vastaus) "Odotettiin samaa määrää tyhjiä oid-listoja, kuin fake-velho palautti.")

    (let [kaikki-varustetoteumat (kaikki-varustetoteumat)
          expected-varustetoteuma-maara 5]
      (is (= expected-varustetoteuma-maara (count kaikki-varustetoteumat))
          (str "Odotettiin " expected-varustetoteuma-maara " varustetoteumaa tietokannassa testin jälkeen")))))

(deftest varuste-toteuman-kirjaus-on-idempotentti-test
  (u "DELETE FROM varustetoteuma_ulkoiset")
  ; ASETA
  (let [testitunniste "idempotentti-test"
        odotettu-kohteet-vastaus (atom {})
        odotettu-oidit-vastaus (atom {})
        fake-tunnisteet (fn [_ {:keys [_ headers url]} _]
                          (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                          (let [lahde (lahde-oid-urlista url)]
                            (if (olemassa-testi-tiedostot? lahde testitunniste)
                              (let [oidit-vastaus (slurp (testi-tiedosto-oideille lahde testitunniste))
                                    kohteet-vastaus (slurp (testi-tiedosto-kohteille lahde testitunniste))]
                                (reset! odotettu-oidit-vastaus oidit-vastaus)
                                (reset! odotettu-kohteet-vastaus kohteet-vastaus)
                                {:status 200 :body @odotettu-oidit-vastaus})
                              (do (reset! odotettu-oidit-vastaus nil)
                                  (reset! odotettu-kohteet-vastaus nil)
                                  {:status 200 :body "[]"}))))
        fake-kohteet (fn [_ {:keys [body headers _]} _]
                       (is (= (json/read-str @odotettu-oidit-vastaus) (json/read-str body))
                           "Odotettiin kohteiden hakua samalla oid-listalla kuin hae-oid antoi")
                       (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                       {:status 200 :body @odotettu-kohteet-vastaus})]
    ; SUORITA
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-kohteet
       {:url +velho-toimenpiteet-oid-url+ :method :get} (fake-tunnisteet-yleinen toimenpide-oidit-yleinen)
       {:url +velho-toimenpiteet-kohde-url+ :method :post} (fake-kohteet-yleinen toimenpide-oidit-yleinen toimenpide-kohteet-yleinen)]
      (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+]]
        (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma))))
    ; TARKASTA
    (is (= 1 (count (kaikki-varustetoteumat))))))

(deftest varuste-ei-saa-kutsua-kohde-hakua-jos-oid-lista-on-tyhja-test
  ; ASETA
  (let [testitunniste "oid-lista-on-tyhja-test"
        annettu-kohteet-vastaus (atom {})
        annettu-oidit-vastaus (atom {})
        annettu-ei-tyhja-oid-vastaus (atom 0)
        saatu-ei-tyhja-oid-vastaus (atom 0)
        annettu-tyhja-oid-vastaus (atom 0)
        saatu-tyhja-oid-vastaus (atom 0)
        laske-oid-vastaukset (fn [raportoi-onnistunut oidit url]
                               (if (= 0 (count oidit))
                                 (swap! saatu-tyhja-oid-vastaus inc)
                                 (swap! saatu-ei-tyhja-oid-vastaus inc))
                               (raportoi-onnistunut oidit url))
        fake-tunnisteet (fn [_ {:keys [_ headers url]} _]
                          (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                          (let [lahde (lahde-oid-urlista url)]
                            (if (olemassa-testi-tiedostot? lahde testitunniste)
                              (let [oidit-vastaus (slurp (testi-tiedosto-oideille lahde testitunniste))
                                    kohteet-vastaus (slurp (testi-tiedosto-kohteille lahde testitunniste))]
                                (reset! annettu-oidit-vastaus oidit-vastaus)
                                (reset! annettu-kohteet-vastaus kohteet-vastaus)
                                (swap! annettu-ei-tyhja-oid-vastaus inc)
                                {:status 200 :body @annettu-oidit-vastaus})
                              (do (reset! annettu-oidit-vastaus nil)
                                  (reset! annettu-kohteet-vastaus nil)
                                  (swap! annettu-tyhja-oid-vastaus inc)
                                  {:status 200 :body "[]"}))))
        kieletty (fn [_ {:keys [body headers url]} _]
                   (is false (str "Ei saa kutsua jos ei oikeita oid-tunnuksia url: " url " headers: " headers "  body: " body "")))]
    ; SUORITA
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} kieletty
       {:url +velho-toimenpiteet-oid-url+ :method :get} (fake-tunnisteet-yleinen toimenpide-oidit-yleinen)
       {:url +velho-toimenpiteet-kohde-url+ :method :post} (fake-kohteet-yleinen toimenpide-oidit-yleinen toimenpide-kohteet-yleinen)]
      (let [raportoi-onnistunut-fn varusteet/lokita-oid-haku]
        (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+]
                      varusteet/lokita-oid-haku (partial laske-oid-vastaukset raportoi-onnistunut-fn)]
          (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma)))))
    ; TARKASTA
    (is (= (+ 1 @annettu-tyhja-oid-vastaus) @saatu-tyhja-oid-vastaus))))

(deftest varustetoteuma-paivittyy-uusin-voittaa-test
  (u "DELETE FROM varustetoteuma_ulkoiset")
  ; ASETA
  (let [testitunniste "uusin-voittaa-test"
        odotettu-syotetiedostoparien-maara 3                ;Tämä varmistaa, ettei testisyötteitä jää käyttämättä
        odotettu-kohteet-vastaus (atom {})
        odotettu-oidit-vastaus (atom {})
        odotettu-ei-tyhja-oid-vastaus (atom 0)
        saatu-ei-tyhja-oid-vastaus (atom 0)
        odotettu-tyhja-oid-vastaus (atom 0)
        saatu-tyhja-oid-vastaus (atom 0)
        laske-oid-vastaukset (fn [raportoi-oid-haku-fn oidit url]
                               (if (= 0 (count oidit))
                                 (swap! saatu-tyhja-oid-vastaus inc)
                                 (swap! saatu-ei-tyhja-oid-vastaus inc))
                               (raportoi-oid-haku-fn oidit url))
        fake-tunnisteet (fn [_ {:keys [_ headers url]} _]
                          (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                          (let [lahde (lahde-oid-urlista url)]
                            (if (olemassa-testi-tiedostot? lahde testitunniste)
                              (let [oidit-vastaus (slurp (testi-tiedosto-oideille lahde testitunniste))
                                    kohteet-vastaus (slurp (testi-tiedosto-kohteille lahde testitunniste))]
                                (reset! odotettu-oidit-vastaus oidit-vastaus)
                                (reset! odotettu-kohteet-vastaus kohteet-vastaus)
                                (swap! odotettu-ei-tyhja-oid-vastaus inc)
                                {:status 200 :body @odotettu-oidit-vastaus})
                              (do (reset! odotettu-oidit-vastaus nil)
                                  (reset! odotettu-kohteet-vastaus nil)
                                  (swap! odotettu-tyhja-oid-vastaus inc)
                                  {:status 200 :body "[]"}))))
        fake-kohteet (fn [_ {:keys [body headers _]} _]
                       (is (= (json/read-str @odotettu-oidit-vastaus) (json/read-str body))
                         "Odotettiin kohteiden hakua samalla oid-listalla kuin hae-oid antoi")
                       (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                       {:status 200 :body @odotettu-kohteet-vastaus})]
    ; SUORITA
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-kohteet
       {:url +velho-toimenpiteet-oid-url+ :method :get} (fake-tunnisteet-yleinen-oidien-lisayksella
                                                          toimenpide-oidit-yleinen
                                                          odotettu-ei-tyhja-oid-vastaus
                                                          saatu-ei-tyhja-oid-vastaus)
       {:url +velho-toimenpiteet-kohde-url+ :method :post} (fake-kohteet-yleinen toimenpide-oidit-yleinen toimenpide-kohteet-yleinen)]
      (let [raportoi-oid-haku-fn varusteet/lokita-oid-haku]
        (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+]
                      varusteet/lokita-oid-haku (partial laske-oid-vastaukset raportoi-oid-haku-fn)]
          (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma)))))
    ; TARKASTA
    (is (= @odotettu-ei-tyhja-oid-vastaus @saatu-ei-tyhja-oid-vastaus) "Odotettiin samaa määrää ei-tyhjiä oid-listoja, kuin fake-velho palautti.")
    (is (= odotettu-syotetiedostoparien-maara @saatu-ei-tyhja-oid-vastaus)
        "Testitiedostoja on eri määrä kuin fake-tunnisteissa on haettu. Kaikki testitiedostot on käytettävä testissä.")

    (is (= @odotettu-tyhja-oid-vastaus @saatu-tyhja-oid-vastaus) "Odotettiin samaa määrää tyhjiä oid-listoja, kuin fake-velho palautti.")

    (let [kaikki-varustetoteumat (kaikki-varustetoteumat)   ; TODO tarkista, että kannassa oid-lista vastaa testissä syötettyjä
          expected-varustetoteuma-maara 1]
      (is (= expected-varustetoteuma-maara (count kaikki-varustetoteumat))
          (str "Odotettiin " expected-varustetoteuma-maara " varustetoteumaa tietokannassa testin jälkeen"))
      (when (= expected-varustetoteuma-maara (count kaikki-varustetoteumat))
        (let [kohde (first kaikki-varustetoteumat)]
          (is (= (:muokkaaja kohde) "uusi muokkaaja") "Odotettiin uusimman tiedon korvanneen vanhan."))))))

(deftest varustetoteuma-skipataan-jos-ei-ole-urakkaa
  (u "DELETE FROM varustetoteuma_ulkoiset")
  (let [testitunniste "skipataan-jos-ei-urakkaa"
        odotettu-syotetiedostoparien-maara 3                ;Tämä varmistaa, ettei testisyötteitä jää käyttämättä
        odotettu-kohteet-vastaus (atom {})
        odotettu-oidit-vastaus (atom {})
        odotettu-ei-tyhja-oid-vastaus (atom 0)
        saatu-ei-tyhja-oid-vastaus (atom 0)
        odotettu-tyhja-oid-vastaus (atom 0)
        saatu-tyhja-oid-vastaus (atom 0)
        laske-oid-vastaukset (fn [raportoi-oid-haku-fn oidit url]
                               (if (= 0 (count oidit))
                                 (swap! saatu-tyhja-oid-vastaus inc)
                                 (swap! saatu-ei-tyhja-oid-vastaus inc))
                               (raportoi-oid-haku-fn oidit url))
        fake-tunnisteet (fn [_ {:keys [_ headers url]} _]
                          (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                          (let [lahde (lahde-oid-urlista url)]
                            (if (olemassa-testi-tiedostot? lahde testitunniste)
                              (let [oidit-vastaus (slurp (testi-tiedosto-oideille lahde testitunniste))
                                    kohteet-vastaus (slurp (testi-tiedosto-kohteille lahde testitunniste))]
                                (reset! odotettu-oidit-vastaus oidit-vastaus)
                                (reset! odotettu-kohteet-vastaus kohteet-vastaus)
                                (swap! odotettu-ei-tyhja-oid-vastaus inc)
                                {:status 200 :body @odotettu-oidit-vastaus})
                              (do (reset! odotettu-oidit-vastaus nil)
                                  (reset! odotettu-kohteet-vastaus nil)
                                  (swap! odotettu-tyhja-oid-vastaus inc)
                                  {:status 200 :body "[]"}))))
        fake-kohteet (fn [_ {:keys [body headers _]} _]
                       (is (= (json/read-str @odotettu-oidit-vastaus) (json/read-str body))
                         "Odotettiin kohteiden hakua samalla oid-listalla kuin hae-oid antoi")
                       (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                       {:status 200 :body @odotettu-kohteet-vastaus})]
    ; SUORITA
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-kohteet
       {:url +velho-toimenpiteet-oid-url+ :method :get} (fake-tunnisteet-yleinen-oidien-lisayksella 
                                                          toimenpide-oidit-yleinen 
                                                          odotettu-ei-tyhja-oid-vastaus 
                                                          saatu-ei-tyhja-oid-vastaus) 
       {:url +velho-toimenpiteet-kohde-url+ :method :post} (fake-kohteet-yleinen toimenpide-oidit-yleinen toimenpide-kohteet-yleinen)]
      (let [raportoi-oid-haku-fn varusteet/lokita-oid-haku]
        (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+]
                      varusteet/lokita-oid-haku (partial laske-oid-vastaukset raportoi-oid-haku-fn)]
          (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma)))))
    ; TARKASTA
    (is (= @odotettu-ei-tyhja-oid-vastaus @saatu-ei-tyhja-oid-vastaus) "Odotettiin samaa määrää ei-tyhjiä oid-listoja, kuin fake-velho palautti.")
    (is (= odotettu-syotetiedostoparien-maara @saatu-ei-tyhja-oid-vastaus)
        "Testitiedostoja on eri määrä kuin fake-tunnisteissa on haettu. Kaikki testitiedostot on käytettävä testissä.")

    (is (= @odotettu-tyhja-oid-vastaus @saatu-tyhja-oid-vastaus) "Odotettiin samaa määrää tyhjiä oid-listoja, kuin fake-velho palautti.")

    (let [kaikki-varustetoteumat (kaikki-varustetoteumat)   ; TODO tarkista, että kannassa oid-lista vastaa testissä syötettyjä
          expected-varustetoteuma-maara 1]
      (is (= expected-varustetoteuma-maara (count kaikki-varustetoteumat))
          (str "Odotettiin " expected-varustetoteuma-maara " varustetoteumaa tietokannassa testin jälkeen"))
      (when (= expected-varustetoteuma-maara (count kaikki-varustetoteumat))
        (is (= (:ulkoinen_oid (first kaikki-varustetoteumat)) "1.2.246.578.4.3.1.501.52039770") "Odotettiin ainoastaan kohteen 1.2.246.578.4.3.1.501.52039770 tallentuvan.")))))

(deftest urakka-id-kohteelle-test
  (u "DELETE FROM varustetoteuma_ulkoiset")
  (u "DELETE FROM varustetoteuma_ulkoiset_virhe")
  (let [db (:db jarjestelma)
        ii-oid "1.2.3.4.5.6"
        ii-muutoksen-lahde-oid "1.2.3.4.1234"               ; Urakka Velhossa
        a {:tie 22 :osa 5 :etaisyys 4355}
        varuste-iissa-sijainti {:sijainti a}                ; Sijainti ei saa vaikuttaa, kun Iissa varusteella on muutoksen-lahde-oid
        aktiivinen-ii-urakka-alkupvm "2021-10-01T00:00:00Z"
        odotettu-ii-MHU-urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
        lisaa-muutoksen-lahde (fn [kohde muutoksen-lahde-oid]
                                (assoc kohde :muutoksen-lahde-oid muutoksen-lahde-oid))
        lisaa-pakolliset (fn [kohde oid muokattu] (-> kohde
                                                    (assoc :oid oid :muokattu muokattu)
                                                    (assoc-in [:version-voimassaolo :alku] (first (str/split muokattu #"T")))))]
    (is (= odotettu-ii-MHU-urakka-id
          (varusteet/urakka-id-kohteelle
            db
            (-> varuste-iissa-sijainti
              (lisaa-pakolliset ii-oid aktiivinen-ii-urakka-alkupvm)
              (lisaa-muutoksen-lahde ii-muutoksen-lahde-oid))))
      "kohteen pitää saada urakka muutoksen-lahde-oidn avulla")))

(deftest sijainti-kohteelle-test
  (let [db (:db jarjestelma)
        a {:tie 22 :osa 5 :etaisyys 4355}
        b {:tie 22 :osa 6 :etaisyys 4555}
        tuntematon-sijainti {:sijainti {:tie -1 :osa -1 :etaisyys -1}}
        varuste-oulussa-sijainti {:sijainti a}
        kaide-oulussa-sijainti {:alkusijainti a :loppusijainti b}]
    (is (instance? PGgeometry (varusteet/sijainti-kohteelle db varuste-oulussa-sijainti)))
    (is (nil? (varusteet/sijainti-kohteelle db tuntematon-sijainti)))
    (is (instance? PGgeometry (varusteet/sijainti-kohteelle db kaide-oulussa-sijainti)))))

(deftest varuste-kohdeluokan-viimeinen-hakuaika-test
  "1. Tarkistetaan, että oikea päivämäärä tallentuu kantaan.
   2. Tarkistetaan, että tunnisteita haetaan tallennetulla päivämäärällä (jälkeen parametri get:ssa)."
  ; ASETA
  (let [odotettu-viimeisin-aika (pvm/->pvm-aika "23.11.2021 00:00:00")
        fake-tunnisteet (fn [_ _ _]
                          {:status 200 :body "[]"})
        fake-tunnisteet-2 (fn [_ {:keys [_ _ url]} _]
                            (let [jalkeen (->> url
                                            (re-find #"alkumuokkausaika=(.*)")
                                            second
                                            form-decode
                                            velho-aika->aika)]
                              (is (= (to-date-time odotettu-viimeisin-aika) jalkeen)))
                            {:status 200 :body "[]"})
        ei-sallittu (fake-ei-saa-kutsua-fn "Oid-lista oli tyhjä. Tätä ei saa kutsua.")]
    ; SUORITA
    ;; Nollataan tokenien hakuaika, koska aiemmissa testeissä käytetään nykyaikaa ja testissä menneisyyttä.
    (reset! velho-yhteiset/velho-tokenit nil)
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} ei-sallittu
       {:url +velho-toimenpiteet-oid-url+ :method :get} (fake-tunnisteet-yleinen toimenpide-oidit-yleinen)
       {:url +velho-toimenpiteet-kohde-url+ :method :post} (fake-kohteet-yleinen toimenpide-oidit-yleinen toimenpide-kohteet-yleinen)]
      ; with-redefs korvataan kello, josta viimeisin hakuaika poimitaan
      (with-redefs [pvm/nyt (constantly odotettu-viimeisin-aika)]
        (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma))
        (let [viimeksi-haetut (q-map (str "SELECT kohdeluokka, viimeisin_hakuaika
                                                  FROM varustetoteuma_ulkoiset_viimeisin_hakuaika_kohdeluokalle"))]
          (is (every? #(= odotettu-viimeisin-aika (:viimeisin_hakuaika %)) viimeksi-haetut)
              "Kaikilla kohdeluokilla piti olla odotettu viimeisin hakuaika.")
          (let [odotetut-kohdelajit (set (map :kohdeluokka (conj varusteet/+tietolajien-lahteet+ varusteet/+valimaiset-varustetoimenpiteet+)))
                viimeksi-haettu-kohdelajit (set (map :kohdeluokka viimeksi-haetut))]
            (is (= viimeksi-haettu-kohdelajit odotetut-kohdelajit)
                "Kaikkien kohdeluokkien pitää olla varustetoteuma_ulkoiset_viimeisin_hakuaika_kohdeluokalle taulussa.")))))

    ; Haetaan varusteet uudelleen.
    ; Fake-tunnisteet-2 tarkistaa, että viimeksi haettu on odotettu
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet-2
       {:url +varuste-kohteet-regex+ :method :post} ei-sallittu
       {:url +velho-toimenpiteet-oid-url+ :method :get} (fake-tunnisteet-yleinen toimenpide-oidit-yleinen)
       {:url +velho-toimenpiteet-kohde-url+ :method :post} (fake-kohteet-yleinen toimenpide-oidit-yleinen toimenpide-kohteet-yleinen)]
      (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma)))))

(deftest velho-palauttaa-teknisen-tapahtuman
  (u "DELETE FROM varustetoteuma_ulkoiset")
  (let [odotettu-oidit-vastaus "[\"1.2.246.578.4.3.1.501.158276054\"]"
        odotettu-kohteet-vastaus (slurp "test/resurssit/velho/varusteet/varusterekisteri_api_v1_historia_kohteet-tekninen-tapahtuma.jsonl")
        odotettu-oid-lista #{}
        lokiteksti (kutsu-ja-palauta-varusteiden-loki #(feikkaa-ja-kutsu-varusteintegraatiota odotettu-oidit-vastaus odotettu-kohteet-vastaus))]
    (is (not (str/includes? lokiteksti "ERROR")))
    (is (= odotettu-oid-lista (kaikki-varustetoteuma-oidt)))))

(deftest virheillytta-kohdetta-ei-tuoda-kahdesti
  (u "DELETE FROM varustetoteuma_ulkoiset")
  (let [odotettu-oidit-vastaus "[\"1.2.246.578.4.3.1.501.158276054\"]"
        odotettu-kohteet-vastaus (slurp "test/resurssit/velho/varusteet/virheelliset/varusterekisteri_api_v1_historia_kohteet-virhe.jsonl")
        odotettu-oid-lista #{}
        {lokiteksti :loki, jasennykset :jasennykset} (kutsu-ja-laske-jasennykset
                                                       (fn [] (kutsu-ja-palauta-varusteiden-loki
                                                                (fn [] (str
                                                                         (feikkaa-ja-kutsu-varusteintegraatiota
                                                                           odotettu-oidit-vastaus odotettu-kohteet-vastaus)
                                                                         (feikkaa-ja-kutsu-varusteintegraatiota
                                                                           odotettu-oidit-vastaus odotettu-kohteet-vastaus))))))]
    (is (= 1 jasennykset) "Kohteen tuontia ei saa tehdä, jos on tallessa virhe ko. oidilla.")
    (is (str/includes? lokiteksti "virhe"))
    (is (= odotettu-oid-lista (kaikki-varustetoteuma-oidt)))))

(deftest varuste-varmista-tietokannan-kohdeluokkien-lista-vastaa-koodissa-olevaa-test
  (let [tietokannan-kohdeluokat (->> "SELECT enumlabel
                                      FROM pg_type pt
                                      JOIN pg_enum pe ON pt.oid = pe.enumtypid
                                      WHERE typname = 'kohdeluokka_tyyppi';"
                                     q-map
                                     (map :enumlabel)
                                     set)
        koodin-kohdeluokat (->> (conj varusteet/+tietolajien-lahteet+ varusteet/+valimaiset-varustetoimenpiteet+)
                                (map :kohdeluokka)
                                set)]
    (is (= koodin-kohdeluokat tietokannan-kohdeluokat) "Tietokannassa pitää olla samat kohdeluokat kuin koodissa.")))

(defn urakat-joilla-on-velho-oid []
  (set (q-map "SELECT id, tyyppi, urakkanro, velho_oid FROM urakka WHERE velho_oid IS NOT NULL")))

(deftest hae-mhu-urakka-oidt-test
  "Aurinkoisen päivän testi koko haku ja tallennus polulle."
  (let [odotettu-tulosjoukko #{{:id (hae-kajaanin-alueurakan-2014-2019-id)
                                :tyyppi "hoito"
                                :urakkanro "1236"
                                :velho_oid "1.2.246.578.8.1.147502788"}
                               {:id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
                                :tyyppi "teiden-hoito"
                                :urakkanro "1248"
                                :velho_oid "1.2.246.578.8.1.147502790"}}
        odotettu-oidit-vastaus ["1.2.246.578.8.1.147502788" "1.2.246.578.8.1.147502790"]
        odotettu-kohteet-vastaus (slurp "test/resurssit/velho/varusteet/onnistuneet-test/hallintorekisteri_api_v1_kohteet.jsonl")
        lokiteksti (kutsu-ja-palauta-urakoiden-loki #(feikkaa-ja-kutsu-paivita-urakat odotettu-oidit-vastaus odotettu-kohteet-vastaus))]
    (is (= odotettu-tulosjoukko (urakat-joilla-on-velho-oid)))
    (is (not (str/includes? lokiteksti "ERROR")))))

(deftest velho-palauttaa-kaksi-urakkaa-samalla-velho-oidlla
  "Lokitus huomaa, jos velho_oid tulosjoukko ei ole uniikki. Yksi kohde tallentuu."
  (let [odotettu-oidit-vastaus ["1.2.246.578.8.1.147502788" "1.2.246.578.8.1.147502788"]
        odotettu-kohteet-vastaus (slurp "test/resurssit/velho/varusteet/epaonnistuneet-test/hallintorekisteri_api_v1_kohteet-tupla-velho-oid.jsonl")
        odotettu-tulos-joukko #{{:id 21
                                 :tyyppi "hoito"
                                 :urakkanro "1236"
                                 :velho_oid "1.2.246.578.8.1.147502788"}}
        lokiteksti (kutsu-ja-palauta-urakoiden-loki #(feikkaa-ja-kutsu-paivita-urakat odotettu-oidit-vastaus odotettu-kohteet-vastaus))]
    (is (str/includes? lokiteksti "duplicate key value violates unique constraint"))
    (is (str/includes? lokiteksti "Urakka taulun velho_oid rivien lukumäärä ei vastaa Velhosta saatujen kohteiden määrää."))
    (is (= odotettu-tulos-joukko (urakat-joilla-on-velho-oid)))))

(deftest velho-urakalle-ei-loydy-harjasta-urakanroa
  "Kun urakkaa ei löydy, pitää lokittaa virhe.
  Muut (2) urakat tallentuvat oikein."
  (let [odotettu-oidit-vastaus ["1.2.246.578.8.1.147502788" "1.2.246.578.8.1.147502791" "1.2.246.578.8.1.147502790"]
        odotettu-kohteet-vastaus (slurp "test/resurssit/velho/varusteet/epaonnistuneet-test/hallintorekisteri_api_v1_kohteet-puuttuu-harjasta.jsonl")
        odotettu-urakka-lista #{{:id 21
                                 :tyyppi "hoito"
                                 :urakkanro "1236"
                                 :velho_oid "1.2.246.578.8.1.147502788"}
                                {:id 36
                                 :tyyppi "teiden-hoito"
                                 :urakkanro "1248"
                                 :velho_oid "1.2.246.578.8.1.147502790"}}
        lokiteksti (kutsu-ja-palauta-urakoiden-loki #(feikkaa-ja-kutsu-paivita-urakat odotettu-oidit-vastaus odotettu-kohteet-vastaus))]
    (is (str/includes? lokiteksti "Virhe kohdistettaessa Velho urakkaa '1.2.246.578.8.1.147502791'"))
    (is (str/includes? lokiteksti "Urakka taulun velho_oid rivien lukumäärä ei vastaa Velhosta saatujen kohteiden määrää."))
    (is (= odotettu-urakka-lista (urakat-joilla-on-velho-oid)))))

(deftest urakka-haku-on-idempotentti
  "Urakoiden haun tulee olla idempotentti."
  (let [odotettu-tulosjoukko #{{:id 21
                                :tyyppi "hoito"
                                :urakkanro "1236"
                                :velho_oid "1.2.246.578.8.1.147502788"}
                               {:id 36
                                :tyyppi "teiden-hoito"
                                :urakkanro "1248"
                                :velho_oid "1.2.246.578.8.1.147502790"}}
        odotettu-oidit-vastaus ["1.2.246.578.8.1.147502788" "1.2.246.578.8.1.147502790"]
        odotettu-kohteet-vastaus (slurp "test/resurssit/velho/varusteet/onnistuneet-test/hallintorekisteri_api_v1_kohteet.jsonl")
        lokiteksti1 (kutsu-ja-palauta-urakoiden-loki #(feikkaa-ja-kutsu-paivita-urakat odotettu-oidit-vastaus odotettu-kohteet-vastaus))
        _ (is (= odotettu-tulosjoukko (urakat-joilla-on-velho-oid)))
        lokiteksti2 (kutsu-ja-palauta-urakoiden-loki #(feikkaa-ja-kutsu-paivita-urakat odotettu-oidit-vastaus odotettu-kohteet-vastaus))]
    (is (= odotettu-tulosjoukko (urakat-joilla-on-velho-oid)))
    (is (not (str/includes? (str lokiteksti1 lokiteksti2) "ERROR")))))

(deftest velho-palauttaa-500-urakka-oideja-haettaessa
  (let [fake-oid-fn (fn [_ _ _]
                      {:status 500 :body "spec spec spec..." :headers {:content-type "text/html"}})
        kieletty-fn (fake-ei-saa-kutsua-fn "Ei ole saatu oikeita oideja")
        lokiteksti (kutsu-ja-palauta-urakoiden-loki #(feikkaa-ja-kutsu-paivita-urakat {:fake-oid-fn fake-oid-fn :fake-kohteet-fn kieletty-fn}))]
    (is (str/includes? lokiteksti "Ulkoinen järjestelmä palautti statuskoodin: 500 ja virheen: spec spec spec..."))))

(deftest velho-palauttaa-500-urakka-kohteita-haettaessa
  (let [odotettu-oidit-vastaus ["1.2.246.578.8.1.147502788" "1.2.246.578.8.1.147502790"]
        fake-oid-fn (fake-tunnisteet-yleinen odotettu-oidit-vastaus)
        fake-kohteet-fn (fn [_ _ _]
                          {:status 500 :body "spec spec spec..." :headers {:content-type "text/html"}})
        lokiteksti (kutsu-ja-palauta-urakoiden-loki #(feikkaa-ja-kutsu-paivita-urakat {:fake-oid-fn fake-oid-fn :fake-kohteet-fn fake-kohteet-fn}))]
    (is (str/includes? lokiteksti "Ulkoinen järjestelmä palautti statuskoodin: 500 ja virheen: spec spec spec..."))))

(deftest velhon-urakka-json-ei-jasenny-oikein
  (let [odotettu-oidit-vastaus ["1.2.246.578.8.1.147502788" "1.2.246.578.8.1.147502790"]
        odotettu-kohteet-vastaus (slurp "test/resurssit/velho/varusteet/epaonnistuneet-test/hallintorekisteri_api_v1_kohteet-virhe-json.jsonl")
        odotettu-urakka-lista #{{:id 21
                                 :tyyppi "hoito"
                                 :urakkanro "1236"
                                 :velho_oid "1.2.246.578.8.1.147502788"}
                                {:id 36
                                 :tyyppi "teiden-hoito"
                                 :urakkanro "1248"
                                 :velho_oid "1.2.246.578.8.1.147502790"}}
        lokiteksti (kutsu-ja-palauta-urakoiden-loki #(feikkaa-ja-kutsu-paivita-urakat odotettu-oidit-vastaus odotettu-kohteet-vastaus))]
    (is (str/includes? lokiteksti "JSON jäsennys epäonnistui."))
    (is (str/includes? lokiteksti "Urakka taulun velho_oid rivien lukumäärä ei vastaa Velhosta saatujen kohteiden määrää."))
    (is (not (str/includes? lokiteksti "SQL UPDATE palautti 0 muuttuneiden rivien lukumääräksi.")))
    (is (= odotettu-urakka-lista (urakat-joilla-on-velho-oid)))))

(deftest velho-urakka-oid-json-on-rikki
  (let [fake-oid-fn (fn [_ _ _]
                      {:status 200 :body "[\"1.2.3.4\"," :headers {:content-type "application/json"}})
        kielletty-fn (fake-ei-saa-kutsua-fn "Rikkinäinen OID lista JSON. Ei saa kutsua kohdehakua.")
        lokiteksti (kutsu-ja-palauta-urakoiden-loki #(feikkaa-ja-kutsu-paivita-urakat {:fake-oid-fn fake-oid-fn :fake-kohteet-fn kielletty-fn}))]
    (is (str/includes? lokiteksti "JSON error (end-of-file inside array)"))))

(deftest velho-palauttaa-tyhjan-urakka-oid-listan
  (let [fake-oid-fn (fn [_ _ _]
                      {:status 200 :body "[]" :headers {:content-type "application/json"}})
        kielletty-fn (fake-ei-saa-kutsua-fn "Tyhjä OID lista, ei saa kutsua kohdehakua")
        lokiteksti (kutsu-ja-palauta-urakoiden-loki #(feikkaa-ja-kutsu-paivita-urakat {:fake-oid-fn fake-oid-fn :fake-kohteet-fn kielletty-fn}))]
    (is (str/includes? lokiteksti "Velho palautti tyhjän OID listan"))))

(defn varusteen-toteuma []
  (->> (q-map "SELECT toteuma,ulkoinen_oid FROM varustetoteuma_ulkoiset")
    (map (juxt :toteuma :ulkoinen_oid))))

(deftest paivita-varustetoteumat-valimaisille-kohteille-test
  (u "DELETE FROM varustetoteuma_ulkoiset")
  (let [odotettu-oidit-vastaus "[\"1.2.246.578.4.3.1.501.148568476\", \"1.2.246.578.4.3.1.501.52039770\"]"
        odotettu-kohteet-vastaus (slurp "test/resurssit/velho/varusteet/onnistuneet-test/varusterekisteri_api_v1_historia_kohteet_varusteet_kaiteet.jsonl")
        odotettu-oid-lista #{"1.2.246.578.4.3.1.501.52039770" "1.2.246.578.4.3.1.501.148568476"}
        odotettu-toteuma-lista (list ["lisatty" "1.2.246.578.4.3.1.501.52039770"]
                                 ["puhdistus" "1.2.246.578.4.3.1.501.52039770"]
                                 ["puhdistus" "1.2.246.578.4.3.1.501.148568476"]) 
        lokiteksti (kutsu-ja-palauta-varusteiden-loki #(feikkaa-ja-kutsu-varusteintegraatiota odotettu-oidit-vastaus odotettu-kohteet-vastaus)) 
        paivitetyt-totetumat (varusteen-toteuma)]
    (is (not (str/includes? lokiteksti "ERROR")))
    (is (= odotettu-toteuma-lista paivitetyt-totetumat))
    (is (= odotettu-oid-lista (kaikki-varustetoteuma-oidt)))))
