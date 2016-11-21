(ns harja-laadunseuranta.ui.paanavigointi
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.ui.nappaimisto :as nappaimisto]
            [harja-laadunseuranta.tiedot.paanavigointi :as tiedot]
            [cljs-time.local :as l]
            [harja-laadunseuranta.ui.ikonit :as ikonit]
            [harja-laadunseuranta.ui.napit :refer [nappi]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [clojure.set :as set]
            [harja-laadunseuranta.tiedot.dom :as dom]
            [harja-laadunseuranta.utils :as utils]
            [reagent.core :as r]
            [harja-laadunseuranta.asiakas.tapahtumat :as tapahtumat])
  (:require-macros
    [harja-laadunseuranta.macros :as m]
    [cljs.core.async.macros :refer [go go-loop]]
    [devcards.core :as dc :refer [defcard deftest]]))

(def +header-reuna-padding+ 80)
(def +valilehti-perusleveys+ 50) ;; Kun välilehti on tyhjä
(def +kirjain-leveys 9.3) ;; kirjaimen leveys keskimäärin
(defn- maarittele-valilehtien-maara-per-ryhma [header-leveys valilehdet]
  ;; Jaetaan välilehdet ryhmiin. Tarkoituksena etsiä sellainen jako, jossa
  ;; välilehtien määrä / ryhmä on mahdollisimman suuri niin, ettei välilehtien
  ;; leveys ylitä containerin leveyttä.
  ;; HUOMAA, että tämä ei ole pikselintarkka lasku, mutta selkeästi riittävä
  (let [valilehtien-nimet (map #(utils/ilman-tavutusta (:nimi %)) valilehdet)
        valilehtien-leveydet (map
                               #(+ +valilehti-perusleveys+ (* +kirjain-leveys (count %)))
                               valilehtien-nimet)]

    (loop [jako 1]
      (let [ryhmat (partition-all jako valilehtien-leveydet)
            ryhmien-yhteysleveys (map #(reduce + 0 %) ryhmat)
            ryhmat-mahtuvat-containeriin? (every?
                                            #(< % (- header-leveys +header-reuna-padding+))
                                            ryhmien-yhteysleveys)]
        (if ryhmat-mahtuvat-containeriin?
          ;; Voidaan kasvattaa jakoa edelleen
          (recur (+ jako 1))
          ;; Edellinen jako mahtui eli se on vastaus
          (- jako 1))))))

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

(defn- paanavigointi-header [{:keys [valilehdet valittu-valilehti] :as tiedot}]
  (let [valilehtia-per-ryhma (atom 0)
        paivita-valilehtien-maara-per-ryhma
        (fn [this]
          (reset! valilehtia-per-ryhma
                  (maarittele-valilehtien-maara-per-ryhma (.-width (.getBoundingClientRect this))
                                                          valilehdet)))
        valitse-valilehti! (fn [uusi-valinta]
                             (reset! valittu-valilehti uusi-valinta))
        lopeta-leveyskuuntelu (atom nil)]

    (r/create-class
      {:component-did-mount (fn [this]
                              (paivita-valilehtien-maara-per-ryhma (reagent/dom-node this))
                              (let [lopeta-kuuntelu-fn
                                    (tapahtumat/kuuntele! :window-resize
                                                          #(paivita-valilehtien-maara-per-ryhma
                                                            (reagent/dom-node this)))]
                                (reset! lopeta-leveyskuuntelu lopeta-kuuntelu-fn)))
       :component-will-unmount (fn [_]
                                 (@lopeta-leveyskuuntelu))
       :reagent-render
       (fn [{:keys [kayta-hampurilaisvalikkoa? togglaa-valilehtien-nakyvyys
                    valilehdet-nakyvissa? valilehdet jatkuvat-havainnot
                    valittu-valilehti valittu-valilehtiryhma]}]
         (let [valilehtiryhmat (partition-all @valilehtia-per-ryhma valilehdet)
               valitun-valilehtiryhman-valilehdet (nth valilehtiryhmat @valittu-valilehtiryhma)]
           [:header {:class (when-not kayta-hampurilaisvalikkoa? "hampurilaisvalikko-ei-kaytossa")}
            (when kayta-hampurilaisvalikkoa?
              [:div.hampurilaisvalikko
               [:img.hampurilaisvalikko-ikoni
                {:src kuvat/+hampurilaisvalikko+
                 :on-click togglaa-valilehtien-nakyvyys}]])

            (when-not kayta-hampurilaisvalikkoa?
              [:div
               [:div.selaa-valilehtiryhmia.selaa-valilehtiryhmia-oikealle]
               [:div.selaa-valilehtiryhmia.selaa-valilehtiryhmia-vasemmalle]])

            (when @valilehdet-nakyvissa?
              [:ul.valilehtilista
               (doall
                 (for [{:keys [avain sisalto] :as valilehti} (if kayta-hampurilaisvalikkoa?
                                                               valilehdet
                                                               valitun-valilehtiryhman-valilehdet)]
                   (let [valilehden-jatkuvat-havainnot
                         (set/intersection (into #{} (map :avain sisalto))
                                           jatkuvat-havainnot)]
                     ^{:key avain}
                     [:li {:class (str "valilehti "
                                       (when (= avain
                                                @valittu-valilehti)
                                         "valilehti-valittu"))
                           :on-click #(valitse-valilehti! avain)}
                      [:span.valilehti-nimi (:nimi valilehti)]
                      [:span.valilehti-havainnot (when-not (empty? valilehden-jatkuvat-havainnot)
                                                   (str "(" (count valilehden-jatkuvat-havainnot) ")"))]])))])]))})))

(defn- paanavigointi-sisalto [{:keys [valilehdet kirjaa-pistemainen-havainto-fn
                                      kirjaa-valikohtainen-havainto-fn valittu-valilehti
                                      jatkuvat-havainnot nykyinen-mittaustyyppi] :as tiedot}]
  (let [mittaus-paalla? (some? nykyinen-mittaustyyppi)
        jatkuvia-havaintoja-paalla? (not (empty? jatkuvat-havainnot))]
    [:div.sisalto
     [:div.valintapainikkeet
      (let [{:keys [sisalto]} (first (filter
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
                                                     (:vaatii-nappaimiston? havainto)))})])))]]))

