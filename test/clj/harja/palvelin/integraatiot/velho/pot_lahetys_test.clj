(ns harja.palvelin.integraatiot.velho.pot-lahetys-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [clojure.set :as set]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho-integraatio]
            [harja.palvelin.integraatiot.velho.yhteiset-test :as yhteiset-test])
  (:import (org.postgis PGgeometry)))

(def kayttaja "jvh")

(def +velho-paallystystoteumat-url+ "http://localhost:1234/paallystystoteumat")
(def +velho-token-url+ "http://localhost:1234/token")

(def +velho-api-juuri+ "http://localhost:1234")

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

(deftest token-epaonnistunut-palauta-tekninen-virhen-test
  (yhteiset-test/tyhjenna-velho-tokenit-atomi)
  (let [[kohde-id pot2-id urakka-id] (hae-pot2-testi-idt)
        kohteen-tila-ennen (lue-kohteen-tila kohde-id)
        rivien-tila-ennen (lue-rivien-tila pot2-id)
        fake-feilava-token-palvelin (fn [_ {:keys [body headers]} _]
                                      "{\"error\":\"invalid_client\"}")
        kieletty-palvelu (fn [_ {:keys [body headers]} _]
                           (is false "Ei saa kutsua jos ei saannut tokenia")
                           {:status 500 :body ""})]
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
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
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
      [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
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
