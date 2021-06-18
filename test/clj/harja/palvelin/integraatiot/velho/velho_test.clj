(ns harja.palvelin.integraatiot.velho.velho-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [clojure.set :as set]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho]
            [harja.pvm :as pvm]))

(def kayttaja "jvh")

(def +velho-paallystystoteumat-url+ "http://localhost:1234/paallystystoteumat")
(def +velho-token-url+ "http://localhost:1234/token")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :velho (component/using
             (velho/->Velho {:paallystetoteuma-url +velho-paallystystoteumat-url+
                             :token-url +velho-token-url+
                             :kayttajatunnus "abc-123"
                             :salasana "blabla"})
             [:db :http-palvelin :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(deftest laheta-kohteet
  (let [{kohde-id :id pot2-id :pot2-id} (first
                                          (q-map (str "SELECT k.id,
                                                              p.id as \"pot2-id\"
                                                         FROM yllapitokohde k
                                                         JOIN paallystysilmoitus p ON p.paallystyskohde = k.id
                                                        WHERE nimi = 'Tärkeä kohde mt20'")))
        urakka-id (hae-utajarven-paallystysurakan-id)
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
        asenna-tietokannan-tila (fn []
                                  (u (str "UPDATE yllapitokohde
                                              SET velho_lahetyksen_aika = NULL,
                                                  velho_lahetyksen_tila = 'ei-lahetetty',
                                                  velho_lahetyksen_vastaus = NULL
                                              WHERE id = " kohde-id ";"))
                                  (u (str "UPDATE pot2_paallystekerros
                                              SET velho_lahetyksen_aika = NULL,
                                                  velho_rivi_lahetyksen_tila = 'ei-lahetetty',
                                                  velho_lahetyksen_vastaus = NULL
                                              WHERE jarjestysnro = 1 AND
                                                    pot2_id = " pot2-id ";"))
                                  (u (str "UPDATE pot2_alusta
                                              SET velho_lahetyksen_aika = NULL,
                                                  velho_rivi_lahetyksen_tila = 'ei-lahetetty',
                                                  velho_lahetyksen_vastaus = NULL
                                              WHERE pot2_id = " pot2-id ";")))
        analysoi-body (fn [body]
                          (let [tyyppi (if (some? (get-in body ["ominaisuudet" "sidottu-paallysrakenne"]))
                                         :paallystekerros
                                         :alusta)
                                id (get-in body ["ominaisuudet" "korjauskohdeosan-ulkoinen-tunniste"])]
                            {:tyyppi tyyppi :id id}))
        lue-tila (fn []
                   (first (q-map (str "SELECT velho_lahetyksen_aika,
                                              velho_lahetyksen_tila,
                                              velho_lahetyksen_vastaus
                                         FROM yllapitokohde
                                        WHERE id = " kohde-id ";"))))
        lue-rivien-tila (fn []
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
        etsi-rivit (fn [rivien-tila pred]
                     (->> rivien-tila
                          (filter #(pred (second %)))
                          (map first)
                          set))
        feilavat (atom feilavat-1)
        pyynnot (atom {})
        vastaanotetut (atom #{})
        vastaanotetut? (fn [body-avain] (contains? @vastaanotetut body-avain))
        fake-token-palvelin (fn [_ {:keys [body headers]} _]
                              "{\"access_token\":\"TEST_TOKEN\",\"expires_in\":3600,\"token_type\":\"Bearer\"}")
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
    (asenna-tietokannan-tila)

    (let [tila-alussa (lue-rivien-tila)
          kohteen-tila-alussa (lue-tila)]
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

      (velho/laheta-kohteet (:velho jarjestelma) urakka-id [kohde-id]))

    (is (= (+ (count alusta-idt) (count paallystekerros-idt))
           (count @pyynnot))
        (str "Kokonaan täytyy olla: " (count paallystekerros-idt) " päällystekerrosta + " (count alusta-idt) " alustaa pyyntöä"))
    (is (= onnistuneet-pyynnot-1 @vastaanotetut) "Vastaanotetut ovat ne jotka eivät feilaneet")
    (let [tila-1 (lue-rivien-tila)
          kohteen-tila-1 (lue-tila)]
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

      (velho/laheta-kohteet (:velho jarjestelma) urakka-id [kohde-id]))

    (is (= odotetut-pyynnot-2
           (-> @pyynnot keys set)) "Lähettämme vain ne jotka eivät onnistuneet ennen.")
    (is (= feilavat-1 @vastaanotetut) "Tällä kerta onnistuivat ne jotka ennen feilasivat")
    (let [tila-2 (lue-rivien-tila)
          kohteen-tila-2 (lue-tila)]
      (is (= odotetut-pyynnot-1
             (etsi-rivit tila-2 #(= (:velho_rivi_lahetyksen_tila %) "onnistunut"))) "Onnistuneet ovat nyt kaikki")
      (is (= #{}
             (etsi-rivit tila-2 #(= (:velho_rivi_lahetyksen_tila %) "epaonnistunut"))) "Ei mitään enää on epäonnistunut")
      (is (= #{}
             (etsi-rivit tila-2 #(= (:velho_rivi_lahetyksen_tila %) "ei-lahetetty"))) "Ei mitään on jäännyt lähetämättä")
      (is (= "valmis" (:velho_lahetyksen_tila kohteen-tila-2))))))
