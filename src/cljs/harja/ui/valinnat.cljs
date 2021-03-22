(ns harja.ui.valinnat
  "Yleisiä valintoihin liittyviä komponentteja.
  Refaktoroitu vanhasta harja.views.urakka.valinnat namespacesta."
  (:require [reagent.core :refer [atom] :as r]

            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [livi-pudotusvalikko]]
            [harja.domain.tierekisteri.varusteet :as varusteet]
            [harja.fmt :as fmt]
            [clojure.string :as str]
            [cljs-time.core :as t]
            [goog.events.EventType :as EventType]
            [harja.ui.lomake :as lomake]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.ui.dom :as dom]
            [harja.domain.urakka :as u-domain]
            [harja.loki :as log])
  (:require-macros [harja.tyokalut.ui :refer [for*]]
                   [cljs.core.async.macros :refer [go]]))

(defn urakan-sopimus
  ([ur valittu-sopimusnumero-atom valitse-fn] (urakan-sopimus ur valittu-sopimusnumero-atom valitse-fn {}))
  ([ur valittu-sopimusnumero-atom valitse-fn {:keys [kaikki-valinta?] :as optiot}]
   [:div.label-ja-alasveto.sopimusnumero
    [:span.alasvedon-otsikko "Sopimusnumero"]
    [livi-pudotusvalikko {:valinta @valittu-sopimusnumero-atom
                          :format-fn second
                          :valitse-fn valitse-fn
                          :li-luokka-fn #(when (= (first %) (:paasopimus ur))
                                           "bold")}
     (if kaikki-valinta?
       (merge (:sopimukset ur) {nil "Kaikki"})
       (:sopimukset ur))]]))

(defn urakkatyyppi
  [valittu-urakkatyyppi-atom urakkatyypit valitse-fn]
  [:div.label-ja-alasveto.urakkatyyppi
   [:span.alasvedon-otsikko "Urakkatyyppi"]
   [livi-pudotusvalikko {:valinta @valittu-urakkatyyppi-atom
                         :format-fn :nimi
                         :valitse-fn valitse-fn}
    urakkatyypit]])

(defn urakan-hoitokausi
  [ur hoitokaudet valittu-hoitokausi-atom valitse-fn]
  [:div.label-ja-alasveto.hoitokausi
   [:span.alasvedon-otsikko (cond
                              (= (:tyyppi ur) :hoito) "Hoitokausi"
                              (u-domain/vesivaylaurakkatyyppi? (:tyyppi ur)) "Urakkavuosi"
                              :default "Sopimuskausi")]
   [livi-pudotusvalikko {:valinta @valittu-hoitokausi-atom
                         :format-fn #(if % (fmt/pvm-vali-opt %) "Valitse")
                         :valitse-fn valitse-fn}
    @hoitokaudet]])

