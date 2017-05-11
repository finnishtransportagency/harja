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
            [harja.ui.lomake :as lomake]))

(defn urakan-sopimus
  [ur valittu-sopimusnumero-atom valitse-fn]
  [:div.label-ja-alasveto.sopimusnumero
   [:span.alasvedon-otsikko "Sopimusnumero"]
   [livi-pudotusvalikko {:valinta @valittu-sopimusnumero-atom
                         :format-fn second
                         :valitse-fn valitse-fn
                         :li-luokka-fn #(when (= (first %) (:paasopimus ur))
                                          "bold")}
    (:sopimukset ur)]])

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
   [:span.alasvedon-otsikko (if (= :hoito (:tyyppi ur)) "Hoitokausi" "Sopimuskausi")]
   [livi-pudotusvalikko {:valinta @valittu-hoitokausi-atom
                         :format-fn #(if % (fmt/pvm-vali-opt %) "Valitse")
                         :valitse-fn valitse-fn}
    @hoitokaudet]])

(defn hoitokausi
  ([hoitokaudet valittu-hoitokausi-atom]
   (hoitokausi {} hoitokaudet valittu-hoitokausi-atom #(reset! valittu-hoitokausi-atom %)))
  ([hoitokaudet valittu-hoitokausi-atom valitse-fn]
   (hoitokausi {} hoitokaudet valittu-hoitokausi-atom valitse-fn))
  ([{:keys [disabled]} hoitokaudet valittu-hoitokausi-atom valitse-fn]
   [:div.label-ja-alasveto.hoitokausi
    [:span.alasvedon-otsikko "Hoitokausi"]
    [livi-pudotusvalikko {:valinta @valittu-hoitokausi-atom
                          :disabled disabled
                          :format-fn #(if % (fmt/pvm-vali-opt %) "Valitse")
                          :valitse-fn valitse-fn}
     hoitokaudet]]))

(defn kuukausi [{:keys [disabled nil-valinta]} kuukaudet valittu-kuukausi-atom]
  [:div.label-ja-alasveto.kuukausi
   [:span.alasvedon-otsikko "Kuukausi"]
   [livi-pudotusvalikko {:valinta @valittu-kuukausi-atom
                         :disabled disabled
                         :format-fn #(if %
                                       (let [[alkupvm _] %
                                             kk-teksti (pvm/kuukauden-nimi (pvm/kuukausi alkupvm))]
                                         (str (str/capitalize kk-teksti) " " (pvm/vuosi alkupvm)))
                                       (or nil-valinta "Kaikki"))
                         :valitse-fn #(reset! valittu-kuukausi-atom %)}
    kuukaudet]])

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

(defn urakan-hoitokausi-ja-kuukausi
  [ur
   hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn
   hoitokauden-kuukaudet valittu-kuukausi-atom valitse-kuukausi-fn]
  [:span
   [urakan-hoitokausi ur hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn]
   [hoitokauden-kuukausi hoitokauden-kuukaudet valittu-kuukausi-atom valitse-kuukausi-fn]])

(defn aikavali
  ([valittu-aikavali-atom] (aikavali valittu-aikavali-atom nil))
  ([valittu-aikavali-atom {:keys [nayta-otsikko? aikavalin-rajoitus
                                  aloitusaika-pakota-suunta paattymisaika-pakota-suunta
                                  lomake?]}]
   [:span {:class (if lomake?
                    "label-ja-aikavali-lomake"
                    "label-ja-aikavali")}
    (when (and (not lomake?)
               (or (nil? nayta-otsikko?)
                   (true? nayta-otsikko?)))
      [:span.alasvedon-otsikko "Aikaväli"])
    [:div.aikavali-valinnat
     [tee-kentta {:tyyppi :pvm :pakota-suunta aloitusaika-pakota-suunta}
      (r/wrap (first @valittu-aikavali-atom)
              (fn [uusi-arvo]
                (let [uusi-arvo (pvm/paivan-alussa-opt uusi-arvo)]
                  (if-not aikavalin-rajoitus
                    (swap! valittu-aikavali-atom #(pvm/varmista-aikavali-opt [uusi-arvo (second %)] :alku))
                    (swap! valittu-aikavali-atom #(pvm/varmista-aikavali-opt [uusi-arvo (second %)] aikavalin-rajoitus :alku))))
                (log "Uusi aikaväli: " (pr-str @valittu-aikavali-atom))))]
     [:div.pvm-valiviiva-wrap [:span.pvm-valiviiva " \u2014 "]]
     [tee-kentta {:tyyppi :pvm :pakota-suunta paattymisaika-pakota-suunta}
      (r/wrap (second @valittu-aikavali-atom)
              (fn [uusi-arvo]
                (let [uusi-arvo (pvm/paivan-lopussa-opt uusi-arvo)]
                  (if-not aikavalin-rajoitus
                    (swap! valittu-aikavali-atom #(pvm/varmista-aikavali-opt [(first %) uusi-arvo] :loppu))
                    (swap! valittu-aikavali-atom #(pvm/varmista-aikavali-opt [(first %) uusi-arvo] aikavalin-rajoitus :loppu))))
                (log "Uusi aikaväli: " (pr-str @valittu-aikavali-atom))))]]]))

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

;; Parametreja näissä on melkoisen hurja määrä, mutta ei voi mitään
(defn urakan-sopimus-ja-hoitokausi
  [ur
   valittu-sopimusnumero-atom valitse-sopimus-fn ;; urakan-sopimus
   hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn] ;; urakan-hoitokausi

  [:span
   [urakan-sopimus ur valittu-sopimusnumero-atom valitse-sopimus-fn]
   [urakan-hoitokausi ur hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn]])

(defn urakan-sopimus-ja-toimenpide
  [ur
   valittu-sopimusnumero-atom valitse-sopimus-fn ;; urakan-sopimus
   urakan-toimenpideinstassit-atom valittu-toimenpideinstanssi-atom valitse-toimenpide-fn] ;; urakan-toimenpide

  [:span
   [urakan-sopimus ur valittu-sopimusnumero-atom valitse-sopimus-fn]
   [urakan-toimenpide urakan-toimenpideinstassit-atom valittu-toimenpideinstanssi-atom valitse-toimenpide-fn]])



(defn urakan-sopimus-ja-hoitokausi-ja-toimenpide
  [ur
   valittu-sopimusnumero-atom valitse-sopimus-fn ;; urakan-sopimus
   hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn ;; urakan-hoitokausi
   urakan-toimenpideinstassit-atom valittu-toimenpideinstanssi-atom valitse-toimenpide-fn] ;; urakan-toimenpide

  [:span
   [urakan-sopimus-ja-hoitokausi
    ur
    valittu-sopimusnumero-atom valitse-sopimus-fn
    hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn]

   [urakan-toimenpide urakan-toimenpideinstassit-atom valittu-toimenpideinstanssi-atom valitse-toimenpide-fn]])

(defn urakan-hoitokausi-ja-toimenpide
  [ur
   hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn ;; urakan-hoitokausi
   urakan-toimenpideinstassit-atom valittu-toimenpideinstanssi-atom valitse-toimenpide-fn] ;; urakan-toimenpide]
  [:span
   [urakan-hoitokausi
    ur
    hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn]

   [urakan-toimenpide
    urakan-toimenpideinstassit-atom valittu-toimenpideinstanssi-atom valitse-toimenpide-fn]])


(defn urakan-hoitokausi-ja-aikavali
  [ur
   hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn ;; urakan-hoitokausi
   valittu-aikavali-atom ;; hoitokauden-aikavali
   ]

  [:span

   [urakan-hoitokausi
    ur
    hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn]

   [aikavali valittu-aikavali-atom]])

(defn urakan-sopimus-ja-hoitokausi-ja-aikavali-ja-toimenpide
  [ur
   valittu-sopimusnumero-atom valitse-sopimus-fn ;; urakan-sopimus
   hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn ;; urakan-hoitokausi
   valittu-aikavali-atom ;; hoitokauden-aikavali
   urakan-toimenpideinstassit-atom valittu-toimenpideinstanssi-atom valitse-toimenpide-fn] ;; urakan-toimenpide

  [:span
   [urakan-sopimus-ja-hoitokausi
    ur
    valittu-sopimusnumero-atom valitse-sopimus-fn
    hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn]
   [aikavali valittu-aikavali-atom]
   [urakan-toimenpide urakan-toimenpideinstassit-atom valittu-toimenpideinstanssi-atom valitse-toimenpide-fn]])

(defn urakan-sopimus-ja-hoitokausi-ja-aikavali
  [ur
   valittu-sopimusnumero-atom valitse-sopimus-fn ;; urakan-sopimus
   hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn ;; urakan-hoitokausi
   valittu-aikavali-atom] ;; hoitokauden-aikavali

  [:span
   [urakan-sopimus-ja-hoitokausi
    ur
    valittu-sopimusnumero-atom valitse-sopimus-fn
    hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn]
   [aikavali valittu-aikavali-atom]])

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
                          :class "alasveto-vuosi"}
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
   (let [vapaa-aikavali? (get-in valinnat-nyt [(or (:valokioaikavali kenttien-nimet) :vakioaikavali) :vapaa-aikavali])
         alkuaika (:alkuaika valinnat-nyt)
         vakio-aikavalikentta {:nimi (or (:valokioaikavali kenttien-nimet) :vakioaikavali)
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

(defn urakkavalinnat [& sisalto]
  [:div.urakkavalinnat sisalto])

(defn urakkatoiminnot [& sisalto]
  [:div.urakkatoiminnot sisalto])

(defn valintaryhmat-3 [& [ryhma1 ryhma2 ryhma3]]
  [:div.row
   [:div.valintaryhma.col-sm-12.col-md-4
    ryhma1]
   [:div.valintaryhma.col-sm-12.col-md-4
    ryhma2]
   [:div.valintaryhma.col-sm-12.col-md-4
    ryhma3]])