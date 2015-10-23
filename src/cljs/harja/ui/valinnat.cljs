(ns harja.ui.valinnat
  "Yleisiä valintoihin liittyviä komponentteja.
  Refaktoroitu vanhasta harja.views.urakka.valinnat namespacesta."
  (:require [reagent.core :refer [atom] :as r]

            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [livi-pudotusvalikko]]
            [harja.fmt :as fmt]
            [clojure.string :as str]))

(defn urakan-sopimus
  [ur valittu-sopimusnumero-atom valitse-fn]
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko "Sopimusnumero"]
   [livi-pudotusvalikko {:valinta    @valittu-sopimusnumero-atom
                         :format-fn  second
                         :valitse-fn valitse-fn
                         :class      "suunnittelu-alasveto"
                         }
    (:sopimukset ur)]])

(defn urakan-hoitokausi
  [ur hoitokaudet valittu-hoitokausi-atom valitse-fn]
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko (if (= :hoito (:tyyppi ur)) "Hoitokausi" "Sopimuskausi")]
   [livi-pudotusvalikko {:valinta    @valittu-hoitokausi-atom
                         ;;\u2014 on väliviivan unikoodi
                         :format-fn  #(if % (fmt/pvm-vali-opt %) "Valitse")
                         :valitse-fn valitse-fn
                         :class      "suunnittelu-alasveto"
                         }
    hoitokaudet]])

(defn hoitokauden-kuukausi
  [hoitokauden-kuukaudet valittu-kuukausi-atom valitse-fn]
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko "Kuukausi"]
   [livi-pudotusvalikko {:valinta    @valittu-kuukausi-atom
                         :format-fn  #(if %
                                       (let [[alkupvm _] %
                                             kk-teksti (pvm/kuukauden-nimi (pvm/kuukausi alkupvm))]
                                         (str (str/capitalize kk-teksti) " " (pvm/vuosi alkupvm)))
                                       "Koko hoitokausi")
                         :valitse-fn valitse-fn
                         :class      "suunnittelu-alasveto"
                         }
    hoitokauden-kuukaudet]])

(defn urakan-hoitokausi-ja-kuukausi
  [ur
   hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn
   hoitokauden-kuukaudet valittu-kuukausi-atom valitse-kuukausi-fn]
  [:span
   [urakan-hoitokausi ur hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn]
   [hoitokauden-kuukausi hoitokauden-kuukaudet valittu-kuukausi-atom valitse-kuukausi-fn]])

(defn aikavali
  [valittu-aikavali-atom]
  [:span.label-ja-aikavali
   [:span.alasvedon-otsikko "Aikaväli"]
   [:div.aikavali-valinnat
    [tee-kentta {:tyyppi :pvm :irrallinen? true}
     (r/wrap (first @valittu-aikavali-atom)
             (fn [uusi-arvo]
               (reset! valittu-aikavali-atom [uusi-arvo
                                              (if-not (or
                                                        (and (string? uusi-arvo) (empty? uusi-arvo))
                                                        (nil? uusi-arvo))
                                                (second (pvm/kuukauden-aikavali uusi-arvo))
                                                (second @valittu-aikavali-atom))])
               (log "Uusi aikaväli: " (pr-str @valittu-aikavali-atom))))]
    [:div.pvm-valiviiva-wrap [:span.pvm-valiviiva " \u2014 "]]
    [tee-kentta {:tyyppi :pvm :irrallinen? true}
     (r/wrap (second @valittu-aikavali-atom)
             (fn [uusi-arvo]
               (swap! valittu-aikavali-atom (fn [[alku _]] [alku uusi-arvo]))
               (log "Uusi aikaväli: " (pr-str @valittu-aikavali-atom))))]
    ]])

(defn urakan-toimenpide
  [urakan-toimenpideinstanssit-atom valittu-toimenpideinstanssi-atom valitse-fn]
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko "Toimenpide"]
   [livi-pudotusvalikko {:valinta    @valittu-toimenpideinstanssi-atom
                         ;;\u2014 on väliviivan unikoodi
                         :format-fn  #(if % (str (:tpi_nimi %)) "Ei toimenpidettä")
                         :valitse-fn valitse-fn}
    @urakan-toimenpideinstanssit-atom]])

