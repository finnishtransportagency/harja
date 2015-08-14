(ns harja.ui.valinnat
  "Yleisiä valintoihin liittyviä komponentteja.
  Refaktoroitu vanhasta harja.views.urakka.valinnat namespacesta."
  (:require [reagent.core :refer [atom] :as r]

            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [livi-pudotusvalikko]]
            [harja.fmt :as fmt]))

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

(defn aikavali
  [valittu-aikavali-atom]
  ; TODO Harjaa vastaava tyyli tälle
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
    [:span " \u2014 "]
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
