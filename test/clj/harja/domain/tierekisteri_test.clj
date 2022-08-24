(ns harja.domain.tierekisteri-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.domain.tierekisteri :as tierekisteri]))

(deftest laske-tien-pituus
  (is (thrown-with-msg? AssertionError #"osien-pituudet oltava map tai nil"
                        (tierekisteri/laske-tien-pituus 5 nil)))
  (is (= 9990 (tierekisteri/laske-tien-pituus nil {:tr-alkuosa 1 :tr-alkuetaisyys 10 :tr-loppuosa 1 :tr-loppuetaisyys 10000}))
      "Kun osien tiedot ei ole annettu, pituus voi olla mitä vaan jos on sama osa")
  (is (= 9990 (tierekisteri/laske-tien-pituus nil {:tr-alkuosa 1 :tr-alkuetaisyys 10000 :tr-loppuosa 1 :tr-loppuetaisyys 10}))
      "Järjestys voi olla mitä vaan")
  (is (nil? (tierekisteri/laske-tien-pituus nil {:tr-alkuosa 1 :tr-alkuetaisyys 10 :tr-loppuosa 2 :tr-loppuetaisyys 10000}))
      "Jos ei ole osien-pituuksia, alku- ja loppu-osa täytyy olla sama")
  (is (= 90 (tierekisteri/laske-tien-pituus {1 500} {:tr-alkuosa 1 :tr-alkuetaisyys 10 :tr-loppuosa 1 :tr-loppuetaisyys 100}))
      "Laske pituus hyvin kun on yhden osan sisällä")
  (is (nil? (tierekisteri/laske-tien-pituus {1 500} {:tr-alkuosa 1 :tr-alkuetaisyys 10 :tr-loppuosa 1 :tr-loppuetaisyys 10000}))
      "täytyy olla osien sisällä")
  (is (nil? (tierekisteri/laske-tien-pituus {1 500} {:tr-alkuosa 1 :tr-alkuetaisyys 10 :tr-loppuosa 2 :tr-loppuetaisyys 100}))
      "täytyy olla olemassa olevien osien sisällä")
  (is (= 50 (tierekisteri/laske-tien-pituus {1 500 3 100} {:tr-alkuosa 1 :tr-alkuetaisyys 470 :tr-loppuosa 3 :tr-loppuetaisyys 20}))
      "Hyvin laske kun on eri osilla")
  (is (= 1050 (tierekisteri/laske-tien-pituus {1 500 2 1000 3 100 5 2000} {:tr-alkuosa 1 :tr-alkuetaisyys 470 :tr-loppuosa 3 :tr-loppuetaisyys 20}))
      "Hyvin laske kun on eri osilla ja on jotain välillä"))

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

