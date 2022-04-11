(ns harja.palvelin.integraatiot.velho.varusteet-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as string]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho-integraatio]
            [harja.palvelin.integraatiot.velho.varusteet :as varusteet]
            [harja.palvelin.integraatiot.velho.yhteiset-test :as yhteiset-test]
            [harja.pvm :as pvm]
            [clojure.string :as str])
  (:import (org.postgis PGgeometry)))

(def kayttaja "jvh")

(def +velho-paallystystoteumat-url+ "http://localhost:1234/paallystystoteumat")
(def +velho-token-url+ "http://localhost:1234/token")

(def +velho-api-juuri+ "http://localhost:1234")

(def +varuste-tunnisteet-regex+
  (re-pattern
    (str +velho-api-juuri+ "/(varusterekisteri|tiekohderekisteri|sijaintipalvelu)/api/v[0-9]/tunnisteet/[^/]+/[^/]+\\?.*")))

(def +varuste-kohteet-regex+
  (re-pattern
    (str +velho-api-juuri+ "/(varusterekisteri|tiekohderekisteri|sijaintipalvelu)/api/v[0-9]/historia/kohteet")))

(def +velho-urakka-oid-url+ (str +velho-api-juuri+ "/hallintorekisteri/api/v1/tunnisteet/urakka/maanteiden-hoitourakka"))
(def +velho-urakka-kohde-url+ (str +velho-api-juuri+ "hallintorekisteri/api/v1/kohteet"))

(def +ylimaarainen-54321-kohde+ "[{\"kohdeluokka\":\"varusteet/kaiteet\",\"oid\":\"5.4.3.2.1\"},{\"kohdeluokka\":\"varusteet/kaiteet\",\"oid\":\"1.2.3.4.5\"}]")

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
                                                     :varuste-client-id "feffefef"
                                                     :varuste-client-secret "puppua"})
                         [:db :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(defn fake-ei-saa-kutsua [syy-teksti]
  (fn [_ {:keys [body headers url]} _]
    (is false (str "Ei saa kutsua: '" syy-teksti "' otsikot: " headers " url: " url))
    {:status 400 :body ""}))

(defn with-lokita-urakkahakuvirhe-redefs
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

(defn kaikki-kohteet []
  (q-map (str "SELECT * FROM varustetoteuma_ulkoiset")))

(defn kaikki-virheet []
  (q-map "SELECT * FROM varustetoteuma_ulkoiset_virhe"))

(deftest varuste-token-epaonnistunut-ei-saa-kutsua-palvelua-test
  (yhteiset-test/tyhjenna-velho-tokenit-atomi)
  (let [fake-feilava-token (fn [_ {:keys [body headers]} _]
                             "{\"error\":\"invalid_client\"}")
        kieletty (fake-ei-saa-kutsua "Ei ole saanut tokenia")]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} fake-feilava-token
       {:url +varuste-tunnisteet-regex+ :method :get} kieletty
       {:url +varuste-kohteet-regex+ :method :post} kieletty]
      (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+]]
        (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma))))))

(deftest varuste-oid-hakeminen-epaonnistunut-ala-rajahda-test
  (let [fake-feilava-tunnisteet (fn [_ {:keys [body headers]} _]
                                  (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                                  {:status 500 :body "{\n    \"viesti\": \"Sisäinen palvelukutsu epäonnistui: palvelinvirhe\"\n}"})
        kieletty (fake-ei-saa-kutsua "Ei ole oikeita oid-tunnuksia")]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-feilava-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} kieletty]
      (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+]]
        (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma))
        (is (= 1 (count (kaikki-virheet))))
        (when (= 1 (count (kaikki-virheet)))
          (is (str/includes? (:virhekuvaus (first (kaikki-virheet))) "järjestelmä palautti statuskoodin: 500")))))))

(deftest varuste-velho-tunnisteet-palauttaa-rikkinaisen-vastauksen-test
  (let [fake-feilava-tunnisteet (fn [_ {:keys [body headers]} _]
                                  (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                                  {:status 200 :body "[\n    \"1.2.246.578.4.3.1.501.120103774\",\n    \"1.2.246.578.4.3.1.501.120103775\",\n"})
        kieletty (fake-ei-saa-kutsua "Ei ole oikeita oid-tunnuksia")]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-feilava-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} kieletty]
      (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+]]
        (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma))
        (is (= 1 (count (kaikki-virheet))))
        (when (= 1 (count (kaikki-virheet)))
          (is (str/includes? (:virhekuvaus (first (kaikki-virheet))) "end-of-file inside array")))))))

