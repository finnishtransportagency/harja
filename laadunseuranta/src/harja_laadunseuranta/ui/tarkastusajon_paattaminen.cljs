(ns harja-laadunseuranta.ui.tarkastusajon-paattaminen
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.comms :as comms]
            [harja-laadunseuranta.tiedot.tarkastusajon-luonti :as luonti]
            [harja-laadunseuranta.tiedot.tarkastusajon-paattaminen :as paattaminen]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.ui.yleiset.napit :refer [nappi]]
            [harja-laadunseuranta.ui.yleiset.dropdown :refer [dropdown]]
            [harja-laadunseuranta.tiedot.sovellus :as s])
  (:require-macros [reagent.ratom :refer [run!]]
                   [cljs.core.async.macros :refer [go]]
                   [devcards.core :refer [defcard]]))

(defn ajo-paatetaan-dialogi []
  [:div.tarkastusajon-paattaminen-dialog
   [:div.ohjeteksti "Päätetään, älä sulje selainta..."]
   [:div [:img.centered {:src kuvat/+spinner+
                         :height "32px"}]]])

(defn- urakkavarmistusdialogi
  [{:keys [urakat valittu-urakka-atom urakka-varmistettu! paattaminen-peruttu!]}]
  [:div.tarkastusajon-paattaminen-dialog
   [:div.ohjeteksti "Tarkastusajo liitetään urakkaan"]
   [dropdown
    urakat
    {:luokka "urakkavalitsin"
     :valittu (fn [e]
                (reset! valittu-urakka-atom (js/parseInt (.-value (.-target e)))))}]
   [nappi "OK"
    {:luokat-str "nappi-ensisijainen"
     :on-click urakka-varmistettu!}]
   [nappi "Peruuta"
    {:luokat-str "nappi-toissijainen"
     :on-click paattaminen-peruttu!}]])

(defn- paattasmisvarmistusdialogi
  [{:keys [paattamattomia-merkintoja paattaminen-peruttu! lopetuspaatos-varmistettu!]}]
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
                  (lopetuspaatos-varmistettu!))}]
   [nappi "Ei"
    {:luokat-str "nappi-toissijainen"
     :on-click paattaminen-peruttu!}]
   [:div.lahettamattomia
    (when (> paattamattomia-merkintoja 0)
      (str "Lähetetään merkintöjä... (" paattamattomia-merkintoja ")"))]])

(defn tarkastusajon-paattamiskomponentti [paattamisvaihe]
  [:div.tarkastusajon-paattaminen-dialog-container
   (case paattamisvaihe
     :paattamisvarmistus
     [paattasmisvarmistusdialogi
      {:paattamattomia-merkintoja @s/lahettamattomia-merkintoja
       :lopetuspaatos-varmistettu! paattaminen/lopetuspaatos-varmistettu!
       :paattaminen-peruttu! paattaminen/paattaminen-peruttu!}]

     :urakkavarmistus
     [urakkavarmistusdialogi
      {:urakka-varmistettu! paattaminen/urakka-varmistettu!
       :paattaminen-peruttu! paattaminen/paattaminen-peruttu!
       :urakat @s/oikeus-urakoihin
       :valittu-urakka-atom s/valittu-urakka-id}]

     :paatetaan
     [ajo-paatetaan-dialogi])])

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
