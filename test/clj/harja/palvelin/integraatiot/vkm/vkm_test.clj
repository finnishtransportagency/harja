(ns harja.palvelin.integraatiot.vkm.vkm-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.vkm.vkm-komponentti :as vkm]
            [harja.pvm :as pvm]
            [com.stuartsierra.component :as component]
            [org.httpkit.fake :refer [with-fake-http]]
            [clojure.string :as str]))

(def kayttaja "jvh")
(def +testi-vkm+ "https://avoinapi.testivaylapilvi.fi/viitekehysmuunnin/")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :vkm (component/using
           (vkm/->VKM +testi-vkm+)
           [:db :integraatioloki])))

(use-fixtures :once (compose-fixtures tietokanta-fixture
                      jarjestelma-fixture))

(deftest vkm-parametrit
  (let [parametrit (vkm/yllapitokohde->vkm-parametrit
                     [{:yha-id 123456
                       :tierekisteriosoitevali {:tienumero 4 :aosa 1 :aet 0 :losa 3 :let 1000 :ajorata 1}}]
                     (pvm/luo-pvm 2017 0 1)
                     (pvm/luo-pvm 2017 5 25))
        odotetut [{:tunniste "kohde-123456-alku"
                   :tie 4
                   :osa 1
                   :etaisyys 0
                   :tilannepvm "01.01.2017"
                   :kohdepvm "25.06.2017"
                   :ajorata 1
                   :palautusarvot "2"}
                  {:tunniste "kohde-123456-loppu"
                   :tie 4
                   :osa 3
                   :etaisyys 1000
                   :tilannepvm "01.01.2017"
                   :kohdepvm "25.06.2017"
                   :ajorata 1
                   :palautusarvot "2"}]]
    (is (= odotetut parametrit) "VKM:n Parametrit muodostettu oikein")))

;; Tämä testi vastaa ainakin yhtä löydettyä oikean elämän esimerkkiä.
;; VKM:stä palautuu useampi ajorata, joissa on sama osa ja etäisyys.
(deftest yhdista-vkm-ajoradat-samoilla-tiedoilla
  (let [vkm-osoitteet [{"tunniste" "kohde-666-alku"
                        "tie" 4
                        "ajorata" 0
                        "osa" 1
                        "etaisyys" 0
                        "vaylan_luonne" 12
                        "hallinnollinen_luokka" 1
                        "vertikaalisuhde" 0}
                       {"tunniste" "kohde-666-alku"
                        "tie" 4
                        "ajorata" 1
                        "osa" 1
                        "etaisyys" 0
                        "vaylan_luonne" 12
                        "hallinnollinen_luokka" 1
                        "vertikaalisuhde" 0}
                       {"tunniste" "kohde-666-loppu"
                        "tie" 4
                        "ajorata" 1
                        "osa" 0
                        "etaisyys" 0
                        "vaylan_luonne" 12
                        "hallinnollinen_luokka" 1
                        "vertikaalisuhde" 0}
                       {"tunniste" "kohde-667-alku"
                        "tie" 4
                        "ajorata" 1
                        "osa" 4
                        "etaisyys" 0
                        "vaylan_luonne" 12
                        "hallinnollinen_luokka" 1
                        "vertikaalisuhde" 0}]
        odotettu [{"tunniste" "kohde-666-alku"
                   "tie" 4
                   "ajorata" 0
                   "osa" 1
                   "etaisyys" 0
                   "vaylan_luonne" 12
                   "hallinnollinen_luokka" 1
                   "vertikaalisuhde" 0}
                  {"tunniste" "kohde-666-loppu"
                   "tie" 4
                   "ajorata" 1
                   "osa" 0
                   "etaisyys" 0
                   "vaylan_luonne" 12
                   "hallinnollinen_luokka" 1
                   "vertikaalisuhde" 0}
                  {"tunniste" "kohde-667-alku"
                   "tie" 4
                   "ajorata" 1
                   "osa" 4
                   "etaisyys" 0
                   "vaylan_luonne" 12
                   "hallinnollinen_luokka" 1
                   "vertikaalisuhde" 0}]]
    (is (= odotettu (vkm/yhdista-vkm-ajoradat vkm-osoitteet))
      "VKM-osoittet pitäisi yhdistyä, jos niillä on sama tunniste.")))

