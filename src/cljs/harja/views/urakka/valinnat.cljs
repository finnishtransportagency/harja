(ns harja.views.urakka.valinnat
  "Yleiset urakkaan liittyvät valintakomponentit."
  (:require [harja.tiedot.urakka :as u]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [harja.ui.kentat :refer [tee-otsikollinen-kentta]]
            [harja.ui.yleiset :refer [livi-pudotusvalikko]]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.navigaatio :as nav]
            [reagent.core :as r]
            [cljs-time.core :as t]
            [harja.ui.komponentti :as komp]))

(defn tienumero [tienumero-atom]
  [tee-otsikollinen-kentta
   {:otsikko "Tienumero"
    :kentta-params {:tyyppi :numero :placeholder "Rajaa tienumerolla" :kokonaisluku? true}
    :arvo-atom tienumero-atom
    :luokka "label-ja-kentta-puolikas"}
   "Tienumero"])

(defn yllapitokohteen-kohdenumero [kohdenumero-atom]
  [tee-otsikollinen-kentta
   {:otsikko "Kohdenumero"
    :kentta-params {:tyyppi :string :placeholder "Rajaa kohdenumerolla"}
    :arvo-atom kohdenumero-atom
    :luokka "label-ja-kentta-puolikas"}])

(defn urakan-sopimus [ur]
  (valinnat/urakan-sopimus ur u/valittu-sopimusnumero u/valitse-sopimusnumero!))

(defn urakan-hoitokausi [ur]
  (valinnat/urakan-hoitokausi ur u/valitun-urakan-hoitokaudet u/valittu-hoitokausi u/valitse-hoitokausi!))

(defn hoitokauden-kuukausi []
  [valinnat/hoitokauden-kuukausi
   (pvm/hoitokauden-kuukausivalit @u/valittu-hoitokausi)
   u/valittu-hoitokauden-kuukausi
   u/valitse-hoitokauden-kuukausi!])

(defn- urakan-vuosi
  "Valitsee urakkavuoden urakan alku- ja loppupvm väliltä."
  ([ur]
   (urakan-vuosi ur {}))
  ([ur {:keys [kaikki-valinta?] :as optiot}]
   [valinnat/vuosi optiot
    (t/year (:alkupvm ur))
    (t/year (:loppupvm ur))
    u/valittu-urakan-vuosi
    u/valitse-urakan-vuosi!]))

(defn urakan-hoitokausi-ja-kuukausi [urakka]
  (let [kuukaudet (vec (concat [nil] (pvm/hoitokauden-kuukausivalit @u/valittu-hoitokausi)))]
    [valinnat/urakan-hoitokausi-ja-kuukausi
     urakka
     u/valitun-urakan-hoitokaudet
     u/valittu-hoitokausi
     u/valitse-hoitokausi!
     kuukaudet
     u/valittu-hoitokauden-kuukausi
     u/valitse-hoitokauden-kuukausi!]))

(defn aikavali []
  (valinnat/aikavali u/valittu-aikavali))

(defn- aikavali-nyt-miinus [paivia]
  (let [nyt (pvm/nyt)]
    [(t/minus nyt (t/days paivia)) nyt]))