(defn hoitokausi
  ([hoitokaudet valittu-hoitokausi-atom]
   (hoitokausi {} hoitokaudet valittu-hoitokausi-atom #(reset! valittu-hoitokausi-atom %)))
  ([hoitokaudet valittu-hoitokausi-atom valitse-fn]
   (hoitokausi {} hoitokaudet valittu-hoitokausi-atom valitse-fn))
  ([{:keys [disabled disabloi-tulevat-hoitokaudet?]} hoitokaudet valittu-hoitokausi-atom valitse-fn]
   (let [nyt (pvm/nyt)
         disabled-vaihtoehdot (when disabloi-tulevat-hoitokaudet?
                                (into #{}
                                      (filter #(pvm/jalkeen? (first %) nyt))
                                      hoitokaudet))]
     [:div.label-ja-alasveto.hoitokausi
      [:span.alasvedon-otsikko "Hoitokausi"]
      [livi-pudotusvalikko {:valinta @valittu-hoitokausi-atom
                            :disabled disabled
                            :format-fn #(if % (fmt/pvm-vali-opt %) "Valitse")
                            :disabled-vaihtoehdot disabled-vaihtoehdot
                            :valitse-fn valitse-fn}
       hoitokaudet]])))

(defn kuukausi [{:keys [disabled nil-valinta disabloi-tulevat-kk?] :or {disabloi-tulevat-kk? false}} kuukaudet valittu-kuukausi-atom]
  (let [nyt (pvm/nyt)
        format-fn (r/partial
                    (fn [kuukausi]
                      (if kuukausi
                        (let [[alkupvm _] kuukausi
                              kk-teksti (pvm/kuukauden-nimi (pvm/kuukausi alkupvm))]
                          (str (str/capitalize kk-teksti) " " (pvm/vuosi alkupvm)))
                        (or nil-valinta "Kaikki"))))
        valitse-fn (r/partial
                     (fn [kuukausi]
                       (reset! valittu-kuukausi-atom kuukausi)))
        disabled-vaihtoehdot (when disabloi-tulevat-kk?
                               (into #{}
                                     (filter #(pvm/jalkeen? (first %) nyt))
                                     kuukaudet))]
    [:div.label-ja-alasveto.kuukausi
     [:span.alasvedon-otsikko "Kuukausi"]
     [livi-pudotusvalikko {:valinta @valittu-kuukausi-atom
                           :disabled disabled
                           :disabled-vaihtoehdot disabled-vaihtoehdot
                           :format-fn format-fn
                           :valitse-fn valitse-fn}
      kuukaudet]]))

(defn hoitokauden-kuukausi
  [hoitokauden-kuukaudet valittu-kuukausi-atom valitse-fn]
  [:div.label-ja-alasveto.kuukausi
   [:span.alasvedon-otsikko "Kuukausi"]
   [livi-pudotusvalikko {:valinta @valittu-kuukausi-atom
                         :format-fn #(if %
                                       (let [[alkupvm _] %
                                             kk-teksti (pvm/kuukauden-nimi (pvm/kuukausi alkupvm))]
                                         (str (str/capitalize kk-teksti) " " (pvm/vuosi alkupvm)))
                                       "Koko hoitokausi")
                         :valitse-fn valitse-fn}
    hoitokauden-kuukaudet]])

(defn aikavali
  ([valittu-aikavali-atom] [aikavali valittu-aikavali-atom nil])
  ([valittu-aikavali-atom asetukset]
   (let [aikavalin-alku (atom (first @valittu-aikavali-atom))
         aikavalin-loppu (atom (second @valittu-aikavali-atom))
         asetukset-atom (atom asetukset)
         uusi-aikavali (fn [paa uusi-arvo]
                         {:pre [(contains? #{:alku :loppu} paa)]}
                         (let [uusi-arvo (if (= :alku paa)
                                           (pvm/paivan-alussa-opt uusi-arvo)
                                           (pvm/paivan-lopussa-opt uusi-arvo))
                               aikavalin-rajoitus (:aikavalin-rajoitus @asetukset-atom)
                               aikavali (if (= :alku paa)
                                          [uusi-arvo @aikavalin-loppu]
                                          [@aikavalin-alku uusi-arvo])]
                           (if-not aikavalin-rajoitus
                             (pvm/varmista-aikavali-opt aikavali paa)
                             (pvm/varmista-aikavali-opt aikavali aikavalin-rajoitus paa))))
         tarkasta-esitettavat-arvot! (fn [uusi-aikavali]
                                       (r/next-tick (fn []
                                                      (let [[uusi-alku uusi-loppu] uusi-aikavali]
                                                        (when-not (= @aikavalin-alku uusi-alku)
                                                          (reset! aikavalin-alku uusi-alku))
                                                        (when-not (= @aikavalin-loppu uusi-loppu)
                                                          (reset! aikavalin-loppu uusi-loppu))))))]
     (komp/luo
       (komp/sisaan-ulos #(do
                            (add-watch aikavalin-alku :ui-valinnat-aikavalin-alku
                                       (fn [_ _ vanha-arvo uusi-arvo]
                                         (let [uusi-arvo (uusi-aikavali :alku uusi-arvo)]
                                           (tarkasta-esitettavat-arvot! uusi-arvo)
                                           (when-not (= vanha-arvo uusi-arvo)
                                             (reset! valittu-aikavali-atom uusi-arvo))
                                           (log "Uusi aikaväli: " (pr-str uusi-arvo)))))
                            (add-watch aikavalin-loppu :ui-valinnat-aikavalin-loppu
                                       (fn [_ _ vanha-arvo uusi-arvo]
                                         (let [uusi-arvo (uusi-aikavali :loppu uusi-arvo)]
                                           (tarkasta-esitettavat-arvot! uusi-arvo)
                                           (when-not (= vanha-arvo uusi-arvo)
                                             (reset! valittu-aikavali-atom uusi-arvo))
                                           (log "Uusi aikaväli: " (pr-str uusi-arvo)))))
                            (add-watch valittu-aikavali-atom
                                       :aikavali-komponentin-kuuntelija
                                       (fn [_ _ _ uusi-arvo]
                                         (let [alku (first uusi-arvo)
                                               loppu (second uusi-arvo)]
                                           (when-not (= alku @aikavalin-alku)
                                             (reset! aikavalin-alku alku))
                                           (when-not (= loppu @aikavalin-loppu)
                                             (reset! aikavalin-loppu loppu))))))
                         #(do
                            (remove-watch aikavalin-alku :ui-valinnat-aikavalin-alku)
                            (remove-watch aikavalin-loppu :ui-valinnat-aikavalin-loppu)
                            (remove-watch valittu-aikavali-atom :aikavali-komponentin-kuuntelija)))
       (fn [_ {:keys [nayta-otsikko? aikavalin-rajoitus
                      aloitusaika-pakota-suunta paattymisaika-pakota-suunta
                      lomake? otsikko validointi]}]
         (when-not (= aikavalin-rajoitus (:aikavalin-rajoitus @asetukset-atom))
           (swap! asetukset-atom assoc :aikavalin-rajoitus aikavalin-rajoitus))
         [:span {:class (if lomake?
                          "label-ja-aikavali-lomake"
                          "label-ja-aikavali")}
          (when (and (not lomake?)
                     (or (nil? nayta-otsikko?)
                         (true? nayta-otsikko?)))
            [:span.alasvedon-otsikko (or otsikko "Aikaväli")])
          [:div.aikavali-valinnat
           [tee-kentta {:tyyppi :pvm :pakota-suunta aloitusaika-pakota-suunta :validointi validointi}
            aikavalin-alku]
           [:div.pvm-valiviiva-wrap [:span.pvm-valiviiva " \u2014 "]]
           [tee-kentta {:tyyppi :pvm :pakota-suunta paattymisaika-pakota-suunta :validointi validointi}
            aikavalin-loppu]]])))))

(defn numerovali
  ([valittu-numerovali-atom] (numerovali valittu-numerovali-atom nil))
  ([valittu-numerovali-atom {:keys [nayta-otsikko? lomake? otsikko
                                    vain-positiivinen?]}]
   [:span {:class (if lomake?
                    "label-ja-numerovali-lomake"
                    "label-ja-numerovali")}
    (when (and (not lomake?)
               (or (nil? nayta-otsikko?)
                   (true? nayta-otsikko?)))
      [:span.alasvedon-otsikko (or otsikko "Väli")])
    [:div.numerovali-valinnat
     [:span.numerovali-kentta
      [tee-kentta {:tyyppi (if vain-positiivinen? :positiivinen-numero :numero)}
       (r/wrap (first @valittu-numerovali-atom)
               (fn [uusi-arvo]
                 (reset! valittu-numerovali-atom [uusi-arvo (second @valittu-numerovali-atom)])
                 (log "Uusi numeroväli: " (pr-str @valittu-numerovali-atom))))]]
     [:div.pvm-valiviiva-wrap [:span.pvm-valiviiva " \u2014 "]]
     [:span.numerovali-kentta
      [tee-kentta {:tyyppi (if vain-positiivinen? :positiivinen-numero :numero)}
       (r/wrap (second @valittu-numerovali-atom)
               (fn [uusi-arvo]
                 (reset! valittu-numerovali-atom [(first @valittu-numerovali-atom) uusi-arvo])
                 (log "Uusi numeroväli: " (pr-str @valittu-numerovali-atom))))]]]]))

(defn- toimenpideinstanssi-fmt
  [tpi]
  (if-let [tpi-nimi (:tpi_nimi tpi)]
    (clojure.string/replace tpi-nimi #"alueurakka" "AU")
    "Ei toimenpidettä"))

(defn urakan-toimenpide
  [urakan-toimenpideinstanssit-atom valittu-toimenpideinstanssi-atom valitse-fn]
  (when (not (some
               #(= % @valittu-toimenpideinstanssi-atom)
               @urakan-toimenpideinstanssit-atom))
    ; Nykyisessä valintalistassa ei ole valittua arvoa, resetoidaan.
    (reset! valittu-toimenpideinstanssi-atom (first @urakan-toimenpideinstanssit-atom)))
  [:div.label-ja-alasveto.toimenpide
   [:span.alasvedon-otsikko "Toimenpide"]
   [livi-pudotusvalikko {:valinta @valittu-toimenpideinstanssi-atom
                         :format-fn #(toimenpideinstanssi-fmt %)
                         :valitse-fn valitse-fn}
    @urakan-toimenpideinstanssit-atom]])

(defn urakan-kokonaishintainen-tehtava
  [urakan-kokonaishintaiset-tehtavat-atom
   valittu-kokonaishintainen-tehtava-atom
   valitse-kokonaishintainen-tehtava-fn]
  [:span
   [:div.label-ja-alasveto
    [:span.alasvedon-otsikko "Tehtävä"]
    [livi-pudotusvalikko {:valinta @valittu-kokonaishintainen-tehtava-atom
                          :format-fn #(if % (str (:nimi %)) "Ei tehtävää")
                          :valitse-fn valitse-kokonaishintainen-tehtava-fn}
     @urakan-kokonaishintaiset-tehtavat-atom]]])

(defn urakan-yksikkohintainen-tehtava
  [urakan-yksikkohintainen-tehtavat-atom
   valittu-yksikkohintainen-tehtava-atom
   valitse-yksikkohintainen-tehtava-fn]
  [:span
   [:div.label-ja-alasveto
    [:span.alasvedon-otsikko "Tehtävä"]
    [livi-pudotusvalikko {:valinta @valittu-yksikkohintainen-tehtava-atom
                          :format-fn #(if % (str (:nimi %)) "Ei tehtävää")
                          :valitse-fn valitse-yksikkohintainen-tehtava-fn}
     @urakan-yksikkohintainen-tehtavat-atom]]])

(defn urakan-tehtava
  [urakan-tehtavat-atom
   valittu-urakan-tehtava-atom
   valitse-urakan-tehtava-fn]
  (when (not (some
               #(= % @valittu-urakan-tehtava-atom)
               @urakan-tehtavat-atom))
    ; Nykyisessä valintalistassa ei ole valittua arvoa, resetoidaan.
    (reset! valittu-urakan-tehtava-atom (first @urakan-tehtavat-atom)))
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko "Tehtävä"]
   [livi-pudotusvalikko {:valinta @valittu-urakan-tehtava-atom
                         :format-fn #(if % (str (:t4_nimi %)) "Ei muutoshintaista tehtävää")
                         :valitse-fn valitse-urakan-tehtava-fn}
    @urakan-tehtavat-atom]])

(defn kanavaurakan-kohde
  [kohteet-atom valittu-kohde-atom valitse-kohde-fn]
  (when (not (some
               #(= % @valittu-kohde-atom)
               @kohteet-atom))
    ; Nykyisessä valintalistassa ei ole valittua arvoa, resetoidaan.
    (reset! valittu-kohde-atom (first @kohteet-atom)))
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko "Kohde"]
   [livi-pudotusvalikko {:valinta @valittu-kohde-atom
                         :format-fn #(if % (str (:harja.domain.kanavat.kohde/nimi %)) "Ei kohteita")
                         :valitse-fn #(reset! valittu-kohde-atom %)}
    @kohteet-atom]])

(defn urakan-valinnat [urakka {:keys [sopimus hoitokausi kuukausi toimenpide aikavali-optiot tehtava] :as optiot}]
  [:span
   (when-let [{:keys [valittu-sopimusnumero-atom valitse-sopimus-fn optiot]} sopimus]
     [urakan-sopimus urakka valittu-sopimusnumero-atom valitse-sopimus-fn optiot])
   (when-let [{:keys [hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn]} hoitokausi]
     [urakan-hoitokausi urakka hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn])
   (when-let [{:keys [hoitokauden-kuukaudet valittu-kuukausi-atom valitse-kuukausi-fn]} kuukausi]
     [hoitokauden-kuukausi hoitokauden-kuukaudet valittu-kuukausi-atom valitse-kuukausi-fn])
   (when-let [{:keys [urakan-toimenpideinstassit-atom valittu-toimenpideinstanssi-atom valitse-toimenpide-fn]} toimenpide]
     [urakan-toimenpide urakan-toimenpideinstassit-atom valittu-toimenpideinstanssi-atom valitse-toimenpide-fn])
   (when-let [{:keys [valittu-aikavali-atom]} aikavali-optiot]
     [aikavali valittu-aikavali-atom])])

(defn vuosi
  ([ensimmainen-vuosi viimeinen-vuosi valittu-vuosi-atom]
   (vuosi {}
          ensimmainen-vuosi viimeinen-vuosi valittu-vuosi-atom
          #(reset! valittu-vuosi-atom %)))
  ([{:keys [disabled kaanteinen-jarjestys? kaikki-valinta?] :as optiot}
    ensimmainen-vuosi viimeinen-vuosi valittu-vuosi-atom valitse-fn]
   [:span.label-ja-aikavali-lyhyt
    [:span.alasvedon-otsikko "Vuosi"]
    [livi-pudotusvalikko {:valinta @valittu-vuosi-atom
                          :disabled disabled
                          :valitse-fn valitse-fn
                          :format-fn #(if % (if (= % :kaikki)
                                              "Kaikki"
                                              (str %))
                                            "Valitse")
                          :class "alasveto-vuosi"
                          :data-cy "valinnat-vuosi"}
     (let [vuodet (range ensimmainen-vuosi (inc viimeinen-vuosi))
           vuodet (if kaanteinen-jarjestys?
                    (reverse vuodet)
                    vuodet)
           vuodet (if kaikki-valinta?
                    (concat [:kaikki] vuodet)
                    vuodet)]
       vuodet)]]))

(defn varustetoteuman-tyyppi
  [valittu-varustetoteumatyyppi-atom]
  [:span
   [:div.label-ja-alasveto
    [:span.alasvedon-otsikko "Toimenpide"]
    [livi-pudotusvalikko {:valinta @valittu-varustetoteumatyyppi-atom
                          :format-fn #(if % (second %) "Kaikki")
                          :valitse-fn #(reset! valittu-varustetoteumatyyppi-atom %)}
     varusteet/varustetoteumatyypit]]])

(defn aikavalivalitsin
  ([otsikko aikavalit valinnat-nyt] (aikavalivalitsin otsikko aikavalit valinnat-nyt nil))
  ([otsikko aikavalit valinnat-nyt kenttien-nimet] (aikavalivalitsin otsikko aikavalit valinnat-nyt kenttien-nimet false))
  ([otsikko aikavalit valinnat-nyt kenttien-nimet vain-pvm]
   (let [vapaa-aikavali? (get-in valinnat-nyt [(or (:vakioaikavali kenttien-nimet) :vakioaikavali) :vapaa-aikavali])
         alkuaika (:alkuaika valinnat-nyt)
         vakio-aikavalikentta {:nimi (or (:vakioaikavali kenttien-nimet) :vakioaikavali)
                               :otsikko otsikko
                               :fmt :nimi
                               :tyyppi :valinta
                               :valinnat aikavalit
                               :valinta-nayta :nimi
                               :alasveto-luokka "aikavalinta"}
         alkuaikakentta {:nimi (or (:alkuaika kenttien-nimet) :alkuaika)
                         :otsikko "Alku"
                         :tyyppi (if vain-pvm :pvm :pvm-aika)
                         :validoi [[:ei-tyhja "Anna alkuaika"]]}
         loppuaikakentta {:nimi (or (:loppuaika kenttien-nimet) :loppuaika)
                          :otsikko "Loppu"
                          :tyyppi (if vain-pvm :pvm :pvm-aika)
                          :validoi [[:ei-tyhja "Anna loppuaika"]
                                    [:pvm-toisen-pvmn-jalkeen alkuaika "Loppuajan on oltava alkuajan jälkeen"]]}]

     (if vapaa-aikavali?
       (lomake/ryhma
         {:rivi? true}
         vakio-aikavalikentta
         alkuaikakentta
         loppuaikakentta)
       (lomake/ryhma
         {:rivi? true}
         vakio-aikavalikentta)))))

(defn vaylatyyppi
  [valittu-vaylatyyppi-atom vaylatyypit format-fn]
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko "Väylätyyppi"]
   [livi-pudotusvalikko {:valinta @valittu-vaylatyyppi-atom
                         :format-fn format-fn
                         :valitse-fn #(reset! valittu-vaylatyyppi-atom %)}
    vaylatyypit]])

(defn vayla
  [valittu-vayla-atom vaylat format-fn]
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko "Väylä"]
   [livi-pudotusvalikko {:valinta @valittu-vayla-atom
                         :format-fn format-fn
                         :valitse-fn #(reset! valittu-vayla-atom %)}
    vaylat]])

(defn tyolaji
  [valittu-tyolaji-atom tyolajit format-fn]
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko "Työlaji"]
   [livi-pudotusvalikko {:valinta @valittu-tyolaji-atom
                         :format-fn format-fn
                         :valitse-fn #(reset! valittu-tyolaji-atom %)}
    tyolajit]])