(defn- paanavigointi-footer [{:keys [vapauta-kaikki-painettu havaintolomake-painettu] :as tiedot}]
  [:footer
   [:div.footer-vasen
    [nappi "Vapauta kaikki" {:on-click vapauta-kaikki-painettu
                             :ikoni (ikonit/livicon-arrow-up)
                             :luokat-str "nappi-toissijainen"}]]
   [:div.footer-oikea
    [nappi "Avaa lomake" {:on-click havaintolomake-painettu
                          :ikoni (ikonit/livicon-pen)
                          :luokat-str "nappi-ensisijainen"}]]])

(defn- paanavigointikomponentti [{:keys [valilehdet paanavigointi-nakyvissa? :valittu-valilehtiryhma
                                         valilehdet-nakyvissa? valittu-valilehti] :as tiedot}]
  (let [togglaa-paanavigoinnin-nakyvyys (fn []
                                          (swap! paanavigointi-nakyvissa? not))
        togglaa-valilehtien-nakyvyys (fn []
                                       (swap! valilehdet-nakyvissa? not))]

    (reset! valittu-valilehti (:avain (first valilehdet)))

    (fn [{:keys [valilehdet kirjaa-pistemainen-havainto-fn
                 kirjaa-valikohtainen-havainto-fn
                 jatkuvat-havainnot nykyinen-mittaustyyppi
                 vapauta-kaikki-painettu havaintolomake-painettu] :as tiedot}]
      (let [mittaus-paalla? (some? nykyinen-mittaustyyppi)
            kayta-hampurilaisvalikkoa? (< @dom/leveys 950)
            mitattava-havainto (when mittaus-paalla?
                                 (first (filter #(= (get-in % [:mittaus :tyyppi])
                                                    nykyinen-mittaustyyppi)
                                                (mapcat :sisalto valilehdet))))
            nayta-valilehdet-tarvittaessa! (fn []
                                             ;; Näytä välilehdet jos eivät näkyvissä
                                             ;; ja ei käytetäkään hampurilaisvalikkoa
                                             (if (and (not @valilehdet-nakyvissa?)
                                                      (not kayta-hampurilaisvalikkoa?))
                                               (togglaa-valilehtien-nakyvyys)))]

        (nayta-valilehdet-tarvittaessa!)

        [:div {:class (str "paanavigointi-container "
                           (if @paanavigointi-nakyvissa?
                             "paanavigointi-container-nakyvissa"
                             "paanavigointi-container-piilossa"))}
         [:div.nayttonappi {:on-click togglaa-paanavigoinnin-nakyvyys}
          [:img {:src kuvat/+nuoli-avaa+}]]
         [:div.navigointilaatikko-container
          [:div.navigointilaatikko
           [:div.piilotusnappi {:on-click togglaa-paanavigoinnin-nakyvyys}
            [:img {:src kuvat/+nuoli-sulje+}]]

           [paanavigointi-header {:kayta-hampurilaisvalikkoa? kayta-hampurilaisvalikkoa?
                                  :togglaa-valilehtien-nakyvyys togglaa-valilehtien-nakyvyys
                                  :valilehdet-nakyvissa? valilehdet-nakyvissa?
                                  :valilehdet valilehdet
                                  :valittu-valilehtiryhma valittu-valilehtiryhma
                                  :jatkuvat-havainnot jatkuvat-havainnot
                                  :valittu-valilehti valittu-valilehti}]

           [paanavigointi-sisalto {:valilehdet valilehdet
                                   :valittu-valilehti valittu-valilehti
                                   :nykyinen-mittaustyyppi nykyinen-mittaustyyppi
                                   :kirjaa-pistemainen-havainto-fn kirjaa-pistemainen-havainto-fn
                                   :kirjaa-valikohtainen-havainto-fn kirjaa-valikohtainen-havainto-fn
                                   :jatkuvat-havainnot jatkuvat-havainnot}]
           [paanavigointi-footer {:havaintolomake-painettu havaintolomake-painettu
                                  :vapauta-kaikki-painettu vapauta-kaikki-painettu}]]]

         (when mittaus-paalla?
           [nappaimisto/nappaimisto mitattava-havainto])]))))

(defn paanavigointi []
  [paanavigointikomponentti {:valilehdet tiedot/oletusvalilehdet
                             :paanavigointi-nakyvissa? s/nayta-paanavigointi?
                             :valilehdet-nakyvissa? s/nayta-paanavigointi-valilehdet?
                             :valittu-valilehti s/paanavigoinnin-valittu-valilehti
                             :kirjaa-pistemainen-havainto-fn
                             tiedot/pistemainen-havainto-painettu!
                             :kirjaa-valikohtainen-havainto-fn
                             tiedot/valikohtainen-havainto-painettu!
                             :aseta-mittaus-paalle s/aseta-mittaus-paalle!
                             :jatkuvat-havainnot @s/jatkuvat-havainnot
                             :nykyinen-mittaustyyppi @s/mittaustyyppi
                             :valittu-valilehtiryhma s/paanavigoinnin-valittu-valilehtiryhma
                             :havaintolomake-painettu tiedot/avaa-havaintolomake!
                             :vapauta-kaikki-painettu s/poista-kaikki-jatkuvat-havainnot!}])