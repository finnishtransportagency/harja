(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.gridit
  (:require [reagent.core :as r]
            [harja.ui.grid :as grid]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]))

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

(def esikatselut
  {:perusgrid perusgrid})
