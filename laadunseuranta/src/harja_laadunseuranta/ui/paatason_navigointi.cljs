(ns harja-laadunseuranta.ui.paatason-navigointi
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.tiedot.paatason-navigointi :as tiedot]
            [cljs-time.local :as l])
  (:require-macros
    [harja-laadunseuranta.macros :as m]
    [cljs.core.async.macros :refer [go go-loop]]
    [devcards.core :as dc :refer [defcard deftest]]))

(defn- toggle-painike [{:keys [nimi ikoni avain tyyppi click-fn] :as tiedot}]
  (fn []
    [:div.toggle-valintapainike {:on-click #(click-fn tiedot)}
     [:div.toggle-valintapainike-ikoni
      (case tyyppi
        :piste [:img.toggle-piste {:src (kuvat/havainto-ikoni "ikoni_pistemainen")}]
        :vali [:img.toggle-vali {:src (kuvat/havainto-ikoni "ikoni_alue")}])
      (when ikoni
        [:img.toggle-ikoni {:src (kuvat/havainto-ikoni ikoni)}])]
     [:div.toggle-valintapainike-otsikko
      nimi]]))

(defn- paatason-navigointikomponentti [{:keys [valilehdet kirjaa-pistemainen-havainto-fn
                                               kirjaa-valikohtainen-havainto-fn] :as tiedot}]
  (let [nakyvissa? (atom true)
        valittu (atom (:avain (first valilehdet)))
        aseta-valinta! (fn [uusi-valinta]
                         (.log js/console "Vaihdetaan tila: " (str uusi-valinta))
                         (reset! valittu uusi-valinta))
        piilotusnappi-klikattu (fn []
                                 (swap! nakyvissa? not))]
    (fn []
      [:div {:class (str "paatason-navigointi-container "
                         (if @nakyvissa?
                           "paatason-navigointilaatikko-nakyvissa"
                           "paatason-navigointilaatikko-piilossa"))}
       [:div.nayttonappi {:on-click piilotusnappi-klikattu}]
       [:div.paatason-navigointilaatikko
        [:div.piilotusnappi {:on-click piilotusnappi-klikattu}]

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
         [:div.valintapainikkeet
          (let [{:keys [sisalto] :as valittu-valilehti}
                (first (filter
                         #(= (:avain %) @valittu)
                         valilehdet))]
            (doall (for [havainto sisalto]
                     ^{:key (:nimi havainto)}
                     [toggle-painike (merge havainto
                                            {:click-fn (case (:tyyppi havainto)
                                                         :piste kirjaa-pistemainen-havainto-fn
                                                         :vali kirjaa-valikohtainen-havainto-fn)})])))]]
        [:footer]]])))

(defn paatason-navigointi []
  [paatason-navigointikomponentti {:valilehdet tiedot/oletusvalilehdet
                                   :kirjaa-pistemainen-havainto-fn
                                   tiedot/kirjaa-pistemainen-havainto!
                                   :kirjaa-valikohtainen-havainto-fn
                                   tiedot/kirjaa-valikohtainen-havainto!}])