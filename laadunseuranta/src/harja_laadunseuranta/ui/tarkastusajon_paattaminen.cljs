(ns harja-laadunseuranta.ui.tarkastusajon-paattaminen
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.comms :as comms]
            [harja-laadunseuranta.tiedot.tarkastusajon-luonti :as luonti]
            [harja-laadunseuranta.tiedot.tarkastusajon-paattaminen :as paattaminen]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.ui.napit :refer [nappi]]
            [harja-laadunseuranta.tiedot.sovellus :as s])
  (:require-macros [reagent.ratom :refer [run!]]
                   [cljs.core.async.macros :refer [go]]
                   [devcards.core :refer [defcard]]))

(defn- tarkastusajon-paattamisdialogi [_]
  (let [kylla-klikattu (atom false)]
    (fn [{:keys [paattamattomia-merkintoja paata-ajo!
                 paattaminen-peruttu!]}]
      (if @kylla-klikattu
        [:div.tarkastusajon-paattaminen-dialog
         [:div.ohjeteksti "Päätetään, älä sulje selainta..."]
         [:div [:img.centered {:src kuvat/+spinner+
                               :height "32px"}]]]
        [:div.tarkastusajon-paattaminen-dialog
         [:div.ohjeteksti "Päätetäänkö tarkastusajo?"]
         [nappi [:div
                 (when (> paattamattomia-merkintoja 0)
                   [:img.odotusspinneri {:src kuvat/+spinner+}])
                 (if (> paattamattomia-merkintoja 0)
                   "Odota..."
                   "Kyllä")]
          {:luokat-str "nappi-ensisijainen nappi-paata-tarkastusajo"
           :on-click #(when (= 0 paattamattomia-merkintoja)
                        (reset! kylla-klikattu true)
                        (paata-ajo!))}]
         [nappi "Ei"
          {:luokat-str "nappi-toissijainen"
           :on-click paattaminen-peruttu!}]
         [:div.lahettamattomia
          (when (> paattamattomia-merkintoja 0)
            (str "Lähetetään merkintöjä... (" paattamattomia-merkintoja ")"))]]))))

(defn tarkastusajon-paattamiskomponentti []
  [:div.tarkastusajon-paattaminen-dialog-container
   [tarkastusajon-paattamisdialogi
    {:paattamattomia-merkintoja @s/lahettamattomia-merkintoja
     :paata-ajo! paattaminen/paata-ajo!
     :paattaminen-peruttu! paattaminen/paattaminen-peruttu!}]])

(defn- tarkastusajon-jatkamisdialogi [{:keys [jatka-ajoa! pakota-ajon-lopetus!]}]
  [:div.tarkastusajon-paattaminen-dialog
   [:div.ohjeteksti "Jatketaanko tarkastusajoa?"]
   [nappi "Jatka" {:luokat-str "nappi-ensisijainen"
                   :on-click jatka-ajoa!}]
   [nappi "Pakota lopetus" {:luokat-str "nappi-kielteinen"
                            :on-click pakota-ajon-lopetus!}]])

(defn tarkastusajon-jatkamiskomponentti []
  [:div.tarkastusajon-paattaminen-dialog-container
   [tarkastusajon-jatkamisdialogi
    {:jatka-ajoa! luonti/jatka-ajoa!
     :pakota-ajon-lopetus! paattaminen/pakota-ajon-lopetus!}]])

(defcard luontidialogi-card
  (reagent/as-element [tarkastusajon-luontidialogi #() #()]))
