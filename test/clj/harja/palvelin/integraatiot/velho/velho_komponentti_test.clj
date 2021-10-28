(ns harja.palvelin.integraatiot.velho.velho-komponentti-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [clojure.set :as set]
            [clojure.string :as string]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho-integraatio]
            [harja.pvm :as pvm]
            [certifiable.log :as log])
  (:import (java.nio.file FileSystems)))

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

(def +varuste-ehjat-tunnisteet+ ["1.2.246.578.4.3.1.501.148568476"
                                 "1.2.246.578.4.3.1.501.52039770"
                                 "1.2.3.4.5.6.7.8.123456"]) ; Voisi oikeasti tulla, jos tapahtuu katastrofi Velhossa
(def +ehjat-tunnisteet-json-muodossa+ (json/write-str +varuste-ehjat-tunnisteet+))

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :velho-integraatio (component/using
                         (velho-integraatio/->Velho {:paallystetoteuma-url +velho-paallystystoteumat-url+
                                                     :token-url +velho-token-url+
                                                     :kayttajatunnus "abc-123"
                                                     :salasana "blabla"
                                                     :varuste-api-juuri +velho-api-juuri+
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

(deftest token-epaonnistunut-palauta-tekninen-virhen
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

(deftest laheta-kohteet
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
                        (let [tyyppi (if (some? (get-in body ["ominaisuudet" "sidottu-paallysrakenne"]))
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

(deftest varuste-token-epaonnistunut-ei-saa-kutsua-palvelua
  (let [fake-feilava-token (fn [_ {:keys [body headers]} _]
                             "{\"error\":\"invalid_client\"}")
        kieletty (fn [_ {:keys [body headers url]} _]
                   (is false (format "Ei saa kutsua jos ei saannut tokenia headers: %s url: %s" headers url)))]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} fake-feilava-token
       {:url +varuste-tunnisteet-regex+ :method :get} kieletty
       {:url +varuste-kohteet-regex+ :method :post} kieletty]
      (velho-integraatio/hae-varustetoteumat (:velho-integraatio jarjestelma)))))

(deftest varuste-oid-hakeminen-epaonnistunut-ala-rajahda
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

(deftest varuste-velho-tunnisteet-palauttaa-rikkinaisen-vastauksen
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

(deftest varuste-velho-kohteet-palauttaa-500
  (let [fake-tunnisteet (fn [_ {:keys [body headers]} _]
                          (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                          {:status 200 :body "[\n    \"1.2.246.578.4.3.1.501.120103774\",\n    \"1.2.246.578.4.3.1.501.120103775\"]"})
        fake-failaava-kohteet (fn [_ {:keys [body headers]} _]
                                (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                                {:status 500 :body (slurp "test/resurssit/velho/varusterekisteri_api_v1_kohteet_500_fail.jsonl")})]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-failaava-kohteet]
      (velho-integraatio/hae-varustetoteumat (:velho-integraatio jarjestelma)))))

(deftest varuste-velho-kohteet-palauttaa-rikkinaisen-vastauksen
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

(deftest varuste-velho-kohteet-palauttaa-vaaraa-tietoa
  (let [fake-tunnisteet (fn [_ {:keys [body headers]} _]
                          (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                          {:status 200 :body "[\"1.2.3.4\"]"})
        fake-failaava-kohteet (fn [_ {:keys [body headers]} _]
                                (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                                {:status 200 :body "[{\"kohdeluokka\":\"varusteet/kaiteet\",\"oid\":\"4.3.2.1\"},{\"kohdeluokka\":\"varusteet/kaiteet\",\"oid\":\"1.2.3.4\"}]"})]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-failaava-kohteet]
      (velho-integraatio/hae-varustetoteumat (:velho-integraatio jarjestelma)))))


(deftest varuste-hae-varusteet-onnistunut
  (let [analysoi-body (fn [body]
                        (let [tyyppi (if (some? (get-in body ["ominaisuudet" "sidottu-paallysrakenne"]))
                                       :paallystekerros
                                       :alusta)
                              id (get-in body ["ominaisuudet" "korjauskohdeosan-ulkoinen-tunniste"])]
                          {:tyyppi tyyppi :id id}))
        fake-token-palvelin (fn [_ {:keys [body headers]} _]
                              "{\"access_token\":\"TEST_TOKEN\",\"expires_in\":3600,\"token_type\":\"Bearer\"}")
        pyynnot (atom {})
        vastaanotetut (atom #{})
        vastaanotetut? (fn [body-avain] (contains? @vastaanotetut body-avain))

        fake-tunnisteet (fn [_ {:keys [body headers]} _]
                          (println "petrisi1310: " body headers)
                          (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                          {:status 200 :body +ehjat-tunnisteet-json-muodossa+})
        fake-kohteet (fn [_ {:keys [body headers]} _]
                       (println "petrisi1311: " body headers)
                       (is (= +varuste-ehjat-tunnisteet+ (json/read-str body))
                           "Odotettiin kohteiden hakua samalla oid-listalla kuin hae-oid antoi")
                       (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                       ; Todo: Assertoi body
                       {:status 200 :body (slurp "test/resurssit/velho/varusterekisteri_api_v1_historia_varusteet_kaiteet.jsonl")})]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} fake-token-palvelin
       {:url +varuste-tunnisteet-regex+ :method :get} fake-tunnisteet
       {:url +varuste-kohteet-regex+ :method :post} fake-kohteet]

      (velho-integraatio/hae-varustetoteumat (:velho-integraatio jarjestelma))))

  (is (= 4 (+ 2 2)) "Koodia puuttuu vielä"))

(deftest paattele-urakka-id-kohteelle-test
  (let [db (:db jarjestelma)
        tuntematon-sijainti {:tie -1 :aosa -1 :aet -1}
        varuste-oulussa-sijainti {:tie 22 :aosa 5 :aet 4355}
        ennen-urakoiden-alkuja-pvm "2000-01-01T00:00:00Z"
        oulun-MHU-urakka-2019-2024-alkupvm "2019-10-01T00:00:00Z"
        oulun-MHU-urakka-2019-2024-loppupvm "2024-09-30T00:00:00Z"
        aktiivinen-oulu-urakka-alkupvm "2020-10-22T00:00:00Z"
        aktiivinen-oulu-urakka-loppupvm "2024-10-22T00:00:00Z"
        expected-aktiivinen-oulu-urakka-id 26
        expected-oulu-MHU-urakka-id 35]
    (is (nil?
          (velho-integraatio/paattele-urakka-id-kohteelle
            (:db jarjestelma)
            {:sijainti tuntematon-sijainti :muokattu oulun-MHU-urakka-2019-2024-alkupvm}))
        "Urakkaa ei pidä löytyä tuntemattomalle sijainnille")
    (is (nil?
          (velho-integraatio/paattele-urakka-id-kohteelle
            (:db jarjestelma)
            {:sijainti varuste-oulussa-sijainti :muokattu ennen-urakoiden-alkuja-pvm}))
        "Urakkaa ei pidä löytyä tuntemattomalle ajalle")
    (is (= expected-oulu-MHU-urakka-id
           (velho-integraatio/paattele-urakka-id-kohteelle
             db
             {:sijainti varuste-oulussa-sijainti :muokattu oulun-MHU-urakka-2019-2024-alkupvm}))
        (format "Odotettiin Oulun MHU urakka id: %s, koska tyyppi = 'teiden-hoito' on uudempi (parempi) kuin 'hoito'"
                expected-oulu-MHU-urakka-id))
    (is (= expected-oulu-MHU-urakka-id
           (velho-integraatio/paattele-urakka-id-kohteelle
             db
             {:sijainti varuste-oulussa-sijainti :muokattu oulun-MHU-urakka-2019-2024-loppupvm}))
        (format "Odotettiin Oulun MHU urakka id: %s, koska tyyppi = 'teiden-hoito' on uudempi (parempi) kuin 'hoito'"
                expected-oulu-MHU-urakka-id))
    (is (= expected-oulu-MHU-urakka-id
           (velho-integraatio/paattele-urakka-id-kohteelle
             db
             {:sijainti varuste-oulussa-sijainti :muokattu aktiivinen-oulu-urakka-alkupvm}))
        (format "Odotettiin Oulun MHU urakka id: %s, koska tyyppi = 'teiden-hoito' on uudempi (parempi) kuin 'hoito'"
                expected-oulu-MHU-urakka-id))
    (is (= expected-aktiivinen-oulu-urakka-id
           (velho-integraatio/paattele-urakka-id-kohteelle
             db
             {:sijainti varuste-oulussa-sijainti :muokattu aktiivinen-oulu-urakka-loppupvm})))
    ))

(defn listaa-matchaavat-tiedostot [juuri glob]
  (let [tietolaji-matcher (.getPathMatcher
                            (FileSystems/getDefault)
                            (str "glob:" glob))]
    (->> juuri
         clojure.java.io/file
         .listFiles
         (filter #(.isFile %))
         (filter #(.matches tietolaji-matcher (.getFileName (.toPath %))))
         (mapv #(.getPath %))
         )))

(defn json->kohde [json-lahde lahdetiedosto]
  (let [lahderivi (inc (first json-lahde))                  ; inc, koska 0-based -> järjestysluvuksi
        json (second json-lahde)]
    (log/debug "Ladataan JSON tiedostosta: " lahdetiedosto " riviltä:" lahderivi)
    (->
      json
      (json/read-str :key-fn keyword)
      (assoc :lahdetiedosto (str lahdetiedosto) :lahderivi (str lahderivi)))))

(defn lue-ndjson->kohteet [tiedosto]
  (let [rivit (clojure.string/split-lines (slurp tiedosto))]
    (filter #(contains? % :oid) (map #(json->kohde % tiedosto) (map-indexed #(vector %1 %2) rivit)))))

(defn muunna-tiedostolista-kohteiksi [tiedostot]
  (flatten (mapv lue-ndjson->kohteet tiedostot)))

(defn lataa-kohteet [palvelu kohdeluokka]
  (->
    (listaa-matchaavat-tiedostot
      (str "test/resurssit/velho/" palvelu)
      (str "*" kohdeluokka ".jsonl"))
    muunna-tiedostolista-kohteiksi))

(defn poimi-tietolaji-tunnisteesta [tunniste]
  (as-> tunniste a
        (clojure.string/split a #"\.")
        (nth a 7)
        (str "tl" a)
        (keyword a)))                                       ; (def oid "1.2.246.578.4.3.11.507.51457624")

(defn assertoi-kohteen-tietolaji-on-kohteen-tunnisteessa [kohteet]
  (log/debug (format "Testiaineistossa %s kohdetta." (count kohteet)))
  (doseq [kohde kohteet]
    (let [tietolaji-tunnisteesta (poimi-tietolaji-tunnisteesta (:oid kohde))
          tietolaji-poikkeus-map {:tl514 :tl501}            ; Melukaiteet ovat kaiteita nyt!
          odotettu-tietolaji (get tietolaji-poikkeus-map tietolaji-tunnisteesta tietolaji-tunnisteesta)]
      (let [paatelty-tietolaji (velho-integraatio/paattele-varusteen-tietolaji kohde)]
        (is (= odotettu-tietolaji paatelty-tietolaji)
            (format "Testitiedoston: %s rivillä: %s (tunniste: %s) odotettu tietolaji: %s ei vastaa pääteltyä tietolajia: %s"
                    (:lahdetiedosto kohde)
                    (:lahderivi kohde)
                    (:oid kohde)
                    odotettu-tietolaji
                    paatelty-tietolaji
                    ))))))

(deftest paattele-varuteen-tl-tienvarsikalusteet-test       ;{:tl503 :tl504 :tl505 :tl507 :tl508 :tl516}
  (assertoi-kohteen-tietolaji-on-kohteen-tunnisteessa (lataa-kohteet "varusterekisteri" "tienvarsikalusteet")))

(deftest paattele-varuteen-tl-kaiteet-test                  ; {:tl501}
  (assertoi-kohteen-tietolaji-on-kohteen-tunnisteessa (lataa-kohteet "varusterekisteri" "kaiteet")))

(deftest paattele-varuteen-tl-liikennemerkit-test           ; {:tl505}
  (assertoi-kohteen-tietolaji-on-kohteen-tunnisteessa (lataa-kohteet "varusterekisteri" "liikennemerkit")))

(deftest paattele-varuteen-tl-rumpuputket-test              ; {:tl509}
  (assertoi-kohteen-tietolaji-on-kohteen-tunnisteessa (lataa-kohteet "varusterekisteri" "rumpuputket")))

(deftest paattele-varuteen-tl-kaivot-test                   ; {:tl512}
  (assertoi-kohteen-tietolaji-on-kohteen-tunnisteessa (lataa-kohteet "varusterekisteri" "kaivot")))

(deftest paattele-varuteen-tl-reunapaalut-test              ; {:tl513}
  (assertoi-kohteen-tietolaji-on-kohteen-tunnisteessa (lataa-kohteet "varusterekisteri" "reunapaalut")))

(deftest paattele-varuteen-tl-aidat-test                    ; {:tl515}
  (assertoi-kohteen-tietolaji-on-kohteen-tunnisteessa (lataa-kohteet "varusterekisteri" "aidat")))

(deftest paattele-varuteen-tl-portaat-test                  ; {:tl517}
  (assertoi-kohteen-tietolaji-on-kohteen-tunnisteessa (lataa-kohteet "varusterekisteri" "portaat")))

(deftest paattele-varuteen-tl-puomit-test                   ; {:tl520}
  (assertoi-kohteen-tietolaji-on-kohteen-tunnisteessa (lataa-kohteet "varusterekisteri" "puomit")))

(deftest paattele-varuteen-tl-reunatuet-test                ; {:tl522}
  (assertoi-kohteen-tietolaji-on-kohteen-tunnisteessa (lataa-kohteet "varusterekisteri" "reunatuet")))

(deftest paattele-varuteen-tl-viherkuviot-test              ; {:tl524}
  (assertoi-kohteen-tietolaji-on-kohteen-tunnisteessa (lataa-kohteet "tiekohderekisteri" "viherkuviot"))
  )