(deftest tr-vali-leikkaa-tr-valin?
  ;; Alikohde ennen pääkohteen alkua
  (is (false? (tierekisteri/tr-vali-leikkaa-tr-valin?
                {:tr-alkuosa 3
                 :tr-alkuetaisyys 100
                 :tr-loppuosa 5
                 :tr-loppuetaisyys 200}
                {:tr-alkuosa 3
                 :tr-alkuetaisyys 50
                 :tr-loppuosa 3
                 :tr-loppuetaisyys 99})))
  (is (false? (tierekisteri/tr-vali-leikkaa-tr-valin?
                {:tr-alkuosa 3
                 :tr-alkuetaisyys 100
                 :tr-loppuosa 5
                 :tr-loppuetaisyys 200}
                {:tr-alkuosa 1
                 :tr-alkuetaisyys 50
                 :tr-loppuosa 2
                 :tr-loppuetaisyys 300})))

  ;; Alikohde päättyy pääkohteen alkuun
  (is (false? (tierekisteri/tr-vali-leikkaa-tr-valin?
                {:tr-alkuosa 3
                 :tr-alkuetaisyys 100
                 :tr-loppuosa 5
                 :tr-loppuetaisyys 200}
                {:id 2
                 :tr-alkuosa 3
                 :tr-alkuetaisyys 50
                 :tr-loppuosa 3
                 :tr-loppuetaisyys 100})))

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

  ;; Alikohde pääkohteen sisällä (osoite väärinpäin)
  (is (true? (tierekisteri/tr-vali-leikkaa-tr-valin?
               {:tr-alkuosa 3
                :tr-alkuetaisyys 100
                :tr-loppuosa 5
                :tr-loppuetaisyys 200}
               {:tr-alkuosa 4
                :tr-alkuetaisyys 50
                :tr-loppuosa 2
                :tr-loppuetaisyys 300})))
  (is (true? (tierekisteri/tr-vali-leikkaa-tr-valin?
               {:tr-alkuosa 3
                :tr-alkuetaisyys 100
                :tr-loppuosa 5
                :tr-loppuetaisyys 200}
               {:tr-alkuosa 4
                :tr-alkuetaisyys 50
                :tr-loppuosa 4
                :tr-loppuetaisyys 200})))

  ;; Alikohde alkaa ennen pääkohteen loppua
  (is (true? (tierekisteri/tr-vali-leikkaa-tr-valin?
               {:tr-alkuosa 3
                :tr-alkuetaisyys 100
                :tr-loppuosa 5
                :tr-loppuetaisyys 200}
               {:tr-alkuosa 5
                :tr-alkuetaisyys 50
                :tr-loppuosa 6
                :tr-loppuetaisyys 300})))

  ;; Alikohde alkaa pääkohteen lopusta
  (is (false? (tierekisteri/tr-vali-leikkaa-tr-valin?
                {:tr-alkuosa 3
                 :tr-alkuetaisyys 100
                 :tr-loppuosa 5
                 :tr-loppuetaisyys 200}
                {:tr-alkuosa 5
                 :tr-alkuetaisyys 200
                 :tr-loppuosa 6
                 :tr-loppuetaisyys 300})))

  ;; Alikohde alkaa pääkohteen jälkeen
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
                {:tr-alkuosa 6
                 :tr-alkuetaisyys 300
                 :tr-loppuosa 6
                 :tr-loppuetaisyys 600}))))

