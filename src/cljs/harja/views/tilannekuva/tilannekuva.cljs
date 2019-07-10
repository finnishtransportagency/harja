(ns harja.views.tilannekuva.tilannekuva
  (:require [reagent.core :refer [atom]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.tilannekuva.tilannekuva :as tiedot]
            [harja.tiedot.tilannekuva.tilannekuva-kartalla :as tilannekuva-kartalla]
            [harja.views.tilannekuva.tienakyma :as tienakyma]
            [harja.views.urakka.yllapitokohteet.yhteyshenkilot :as yllapito-yhteyshenkilot]
            [harja.views.tieluvat.tieluvat :as tielupa-view]
            [harja.views.kartta :as kartta]
            [harja.ui.valinnat :as ui-valinnat]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.kentat :as kentat]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.dom :as dom]
            [reagent.core :as r]
            [goog.events.EventType :as EventType]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.checkbox :as checkbox]
            [harja.domain.urakka :as u]
            [harja.domain.tilannekuva :as tk]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.ui.bootstrap :as bs]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.domain.ely :as ely]
            [harja.domain.oikeudet :as oikeudet]
            [harja.views.tilannekuva.tilannekuva-jaettu :as jaettu]
            [harja.ui.modal :as modal]
            [harja.domain.tielupa :as tielupa]
            [tuck.core :as tuck]
            [harja.tilanhallinta.tila :as tila])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction-writable]]
                   [harja.tyokalut.ui :refer [for*]]))

(def hallintapaneeli-max-korkeus (atom nil))

(defn aseta-hallintapaneelin-max-korkeus [paneelin-sisalto]
  (let [r (.getBoundingClientRect paneelin-sisalto)
        etaisyys-alareunaan (- @dom/korkeus (.-top r))]
    (reset! hallintapaneeli-max-korkeus (max
                                          200
                                          (- etaisyys-alareunaan 30)))))

(defonce paneelien-tila-atomit (atom {}))

