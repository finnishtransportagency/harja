(ns harja.views.vesivaylat.urakka.materiaalit
  (:require [tuck.core :as tuck]
            [harja.ui.grid :as grid]
            [harja.domain.vesivaylat.materiaali :as m]
            [harja.tiedot.vesivaylat.urakka.materiaalit :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]))


(defn materiaalit* [e! app]
  (komp/luo
   (komp/sisaan #(e! (tiedot/->PaivitaUrakka @nav/valittu-urakka)))
   (komp/watcher nav/valittu-urakka (fn [_ _ ur]
                                      (e! (tiedot/->PaivitaUrakka ur))))
   (fn [e! {:keys [materiaalilistaus] :as app}]
     [grid/grid {}
      [{:otsikko "Materiaali" :nimi ::m/nimi :tyyppi :string}
       {:otsikko "Alkuperäinen määrä" :nimi ::m/alkuperainen-maara :tyyppi :numero}
       {:otsikko "Määrä nyt" :nimi ::m/maara-nyt :tyyppi :numero}]
      materiaalilistaus])))

(defn materiaalit [ur]
  [tuck/tuck tiedot/app materiaalit*])
