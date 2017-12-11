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
    (is (tierekisteri/osan-pituus-sopiva-verkolla? 101 700 pituudet))
    (is (tierekisteri/osan-pituus-sopiva-verkolla? 101 1 pituudet))
    (is (tierekisteri/osan-pituus-sopiva-verkolla? 101 7809 pituudet))
    (is (tierekisteri/osan-pituus-sopiva-verkolla? 101 7809 pituudet))
    (is (tierekisteri/osan-pituus-sopiva-verkolla? 101 0 pituudet))
    (is (tierekisteri/osan-pituus-sopiva-verkolla? 101 0 pituudet))
    (is (not (tierekisteri/osan-pituus-sopiva-verkolla? 101 7810 pituudet)))
    (is (not (tierekisteri/osan-pituus-sopiva-verkolla? 101 7810 pituudet)))
    (is (not (tierekisteri/osan-pituus-sopiva-verkolla? 101 -1 pituudet)))
    (is (not (tierekisteri/osan-pituus-sopiva-verkolla? 101 -1 pituudet)))))

(deftest tr-vali-paakohteen-sisalla
  (let [paakohde {:tr-alkuosa 2
                  :tr-alkuetaisyys 1
                  :tr-loppuosa 3
                  :tr-loppuetaisyys 1}
        alikohde {:tr-alkuosa 2
                  :tr-alkuetaisyys 1
                  :tr-loppuosa 3
                  :tr-loppuetaisyys 1}]
    (is (nil? (tierekisteri/tr-vali-paakohteen-sisalla-validaattori paakohde nil alikohde))
        "Sama tieväli on pääkohteen sisällä")
    (is (nil? (tierekisteri/tr-vali-paakohteen-sisalla-validaattori
                (assoc paakohde :tr-loppuetaisyys 10)
                nil
                (assoc alikohde :tr-alkuetaisyys 10 :tr-loppuetaisyys 2)))
        "Lyhempi osuus on pääkohteen sisällä")

    (is (= "Ei pääkohteen sisällä" (tierekisteri/tr-vali-paakohteen-sisalla-validaattori
                                     paakohde
                                     nil
                                     (assoc alikohde :tr-alkuosa 1)))
        "Pienempi alkuosa ei ole kohteen sisällä")
    (is (= "Ei pääkohteen sisällä" (tierekisteri/tr-vali-paakohteen-sisalla-validaattori
                                     paakohde
                                     nil
                                     (assoc alikohde :tr-alkuetaisyys 0)))
        "Lyhyempi alkuetäisyys ei ole kohteen sisällä")

    (is (= "Ei pääkohteen sisällä" (tierekisteri/tr-vali-paakohteen-sisalla-validaattori
                                     paakohde
                                     nil
                                     (assoc alikohde :tr-loppuosa 4)))
        "Suurempi loppuosa ei ole kohteen sisällä")
    (is (= "Ei pääkohteen sisällä" (tierekisteri/tr-vali-paakohteen-sisalla-validaattori
                                     paakohde
                                     nil
                                     (assoc alikohde :tr-loppuetaisyys 10)))
        "Pidempi loppuetäisyys ei ole kohteen sisällä")


    (is (= "Ei pääkohteen sisällä" (tierekisteri/tr-vali-paakohteen-sisalla-validaattori
                                     {:tr-alkuosa 1
                                      :tr-alkuetaisyys 1
                                      :tr-loppuosa 1
                                      :tr-loppuetaisyys 1}
                                     nil
                                     {:tr-alkuosa 1
                                      :tr-alkuetaisyys 112
                                      :tr-loppuosa 1
                                      :tr-loppuetaisyys 100}))
        "Pääkohteen loppuetäisyyttä suurempi osan alkuetäisyyttä ei katsota sisältyväksi")


    (is (nil? (tierekisteri/tr-vali-paakohteen-sisalla-validaattori
                {:tr-alkuosa 3
                 :tr-alkuetaisyys 4815
                 :tr-loppuosa 5
                 :tr-loppuetaisyys 4520}
                nil
                {:tr-alkuosa 3
                 :tr-alkuetaisyys 4815
                 :tr-loppuosa 5
                 :tr-loppuetaisyys 4520}))
        "Eri osilla olevat etäisyydet ovat ok")

    (is (nil? (tierekisteri/tr-vali-paakohteen-sisalla-validaattori
                {:tr-alkuosa 1
                 :tr-alkuetaisyys 15
                 :tr-loppuosa 3
                 :tr-loppuetaisyys 0}
                nil
                {:tr-alkuosa 2
                 :tr-alkuetaisyys 1
                 :tr-loppuosa 3
                 :tr-loppuetaisyys 0}))
        "Alikohteen alkuosa eri, alkuetäisyys pidempi, silti sisällä")

    (is (nil? (tierekisteri/tr-vali-paakohteen-sisalla-validaattori
                {:tr-alkuosa 1
                 :tr-alkuetaisyys 10
                 :tr-loppuosa 3
                 :tr-loppuetaisyys 5}
                nil
                {:tr-alkuosa 2
                 :tr-alkuetaisyys 15
                 :tr-loppuosa 3
                 :tr-loppuetaisyys 2})))

    (is (nil? (tierekisteri/tr-vali-paakohteen-sisalla-validaattori
                {:tr-alkuosa 1
                 :tr-alkuetaisyys 10
                 :tr-loppuosa 3
                 :tr-loppuetaisyys 5}
                nil
                {:tr-alkuosa 2
                 :tr-alkuetaisyys 15
                 :tr-loppuosa 2
                 :tr-loppuetaisyys 30})))

    (is (nil? (tierekisteri/tr-vali-paakohteen-sisalla-validaattori
                {:tr-alkuosa 1
                 :tr-alkuetaisyys 10
                 :tr-loppuosa 3
                 :tr-loppuetaisyys 30}
                nil
                {:tr-alkuosa 1
                 :tr-alkuetaisyys 10
                 :tr-loppuosa 3
                 :tr-loppuetaisyys 30})))

    (is (= (tierekisteri/tr-vali-paakohteen-sisalla-validaattori
             {:tr-alkuosa 1
              :tr-alkuetaisyys 10
              :tr-loppuosa 3
              :tr-loppuetaisyys 5}
             nil
             {:tr-alkuosa 1
              :tr-alkuetaisyys 5
              :tr-loppuosa 3
              :tr-loppuetaisyys 2})
           "Ei pääkohteen sisällä"))

    (is (nil? (tierekisteri/tr-vali-paakohteen-sisalla-validaattori
                {:tr-alkuosa 19
                 :tr-alkuetaisyys 5
                 :tr-loppuosa 21
                 :tr-loppuetaisyys 15}
                nil
                {:tr-alkuosa 19
                 :tr-alkuetaisyys 20
                 :tr-loppuosa 21
                 :tr-loppuetaisyys 15})))

    (is (nil? (tierekisteri/tr-vali-paakohteen-sisalla-validaattori
                {:tr-alkuosa 19
                 :tr-alkuetaisyys 5
                 :tr-loppuosa 21
                 :tr-loppuetaisyys 15}
                nil
                {:tr-alkuosa 21
                 :tr-alkuetaisyys 15
                 :tr-loppuosa 19
                 :tr-loppuetaisyys 5})))

    (is (nil? (tierekisteri/tr-vali-paakohteen-sisalla-validaattori
                {:tr-alkuosa 21
                 :tr-alkuetaisyys 15
                 :tr-loppuosa 19
                 :tr-loppuetaisyys 5}
                nil
                {:tr-alkuosa 21
                 :tr-alkuetaisyys 15
                 :tr-loppuosa 19
                 :tr-loppuetaisyys 5})))

    (is (= (tierekisteri/tr-vali-paakohteen-sisalla-validaattori
             {:tr-alkuosa 1
              :tr-alkuetaisyys 10
              :tr-loppuosa 3
              :tr-loppuetaisyys 5}
             nil
             {:tr-alkuosa 1
              :tr-alkuetaisyys 5
              :tr-loppuosa 3
              :tr-loppuetaisyys 10})
           "Ei pääkohteen sisällä"))

    (is (nil? (tierekisteri/tr-vali-paakohteen-sisalla-validaattori
                {:tr-alkuosa 3
                 :tr-alkuetaisyys 4815
                 :tr-loppuosa 5
                 :tr-loppuetaisyys 4520}
                nil
                {:tr-alkuosa 5
                 :tr-alkuetaisyys 4520
                 :tr-loppuosa 3
                 :tr-loppuetaisyys 4815}))
        "Sama väli eri suuntiin on ok")))

