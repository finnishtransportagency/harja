(ns harja.palvelin.integraatiot.vkm.vkm-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.vkm.vkm-komponentti :as vkm]
            [harja.pvm :as pvm]
            [com.stuartsierra.component :as component]
            [org.httpkit.fake :refer [with-fake-http]]))

(def kayttaja "jvh")
(def +testi-vkm+ "https://localhost:666/vkm/muunnos")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :vkm (component/using
           (vkm/->VKM +testi-vkm+)
           [:db :integraatioloki])))

(use-fixtures :once (compose-fixtures tietokanta-fixture
                                      jarjestelma-fixture))

(deftest vkm-parametrit
  (let [parametrit (vkm/vkm-parametrit [{:tie 4 :aosa 1 :aet 0 :losa 3 :let 1000 :vkm-id "666" :ajorata 1}]
                                       (pvm/luo-pvm 2017 1 1)
                                       (pvm/luo-pvm 2017 5 25))
        odotetut {:in "tieosoite"
                  :out "tieosoite"
                  :callback "json"
                  :tilannepvm "01.02.2017"
                  :kohdepvm "25.06.2017"
                  :json "{\"tieosoitteet\":[{\"tunniste\":\"666-alku\",\"tie\":4,\"osa\":1,\"ajorata\":null,\"etaisyys\":0},{\"tunniste\":\"666-loppu\",\"tie\":4,\"osa\":3,\"ajorata\":null,\"etaisyys\":1000}]}"}]
    (is (= odotetut parametrit) "VKM:n Parametrit muodostettu oikein")))

(deftest pura-tieosoitteet
  (let [puretut (vkm/pura-tieosoitteet [{:tie 4 :aosa 1 :aet 0 :losa 3 :let 1000 :vkm-id "666" :ajr 1}])
        odotetut [{:tunniste "666-alku", :tie 4, :osa 1, :ajorata 1, :etaisyys 0}
                  {:tunniste "666-loppu", :tie 4, :osa 3, :ajorata 1, :etaisyys 1000}]]
    (is (= odotetut puretut) "Tieosoitteet on purettu oikein VKM:ää varten")))

(deftest tieosoitteet-vkm-vastauksesta
  (let [tieosoitteet [{:tie 4 :aosa 1 :aet 0 :losa 3 :let 1000 :ajorata 1 :joku "muu arvo" :vkm-id "666"}]
        onnistunut-vkm-vastaus "json({\"tieosoitteet\": [{\"ajorata\": 1,
                                                         \"palautusarvo\": 1 ,
                                                         \"osa\": 2,
                                                         \"etaisyys\": 0,
                                                         \"tie\": 4,
                                                         \"tunniste\": \"666-alku\"},
                                                        {\"ajorata\": 1,
                                                         \"palautusarvo\": 1,
                                                         \"osa\": 3,
                                                         \"etaisyys\": 800,
                                                         \"tie\": 4,
                                                         \"tunniste\": \"666-loppu\"}]})"
        vkm-virhevastaus "json({\"tieosoitteet\": [{\"ajorata\": 1,
                                                    \"palautusarvo\": 0,
                                                    \"osa\": 2,
                                                    \"etaisyys\": 0,
                                                    \"tie\": 4,
                                                    \"tunniste\": \"666-alku\"},
                                                   {\"ajorata\": 1,
                                                    \"palautusarvo\": 0,
                                                    \"osa\": 3,
                                                    \"etaisyys\": 800,
                                                    \"tie\": 4,
                                                    \"tunniste\": \"666-loppu\"}]})"

        odotetut [{:tie 4 :aosa 2 :aet 0 :losa 3 :let 800 :vkm-id "666" :ajorata 1 :joku "muu arvo"}]]
    (is (= odotetut (vkm/osoitteet-vkm-vastauksesta tieosoitteet onnistunut-vkm-vastaus))
        "Alkuosa ja loppuetäisyys on päivitetty oikein VKM:n vastauksesta")
    (is (= tieosoitteet (vkm/osoitteet-vkm-vastauksesta tieosoitteet vkm-virhevastaus))
        "Jos vastauksessa on virheitä, osoitteisiin ei ole koskettu")))

(deftest muunna-osoitteet-paivan-verkolta-toiselle
  (with-fake-http [+testi-vkm+ (.replace (slurp "test/resurssit/vkm/vkm-vastaus.txt") "[KOHDEID]" "666")]
    (let [tieosoitteet [{:tie 4 :aosa 1 :aet 0 :losa 3 :let 1000 :vkm-id "666" :ajorata 1}]
          muunnetut (vkm/muunna-tieosoitteet-verkolta-toiselle
                      (:vkm jarjestelma)
                      tieosoitteet
                      (pvm/luo-pvm 2017 1 1)
                      (pvm/luo-pvm 2017 5 1))
          odotetut [{:tie 20, :aosa 1, :aet 1, :losa 4, :let 100, :vkm-id "666", :ajorata 1}]]
      (is (= odotetut muunnetut) "VKM-muunnos tehtiin odotusten mukaisesti"))))



