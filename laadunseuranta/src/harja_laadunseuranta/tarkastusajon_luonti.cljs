(ns harja-laadunseuranta.tarkastusajon-luonti
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.kuvat :as kuvat]
            [harja-laadunseuranta.sovellus :as sovellus])
  (:require-macros [reagent.ratom :refer [run!]]
                   [devcards.core :refer [defcard]]))

(defn tarkastusajon-luontidialogi [valittu-fn peruutettu-fn]
  (let [aloitettu (atom false)
        valittu (fn [tyyppi]
                  (reset! aloitettu true)
                  (valittu-fn tyyppi))]
    (fn [_ _]
      (if @aloitettu
        [:div.tarkastusajon-luonti-dialog
         [:p "Luodaan tarkastusajoa..."]]
        [:div.tarkastusajon-luonti-dialog
         [:p "Valitse tarkastusajon tyyppi"]
         [:div
          [:nav.pikavalintapainike {:on-click #(valittu :kelitarkastus)} "Talvitarkastus"]
          [:nav.pikavalintapainike {:class (when (not (sovellus/kesatarkastus-beta?))
                                             "disabled") :on-click #(when (sovellus/kesatarkastus-beta?)
                                                                      (valittu :soratietarkastus))}
           "Kesätarkastus"]]
         [:nav.pikavalintapainike.peruutuspainike {:on-click #(peruutettu-fn)}
          [:span.livicon-delete] "Peruuta"]]))))

(defn tarkastusajon-paattamisdialogi [paattamattomia kylla-fn ei-fn]
  (let [kylla-klikattu (atom false)]
    (fn [_ _ _]
      (if @kylla-klikattu
        [:div.tarkastusajon-luonti-dialog
         [:p "Päätetään, älä sulje selainta..."]
         [:div [:img {:src kuvat/+spinner+
                      :height "32px"}]]]
        [:div.tarkastusajon-luonti-dialog
         [:p "Päätetäänkö tarkastusajo?"]
         [:ul
          [:li {:on-click #(when (= 0 @paattamattomia)
                             (reset! kylla-klikattu true)
                             (kylla-fn))}
           (if (> @paattamattomia 0)
             [:div
              [:img {:src kuvat/+spinner+
                     :height "32px"}]
              "Odota..."]
             "Kyllä")]
          [:li {:on-click #(ei-fn)} "Ei"]]]))))

(defn tarkastusajon-jatkamisdialogi [jatka-fn pakota-lopetus-fn]
  [:div.tarkastusajon-luonti-dialog
   [:p "Jatketaanko tarkastusajoa?"]
   [:div
    [:nav.pikavalintapainike {:on-click #(jatka-fn)} "Jatka"]
    [:nav.pikavalintapainike {:on-click #(pakota-lopetus-fn)} "Pakota lopetus"]]])

(defcard luontidialogi-card
  (reagent/as-element [tarkastusajon-luontidialogi #() #()]))