(deftest varuste-velho-kohteet-palauttaa-500-test
  (let [fake-tunnisteet (fn [_ {:keys [body headers]} _]
                          (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                          {:status 200 :body "[\n    \"1.2.246.578.4.3.1.501.120103774\",\n    \"1.2.246.578.4.3.1.501.120103775\"]"})
        fake-failaava-kohteet (fn [_ {:keys [body headers]} _]
                                (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                                {:status 500 :body (slurp "test/resurssit/velho/varusteet/varusterekisteri_api_v1_kohteet_500_fail.jsonl")})]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-failaava-kohteet]
      (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+]]
        (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma))
        (is (= 1 (count (kaikki-virheet))))
        (when (= 1 (count (kaikki-virheet)))
          (is (str/includes? (:virhekuvaus (first (kaikki-virheet))) "Ulkoinen käsittelyvirhe")))))))

(deftest varuste-velho-kohteet-palauttaa-rikkinaisen-vastauksen-test
  (u "DELETE FROM varustetoteuma_ulkoiset")
  (u "DELETE FROM varustetoteuma_ulkoiset_virhe")
  (let [odotettu-kohdevirherivien-lukumaara 1               ;TODO VHAR-6099 pitää ensin korjata
        odotettu-kohderivien-lukumaara 0
        fake-tunnisteet (fn [_ {:keys [body headers]} _]
                          (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                          {:status 200 :body "[\n    \"1.2.246.578.4.3.1.501.120103774\",\n    \"1.2.246.578.4.3.1.501.120103775\"]"})
        fake-failaava-kohteet (fn [_ {:keys [body headers]} _]
                                (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                                {:status 200 :body "[{\"kohdeluokka\":\"varusteet/kaiteet\",\"alkusijainti\""})]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-failaava-kohteet]
      (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+]]
        (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma))
        (is (= odotettu-kohderivien-lukumaara (count (kaikki-kohteet))) "Ei saa lisätä kohderiviä")
        (is (= 1 (count (kaikki-virheet))))
        (when (= 1 (count (kaikki-virheet)))
          (is (str/includes? (:virhekuvaus (first (kaikki-virheet))) "end-of-file inside object")))))))

(deftest varuste-velho-kohteet-palauttaa-vaaraa-tietoa-test
  (let [fake-tunnisteet (fn [_ {:keys [body headers]} _]
                          (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                          {:status 200 :body "[\"1.2.3.4.5\"]"})
        fake-failaava-kohteet (fn [_ {:keys [body headers]} _]
                                (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                                {:status 200 :body +ylimaarainen-54321-kohde+})]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-failaava-kohteet]
      (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+]]
        (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma))))))

(defn testi-tiedosto-oideille [{:keys [palvelu api-versio kohdeluokka] :as lahde} testitunniste]
  (let [kohdeluokka-tiedostonimessa (string/replace kohdeluokka "/" "_")]
    (str "test/resurssit/velho/varusteet/" testitunniste "/" palvelu "_api_" api-versio "_tunnisteet_" kohdeluokka-tiedostonimessa ".jsonl")))

(defn testi-tiedosto-kohteille [{:keys [palvelu api-versio kohdeluokka] :as lahde} testitunniste]
  (let [kohdeluokka-tiedostonimessa (string/replace kohdeluokka "/" "_")]
    (str "test/resurssit/velho/varusteet/" testitunniste "/" palvelu "_api_" api-versio "_historia_kohteet_" kohdeluokka-tiedostonimessa ".jsonl")))

(defn olemassa-testi-tiedostot? [lahde testitunniste]
  (let [oid-tiedostonimi (testi-tiedosto-oideille lahde testitunniste)
        kohde-tiedostonimi (testi-tiedosto-kohteille lahde testitunniste)]
    (and (.exists (io/file oid-tiedostonimi)) (.exists (io/file kohde-tiedostonimi)))))

