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
  (let [valittu (atom (:avain (first valilehdet)))
        aseta-valinta! (fn [uusi-valinta]
                         (.log js/console "Vaihdetaan tila: " (str uusi-valinta))
                         (reset! valittu uusi-valinta))]
    (fn []
      [:div.paatason-navigointi
       [:header
        [:ul.valilehtilista
         (doall
           (for [{:keys [avain] :as valilehti} valilehdet]
            ^{:key avain}
              [:li {:class (str "valilehti "
                                (when (= avain
                                         @valittu)
                                  "valilehti-valittu"))
                    :on-click #(aseta-valinta! avain)}
               (:nimi valilehti)]))]]
       [:div.sisalto
        #_(doall (for [valilehti valilehdet]
                 ^{:key avain}
                 [toggle-painike "Saumavirhe"]))]
       [:div.footer]])))