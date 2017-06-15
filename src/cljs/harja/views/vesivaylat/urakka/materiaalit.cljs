(ns harja.views.vesivaylat.urakka.materiaalit
  (:require [tuck.core :as tuck]
            [harja.ui.grid :as grid]
            [harja.domain.vesivaylat.materiaali :as m]
            [harja.tiedot.vesivaylat.urakka.materiaalit :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]))


(defn- materiaaliloki [e! nimi rivit]
  (komp/luo
   (komp/sisaan #(e! (tiedot/->HaeMateriaalinKaytto nimi)))
   (fn [e! nimi rivit]
     [grid/grid {:tunniste ::m/pvm}
      [{:otsikko "Pvm" :nimi ::m/pvm :fmt pvm/pvm :leveys 1}
       {:otsikko "Määrä" :nimi ::m/maara :tyyppi :numero :leveys 1}
       {:otsikko "Lisätieto" :nimi ::m/lisatieto :leveys 5}]
      rivit])))

(defn materiaalit* [e! app]
  (komp/luo
   (komp/sisaan #(e! (tiedot/->PaivitaUrakka @nav/valittu-urakka)))
   (komp/watcher nav/valittu-urakka (fn [_ _ ur]
                                      (e! (tiedot/->PaivitaUrakka ur))))
   (fn [e! {:keys [materiaalilistaus materiaalin-kaytto] :as app}]
     [grid/grid {:tunniste ::m/nimi
                 :vetolaatikot (into {}
                                     (map (juxt ::m/nimi (fn [{nimi ::m/nimi}]
                                                           [materiaaliloki e! nimi
                                                            (materiaalin-kaytto nimi)])))
                                     materiaalilistaus)}
      [{:tyyppi :vetolaatikon-tila :leveys 1}
       {:otsikko "Materiaali" :nimi ::m/nimi :tyyppi :string :leveys 30}
       {:otsikko "Alkuperäinen määrä" :nimi ::m/alkuperainen-maara :tyyppi :numero :leveys 10}
       {:otsikko "Määrä nyt" :nimi ::m/maara-nyt :tyyppi :numero :leveys 10}]
      materiaalilistaus])))

(defn materiaalit [ur]
  [tuck/tuck tiedot/app materiaalit*])
