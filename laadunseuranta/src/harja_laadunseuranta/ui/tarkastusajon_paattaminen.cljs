(ns harja-laadunseuranta.ui.tarkastusajon-paattaminen
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.comms :as comms]
            [harja-laadunseuranta.tiedot.tarkastusajon-luonti :as tiedot]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.ui.napit :refer [nappi]]
            [harja-laadunseuranta.tiedot.sovellus :as s])
  (:require-macros [reagent.ratom :refer [run!]]
                   [cljs.core.async.macros :refer [go]]
                   [devcards.core :refer [defcard]]))

(defn tarkastusajon-paattamisdialogi [paattamattomia]
  (let [kylla-klikattu (atom false)]
    (fn [_ _ _]
      (if @kylla-klikattu
        [:div.tarkastusajon-paattaminen-dialog
         [:div.ohjeteksti "Päätetään, älä sulje selainta..."]
         [:div [:img.centered {:src kuvat/+spinner+
                               :height "32px"}]]]
        [:div.tarkastusajon-paattaminen-dialog
         [:div.ohjeteksti "Päätetäänkö tarkastusajo?"]
         [nappi [:div
                 (when (> @paattamattomia 0)
                   [:img.odotusspinneri {:src kuvat/+spinner+}])
                 (if (> @paattamattomia 0)
                   "Odota..."
                   "Kyllä")]
          {:luokat-str "nappi-ensisijainen nappi-paata-tarkastusajo"
           :on-click #(when (= 0 @paattamattomia)
                        (reset! kylla-klikattu true)
                        (tiedot/paata-ajo!))}]
         [nappi "Ei"
          {:luokat-str "nappi-toissijainen"
           :on-click #(tiedot/paattaminen-peruttu!)}]
         [:div.lahettamattomia
          (when (> @paattamattomia 0)
            (str "Lähetetään merkintöjä... (" @paattamattomia ")"))]]))))

(defn tarkastusajon-jatkamisdialogi []
  [:div.tarkastusajon-paattaminen-dialog
   [:div.ohjeteksti "Jatketaanko tarkastusajoa?"]
   [nappi "Jatka" {:luokat-str "nappi-ensisijainen"
                   :on-click #(tiedot/jatka-ajoa!)}]
   [nappi "Pakota lopetus" {:luokat-str "nappi-kielteinen"
                            :on-click #(tiedot/pakota-ajon-lopetus!)}]])

(defcard luontidialogi-card
         (reagent/as-element [tarkastusajon-luontidialogi #() #()]))
