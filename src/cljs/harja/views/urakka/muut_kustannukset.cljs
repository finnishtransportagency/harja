(ns harja.views.urakka.muut-kustannukset
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.grid :as grid]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.komponentti :as komp]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka :as urakka]
            [harja.ui.valinnat :as valinnat]
            [cljs-time.core :as t]))

(defonce kustannukset-atom (atom {}))

;; Ylläpitokohteiden sarakkeiden leveydet
(def kustannus-nimi-leveys 5)
(def kustannus-summa-leveys 3)

;; muokkaus yllapitokohteet-viewin pohjalla wip

(def grid-opts {:otsikko "Muut kustannukset"
                :tyhja (if (nil? @kustannukset-atom) [ajax-loader "Haetaan kustannuksia..."] "Ei kustannuksia")
                :tallenna #(log "muut-kustannukset: tallenna kutsuttu")
                :muutos (fn [grid]
                          #(log "muut-kustannukset: muutos kutsuttu"))
                :voi-lisata? true
                :voi-muokata-rivia? :muokattava
                :esta-poistaminen? (fn [rivi] (or (not (nil? (:paallystysilmoitus-id rivi))) ;; <- tahan tsekkaus onko kohdistamaton sanktio vai suoraan syötetty?
                                                  (not (nil? (:paikkausilmoitus-id rivi)))))
                :esta-poistaminen-tooltip
                (fn [_] "Kohteeseen liittymättömästä sanktiosta johtuvaa kustannusta ei voi poistaa.")})

(def grid-skeema
  (into [] (concat  [{:otsikko "Kustannus" :nimi :kustannus-nimi
                      :tyyppi :string :leveys kustannus-nimi-leveys
                      :validoi [[:uniikki "Sama kohdenumero voi esiintyä vain kerran."]]}
                     {:otsikko "summa" :nimi :kustannus-summa
                      :tyyppi :string :leveys kustannus-summa-leveys}]
                    [])))

(defn grid-tiedot []
  [{:id "foo" :kustannus-nimi "ruoka" :muokattava true :kustannus-summa 42.50 }
   {:id "bar" :kustannus-nimi "juoma" :muokattava false :kustannus-summa 234.00}])

(defn muut-kustannukset [urakka muut-kustannukset-lomakedata]
  (komp/luo
   (fn [urakka]
     [:div.muut-kustannukset
      [grid/grid grid-opts grid-skeema (grid-tiedot)]])))
