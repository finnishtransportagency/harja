(ns harja.views.hallinta.tarjoushinnat
  (:require [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.komponentti :as komp]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.hallinta.tarjoushinnat :as tiedot]))

(defn urakan-tarjoushinnat [e! tarjoushinnat]
  [grid/grid
   {:tunniste :id
    :luokat "urakan-tarjoushinnat-taulukko"
    :reunaviiva? true
    :voi-poistaa? (constantly false)
    :piilota-toiminnot? true
    :tallenna #(tuck-apurit/e-kanavalla! e! tiedot/->PaivitaTarjoushinnat %)}
   [{:otsikko "Hoitokausi"
     :nimi :hoitokausi
     :tyyppi :numero
     :muokattava? (constantly false)
     :desimaalien-maara 0
     :leveys "100px"}
    {:otsikko "Tarjoushinta"
     :nimi :tarjous-tavoitehinta
     :muokattava? (comp not :on-paatos)
     :tyyppi :euro
     :leveys ""}]
   tarjoushinnat])

(defn tarjoushinnat* [e! app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeTarjoushinnat)))
    (fn [e! {:keys [tarjoushinnat] :as app}]
      [:div
       [debug/debug app]
       [:h1 "MH-Urakoiden tarjoushinnat"]
       [:p "Tarjouksen mukaisten tavoitehintojen syöttö."]
       [:p "Mikäli kentän syöttö on estetty, kyseiselle hoitokaudelle on kirjattu välikatselmuksessa lupauksia, eikä tarjoushintaa voi muokata ennen kuin päätös perutaan."]
       [grid/grid
        {:otsikko "MH-urakoiden tarjoushinnat"
         :tunniste :urakka
         :vetolaatikot (into {}
                         (map (fn [[urakka tavoitehinnat]]
                                [urakka [urakan-tarjoushinnat e! tavoitehinnat]])
                           (group-by :urakka tarjoushinnat)))}
        [{:tyyppi :vetolaatikon-tila :leveys 0.5}
         {:otsikko "Urakka" :nimi :urakka-nimi :leveys 15}]
        (vec (into #{} (map #(select-keys % [:urakka :urakka-nimi])
                         tarjoushinnat)))]])))

;; TODO: Riviin näkuviin, onko tarjoushintoja syöttämättä
;;       Päättyneet urakat erikseen
;;       Haku urakalla
;;       Checkbox näytä vain puutteelliset

(defn tarjoushinnat []
  [tuck tiedot/tila tarjoushinnat*])
