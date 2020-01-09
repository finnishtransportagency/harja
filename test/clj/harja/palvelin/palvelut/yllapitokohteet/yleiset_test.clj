(ns harja.palvelin.palvelut.yllapitokohteet.yleiset-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yleiset]))

(deftest vaadi-etta-kohdeosat-eivat-mene-paallekkain
  (let [db (luo-testitietokanta)]
    666
    (is (= ["Kohde: 'Testi 1' menee päällekkäin urakan: 'Muhoksen päällystysurakka' kohteen: 'Leppäjärven ramppi 2018' kohdeosan: 'Leppäjärven kohdeosa 2018' kanssa."]
           (yleiset/paallekkaiset-kohdeosat-saman-vuoden-osien-kanssa
             db
             666
             2018
             [{:nimi "Testi 1"
               :tr-numero 20
               :tr-ajorata 1
               :tr-kaista 11
               :tr-alkuosa 1
               :tr-alkuetaisyys 1
               :tr-loppuosa 1
               :tr-loppuetaisyys 100}]))
        "Samana vuonna päällekkäin menevät kohteet huomataan")

    (is (empty?
          (yleiset/paallekkaiset-kohdeosat-saman-vuoden-osien-kanssa
            db
            666
            2018
            [{:nimi "Testi 1"
              :id 10001
              :tr-numero 20
              :tr-ajorata 1
              :tr-kaista 11
              :tr-alkuosa 1
              :tr-alkuetaisyys 1
              :tr-loppuosa 1
              :tr-loppuetaisyys 100}]))
        "Kun kohde on samalla tunnisteella, ei siitä aiheudu validointivirhettä")

    (is (empty?
          (yleiset/paallekkaiset-kohdeosat-saman-vuoden-osien-kanssa
            db
            666
            2018
            [{:nimi "Testi 1"
              :tr-numero 20
              :tr-ajorata 2
              :tr-kaista 11
              :tr-alkuosa 1
              :tr-alkuetaisyys 1
              :tr-loppuosa 1
              :tr-loppuetaisyys 100}]))
        "Kun kohde on eri ajoradalla tai kaistalla, ei siitä aiheudu validointivirhettä")

    (is (empty? (yleiset/paallekkaiset-kohdeosat-saman-vuoden-osien-kanssa
                db
                666
                2018
                [{:nimi "Testi 1"
                  :tr-numero 4
                  :tr-ajorata 1
                  :tr-kaista 11
                  :tr-alkuosa 101
                  :tr-alkuetaisyys 1
                  :tr-loppuosa 101
                  :tr-loppuetaisyys 100}]))
        "Validista kohteesta ei palaudu virheitä")


    (is (= ["Kohde: 'Ei-validi' menee päällekkäin urakan: 'Muhoksen päällystysurakka' kohteen: 'Leppäjärven ramppi 2018' kohdeosan: 'Leppäjärven kohdeosa 2018' kanssa."]
           (yleiset/paallekkaiset-kohdeosat-saman-vuoden-osien-kanssa
             db
             666
             2018
             [{:nimi "Validi"
               :tr-numero 4
               :tr-ajorata 1
               :tr-kaista 11
               :tr-alkuosa 101
               :tr-alkuetaisyys 1
               :tr-loppuosa 101
               :tr-loppuetaisyys 100}
              {:nimi "Ei-validi"
               :tr-numero 20
               :tr-ajorata 1
               :tr-kaista 11
               :tr-alkuosa 1
               :tr-alkuetaisyys 1
               :tr-loppuosa 1
               :tr-loppuetaisyys 100}]))
        "Epävalidi kohde huomataan validien joukosta")))