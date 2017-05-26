(ns harja.palvelin.integraatiot.vkm.vkm-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.vkm.vkm-komponentti :as vkm]
            [harja.pvm :as pvm]))

(deftest vkm-parametrit
  (let [parametrit (vkm/vkm-parametrit [{:tie 4 :aosa 1 :aet 0 :losa 3 :let 1000 :id "666" :ajorata 1}]
                                       (pvm/luo-pvm 2017 1 1)
                                       (pvm/luo-pvm 2017 5 25))
        odotetut {:in "tieosoite"
                  :out "tieosoite"
                  :callback "jsonp"
                  :tilannepvm "01.02.2017"
                  :kohdepvm "25.06.2017"
                  :json "%7B%22tieosoitteet%22%3A%5B%7B%22tunniste%22%3A%22666-alku%22%2C%22tie%22%3A4%2C%22osa%22%3A1%2C%22ajorata%22%3A1%2C%22etaisyys%22%3A0%7D%2C%7B%22tunniste%22%3A%22666-loppu%22%2C%22tie%22%3A4%2C%22osa%22%3A3%2C%22ajorata%22%3A1%2C%22etaisyys%22%3A1000%7D%5D%7D"}]
    (is (= odotetut parametrit) "VKM:n Parametrit muodostettu oikein")))

(deftest pura-tieosoitteet
  (let [puretut (vkm/pura-tieosoitteet [{:tie 4 :aosa 1 :aet 0 :losa 3 :let 1000 :id "666" :ajorata 1}])
        odotetut [{:tunniste "666-alku", :tie 4, :osa 1, :ajorata 1, :etaisyys 0}
                  {:tunniste "666-loppu", :tie 4, :osa 3, :ajorata 1, :etaisyys 1000}]]
    (is (= odotetut puretut) "Tieosoitteet on purettu oikein VKM:ää varten")))

(deftest tieosoitteet-vkm-vastauksesta
  (let [tieosoitteet [{:tie 4 :aosa 1 :aet 0 :losa 3 :let 1000 :id "666" :ajorata 1}]
        onnistunut-vkm-vastaus {"tieosoitteet" [{"ajorata" 1
                                                 "palautusarvo" 1
                                                 "osa" 2
                                                 "etaisyys" 0
                                                 "tie" 4
                                                 "tunniste" "666-alku"}
                                                {"ajorata" 1
                                                 "palautusarvo" 1
                                                 "osa" 3
                                                 "etaisyys" 800
                                                 "tie" 4
                                                 "tunniste" "666-loppu"}]}
        vkm-virhevastaus {"tieosoitteet" [{"ajorata" 1
                                           "palautusarvo" 0
                                           "osa" 2
                                           "etaisyys" 0
                                           "tie" 4
                                           "tunniste" "666-alku"}
                                          {"ajorata" 1
                                           "palautusarvo" 0
                                           "osa" 3
                                           "etaisyys" 800
                                           "tie" 4
                                           "tunniste" "666-loppu"}]}

        odotetut [{:tie 4 :aosa 2 :aet 0 :losa 3 :let 800 :id "666" :ajorata 1}]]
    (is (= odotetut (vkm/osoitteet-vkm-vastauksesta tieosoitteet onnistunut-vkm-vastaus))
        "Alkuosa ja loppuetäisyys on päivitetty oikein VKM:n vastauksesta")
    (is (= tieosoitteet (vkm/osoitteet-vkm-vastauksesta tieosoitteet vkm-virhevastaus))
        "Jos vastauksessa on virheitä, osoitteisiin ei ole koskettu")))