;; Tällaista tapausta ei luultavasti tapahtu oikeassa elämässä, mutta jos tapahtuu niin
;; Oletetaan, että halutaan yhdistää ajoradat niin, että alkuosista otetaan pienimät
;; etäisyydet ja osat. Loppuosista puolistaan suurimmat.
(deftest yhdista-eri-osat
  ;; Alkuosat
  (let [vkm-osoitteet [{"tunniste" "kohde-666-alku"
                        "tie" 4
                        "ajorata" 0
                        "osa" 1
                        "etaisyys" 0
                        "vaylan_luonne" 12
                        "hallinnollinen_luokka" 1
                        "vertikaalisuhde" 0}
                       {"tunniste" "kohde-666-alku"
                        "tie" 4
                        "ajorata" 1
                        "osa" 2
                        "etaisyys" 100
                        "vaylan_luonne" 12
                        "hallinnollinen_luokka" 1
                        "vertikaalisuhde" 0}]
        odotetut [(first vkm-osoitteet)]]
    (is (= odotetut (vkm/yhdista-vkm-ajoradat vkm-osoitteet))))

  ;; Loppuosat
  (let [vkm-osoitteet [{"tunniste" "kohde-666-loppu"
                        "tie" 4
                        "ajorata" 0
                        "osa" 2
                        "etaisyys" 200
                        "vaylan_luonne" 12
                        "hallinnollinen_luokka" 1
                        "vertikaalisuhde" 0}
                       {"tunniste" "kohde-666-loppu"
                        "tie" 4
                        "ajorata" 1
                        "osa" 3
                        "etaisyys" 1000
                        "vaylan_luonne" 12
                        "hallinnollinen_luokka" 1
                        "vertikaalisuhde" 0}]
        odotetut [(second vkm-osoitteet)]]
    (is (= odotetut (vkm/yhdista-vkm-ajoradat vkm-osoitteet)))))

(deftest tieosoitteet-vkm-vastauksesta
  (let [tieosoitteet (vkm/yllapitokohde->vkm-parametrit
                       [{:yha-id 666
                         :tierekisteriosoitevali {:tienumero 4 :aosa 1 :aet 0 :losa 3 :let 1000 :ajorata 1 :joku "muu arvo"}}]
                       (pvm/->pvm "01.01.2017")
                       (pvm/->pvm "25.06.2017"))
        onnistunut-vkm-vastaus "{\"type\": \"FeatureCollection\",
                                           \"features\": [
                                               {
                                                   \"type\": \"Feature\",
                                                   \"geometry\": {
                                                       \"type\": \"Point\",
                                                       \"coordinates\": []
                                                   },
                                                   \"properties\": {
                                                       \"tunniste\": \"kohde-666-alku\",
                                                       \"tie\": 4,
                                                       \"ajorata\": 1,
                                                       \"osa\": 1,
                                                       \"etaisyys\": 0,
                                                       \"vaylan_luonne\": 12,
                                                       \"hallinnollinen_luokka\": 1,
                                                       \"vertikaalisuhde\": 0
                                                   }
                                               },
                                               {
                                                   \"type\": \"Feature\",
                                                   \"geometry\": {
                                                       \"type\": \"Point\",
                                                       \"coordinates\": []
                                                   },
                                                   \"properties\": {
                                                       \"tunniste\": \"kohde-666-loppu\",
                                                       \"tie\": 4,
                                                       \"ajorata\": 1,
                                                       \"osa\": 3,
                                                       \"etaisyys\": 1000,
                                                       \"vaylan_luonne\": 12,
                                                       \"hallinnollinen_luokka\": 1,
                                                       \"vertikaalisuhde\": 0
                                                   }
                                               }
                                           ]}"
        vkm-virhevastaus "{\"type\": \"FeatureCollection\",
                                           \"features\": [
                                               {
                                                   \"type\": \"Feature\",
                                                   \"geometry\": {
                                                       \"type\": \"Point\",
                                                       \"coordinates\": []
                                                   },
                                                   \"properties\": {
                                                       \"tunniste\": \"kohde-666-alku\",
                                                       \"virheet\": \"Tilannepäivämäärän mukaisia tieosoitetietoja (piste/alkupiste) ei löytynyt\"
                                                   }
                                               },
                                               {
                                                   \"type\": \"Feature\",
                                                   \"geometry\": {
                                                       \"type\": \"Point\",
                                                       \"coordinates\": []
                                                   },
                                                   \"properties\": {
                                                       \"tunniste\": \"kohde-666-loppu\",
                                                       \"tie\": 4,
                                                       \"ajorata\": 1,
                                                       \"osa\": 3,
                                                       \"etaisyys\": 1000,
                                                       \"vaylan_luonne\": 12,
                                                       \"hallinnollinen_luokka\": 1,
                                                       \"vertikaalisuhde\": 0
                                                   }
                                               }
                                           ]}"
        odotetut [{:tie 4 :aosa 1 :aet 0 :losa 3 :let 1000 :yha-id 666}]
        odotetut-virhe (assoc-in odotetut [0 :virheet]
                         {:alku "Tilannepäivämäärän mukaisia tieosoitetietoja (piste/alkupiste) ei löytynyt"
                          :loppu nil})]
    (is (= odotetut (vkm/osoitteet-vkm-vastauksesta tieosoitteet onnistunut-vkm-vastaus))
      "Alkuosa ja loppuetäisyys on päivitetty oikein VKM:n vastauksesta")
    (is (= odotetut-virhe (vkm/osoitteet-vkm-vastauksesta tieosoitteet vkm-virhevastaus))
      "Jos vastauksessa on virheitä, osoitteisiin ei ole koskettu")))

