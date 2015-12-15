(ns harja.views.tilannekuva.tilannekuva
  (:require [reagent.core :refer [atom]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.tilannekuva.tilannekuva :as tiedot]
            [harja.tiedot.tilannekuva.tilannekuva-kartalla :as tilannekuva-kartalla]
            [harja.views.kartta :as kartta]
            [harja.ui.valinnat :as ui-valinnat]
            [harja.loki :refer [log tarkkaile!]]
            [harja.views.tilannekuva.tilannekuva-popupit :as popupit]
            [harja.views.murupolku :as murupolku]
            [harja.ui.kentat :as kentat]
            [reagent.core :as r]
            [harja.pvm :as pvm]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.checkbox :as checkbox]
            [harja.ui.on-off-valinta :as on-off]
            [harja.ui.yleiset :as yleiset]
            [harja.views.urakka.valinnat :as urakka-valinnat])
  (:require-macros [reagent.ratom :refer [reaction]]))

(def hallintapaneeli-max-korkeus (atom nil))
(defn aseta-hallintapaneelin-max-korkeus [paneelin-sisalto]
  (let [r (.getBoundingClientRect paneelin-sisalto)
        etaisyys-alareunaan (- @yleiset/korkeus (.-top r))]
    (reset! hallintapaneeli-max-korkeus (max
                                          200
                                          (- etaisyys-alareunaan 30)))))
(defn tilan-vaihtaja []
  (let [on-off-tila (atom (if (= :nykytilanne @tiedot/valittu-tila)
                            false
                            true))]
    (fn []
      [:div#tk-tilan-vaihto
       [:div.tk-tilan-vaihto-nykytilanne "Nykytilanne"]
       [:div.tk-tilan-vaihto-historia "Historia"]
       [on-off/on-off-valinta on-off-tila {:luokka    "on-off-tilannekuva"
                                           :on-change (fn []
                                                        (if (false? @on-off-tila)
                                                          (reset! tiedot/valittu-tila :nykytilanne)
                                                          (reset! tiedot/valittu-tila :historiakuva)))}]])))

(defn nykytilanteen-aikavalinnat []
  [:div#tk-nykytilanteen-aikavalit
   [kentat/tee-kentta {:tyyppi        :radio
                       :valinta-nayta (fn [[nimi _]] nimi)
                       :valinta-arvo  (fn [[_ arvo]] arvo)
                       :valinnat      tiedot/nykytilanteen-aikasuodatin-tunteina}
    tiedot/nykytilanteen-aikasuodattimen-arvo]])

(defn historiankuvan-aikavalinnat []
  [:div#tk-historiakuvan-aikavalit
   [ui-valinnat/aikavali tiedot/historiakuvan-aikavali {:nayta-otsikko? false
                                                        :salli-pitka-aikavali? true
                                                        :aloitusaika-pakota-suunta :alas-oikea
                                                        :paattymisaika-pakota-suunta :alas-vasen}]])

(defn yksittainen-suodatincheckbox
  "suodatin-polku on polku, josta tämän checkboxin nimi ja tila löytyy suodattimet-atomissa"
  [nimi suodattimet-atom suodatin-polku]
  [checkbox/checkbox
   (reaction (checkbox/boolean->checkbox-tila-keyword (get-in @suodattimet-atom suodatin-polku)))
   nimi
   {:display   "block"
    :on-change (fn [uusi-tila]
                 (reset! suodattimet-atom
                         (assoc-in
                           @suodattimet-atom
                           suodatin-polku
                           (checkbox/checkbox-tila-keyword->boolean uusi-tila))))}])

(def auki-oleva-checkbox-ryhma (atom nil))

(defn checkbox-suodatinryhma
  "ryhma-polku on polku, josta tämän checkbox-ryhmän jäsenten nimet ja tilat löytyvät suodattimet-atomissa
   kokoelma-atomin antaminen tarkoittaa, että checkbox-ryhmä on osa usean checkbox-ryhmän kokoelmaa, joista
   vain atomin ilmoittama ryhmä voi olla kerrallaan auki. Jos kokoelmaa ei anneta, tämä checkbox-ryhmä ylläpitää
   itse omaa auki/kiinni-tilaansa."
  [otsikko suodattimet-atom ryhma-polku kokoelma-atom]
  (let [oma-auki-tila (atom false)
        auki? (fn [] (or @oma-auki-tila
                         (and kokoelma-atom
                              (= otsikko @kokoelma-atom))))
        ryhmanjohtaja-tila-atom (reaction
                                  (if (every? true? (vals (get-in @suodattimet-atom ryhma-polku)))
                                    :valittu
                                    (if (every? false? (vals (get-in @suodattimet-atom ryhma-polku)))
                                      :ei-valittu
                                      :osittain-valittu)))]
    (fn []
      (let [ryhman-elementit-ja-tilat (atom (get-in @suodattimet-atom ryhma-polku))]
        @suodattimet-atom
        [:div.tk-checkbox-ryhma
         [:div.tk-checkbox-ryhma-otsikko
          [:span {:class    (str
                              "tk-checkbox-ryhma-tila chevron-rotate "
                              (when-not (auki?) "chevron-rotate-down"))
                  :on-click (fn []
                              (if kokoelma-atom
                                ; Osa kokoelmaa
                                (if (= otsikko @kokoelma-atom)
                                  (reset! kokoelma-atom nil)
                                  (reset! kokoelma-atom otsikko))
                                ; Ylläpitää itse omaa tilaansa
                                (swap! oma-auki-tila not))
                              (aseta-hallintapaneelin-max-korkeus (yleiset/elementti-idlla "tk-suodattimet")))}
           (if (auki?)
             (ikonit/chevron-down) (ikonit/chevron-right))]
          [:div.tk-checkbox-ryhma-checkbox
           [checkbox/checkbox ryhmanjohtaja-tila-atom otsikko
            {:display   "inline-block"
             :on-change (fn [uusi-tila]
                          ; Aseta kaikkien tämän ryhmän suodattimien tilaksi tämän elementin uusi tila.
                          (when (not= :osittain-valittu uusi-tila)
                            (reset! suodattimet-atom
                                    (reduce (fn [edellinen-map tehtava-avain]
                                              (assoc-in edellinen-map
                                                        (conj ryhma-polku tehtava-avain)
                                                        (checkbox/checkbox-tila-keyword->boolean uusi-tila)))
                                            @suodattimet-atom
                                            (keys (get-in @suodattimet-atom ryhma-polku))))))}]]]

         (when (auki?)
           [:div.tk-checkbox-ryhma-sisalto
            (doall (for [elementti (seq @ryhman-elementit-ja-tilat)]
                     ^{:key (str "pudotusvalikon-asia-" (get tiedot/suodattimien-nimet (first elementti)))}
                     [yksittainen-suodatincheckbox
                      (get tiedot/suodattimien-nimet (first elementti))
                      suodattimet-atom
                      (conj ryhma-polku (first elementti))]))])]))))

