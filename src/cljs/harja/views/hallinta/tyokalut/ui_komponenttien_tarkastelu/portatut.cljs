(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.portatut
  (:require [reagent.core :as r]
            [harja.ui.bootstrap :as bootstrap]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]))

(defn- panel [komponentti]
  [kehys/kortti komponentti
   [:p "Public API säilyy, mutta esikatselussa ei renderöidy Bootstrapin panel-rakennetta."]
   [bootstrap/panel {:data-cy (kehys/ankkuri komponentti "-juuri")}
    "Panel-otsikko"
    [:p {:data-cy (kehys/ankkuri komponentti "-sisalto")} "Panel-sisältö"]]])

(defn- tabs [komponentti]
  (r/with-let [aktiivinen (r/atom :perustiedot)]
    [kehys/kortti komponentti
     [:p "Aktiivinen välilehti ja valinnan callback säilyvät käyttöpaikoille."]
     [:div {:data-cy (kehys/ankkuri komponentti "-juuri")}
      [bootstrap/tabs {:active aktiivinen
                       :classes "ui-portatut-tabs"}
       "Perustiedot" :perustiedot [:p "Perustiedot"]
       "Historia" :historia [:p "Historia"]]]
     [:p {:data-cy (kehys/ankkuri komponentti "-tila")} (str "Valittu: " (name @aktiivinen))]]))

(defn- navbar [komponentti]
  [kehys/kortti komponentti
   [:p "Header, vasen/oikea-itemit ja mobiilivaihtaja säilyvät."
    ]
   [:div {:data-cy (kehys/ankkuri komponentti "-juuri")}
    [bootstrap/navbar {}
     [:span "Harja"]
     [:a {:href "#"} "Vasen linkki"]
     :right
     [:a {:href "#"} "Oikea linkki"]]]])

(def esikatselut
  {:panel panel
   :tabs tabs
   :navbar navbar})