(deftest tayta-paakohteen-alikohteet

  (is (= (tierekisteri/alikohteet-tayttamaan-kohde
           {:tr-numero 1
            :tr-alkuosa 3
            :tr-alkuetaisyys 100
            :tr-loppuosa 5
            :tr-loppuetaisyys 200}
           [{:id 1
             :tr-numero 1
             :tr-alkuosa 1
             :tr-alkuetaisyys 50
             :tr-loppuosa 3
             :tr-loppuetaisyys 50}
            {:id 2
             :tr-numero 1
             :tr-alkuosa 3
             :tr-alkuetaisyys 50
             :tr-loppuosa 4
             :tr-loppuetaisyys 200}
            {:id 3
             :tr-numero 1
             :tr-alkuosa 4
             :tr-alkuetaisyys 200
             :tr-loppuosa 5
             :tr-loppuetaisyys 300}
            {:id 4
             :tr-numero 1
             :tr-alkuosa 5
             :tr-alkuetaisyys 200
             :tr-loppuosa 10
             :tr-loppuetaisyys 0}])
         [{:id 2
           :tr-numero 1
           :tr-alkuosa 3
           :tr-alkuetaisyys 100
           :tr-loppuosa 4
           :tr-loppuetaisyys 200}
          {:id 3
           :tr-numero 1
           :tr-alkuosa 4
           :tr-alkuetaisyys 200
           :tr-loppuosa 5
           :tr-loppuetaisyys 200}]))

  (is (= (tierekisteri/alikohteet-tayttamaan-kohde
           {:tr-alkuosa 3
            :tr-alkuetaisyys 100
            :tr-loppuosa 5
            :tr-loppuetaisyys 200}
           [{:id 1
             :tr-alkuosa 4
             :tr-alkuetaisyys 50
             :tr-loppuosa 4
             :tr-loppuetaisyys 200}
            {:id 2
             :tr-alkuosa 4
             :tr-alkuetaisyys 200
             :tr-loppuosa 4
             :tr-loppuetaisyys 200}])
         [{:id 1
           :tr-alkuosa 3
           :tr-alkuetaisyys 100
           :tr-loppuosa 4
           :tr-loppuetaisyys 200}
          {:id 2
           :tr-alkuosa 4
           :tr-alkuetaisyys 200
           :tr-loppuosa 5
           :tr-loppuetaisyys 200}]))

  (is (= (tierekisteri/alikohteet-tayttamaan-kohde
           {:tr-alkuosa 3
            :tr-alkuetaisyys 100
            :tr-loppuosa 5
            :tr-loppuetaisyys 200}
           [{:id 1
             :tr-alkuosa 3
             :tr-alkuetaisyys 50
             :tr-loppuosa 3
             :tr-loppuetaisyys 200}
            {:id 2
             :tr-alkuosa 3
             :tr-alkuetaisyys 200
             :tr-loppuosa 4
             :tr-loppuetaisyys 200}
            {:id 3
             :tr-alkuosa 4
             :tr-alkuetaisyys 200
             :tr-loppuosa 5
             :tr-loppuetaisyys 300}])
         [{:id 1
           :tr-alkuosa 3
           :tr-alkuetaisyys 100
           :tr-loppuosa 3
           :tr-loppuetaisyys 200}
          {:id 2
           :tr-alkuosa 3
           :tr-alkuetaisyys 200
           :tr-loppuosa 4
           :tr-loppuetaisyys 200}
          {:id 3
           :tr-alkuosa 4
           :tr-alkuetaisyys 200
           :tr-loppuosa 5
           :tr-loppuetaisyys 200}]))

  (is (= (tierekisteri/alikohteet-tayttamaan-kohde
           {:tr-alkuosa 3
            :tr-alkuetaisyys 100
            :tr-loppuosa 5
            :tr-loppuetaisyys 200}
           [{:id 1
             :tr-alkuosa 3
             :tr-alkuetaisyys 100
             :tr-loppuosa 5
             :tr-loppuetaisyys 300}])
         [{:id 1
           :tr-alkuosa 3
           :tr-alkuetaisyys 100
           :tr-loppuosa 5
           :tr-loppuetaisyys 200}]))

  (is (= (tierekisteri/alikohteet-tayttamaan-kohde
           {:tr-numero 1
            :tr-alkuosa 3
            :tr-alkuetaisyys 100
            :tr-loppuosa 5
            :tr-loppuetaisyys 200}
           [{:id 1
             :tr-numero 1
             :tr-alkuosa 3
             :tr-alkuetaisyys 25
             :tr-loppuosa 5
             :tr-loppuetaisyys 300}])
         [{:id 1
           :tr-numero 1
           :tr-alkuosa 3
           :tr-alkuetaisyys 100
           :tr-loppuosa 5
           :tr-loppuetaisyys 200}]))

  (is (= (tierekisteri/alikohteet-tayttamaan-kohde
           {:tr-numero 1
            :tr-alkuosa 2
            :tr-alkuetaisyys 1
            :tr-loppuosa 2
            :tr-loppuetaisyys 1}
           [{:id 1
             :tr-numero 1
             :tr-alkuosa 1
             :tr-alkuetaisyys 1
             :tr-loppuosa 5
             :tr-loppuetaisyys 1}])
         [{:id 1
           :tr-numero 1
           :tr-alkuosa 2
           :tr-alkuetaisyys 1
           :tr-loppuosa 2
           :tr-loppuetaisyys 1}])
      "Pistemäinen kohde osataan korjata")

  (is (= (tierekisteri/alikohteet-tayttamaan-kohde
           {:tr-numero 1
            :tr-alkuosa 2
            :tr-alkuetaisyys 1
            :tr-loppuosa 2
            :tr-loppuetaisyys 1}
           [])
         [])
      "Tyhjä kohdelista osataan käsitellä"))

