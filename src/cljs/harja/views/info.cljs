(ns harja.views.info
  "FIXME Infonäkymä mihin siirretty koulutusvideot julkiselta sisäiseen palvelimeen.
   Videot haetaan tietokannasta rajapintaa käyttäen"
  (:require [tuck.core :refer [tuck]]
            [harja.ui.komponentti :as komp]
            [harja.ui.debug :as debug]
            [harja.tiedot.info :as tiedot]))

(defn listaa-videot [e! app]
  (let [videot (:videot app)
        koulutusvideo (into {}
                            (map (juxt :id (fn [video] [videot e! app video])))
                            videot)]
    [:li
     [:a {:href (koulutusvideo :linkki)}]]))

(defn videot* [e! _]
  (komp/luo
   (komp/sisaan
    #(do
       (e! (tiedot/->HaeKoulutusvideot))))
   (fn [e! app]
     (when (:videot app)

       [:span
        [:div.section
         [:h3 "Harja infosivu"
          [:li
           [:a {:href "https://finnishtransportagency.github.io/harja/"}
            "https://finnishtransportagency.github.io/harja/"]]]
         [:h3 "Koulutusvideot"]]

        [:div
         [:div
          [debug/debug app]
          (listaa-videot e! app)]]]))))

(defn info []
  [tuck tiedot/data videot*])