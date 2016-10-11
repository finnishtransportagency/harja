(ns harja.domain.tierekisteri-test
  (:require [clojure.test :refer [deftest is]]
            [harja.testi :refer :all]
            [harja.domain.tierekisteri :as tierekisteri]))

(deftest tarkista-tierekisteriosoitteen-muunnos-tekstiksi
  ;; Koko tie muodostuu oikein
  (is (= (tierekisteri/tierekisteriosoite-tekstina
           {:tie 20 :aosa 0 :aet 1 :losa 2 :let 3})
         "Tie 20 / 0 / 1 / 2 / 3"))
  (is (= (tierekisteri/tierekisteriosoite-tekstina
           {:tie 20 :aosa 0 :aet 1 :losa 2 :let 3}
           {:teksti-tie? false})
         "20 / 0 / 1 / 2 / 3"))

  ;; Tie puuttuu
  (is (= (tierekisteri/tierekisteriosoite-tekstina
           {}
           {})
         "Ei tierekisteriosoitetta"))
  (is (= (tierekisteri/tierekisteriosoite-tekstina
           {}
           {:teksti-ei-tr-osoitetta? false})
         ""))
  (is (= (tierekisteri/tierekisteriosoite-tekstina
           {}
           {:teksti-tie? true
            :teksti-ei-tr-osoitetta? false})
         ""))

  ;; Pistemäinen tie
  (is (= (tierekisteri/tierekisteriosoite-tekstina
           {:tie 20 :aosa 0 :aet 1})
         "Tie 20 / 0 / 1"))

  ;; Pelkkä tienumero
  (is (= (tierekisteri/tierekisteriosoite-tekstina
           {:tie 20})
         "Tie 20"))

  ;; Vain osittainen tienumero
  (is (= (tierekisteri/tierekisteriosoite-tekstina
           {:tie 20 :losa 5 :let 3}
           {:teksti-tie? false})
         "20"))
  (is (= (tierekisteri/tierekisteriosoite-tekstina
           {:aosa 3 :aet 3 :losa 5 :let 3}
           {:teksti-tie? false})
         "Ei tierekisteriosoitetta"))
  (is (= (tierekisteri/tierekisteriosoite-tekstina
           {:tie 19 :aosa 3 :aet 3 :losa 5}
           {:teksti-tie? false})
         "19 / 3 / 3"))
  (is (= (tierekisteri/tierekisteriosoite-tekstina
           {:tie 20 :aosa 5})
         "Tie 20"))
  (is (= (tierekisteri/tierekisteriosoite-tekstina
           {:tie 20 :aosa 5}
           {:teksti-ei-tr-osoitetta? false})
         "Tie 20"))
  (is (= (tierekisteri/tierekisteriosoite-tekstina
           {:tie 20 :aosa 5}
           {:teksti-tie? false
            :teksti-ei-tr-osoitetta? false})
         "20")))