(defn tyoluokka
  [valittu-tyoluokka-atom tyoluokat format-fn]
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko "Työluokka"]
   [livi-pudotusvalikko {:valinta @valittu-tyoluokka-atom
                         :format-fn format-fn
                         :valitse-fn #(reset! valittu-tyoluokka-atom %)}
    tyoluokat]])

(defn toimenpide
  [valittu-toimenpide-atom toimenpiteet format-fn]
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko "Toimenpide"]
   [livi-pudotusvalikko {:valinta @valittu-toimenpide-atom
                         :format-fn format-fn
                         :valitse-fn #(reset! valittu-toimenpide-atom %)}
    toimenpiteet]])

(defn vikaluokka
  [valittu-vikaluokka-atom vikaluokat format-fn]
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko "Vikaluokka"]
   [livi-pudotusvalikko {:valinta @valittu-vikaluokka-atom
                         :format-fn format-fn
                         :valitse-fn #(reset! valittu-vikaluokka-atom %)}
    vikaluokat]])

(defn korjauksen-tila
  [valittu-korjauksen-tila-atom tilat format-fn]
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko "Korjauksen tila"]
   [livi-pudotusvalikko {:valinta @valittu-korjauksen-tila-atom
                         :format-fn format-fn
                         :valitse-fn #(reset! valittu-korjauksen-tila-atom %)}
    tilat]])

