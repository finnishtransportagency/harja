(ns harja.views.tilannekuva.tilannekuva
  (:require [reagent.core :refer [atom]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.tilannekuva.tilannekuva :as tiedot]
            [harja.views.kartta :as kartta]
            [harja.views.tilannekuva.tilannekuva-popupit :as popupit]
            [harja.ui.kentat :as kentat]
            [reagent.core :as r]
            [harja.pvm :as pvm]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :as yleiset])
  (:require-macros [reagent.ratom :refer [reaction]]))

(defn ilmoitus-lista-elementti [[avain nimi]]
  ^{:key (str "ilmoitus_tunniste_" nimi)}
  [:li.tk-ilmoitukset-tyyppi
   [:div.tk-checkbox
    [:label
     [:input {:type    "checkbox"
              :checked true}
      nimi]]]])

(defn ilmoitukset []
  (let [auki? (atom false)
        ilmoitukset (select-keys tiedot/suodattimet
                                 [:toimenpidepyynnot :kyselyt :tiedotukset])]
    [:div#tk-ilmoitukset
     [:div.tk-pudotusvalikko-nappi {:on-click #(swap! auki? not)}
      [:div.tk-pudotusvalikko-checkbox
       [:label
        [:input {:type    "checkbox"
                 :checked true}]
        "Ilmoitukset"]
       [:span.tk-pudotusvalikko-tila (if @auki? (ikonit/chevron-down) (ikonit/chevron-up))]]]

     [:ul#tk-ilmoitukset-lista (if @auki? {:class "tk-pudotusvalikko-auki"} {:class "tk-pudotusvalikko-kiinni"})
      (doall (for [ilmoitus ilmoitukset]
               [ilmoitus-lista-elementti ilmoitus]))]]))

(defn toimenpide-lista-elementti [toimenpide]
  ^{:key (str "tunniste_tp_" (:nimi toimenpide))}
  [:li.tk-toteumat-toimenpide
   [:div.tk-checkbox
    [:label
     [:input {:type    "checkbox"
              :checked true}
      (:nimi toimenpide)]]]])

(defn toimenpiteet-toisen-tason-elementti [emo]
  (let [toimenpiteet (get @tiedot/naytettavat-toteumatyypit emo)
        auki? (atom false)]
    ^{:key (str "tunniste_emo_" emo)}
    [:li.tk-toteumat-toinen-taso-li
     [:div.tk-pudotusvalikko-nappi {:on-click #(swap! auki? not)}
      [:div.tk-pudotusvalikko-checkbox
       [:label
        [:input {:type    "checkbox"
                 :checked true}]
        emo]
       [:span.tk-pudotusvalikko-tila (if @auki? (ikonit/chevron-down) (ikonit/chevron-up))]]]
     [:ul#tk-toteumat-kolmas-taso (if @auki? {:class "tk-pudotusvalikko-auki"} {:class "tk-pudotusvalikko-kiinni"})
      (doall (for [toimenpide toimenpiteet]
               [toimenpide-lista-elementti toimenpide]))]]))

(defn toimenpiteet []
  (let [auki? (atom false)
        toisen-tason-napit (sort (keys @tiedot/naytettavat-toteumatyypit))]
    [:div#tk-toteumat
     [:div.tk-pudotusvalikko-nappi {:on-click #(swap! auki? not)}
      [:div.tk-pudotusvalikko-checkbox
       [:label
        [:input {:type    "checkbox"
                 :checked true}]
        "Toteumat"]
       [:span.tk-pudotusvalikko-tila (if @auki? (ikonit/chevron-down) (ikonit/chevron-up))]]]

     [:ul#tk-toteumat-toinen-taso (if @auki? {:class "tk-pudotusvalikko-auki"} {:class "tk-pudotusvalikko-kiinni"})
      (doall (for [emo toisen-tason-napit]
               [toimenpiteet-toisen-tason-elementti emo]))]
     ]))

(defn historiakuvan-aikavalitsin []
  [:span#tk-aikavalitsin
   [kentat/tee-kentta {:tyyppi :pvm :absoluuttinen? true}
    (r/wrap (first @tiedot/historiakuvan-aikavali)
            (fn [u]
              (swap! tiedot/historiakuvan-aikavali assoc 0 u)
              (when (apply pvm/jalkeen? @tiedot/historiakuvan-aikavali)
                (swap! tiedot/historiakuvan-aikavali assoc 1 (second (pvm/kuukauden-aikavali u))))))]

   [kentat/tee-kentta {:tyyppi :pvm :absoluuttinen? true}
    (r/wrap (second @tiedot/historiakuvan-aikavali)
            (fn [u]
              (swap! tiedot/historiakuvan-aikavali assoc 1 u)
              (when (apply pvm/jalkeen? @tiedot/historiakuvan-aikavali)
                (swap! tiedot/historiakuvan-aikavali assoc 0 (first (pvm/kuukauden-aikavali u))))))]])

(defn tilan-vaihtaja []
  [:div#tk-tilan-vaihtajat
   [:div.tk-radio
    [:label
     [:input {:type      "radio"
              :value     0
              :checked   true
              :on-change #(reset! tiedot/valittu-tila :nykytilanne)}]
     "Nykytilanne"]]
   [:div.tk-radio
    [:label
     [:input {:type      "radio"
              :value     1
              :checked   true
              :on-change #(reset! tiedot/valittu-tila :historiakuva)}]
     "Historiakuva"]]])

(defn muu-suodatin [[arvo nimi]]
  [:li.tk-paataso-li
   [:div.tk-checkbox
    [:label
     [:input {:type    "checkbox"
              :checked true}
      nimi]]]])

(defn tilannekuvan-kontrollit []
  [:ul#tilannekuvan-kontrollit
   [:li.tk-paataso-li [tilan-vaihtaja]]
   (when-not (= :nykytilanne @tiedot/valittu-tila) [:li.tk-paataso-li [historiakuvan-aikavalitsin]])
   [:li.tk-paataso-li [toimenpiteet]]
   [:li.tk-paataso-li [ilmoitukset]]
   (doall (for [suodatin (dissoc tiedot/suodattimet :toimenpidepyynnot :kyselyt :tiedotukset)]
            ^{:key (first suodatin)}
            [muu-suodatin suodatin]))])

(defonce suodattimet [:span
                      [tilan-vaihtaja]
                      ;; [aikavalinta] TODO: (when historia [aikavalinta])
                      [ilmoitukset]])

(defonce hallintapaneeli (atom {1 {:auki true :otsikko "Tilannekuva" :sisalto suodattimet}}))

(defn tilannekuva []
  (komp/luo
    (komp/lippu tiedot/nakymassa? tiedot/karttataso-tilannekuva)
    (komp/sisaan-ulos #(reset! kartta/pida-geometriat-nakyvilla? false) #(reset! kartta/pida-geometriat-nakyvilla? true))
    (komp/kuuntelija [:toteuma-klikattu :reittipiste-klikattu :ilmoitus-klikattu
                      :havainto-klikattu :tarkastus-klikattu :turvallisuuspoikkeama-klikattu
                      :paallystyskohde-klikattu :paikkaustoteuma-klikattu :tyokone-klikattu
                      :uusi-tyokonedata] (fn [_ tapahtuma] (popupit/nayta-popup tapahtuma))
                     :popup-suljettu #(reset! popupit/klikattu-tyokone nil))
    {:component-will-mount   (fn [_]
                               (kartta/aseta-yleiset-kontrollit
                                 [yleiset/haitari hallintapaneeli {:piiloita-kun-kiinni? true}]))
     :component-will-unmount (fn [_]
                               (kartta/tyhjenna-yleiset-kontrollit)
                               (kartta/poista-popup!))}
    (fn []
      [:span.tilannekuva
       [kartta/kartan-paikka]])))