(defn lahde-oid-urlista [url]
  (let [url-osat (string/split url #"[/\\?]")
        palvelu (nth url-osat 3)
        api-versio (nth url-osat 5)
        kohdeluokka (str (nth url-osat 7) "/" (nth url-osat 8))]
    {:palvelu palvelu :api-versio api-versio :kohdeluokka kohdeluokka}))

(deftest varuste-hae-varusteet-kayttaa-osajoukkoja-test
  (u "DELETE FROM varustetoteuma_ulkoiset")
  ; ASETA
  (let [testitunniste "osajoukkoja-test"
        osajoukkojen-koko 2
        odotettu-syotetiedostoparien-maara 1                ;Tämä varmistaa, ettei testisyötteitä jää käyttämättä
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
        fake-tunnisteet (fn [_ {:keys [body headers url]} _]
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
        fake-kohteet (fn [_ {:keys [body headers url]} _]
                       (let [oidit-pyynnosta (json/read-str body)
                             oidit-lahtojoukko (json/read-str @odotettu-oidit-vastaus)
                             vastauksen-kohteiden-rivit (string/split-lines @odotettu-kohteet-vastaus)
                             vastauksen-oid-joukko (as-> oidit-lahtojoukko x
                                                         (partition osajoukkojen-koko osajoukkojen-koko nil x)
                                                         (nth x @kohteiden-kutsukerta))
                             vastauksen-kohteet (as-> vastauksen-kohteiden-rivit x
                                                      (partition osajoukkojen-koko osajoukkojen-koko nil x)
                                                      (nth x @kohteiden-kutsukerta)
                                                      (string/join "\n" x))]
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
       {:url +varuste-kohteet-regex+ :method :post} fake-kohteet]
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

    (let [kaikki-varustetoteumat (kaikki-kohteet) ; TODO tarkista, että kannassa oid-lista vastaa testissä syötettyjä
          expected-varustetoteuma-maara 3]
      (is (= expected-varustetoteuma-maara (count kaikki-varustetoteumat))
          (str "Odotettiin " expected-varustetoteuma-maara " varustetoteumaa tietokannassa testin jälkeen")))))

(deftest varuste-hae-varusteet-onnistuneet-test
  (u "DELETE FROM varustetoteuma_ulkoiset")
  ; ASETA
  (let [testitunniste "onnistuneet-test"
        odotettu-syotetiedostoparien-maara 2                ;Tämä varmistaa, ettei testisyötteitä jää käyttämättä
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
        fake-tunnisteet (fn [_ {:keys [body headers url]} _]
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
        fake-kohteet (fn [_ {:keys [body headers url]} _]
                       (is (= (json/read-str @odotettu-oidit-vastaus) (json/read-str body))
                           "Odotettiin kohteiden hakua samalla oid-listalla kuin hae-oid antoi")
                       (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                       {:status 200 :body @odotettu-kohteet-vastaus})]
    ; SUORITA
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-kohteet]
      (let [raportoi-oid-haku-fn varusteet/lokita-oid-haku]
        (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+ varusteet/+tl506+]
                      varusteet/lokita-oid-haku (partial laske-oid-vastaukset raportoi-oid-haku-fn)]
          (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma))))
      )
    ; TARKASTA
    (is (= @odotettu-ei-tyhja-oid-vastaus @saatu-ei-tyhja-oid-vastaus) "Odotettiin samaa määrää ei-tyhjiä oid-listoja, kuin fake-velho palautti.")
    (is (= odotettu-syotetiedostoparien-maara @saatu-ei-tyhja-oid-vastaus)
        "Testitiedostoja on eri määrä kuin fake-tunnisteissa on haettu. Kaikki testitiedostot on käytettävä testissä.")

    (is (= @odotettu-tyhja-oid-vastaus @saatu-tyhja-oid-vastaus) "Odotettiin samaa määrää tyhjiä oid-listoja, kuin fake-velho palautti.")

    (let [kaikki-varustetoteumat (kaikki-kohteet) ; TODO tarkista, että kannassa oid-lista vastaa testissä syötettyjä
          expected-varustetoteuma-maara 4]
      (is (= expected-varustetoteuma-maara (count kaikki-varustetoteumat))
          (str "Odotettiin " expected-varustetoteuma-maara " varustetoteumaa tietokannassa testin jälkeen")))))

(deftest varuste-toteuman-kirjaus-on-idempotentti-test
  (u "DELETE FROM varustetoteuma_ulkoiset")
  ; ASETA
  (let [testitunniste "idempotentti-test"
        odotettu-kohteet-vastaus (atom {})
        odotettu-oidit-vastaus (atom {})
        fake-tunnisteet (fn [_ {:keys [body headers url]} _]
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
        fake-kohteet (fn [_ {:keys [body headers url]} _]
                       (is (= (json/read-str @odotettu-oidit-vastaus) (json/read-str body))
                           "Odotettiin kohteiden hakua samalla oid-listalla kuin hae-oid antoi")
                       (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                       {:status 200 :body @odotettu-kohteet-vastaus})]
    ; SUORITA
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-kohteet]
      (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+]]
        (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma))))
    ; TARKASTA
    (is (= 1 (count (kaikki-kohteet))))))

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
        fake-tunnisteet (fn [_ {:keys [body headers url]} _]
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
       {:url +varuste-kohteet-regex+ :method :post} kieletty]
      (let [raportoi-onnistunut-fn varusteet/lokita-oid-haku]
        (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+]
                      varusteet/lokita-oid-haku (partial laske-oid-vastaukset raportoi-onnistunut-fn)]
          (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma)))))
    ; TARKASTA
    (is (= (+ 1 @annettu-tyhja-oid-vastaus) @saatu-tyhja-oid-vastaus))))

