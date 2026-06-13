(ns harja.views.main
  "Harjan päänäkymä"
  (:require [clojure.string :as str]
            [harja.ui.bootstrap :as bs]
            [reagent.core :refer [atom]]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [linkki staattinen-linkki-uuteen-valilehteen ajax-loader livi-pudotusvalikko]]
            [harja.ui.dom :as dom]
            [harja.ui.modal :as modal]
            [harja.ui.palaute :as palaute]
            [harja.ui.viesti :refer [toast-viesti-container]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.navigaatio.reitit :as reitit]
            [harja.loki :refer [log logt]]
            [harja.tiedot.hairioilmoitukset :as hairiotiedot]
            [harja.views.murupolku :as murupolku]
            [harja.views.haku :as haku]
            [cljs.core.async :refer [put! close! chan timeout]]

            [harja.views.urakat :as urakat]
            [harja.views.info :as info]
            [harja.views.urakkatilanne.kojelauta :as kojelauta]
            [harja.views.raportit :as raportit]
            [harja.views.tilannekuva.tilannekuva :as tilannekuva]
            [harja.views.ilmoitukset.tieliikenneilmoitukset :as ilmoitukset]
            [harja.views.kartta :as kartta]
            [harja.views.hallinta :as hallinta]
            [harja.views.about :as about]
            [harja.views.tierekisteri :as tierekisteri]
            [harja.views.tieluvat.tielupa-nakyma :as tieluvat]

            [harja.asiakas.kommunikaatio :as k]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.hairioilmoitus :as hairio]
            [harja.asiakas.tapahtumat :as t]
            [harja.ui.viesti :as viesti]
            [harja.ui.ikonit :as ikonit]
            [harja.pvm :as pvm]
            [harja.ui.napit :as napit]
            [harja.ui.kartta-debug :refer [kartta-layers]]
            [harja.ui.debug :as debug]
            [harja.ui.saavutettavuus :as saavutettavuus])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn kayttajatiedot [kayttaja]
  (let [{:keys [etunimi sukunimi]} @kayttaja
        kayttajainfo [:a.klikattava
                      {:href "#"
                       :id "kayttajatiedot-linkki"
                       :on-click #(do
                                    (.preventDefault %)
                                    (haku/nayta-kayttaja @kayttaja))}
                      [ikonit/ikoni-ja-teksti (ikonit/harja-icon-navigation-user) (str etunimi " " sukunimi)]]]
    (if-not (istunto/testikaytto-mahdollista?)
      kayttajainfo

      (let [testikayttaja @istunto/testikayttaja]
        [:span
         (if testikayttaja
           [:span.alert-warning "TESTIKÄYTTÖ"]
           kayttajainfo)
         [livi-pudotusvalikko {:valinta testikayttaja
                               :class "testikaytto-alasveto"
                               :title "Järjestelmän vastuuhenkilönä voit testata Harjaa myös muissa rooleissa."
                               :format-fn #(if %
                                             (:kuvaus %)
                                             (str "- Ei testikäyttäjänä -"))
                               :valitse-fn istunto/aseta-testikayttaja!}
          (concat [nil] @istunto/testikayttajat)]]))))

