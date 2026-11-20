(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.gridit
  (:require [reagent.core :as r]
            [harja.ui.grid :as grid]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.rekisterointi :refer [register!]]))

(def ^:private sarakkeet
  [{:otsikko "Nimi" :nimi :nimi :leveys 2}
   {:otsikko "Määrä" :nimi :maara :leveys 1}
   {:otsikko "Yks." :nimi :yksikko :leveys 0.5 :muokattava? (constantly false)}])

(def ^:private rivit
  [{:id 1 :nimi "Sorastus" :maara 12 :yksikko "tn"}
   {:id 2 :nimi "Paikkaus" :maara 4 :yksikko "kpl"}])

(defn- perusgrid [komponentti]
  [kehys/kortti komponentti
   [:p "Taulukko rivien ja sarakkeiden esittämiseen. Sarakkeet ja rivit tulevat käyttöpaikalta."]
   [:div {:data-cy (kehys/ankkuri komponentti "-grid")}
    [grid/grid {:otsikko "Perusgrid"
                :tyhja "Ei rivejä."
                :tunniste :id}
     sarakkeet
     rivit]]])

(defn- muokkausgrid [komponentti]
  (r/with-let [rivit (r/atom {1 {:id 1 :nimi "Murske" :maara 10 :yksikko "tn"}
                              2 {:id 2 :nimi "Sora" :maara 5 :yksikko "tn"}})]
    [kehys/kortti komponentti
     [:p "Muokkausgrid käsittelee rivit atomissa. Muutokset kulkevat käyttöpaikan muutos-callbackille."]
     [:div {:data-cy (kehys/ankkuri komponentti "-grid")}
      [grid/muokkaus-grid {:otsikko "Muokkausgrid"
                           :tyhja "Ei muokattavia rivejä."
                           :voi-muokata? true
                           :voi-lisata? false
                           :voi-kumota? false
                           :muutos (fn [_] nil)}
       [{:otsikko "Nimi" :nimi :nimi :tyyppi :string :leveys 2}
        {:otsikko "Määrä" :nimi :maara :tyyppi :numero :leveys 1}
        {:otsikko "Yks." :nimi :yksikko :muokattava? (constantly false) :leveys 0.5}]
       rivit]]]))

(register! :perusgrid {:esikatselu perusgrid})
(register! :muokkausgrid {:esikatselu muokkausgrid})