(defn urakan-kokonaishintainen-toimenpide-ja-tehtava
  [urakan-kokonaishintaiset-toimenpiteet-ja-tehtavat-atom
   valittu-kokonaishintainen-toimenpideinstanssi-atom
   valitse-kokonaishintainen-toimenpide-fn
   valittu-kokonaishintainen-tehtava-atom
   valitse-kokonaishintainen-tehtava-fn]
  [:span
   [:div.label-ja-alasveto
    [:span.alasvedon-otsikko "Toimenpide"]
    [livi-pudotusvalikko {:valinta    @valittu-kokonaishintainen-toimenpideinstanssi-atom
                          ;;\u2014 on väliviivan unikoodi
                          :format-fn  #(if % (str (second (first %))) "Kaikki toimenpiteet")
                          :valitse-fn #(do
                                        (valitse-kokonaishintainen-tehtava-fn nil)
                                        (valitse-kokonaishintainen-toimenpide-fn %))}
     (concat [nil] @urakan-kokonaishintaiset-toimenpiteet-ja-tehtavat-atom)]]

   [:div.label-ja-alasveto
    [:span.alasvedon-otsikko "Tehtävä"]
    [livi-pudotusvalikko {:valinta    @valittu-kokonaishintainen-tehtava-atom
                          ;;\u2014 on väliviivan unikoodi
                          :format-fn  #(if % (str (:t4_nimi %)) "Kaikki tehtävät")
                          :valitse-fn valitse-kokonaishintainen-tehtava-fn}
     (concat [nil] (second @valittu-kokonaishintainen-toimenpideinstanssi-atom))]]])

;; Parametreja näissä on melkoisen hurja määrä, mutta ei voi mitään
(defn urakan-sopimus-ja-hoitokausi
  [ur
   valittu-sopimusnumero-atom valitse-sopimus-fn            ;; urakan-sopimus
   hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn] ;; urakan-hoitokausi

  [:span
   [urakan-sopimus ur valittu-sopimusnumero-atom valitse-sopimus-fn]
   [urakan-hoitokausi ur hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn]])



(defn urakan-sopimus-ja-toimenpide
  [ur
   valittu-sopimusnumero-atom valitse-sopimus-fn            ;; urakan-sopimus
   urakan-toimenpideinstassit-atom valittu-toimenpideinstanssi-atom valitse-toimenpide-fn] ;; urakan-toimenpide

  [:span
   [urakan-sopimus ur valittu-sopimusnumero-atom valitse-sopimus-fn]
   [urakan-toimenpide urakan-toimenpideinstassit-atom valittu-toimenpideinstanssi-atom valitse-toimenpide-fn]])



(defn urakan-sopimus-ja-hoitokausi-ja-toimenpide
  [ur
   valittu-sopimusnumero-atom valitse-sopimus-fn            ;; urakan-sopimus
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
   valittu-aikavali-atom                                    ;; hoitokauden-aikavali
   ]

  [:span

   [urakan-hoitokausi
    ur
    hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn]

   [aikavali valittu-aikavali-atom]])



(defn urakan-sopimus-ja-hoitokausi-ja-aikavali-ja-toimenpide
  [ur
   valittu-sopimusnumero-atom valitse-sopimus-fn            ;; urakan-sopimus
   hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn ;; urakan-hoitokausi
   valittu-aikavali-atom                                    ;; hoitokauden-aikavali
   urakan-toimenpideinstassit-atom valittu-toimenpideinstanssi-atom valitse-toimenpide-fn] ;; urakan-toimenpide

  [:span

   [urakan-sopimus-ja-hoitokausi
    ur
    valittu-sopimusnumero-atom valitse-sopimus-fn
    hoitokaudet valittu-hoitokausi-atom valitse-hoitokausi-fn]

   [aikavali valittu-aikavali-atom]

   [urakan-toimenpide urakan-toimenpideinstassit-atom valittu-toimenpideinstanssi-atom valitse-toimenpide-fn]])