(defn paikallinen-kaytto
  [valittu-paikallinen-kaytto-atom valinnat format-fn]
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko "Paikallinen käyttö?"]
   [livi-pudotusvalikko {:valinta @valittu-paikallinen-kaytto-atom
                         :format-fn format-fn
                         :valitse-fn #(reset! valittu-paikallinen-kaytto-atom %)}
    valinnat]])

(defn kanava-kohde
  [valittu-kohde-atom kohteet format-fn]
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko "Kohde"]
   [livi-pudotusvalikko {:valinta @valittu-kohde-atom
                         :format-fn format-fn
                         :valitse-fn #(reset! valittu-kohde-atom %)}
    kohteet]])

(defn kanava-aluslaji
  [valittu-aluslaji-atom kohteet format-fn]
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko "Aluslaji"]
   [livi-pudotusvalikko {:valinta @valittu-aluslaji-atom
                         :format-fn format-fn
                         :valitse-fn #(reset! valittu-aluslaji-atom %)}
    kohteet]])

(defn urakkavalinnat [{:keys [urakka]} & sisalto]
  [:div.urakkavalinnat (when (and urakka (not (u-domain/vesivaylaurakka? urakka)))
                         {:class "urakkavalinnat-tyyliton"})
   (for* [item sisalto]
     item)])

