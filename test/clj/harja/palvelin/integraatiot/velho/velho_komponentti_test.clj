(ns harja.palvelin.integraatiot.velho.velho-komponentti-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [clojure.set :as set]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho-integraatio]
            [harja.pvm :as pvm]
            [certifiable.log :as log])
  (:import (java.nio.file FileSystems)))

(def kayttaja "jvh")

(def +velho-paallystystoteumat-url+ "http://localhost:1234/paallystystoteumat")
(def +velho-token-url+ "http://localhost:1234/token")

(def +velho-varuste-muuttuneet-url+ "http://localhost:1234/varusterekisteri/api/v1/tunnisteet/varusteet/")
(def +velho-varuste-hae-kohde-lista-url+ "http://localhost:1234/varusterekisteri/api/v1/kohteet")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :velho-integraatio (component/using
                         (velho-integraatio/->Velho {:paallystetoteuma-url +velho-paallystystoteumat-url+
                                                     :token-url +velho-token-url+
                                                     :kayttajatunnus "abc-123"
                                                     :salasana "blabla"
                                                     :varuste-muuttuneet-url +velho-varuste-muuttuneet-url+
                                                     :varuste-hae-kohde-lista-url +velho-varuste-hae-kohde-lista-url+
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
                          {:tyyppi tyyppi :id id}))
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

(deftest hae-varusteet
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

        fake-varuste-hae-tunnisteet (fn [_ {:keys [body headers]} _]
                                      (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                                      (let [body-vastaus-json (slurp "test/resurssit/velho/varusterekisteri_api_v1_tunnisteet_varusteet_portaat.json")]
                                        {:status 200 :body body-vastaus-json}))
        fake-varuste-hae-kohteet (fn [_ {:keys [headers]} _]
                                   (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                                   ; Todo: Assertoi body
                                   (let [body-vastaus-json (slurp "test/resurssit/velho/varusterekisteri_api_v1_kohteet.ndjson")]
                                     {:status 200 :body body-vastaus-json}))
        ]
    (with-fake-http
      [{:url +velho-token-url+ :method :post} fake-token-palvelin
       {:url (str +velho-varuste-muuttuneet-url+ "kaiteet?jalkeen=2021-09-01T00:00:00Z") :method :get} fake-varuste-hae-tunnisteet
       {:url +velho-varuste-hae-kohde-lista-url+ :method :post} fake-varuste-hae-kohteet]

      (velho-integraatio/hae-varustetoteumat (:velho-integraatio jarjestelma))))

  (is (= 4 (+ 2 2)) "Koodia puuttuu vielä"))

(defn listaa-matchaavat-tiedostot [juuri glob]
  (let [tietolaji-matcher (.getPathMatcher
                            (FileSystems/getDefault)
                            (str "glob:" glob))]
    (->> juuri
         clojure.java.io/file
         file-seq
         (filter #(.isFile %))
         (filter #(.matches tietolaji-matcher (.getFileName (.toPath %))))
         (mapv #(.getPath %))
         )))

(defn listaa-tl-testitiedostot [tietolaji]
  (listaa-matchaavat-tiedostot "test/resurssit/velho" (str tietolaji "*.json")))

(defn json->kohde [json-lahde lahdetiedosto]
  (let [lahderivi (inc (first json-lahde))                 ; inc, koska 0-based -> järjestysluvuksi
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

(defn lataa-kohteet-tietolajille [tietolaji]
  (muunna-tiedostolista-kohteiksi (listaa-tl-testitiedostot tietolaji)))

(defn lataa-latauspalvelun-kohteet [tietokokonaisuus]
  (->
    (listaa-matchaavat-tiedostot
      "test/resurssit/velho/latauspalvelu"
      (str "*" tietokokonaisuus ".jsonl"))
    (muunna-tiedostolista-kohteiksi)))

(defn assertoi-kohteet
  [odotettu-tietolaji tietokokonaisuus kohdelaji kohteet]
  (doseq [kohde kohteet]
    (is (= odotettu-tietolaji (velho-integraatio/paattele-tietolaji tietokokonaisuus kohdelaji kohde))
        (str "Testitiedoston: " (:lahdetiedosto kohde) " rivillä: "
             (:lahderivi kohde) "tietolajin pitää olla " odotettu-tietolaji))))

(defn poimi-tietolaji-oidsta [oid]
  (as-> oid a
        (clojure.string/split a #"\.")
        (nth a 7)
        (str "tl" a)
        (keyword a)))                                       ; (def oid "1.2.246.578.4.3.11.507.51457624")

(defn assertoi-kohteen-tietolaji-on-kohteen-oid-ssa [tietokokonaisuus kohdelaji kohteet]
  (let [tunnetut-tietolajit #{:tl501 :tl503 :tl504 :tl505 :tl506}]
    (doseq [kohde kohteet]
      (let [odotettu-tietolaji (poimi-tietolaji-oidsta (:oid kohde))]
        (when (contains? tunnetut-tietolajit odotettu-tietolaji)
          (let [paatelty-tietolaji (velho-integraatio/paattele-tietolaji
                                     tietokokonaisuus kohdelaji kohde)]
            (is (= odotettu-tietolaji paatelty-tietolaji)
                (format "Testitiedoston: %s rivillä: %s (oid: %s) odotettu tietolaji: %s ei vastaa pääteltyä tietolajia: %s"
                        (:lahdetiedosto kohde)
                        (:lahderivi kohde)
                        (:oid kohde)
                        odotettu-tietolaji
                        paatelty-tietolaji
                        ))))))))

; TODO Kohdeluokka tieto näyttäisi sisältävän kohteen propertynä `tietokokonaisuus/kohdelaji` tiedon.
; TODO Tämän avulla voi poistaa turhat :varusteet :tienvarsikalusteet avaimet assert-funktioista.

(deftest paattele-tietolaji-test
  (let [tl501-kohteet (lataa-kohteet-tietolajille "tl501")
        tl503-kohteet (lataa-kohteet-tietolajille "tl503")
        tl505-kohteet (lataa-kohteet-tietolajille "tl505")]
    (assertoi-kohteet :tl501 :varusteet :kaiteet tl501-kohteet)
    (assertoi-kohteet :tl503 :varusteet :tienvarsikalusteet tl503-kohteet)
    (assertoi-kohteet :tl505 :varusteet :tienvarsikalusteet tl505-kohteet)))

(deftest paattele-kohteet-latauspalvelu-materiaalista-test
  (->>
    (lataa-latauspalvelun-kohteet "tienvarsikalusteet")
    (assertoi-kohteen-tietolaji-on-kohteen-oid-ssa :varusteet :tienvarsikalusteet))
  (->>
    (lataa-latauspalvelun-kohteet "kaiteet")
    (assertoi-kohteen-tietolaji-on-kohteen-oid-ssa :varusteet :kaiteet)))