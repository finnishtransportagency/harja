(ns harja-laadunseuranta.tarkastusajon-luonti
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.comms :as comms]
            [harja-laadunseuranta.urakkavalitsin :as urakkavalitsin]
            [harja-laadunseuranta.kuvat :as kuvat]
            [harja-laadunseuranta.sovellus :as s])
  (:require-macros [reagent.ratom :refer [run!]]
                   [cljs.core.async.macros :refer [go]]
                   [devcards.core :refer [defcard]]))

(def urakkatyypin-urakat
  (atom nil))

(defn tarkastusajon-luontidialogi [valittu-fn peruutettu-fn]
  (let [aloitettu (atom false)
        aloita-tarkastus (fn [tarkastustyyppi]
                           (s/valitse-urakka nil)
                           (valittu tarkastustyyppi))
        valittu (fn [tyyppi]
                  (reset! aloitettu true)
                  (valittu-fn tyyppi))
        urakkavalitsimen-urakkatyyppi (atom nil)]
    (fn [_ _]
      (if @urakkavalitsimen-urakkatyyppi
        (let [cb #(do
                   (s/valitse-urakka %)
                   (valittu @urakkavalitsimen-urakkatyyppi)
                   (reset! urakkavalitsimen-urakkatyyppi nil))]
          [urakkavalitsin/urakkavalitsin @urakkatyypin-urakat urakkavalitsimen-urakkatyyppi cb])
        (if @aloitettu
          [:div.tarkastusajon-luonti-dialog
           [:p "Luodaan tarkastusajoa..."]]
          [:div.tarkastusajon-luonti-dialog
           [:p "Valitse tarkastusajon tyyppi"]
           [:div
            [:nav.pikavalintapainike {:on-click #(aloita-tarkastus :kelitarkastus)} "Talvihoito"]
            [:nav.pikavalintapainike {:on-click #(aloita-tarkastus :soratietarkastus)} "Kesähoito"]
            [:nav.pikavalintapainike {:on-click #(do
                                                  (go (let [urakat (:ok (<! (comms/hae-urakkatyypin-urakat "paallystys")))]
                                                        (reset! urakkatyypin-urakat urakat)
                                                        (reset! urakkavalitsimen-urakkatyyppi :paallystys))))} "Päällystys"]
            #_[:nav.pikavalintapainike {:on-click #(valittu :tiemerkinta)} "Tiemerkintä"]]
           [:nav.pikavalintapainike.peruutuspainike {:on-click #(peruutettu-fn)}
            "Peruuta"]])))))

(defn tarkastusajon-paattamisdialogi [paattamattomia kylla-fn ei-fn]
  (let [kylla-klikattu (atom false)]
    (fn [_ _ _]
      (if @kylla-klikattu
        [:div.tarkastusajon-luonti-dialog
         [:p "Päätetään, älä sulje selainta..."]
         [:div [:img.centered {:src kuvat/+spinner+
                      :height "32px"}]]]
        [:div.tarkastusajon-luonti-dialog
         [:p "Päätetäänkö tarkastusajo?"]
         [:div.tarkastusajon-luonti-dialog-wrap
          [:button.nappi-ensisijainen {:on-click #(when (= 0 @paattamattomia)
                             (reset! kylla-klikattu true)
                             (kylla-fn))}
           (if (> @paattamattomia 0)
             [:div
              [:img.odotusspinneri {:src kuvat/+spinner+
                     :height "32px"}]
              "Odota..."]
             "Kyllä")]
          [:button.nappi-toissijainen {:on-click #(ei-fn)} "Ei"]]]))))

(defn tarkastusajon-jatkamisdialogi [jatka-fn pakota-lopetus-fn]
  [:div.tarkastusajon-luonti-dialog
   [:p "Jatketaanko tarkastusajoa?"]
   [:div
    [:nav.pikavalintapainike {:on-click #(jatka-fn)} "Jatka"]
    [:nav.pikavalintapainike.nappi-kielteinen {:on-click #(pakota-lopetus-fn)} "Pakota lopetus"]]])

(defcard luontidialogi-card
  (reagent/as-element [tarkastusajon-luontidialogi #() #()]))
