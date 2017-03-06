(ns harja-laadunseuranta.ui.paanavigointi
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :refer [<! >! timeout]]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.ui.nappaimisto :as nappaimisto]
            [harja-laadunseuranta.tiedot.paanavigointi :as tiedot]
            [cljs-time.local :as l]
            [harja-laadunseuranta.ui.yleiset.napit :refer [nappi]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [clojure.set :as set]
            [harja-laadunseuranta.ui.yleiset.dom :as dom]
            [harja-laadunseuranta.utils :as utils]
            [reagent.core :as r]
            [harja-laadunseuranta.asiakas.tapahtumat :as tapahtumat])
  (:require-macros
    [harja-laadunseuranta.macros :as m]
    [cljs.core.async.macros :refer [go go-loop]]
    [devcards.core :as dc :refer [defcard deftest]]))

(def edellinen-header-leveys (atom nil))
(def +header-reuna-padding+ 80)
(def +valilehti-perusleveys+ 60) ;; Kun välilehti on tyhjä
(def +kirjain-leveys+ 9.5) ;; kirjaimen leveys keskimäärin
(defn- maarittele-valilehtien-maara-per-ryhma [header-leveys valilehdet]
  ;; Jaetaan välilehdet ryhmiin. Tarkoituksena etsiä sellainen jako, jossa
  ;; välilehtien määrä / ryhmä on mahdollisimman suuri niin, ettei välilehtien
  ;; leveys ylitä containerin leveyttä.
  ;; HUOMAA että tämä ei ole pikselintarkka lasku, vaan "close enough" ratkaisu, joka toimii
  (if (= header-leveys 0)
    (count valilehdet)
    (let [valilehtien-nimet (map #(utils/ilman-tavutusta (:nimi %)) valilehdet)
          valilehtien-leveydet (map
                                 #(+ +valilehti-perusleveys+ (* +kirjain-leveys+ (count %)))
                                 valilehtien-nimet)]

      (loop [jako 1]
        (let [ryhmat (partition-all jako valilehtien-leveydet)
              ryhmien-yhteysleveys (map #(reduce + 0 %) ryhmat)
              ryhmat-mahtuvat-containeriin? (every?
                                              #(< % (- header-leveys +header-reuna-padding+))
                                              ryhmien-yhteysleveys)]
          (if ryhmat-mahtuvat-containeriin?
            ;; Voidaan kasvattaa jakoa edelleen
            (if (< jako (count valilehdet))
              (recur (+ jako 1))
              (count valilehdet))
            ;; Edellinen jako mahtui eli se on vastaus
            (- jako 1)))))))

;; TODO REFACTOR, yllä olevassa voisi ehkä formuloida
;; somella alaspäin laskevasta sarjasta
;; (some mahtuuko-fn (range (count homma) 1))

(defn- toggle-painike [_]
  (fn [{:keys [nimi ikoni avain tyyppi ikoni-lahde pienempi-fontti?
               click-fn jatkuvat-havainnot disabloitu?] :as tiedot}]
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
        :piste [:img.toggle-piste {:src kuvat/+havainto-piste+}]
        :vali [:img.toggle-vali {:src kuvat/+havainto-vali+}])
      (when ikoni
        [kuvat/svg-sprite ikoni "toggle-ikoni"])]
     [:div {:class (str "toggle-valintapainike-otsikko "
                        (when pienempi-fontti?
                          "toggle-valintapainike-otsikko-pienempi"))}
      nimi]]))

(defn- ryhmittele-valilehdet-uudelleen-tarvittaessa! [{:keys [header-komponentti
                                                              valilehdet valilehtiryhmat
                                                              valittu-valilehtiryhma]}]
  (when header-komponentti
    (let [header-leveys (.-width (.getBoundingClientRect header-komponentti))]
      (when (not= header-leveys @edellinen-header-leveys)
        (.log js/console "Header leveys muuttui " @edellinen-header-leveys " -> " header-leveys ", ryhmitellään tabit uudelleen.")
        (let [valilehtia-per-ryhma
              (maarittele-valilehtien-maara-per-ryhma header-leveys
                                                      valilehdet)]
          (when (> valilehtia-per-ryhma 0)
            (reset! valilehtiryhmat (partition-all valilehtia-per-ryhma valilehdet))
            ;; Ryhmittely päivitetty, varmistetaan, että nykyinen valinta on edelleen taulukon sisällä
            (when (> @valittu-valilehtiryhma (- (count @valilehtiryhmat) 1))
              (reset! valittu-valilehtiryhma (- (count @valilehtiryhmat) 1))))
          (reset! edellinen-header-leveys header-leveys))))))

