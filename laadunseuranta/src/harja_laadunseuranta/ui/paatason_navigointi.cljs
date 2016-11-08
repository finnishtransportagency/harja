(ns harja-laadunseuranta.ui.paatason-navigointi
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.ui.ilmoitukset :as ilmoitukset]
            [harja-laadunseuranta.tiedot.paatason-navigointi :as paatason-navigointi-tiedot]
            [harja-laadunseuranta.tiedot.reitintallennus :as reitintallennus]
            [harja-laadunseuranta.tiedot.sovellus :as s])
  (:require-macros
    [harja-laadunseuranta.macros :as m]
    [cljs.core.async.macros :refer [go go-loop]]
    [devcards.core :as dc :refer [defcard deftest]]))

(defn- toggle-painike [{:keys [otsikko ikoni tyyppi click-fn] :as tiedot}]
  (fn []
      [:div.toggle-valintapainike {:on-click #(click-fn otsikko)}
       [:div.toggle-valintapainike-ikoni
        (case tyyppi
          :piste [:img.toggle-piste {:src (kuvat/havainto-ikoni "ikoni_pistemainen")}]
          :vali [:img.toggle-vali {:src (kuvat/havainto-ikoni "ikoni_alue")}])
        (when ikoni
          [:img.toggle-ikoni {:src (kuvat/havainto-ikoni ikoni)}])]
       [:div.toggle-valintapainike-otsikko
        otsikko]]))

(defn- paatason-navigointikomponentti [{:keys [valilehdet
                                               kirjaa-pistemainen-havainto-fn] :as tiedot}]
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
            (doall (for [{:keys [nimi ikoni tyyppi]} (sort-by :nimi sisalto)]
                     ^{:key nimi}
                     [toggle-painike {:nimi nimi :ikoni ikoni :tyyppi tyyppi
                                      :click-fn (case tyyppi
                                                  :piste kirjaa-pistemainen-havainto-fn
                                                  :vali #(.log js/console "TODO Väli painettu"))}])))]]
        [:footer]]])))

(defn paatason-navigointi []
  [paatason-navigointikomponentti {:valilehdet paatason-navigointi-tiedot/oletusvalilehdet
                                   :kirjaa-pistemainen-havainto-fn
                                   (fn [otsikko]
                                     (ilmoitukset/ilmoita
                                       (str "Pistemäinen havainto kirjattu: " otsikko))
                                     ;; TODO Selvitä miten tallennus tehdään
                                     #_(reitintallennus/kirjaa-kertakirjaus
                                       @s/idxdb
                                       {:kayttajanimi @s/kayttajanimi
                                        :tr-osoite (utils/unreactive-deref s/tr-osoite)
                                        :tr-alku @s/tr-alku
                                        :tr-loppu @s/tr-loppu
                                        :aikaleima (l/local-now)
                                        :havainnot (erota-havainnot @s/havainnot)
                                        :pikavalinnan-kuvaus (@s/vakiohavaintojen-kuvaukset @s/pikavalinta)
                                        :pikavalinta @s/pikavalinta
                                        :mittaukset {}
                                        :kitkan-keskiarvo @s/kitkan-keskiarvo
                                        :lumisuus @s/lumimaara
                                        :tasaisuus @s/tasaisuus
                                        :sijainti (:nykyinen (utils/unreactive-deref s/sijainti))
                                        :laadunalitus? false
                                        :kuvaus ""
                                        :kuva nil}
                                       @s/tarkastusajo-id))}])