(deftest tayta-kutistuneen-paakohteen-alikohteet

  (is (= (tierekisteri/alikohteet-tayttamaan-kutistunut-paakohde
           {:tr-numero 1
            :tr-alkuosa 3
            :tr-alkuetaisyys 100
            :tr-loppuosa 5
            :tr-loppuetaisyys 200}
           [{:id 1
             :tr-numero 1
             :tr-alkuosa 1
             :tr-alkuetaisyys 50
             :tr-loppuosa 3
             :tr-loppuetaisyys 50}
            {:id 2
             :tr-numero 1
             :tr-alkuosa 3
             :tr-alkuetaisyys 50
             :tr-loppuosa 4
             :tr-loppuetaisyys 200}
            {:id 3
             :tr-numero 1
             :tr-alkuosa 4
             :tr-alkuetaisyys 200
             :tr-loppuosa 5
             :tr-loppuetaisyys 300}
            {:id 4
             :tr-numero 1
             :tr-alkuosa 5
             :tr-alkuetaisyys 200
             :tr-loppuosa 10
             :tr-loppuetaisyys 0}])
         [;; id 1 ulkopuolella -> katoaa
          {:id 2
           :tr-numero 1
           :tr-alkuosa 3
           :tr-alkuetaisyys 100
           :tr-loppuosa 4
           :tr-loppuetaisyys 200}
          {:id 3
           :tr-numero 1
           :tr-alkuosa 4
           :tr-alkuetaisyys 200
           :tr-loppuosa 5
           :tr-loppuetaisyys 200}
          ; id 4 ulkopuolella -> katoaa
          ]))

  (is (= (tierekisteri/alikohteet-tayttamaan-kutistunut-paakohde
           {:tr-alkuosa 3
            :tr-alkuetaisyys 100
            :tr-loppuosa 5
            :tr-loppuetaisyys 200}
           [{:id 1
             :tr-alkuosa 4
             :tr-alkuetaisyys 50
             :tr-loppuosa 4
             :tr-loppuetaisyys 200}
            {:id 2
             :tr-alkuosa 4
             :tr-alkuetaisyys 200
             :tr-loppuosa 4
             :tr-loppuetaisyys 200}])
         ;; Molemmat alikohteet ovat pääkohteen sisällä -> palautuvat sellaisenaan
         [{:id 1
           :tr-alkuosa 4
           :tr-alkuetaisyys 50
           :tr-loppuosa 4
           :tr-loppuetaisyys 200}
          {:id 2
           :tr-alkuosa 4
           :tr-alkuetaisyys 200
           :tr-loppuosa 4
           :tr-loppuetaisyys 200}]))

  (is (= (tierekisteri/alikohteet-tayttamaan-kutistunut-paakohde
           {:tr-alkuosa 3
            :tr-alkuetaisyys 100
            :tr-loppuosa 5
            :tr-loppuetaisyys 200}
           [{:id 1
             :tr-alkuosa 3
             :tr-alkuetaisyys 50
             :tr-loppuosa 3
             :tr-loppuetaisyys 200}
            {:id 2
             :tr-alkuosa 3
             :tr-alkuetaisyys 200
             :tr-loppuosa 4
             :tr-loppuetaisyys 200}
            {:id 3
             :tr-alkuosa 4
             :tr-alkuetaisyys 200
             :tr-loppuosa 5
             :tr-loppuetaisyys 300}])
         [{:id 1
           :tr-alkuosa 3
           :tr-alkuetaisyys 100
           :tr-loppuosa 3
           :tr-loppuetaisyys 200}
          {:id 2
           :tr-alkuosa 3
           :tr-alkuetaisyys 200
           :tr-loppuosa 4
           :tr-loppuetaisyys 200}
          {:id 3
           :tr-alkuosa 4
           :tr-alkuetaisyys 200
           :tr-loppuosa 5
           :tr-loppuetaisyys 200}]))

  (is (= (tierekisteri/alikohteet-tayttamaan-kutistunut-paakohde
           {:tr-alkuosa 3
            :tr-alkuetaisyys 100
            :tr-loppuosa 5
            :tr-loppuetaisyys 200}
           [{:id 1
             :tr-alkuosa 3
             :tr-alkuetaisyys 100
             :tr-loppuosa 5
             :tr-loppuetaisyys 300}])
         [{:id 1
           :tr-alkuosa 3
           :tr-alkuetaisyys 100
           :tr-loppuosa 5
           :tr-loppuetaisyys 200}]))

  (is (= (tierekisteri/alikohteet-tayttamaan-kutistunut-paakohde
           {:tr-numero 1
            :tr-alkuosa 3
            :tr-alkuetaisyys 100
            :tr-loppuosa 5
            :tr-loppuetaisyys 200}
           [{:id 1
             :tr-numero 1
             :tr-alkuosa 3
             :tr-alkuetaisyys 25
             :tr-loppuosa 5
             :tr-loppuetaisyys 300}])
         [{:id 1
           :tr-numero 1
           :tr-alkuosa 3
           :tr-alkuetaisyys 100
           :tr-loppuosa 5
           :tr-loppuetaisyys 200}]))

  (is (= (tierekisteri/alikohteet-tayttamaan-kutistunut-paakohde
           {:tr-numero 1
            :tr-alkuosa 2
            :tr-alkuetaisyys 1
            :tr-loppuosa 2
            :tr-loppuetaisyys 1}
           [{:id 1
             :tr-numero 1
             :tr-alkuosa 1
             :tr-alkuetaisyys 1
             :tr-loppuosa 5
             :tr-loppuetaisyys 1}])
         [{:id 1
           :tr-numero 1
           :tr-alkuosa 2
           :tr-alkuetaisyys 1
           :tr-loppuosa 2
           :tr-loppuetaisyys 1}])
      "Pistemäinen kohde osataan korjata")

  (is (= (tierekisteri/alikohteet-tayttamaan-kutistunut-paakohde
           {:tr-numero 1
            :tr-alkuosa 2
            :tr-alkuetaisyys 1
            :tr-loppuosa 2
            :tr-loppuetaisyys 1}
           [])
         [])
      "Tyhjä kohdelista osataan käsitellä"))

