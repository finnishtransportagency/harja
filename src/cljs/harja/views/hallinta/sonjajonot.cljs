(ns harja.views.hallinta.sonjajonot
  (:require [tuck.core :as t]
            [harja.tiedot.hallinta.sonjajonot :as tila]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.debug :as debug]))

(defn vastaanottaja [e! {vastaanottajan-tila :vastaanottajan-tila}]
  [:div
   [ikonit/livicon-arrow-up]])

(defn tuottaja [e! {tuottajan-tila :tuottajan-tila}]
  [:div
   [ikonit/livicon-arrow-down]])

(defn jono [e! [jonon-nimi {:keys [tuottajat vastaanottajat jonon-viestit]}]]
  [:div {:style {:display "flex"
                 :flex-direction "column"}}
   [:h3 jonon-nimi]
   [:div
    (map #(with-meta
            (identity
              [:span %])
            {:key %})
         jonon-viestit)]
   (concat
     (map-indexed #(with-meta
                     (tuottaja e! %2)
                     {:key %1})
                  tuottajat)
     (map-indexed #(with-meta
                     (vastaanottaja e! %2)
                     {:key %1})
                  vastaanottajat))])

(defn istunto [e! {sonja-jono :jono istunnon-tila :istunnon-tila}]
  [:div {:style {:display "flex"
                 :flex-direction "row"}}
   [:span istunnon-tila]
   (map #(with-meta
           (jono e! %)
           {:key (key %)})
        sonja-jono)])

(defn yhteys [e! {palvelin :palvelin {:keys [istunnot yhteyden-tila]} :tila}]
  [:div {:style {:display "flex"
                :flex-direction "column"}}
   [:div.thumbnail
    [:h2 palvelin]
    [:span yhteyden-tila]]
   [:div {:style {:display "flex"
                  :flex-direction "row"}}
    (map-indexed #(with-meta
                    (istunto e! %2)
                    {:key %1})
         istunnot)]])

(defn virhe [app]
  [:p app])

(defn sonjajonot* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tila/->HaeSonjanTila))
                      #())
    (fn [e! {sonjan-tila :sonjan-tila :as app}]
      [:div
       [debug/debug app]
       (if sonjan-tila
         (map #(with-meta
                 (yhteys e! %)
                 {:key (:palvelin %)})
              sonjan-tila)
         [virhe app])])))

(defn sonjajonot []
  [t/tuck tila/tila sonjajonot*])