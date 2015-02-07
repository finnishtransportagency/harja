(ns harja.views.urakka.yleiset
  "Urakan 'Yleiset' välilehti: perustiedot ja yhteyshenkilöt"
  (:require [reagent.core :refer [atom] :as reagent]
            [bootstrap :as bs]
            [harja.ui.grid :as grid]
            [harja.tiedot.urakka.yhteystiedot :as yht]))

(defn yleiset
  "Yleiset välilehti"
  [ur]
  (fn []
    (let [yhteyshenkilot (yht/hae-urakan-yhteyshenkilot (:id ur))
          paivystajat (yht/hae-urakan-paivystajat (:id ur))]
      [:div
       "Urakan tunnus: foo" [:br]
       "Aikaväli: 123123" [:br]
       "Hallintayksikkö: sehän näkyy jo murupolussa" [:br]
       "Urakoitsija: Urakkapojat Oy" [:br]
       
       [grid/grid
         {:otsikko "Yhteyshenkilöt"}
         [{:otsikko "Rooli" :nimi :rooli :tyyppi :string}
          {:otsikko "Organisaatio" :nimi :organisaatio :tyyppi :string}
          {:otsikko "Nimi" :nimi :nimi :tyyppi :string}
          {:otsikko "Puhelin (virka)" :nimi :puhelin :tyyppi :string}
          {:otsikko "Puhelin (gsm)" :nimi :gsm :tyyppi :string} ;; mieti eri tyyppejä :puhelin / :email / jne...
          {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppin :email}]
         paivystajat
        ]
       
        [grid/grid
         {:otsikko "Päivystystiedot"}
         [{:otsikko "Rooli" :nimi :rooli :tyyppi :string}
          {:otsikko "Organisaatio" :nimi :organisaatio :tyyppi :string}
          {:otsikko "Nimi" :nimi :nimi :tyyppi :string}
          {:otsikko "Puhelin (virka)" :nimi :puhelin :tyyppi :string}
          {:otsikko "Puhelin (gsm)" :nimi :gsm :tyyppi :string} ;; mieti eri tyyppejä :puhelin / :email / jne...
          {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppin :email}]
         paivystajat
         ]
       
       ])))