(deftest varuste-toteuma-paivittyy-uusin-voittaa-test
  "On mahdollista, että Velhosta tulee uudelleen vanha toteuma samalla `velho_oid` ja `muokattu` tiedoilla.
  Historiaa saatetaan muokata.
  Silloin tallennetaan tiedot siltä varalta, että jos ne ovat kuitenkin muuttuneet. Uusin tieto voitaa.
  Lisäksi kirjoitetaan warning lokiin.

  #ext-urpo 28.10.2021 11:39:
  Petri Sirkkala
  Miten Varusteiden \"version-voimassaolo\" on tarkoitus tulkita. Merkataanko varusteen poisto kirjaamalla versio,
  jossa voimassaolo on päättynyt?
  Onko tosiaan niin, että \"version-voimassaolo\" on tyyppiä päivämäärä? Eikö samana päivänä voi olla kuin yksi versio kohteesta?
  Kimmo Rantala  11:53
  version-voimassaolo on tosiaan päivämääräväli. Samalla kohteella ei voi olla päällekkäisiä versioita.
  null tarkoittaa avointa. Eli jos version voimassaolon loppu on null, niin se versio on voimassa (toistaiseksi)
  Jos kohdetta päivitetään, niin sille tulee uusi versio uusilla tiedoilla ja vanha merkitään päättyneeksi
  alku on inklusiivinen, loppu eksklusiivinen
  Jos kohteen kaikki versiot ovat päättyneet, niin silloin kohdetta ei enää ole (poistunut/lakannut/vanhentunut tms)
  itseasiassa version voimassaolon loppu (tai miksei alkukin) voi olla myös tulevaisuudessa. Hauissa voi antaa
  tilannepäivän parametrina jolloin saadaan sinä päivänä voimassaollut versio kohteesta. Oletuksena annetaan kuluvan päivän versio.
  Päivämäärätaso tosiaan riittää version voimassaololle. Ei noi oikeat tiekohteet montaa kertaa päivässä muutu.
  Jos on jokin virheellinen tieto versiolla niin versioita voi myös muuttaa jälkikäteen (eli korjata historiaa)"
  (u "DELETE FROM varustetoteuma_ulkoiset")
  ; ASETA
  (let [testitunniste "uusin-voittaa-test"
        odotettu-syotetiedostoparien-maara 1                ;Tämä varmistaa, ettei testisyötteitä jää käyttämättä
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
        fake-tunnisteet (fn [_ {:keys [body headers url]} _]
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
        fake-kohteet (fn [_ {:keys [body headers url]} _]
                       (is (= (json/read-str @odotettu-oidit-vastaus) (json/read-str body))
                           "Odotettiin kohteiden hakua samalla oid-listalla kuin hae-oid antoi")
                       (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                       {:status 200 :body @odotettu-kohteet-vastaus})]
    ; SUORITA
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-kohteet]
      (let [raportoi-oid-haku-fn varusteet/lokita-oid-haku]
        (with-redefs [varusteet/+tietolajien-lahteet+ [varusteet/+tl501+]
                      varusteet/lokita-oid-haku (partial laske-oid-vastaukset raportoi-oid-haku-fn)]
          (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma))))
      )
    ; TARKASTA
    (is (= @odotettu-ei-tyhja-oid-vastaus @saatu-ei-tyhja-oid-vastaus) "Odotettiin samaa määrää ei-tyhjiä oid-listoja, kuin fake-velho palautti.")
    (is (= odotettu-syotetiedostoparien-maara @saatu-ei-tyhja-oid-vastaus)
        "Testitiedostoja on eri määrä kuin fake-tunnisteissa on haettu. Kaikki testitiedostot on käytettävä testissä.")

    (is (= @odotettu-tyhja-oid-vastaus @saatu-tyhja-oid-vastaus) "Odotettiin samaa määrää tyhjiä oid-listoja, kuin fake-velho palautti.")

    (let [kaikki-varustetoteumat (kaikki-kohteet) ; TODO tarkista, että kannassa oid-lista vastaa testissä syötettyjä
          expected-varustetoteuma-maara 1]
      (is (= expected-varustetoteuma-maara (count kaikki-varustetoteumat))
          (str "Odotettiin " expected-varustetoteuma-maara " varustetoteumaa tietokannassa testin jälkeen"))
      (when (= expected-varustetoteuma-maara (count kaikki-varustetoteumat))
        (let [kohde (first kaikki-varustetoteumat)]
          (is (= (:muokkaaja kohde) "uusi muokkaaja") "Odotettiin uusimman tiedon korvanneen vanhan."))))))