(defn kayttaja-dropdown [s]
  (let [auki? (reagent.core/atom false)]
    (fn [s]
      [:div {:class "nav-item dropdown"
             :on-blur (fn [e]
                        (when-not (.contains (.-currentTarget e) (.-relatedTarget e))
                          (reset! auki? false)))}

       ;; ===============================
       ;; Käyttäjä nimi & organisaatio
       [:a {:href "#"
            :class (str "nav-link d-flex lh-1 p-0 px-2 dropdown-toggle " (when @auki? "show"))
            :on-click (fn [e]
                        (.preventDefault e)
                        (swap! auki? not))}
        [:span.nav-link-icon [:i.icon.ti.ti-user]]
        [:div {:class "d-none d-xl-block ps-2"}
         [:div {} @istunto/kayttajan-nimi]
         [:div {:class "mt-1 small text-secondary"} (-> @istunto/kayttaja :organisaatio :nimi)]]]

       [:div {:class (str "dropdown-menu dropdown-menu-end dropdown-menu-arrow "
                       (when @auki? "show"))
              :style {:position "absolute"
                      :inset "0px 0px auto auto"
                      :transform "translate(0px, 42px)"}}

        ;; ===============================
        ;; Info linkki
        [:a {:href "#"
             :class (str "dropdown-item " (when (= s :info) "active"))
             :on-click #(do
                          (.preventDefault %)
                          (reset! auki? false)
                          (nav/vaihda-sivu! :info))}
         [:span.nav-link-icon [:i.icon.ti.ti-info-hexagon]]
         "INFO"]

        ;; ===============================
        ;; Käyttäjätiedot
        [:a
         {:href "#"
          :class "dropdown-item"
          :on-click #(do
                       (.preventDefault %)
                       (haku/nayta-kayttaja @istunto/kayttaja))}
         [:span.nav-link-icon [:i.icon.ti.ti-user-circle]]
         "Käyttäjätiedot"]

        ;; ===============================
        ;; Palaute linkki
        [:div {:class "dropdown-divider"}]
        [palaute/palaute-linkki]]])))

(defn header [s]
  [:header
   {:class "page"}
   [:div
    {:class "navbar navbar-expand-md d-print-none"}
    [:div
     {:class "container-xl"}
     [:button
      {:class "navbar-toggler",
       :type "button",
       :data-bs-toggle "collapse",
       :data-bs-target "#navbar-menu",
       :aria-controls "navbar-menu",
       :aria-expanded "false",
       :aria-label "Toggle navigation"}
      [:span {:class "navbar-toggler-icon"}]]

     [:div
      {:class
       "navbar-brand navbar-brand-autodark d-none-navbar-horizontal pe-0 pe-md-3"}
      [:div
       {:class "row g-2 align-items-center"}
       [:div
        {:class "col"}
        [:div {:class "page-pretitle"} "Väyläviraston"]
        [:h2 {:class "page-title"} (str (when (k/kehitysymparistossa?) "TESTI") "HARJA")]]]]

     [haku/haku]
     [kayttaja-dropdown s]]]

   [:div {:class "navbar-expand-md" :style {:margin-bottom "12px"}}
    [:div {:class "collapse navbar-collapse" :id "navbar-menu" :style {:padding "0"}}
     [:div {:class "navbar w-100"}
      [:div {:class "container-xl"}
       [:div
        {:class
         "row flex-column flex-md-row flex-fill align-items-center"}
        [:div
         {:class "col"}
         [:ul
          {:class "navbar-nav"}

          ;; ==================================================================================
          ;; Urakat
          (when (oikeudet/urakat)
            [:li
             {:class (str "nav-item" (when (= s :urakat) " active"))
              :on-click #(do
                           (.preventDefault %)
                           (nav/vaihda-sivu! :urakat))}
             [:a
              {:class "nav-link", :href "#"}

              [:span
               {:class "nav-link-icon d-md-none d-lg-inline-block"}
               [:i.icon.ti.ti-home]]
              [:span {:class "nav-link-title"} " Urakat "]]])

          ;; ==================================================================================
          ;; Raportit 
          (when (oikeudet/raportit)
            [:li
             {:class (str "nav-item" (when (= s :raportit) " active"))}
             [:a
              {:class "nav-link",
               :href "#"
               :on-click #(do
                            (.preventDefault %)
                            (nav/vaihda-sivu! :raportit))}

              [:span
               {:class "nav-link-icon d-md-none d-lg-inline-block"}
               [:i.icon.ti.ti-report-analytics]]
              [:span {:class "nav-link-title"} " Raportit "]]])

          ;; ==================================================================================
          ;; Tilannekuva
          (when (oikeudet/tilannekuva)
            [:li
             {:class (str "nav-item" (when (= s :tilannekuva) " active"))}
             [:a
              {:class "nav-link",
               :href "#"
               :on-click #(do
                            (.preventDefault %)
                            (nav/vaihda-sivu! :tilannekuva))}

              [:span
               {:class "nav-link-icon d-md-none d-lg-inline-block"}
               [:i.icon.ti.ti-live-view]]
              [:span {:class "nav-link-title"} " Tilannekuva "]]])

          ;; ==================================================================================
          ;; Ilmoitukset
          (when (oikeudet/ilmoitukset)
            [:li
             {:class (str "nav-item" (when (= s :ilmoitukset) " active"))}
             [:a
              {:class "nav-link",
               :href "#"
               :on-click #(do
                            (.preventDefault %)
                            (nav/vaihda-sivu! :ilmoitukset))}

              [:span
               {:class "nav-link-icon d-md-none d-lg-inline-block"}
               [:i.icon.ti.ti-bell-ringing]]
              [:span {:class "nav-link-title"} " Ilmoitukset "]]])

          ;; ==================================================================================
          ;; Tiepidon luvat
          (when (and (oikeudet/tieluvat)
                  (istunto/ominaisuus-kaytossa? :tienpidon-luvat))
            [:li
             {:class (str "nav-item" (when (= s :tienpidon-luvat) " active"))}
             [:a
              {:class "nav-link",
               :href "#"
               :on-click #(do
                            (.preventDefault %)
                            (nav/vaihda-sivu! :tienpidon-luvat))}
              [:span
               {:class "nav-link-icon d-md-none d-lg-inline-block"}
               [:i.icon.ti.ti-certificate]]
              [:span {:class "nav-link-title"} " Tiepidon luvat "]]])

          ;; ==================================================================================
          ;; Urakoiden tilanne
          (when (oikeudet/urakkatilanne)
            [:li
             {:class (str "nav-item" (when (= s :urakoiden-tilanne) " active"))}
             [:a
              {:class "nav-link",
               :href "#"
               :on-click #(do
                            (.preventDefault %)
                            (nav/vaihda-sivu! :urakoiden-tilanne))}
              [:span
               {:class "nav-link-icon d-md-none d-lg-inline-block"}
               [:i.icon.ti.ti-progress-check]]
              [:span {:class "nav-link-title"} " Urakoiden tilanne "]]])

          ;; ==================================================================================
          ;; Hallinta
          (when (oikeudet/hallinta)
            [:li
             {:class (str "nav-item" (when (= s :hallinta) " active"))}
             [:a
              {:class "nav-link",
               :href "#"
               :on-click #(do
                            (.preventDefault %)
                            (nav/vaihda-sivu! :hallinta))}

              [:span
               {:class "nav-link-icon d-md-none d-lg-inline-block"}
               [:i.icon.ti.ti-device-desktop-cog]]
              [:span {:class "nav-link-title"} " Hallinta "]]])]]]]]]]])