(defn tr-vali-leikkaa-tr-valin? []

  ;; Alikohteen osuminen pääkohteen alkupuolelle

  (is (false? (tierekisteri/tr-vali-leikkaa-tr-valin?
                {:tr-alkuosa 3
                 :tr-alkuetaisyys 100
                 :tr-loppuosa 5
                 :tr-loppuetaisyys 200}
                {:tr-alkuosa 3
                 :tr-alkuetaisyys 50
                 :tr-loppuosa 3
                 :tr-loppuetaisyys 99})))

  (is (true? (tierekisteri/tr-vali-leikkaa-tr-valin?
               {:tr-alkuosa 3
                :tr-alkuetaisyys 100
                :tr-loppuosa 5
                :tr-loppuetaisyys 200}
               {:tr-alkuosa 3
                :tr-alkuetaisyys 50
                :tr-loppuosa 3
                :tr-loppuetaisyys 100})))

  (is (false? (tierekisteri/tr-vali-leikkaa-tr-valin?
               {:tr-alkuosa 3
                :tr-alkuetaisyys 100
                :tr-loppuosa 5
                :tr-loppuetaisyys 200}
               {:tr-alkuosa 1
                :tr-alkuetaisyys 50
                :tr-loppuosa 2
                :tr-loppuetaisyys 300})))

  ;; Alikohde pääkohteen sisällä

  (is (true? (tierekisteri/tr-vali-leikkaa-tr-valin?
               {:tr-alkuosa 3
                :tr-alkuetaisyys 100
                :tr-loppuosa 5
                :tr-loppuetaisyys 200}
               {:tr-alkuosa 4
                :tr-alkuetaisyys 50
                :tr-loppuosa 5
                :tr-loppuetaisyys 300})))

  (is (true? (tierekisteri/tr-vali-leikkaa-tr-valin?
               {:tr-alkuosa 3
                :tr-alkuetaisyys 100
                :tr-loppuosa 5
                :tr-loppuetaisyys 200}
               {:tr-alkuosa 4
                :tr-alkuetaisyys 50
                :tr-loppuosa 2
                :tr-loppuetaisyys 300})))

  ;; Alikohteen osuminen pääkohteen loppupuolelle

  (is (true? (tierekisteri/tr-vali-leikkaa-tr-valin?
               {:tr-alkuosa 3
                :tr-alkuetaisyys 100
                :tr-loppuosa 5
                :tr-loppuetaisyys 200}
               {:tr-alkuosa 5
                :tr-alkuetaisyys 50
                :tr-loppuosa 6
                :tr-loppuetaisyys 300})))

  (is (true? (tierekisteri/tr-vali-leikkaa-tr-valin?
               {:tr-alkuosa 3
                :tr-alkuetaisyys 100
                :tr-loppuosa 5
                :tr-loppuetaisyys 200}
               {:tr-alkuosa 5
                :tr-alkuetaisyys 200
                :tr-loppuosa 6
                :tr-loppuetaisyys 300})))

  (is (false? (tierekisteri/tr-vali-leikkaa-tr-valin?
               {:tr-alkuosa 3
                :tr-alkuetaisyys 100
                :tr-loppuosa 5
                :tr-loppuetaisyys 200}
               {:tr-alkuosa 5
                :tr-alkuetaisyys 300
                :tr-loppuosa 6
                :tr-loppuetaisyys 300})))

  (is (false? (tierekisteri/tr-vali-leikkaa-tr-valin?
                {:tr-alkuosa 3
                 :tr-alkuetaisyys 100
                 :tr-loppuosa 5
                 :tr-loppuetaisyys 200}
                {:tr-alkuosa 5
                 :tr-alkuetaisyys 300
                 :tr-loppuosa 6
                 :tr-loppuetaisyys 600}))))

