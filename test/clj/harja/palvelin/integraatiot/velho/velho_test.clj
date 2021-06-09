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
                                          (q-map (str "SELECT k.id, p.id as \"pot2-id\"
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
        _ (println "petar svi ideovi " (pr-str paallystekerros-idt))
        odotetut-pyynnot-1 (set (set/union (map (fn [id] {:tyyppi :paallystekerros :id id}) paallystekerros-idt)
                                           (map (fn [id] {:tyyppi :alusta :id id}) alusta-idt)))
        odotetut-pyynnot-2 #{{:tyyppi :alusta, :id feilava-alusta-id} {:tyyppi :paallystekerros, :id feilava-paallystekerros-id}}
        onnistuneet-pyynnot-1 (set/difference odotetut-pyynnot-1 odotetut-pyynnot-2)
        asenna-tietokannan-tila (fn []
                                  (u (str "UPDATE yllapitokohde
                                              SET velho_lahetyksen_aika = NULL,
                                                  velho_lahetyksen_tila = 'ei-lahetetty',
                                                  velho_lahetyksen_vastaus = NULL
                                              WHERE id = " pot2-id ";"))
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
                   (first (q-map (str "SELECT * FROM yllapitokohde WHERE id = " pot2-id ";"))))
        lue-rivien-tila (fn [flagit]
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
                                                         (if (= :ilman-aikaa flagit)
                                                           (dissoc rivi :velho_lahetyksen_aika)
                                                           rivi)}))
                                                 (into {}))]
                            rivit-mappi))
        feilavat (atom #{{:tyyppi :paallystekerros :id feilava-paallystekerros-id}
                         {:tyyppi :alusta :id feilava-alusta-id}})
        pyynnot (atom {})
        vastaanotetut (atom #{})
        vastaanotetut? (fn [body-avain] (contains? @vastaanotetut body-avain))
        fake-token-palvelin (fn [_ {:keys [body headers]} _]
                              "{\"access_token\":\"TEST_TOKEN\",\"expires_in\":3600,\"token_type\":\"Bearer\"}")
        fake-palvelin (fn [_ {:keys [body headers]} _]
                        (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
                        (let [body (json/read-str body)
                              body-avain (analysoi-body body)]
                          (println "petar body je a " body-avain)
                          (swap! pyynnot merge {body-avain {:headers headers :body body}})
                          (is (not (vastaanotetut? body-avain)) (str "Ei saa lähettää saman sisällön kaksi kertaa: " body-avain))
                          (if (contains? @feilavat body-avain)
                            {:status 500 :body (str "{\"type\": \"Epäonnistunut lähetys " body-avain "\"}")}
                            (do
                              (swap! vastaanotetut conj body-avain)
                              {:status 200 :body "ok"}))))]
    (asenna-tietokannan-tila)

    (is (= {{:id 1, :tyyppi :alusta} {:id 1, :tyyppi :alusta,
                                      :velho_rivi_lahetyksen_tila "ei-lahetetty",
                                      :velho_lahetyksen_vastaus nil},
            {:id 12, :tyyppi :paallystekerros} {:id 12, :tyyppi :paallystekerros,
                                                :velho_rivi_lahetyksen_tila "ei-lahetetty",
                                                :velho_lahetyksen_vastaus nil},
            {:id 2, :tyyppi :alusta} {:id 2, :tyyppi :alusta,
                                      :velho_rivi_lahetyksen_tila "ei-lahetetty",
                                      :velho_lahetyksen_vastaus nil},
            {:id 11, :tyyppi :paallystekerros} {:id 11, :tyyppi :paallystekerros,
                                                :velho_rivi_lahetyksen_tila "ei-lahetetty",
                                                :velho_lahetyksen_vastaus nil}}
           (lue-rivien-tila :ilman-aikaa)) "Lähetysten tila alussa on oikea")

    (with-fake-http
      [{:url +velho-token-url+ :method :post} fake-token-palvelin
       {:url +velho-paallystystoteumat-url+ :method :post} fake-palvelin]

      (velho/laheta-kohteet (:velho jarjestelma) urakka-id [kohde-id]))


    (println "petar primljeno " (pr-str @vastaanotetut))
    (println "petar poslato " (pr-str @pyynnot))

    (is (= (+ (count alusta-idt) (count paallystekerros-idt))
           (count @pyynnot))
        (str "Kokonaan täytyy olla: " (count paallystekerros-idt) " päällystekerrosta + " (count alusta-idt) " alustaa pyyntöä"))
    (is (= onnistuneet-pyynnot-1 @vastaanotetut) "Vastaanotetut ovat ne jotka eivät feilaneet")
    ()


    ))
