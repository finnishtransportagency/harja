(ns harja.views.hallinta.tyokalut.tieosoitteet-nakyma
  "Näytetään kaikki tieosoitteet, jotta toteumia, talvihoitoreitteja ja muita, jotka vaatii tieosoitteen, on helpompi lisätä."
  (:require [reagent.core :as r]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.ui.debug :as debug]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.napit :as napit]
            [harja.tiedot.hallinta.tyokalut.tieosoitteet-tyokalu-tiedot :as tiedot]))



(defn tieosoitteet* [e! app]
  (if (oikeudet/voi-kirjoittaa? oikeudet/hallinta-toteumatyokalu)
    (komp/luo
      (komp/sisaan #(e! (tiedot/->HaeTieosoitteet)))
      (fn [e! {:keys [tieosoitteet filtteroidyt-tieosoitteet haku-kaynnissa?] :as app}]
        [:div.tieosoitteet-hallinta
         [:h1 "Tieosoitteet"]

         [debug/debug app]
         [:div.row
          [:div.col-xs-4
           [kentat/tee-kentta
            {:vayla-tyyli? true
             :elementin-id (gensym)
             :placeholder "Tie"
             :tyyppi :positiivinen-numero}
            (r/wrap (:tie app)
              #(e! (tiedot/->FiltteroiTienumerolla %)))]]]

         [:div.row
          ;; Jos haku käynnissä, näytä hyrrä
          (if haku-kaynnissa?
            [:div.ajax-loader-valistys
             [ajax-loader-pieni (str "Haetaan tietoja...")]]

            [grid/grid
             {:tyhja "Ei tieosoitteita."
              :tunniste :tunniste
              :piilota-toiminnot? true}
             [{:otsikko "Tie" :nimi :tie :tyyppi :string :leveys 1}
              {:otsikko "Osa" :nimi :osa :tyyppi :string :leveys 1}
              {:otsikko "Alkuetäisyys" :nimi :alkuetaisyys :tyyppi :string :leveys 1}
              {:otsikko "Loppuetäisyys" :nimi :loppuetaisyys :tyyppi :string :leveys 1}]
             filtteroidyt-tieosoitteet])]]))
    "Puutteelliset käyttöoikeudet"))

(defn tieosoitteet []
  [tuck tiedot/tila tieosoitteet*])
