(ns harja-laadunseuranta.ui.paatason-navigointi
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat])
  (:require-macros
    [harja-laadunseuranta.macros :as m]
    [cljs.core.async.macros :refer [go go-loop]]
    [devcards.core :as dc :refer [defcard deftest]]))

#_(defn toggle-painike [otsikko ikoni]
  [:nav.pikavalintapainike
   {:class (when (avain @havainnot) "painike-aktiivinen")
    :on-click (fn [_]
                (swap! havainnot #(update % avain not))
                (on-click))}
   (cond (string? ikoni)
         [:img.pikavalintaikoni {:src ikoni}]
         (vector? ikoni)
         [:span.pikavalintaikoni ikoni])
   [:span.pikavalintaotsikko otsikko]
   (when (avain @havainnot)
     [:span.rec "REC"])])

(defn paatason-navigointi [valilehdet]
  [:div.paatason-navigointi
   [:header.valilehdet
    [:ul.valilehdet
     (for [valilehti valilehdet]
       [:li.valilehti (:nimi valilehti)])]]
   [:div.sisalto
    ;; TODO
    #_(doall (for [valilehti valilehdet]
             [toggle-painike "Saumavirhe"]))]
   [:div.footer]])