(defn urakkatoiminnot [{:keys [sticky? urakka] :as optiot} & sisalto]
  (let [naulattu? (atom false)
        elementin-etaisyys-ylareunaan (atom nil)
        maarita-sticky! (fn []
                          (if (and
                                sticky?
                                (> (dom/scroll-sijainti-ylareunaan) (+ @elementin-etaisyys-ylareunaan 20)))
                            (reset! naulattu? true)
                            (reset! naulattu? false)))
        kasittele-scroll-event (fn [this _]
                                 (maarita-sticky!))
        kasittele-resize-event (fn [this _]
                                 (maarita-sticky!))]
    (komp/luo
      (komp/dom-kuuntelija js/window
                           EventType/SCROLL kasittele-scroll-event
                           EventType/RESIZE kasittele-resize-event)
      (komp/kun-muuttuu (fn [_ _ {:keys [disabled] :as optiot}]
                          (maarita-sticky!)))
      (komp/piirretty #(reset! elementin-etaisyys-ylareunaan
                               (dom/elementin-etaisyys-dokumentin-ylareunaan
                                 (r/dom-node %))))
      (fn [{:keys [urakka] :as optiot} & sisalto]
        [:div.urakkatoiminnot {:class (str (when @naulattu? "urakkatoiminnot-naulattu ")
                                           (when (and urakka (not (u-domain/vesivaylaurakka? urakka)))
                                             "urakkatoiminnot-tyyliton "))}
         (for* [item sisalto]
           item)]))))