(deftest urakka-id-kohteelle-test
  (u "DELETE FROM varustetoteuma_ulkoiset")
  (u "DELETE FROM varustetoteuma_ulkoiset_virhe")
  (let [kohde-virheet (fn [] (kaikki-virheet))
        db (:db jarjestelma)
        oid "1.2.3.4.5"
        ii-oid "1.2.3.4.5.6"
        ii-muutoksen-lahde-oid "1.2.3.4.1234"               ; Urakka Velhossa
        a {:tie 22 :osa 5 :etaisyys 4355}
        b {:tie 22 :osa 5 :etaisyys 4555}
        tuntematon-sijainti {:sijainti {:tie -1 :osa -1 :etaisyys -1}}
        varuste-oulussa-sijainti {:sijainti a}
        kaide-oulussa-sijainti {:alkusijainti a :loppusijainti b}
        varuste-iissa-sijainti {:sijainti a}              ; Sijainti ei saa vaikuttaa, kun Iissa varusteella on muutoksen-lahde-oid
        ennen-urakoiden-alkuja-pvm "2000-01-01T00:00:00Z"
        oulun-MHU-urakka-2019-2024-alkupvm "2019-10-01T00:00:00Z"
        oulun-MHU-urakka-2019-2024-loppupvm "2024-09-30T00:00:00Z"
        aktiivinen-oulu-urakka-alkupvm "2020-10-22T00:00:00Z"
        aktiivinen-oulu-urakka-loppupvm "2024-10-22T00:00:00Z"
        aktiivinen-ii-urakka-alkupvm "2021-10-01T00:00:00Z"
        expected-aktiivinen-oulu-urakka-id 26
        expected-oulu-MHU-urakka-id 35
        expected-ii-MHU-urakka-id 36
        lisaa-muutoksen-lahde (fn [kohde muutoksen-lahde-oid]
                                (assoc kohde :muutoksen-lahde-oid muutoksen-lahde-oid))
        lisaa-pakolliset (fn [kohde oid muokattu] (-> kohde
                                                      (assoc :oid oid :muokattu muokattu)
                                                      (assoc-in [:version-voimassaolo :alku] (first (str/split muokattu #"T")))))]
    (is (= 1 (u "UPDATE urakka SET velho_oid = '" ii-muutoksen-lahde-oid "' WHERE id = " expected-ii-MHU-urakka-id)))
    (is (nil?
          (varusteet/urakka-id-kohteelle
            db
            (lisaa-pakolliset tuntematon-sijainti oid oulun-MHU-urakka-2019-2024-alkupvm))
          )
        "Urakkaa ei pidä löytyä tuntemattomalle sijainnille")
    (is (nil?
          (varusteet/urakka-id-kohteelle
            db
            (lisaa-pakolliset varuste-oulussa-sijainti oid ennen-urakoiden-alkuja-pvm)))
        "Urakkaa ei pidä löytyä tuntemattomalle ajalle")
    (is (= expected-oulu-MHU-urakka-id
           (varusteet/urakka-id-kohteelle
             db
             (lisaa-pakolliset varuste-oulussa-sijainti oid oulun-MHU-urakka-2019-2024-alkupvm)))
        (str "Odotettiin Oulun MHU urakka id: " expected-oulu-MHU-urakka-id ", koska tyyppi = 'teiden-hoito' on uudempi (parempi) kuin 'hoito'"))
    (is (= expected-oulu-MHU-urakka-id
           (varusteet/urakka-id-kohteelle
             db
             (lisaa-pakolliset varuste-oulussa-sijainti oid oulun-MHU-urakka-2019-2024-loppupvm)))
        (str "Odotettiin Oulun MHU urakka id: " expected-oulu-MHU-urakka-id ", koska tyyppi = 'teiden-hoito' on uudempi (parempi) kuin 'hoito'"))
    (is (= expected-oulu-MHU-urakka-id
           (varusteet/urakka-id-kohteelle
             db
             (lisaa-pakolliset varuste-oulussa-sijainti oid aktiivinen-oulu-urakka-alkupvm)))
        (str "Odotettiin Oulun MHU urakka id: " expected-oulu-MHU-urakka-id ", koska tyyppi = 'teiden-hoito' on uudempi (parempi) kuin 'hoito'"))
    (is (= expected-aktiivinen-oulu-urakka-id
           (varusteet/urakka-id-kohteelle
             db
             (lisaa-pakolliset varuste-oulussa-sijainti oid aktiivinen-oulu-urakka-loppupvm))))
    (is (= expected-aktiivinen-oulu-urakka-id
           (varusteet/urakka-id-kohteelle
             db
             (lisaa-pakolliset kaide-oulussa-sijainti oid aktiivinen-oulu-urakka-loppupvm))))
    (is (= expected-ii-MHU-urakka-id
           (varusteet/urakka-id-kohteelle
             db
             (-> varuste-iissa-sijainti
                 (lisaa-pakolliset ii-oid aktiivinen-ii-urakka-alkupvm)
                 (lisaa-muutoksen-lahde ii-muutoksen-lahde-oid))))
        "muutoksen-lahde-oid on enemmän merkitsevä kuin sijanti")))

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
  (let [odotettu-viimeisin-aika (pvm/iso-8601->aika "2021-11-23T00:00:00Z")
        fake-tunnisteet (fn [_ {:keys [body headers url]} _]
                          {:status 200 :body "[]"})
        fake-tunnisteet-2 (fn [_ {:keys [body headers url]} _]
                            (let [jalkeen (->> url
                                               (re-find #"jalkeen=(.*)")
                                               second
                                               pvm/iso-8601->aika)]
                              (is (= odotettu-viimeisin-aika jalkeen)))
                            {:status 200 :body "[]"})
        ei-sallittu (fn [_ {:keys [body headers url]} _]
                      (is false "Oid-lista oli tyhjä. Tätä ei saa kutsua."))]
    ; SUORITA
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} ei-sallittu]
      ; with-redefs korvataan kello, josta viimeisin hakuaika poimitaan
      (with-redefs [harja.pvm/nyt (fn [] (identity odotettu-viimeisin-aika))]
        (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma))
        (let [viimeksi-haetut (q-map (str "SELECT kohdeluokka, viimeisin_hakuaika
                                                  FROM varustetoteuma_ulkoiset_viimeisin_hakuaika_kohdeluokalle"))]
          (is (every? (fn [x] (= odotettu-viimeisin-aika (:viimeksi_haettu x))) viimeksi-haetut)
              "Kaikilla kohdeluokilla piti olla odotettu viimeisin hakuaika.")
          (let [odotetut-kohdelajit (set (map :kohdeluokka varusteet/+tietolajien-lahteet+))
                viimeksi-haettu-kohdelajit (set (map :kohdeluokka viimeksi-haetut))]
            (is (= viimeksi-haettu-kohdelajit odotetut-kohdelajit)
                "Kaikkien kohdeluokkien pitää olla varustetoteuma_ulkoiset_viimeisin_hakuaika_kohdeluokalle taulussa.")))))

    ; Haetaan varusteet uudelleen.
    ; Fake-tunnisteet-2 tarkistaa, että viimeksi haettu on odotettu
    (with-fake-http
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet-2
       {:url +varuste-kohteet-regex+ :method :post} ei-sallittu]
      (velho-integraatio/tuo-uudet-varustetoteumat-velhosta (:velho-integraatio jarjestelma)))))

(deftest varuste-varmista-tietokannan-kohdeluokkien-lista-vastaa-koodissa-olevaa-test
  (let [tietokannan-kohdeluokat (->> "SELECT enumlabel
                                      FROM pg_type pt
                                      JOIN pg_enum pe ON pt.oid = pe.enumtypid
                                      WHERE typname = 'kohdeluokka_tyyppi';"
                                     q-map
                                     (map :enumlabel)
                                     set)
        koodin-kohdeluokat (->> varusteet/+tietolajien-lahteet+
                                (map :kohdeluokka)
                                set)]
    (is (= koodin-kohdeluokat tietokannan-kohdeluokat) "Tietokannassa pitää olla samat kohdeluokat kuin koodissa.")))

(defn fake-tunnisteet [odotettu-oidit-vastaus]
  (fn [_ {:keys [body headers url]} _]
    {:status 200 :body (json/write-str odotettu-oidit-vastaus) :headers {:content-type "application/json"}}))

(defn fake-kohteet [odotettu-oidit-vastaus odotettu-kohteet-vastaus]
  (fn [_ {:keys [body headers url]} _]
    (is (= (set odotettu-oidit-vastaus)
           (set (json/read-str body)))
        "Odotettiin kohteiden hakua samalla oid-listalla kuin hae-oid antoi")
    (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
    {:status 200 :body odotettu-kohteet-vastaus :headers {:content-type "application/x-ndjson"}}))

(defn feikkaa-ja-kutsu
  ([odotettu-oidit-vastaus odotettu-kohteet-vastaus]
   (feikkaa-ja-kutsu {:fake-oid-fn (fake-tunnisteet odotettu-oidit-vastaus)
                      :fake-kohteet-fn (fake-kohteet odotettu-oidit-vastaus odotettu-kohteet-vastaus)}))
  ([{:keys [fake-oid-fn fake-kohteet-fn] :as fake-funktiot}]
   (with-fake-http
     [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
      {:url +velho-urakka-oid-url+ :method :get} fake-oid-fn
      {:url +velho-urakka-kohde-url+ :method :post} fake-kohteet-fn]
     (velho-integraatio/paivita-mhu-urakka-oidt-velhosta (:velho-integraatio jarjestelma)))))

(defn urakat-joilla-on-velho-oid []
  (set (q-map "SELECT id, tyyppi, urakkanro, velho_oid FROM urakka WHERE velho_oid IS NOT NULL")))

(deftest hae-mhu-urakka-oidt-test
  "Aurinkoisen päivän testi koko haku ja tallennus polulle."
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
        lokiteksti (with-lokita-urakkahakuvirhe-redefs #(feikkaa-ja-kutsu odotettu-oidit-vastaus odotettu-kohteet-vastaus))]
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
        lokiteksti (with-lokita-urakkahakuvirhe-redefs #(feikkaa-ja-kutsu odotettu-oidit-vastaus odotettu-kohteet-vastaus))]
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
        lokiteksti (with-lokita-urakkahakuvirhe-redefs #(feikkaa-ja-kutsu odotettu-oidit-vastaus odotettu-kohteet-vastaus))]
    (is (str/includes? lokiteksti "Virhe kohdistettaessa Velho urakkaa '1.2.246.578.8.1.147502791'"))
    (is (str/includes? lokiteksti "Urakka taulun velho_oid rivien lukumäärä ei vastaa Velhosta saatujen kohteiden määrää."))
    (is (= odotettu-urakka-lista (urakat-joilla-on-velho-oid)))))

(deftest urakka-haku-on-idempotentti
  "Ensisijaisesti urakoiden haun tulee olla idempotentti."
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
        lokiteksti1 (with-lokita-urakkahakuvirhe-redefs #(feikkaa-ja-kutsu odotettu-oidit-vastaus odotettu-kohteet-vastaus))
        _ (is (= odotettu-tulosjoukko (urakat-joilla-on-velho-oid)))
        lokiteksti2 (with-lokita-urakkahakuvirhe-redefs #(feikkaa-ja-kutsu odotettu-oidit-vastaus odotettu-kohteet-vastaus))]
    (is (= odotettu-tulosjoukko (urakat-joilla-on-velho-oid)))
    (is (not (str/includes? (str lokiteksti1 lokiteksti2) "ERROR")))))

(deftest velho-palauttaa-500-urakka-oideja-haettaessa
  (let [fake-oid-fn (fn [_ {:keys [body headers url]} _]
                      {:status 500 :body "spec spec spec..." :headers {:content-type "text/html"}})
        kieletty-fn (fake-ei-saa-kutsua "Ei ole saatu oikeita oideja")
        lokiteksti (with-lokita-urakkahakuvirhe-redefs #(feikkaa-ja-kutsu {:fake-oid-fn fake-oid-fn :fake-kohteet-fn kieletty-fn}))]
    (is (str/includes? lokiteksti "Ulkoinen järjestelmä palautti statuskoodin: 500 ja virheen: spec spec spec..."))))

(deftest velho-palauttaa-500-urakka-kohteita-haettaessa
  (let [odotettu-oidit-vastaus ["1.2.246.578.8.1.147502788" "1.2.246.578.8.1.147502790"]
        fake-oid-fn (fake-tunnisteet odotettu-oidit-vastaus)
        fake-kohteet-fn (fn [_ {:keys [body headers url]} _]
                          {:status 500 :body "spec spec spec..." :headers {:content-type "text/html"}})
        lokiteksti (with-lokita-urakkahakuvirhe-redefs #(feikkaa-ja-kutsu {:fake-oid-fn fake-oid-fn :fake-kohteet-fn fake-kohteet-fn}))]
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
        lokiteksti (with-lokita-urakkahakuvirhe-redefs #(feikkaa-ja-kutsu odotettu-oidit-vastaus odotettu-kohteet-vastaus))]
    (is (str/includes? lokiteksti "JSON jäsennys epäonnistui. JSON (alku 200 mki): '{\"ominaisuudet\":{\"urakkakoodi\":\"1248\"},asdsaasdasdasadjkhgsadjkhgsadkjhgsadkjhgsadkjsahgdksajdhgaskjdhgsadkjhsagdkjsahgdsakjhdgsakjdhgsakjdhsagdkjsahgdksajhdgaskjdhgsakjdhgaskjhsagksjadhgdsakjhgasdas'"))
    (is (str/includes? lokiteksti "Urakka taulun velho_oid rivien lukumäärä ei vastaa Velhosta saatujen kohteiden määrää."))
    (is (not (str/includes? lokiteksti "SQL UPDATE palautti 0 muuttuneiden rivien lukumääräksi.")))
    (is (= odotettu-urakka-lista (urakat-joilla-on-velho-oid)))))

(deftest velho-urakka-oid-json-on-rikki
  (let [fake-oid-fn (fn [_ {:keys [body headers url]} _]
                      {:status 200 :body "[\"1.2.3.4\"," :headers {:content-type "application/json"}})
        kielletty-fn (fake-ei-saa-kutsua "Rikkinäinen OID lista JSON. Ei saa kutsua kohdehakua.")
        lokiteksti (with-lokita-urakkahakuvirhe-redefs #(feikkaa-ja-kutsu {:fake-oid-fn fake-oid-fn :fake-kohteet-fn kielletty-fn}))]
    (is (str/includes? lokiteksti "JSON error (end-of-file inside array)"))))

(deftest velho-palauttaa-tyhjan-urakka-oid-listan
  (let [fake-oid-fn (fn [_ {:keys [body headers url]} _]
                      {:status 200 :body "[]" :headers {:content-type "application/json"}})
        kielletty-fn (fake-ei-saa-kutsua "Tyhjä OID lista, ei saa kutsua kohdehakua")
        lokiteksti (with-lokita-urakkahakuvirhe-redefs #(feikkaa-ja-kutsu {:fake-oid-fn fake-oid-fn :fake-kohteet-fn kielletty-fn}))]
    (is (str/includes? lokiteksti "Velho palautti tyhjän OID listan"))))