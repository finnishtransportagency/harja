(ns harja.ui.kumousboksi
  (:require [reagent.core :as r]
            [reagent.core :refer [atom] :as r]
            [harja.ui.napit :as napit]
            [cljs.core.async :refer [<! timeout]]
            [harja.ui.komponentti :as komp]
            [harja.ui.dom :as dom]
            [harja.fmt :as fmt]
            [goog.events.EventType :as EventType]
            [harja.loki :refer [log]]
            [harja.ui.ikonit :as ikonit]
            [cljs-time.core :as t])
  (:require-macros [harja.tyokalut.ui :refer [for*]]
                   [cljs.core.async.macros :refer [go]]))

(def ^{:private true}
kumoustiedot (atom
               {:ehdota-kumoamista? false
                :edellinen-tila nil
                :kumoustunniste nil})) ;; Jotta voidaan autom. piilottaa, jos samaa kumousta ehdotettu liian kauan

(defn ala-ehdota-kumoamista! []
  (swap! kumoustiedot assoc
         :ehdota-kumoamista? false
         :edellinen-tila nil
         :kumoustunniste nil))

(defn ehdotetaan-kumamomista? []
  (:ehdota-kumoamista? @kumoustiedot))

(defn ehdota-kumoamista!
  "Tallentaa annetun edellisen tilan ja ehdottaa toiminnon kumoamista."
  [edellinen-tila]
  (let [kumoustunniste (gensym "kumoustunniste")
        ehdotusaika-ms 10000]
    (swap! kumoustiedot assoc
           :ehdota-kumoamista? true
           :edellinen-tila edellinen-tila
           :kumoustunniste kumoustunniste)
    (go (<! (timeout ehdotusaika-ms))
        ;; Timeoutin jälkeen lopeta kumouksen ehdottaminen, jos ollaan edelleen ehdottamassa saman toiminnon kumoamista
        (when (= (:kumoustunniste @kumoustiedot) kumoustunniste)
          (ala-ehdota-kumoamista!)))))

(defn kumoa-muutos!
  "Kutsuu annettua kumoa-fn funktiota antaen sille parametriksi edellisen tilan ja funktion kumottu-fn,
   jota täytyy kutsua, kun kumous on valmis. Itse kumoaminen on kutsujan vastuulla, oletettavasti
   halutaan tallentaa edellinen tila palvelimelle."
  [kumoa-fn]
  (when-let [edellinen-tila (:edellinen-tila @kumoustiedot)]
    (kumoa-fn edellinen-tila ala-ehdota-kumoamista!)))

(defn kumousboksi [{:keys [nakyvissa? piilossa-sijainti nakyvissa-sijainti kumoa-fn sulje-fn]}]
  (let [tila (atom :tallennettu)
        nyky-sijainti (atom piilossa-sijainti)]
    (komp/luo
      (komp/kun-muuttuu (fn [{:keys [nakyvissa?]}]
                          (if nakyvissa?
                            (reset! nyky-sijainti nakyvissa-sijainti)
                            (do
                              (reset! nyky-sijainti piilossa-sijainti)
                              (reset! tila :tallennettu)))))
      (fn [{:keys [nakyvissa? lahto-x lahto-y loppu-x loppu-y kumoa-fn]}]
        [:div.kumousboksi {:style {:left (:left @nyky-sijainti)
                                   :top (:top @nyky-sijainti)
                                   :bottom (:bottom @nyky-sijainti)
                                   :right (:right @nyky-sijainti)}}
         [napit/sulje-ruksi sulje-fn]
         [:p (case @tila
               :tallennettu "Muutos tallennettu!"
               :kumotaan "Kumotaan...")]
         [napit/kumoa "Kumoa"
          (fn []
            (reset! tila :kumotaan)
            (kumoa-fn))
          {:disabled (or (= @tila :kumotaan)
                         (not nakyvissa?))}]]))))