(defn- valilehtielementit [{:keys [valittu-valilehti]}]
  ;; Hampurilaisvalikon kanssa näytetään aina vain aktiivinen välilehti
  ;; Muuten näytetään valitun välilehtiryhmän välilehdet
  (let [valitse-valilehti! (fn [uusi-valinta]
                             (reset! valittu-valilehti uusi-valinta))]
    (fn [{:keys [valilehdet kayta-hampurilaisvalikkoa?
                 valilehdet-nakyvissa? valitun-valilehtiryhman-valilehdet
                 jatkuvat-havainnot valittu-valilehti]}]
      (when @valilehdet-nakyvissa?
        [:ul.valilehtilista
         (doall
           (for [{:keys [avain sisalto] :as valilehti} (if kayta-hampurilaisvalikkoa?
                                                         (filter #(= (:avain %)
                                                                     @valittu-valilehti)
                                                                 valilehdet)
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
                                             (count valilehden-jatkuvat-havainnot))]])))]))))

(defn- paanavigointi-header-valilehtiryhmilla
  [{:keys [valittu-valilehtiryhma]}]
  (let [selauspainike-painettu! (fn [suunta]
                                  (case suunta
                                    :oikea (when (< @valittu-valilehtiryhma 3)
                                             (reset! valittu-valilehtiryhma (+ @valittu-valilehtiryhma 1)))
                                    :vasen (when (> @valittu-valilehtiryhma 0)
                                             (reset! valittu-valilehtiryhma (- @valittu-valilehtiryhma 1)))))]
    (fn [{:keys [valittu-valilehtiryhma valilehtiryhmat valilehdet
                 valilehdet-nakyvissa? valitun-valilehtiryhman-valilehdet
                 jatkuvat-havainnot valittu-valilehti]}]
      [:div
       ;; Välilehtiryhmien pallerot
       [:div.valilehtiryhmien-pallerot
        (doall
          (map-indexed
            (fn [index _]
              ^{:key index}
              [:div {:class (str "valilehtiryhma-pallero "
                                 (when (= index @valittu-valilehtiryhma)
                                   "valilehtiryhma-pallero-aktiivinen"))}])
            @valilehtiryhmat))]


       ;; Välilehtien selaamiseen tarkoitetut nuolinapit
       [:div
        (let [disabloitu? (>= @valittu-valilehtiryhma (- (count @valilehtiryhmat) 1))]
          [:div
           {:class (str "selaa-valilehtiryhmia selaa-valilehtiryhmia-oikealle "
                        (when disabloitu? "selaa-valilehtiryhmia-disabled"))
            :on-click #(when-not disabloitu?
                         (selauspainike-painettu! :oikea))}
           [:img {:src kuvat/+avausnuoli+}]])
        (let [disabloitu? (<= @valittu-valilehtiryhma 0)]
          [:div
           {:class (str "selaa-valilehtiryhmia selaa-valilehtiryhmia-vasemmalle "
                        (when disabloitu? "selaa-valilehtiryhmia-disabled"))
            :on-click #(when-not disabloitu?
                         (selauspainike-painettu! :vasen))}
           [:img {:src kuvat/+avausnuoli+}]])]

       [valilehtielementit {:valilehet valilehdet
                            :kayta-hampurilaisvalikkoa? false
                            :valilehdet-nakyvissa? valilehdet-nakyvissa?
                            :valitun-valilehtiryhman-valilehdet valitun-valilehtiryhman-valilehdet
                            :jatkuvat-havainnot jatkuvat-havainnot
                            :valittu-valilehti valittu-valilehti}]])))