(def aikavali-valinnat [["Edellinen viikko" #(aikavali-nyt-miinus 7)]
                        ["Edelliset 2 viikkoa" #(aikavali-nyt-miinus 14)]
                        ["Edelliset 3 viikkoa" #(aikavali-nyt-miinus 21)]
                        ["Valittu aikaväli" nil]])

(defn aikavali-nykypvm-taakse
  "Näyttää aikavalinnan tästä hetkestä taaksepäin, jos urakka on käynnissä.
  Jos urakka ei ole käynnissä, näyttää hoitokausi ja kuukausi valinnat."
  [urakka valittu-aikavali]
  (let [[valittu-aikavali-alku valittu-aikavali-loppu
         :as valittu-aikavali-nyt] @valittu-aikavali

        alkuvalinta (or
                      (and (nil? valittu-aikavali-nyt) (first aikavali-valinnat))
                      (and valittu-aikavali-alku
                           valittu-aikavali-loppu
                           (some (fn [[nimi aikavali-fn :as valinta]]
                                   (when aikavali-fn
                                     (let [[alku loppu] (aikavali-fn)]
                                       (when (and (pvm/sama-pvm? alku valittu-aikavali-alku)
                                                  (pvm/sama-pvm? loppu valittu-aikavali-loppu))
                                         valinta)))) aikavali-valinnat))
                      (last aikavali-valinnat))
        [_ aikavali-fn] alkuvalinta
        valinta (r/atom alkuvalinta)
        vapaa-aikavali? (r/atom false)
        valitse (fn [urakka v]
                  (when (u/urakka-kaynnissa? urakka)
                    (reset! valinta v)
                    (if-let [aikavali-fn (second v)]
                      ;; Esiasetettu laskettava aikaväli
                      (do
                        (reset! vapaa-aikavali? false)
                        (reset! valittu-aikavali (aikavali-fn)))
                      ;; Käyttäjä haluaa asettaa itse aikavälin
                      (reset! vapaa-aikavali? true))))]
    (valitse urakka alkuvalinta)
    (komp/luo
      (komp/kun-muuttuu
        (fn [urakka _]
          (valitse urakka @valinta)))

      (fn [urakka valittu-aikavali]
        (if-not (u/urakka-kaynnissa? urakka)
          [urakan-hoitokausi-ja-kuukausi urakka]
          [:span.aikavali-nykypvm-taakse
           [:div.label-ja-alasveto
            [:span.alasvedon-otsikko "Aikaväli"]
            [livi-pudotusvalikko {:valinta @valinta
                                  :format-fn first
                                  :valitse-fn (partial valitse urakka)}
             aikavali-valinnat]]
           (when @vapaa-aikavali?
             [valinnat/aikavali valittu-aikavali])])))))

(defn urakan-toimenpide []
  (valinnat/urakan-toimenpide u/urakan-toimenpideinstanssit u/valittu-toimenpideinstanssi u/valitse-toimenpideinstanssi!))

(defn urakan-toimenpide+kaikki []
  (valinnat/urakan-toimenpide
    (r/wrap (vec (concat [{:tpi_nimi "Kaikki"}]
                         @u/urakan-toimenpideinstanssit))
            identity)
    u/valittu-toimenpideinstanssi u/valitse-toimenpideinstanssi!))

(defn urakan-toimenpide+muut []
  (valinnat/urakan-toimenpide
    (r/wrap (vec (concat @u/urakan-toimenpideinstanssit
                         [{:tpi_nimi "Muut"}]))
            identity)
    u/valittu-toimenpideinstanssi u/valitse-toimenpideinstanssi!))

(defn urakan-kokonaishintainen-tehtava+kaikki []
  (valinnat/urakan-kokonaishintainen-tehtava
    (r/wrap (vec (concat [{:nimi "Kaikki"}]
                         @u/urakan-tpin-kokonaishintaiset-tehtavat))
            identity)
    u/valittu-kokonaishintainen-tehtava
    u/valitse-kokonaishintainen-tehtava!))

(defn urakan-yksikkohintainen-tehtava+kaikki []
  (valinnat/urakan-yksikkohintainen-tehtava
    (r/wrap (vec (concat [{:nimi "Kaikki"}]
                         @u/urakan-tpin-yksikkohintaiset-tehtavat))
            identity)
    u/valittu-yksikkohintainen-tehtava
    u/valitse-yksikkohintainen-tehtava!))


;; Komponentit, jotka käyttävät hoitokautta, joutuvat resetoimaan valitun aikavälin eksplisiittisesti
;; Muuten valituksi aikaväliksi voi jäädä jokin muu arvo kuin valittu hoitokausi, joka on hämmentävä
;; tilanne käyttäjälle. Erityisesti tämä näkyy kun käydään tarkastusten näkymässä, jossa käytetään
;; komponenttia, jolla aikavälin voi asettaa esim "viikon taaksepäin".
(defn urakan-sopimus-ja-hoitokausi [ur]
  (komp/luo
    {:component-will-mount
     (fn [& args] (u/valitse-hoitokausi! @u/valittu-hoitokausi))}
    (fn [ur]
      (valinnat/urakan-sopimus-ja-hoitokausi
        ur
        u/valittu-sopimusnumero u/valitse-sopimusnumero!
        u/valitun-urakan-hoitokaudet u/valittu-hoitokausi u/valitse-hoitokausi!))))

(defn urakan-sopimus-ja-toimenpide [ur]
  (valinnat/urakan-sopimus-ja-toimenpide
    ur
    u/valittu-sopimusnumero u/valitse-sopimusnumero!
    u/urakan-toimenpideinstanssit u/valittu-toimenpideinstanssi u/valitse-toimenpideinstanssi!))

(defn urakan-sopimus-ja-hoitokausi-ja-toimenpide [ur]
  (valinnat/urakan-sopimus-ja-hoitokausi-ja-toimenpide
    ur
    u/valittu-sopimusnumero u/valitse-sopimusnumero!
    u/valitun-urakan-hoitokaudet u/valittu-hoitokausi u/valitse-hoitokausi!
    u/urakan-toimenpideinstanssit u/valittu-toimenpideinstanssi u/valitse-toimenpideinstanssi!))

(defn urakan-sopimus-ja-hoitokausi-ja-toimenpide+muut [ur]
  (fn [ur]
    (valinnat/urakan-sopimus-ja-hoitokausi-ja-toimenpide
      ur
      u/valittu-sopimusnumero u/valitse-sopimusnumero!
      u/valitun-urakan-hoitokaudet u/valittu-hoitokausi u/valitse-hoitokausi!
      (r/wrap (vec (concat @u/urakan-toimenpideinstanssit
                           [{:tpi_nimi "Muut"}]))
              identity)
      u/valittu-toimenpideinstanssi u/valitse-toimenpideinstanssi!)))

(defn urakan-hoitokausi-ja-toimenpide [ur]
  (fn [ur]
    (valinnat/urakan-hoitokausi-ja-toimenpide
      ur
      u/valitun-urakan-hoitokaudet u/valittu-hoitokausi u/valitse-hoitokausi!
      u/urakan-toimenpideinstanssit u/valittu-toimenpideinstanssi u/valitse-toimenpideinstanssi!)))

(defn urakan-hoitokausi-ja-aikavali [ur]
  (fn [ur]
    (valinnat/urakan-hoitokausi-ja-aikavali
      ur
      u/valitun-urakan-hoitokaudet u/valittu-hoitokausi u/valitse-hoitokausi!
      u/valittu-aikavali)))

(defn urakan-sopimus-ja-hoitokausi-ja-aikavali-ja-toimenpide [ur]
  (fn [ur]
    (valinnat/urakan-sopimus-ja-hoitokausi-ja-aikavali-ja-toimenpide
      ur
      u/valittu-sopimusnumero u/valitse-sopimusnumero!
      u/valitun-urakan-hoitokaudet u/valittu-hoitokausi u/valitse-hoitokausi!
      u/valittu-aikavali
      u/urakan-toimenpideinstanssit u/valittu-toimenpideinstanssi u/valitse-toimenpideinstanssi!)))

(defn urakan-sopimus-ja-hoitokausi-ja-aikavali [ur]
  (fn [ur]
    (valinnat/urakan-sopimus-ja-hoitokausi-ja-aikavali
      ur
      u/valittu-sopimusnumero u/valitse-sopimusnumero!
      u/valitun-urakan-hoitokaudet u/valittu-hoitokausi u/valitse-hoitokausi!
      u/valittu-aikavali)))
