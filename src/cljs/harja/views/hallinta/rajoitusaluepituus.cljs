(ns harja.views.hallinta.rajoitusaluepituus
  "Työkalu rajoitusalueiden pituuden laskentaan ja korjaamiseen."
  (:require [tuck.core :refer [tuck send-value! send-async!]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.hallinta.rajoitusalue-tiedot :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :refer [grid]]
            [harja.ui.debug :as debug]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.views.kartta :as kartta]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.hallintayksikot :as hal])

  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn rajoitusalueet* [e! app]
  (komp/luo
    (komp/sisaan-ulos
      #(do
         (reset! tiedot/nakymassa? true)
         (e! (tiedot/->HaeRajoitusalueet)))
      #(reset! tiedot/nakymassa? false))
    (fn [e! app]
      (let [rajoitusalueet (:rajoitusalueet app)]
        (if (oikeudet/voi-kirjoittaa? oikeudet/hallinta-toteumatyokalu)
          (when @tiedot/nakymassa?
            [:div
             [grid {:otsikko "Rajoitusalueiden pituudet"}

              [{:otsikko "Urakka"
                :tyyppi :string
                :nimi :urakka_nimi
                :leveys 1}
               {:otsikko "Tierekisteri"
                :tyyppi :string
                :nimi :tie
                :leveys 1}
               {:otsikko "Pituus kannasta"
                :tyyppi :numero
                :nimi :pituus-kannasta
                :leveys 0.5}
               {:otsikko "Pituus laskettu"
                :tyyppi :numero
                :nimi :pituus-laskettu
                :leveys 0.5}
               {:otsikko "Ajoradan pituus kannasta"
                :tyyppi :numero
                :nimi :ajoradan-pituus-kannasta
                :leveys 0.5}
               {:otsikko "Ajoradan pituus laskettu"
                :tyyppi :numero
                :nimi :ajoradan-pituus-laskettu
                :leveys 0.5}
               {:otsikko "Ajoradan pituus laskettu"
                :tyyppi :string
                :nimi :ei-tasmaa
                :leveys 0.5}]
              rajoitusalueet]
             ])
          "Puutteelliset käyttöoikeudet")))))

(defn rajoitusaluepituus-nakyma []
  [tuck tiedot/data rajoitusalueet*])