(defn- paanavigointi-header-hampurilaisvalikolla
  [_]
  (let [hampurilaisvalikon-listan-max-korkeus (atom 200)
        maarita-hampurilaisvalikon-listan-max-korkeus!
        (fn [node]
          (when node
            (reset! hampurilaisvalikon-listan-max-korkeus
                    (dom/elementin-etaisyys-viewportin-alareunaan node))))]
    (fn [{:keys [hampurilaisvalikko-painettu hampurilaisvalikon-lista-nakyvissa?
                 valilehdet-nakyvissa?
                 valilehdet header-dom-node valittu-valilehti valitun-valilehtiryhman-valilehdet
                 hampurilaisvalikon-lista-item-painettu jatkuvat-havainnot]}]
      (maarita-hampurilaisvalikon-listan-max-korkeus! @header-dom-node)
      [:div
       [:div.hampurilaisvalikko
        [:img.hampurilaisvalikko-ikoni
         {:src kuvat/+hampurilaisvalikko+
          :on-click hampurilaisvalikko-painettu}]
        (when @hampurilaisvalikon-lista-nakyvissa?
          [:div.lista.hampurilaisvalikon-lista
           [:ul {:style {:max-height @hampurilaisvalikon-listan-max-korkeus}}
            (doall
              (for [{:keys [avain nimi] :as valilehti} valilehdet]
                ^{:key avain}
                [:li {:class (when (= avain @valittu-valilehti)
                               "aktiivinen-valinta")
                      :on-click (partial hampurilaisvalikon-lista-item-painettu avain)}
                 nimi]))]])]

       [valilehtielementit {:valilehdet valilehdet
                            :kayta-hampurilaisvalikkoa? true
                            :valilehdet-nakyvissa? valilehdet-nakyvissa?
                            :valitun-valilehtiryhman-valilehdet valitun-valilehtiryhman-valilehdet
                            :jatkuvat-havainnot jatkuvat-havainnot
                            :valittu-valilehti valittu-valilehti}]])))

