(ns harja-laadunseuranta.painike
  (:require [reagent.core :as reagent :refer [atom]])
  (:require-macros [reagent.ratom :refer [run!]]
                   [devcards.core :refer [defcard]]))

(defn painike [{:keys [otsikko aktiivinen-otsikko on-click on-press delay]} aktiivinen]
  (let [active (atom aktiivinen)
        click-jo-hanskattu (cljs.core/atom false)
        alhaalla (cljs.core/atom nil)
        togglaa #(do
                   (reset! active true)
                   (reset! alhaalla nil)
                   (on-press))
        alas (fn []
               (when @active
                 (reset! active false)
                 (reset! click-jo-hanskattu true)
                 (on-click true))
               (reset! alhaalla (js/setTimeout togglaa delay)))
        ylos (fn []
               (when @alhaalla
                 (js/clearTimeout @alhaalla)
                 (reset! alhaalla nil)
                 (when-not @click-jo-hanskattu (on-click false))
                 (reset! click-jo-hanskattu false)))]
    (fn []
      [:nav.pikavalintapainike {:class (when @active "painike-aktiivinen")}
       [:span {;:on-touch-start alas
               ;:on-touch-end ylos
               
               :on-mouse-down alas
               :on-mouse-up ylos}
        (if @active aktiivinen-otsikko otsikko)]])))

(defcard painike-test
  (reagent/as-element [:div [painike {:delay 500
                                      :otsikko "Paina"
                                      :aktiivinen-otsikko "Painettu!"
                                      :on-click #(js/console.log "Click! " %)
                                      :on-press #(js/console.log "Press!")}]]))
