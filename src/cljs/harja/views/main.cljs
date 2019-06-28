(ns harja.views.main
  "Harjan päänäkymä"
  (:require [tuck.core :as tuck]
            [harja.ui.bootstrap :as bs]
            [reagent.core :refer [atom]]
            [harja.tilanhallinta.tila :as tila]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [linkki staattinen-linkki-uuteen-ikkunaan ajax-loader livi-pudotusvalikko]]
            [harja.ui.dom :as dom]
            [harja.ui.modal :as modal]
            [harja.ui.palaute :as palaute]
            [harja.ui.viesti :refer [viesti-container]]
            [harja.tiedot.navigaatio :as nav]
            [harja.loki :refer [log logt]]
            [harja.tiedot.hairioilmoitukset :as hairiotiedot]
            [harja.views.murupolku :as murupolku]
            [harja.views.haku :as haku]
            [cljs.core.async :refer [put! close! chan timeout]]

            ;; Nämä on pakko requirettaa, muuten frontin prod-käännös ei toimi (joku ongelma kääntämisjärjestyksessä?)
            [harja.domain.vesivaylat.vatu-turvalaite :as vatu-turvalaite]
            [harja.domain.paikkaus :as paikkaus-domain]

            [harja.views.urakat :as urakat]
            [harja.views.raportit :as raportit]
            [harja.views.tilannekuva.tilannekuva :as tilannekuva]
            [harja.views.ilmoitukset.tieliikenneilmoitukset :as ilmoitukset]
            [harja.views.kartta :as kartta]
            [harja.views.hallinta :as hallinta]
            [harja.views.about :as about]
            [harja.views.tierekisteri :as tierekisteri]
            [harja.views.tieluvat.tieluvat :as tieluvat]

            [harja.asiakas.kommunikaatio :as k]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.hairioilmoitus :as hairio]
            [harja.asiakas.tapahtumat :as t]
            [harja.ui.viesti :as viesti]
            [harja.ui.ikonit :as ikonit]
            [harja.pvm :as pvm]
            [harja.ui.napit :as napit]
            [harja.ui.kartta-debug :refer [kartta-layers]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn kayttajatiedot [kayttaja]
  (let [{:keys [etunimi sukunimi]} @kayttaja
        kayttajainfo [:a {:href "#" :on-click #(do
                                                 (.preventDefault %)
                                                 (haku/nayta-kayttaja @kayttaja))}
                      etunimi " " sukunimi]]
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

(defn harja-info []
  [:a {:class "klikattava"
       :id "infolinkki"
       :href "http://finnishtransportagency.github.io/harja/"}
   [ikonit/ikoni-ja-teksti (ikonit/livicon-info-circle) "INFO"]])

(defn- mobiiliselain? []
  (some #(re-matches % (clojure.string/lower-case js/window.navigator.userAgent))
        [#".*android.*" #".*ipad.*"]))

(defn header [s]
  [bs/navbar {:luokka (when (k/kehitysymparistossa?) "testiharja")}
   [:span
    [:img#harja-brand-icon {:alt "HARJA"
                            :src "images/harja_logo_soft.svg"
                            :on-click #(.reload js/window.location)}]
    (when (k/kehitysymparistossa?)
      [:span#testiharja "TESTI"])]
   [haku/haku]

   [:ul#sivut.nav.nav-pills

    (when (oikeudet/urakat)
      [:li {:role "presentation" :class (when (= s :urakat) "active")}
       [linkki "Urakat" #(nav/vaihda-sivu! :urakat)]])

    (when (oikeudet/raportit)
      [:li {:role "presentation" :class (when (= s :raportit) "active")}
       [linkki "Raportit" #(nav/vaihda-sivu! :raportit)]])

    (when (oikeudet/tilannekuva)
      [:li {:role "presentation" :class (when (= s :tilannekuva) "active")}
       [linkki "Tilannekuva" #(nav/vaihda-sivu! :tilannekuva)]])

    (when (oikeudet/ilmoitukset)
      [:li {:role "presentation" :class (when (= s :ilmoitukset) "active")}
       [linkki "Ilmoitukset" #(nav/vaihda-sivu! :ilmoitukset)]])

    (when (and (oikeudet/tieluvat)
               (istunto/ominaisuus-kaytossa? :tienpidon-luvat))
      [:li {:role "presentation" :class (when (= s :tienpidon-luvat) "active")}
       [linkki "Tienpidon luvat" #(nav/vaihda-sivu! :tienpidon-luvat)]])

    (when (oikeudet/hallinta)
      [:li {:role "presentation" :class (when (= s :hallinta) "active")}
       [linkki "Hallinta" #(nav/vaihda-sivu! :hallinta)]])

    (when (and (mobiiliselain?)
               (oikeudet/laadunseuranta))
      [:li {:role "presentation"}
       [staattinen-linkki-uuteen-ikkunaan "Laadunseurannan mobiilityökalu"
        (str k/+polku+ "laadunseuranta")]])]

   :right
   [harja-info]
   [palaute/palaute-linkki]
   [kayttajatiedot istunto/kayttaja]])

(defn ladataan []
  [:div {:style {:position "absolute" :top "50%" :left "50%"}}
   [:div {:style {:position "relative" :left "-50px" :top "-20px"}}
    [ajax-loader "Ladataan..." {:luokka "ladataan-harjaa"}]]])

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

(defn hairioilmoitus [hairiotiedot]
  (let [otsikko (hairio/tyyppi-fmt (::hairio/tyyppi hairiotiedot))
        tyyppi-luokka (case (::hairio/tyyppi hairiotiedot)
                        :tiedote "hairioilmoitin-tyyppi-tiedote"
                        "hairioilmoitin-tyyppi-hairio")]
    [:div.hairioilmoitin {:class tyyppi-luokka}
     [napit/sulje-ruksi hairiotiedot/piilota-hairioilmoitus!]
     [:div (str otsikko " " (pvm/pvm-opt (::hairio/pvm hairiotiedot)) ": "
                (::hairio/viesti hairiotiedot))]]))

(defn paasisalto [sivu korkeus]
  [:div
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

   (let [hairiotiedot (:hairioilmoitus @hairiotiedot/tuore-hairioilmoitus)]
     (when (and hairiotiedot @hairiotiedot/nayta-hairioilmoitus?)
       [hairioilmoitus hairiotiedot]))

   [:div.container
    [header sivu]]

   [:div.container
    [tuck/tuck tila/master murupolku/murupolku]]

   ^{:key "harjan-paasisalto"}
   [:div.container.sisalto {:style {:min-height (max 200 (- @dom/korkeus 220))}} ; contentin minimikorkeus pakottaa footeria alemmas
    [:div.row.row-sisalto
     [:div {:class (when-not (= sivu :tilannekuva) "col-sm-12")}
      (case sivu
        :urakat [urakat/urakat]
        :raportit [raportit/raportit]
        :ilmoitukset [ilmoitukset/ilmoitukset]
        :tienpidon-luvat [tieluvat/tieluvat]
        :hallinta [hallinta/hallinta]
        :tilannekuva [tuck/tuck tila/master tilannekuva/tilannekuva]
        :about [about/about]
        :tr [tierekisteri/tierekisteri]

        ;; jos käyttäjä kirjoittaa selaimeen invalidin urlin, estetään räsähdys
        [urakat/urakat])]]]
   [modal/modal-container]
   [viesti-container]
   (when @nav/kartta-nakyvissa?
     [kartta-layers korkeus])

   ;; kartta luodaan ja liitetään DOM:iin tässä. Se asemoidaan muualla #kartan-paikka divin avulla
   ;; asetetaan alkutyyli siten, että kartta on poissa näkyvistä, jos näkymässä on kartta,
   ;; se asemoidaan mountin jälkeen
   ^{:key "kartta-container"}
   [:div#kartta-container {:style {:position "absolute"
                                   :top (- korkeus)
                                   ;; Estetään asioiden vuotaminen ulos kartalta kun kartta on avattu
                                   :overflow (if @nav/kartta-nakyvissa?
                                               "hidden"
                                               "visible")}}
    [tuck/tuck tila/master kartta/kartta]]])

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
        (let [sivu @nav/valittu-sivu
              aikakatkaistu? @istunto/istunto-aikakatkaistu?
              korkeus @dom/korkeus
              kayttaja @istunto/kayttaja]
          (if aikakatkaistu?
            [:div "Harjan käyttö aikakatkaistu kahden tunnin käyttämättömyyden takia. Lataa sivu uudelleen."]
            (if (nil? kayttaja)
              [ladataan]
              (if (ei-kayttooikeutta? kayttaja)
                [:div.ei-kayttooikeutta-wrap
                 [:img#harja-brand-icon {:src "images/harja_logo_soft.svg"}]
                 [:div.ei-kayttooikeutta "Ei käyttöoikeutta Harjaan. Ota yhteys organisaatiosi käyttövaltuusvastaavaan."]]
                [paasisalto sivu korkeus]))))
        [ladataan]))))