(defn- paanavigointi-header [{:keys [valilehdet
                                     hampurilaisvalikon-lista-nakyvissa?
                                     hampurilaisvalikko-painettu body-click
                                     hampurilaisvalikon-lista-item-painettu
                                     valittu-valilehtiryhma valilehtiryhmat] :as tiedot}]
  (let [dom-node (atom nil)
        body-click-kuuntelija (atom nil)
        tarkkaile-leveytta? (atom false)]

    (r/create-class
      {:component-did-mount (fn [this]
                              (reset! dom-node (reagent/dom-node this))
                              (reset! body-click-kuuntelija
                                      (tapahtumat/kuuntele! :body-click
                                                            #(when-not (dom/sisalla? @dom-node (:tapahtuma %))
                                                               (body-click %))))
                              (reset! tarkkaile-leveytta? true)
                              (go-loop []
                                ;; Jos joskus löytyy elegantti tapa kutsua välilehtien ryhmittelyä uudelleen
                                ;; kun headerin leveys muuttuu mistä tahansa syystä, niin tämän voi
                                ;; poistaa. Toistaiseksi nyt näin.
                                (when @tarkkaile-leveytta?
                                  (ryhmittele-valilehdet-uudelleen-tarvittaessa! {:header-komponentti @dom-node
                                                                                  :valilehdet valilehdet
                                                                                  :valilehtiryhmat valilehtiryhmat
                                                                                  :valittu-valilehtiryhma valittu-valilehtiryhma})
                                  (<! (timeout 1000))
                                  (recur))))
       :component-will-unmount (fn [_]
                                 (@body-click-kuuntelija)
                                 (reset! tarkkaile-leveytta? false)
                                 (reset! dom-node nil))
       :reagent-render
       (fn [{:keys [kayta-hampurilaisvalikkoa?
                    valilehdet-nakyvissa? valilehdet jatkuvat-havainnot
                    valittu-valilehti valittu-valilehtiryhma]}]
         (ryhmittele-valilehdet-uudelleen-tarvittaessa! {:header-komponentti @dom-node
                                                         :valilehdet valilehdet
                                                         :valilehtiryhmat valilehtiryhmat
                                                         :valittu-valilehtiryhma valittu-valilehtiryhma})

         (let [valitun-valilehtiryhman-valilehdet (when-not (empty? @valilehtiryhmat)
                                                    (nth @valilehtiryhmat @valittu-valilehtiryhma))]
           [:header {:class (when-not kayta-hampurilaisvalikkoa? "hampurilaisvalikko-ei-kaytossa")}

            (if kayta-hampurilaisvalikkoa?
              [paanavigointi-header-hampurilaisvalikolla
               {:hampurilaisvalikko-painettu hampurilaisvalikko-painettu
                :hampurilaisvalikon-lista-nakyvissa? hampurilaisvalikon-lista-nakyvissa?
                :valilehdet valilehdet
                :valilehdet-nakyvissa? valilehdet-nakyvissa?
                :valitun-valilehtiryhman-valilehdet valitun-valilehtiryhman-valilehdet
                :jatkuvat-havainnot jatkuvat-havainnot
                :valittu-valilehti valittu-valilehti
                :hampurilaisvalikon-lista-item-painettu hampurilaisvalikon-lista-item-painettu
                :header-dom-node dom-node}]
              [paanavigointi-header-valilehtiryhmilla
               {:valittu-valilehtiryhma valittu-valilehtiryhma
                :valilehtiryhmat valilehtiryhmat
                :valilehet valilehdet
                :valilehdet-nakyvissa? valilehdet-nakyvissa?
                :valitun-valilehtiryhman-valilehdet valitun-valilehtiryhman-valilehdet
                :jatkuvat-havainnot jatkuvat-havainnot
                :valittu-valilehti valittu-valilehti}])]))})))

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
    [nappi (if (< @dom/leveys dom/+leveys-tabletti+)
             "Vapauta"
             "Vapauta kaikki")
     {:on-click vapauta-kaikki-painettu
      :ikoni (kuvat/svg-sprite "nuoli-ylos-24")
      :luokat-str "nappi-toissijainen"}]]
   [:div.footer-oikea
    [nappi (if (< @dom/leveys dom/+leveys-tabletti+)
             "Lomake"
             "Avaa lomake")
     {:on-click havaintolomake-painettu
      :ikoni (kuvat/svg-sprite "kyna-24")
      :luokat-str "nappi-ensisijainen"}]]])

