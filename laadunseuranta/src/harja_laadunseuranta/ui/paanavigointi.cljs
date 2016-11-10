(ns harja-laadunseuranta.ui.paanavigointi
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.tiedot.paanavigointi :as tiedot]
            [cljs-time.local :as l]
            [harja-laadunseuranta.tiedot.sovellus :as s])
  (:require-macros
    [harja-laadunseuranta.macros :as m]
    [cljs.core.async.macros :refer [go go-loop]]
    [devcards.core :as dc :refer [defcard deftest]]))

(defn- toggle-painike [_]
  (fn [{:keys [nimi ikoni avain tyyppi click-fn jatkuvat-havainnot disabloitu?] :as tiedot}]
    [:div {:on-click #(when-not disabloitu?
                       (click-fn tiedot))
           :class (str "toggle-valintapainike "
                       (when (and (= tyyppi :vali)
                                  (jatkuvat-havainnot avain))
                         "toggle-valintapainike-aktiivinen ")
                       (when (and (= tyyppi :vali)
                                  disabloitu?)
                         "toggle-valintapainike-disabloitu "))}
     [:div.toggle-valintapainike-ikoni
      (case tyyppi
        :piste [:img.toggle-piste {:src (kuvat/havainto-ikoni "ikoni_pistemainen")}]
        :vali [:img.toggle-vali {:src (kuvat/havainto-ikoni "ikoni_alue")}])
      (when ikoni
        [:img.toggle-ikoni {:src (kuvat/havainto-ikoni ikoni)}])]
     [:div.toggle-valintapainike-otsikko
      nimi]]))

(defn- paanavigointikomponentti [{:keys [valilehdet] :as tiedot}]
  (let [paanavigointi-nakyvissa? (atom true)
        valittu (atom (:avain (first valilehdet)))
        aseta-valinta! (fn [uusi-valinta]
                         (.log js/console "Vaihdetaan tila: " (str uusi-valinta))
                         (reset! valittu uusi-valinta))
        piilotusnappi-klikattu (fn []
                                 (swap! paanavigointi-nakyvissa? not))]
    (fn [{:keys [valilehdet kirjaa-pistemainen-havainto-fn
                 kirjaa-valikohtainen-havainto-fn
                 jatkuvat-havainnot] :as tiedot}]
      (let [jatkuvia-havaintoja-paalla? (not (empty? jatkuvat-havainnot))
            jatkuva-havainto-vaatii-nappaimiston? (not (empty?
                                                         (filter
                                                           #(and (jatkuvat-havainnot (:avain %))
                                                                 (:vaatii-nappaimiston? %))
                                                           (mapcat :sisalto valilehdet))))
            nayta-nappaimisto? (and jatkuvia-havaintoja-paalla?
                                    jatkuva-havainto-vaatii-nappaimiston?)]
        [:div {:class (str "paanavigointi-container "
                           (if @paanavigointi-nakyvissa?
                             "paanavigointi-container-nakyvissa"
                             "paanavigointi-container-piilossa"))}
         [:div.nayttonappi {:on-click piilotusnappi-klikattu}]
         [:div.navigointilaatikko-container
          [:div.navigointilaatikko
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
                        [toggle-painike
                         (merge havainto
                                {:click-fn (case (:tyyppi havainto)
                                             :piste kirjaa-pistemainen-havainto-fn
                                             :vali kirjaa-valikohtainen-havainto-fn)
                                 :jatkuvat-havainnot jatkuvat-havainnot
                                 :disabloitu? (boolean (and (= (:tyyppi havainto) :vali)
                                                            (not (empty? jatkuvat-havainnot))
                                                            (not (jatkuvat-havainnot (:avain havainto)))
                                                            (:vaatii-nappaimiston? havainto)))})])))]]
           [:footer]]]

         (when nayta-nappaimisto?
           [:div.nappaimisto
            "Näppis tähän"])]))))

(defn paanavigointi []
  [paanavigointikomponentti {:valilehdet tiedot/oletusvalilehdet
                             :kirjaa-pistemainen-havainto-fn
                             tiedot/kirjaa-pistemainen-havainto!
                             :kirjaa-valikohtainen-havainto-fn
                             tiedot/kirjaa-valikohtainen-havainto!
                             :jatkuvat-havainnot
                             (into #{} (filterv @s/havainnot (keys @s/havainnot)))}])