(deftest muunna-osoitteet-paivan-verkolta-toiselle
  (with-fake-http
    [(str +testi-vkm+ "muunna") (.replace (slurp "test/resurssit/vkm/vkm-vastaus.json") "[KOHDEID]" "666")]
    (let [tieosoitteet (vkm/yllapitokohde->vkm-parametrit
                         [{:yha-id 666
                           :tierekisteriosoitevali {:tienumero 4 :aosa 1 :aet 0 :losa 3 :let 1000 :ajorata 1 :joku "muu arvo"}}]
                         (pvm/->pvm "01.01.2017")
                         (pvm/->pvm "25.06.2017"))
          muunnetut (vkm/muunna-tieosoitteet-verkolta-toiselle
                      (:vkm jarjestelma)
                      tieosoitteet)
          odotetut [{:tie 20, :aosa 1, :aet 1, :losa 3, :let 1000, :yha-id 666}]]
      (is (= odotetut muunnetut) "VKM-muunnos tehtiin odotusten mukaisesti"))))

(deftest muunna-osoitteet-paivan-verkolta-toiselle-alikohteilla
  (with-fake-http
    [(str +testi-vkm+ "muunna")
     (-> (slurp "test/resurssit/vkm/vkm-vastaus-alikohteiden-kanssa.json")
       (.replace "[KOHDEID]" "666")
       (.replace "[ALIKOHDEID1]" "1234")
       (.replace "[ALIKOHDEID2]" "2345"))]
    (let [tieosoitteet (vkm/yllapitokohde->vkm-parametrit
                         [{:yha-id 666
                           :tierekisteriosoitevali {:tienumero 4 :aosa 1 :aet 0 :losa 3 :let 1000 :ajorata 1 :joku "muu arvo"}
                           :alikohteet [{:yha-id 1234
                                         :tierekisteriosoitevali {:tienumero 4 :aosa 1 :aet 0 :losa 2 :let 500 :ajorata 1}}
                                        {:yha-id 2345
                                         :tierekisteriosoitevali {:tienumero 4 :aosa 2 :aet 500 :losa 3 :let 1000 :ajorata 1}}]}]
                         (pvm/->pvm "01.01.2017")
                         (pvm/->pvm "25.06.2017"))
          muunnetut (vkm/muunna-tieosoitteet-verkolta-toiselle
                      (:vkm jarjestelma)
                      tieosoitteet)
          odotetut [{:tie 20, :aosa 1, :aet 0, :losa 3, :let 1000, :yha-id 666}
                    {:tie 20, :aosa 1, :aet 0, :losa 2, :let 500, :yha-id 1234}
                    {:tie 20, :aosa 2, :aet 500, :losa 3, :let 1000, :yha-id 2345}]]
      (is (= odotetut muunnetut) "VKM-muunnos tehtiin odotusten mukaisesti"))))
