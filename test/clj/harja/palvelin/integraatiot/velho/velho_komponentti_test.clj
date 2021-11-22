(ns harja.palvelin.integraatiot.velho.velho-komponentti-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as string]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho-integraatio]
            [harja.palvelin.integraatiot.velho.sanomat.varuste-vastaanottosanoma :as varuste-vastaanottosanoma]
            [harja.pvm :as pvm]
            [certifiable.log :as log])
  (:import (org.postgis PGgeometry)))

(def kayttaja "jvh")

(def +velho-paallystystoteumat-url+ "http://localhost:1234/paallystystoteumat")
(def +velho-token-url+ "http://localhost:1234/token")

(def +velho-api-juuri+ "http://localhost:1234")

(def +varuste-tunnisteet-regex+
  (re-pattern
    (format "%s/(varusterekisteri|tiekohderekisteri|sijaintipalvelu)/api/v[0-9]/tunnisteet/[^/]+/[^/]+\\?.*"
            +velho-api-juuri+)))

(def +varuste-kohteet-regex+
  (re-pattern
    (format "%s/(varusterekisteri|tiekohderekisteri|sijaintipalvelu)/api/v[0-9]/historia/kohteet"
            +velho-api-juuri+)))

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
                                                     :varuste-client-id "feffefef"
                                                     :varuste-client-secret "puppua"})
                         [:db :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(defn lue-kohteen-tila [kohde-id]
  (first (q-map (str "SELECT velho_lahetyksen_aika,
                             velho_lahetyksen_tila,
                             velho_lahetyksen_vastaus
                        FROM yllapitokohde
                       WHERE id = " kohde-id ";"))))

(defn lue-rivien-tila [pot2-id]
  (let [raakat-rivit (q-map (str "SELECT kohdeosa_id AS \"id\",
                                         'paallystekerros' AS \"tyyppi\",
                                         velho_lahetyksen_aika,
                                         velho_rivi_lahetyksen_tila,
                                         velho_lahetyksen_vastaus
                                    FROM pot2_paallystekerros
                                   WHERE jarjestysnro = 1 AND
                                         pot2_id = " pot2-id "
                                 UNION
                                  SELECT id,
                                         'alusta' AS \"tyyppi\",
                                         velho_lahetyksen_aika,
                                         velho_rivi_lahetyksen_tila,
                                         velho_lahetyksen_vastaus
                                    FROM pot2_alusta
                                   WHERE pot2_id = " pot2-id ";"))
        rivit (map #(update % :tyyppi keyword) raakat-rivit)
        rivit-mappi (->> rivit
                         (map (fn [rivi]
                                {(select-keys rivi [:id :tyyppi])
                                 rivi}))
                         (into {}))]
    rivit-mappi))

(defn fake-token-palvelin [_ {:keys [body headers]} _]
  "{\"access_token\":\"TEST_TOKEN\",\"expires_in\":3600,\"token_type\":\"Bearer\"}")

(deftest token-epaonnistunut-palauta-tekninen-virhen-test
  (let [[kohde-id pot2-id urakka-id] (hae-pot2-testi-idt)
        kohteen-tila-ennen (lue-kohteen-tila kohde-id)
        rivien-tila-ennen (lue-rivien-tila pot2-id)
        fake-feilava-token-palvelin (fn [_ {:keys [body headers]} _]
                                      "{\"error\":\"invalid_client\"}")
        kieletty-palvelu (fn [_ {:keys [body headers]} _]
                           (is false "Ei saa kutsua jos ei saannut tokenia"))]
    (is (= "ei-lahetetty" (:velho_lahetyksen_tila kohteen-tila-ennen)))
    (with-fake-http
      [{:url +velho-token-url+ :method :post} fake-feilava-token-palvelin
       {:url +velho-paallystystoteumat-url+ :method :post} kieletty-palvelu]
      (velho-integraatio/laheta-kohde (:velho-integraatio jarjestelma) urakka-id kohde-id))

    (let [kohteen-tila-jalkeen (lue-kohteen-tila kohde-id)
          rivien-tila-jalkeen (lue-rivien-tila pot2-id)]
      (is (= "tekninen-virhe" (:velho_lahetyksen_tila kohteen-tila-jalkeen)))
      (is (= "Token pyyntö virhe invalid_client" (:velho_lahetyksen_vastaus kohteen-tila-jalkeen)))
      (is (= rivien-tila-ennen rivien-tila-jalkeen) "Rivien tilaa ei muuttunut"))))

(deftest laheta-kohteet-test
  (let [[kohde-id pot2-id urakka-id] (hae-pot2-testi-idt)
        urakka-yhaid (:yhaid (first (q-map (str "SELECT yhaid FROM yhatiedot WHERE urakka = " urakka-id ";"))))
        paallystekerros-idt (map :kohdeosa_id
                                 (q-map (str "SELECT kohdeosa_id FROM pot2_paallystekerros
                                               WHERE jarjestysnro = 1 AND pot2_id = " pot2-id ";")))
        _ (assert (< 1 (count paallystekerros-idt)) "Testitietokannalla ei löyty hyvä päällystekerros esimerkki")
        feilava-paallystekerros-id (second paallystekerros-idt)
        alusta-idt (map :id
                        (q-map (str "SELECT id FROM pot2_alusta
                                      WHERE pot2_id = " pot2-id ";")))
        _ (assert (< 1 (count alusta-idt)) "Testitietokannalla ei löyty hyvä alusta esimerkki")
        feilava-alusta-id (first alusta-idt)
        feilavat-1 #{{:tyyppi :alusta, :id feilava-alusta-id} {:tyyppi :paallystekerros, :id feilava-paallystekerros-id}}
        odotetut-pyynnot-1 (set (concat (map (fn [id] {:tyyppi :paallystekerros :id id}) paallystekerros-idt)
                                        (map (fn [id] {:tyyppi :alusta :id id}) alusta-idt)))
        odotetut-pyynnot-2 feilavat-1
        onnistuneet-pyynnot-1 (set/difference odotetut-pyynnot-1 odotetut-pyynnot-2)
        analysoi-body (fn [body]
                        (let [tyyppi (if (= (get-in body ["ominaisuudet" "sidottu-paallysrakenne" "tyyppi"])
                                            ["sidotun-paallysrakenteen-tyyppi/spt01"])
                                       :paallystekerros
                                       :alusta)
                              id (get-in body ["ominaisuudet" "korjauskohdeosan-ulkoinen-tunniste"])]
                          {:tyyppi tyyppi :id (Integer/parseInt id)}))
        etsi-rivit (fn [rivien-tila pred]
                     (->> rivien-tila
                          (filter #(pred (second %)))
                          (map first)
                          set))
        feilavat (atom feilavat-1)
        pyynnot (atom {})
        vastaanotetut (atom #{})
        vastaanotetut? (fn [body-avain] (contains? @vastaanotetut body-avain))
        fake-palvelin (fn [_ {:keys [body headers]} _]
                        (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                        (let [body (json/read-str body)
                              body-avain (analysoi-body body)]
                          (swap! pyynnot into [{body-avain {:headers headers :body body}}])
                          (is (not (vastaanotetut? body-avain)) (str "Ei saa lähettää saman sisällön kaksi kertaa: " body-avain))
                          (if (contains? @feilavat body-avain)
                            (do
                              {:status 500 :body (str "{\"viesti\": \"Kohde ei validi\", \"virheet\": \"" body-avain "\"}")})
                            (do
                              (swap! vastaanotetut conj body-avain)
                              (let [velho-oid (str "OID-" (:tyyppi body-avain) "-" (:id body-avain))
                                    body-vastaus {:oid velho-oid} ; todellisuudessa on koko alkuperainen body JA oid
                                    body-vastaus-json (json/write-str body-vastaus)]
                                {:status 200 :body body-vastaus-json})))))]
    (asenna-pot-lahetyksen-tila kohde-id pot2-id)

    (let [tila-alussa (lue-rivien-tila pot2-id)
          kohteen-tila-alussa (lue-kohteen-tila kohde-id)]
      (is (= #{}
             (etsi-rivit tila-alussa #(= (:velho_rivi_lahetyksen_tila %) "onnistunut"))) "Ei mitään on onnistunut vielä")
      (is (= #{}
             (etsi-rivit tila-alussa #(= (:velho_rivi_lahetyksen_tila %) "epaonnistunut"))) "Ei mitään on epäonnistunut vielä")
      (is (= (+ (count paallystekerros-idt) (count alusta-idt))
             (count (etsi-rivit tila-alussa #(= (:velho_rivi_lahetyksen_tila %) "ei-lahetetty")))) "Kaikki on lähetämättä")
      (is (= {:velho_lahetyksen_aika nil, :velho_lahetyksen_tila "ei-lahetetty", :velho_lahetyksen_vastaus nil}
             kohteen-tila-alussa)))

    (with-fake-http
      [{:url +velho-token-url+ :method :post} fake-token-palvelin
       {:url +velho-paallystystoteumat-url+ :method :post} fake-palvelin]

      (velho-integraatio/laheta-kohde (:velho-integraatio jarjestelma) urakka-id kohde-id))

    (is (= (+ (count alusta-idt) (count paallystekerros-idt))
           (count @pyynnot))
        (str "Kokonaan täytyy olla: " (count paallystekerros-idt) " päällystekerrosta + " (count alusta-idt) " alustaa pyyntöä"))
    (is (= onnistuneet-pyynnot-1 @vastaanotetut) "Vastaanotetut ovat ne jotka eivät feilaneet")
    (let [tila-1 (lue-rivien-tila pot2-id)
          kohteen-tila-1 (lue-kohteen-tila kohde-id)]
      (is (= odotetut-pyynnot-1 (set (keys tila-1))) "Alussa, kaikki rivit ovat yritetty")
      (is (= (set onnistuneet-pyynnot-1)
             (etsi-rivit tila-1 #(= (:velho_rivi_lahetyksen_tila %) "onnistunut"))) "Onnistuneet ovat ne jotka ei feilanut")
      (is (= @feilavat
             (etsi-rivit tila-1 #(= (:velho_rivi_lahetyksen_tila %) "epaonnistunut"))) "Epäonnistuneet ovat juri ne jotka felaisimme")
      (is (= #{}
             (etsi-rivit tila-1 #(= (:velho_rivi_lahetyksen_tila %) "ei-lahetetty"))) "Ei mitään on jäännyt lähetämättä")
      (is (= "osittain-onnistunut" (:velho_lahetyksen_tila kohteen-tila-1))))

    (reset! feilavat #{})
    (reset! pyynnot {})
    (reset! vastaanotetut #{})
    (with-fake-http
      [{:url +velho-token-url+ :method :post} fake-token-palvelin
       {:url +velho-paallystystoteumat-url+ :method :post} fake-palvelin]

      (velho-integraatio/laheta-kohde (:velho-integraatio jarjestelma) urakka-id kohde-id))

    (is (= odotetut-pyynnot-2
           (-> @pyynnot keys set)) "Lähettämme vain ne jotka eivät onnistuneet ennen.")
    (is (= feilavat-1 @vastaanotetut) "Tällä kerta onnistuivat ne jotka ennen feilasivat")
    (let [tila-2 (lue-rivien-tila pot2-id)
          kohteen-tila-2 (lue-kohteen-tila kohde-id)]
      (is (= odotetut-pyynnot-1
             (etsi-rivit tila-2 #(= (:velho_rivi_lahetyksen_tila %) "onnistunut"))) "Onnistuneet ovat nyt kaikki")
      (is (= #{}
             (etsi-rivit tila-2 #(= (:velho_rivi_lahetyksen_tila %) "epaonnistunut"))) "Ei mitään enää on epäonnistunut")
      (is (= #{}
             (etsi-rivit tila-2 #(= (:velho_rivi_lahetyksen_tila %) "ei-lahetetty"))) "Ei mitään on jäännyt lähetämättä")
      (is (= "valmis" (:velho_lahetyksen_tila kohteen-tila-2))))))

(deftest varuste-token-epaonnistunut-ei-saa-kutsua-palvelua-test
  (let [fake-feilava-token (fn [_ {:keys [body headers]} _]
                             "{\"error\":\"invalid_client\"}")
        kieletty (fn [_ {:keys [body headers url]} _]
                   (is false (format "Ei saa kutsua jos ei saannut tokenia headers: %s url: %s" headers url)))]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} fake-feilava-token
       {:url +varuste-tunnisteet-regex+ :method :get} kieletty
       {:url +varuste-kohteet-regex+ :method :post} kieletty]
      (velho-integraatio/hae-varustetoteumat (:velho-integraatio jarjestelma)))))

(deftest varuste-oid-hakeminen-epaonnistunut-ala-rajahda-test
  (let [fake-feilava-tunnisteet (fn [_ {:keys [body headers]} _]
                                  (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                                  {:status 500 :body "{\n    \"viesti\": \"Sisäinen palvelukutsu epäonnistui: palvelinvirhe\"\n}"})
        kieletty (fn [_ {:keys [body headers url]} _]
                   (is false (format "Ei saa kutsua jos ei oikeita oid-tunnuksia headers: %s url: %s" headers url)))]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-feilava-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} kieletty]
      (velho-integraatio/hae-varustetoteumat (:velho-integraatio jarjestelma)))))

(deftest varuste-velho-tunnisteet-palauttaa-rikkinaisen-vastauksen-test
  (let [fake-feilava-tunnisteet (fn [_ {:keys [body headers]} _]
                                  (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                                  {:status 200 :body "[\n    \"1.2.246.578.4.3.1.501.120103774\",\n    \"1.2.246.578.4.3.1.501.120103775\",\n"})
        kieletty (fn [_ {:keys [body headers url]} _]
                   (is false (format "Ei saa kutsua jos ei oikeita oid-tunnuksia headers: %s url: %s" headers url)))]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-feilava-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} kieletty]
      (velho-integraatio/hae-varustetoteumat (:velho-integraatio jarjestelma)))))

(deftest varuste-velho-kohteet-palauttaa-500-test
  (let [fake-tunnisteet (fn [_ {:keys [body headers]} _]
                          (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                          {:status 200 :body "[\n    \"1.2.246.578.4.3.1.501.120103774\",\n    \"1.2.246.578.4.3.1.501.120103775\"]"})
        fake-failaava-kohteet (fn [_ {:keys [body headers]} _]
                                (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                                {:status 500 :body (slurp "test/resurssit/velho/varusteet/varusterekisteri_api_v1_kohteet_500_fail.jsonl")})]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-failaava-kohteet]
      (velho-integraatio/hae-varustetoteumat (:velho-integraatio jarjestelma)))))

(deftest varuste-velho-kohteet-palauttaa-rikkinaisen-vastauksen-test
  (let [fake-tunnisteet (fn [_ {:keys [body headers]} _]
                          (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                          {:status 200 :body "[\n    \"1.2.246.578.4.3.1.501.120103774\",\n    \"1.2.246.578.4.3.1.501.120103775\"]"})
        fake-failaava-kohteet (fn [_ {:keys [body headers]} _]
                                (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                                {:status 200 :body "[{\"kohdeluokka\":\"varusteet/kaiteet\",\"alkusijainti\""})]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-failaava-kohteet]
      (velho-integraatio/hae-varustetoteumat (:velho-integraatio jarjestelma)))))

(deftest varuste-velho-kohteet-palauttaa-vaaraa-tietoa-test
  (let [fake-tunnisteet (fn [_ {:keys [body headers]} _]
                          (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                          {:status 200 :body "[\"1.2.3.4.5\"]"})
        fake-failaava-kohteet (fn [_ {:keys [body headers]} _]
                                (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                                {:status 200 :body +ylimaarainen-54321-kohde+})]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-failaava-kohteet]
      (velho-integraatio/hae-varustetoteumat (:velho-integraatio jarjestelma)))))

(defn varuste-lue-kaikki-kohteet []
  (let [rivit (q-map (str "SELECT * FROM varustetoteuma2"))]
    rivit))


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
  ; ASETA
  (u "DELETE FROM varustetoteuma2")
  (let [testitunniste "osajoukkoja-test"
        osajoukkojen-koko 2
        odotettu-syotetiedostoparien-maara 2                ;Tämä varmistaa, ettei testisyötteitä jää käyttämättä
        fake-token-palvelin (fn [_ {:keys [body headers]} _]
                              "{\"access_token\":\"TEST_TOKEN\",\"expires_in\":3600,\"token_type\":\"Bearer\"}")
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
                                (reset! @kohteiden-kutsukerta 0)
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
                             vastauksen-oid-joukko (->> oidit-lahtojoukko
                                                        (partition osajoukkojen-koko)
                                                        (nth @kohteiden-kutsukerta))
                             vastauksen-kohteet (->> vastauksen-kohteiden-rivit
                                                     (partition osajoukkojen-koko)
                                                     (nth @kohteiden-kutsukerta)
                                                     (string/join "\n"))]
                         (is (= vastauksen-oid-joukko oidit-pyynnosta)
                             "Odotettiin kohteiden hakua samalla oid-listalla kuin hae-oid antoi")
                         (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                         (swap! kohteiden-kutsukerta inc)
                         (swap! fake-kohteet-kutsujen-maara inc)
                         {:status 200 :body vastauksen-kohteet}))]
    ; SUORITA
    (with-fake-http
      [{:url +velho-token-url+ :method :post} fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-kohteet]
      (let [raportoi-oid-haku-fn velho-integraatio/varuste-raportoi-oid-haku]
        (with-redefs [velho-integraatio/varuste-raportoi-oid-haku (partial laske-oid-vastaukset raportoi-oid-haku-fn)
                      velho-integraatio/+varuste-kohde-haku-maksimi-koko+ osajoukkojen-koko]
          (velho-integraatio/hae-varustetoteumat (:velho-integraatio jarjestelma)))))
    ; TARKASTA
    (is (= @odotettu-ei-tyhja-oid-vastaus @saatu-ei-tyhja-oid-vastaus) "Odotettiin samaa määrää ei-tyhjiä oid-listoja, kuin fake-velho palautti.")
    #_(is (= odotettu-syotetiedostoparien-maara @saatu-ei-tyhja-oid-vastaus)
        "Testitiedostoja on eri määrä kuin fake-tunnisteissa on haettu. Kaikki testitiedostot on käytettävä testissä.")

    (is (= @odotettu-tyhja-oid-vastaus @saatu-tyhja-oid-vastaus) "Odotettiin samaa määrää tyhjiä oid-listoja, kuin fake-velho palautti.")

    (let [kaikki-varustetoteumat (varuste-lue-kaikki-kohteet) ; TODO tarkista, että kannassa oid-lista vastaa testissä syötettyjä
          expected-varustetoteuma-maara 3]
      #_(is (= expected-varustetoteuma-maara (count kaikki-varustetoteumat))
          (str "Odotettiin " expected-varustetoteuma-maara " varustetoteumaa tietokannassa testin jälkeen")))))

(deftest varuste-hae-varusteet-onnistuneet-test
  ; ASETA
  (u "DELETE FROM varustetoteuma2")
  (let [testitunniste "onnistuneet-test"
        odotettu-syotetiedostoparien-maara 2                ;Tämä varmistaa, ettei testisyötteitä jää käyttämättä
        fake-token-palvelin (fn [_ {:keys [body headers]} _]
                              "{\"access_token\":\"TEST_TOKEN\",\"expires_in\":3600,\"token_type\":\"Bearer\"}")
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
      [{:url +velho-token-url+ :method :post} fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-kohteet]
      (let [raportoi-oid-haku-fn velho-integraatio/varuste-raportoi-oid-haku]
        (with-redefs [velho-integraatio/varuste-raportoi-oid-haku (partial laske-oid-vastaukset raportoi-oid-haku-fn)]
          (velho-integraatio/hae-varustetoteumat (:velho-integraatio jarjestelma))))
      )
    ; TARKASTA
    (is (= @odotettu-ei-tyhja-oid-vastaus @saatu-ei-tyhja-oid-vastaus) "Odotettiin samaa määrää ei-tyhjiä oid-listoja, kuin fake-velho palautti.")
    (is (= odotettu-syotetiedostoparien-maara @saatu-ei-tyhja-oid-vastaus)
        "Testitiedostoja on eri määrä kuin fake-tunnisteissa on haettu. Kaikki testitiedostot on käytettävä testissä.")

    (is (= @odotettu-tyhja-oid-vastaus @saatu-tyhja-oid-vastaus) "Odotettiin samaa määrää tyhjiä oid-listoja, kuin fake-velho palautti.")

    (let [kaikki-varustetoteumat (varuste-lue-kaikki-kohteet) ; TODO tarkista, että kannassa oid-lista vastaa testissä syötettyjä
          expected-varustetoteuma-maara 4]
      (is (= expected-varustetoteuma-maara (count kaikki-varustetoteumat))
          (str "Odotettiin " expected-varustetoteuma-maara " varustetoteumaa tietokannassa testin jälkeen")))))

(deftest varuste-toteuman-kirjaus-on-idempotentti-test
  ; ASETA
  (u "DELETE FROM varustetoteuma2")
  (let [testitunniste "idempotentti-test"
        fake-token-palvelin (fn [_ {:keys [body headers]} _]
                              "{\"access_token\":\"TEST_TOKEN\",\"expires_in\":3600,\"token_type\":\"Bearer\"}")
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
      [{:url +velho-token-url+ :method :post} fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-kohteet]
      (velho-integraatio/hae-varustetoteumat (:velho-integraatio jarjestelma)))
    ; TARKASTA
    (is (= 1 (count (varuste-lue-kaikki-kohteet))))
    ))

(deftest varuste-ei-saa-kutsua-kohde-hakua-jos-oid-lista-on-tyhja-test
  ; ASETA
  (u "DELETE FROM varustetoteuma2")
  (let [testitunniste "oid-lista-on-tyhja-test"
        fake-token-palvelin (fn [_ {:keys [body headers]} _]
                              "{\"access_token\":\"TEST_TOKEN\",\"expires_in\":3600,\"token_type\":\"Bearer\"}")
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
      [{:url +velho-token-url+ :method :post} fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} kieletty]
      (let [raportoi-onnistunut-fn velho-integraatio/varuste-raportoi-oid-haku]
        (with-redefs [velho-integraatio/varuste-raportoi-oid-haku (partial laske-oid-vastaukset raportoi-onnistunut-fn)]
          (velho-integraatio/hae-varustetoteumat (:velho-integraatio jarjestelma)))))
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
  ; ASETA
  (u "DELETE FROM varustetoteuma2")
  (let [testitunniste "uusin-voittaa-test"
        odotettu-syotetiedostoparien-maara 1                ;Tämä varmistaa, ettei testisyötteitä jää käyttämättä
        fake-token-palvelin (fn [_ {:keys [body headers]} _]
                              "{\"access_token\":\"TEST_TOKEN\",\"expires_in\":3600,\"token_type\":\"Bearer\"}")
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
      [{:url +velho-token-url+ :method :post} fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-kohteet]
      (let [raportoi-oid-haku-fn velho-integraatio/varuste-raportoi-oid-haku]
        (with-redefs [velho-integraatio/varuste-raportoi-oid-haku (partial laske-oid-vastaukset raportoi-oid-haku-fn)]
          (velho-integraatio/hae-varustetoteumat (:velho-integraatio jarjestelma))))
      )
    ; TARKASTA
    (is (= @odotettu-ei-tyhja-oid-vastaus @saatu-ei-tyhja-oid-vastaus) "Odotettiin samaa määrää ei-tyhjiä oid-listoja, kuin fake-velho palautti.")
    (is (= odotettu-syotetiedostoparien-maara @saatu-ei-tyhja-oid-vastaus)
        "Testitiedostoja on eri määrä kuin fake-tunnisteissa on haettu. Kaikki testitiedostot on käytettävä testissä.")

    (is (= @odotettu-tyhja-oid-vastaus @saatu-tyhja-oid-vastaus) "Odotettiin samaa määrää tyhjiä oid-listoja, kuin fake-velho palautti.")

    (let [kaikki-varustetoteumat (varuste-lue-kaikki-kohteet) ; TODO tarkista, että kannassa oid-lista vastaa testissä syötettyjä
          expected-varustetoteuma-maara 1]
      (is (= expected-varustetoteuma-maara (count kaikki-varustetoteumat))
          (str "Odotettiin " expected-varustetoteuma-maara " varustetoteumaa tietokannassa testin jälkeen"))
      (let [kohde (first kaikki-varustetoteumat)]
        (is (= (:muokkaaja kohde) "uusi muokkaaja") "Odotettiin uusimman tiedon korvanneen vanhan.")))))

(deftest urakka-id-kohteelle-test-test
  (let [db (:db jarjestelma)
        oid "1.2.3.4.5"
        a {:tie 22 :osa 5 :etaisyys 4355}
        b {:tie 22 :osa 5 :etaisyys 4555}
        tuntematon-sijainti {:sijainti {:tie -1 :osa -1 :etaisyys -1}}
        varuste-oulussa-sijainti {:sijainti a}
        kaide-oulussa-sijainti {:alkusijainti a :loppusijainti b}
        ennen-urakoiden-alkuja-pvm "2000-01-01T00:00:00Z"
        oulun-MHU-urakka-2019-2024-alkupvm "2019-10-01T00:00:00Z"
        oulun-MHU-urakka-2019-2024-loppupvm "2024-09-30T00:00:00Z"
        aktiivinen-oulu-urakka-alkupvm "2020-10-22T00:00:00Z"
        aktiivinen-oulu-urakka-loppupvm "2024-10-22T00:00:00Z"
        expected-aktiivinen-oulu-urakka-id 26
        expected-oulu-MHU-urakka-id 35
        lisaa-pakolliset (fn [s o m] (assoc s :oid o :muokattu m))]
    (is (nil?
          (velho-integraatio/urakka-id-kohteelle
            db
            (lisaa-pakolliset tuntematon-sijainti oid oulun-MHU-urakka-2019-2024-alkupvm)))
        "Urakkaa ei pidä löytyä tuntemattomalle sijainnille")
    (is (nil?
          (velho-integraatio/urakka-id-kohteelle
            db
            (lisaa-pakolliset varuste-oulussa-sijainti oid ennen-urakoiden-alkuja-pvm)))
        "Urakkaa ei pidä löytyä tuntemattomalle ajalle")
    (is (= expected-oulu-MHU-urakka-id
           (velho-integraatio/urakka-id-kohteelle
             db
             (lisaa-pakolliset varuste-oulussa-sijainti oid oulun-MHU-urakka-2019-2024-alkupvm)))
        (str "Odotettiin Oulun MHU urakka id: " expected-oulu-MHU-urakka-id ", koska tyyppi = 'teiden-hoito' on uudempi (parempi) kuin 'hoito'"))
    (is (= expected-oulu-MHU-urakka-id
           (velho-integraatio/urakka-id-kohteelle
             db
             (lisaa-pakolliset varuste-oulussa-sijainti oid oulun-MHU-urakka-2019-2024-loppupvm)))
        (str "Odotettiin Oulun MHU urakka id: " expected-oulu-MHU-urakka-id ", koska tyyppi = 'teiden-hoito' on uudempi (parempi) kuin 'hoito'"))
    (is (= expected-oulu-MHU-urakka-id
           (velho-integraatio/urakka-id-kohteelle
             db
             (lisaa-pakolliset varuste-oulussa-sijainti oid aktiivinen-oulu-urakka-alkupvm)))
        (str "Odotettiin Oulun MHU urakka id: " expected-oulu-MHU-urakka-id ", koska tyyppi = 'teiden-hoito' on uudempi (parempi) kuin 'hoito'"))
    (is (= expected-aktiivinen-oulu-urakka-id
           (velho-integraatio/urakka-id-kohteelle
             db
             (lisaa-pakolliset varuste-oulussa-sijainti oid aktiivinen-oulu-urakka-loppupvm))))

    (is (= expected-aktiivinen-oulu-urakka-id
           (velho-integraatio/urakka-id-kohteelle
             db
             (lisaa-pakolliset kaide-oulussa-sijainti oid aktiivinen-oulu-urakka-loppupvm))))
    ))

(deftest sijainti-kohteelle-test
  (let [db (:db jarjestelma)
        a {:tie 22 :osa 5 :etaisyys 4355}
        b {:tie 22 :osa 6 :etaisyys 4555}
        tuntematon-sijainti {:sijainti {:tie -1 :osa -1 :etaisyys -1}}
        varuste-oulussa-sijainti {:sijainti a}
        kaide-oulussa-sijainti {:alkusijainti a :loppusijainti b}]
    (is (instance? PGgeometry (velho-integraatio/sijainti-kohteelle db varuste-oulussa-sijainti)))
    (is (nil? (velho-integraatio/sijainti-kohteelle db tuntematon-sijainti)))
    (is (instance? PGgeometry (velho-integraatio/sijainti-kohteelle db kaide-oulussa-sijainti)))))