(defn aikasuodattimet []
  [:div#tk-paavalikko
   [:span "Näytä seuraavat aikavälillä:"]
   (when (= :nykytilanne @tiedot/valittu-tila)
     [nykytilanteen-aikavalinnat])
   (when (= :historiakuva @tiedot/valittu-tila)
     [historiankuvan-aikavalinnat])
   [:div.tk-suodatinryhmat
    (when (= :historiakuva @tiedot/valittu-tila)
      [:div.tk-suodatinryhmat
       [checkbox-suodatinryhma "Ilmoitukset" tiedot/suodattimet [:ilmoitukset :tyypit] auki-oleva-checkbox-ryhma]
       [checkbox-suodatinryhma "Ylläpito" tiedot/suodattimet [:yllapito] auki-oleva-checkbox-ryhma]])
    [checkbox-suodatinryhma "Talvihoitotyöt" tiedot/suodattimet [:talvi] auki-oleva-checkbox-ryhma]
    [checkbox-suodatinryhma "Kesähoitotyöt" tiedot/suodattimet [:kesa] auki-oleva-checkbox-ryhma]]
   [:div.tk-yksittaiset-suodattimet
    [yksittainen-suodatincheckbox "Laatupoikkeamat" tiedot/suodattimet [:laadunseuranta :laatupoikkeamat] auki-oleva-checkbox-ryhma]
    [yksittainen-suodatincheckbox "Tarkastukset" tiedot/suodattimet [:laadunseuranta :tarkastukset] auki-oleva-checkbox-ryhma]
    [yksittainen-suodatincheckbox "Turvallisuuspoikkeamat" tiedot/suodattimet [:turvallisuus :turvallisuuspoikkeamat] auki-oleva-checkbox-ryhma]]])

(defn suodattimet []
  (let [resize-kuuntelija (fn [this _]
                            (aseta-hallintapaneelin-max-korkeus (r/dom-node this)))]
    (komp/luo
      (komp/dom-kuuntelija js/window
                           EventType/RESIZE resize-kuuntelija)
      (fn []
        [:div#tk-suodattimet {:style {:max-height @hallintapaneeli-max-korkeus :overflow "auto"}}
         [tilan-vaihtaja]
         (when (= :nykytilanne @tiedot/valittu-tila)
           [checkbox-suodatinryhma "Ilmoitukset" tiedot/suodattimet [:ilmoitukset :tyypit] auki-oleva-checkbox-ryhma])
         (when (= :nykytilanne @tiedot/valittu-tila)
           [checkbox-suodatinryhma "Ylläpito" tiedot/suodattimet [:yllapito] auki-oleva-checkbox-ryhma])
         [aikasuodattimet]]))))

(def hallintapaneeli (atom {1 {:auki true :otsikko "Hallintapaneeli" :sisalto [suodattimet]}}))

(defn tilannekuva []
  (komp/luo
    (komp/lippu tiedot/nakymassa? tilannekuva-kartalla/karttataso-tilannekuva)
    (komp/sisaan-ulos #(do (murupolku/aseta-murupolku-muotoon :tilannekuva)
                           (reset! kartta/pida-geometriat-nakyvilla? false))
                      #(do (murupolku/aseta-murupolku-perusmuotoon)
                           (reset! kartta/pida-geometriat-nakyvilla? true)
                           (kartta/aseta-paivitetaan-karttaa-tila false)))
    (komp/kuuntelija [:toteuma-klikattu :reittipiste-klikattu :ilmoitus-klikattu
                      :laatupoikkeama-klikattu :tarkastus-klikattu :turvallisuuspoikkeama-klikattu
                      :paallystys-klikattu :paikkaus-klikattu :tyokone-klikattu
                      :uusi-tyokonedata] (fn [_ tapahtuma] (popupit/nayta-popup tapahtuma))
                     :popup-suljettu #(reset! popupit/klikattu-tyokone nil))
    {:component-will-mount   (fn [_]
                               (kartta/aseta-yleiset-kontrollit
                                 [yleiset/haitari hallintapaneeli {:piiloita-kun-kiinni? false
                                                                   :luokka               "haitari-tilannekuva"}]))
     :component-will-unmount (fn [_]
                               (kartta/tyhjenna-yleiset-kontrollit)
                               (kartta/poista-popup!))}
    (fn []
      [:span.tilannekuva
       [kartta/kartan-paikka]])))