(deftest tieosilla-maantieteellinen-jatkumo?
  (testing "Line-käsittely toimii"
    (let [tie1-geo {:type :line
                    :points [[1 2] [2 2] [200 200] [3 4]]}
          tie2-geo {:type :line
                    :points [[2 2]]}]
      (is (tierekisteri/tieosilla-maantieteellinen-jatkumo? tie1-geo tie2-geo)))

    (let [tie1-geo {:type :line
                    :points [[1 2] [2 2] [200 200] [3 4]]}
          tie2-geo {:type :line
                    :points [[9000 2000]]}]
      (is (not (tierekisteri/tieosilla-maantieteellinen-jatkumo? tie1-geo tie2-geo))))

    (let [tie1-geo {:type :line
                    :points [[1 2] [2 2] [200 200] [3 4]]}
          tie2-geo {:type :line
                    :points [[2 2] [201 201]]}]
      (is (tierekisteri/tieosilla-maantieteellinen-jatkumo? tie1-geo tie2-geo))))

  (testing "Multi-line käsittely toimii"
    (let [tie1-geo {:type :multiline
                    :lines [{:type :line
                             :points [[1 2] [2 2] [200 200] [3 4]]}
                            {:type :line
                             :points [[9000 2000]]}]}
          tie2-geo {:type :line
                    :points [[2 2]]}]
      (is (tierekisteri/tieosilla-maantieteellinen-jatkumo? tie1-geo tie2-geo)))

    (let [tie1-geo {:type :multiline
                    :lines [{:type :line
                             :points [[1 2] [2 2] [200 200] [3 4]]}
                            {:type :line
                             :points [[9000 2000]]}]}
          tie2-geo {:type :multiline
                    :lines [{:type :line
                             :points [[3 4] [200 200]]}
                            {:type :line
                             :points [[50000 20000]]}]}]
      (is (tierekisteri/tieosilla-maantieteellinen-jatkumo? tie1-geo tie2-geo)))

    (let [tie1-geo {:type :multiline
                    :lines [{:type :line
                             :points [[1 2] [2 2] [200 200] [3 4]]}
                            {:type :line
                             :points [[9000 2000]]}]}
          tie2-geo {:type :multiline
                    :lines [{:type :line
                             :points [[50000 20000] [70000 80000]]}
                            {:type :line
                             :points [[40000 22000]]}]}]
      (is (not (tierekisteri/tieosilla-maantieteellinen-jatkumo? tie1-geo tie2-geo))))))