(defn valintaryhmat-3 [& [ryhma1 ryhma2 ryhma3]]
  [:div.row
   [:div.valintaryhma.col-sm-12.col-md-4
    ryhma1]
   [:div.valintaryhma.col-sm-12.col-md-4
    ryhma2]
   [:div.valintaryhma.col-sm-12.col-md-4
    ryhma3]])

(defn valintaryhmat-4 [& [ryhma1 ryhma2 ryhma3 ryhma4]]
  [:div.row
   [:div.valintaryhma.col-sm-12.col-md-3
    ryhma1]
   [:div.valintaryhma.col-sm-12.col-md-3
    ryhma2]
   [:div.valintaryhma.col-sm-12.col-md-3
    ryhma3]
   [:div.valintaryhma.col-sm-12.col-md-3
    ryhma4]])

(defn checkbox-pudotusvalikko
  ([valinnat on-change teksti] (checkbox-pudotusvalikko valinnat on-change teksti {}))
  ([valinnat on-change teksti asetukset]
   (let [idn-alku-label (gensym "label")
         idn-alku-cb (gensym "cb")]
     (fn [valinnat on-change teksti {:keys [kaikki-valinta-fn] :as asetukset}]
       [:div.checkbox-pudotusvalikko
        [livi-pudotusvalikko
         (merge
           asetukset
           {:naytettava-arvo (let [valittujen-valintojen-maara (count (filter :valittu? valinnat))
                                   valintojen-maara (count valinnat)
                                   naytettava-teksti (cond
                                                       (= valittujen-valintojen-maara valintojen-maara) "Kaikki valittu"
                                                       (= valittujen-valintojen-maara 1) (str "1" (first teksti))
                                                       :else (str valittujen-valintojen-maara (second teksti)))]
                               naytettava-teksti)
            :itemit-komponentteja? true}
           (when kaikki-valinta-fn
             {:class "pudotusvalikko"}))
         (map (fn [{:keys [id nimi valittu?] :as valinta}]
                (if (:vayla-tyyli? asetukset)
                  [:div.flex-row
                   [:input.vayla-checkbox
                    {:id (str idn-alku-cb id)
                     :class "check"
                     :type "checkbox"
                     :checked valittu?
                     :on-change #(let [valittu? (-> % .-target .-checked)]
                                   (on-change valinta valittu?))}]
                   [:label {:on-click #(.stopPropagation %)
                            :for (str idn-alku-cb id)}
                    nimi]]
                  [:label.checkbox-label-valikko {:on-click #(.stopPropagation %)
                                                  :id (str idn-alku-label id)}
                   nimi
                   [:input
                    (merge
                      (when (:vayla-tyyli? asetukset)
                        {:class "vayla-checkbox"})
                      {:id (str idn-alku-cb id)
                       :type "checkbox"
                       :checked valittu?
                       :on-change #(let [valittu? (-> % .-target .-checked)]
                                     (on-change valinta valittu?))})]]))
              valinnat)]
        (when kaikki-valinta-fn
          [napit/yleinen-ensisijainen (if (some :valittu? valinnat)
                                        "Poista valinnat"
                                        "Valitse kaikki")
           kaikki-valinta-fn {:luokka "valinta-nappi"}])]))))

