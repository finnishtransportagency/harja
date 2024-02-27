(ns harja.views.hallinta.tarjoushinnat
  (:require [clojure.string :as str]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [reagent.core :as r]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.komponentti :as komp]
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
    (fn [e! {:keys [tarjoushinnat haettava-urakka vain-puutteelliset?] :as app}]
      (let [urakka-haku-id (gensym "urakkahaku")
            tarjoushinnat (->> tarjoushinnat
                            ;; Suodatetaan valintojen perusteella
                            (filter #(and (str/includes?
                                            (str/lower-case (:urakka-nimi %))
                                            (str/lower-case haettava-urakka))
                                       (or (not vain-puutteelliset?) (:puutteellisia? %))))

                            ;; Järjestys urakan nimellä
                            (sort-by :urakka-nimi)

                            ;; Lisätään väliotsikko jos mukana on päättyneitä urakoita
                            (#(if (some :urakka-paattynyt? %)
                                (conj % (grid/otsikko "Päättyneet urakat"))
                                %))

                            ;; Pääättyneet urakat pohjalle, jos niitä on.
                            (sort-by #(cond
                                        (grid/otsikko? %) 2
                                        (:urakka-paattynyt? %) 3
                                        :else 1)))]
        [:div.hallinta-tarjoushinnat
         [:h1 "MH-Urakoiden tarjoushinnat"]
         [:p "Tarjouksen mukaisten tavoitehintojen syöttö."]
         [:p "Mikäli kentän syöttö on estetty, kyseiselle hoitokaudelle on kirjattu välikatselmuksessa lupauksia, eikä tarjoushintaa voi muokata ennen kuin päätös perutaan."]
         [:div.suodattimet
          [:label {:for urakka-haku-id}
           "Urakka"]
          [kentat/tee-kentta
           {:vayla-tyyli? true
            :elementin-id urakka-haku-id
            :placeholder "Hae urakan nimellä..."
            :tyyppi :string}
           (r/wrap haettava-urakka
             #(do
                (e! (tiedot/->AsetaHaettavaUrakka %))))]
          [kentat/tee-kentta
           {:vayla-tyyli? true
            :tyyppi :checkbox
            :teksti "Näytä vain puutteelliset"}
           (r/wrap vain-puutteelliset?
             #(do
                (e! (tiedot/->AsetaVainPuutteelliset %))))]]
         [grid/grid
          {:otsikko "MH-urakoiden tarjoushinnat"
           :tunniste :urakka
           :vetolaatikot (into {}
                           (map (fn [{:keys [urakka tarjoushinnat]}]
                                  [urakka [urakan-tarjoushinnat e! tarjoushinnat]])
                             tarjoushinnat))}
          [{:tyyppi :vetolaatikon-tila :leveys 0.5}
           {:otsikko "Urakka" :nimi :urakka-nimi :leveys 10}
           {:nimi :puutteellisia? :leveys 5
            :tyyppi :komponentti
            :komponentti (fn [rivi]
                           (when (:puutteellisia? rivi)
                             [:span.tarjoushintoja-puuttuu [ikonit/harja-icon-status-error] "Tarjoushintoja puuttuu"]))}]
          tarjoushinnat]]))))

(defn tarjoushinnat []
  [tuck tiedot/tila tarjoushinnat*])
