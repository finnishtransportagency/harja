(ns harja-laadunseuranta.ui.paanavigointi
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.ui.nappaimisto :as nappaimisto]
            [harja-laadunseuranta.tiedot.paanavigointi :as tiedot]
            [cljs-time.local :as l]
            [harja-laadunseuranta.ui.napit :refer [nappi]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [harja-laadunseuranta.tiedot.dom :as dom])
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
        kayta-hampurilaisvalikkoa? (< (dom/leveys) 950)
        valilehdet-nakyvissa? (atom true)
        valittu-valilehti (atom (:avain (first valilehdet)))
        valitse-valilehti! (fn [uusi-valinta]
                             (.log js/console "Vaihdetaan välilehti: " (str uusi-valinta))
                             (reset! valittu-valilehti uusi-valinta)
                             (when kayta-hampurilaisvalikkoa?
                               (swap! valilehdet-nakyvissa? not)))
        piilotusnappi-klikattu (fn []
                                 (swap! paanavigointi-nakyvissa? not))]
    (fn [{:keys [valilehdet kirjaa-pistemainen-havainto-fn
                 kirjaa-valikohtainen-havainto-fn
                 jatkuvat-havainnot
                 nykyinen-mittaustyyppi] :as tiedot}]
      (let [jatkuvia-havaintoja-paalla? (not (empty? jatkuvat-havainnot))
            mittaus-paalla? (some? nykyinen-mittaustyyppi)
            havainto (when mittaus-paalla?
                       (first (filter #(= (get-in % [:mittaus :tyyppi])
                                          nykyinen-mittaustyyppi)
                                      (mapcat :sisalto valilehdet))))]
        [:div {:class (str "paanavigointi-container "
                           (if @paanavigointi-nakyvissa?
                             "paanavigointi-container-nakyvissa"
                             "paanavigointi-container-piilossa"))}
         [:div.nayttonappi {:on-click piilotusnappi-klikattu}]
         [:div.navigointilaatikko-container
          [:div.navigointilaatikko
           [:div.piilotusnappi {:on-click piilotusnappi-klikattu}]

           [:header
            [:div.hampurilaisvalikko
             (when kayta-hampurilaisvalikkoa?
               [:img.hampurilaisvalikko-ikoni
               {:src kuvat/+hampurilaisvalikko+
                :on-click #(swap! valilehdet-nakyvissa? not)}])]
            (when @valilehdet-nakyvissa?
              [:ul.valilehtilista
              (doall
                (for [{:keys [avain] :as valilehti} valilehdet]
                  ^{:key avain}
                  [:li {:class (str "valilehti "
                                    (when (= avain
                                             @valittu-valilehti)
                                      "valilehti-valittu"))
                        :on-click #(valitse-valilehti! avain)}
                   (:nimi valilehti)]))])]
           [:div.sisalto
            [:div.valintapainikkeet
             (let [{:keys [sisalto] :as valittu-valilehti}
                   (first (filter
                            #(= (:avain %) @valittu-valilehti)
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
                                                            jatkuvia-havaintoja-paalla?
                                                            (not (jatkuvat-havainnot (:avain havainto)))
                                                            mittaus-paalla?
                                                            (:vaatii-nappaimiston? havainto)))})])))]]
           [:footer
            [:div.footer-vasen
             [nappi "Vapauta kaikki" {:on-click (fn [])
                                      :luokat-str "nappi-toissijainen"}]]
            [:div.footer-oikea
             [nappi "Avaa lomake" {:on-click (fn [])
                                      :luokat-str "nappi-ensisijainen"}]]]]]

         (when mittaus-paalla?
           [nappaimisto/nappaimisto havainto])]))))

(defn paanavigointi []
  [paanavigointikomponentti {:valilehdet tiedot/oletusvalilehdet
                             :kirjaa-pistemainen-havainto-fn
                             tiedot/pistemainen-havainto-painettu!
                             :kirjaa-valikohtainen-havainto-fn
                             tiedot/valikohtainen-havainto-painettu!
                             :aseta-mittaus-paalle s/aseta-mittaus-paalle!
                             :jatkuvat-havainnot @s/jatkuvat-havainnot
                             :nykyinen-mittaustyyppi @s/mittaustyyppi}])