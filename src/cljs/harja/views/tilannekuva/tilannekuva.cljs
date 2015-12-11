(ns harja.views.tilannekuva.tilannekuva
  (:require [reagent.core :refer [atom]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.tilannekuva.tilannekuva :as tiedot]
            [harja.views.kartta :as kartta]
            [harja.loki :refer [log tarkkaile!]]
            [harja.views.tilannekuva.tilannekuva-popupit :as popupit]
            [harja.ui.kentat :as kentat]
            [reagent.core :as r]
            [harja.pvm :as pvm]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.checkbox :as checkbox]
            [harja.ui.on-off-valinta :as on-off]
            [harja.ui.yleiset :as yleiset])
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

(defn nykytilanteen-aikavalinta []
    [:div#tk-nykytilanteen-aikavalit
      [kentat/tee-kentta {:tyyppi   :radio
                          :valinta-nayta (fn [[nimi _]] nimi)
                          :valinta-arvo (fn [[_ arvo]] arvo)
                          :valinnat tiedot/nykytilanteen-aikasuodatin-tunteina}
       tiedot/nykytilanteen-aikasuodattimen-arvo]])

(defn checkbox-ryhma-elementti
  "Suodatinpolku on polku, josta tämän checkboxin nimi ja tila löytyy suodattimet-atomissa"
  [nimi suodattimet-atom suodatinpolku]
  [checkbox/checkbox
   (reaction (checkbox/boolean->checkbox-tila-keyword (get-in @suodattimet-atom suodatinpolku)))
   nimi
   {:display   "block"
    :on-change (fn [uusi-tila]
                 (reset! suodattimet-atom
                         (assoc-in
                           @suodattimet-atom
                           suodatinpolku
                           (checkbox/checkbox-tila-keyword->boolean uusi-tila))))}])

(defn checkbox-ryhma
  "Ryhmäpolku on polku, josta tämän checkbox-ryhmän jäsenten nimet ja tilat löytyvät suodattimet-atomissa"
  [otsikko suodattimet-atom ryhma-polku]
  (let [auki? (atom false)
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
          [:span.tk-checkbox-ryhma-tila {:on-click (fn []
                                                     (swap! auki? not)
                                                     (aseta-hallintapaneelin-max-korkeus (yleiset/elementti-idlla "tk-suodattimet")))}
           (if @auki? (ikonit/chevron-down) (ikonit/chevron-right))]
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

         (when @auki?
           [:div.tk-checkbox-ryhma-sisalto
            (doall (for [elementti (seq @ryhman-elementit-ja-tilat)]
                     ^{:key (str "pudotusvalikon-asia-" (get tiedot/suodattimien-nimet (first elementti)))}
                     [checkbox-ryhma-elementti
                      (get tiedot/suodattimien-nimet (first elementti))
                      suodattimet-atom
                      (conj ryhma-polku (first elementti))]))])]))))

(defn nykytilanteen-aikasuodattimet []
  [:div#tk-nykytila-paavalikko
   [:span "Näytä seuraavat aikavälillä:"]
   [nykytilanteen-aikavalinta]
   [checkbox-ryhma "Talvihoitotyöt" tiedot/suodattimet [:talvi]]
   [checkbox-ryhma "Kesähoitotyöt" tiedot/suodattimet [:kesa]]
   [checkbox-ryhma "Laadunseuranta" tiedot/suodattimet [:laadunseuranta]]
   [checkbox-ryhma "Turvallisuus" tiedot/suodattimet [:turvallisuus]]])

(defn suodattimet []
  (let [resize-kuuntelija (fn [this _]
                            (aseta-hallintapaneelin-max-korkeus (r/dom-node this)))]
    (komp/luo
      (komp/dom-kuuntelija js/window
                           EventType/RESIZE resize-kuuntelija)
      (fn []
        [:div#tk-suodattimet {:style {:max-height @hallintapaneeli-max-korkeus :overflow "auto"}}
         [tilan-vaihtaja]
         [checkbox-ryhma "Ilmoitukset" tiedot/suodattimet [:ilmoitukset :tyypit]]
         [checkbox-ryhma "Ylläpito" tiedot/suodattimet [:yllapito]]
         (when (= :nykytilanne @tiedot/valittu-tila)
           [nykytilanteen-aikasuodattimet])]))))

(def hallintapaneeli (atom {1 {:auki true :otsikko "Tilannekuva" :sisalto [suodattimet]}}))

(defn tilannekuva []
  (komp/luo
    (komp/lippu tiedot/nakymassa? tiedot/karttataso-tilannekuva)
    (komp/sisaan-ulos #(reset! kartta/pida-geometriat-nakyvilla? false) #(reset! kartta/pida-geometriat-nakyvilla? true))
    (komp/kuuntelija [:toteuma-klikattu :reittipiste-klikattu :ilmoitus-klikattu
                      :laatupoikkeama-klikattu :tarkastus-klikattu :turvallisuuspoikkeama-klikattu
                      :paallystys-klikattu :paikkaus-klikattu :tyokone-klikattu
                      :uusi-tyokonedata] (fn [_ tapahtuma] (popupit/nayta-popup tapahtuma))
                     :popup-suljettu #(reset! popupit/klikattu-tyokone nil))
    {:component-will-mount   (fn [_]
                               (kartta/aseta-yleiset-kontrollit
                                 [yleiset/haitari hallintapaneeli {:piiloita-kun-kiinni? true
                                                                   :luokka               "haitari-tilannekuva"}]))
     :component-will-unmount (fn [_]
                               (kartta/tyhjenna-yleiset-kontrollit)
                               (kartta/poista-popup!))}
    (fn []
      [:span.tilannekuva
       [kartta/kartan-paikka]])))