(defn- paanavigointikomponentti [{:keys [valilehdet paanavigointi-nakyvissa?
                                         hampurilaisvalikon-lista-nakyvissa?
                                         body-click
                                         hampurilaisvalikon-lista-item-painettu
                                         valittu-valilehtiryhma valilehdet-nakyvissa?
                                         valittu-valilehti] :as tiedot}]
  (let [togglaa-valilehtien-nakyvyys! (fn []
                                        (swap! valilehdet-nakyvissa? not))
        resetoi-valittu-valilehti-tarvittaessa! (fn []
                                                  ;; Resetoi valittu välilehti jos tyhjä tai ei enää valittavissa
                                                  (when (or (nil? @valittu-valilehti)
                                                            (not-any? #(= (:avain %) @valittu-valilehti)
                                                                      valilehdet))
                                                    (reset! valittu-valilehti (:avain (first valilehdet)))))]
    (resetoi-valittu-valilehti-tarvittaessa!)

    (fn [{:keys [valilehdet kirjaa-pistemainen-havainto-fn
                 kirjaa-valikohtainen-havainto-fn valilehtiryhmat
                 jatkuvat-havainnot nykyinen-mittaustyyppi hampurilaisvalikko-painettu
                 vapauta-kaikki-painettu havaintolomake-painettu] :as tiedot}]
      (let [mittaus-paalla? (some? nykyinen-mittaustyyppi)
            kayta-hampurilaisvalikkoa? (< @dom/leveys dom/+leveys-tabletti+)
            mitattava-havainto (when mittaus-paalla?
                                 (first (filter #(= (get-in % [:mittaus :tyyppi])
                                                    nykyinen-mittaustyyppi)
                                                (mapcat :sisalto valilehdet))))
            nayta-valilehdet-tarvittaessa! (fn []
                                             ;; Näytä välilehdet jos eivät näkyvissä
                                             ;; ja ei käytetäkään hampurilaisvalikkoa
                                             (if (and (not @valilehdet-nakyvissa?)
                                                      (not kayta-hampurilaisvalikkoa?))
                                               (togglaa-valilehtien-nakyvyys!)))]

        (nayta-valilehdet-tarvittaessa!)

        [:div {:class (str "paanavigointi-container "
                           (if @paanavigointi-nakyvissa?
                             "paanavigointi-container-nakyvissa"
                             "paanavigointi-container-piilossa"))}
         [:div.navigointilaatikko-container
          [:div.navigointilaatikko

           [paanavigointi-header {:kayta-hampurilaisvalikkoa? kayta-hampurilaisvalikkoa?
                                  :hampurilaisvalikko-painettu hampurilaisvalikko-painettu
                                  :hampurilaisvalikon-lista-item-painettu hampurilaisvalikon-lista-item-painettu
                                  :valilehtiryhmat valilehtiryhmat
                                  :valilehdet-nakyvissa? valilehdet-nakyvissa?
                                  :valilehdet valilehdet
                                  :body-click body-click
                                  :hampurilaisvalikon-lista-nakyvissa? hampurilaisvalikon-lista-nakyvissa?
                                  :valittu-valilehtiryhma valittu-valilehtiryhma
                                  :jatkuvat-havainnot jatkuvat-havainnot
                                  :valittu-valilehti valittu-valilehti}]

           [paanavigointi-sisalto {:valilehdet valilehdet
                                   :valittu-valilehti valittu-valilehti
                                   :nykyinen-mittaustyyppi nykyinen-mittaustyyppi
                                   :kirjaa-pistemainen-havainto-fn kirjaa-pistemainen-havainto-fn
                                   :kirjaa-valikohtainen-havainto-fn kirjaa-valikohtainen-havainto-fn
                                   :jatkuvat-havainnot jatkuvat-havainnot}]
           [paanavigointi-footer {:paanavigointi-nakyvissa? paanavigointi-nakyvissa?
                                  :havaintolomake-painettu havaintolomake-painettu
                                  :vapauta-kaikki-painettu vapauta-kaikki-painettu}]]]

         (when mittaus-paalla?
           [nappaimisto/nappaimisto mitattava-havainto])]))))

(defn paanavigointi []
  [paanavigointikomponentti {:valilehdet (tiedot/kayttajaroolin-mukaiset-valilehdet
                                           tiedot/oletusvalilehdet
                                           @s/kayttaja)
                             :hampurilaisvalikon-lista-nakyvissa? s/paanavigoinnin-hampurilaisvalikon-lista-nakyvissa?
                             :hampurilaisvalikko-painettu tiedot/hampurilaisvalikko-painettu!
                             :body-click tiedot/body-click
                             :hampurilaisvalikon-lista-item-painettu tiedot/hampurilaisvalikon-lista-item-valittu!
                             :valilehtiryhmat s/paanavigoinnin-valilehtiryhmat
                             :paanavigointi-nakyvissa? s/nayta-paanavigointi?
                             :valilehdet-nakyvissa? s/nayta-paanavigointi-valilehdet?
                             :valittu-valilehti s/paanavigoinnin-valittu-valilehti
                             :kirjaa-pistemainen-havainto-fn tiedot/pistemainen-havainto-painettu!
                             :kirjaa-valikohtainen-havainto-fn tiedot/valikohtainen-havainto-painettu!
                             :aseta-mittaus-paalle s/aseta-mittaus-paalle!
                             :jatkuvat-havainnot @s/jatkuvat-havainnot
                             :nykyinen-mittaustyyppi @s/mittaustyyppi
                             :valittu-valilehtiryhma s/paanavigoinnin-valittu-valilehtiryhma
                             :havaintolomake-painettu tiedot/avaa-havaintolomake!
                             :vapauta-kaikki-painettu s/poista-kaikki-jatkuvat-havainnot!}])