(defn materiaali-valikko
  "Pudotusvalikko materiaaleille. Ottaa mapin, jolle täytyy antaa parametrit valittu-materiaali ja
   valitse-fn.

   Pakolliset:
   :valittu-materiaali   Sisältää valitun materiaalin siinä muodossa, että (format-fn valittu-materiaali)
                         palauttaa näytettävän arvon.
   :valitse-fn           Funktio, jota kutsutaan, kun käyttäjä valitsee pudotusvalikosta jonkun arvon. Saa argumentikseen
                         valitun arvon.

   Muita optioita ovat:
   :otsikko        Pudostusvalikon otsikko (default: \"Materiaali\")
   :format-fn      Funktio jolle annetaan näytettävä arvo. Jos lista materiaaleista on vaikkapa mappi,
                   niin tämä on yleensä avain. (default: str)
   :materiaalit    Lista materiaaleista.
   :lisaa-kaikki?  Lisätään valinta \"Kaikki\" materiaaleihin. Materiaalit pitää olla lista stringejä, jotta tämä
                   toimii (default: false)"
  [{:keys [otsikko format-fn valittu-materiaali materiaalit valitse-fn lisaa-kaikki?]}]
  (assert (or (nil? materiaalit) (sequential? materiaalit)) "Materiaalit pitää olla nil tai lista materiaaleista")
  (let [otsikko (or otsikko "Materiaali")
        format-fn (or format-fn str)
        valitse-fn (or valitse-fn #(reset! valittu-materiaali %))
        materiaalit (if (and lisaa-kaikki? (every? string? materiaalit))
                      (conj materiaalit "Kaikki")
                      materiaalit)]
    [:div.label-ja-alasveto
     [:span.alasvedon-otsikko otsikko]
     [livi-pudotusvalikko {:valinta valittu-materiaali
                           :format-fn format-fn
                           :valitse-fn valitse-fn}
      materiaalit]]))

(defn muutostyon-tyyppi
  [tyypit valittu-tyyppi-atom valitse-fn]
  [:div.label-ja-alasveto.hoitokausi
   [:span.alasvedon-otsikko "Työtyyppi"]
   [livi-pudotusvalikko {:valinta @valittu-tyyppi-atom
                         :format-fn #(if %
                                       (toteumat/muun-tyon-tyypin-teksti %)
                                       "Kaikki")
                         :valitse-fn valitse-fn}
    tyypit]])