(defn paneelin-tila-atomi!
  ([paneeli] (paneelin-tila-atomi! paneeli true))
  ([paneeli luotavan-arvo]
   (let [hae-arvo #(get @paneelien-tila-atomit paneeli)]
     (or (hae-arvo)
         (do (swap! paneelien-tila-atomit assoc paneeli (atom luotavan-arvo))
             (hae-arvo))))))

(defn nykytilanteen-aikavalinnat []
  [:div#tk-nykytilanteen-aikavalit
   [kentat/tee-kentta {:tyyppi :radio
                       :valinta-nayta first
                       :valinta-arvo second
                       :valinnat tiedot/nykytilanteen-aikasuodatin-tunteina}
    tiedot/nykytilanteen-aikasuodattimen-arvo]])

(defn historiankuvan-aikavalinnat []
  [:div#tk-historiakuvan-aikavalit
   [ui-valinnat/aikavali tiedot/historiakuvan-aikavali {:nayta-otsikko? false
                                                        :aikavalin-rajoitus [12 :kuukausi]
                                                        :aloitusaika-pakota-suunta :alas-oikea
                                                        :paattymisaika-pakota-suunta :alas-vasen}]])

(defn yksittainen-suodatincheckbox
  "suodatin-polku on polku, josta tämän checkboxin nimi ja tila löytyy suodattimet-atomissa"
  [nimi suodattimet-atom suodatin-polku]
  [checkbox/checkbox
   (r/wrap (checkbox/boolean->checkbox-tila-keyword
             (get-in @suodattimet-atom suodatin-polku))
           (fn [uusi-tila]
             (swap! suodattimet-atom
                    assoc-in
                    suodatin-polku
                    (checkbox/checkbox-tila-keyword->boolean uusi-tila))))
   nimi])

(def auki-oleva-checkbox-ryhma (atom nil))

(defn checkbox-suodatinryhma
  "ryhma-polku on polku, josta tämän checkbox-ryhmän jäsenten nimet ja tilat löytyvät suodattimet-atomissa.

   kokoelma-atomin antaminen tarkoittaa, että checkbox-ryhmä on osa usean checkbox-ryhmän kokoelmaa, joista
   vain atomin ilmoittama ryhmä voi olla kerrallaan auki. Jos kokoelmaa ei anneta, tämä checkbox-ryhmä ylläpitää
   itse omaa auki/kiinni-tilaansa."
  ([otsikko suodattimet-atom ryhma-polku]
   (checkbox-suodatinryhma otsikko suodattimet-atom ryhma-polku nil))
  ([otsikko suodattimet-atom ryhma-polku {:keys [auki-atomi?] :as optiot}]
   (let [auki-tila (or auki-atomi? (atom false))
         ryhmanjohtaja-tila-atom (reaction-writable
                                   (if (every? true? (vals (get-in @suodattimet-atom ryhma-polku)))
                                     :valittu
                                     (if (every? false? (vals (get-in @suodattimet-atom ryhma-polku)))
                                       :ei-valittu
                                       :osittain-valittu)))]
     (fn [otsikko suodattimet-atom ryhma-polku {:keys [luokka sisallon-luokka otsikon-luokka
                                                       nayta-lkm? kokoelma-atom] :as optiot}]
       (let [ryhman-elementtien-avaimet (or (get-in tk/tehtavien-jarjestys ryhma-polku)
                                            (sort-by :otsikko (keys (get-in @suodattimet-atom ryhma-polku))))
             auki? (fn [] (or @auki-tila
                              (and kokoelma-atom
                                   (= otsikko @kokoelma-atom))))
             valittujen-lkm (count (filter true? (vals (get-in @suodattimet-atom ryhma-polku))))
             kokonais-lkm (count (vals (get-in @suodattimet-atom ryhma-polku)))]
         (when-not (empty? ryhman-elementtien-avaimet)
           [:div {:class (str "tk-checkbox-ryhma" (when luokka (str " " luokka)))}
            [:div
             {:class (str "tk-checkbox-ryhma-otsikko klikattava " (when (auki?) "alaraja"))
              :on-click (fn [_]
                          (if kokoelma-atom
                            ;; Osa kokoelmaa, vain yksi kokoelman jäsen voi olla kerrallaan auki
                            (if (= otsikko @kokoelma-atom)
                              (reset! kokoelma-atom nil)
                              (reset! kokoelma-atom otsikko))
                            ;; Ylläpitää itse omaa auki/kiinni-tilaansa
                            (swap! auki-tila not))
                          (aseta-hallintapaneelin-max-korkeus (dom/elementti-idlla "tk-suodattimet")))}
             [:span {:class (str
                              "tk-chevron-ryhma-tila chevron-rotate "
                              (when-not (auki?) "chevron-rotate-down"))}
              (if (auki?)
                (ikonit/livicon-chevron-down) (ikonit/livicon-chevron-right))]
             [:div.tk-checkbox-ryhma-checkbox {:on-click #(.stopPropagation %)}
              [checkbox/checkbox ryhmanjohtaja-tila-atom (str otsikko (when nayta-lkm? (str " (" valittujen-lkm "/" kokonais-lkm ")")))
               {:otsikon-luokka otsikon-luokka
                :on-change (fn [uusi-tila]
                             ;; Aseta kaikkien tämän ryhmän suodattimien tilaksi tämän elementin uusi tila.
                             (when (not= :osittain-valittu uusi-tila)
                               (reset! suodattimet-atom
                                       (reduce (fn [edellinen-map tehtava-avain]
                                                 (assoc-in edellinen-map
                                                           (conj ryhma-polku tehtava-avain)
                                                           (checkbox/checkbox-tila-keyword->boolean uusi-tila)))
                                               @suodattimet-atom
                                               (keys (get-in @suodattimet-atom ryhma-polku))))))}]]]

            (when (auki?)
              [:div {:class (str "tk-checkbox-ryhma-sisalto" (when sisallon-luokka (str " " sisallon-luokka)))}
               (doall (for [elementti (seq ryhman-elementtien-avaimet)]
                        ^{:key (str "pudotusvalikon-asia-" (:id elementti))}
                        [yksittainen-suodatincheckbox
                         (:otsikko elementti)
                         suodattimet-atom
                         (conj ryhma-polku elementti)]))])]))))))

(defn- asetuskokoelma
  [otsikko {:keys [salli-piilotus? luokka auki-atomi? otsikon-luokka] :as optiot} sisalto]
  (when otsikko
    (let [auki? (or auki-atomi? (atom true))]
      (fn [otsikko {:keys [salli-piilotus? luokka otsikon-luokka] :as optiot} sisalto]
        [:div {:class (str "tk-asetuskokoelma" (when luokka (str " " luokka)))}
         (when salli-piilotus?
           [:div {:class (str
                           "tk-chevron-ryhma-tila chevron-rotate chevron-tk-asetuskokoelma "
                           (when-not @auki? "chevron-rotate-down"))
                  :on-click #(swap! auki? not)}
            (if @auki?
              (ikonit/livicon-chevron-down)
              (ikonit/livicon-chevron-right))])
         [:div {:class (str "tk-otsikko "
                            (when salli-piilotus?
                              "tk-otsikko-sisenna")
                            (when otsikon-luokka (str " " otsikon-luokka)))}
          otsikko]
         (when @auki?
           sisalto)]))))

(def tilannekuvan-alueet ely/elynumerot-jarjestyksessa)

(defn- tyypin-aluesuodattimet [tyyppi]
  (komp/luo
    (fn [tyyppi]
      [asetuskokoelma
       (u/urakkatyyppi->otsikko tyyppi)
       {:salli-piilotus? true
        :auki-atomi? (paneelin-tila-atomi! (keyword (str (name tyyppi) "-aluesuodatin")) false)
        :luokka "taustavari-taso2 ylaraja"
        :otsikon-luokka "fontti-taso2"}
       [:div.tk-suodatinryhmat
        (doall
          (for [elynumero tilannekuvan-alueet]
            ^{:key (str tyyppi "-aluesuodatin-alueelle-" elynumero)}
            [checkbox-suodatinryhma (ely/elynumero->nimi elynumero) tiedot/suodattimet
             [:alueet tyyppi elynumero]
             {:luokka "taustavari-taso3 ylaraja"
              :sisallon-luokka "taustavari-taso4"
              :otsikon-luokka "fontti-taso3"
              :nayta-lkm? true
              :auki-atomi? (paneelin-tila-atomi! (str [:alueet tyyppi elynumero]) false)}]))]])))

(defn- aluesuodattimet [e! app]
  (komp/luo
    (fn [e! {:keys [aluesuodattimet]}]
      (log "alueet jne " aluesuodattimet)
      (let [onko-alueita? (some
                            (fn [[_ hy-ja-urakat]]
                              (some not-empty (vals hy-ja-urakat)))
                            aluesuodattimet)
            ensimmainen-haku-kaynnissa? (and (empty? (:alueet @tiedot/suodattimet))
                                             (nil? @tiedot/uudet-aluesuodattimet))
            tyypit-joissa-alueita (keys (:alueet @tiedot/suodattimet))
            tyypit-joissa-alueita (keys aluesuodattimet)
            alueita-valittu? (tiedot/alueita-valittu? @tiedot/suodattimet)
            ]
        [:div
         [asetuskokoelma
          (cond
            ensimmainen-haku-kaynnissa? "Haetaan urakoita"
            onko-alueita? "Hae urakoista"
            :else "Ei näytettäviä urakoita")
          {:salli-piilotus? true
           :auki-atomi? (paneelin-tila-atomi! :aluesuodattimet true)
           :luokka "taustavari-taso1 eroa-huipulla ylaraja"
           :otsikon-luokka "fontti-taso1"}
          (if ensimmainen-haku-kaynnissa?
            [yleiset/ajax-loader]
            [:div.tk-suodatinryhmat
             (doall
               (for [urakkatyyppi tyypit-joissa-alueita]
                 ^{:key (str "aluesuodattimet-tyypille-" urakkatyyppi)}
                 [tyypin-aluesuodattimet urakkatyyppi]))])]
         (when (and (not alueita-valittu?) (not ensimmainen-haku-kaynnissa?))
           [yleiset/vihje "Yhtään aluetta ei ole valittu."])]))))

(def ^{:private true
       :doc "Mahdolliset checkbox valintaryhmät nykytilanne/historia näkymissä."}
suodatinryhmat
  [["Ilmoitukset" [:ilmoitukset :tyypit]]
   ["Ylläpito" [:yllapito]]
   ["Talvihoitotyöt" [:talvi]]
   ["Kesähoitotyöt" [:kesa]]
   ["Laatupoikkeamat" [:laatupoikkeamat]]
   ["Tarkastukset" [:tarkastukset]]])

(defn- aikasuodattimet []
  (let [yleiset-asetukset {:luokka "taustavari-taso3 ylaraja"
                           :otsikon-luokka "fontti-taso3"
                           :sisallon-luokka "taustavari-taso4"
                           :kokoelma-atom auki-oleva-checkbox-ryhma
                           :nayta-lkm? false}]
    [asetuskokoelma
     (str "Näytä aikavälillä" (when-not (= :nykytilanne @tiedot/valittu-tila)
                                " (max. yksi vuosi):"))
     {:salli-piilotus? false
      :luokka "taustavari-taso0"}
     [:div
      (when (= :nykytilanne @tiedot/valittu-tila)
        [nykytilanteen-aikavalinnat])
      (when (= :historiakuva @tiedot/valittu-tila)
        [historiankuvan-aikavalinnat])
      [:div.tk-yksittaiset-suodattimet.fontti-taso3
       [yksittainen-suodatincheckbox "Turvallisuuspoikkeamat"
        tiedot/suodattimet [:turvallisuus tk/turvallisuuspoikkeamat]
        auki-oleva-checkbox-ryhma]
       (when
         (harja.tiedot.istunto/ominaisuus-kaytossa? :tietyoilmoitukset)
         [yksittainen-suodatincheckbox "Tietyöilmoitukset"
          tiedot/suodattimet [:tietyoilmoitukset tk/tietyoilmoitukset]
          auki-oleva-checkbox-ryhma])
       (when
         (harja.tiedot.istunto/ominaisuus-kaytossa? :tieluvat)
         [yksittainen-suodatincheckbox "Tieluvat"
          tiedot/suodattimet [:tieluvat tk/tieluvat]
          auki-oleva-checkbox-ryhma])
       ]
      [:div {:class "tk-suodatinryhmat"}
       (for*
         [[otsikko polku] suodatinryhmat]
         [checkbox-suodatinryhma otsikko tiedot/suodattimet polku
          (merge yleiset-asetukset
                 {:auki-atomi? (paneelin-tila-atomi! (str polku) false)})])]
      [:div.tk-yksittaiset-suodattimet.fontti-taso3
       [yksittainen-suodatincheckbox "Varustetoteumat"
        tiedot/suodattimet [:varustetoteumat tk/varustetoteumat]]]]]))

(defn nykytilanne-valinnat []
  [:span.tilannekuva-nykytilanne-valinnat
   [aikasuodattimet]
   ;[tuck/tuck tila/-master aluesuodattimet]
   ])

(defn historiakuva-valinnat []
  [:span.tilannekuva-historiakuva-valinnat
   [aikasuodattimet]
   ;[tuck/tuck tila/-master aluesuodattimet]
   ])

(defn tienakyma []
  (komp/luo
    (fn []
      [tienakyma/tienakyma])))

(defn suodattimet [e!]
  (let [resize-kuuntelija (fn [this _]
                            (aseta-hallintapaneelin-max-korkeus (r/dom-node this)))]
    (komp/luo
      (komp/sisaan (fn [this]
                     (when-not (roolit/tilaajan-kayttaja? @istunto/kayttaja)
                       (let [tilaajan-laadunvalvonta-avain (some #(when (= "tilaajan laadunvalvonta" (:nimi %)) %)
                                                                 (keys (:tarkastukset @tiedot/suodattimet)))]
                         (swap! tiedot/suodattimet update :tarkastukset dissoc tilaajan-laadunvalvonta-avain)))))
      (komp/piirretty (fn [this] (aseta-hallintapaneelin-max-korkeus (r/dom-node this))))
      (komp/dom-kuuntelija js/window
                           EventType/RESIZE resize-kuuntelija)
      (fn [e!]
        [:div#tk-suodattimet {:style {:max-height @hallintapaneeli-max-korkeus
                                      :overflow-x "hidden"
                                      :overflow-y "auto"}}
         [bs/tabs {:active (nav/valittu-valilehti-atom :tilannekuva) :event #(e! (tiedot/->AsetaValittuTila %))}
          "Nykytilanne"
          :nykytilanne
          [nykytilanne-valinnat]

          "Historiakuva"
          :historiakuva
          [historiakuva-valinnat]

          "Tienäkymä"
          :tienakyma
          (when (roolit/tilaajan-kayttaja? @istunto/kayttaja)
            [tienakyma])]]))))

(defonce hallintapaneeli-auki (atom {:hallintapaneeli true}))

(defn hallintapaneeli [e!]
  [yleiset/haitari-paneelit
   {:auki @hallintapaneeli-auki
    :luokka "haitari-tilannekuva"
    :toggle-osio! #(swap! hallintapaneeli-auki update % not)}

   "Hallintapaneeli" :hallintapaneeli [suodattimet e!]])



(defn- nayta-vai-piilota? [tila]
  (case tila
    :nykytilanne true
    :historiakuva true
    :tienakyma false))

(defn- nayta-tai-piilota-karttataso! [tila]
  (reset! tilannekuva-kartalla/karttataso-tilannekuva (nayta-vai-piilota? tila)))

(def tilannekuvan-infopaneelin-linkit
  {:paallystys
   {:toiminto (fn [yllapitokohdeosa]
                (yllapito-yhteyshenkilot/nayta-yhteyshenkilot-modal!
                  (:yllapitokohde-id yllapitokohdeosa)
                  :paallystys))
    :teksti "Näytä yhteyshenkilöt"}

   :varustetoteuma
   {:toiminto (comp siirtymat/nayta-varustetoteuma! :id)
    :teksti "Toteumanäkymään"
    :tooltip "Siirry urakan varustetoteumiin"

    ;; Näytä vain, jos käyttäjällä oikeus urakan varustetoteumiin
    :when (comp oikeudet/urakat-toteumat-varusteet :urakka-id)}

   :ilmoitus
   {:toiminto #(jaettu/nayta-kuittausten-tiedot (:kuittaukset %))
    :teksti "Näytä kuittaukset"}

   :tielupa
   {:toiminto (fn [lupa]
                (modal/nayta!
                 {:otsikko (str (tielupa/tyyppi-fmt (::tielupa/tyyppi lupa)) " " (::tielupa/paatoksen-diaarinumero lupa))
                  :luokka "tilannekuva-tielupa-modal"}
                 [tielupa-view/tielupalomake (constantly lupa) {:valittu-tielupa lupa}]))
    :teksti "Näytä tarkemmat tiedot"}})

(defn tilannekuva [e! app]
  (komp/luo
    (komp/lippu tiedot/nakymassa? istunto/ajastin-taukotilassa?)
    (komp/watcher tiedot/valittu-tila
                  (fn [_ _ uusi]
                    (nayta-tai-piilota-karttataso! uusi)))
    (komp/sisaan-ulos
      #(do (kartta/aseta-paivitetaan-karttaa-tila! true)
           (e! (tiedot/->AjaTaustahaku))
           (e! (tiedot/->HaeAluesuodattimet true))
           ;; Karttatason näyttäminen/piilottaminen täytyy tehdä täällä,
           ;; koska aktiivinen tila voi olla tienäkymä, eikä tienäkymässä
           ;; haluta näyttää esim. organisaatiorajoja. Jos tienäkymä
           ;; hallitsisi itse tason näkyvyyttä, ei se voisi tietää,
           ;; poistuttiinko tienäkymästä nykytilanteeseen (-> taso päälle)
           ;; vai toiseen näkymään (-> taso pois)
           (nayta-tai-piilota-karttataso! @tiedot/valittu-tila)
           (reset! tiedot/valittu-urakka-tilannekuvaan-tullessa @nav/valittu-urakka)
           (when (:id @nav/valittu-urakka) (tiedot/aseta-urakka-valituksi! (:id @nav/valittu-urakka)))
           (reset! kartta-tiedot/pida-geometriat-nakyvilla? false)
           (kartta-tiedot/kasittele-infopaneelin-linkit! tilannekuvan-infopaneelin-linkit)
           (tiedot/seuraa-alueita!))
      #(do (kartta/aseta-paivitetaan-karttaa-tila! false)
           (reset! tilannekuva-kartalla/karttataso-tilannekuva false)
           (kartta-tiedot/kasittele-infopaneelin-linkit! nil)
           (reset! tiedot/valittu-urakka-tilannekuvaan-tullessa nil)
           (reset! kartta-tiedot/pida-geometriat-nakyvilla?
                   kartta-tiedot/pida-geometria-nakyvilla-oletusarvo)
           (tiedot/lopeta-alueiden-seuraus!)
           (e! (tiedot/->LopetaTaustahaku))))
    (komp/karttakontrollit :tilannekuva
                           [hallintapaneeli e!])
    (fn [e! app]
      [:span.tilannekuva
       [kartta/kartan-paikka]])))