(defn ladataan []
  [:div {:style {:position "absolute"
                 :top "50%"
                 :left "50%"
                 :transform "translate(-50%, -50%)"}}
   [:div {:style {:width "320px"}}
    [ajax-loader "Ladataan..."]]])

(defn yleinen-varoituspalkki
  "Näyttää yleisluontoisen varoituspalkin selaimen ylänurkassa.
  Ottaa varoitustekstin ja mahdollisia optioita:

  nayta-pisteanimaatio?     Näytetäänkö kolmen pisteen animaatio varoitustekstin perässä? Oletuksena false.
  linkki                    Varoitustekstin perässä olevan linkin teksti
  linkki-fn                 Linkin suoritusfunktio"
  ([varoitusteksti] (yleinen-varoituspalkki varoitusteksti {}))
  ([varoitusteksti opts]
   (assert varoitusteksti "Varoitusteksti on pakollinen!")
   (let [pisteanimaation-pisteet (atom "")
         nayta-pisteanimaatio? (:nayta-pisteanimaatio? opts)
         linkki-fn (:linkki-fn opts)
         linkki (:linkki opts)]
     (komp/luo
       (komp/ulos (let [pisteanimaatio-kaynnissa (atom true)]
                    (go-loop [[teksti & tekstit] (cycle ["" "." ".." "..."])]
                      (when @pisteanimaatio-kaynnissa
                        (<! (timeout 1000))
                        (reset! pisteanimaation-pisteet teksti)
                        (recur tekstit)))
                    #(reset! pisteanimaatio-kaynnissa false)))
       (fn []
         [:div.yhteysilmoitin.yhteys-katkennut-varoitus
          [:div.yhteysilmoitin-viesti varoitusteksti
           (when nayta-pisteanimaatio?
             [:div.yhteysilmoitin-pisteet @pisteanimaation-pisteet])
           (when linkki
             [:span " "
              [:a.klikattava {:on-click linkki-fn} linkki]])]])))))

(defn yhteys-palautunut-ilmoitus []
  [:div.yhteysilmoitin.yhteys-palautunut-ilmoitus "Yhteys palautui!"])

(defn hairioilmoitus
  ([hairiotiedot] (hairioilmoitus hairiotiedot {}))
  ([hairiotiedot {:keys [margin-bottom?] :or {margin-bottom? true}}]
   (let [otsikko (hairio/tyyppi-fmt (::hairio/tyyppi hairiotiedot))
         tyyppi (::hairio/tyyppi hairiotiedot)
         tyyppi-luokka (case tyyppi
                         :tiedote "hairioilmoitin-tyyppi-tiedote"
                         "hairioilmoitin-tyyppi-hairio")
         hairio-pvm (pvm/pvm-opt (or (::hairio/alkuaika hairiotiedot) (::hairio/pvm hairiotiedot)))]
     [:div.container-sm.hairioilmoitin {:class (str tyyppi-luokka (when margin-bottom? " margin-bottom-16"))}
      [:div.margin-right-32.lihavoitu
       (str otsikko " " hairio-pvm ": " (::hairio/viesti hairiotiedot))]
      [napit/sulje-ruksi #(hairiotiedot/piilota-hairioilmoitus-tyypilla! tyyppi) {:style {:margin "0px"}}]])))

(defn paasisalto [sivu korkeus]
  (let [tyypeittain @hairiotiedot/hairioilmoitukset-tyypeittain
        nayta-tyypeittain @hairiotiedot/nayta-hairioilmoitus-tyypeittain?
        hairio-ilmoitus (:hairio tyypeittain)
        tiedote-ilmoitus (:tiedote tyypeittain)
        nayta-hairio? (and hairio-ilmoitus (:hairio nayta-tyypeittain))
        nayta-tiedote? (and tiedote-ilmoitus (:tiedote nayta-tyypeittain))
        molemmat-nakyvissa? (and nayta-hairio? nayta-tiedote?)]
    [:div
     [debug/df-shell-kaikki]
     (cond
       @k/istunto-vanhentunut?
       [yleinen-varoituspalkki
        "Istunto on vanhentunut."
        {:linkki "Lataa sivu uudelleen"
         :linkki-fn #(.reload js/location)}]

       @k/yhteys-katkennut?
       [yleinen-varoituspalkki
        "Yhteys Harjaan on katkennut! Yritetään yhdistää uudelleen"
        {:nayta-pisteanimaatio? true}]

       (and (not @k/yhteys-katkennut?) @k/yhteys-palautui-hetki-sitten)
       [yhteys-palautunut-ilmoitus])

     [:div#harja-header.container
      [header sivu]]

     [:div.container
      [murupolku/murupolku]]

     (when nayta-hairio?
       [hairioilmoitus hairio-ilmoitus {:margin-bottom? (not molemmat-nakyvissa?)}])

     (when nayta-tiedote?
       [hairioilmoitus tiedote-ilmoitus])

     ^{:key "harjan-paasisalto"}
     [:div.container.sisalto {:style {:min-height (max 200 (- @dom/korkeus 220))}} ; contentin minimikorkeus pakottaa footeria alemmas
      [:div.row.row-sisalto
       [:div {:class (when-not (= sivu :tilannekuva) "col-sm-12")}
        (case sivu
          :urakat [urakat/urakat]
          :raportit [raportit/raportit]
          :info [info/info]
          :ilmoitukset [ilmoitukset/ilmoitukset]
          :tienpidon-luvat [tieluvat/tieluvat]
          :urakoiden-tilanne (when (oikeudet/urakkatilanne) [kojelauta/kojelauta])
          :hallinta (when (oikeudet/hallinta) [hallinta/hallinta])
          :tilannekuva [tilannekuva/tilannekuva]
          :about [about/about]
          :tr [tierekisteri/tierekisteri]

          ;; jos käyttäjä kirjoittaa selaimeen invalidin urlin, estetään räsähdys
          [urakat/urakat])]]]
     [modal/modal-container]
     [toast-viesti-container]
     ;; Aria-live containerit eri prioriteeteille  
     [saavutettavuus/aria-live-container (:polite @saavutettavuus/aria-viestit) {:kohteliaisuus "polite"}]
     [saavutettavuus/aria-live-container (:assertive @saavutettavuus/aria-viestit) {:kohteliaisuus "assertive"}]
     (when @nav/kartta-nakyvissa?
       [kartta-layers korkeus])

     ^{:key "kartta-container"}
     [:div#kartta-container {:style {:position "absolute"
                                     :top (- korkeus)
                                     :left 0
                                     :right 0
                                     :width "100%"
                                     :max-width "100%"
                                     :overflow (if @nav/kartta-nakyvissa? "hidden" "visible")
                                     :display (if (and
                                                    (= @nav/kartan-koko :hidden)
                                                    (not @nav/kartta-nakyvissa?))
                                                "none"
                                                "block")}}
      [kartta/kartta]]]))

(defn varoita-jos-vanha-ie []
  (if dom/ei-tuettu-ie?
    (modal/nayta! {:otsikko "Käytössä vanha Internet Explorer"
                   :footer [:span
                            [:button.nappi-toissijainen {:type "button"
                                                         :on-click #(do (.preventDefault %)
                                                                      (modal/piilota!))}
                             "OK"]]}
      [:div
       [:p "Käytössäsi on vanhentunut Internet Explorer -selaimen versio. Emme voi taata, että kaikki Harjan ominaisuudet toimivat täysin oikein."]])))

(defn ei-kayttooikeutta? [kayttaja]
  (or (:poistettu kayttaja)
    (and (empty? (:roolit kayttaja))
      (empty? (:urakkaroolit kayttaja))
      (empty? (:organisaatioroolit kayttaja)))))

(defn todennus-varmistus-epaonnistui? [kayttaja]
  ;; Tarkoittaa että todennus epäonnistui, tästä laukaistaan myös slack häly: JWT-ERROR
  (boolean (contains? (:roolit kayttaja) "failed")))

(defn kuuntele-oikeusvirheita []
  (t/kuuntele! :ei-oikeutta (fn [tiedot]
                              (viesti/nayta! (:viesti tiedot)
                                :warning
                                viesti/viestin-nayttoaika-pitka))))

(defn main
  "Harjan UI:n pääkomponentti"
  []
  (varoita-jos-vanha-ie)
  (kuuntele-oikeusvirheita)
  (komp/luo
    (fn []
      (if @nav/render-lupa?
        (let [sivu (@reitit/url-navigaatio :sivu)
              aikakatkaistu? @istunto/istunto-aikakatkaistu?
              korkeus @dom/korkeus
              kayttaja @istunto/kayttaja]
          (if aikakatkaistu?
            [:div "Harjan käyttö aikakatkaistu kahden tunnin käyttämättömyyden takia. Lataa sivu uudelleen."]
            (if (nil? kayttaja)
              [ladataan]
              (cond
                (todennus-varmistus-epaonnistui? kayttaja)
                [:div.ei-kayttooikeutta-wrap
                 [:img#harja-brand-icon {:src "images/harja_logo_soft.svg"}]
                 [:div.ei-kayttooikeutta "Todennus epäonnistui. Ei käyttöoikeutta Harjaan."]]

                (ei-kayttooikeutta? kayttaja)
                [:div.ei-kayttooikeutta-wrap
                 [:img#harja-brand-icon {:src "images/harja_logo_soft.svg"}]
                 [:div.ei-kayttooikeutta "Ei käyttöoikeutta Harjaan. Ota yhteys organisaatiosi käyttövaltuusvastaavaan."]]

                :else [paasisalto sivu korkeus]))))
        [ladataan]))))
