(ns harja.views.urakka.yllapitokohteet-test
  (:require
    [cljs-time.core :as t]
    [cljs.test :as test :refer-macros [deftest is]]
    [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
    [harja.pvm :refer [->pvm]]

    [harja.loki :refer [log]]))

(def kohdeosat
  [{:nimi "Laivaniemi 1"
    :tr-numero 1
    :tr-alkuosa 2
    :tr-alkuetaisyys 100
    :tr-loppuosa 2
    :tr-loppuetaisyys 200}
   {:nimi "Laivaniemi 2"
    :tr-numero 1
    :tr-alkuosa 2
    :tr-alkuetaisyys 200
    :tr-loppuosa 3
    :tr-loppuetaisyys 15}])

(deftest uuden-kohteen-lisaaminen-toimii
  (let [uusi-kohde-index-0 (yllapitokohteet/lisaa-uusi-kohdeosa kohdeosat 0)
        uusi-kohde-index-1 (yllapitokohteet/lisaa-uusi-kohdeosa kohdeosat 1)]
    (is (= uusi-kohde-index-0
           [{:nimi "Laivaniemi 1"
             :tr-numero 1
             :tr-alkuosa 2
             :tr-alkuetaisyys 100
             :tr-loppuosa nil
             :tr-loppuetaisyys nil}
            {:nimi "" :tr-numero 1
             :tr-alkuosa nil
             :tr-alkuetaisyys nil
             :tr-loppuosa 2
             :tr-loppuetaisyys 200
             :toimenpide ""}
            {:nimi "Laivaniemi 2"
             :tr-numero 1
             :tr-alkuosa 2
             :tr-alkuetaisyys 200
             :tr-loppuosa 3
             :tr-loppuetaisyys 15}]))
    (is (= uusi-kohde-index-1
           [{:nimi "Laivaniemi 1"
             :tr-numero 1
             :tr-alkuosa 2
             :tr-alkuetaisyys 100
             :tr-loppuosa 2
             :tr-loppuetaisyys 200}
            {:nimi "Laivaniemi 2"
             :tr-numero 1
             :tr-alkuosa 2
             :tr-alkuetaisyys 200
             :tr-loppuosa nil
             :tr-loppuetaisyys nil}
            {:nimi "" :tr-numero 1
             :tr-alkuosa nil
             :tr-alkuetaisyys nil
             :tr-loppuosa 3
             :tr-loppuetaisyys 15
             :toimenpide ""}]))))