(ns harja.views.hallinta.palauteluokitukset
  (:require [tuck.core :refer [tuck]]
            [harja.tiedot.hallinta.palauteluokitukset :as tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset]))

(defn- aiheen-tarkenteet [{:keys [tarkenteet]}]
  [:div.margin-left-32
   [grid/grid
    {:otsikko "Tarkenteet"
     :tunniste :tarkenne-id}
    [{:nimi :nimi
      :otsikko "Nimi"
      :tyyppi :string}]
    tarkenteet]])

(defn- palauteluokitukset* [e! _app]
  (komp/luo
    (komp/sisaan
      #(e! (tiedot/->HaePalauteluokitukset)))
    (fn [e! {:keys [palauteluokitukset palauteluokkapaivitys-kesken? palauteluokkahaku-kesken?] :as app}]
      [:div
       [harja.ui.debug/debug {:app app}]
       (when palauteluokkahaku-kesken?
         [yleiset/ajax-loader "Ladataan palauteluokkia"])
       [:div
        [grid/grid
         {:otsikko "Aiheet palautejärjestelmästä"
          :tunniste :aihe-id
          :vetolaatikot (into {}
                          (map (juxt :aihe-id (fn [aihe]
                                                [aiheen-tarkenteet aihe]))
                            (filter #(seq (:tarkenteet %)) palauteluokitukset)))}
         [{:tyyppi :vetolaatikon-tila
           :leveys "52px"}
          {:nimi :nimi
           :leveys "auto"
           :otsikko "Nimi"
           :tyyppi :string}]
         palauteluokitukset]]


       [:div
        [napit/yleinen-ensisijainen "Hae palauteluokitukset palautejärjestelmästä"
         #(e! (tiedot/->PaivitaPalauteluokitukset))
         {:disabled palauteluokkapaivitys-kesken?}]
        [:p.caption.margin-top-4 "Hakee palauteluokitukset ja ylikirjoittaa ne kantaan! Käytä omalla vastuulla."]]])))

(defn palauteluokitukset []
  [tuck tiedot/tila palauteluokitukset*])
