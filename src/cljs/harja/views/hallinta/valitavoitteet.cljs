(ns harja.views.hallinta.valitavoitteet
  "Valtakunnallisten välitavoitteiden näkymä"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! timeout chan]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.hallinta.valitavoitteet :as tiedot]
            [harja.pvm :as pvm]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.grid :refer [grid]]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as y]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defn valitavoitteet-grid [valitavoitteet-atom
                           kertaluontoiset-valitavoitteet-atom
                           toistuvat-valitavoitteet-atom]
  [:div
   [grid/grid
    {:otsikko "Valtakunnalliset kertaluontoiset välitavoitteet"
     :tyhja (if (nil? @kertaluontoiset-valitavoitteet-atom)
              [y/ajax-loader "Välitavoitteita haetaan..."]
              "Ei kertaluontoisia välitavoitteita")
     :tallenna (when (oikeudet/voi-kirjoittaa? oikeudet/hallinta-valitavoitteet)
                 #(go (let [vastaus (<! (tiedot/tallenna-valitavoitteet
                                          (->> %
                                               (map (fn [valitavoite]
                                                      (assoc valitavoite :tyyppi :kertaluontoinen))))))]
                        (if (k/virhe? vastaus)
                          (viesti/nayta! "Välitavoitteiden tallentaminen epännistui"
                                         :warning viesti/viestin-nayttoaika-keskipitka)
                          (reset! valitavoitteet-atom vastaus)))))}
   [{:otsikko "Nimi" :leveys 60 :nimi :nimi :tyyppi :string :pituus-max 128}
    {:otsikko "Urakkatyyppi" :leveys 20 :nimi :urakkatyyppi
     :tyyppi :valinta
     :validoi [[:ei-tyhja "Valitse urakkatyyppi, jota tämä välitavoite koskee"]]
     :valinta-nayta #(or (:nimi (first (filter
                                         (fn [tyyppi] (= (:arvo tyyppi) %))
                                         nav/+urakkatyypit+)))
                         "- valitse -")
     :fmt #(:nimi (first (filter
                               (fn [tyyppi] (= (:arvo tyyppi) %))
                               nav/+urakkatyypit+)))
     :valinnat (mapv :arvo nav/+urakkatyypit+)}

    {:otsikko "Takaraja" :leveys 20 :nimi :takaraja :fmt pvm/pvm-opt :tyyppi :pvm}]
    @kertaluontoiset-valitavoitteet-atom]

   [grid/grid
    {:otsikko "Valtakunnalliset vuosittain toistuvat välitavoitteet"
     :tyhja (if (nil? @toistuvat-valitavoitteet-atom)
              [y/ajax-loader "Välitavoitteita haetaan..."]
              "Ei toistuvia välitavoitteita")
     :tallenna (when (oikeudet/voi-kirjoittaa? oikeudet/hallinta-valitavoitteet)
                 #(go (let [vastaus (<! (tiedot/tallenna-valitavoitteet
                                          (<! (tiedot/tallenna-valitavoitteet
                                                (->> %
                                                     (map (fn [valitavoite]
                                                            (assoc valitavoite :tyyppi :toistuva))))))))]
                        (if (k/virhe? vastaus)
                          (viesti/nayta! "Välitavoitteiden tallentaminen epännistui"
                                         :warning viesti/viestin-nayttoaika-keskipitka)
                          (reset! valitavoitteet-atom vastaus)))))}
    [{:otsikko "Nimi" :leveys 60 :nimi :nimi :tyyppi :string :pituus-max 128}
     {:otsikko "Urakkatyyppi" :leveys 20 :nimi :urakkatyyppi
      :tyyppi :valinta
      :validoi [[:ei-tyhja "Valitse urakkatyyppi, jota tämä välitavoite koskee"]]
      :valinta-nayta #(or (:nimi (first (filter
                                          (fn [tyyppi] (= (:arvo tyyppi) %))
                                          nav/+urakkatyypit+)))
                          "- valitse -")
      :fmt #(:nimi (first (filter
                           (fn [tyyppi] (= (:arvo tyyppi) %))
                           nav/+urakkatyypit+)))
      :valinnat (mapv :arvo nav/+urakkatyypit+)}
     {:otsikko "Takarajan toistopäivä" :leveys 10 :nimi :takaraja-toistopaiva
      :tyyppi :numero :desimaalien-maara 0 :validoi [[:rajattu-numero nil 1 31 "Anna päivä välillä 1 - 31"]]}
     {:otsikko "Takarajan toistokuukausi" :leveys 10 :nimi :takaraja-toistokuukausi
      :tyyppi :numero :desimaalien-maara 0 :validoi [[:rajattu-numero nil 1 12 "Anna kuukausi välillä 1 - 12"]]}]
    @toistuvat-valitavoitteet-atom]])

(defn valitavoitteet []
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (fn []
      [valitavoitteet-grid
       tiedot/valitavoitteet
       tiedot/kertaluontoiset-valitavoitteet
       tiedot/toistuvat-valitavoitteet])))
