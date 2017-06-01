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

(deftest tarkista-ajoradan-pituuden-validointi
  (let [pituudet [{:osa 101, :ajorata 1, :pituus 7809}
                  {:osa 101, :ajorata 2, :pituus 7809}
                  {:osa 102, :ajorata 1, :pituus 4353}
                  {:osa 102, :ajorata 2, :pituus 4353}
                  {:osa 103, :ajorata 1, :pituus 4770}
                  {:osa 103, :ajorata 2, :pituus 4770}]]
    (is (tierekisteri/ajoradan-pituus-sopiva-verkolla? 101 1 700 pituudet))
    (is (tierekisteri/ajoradan-pituus-sopiva-verkolla? 101 1 1 pituudet))
    (is (tierekisteri/ajoradan-pituus-sopiva-verkolla? 101 1 7809 pituudet))
    (is (tierekisteri/ajoradan-pituus-sopiva-verkolla? 101 2 7809 pituudet))
    (is (tierekisteri/ajoradan-pituus-sopiva-verkolla? 101 1 0 pituudet))
    (is (tierekisteri/ajoradan-pituus-sopiva-verkolla? 101 2 0 pituudet))
    (is (not (tierekisteri/ajoradan-pituus-sopiva-verkolla? 101 1 7810 pituudet)))
    (is (not (tierekisteri/ajoradan-pituus-sopiva-verkolla? 101 2 7810 pituudet)))
    (is (not (tierekisteri/ajoradan-pituus-sopiva-verkolla? 101 1 -1 pituudet)))
    (is (not (tierekisteri/ajoradan-pituus-sopiva-verkolla? 101 2 -1 pituudet)))))

