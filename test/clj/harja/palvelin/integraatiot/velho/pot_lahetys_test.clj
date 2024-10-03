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