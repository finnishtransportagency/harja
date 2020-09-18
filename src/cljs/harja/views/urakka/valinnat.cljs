(ns harja.views.urakka.valinnat
  "Yleiset urakkaan liittyvät valintakomponentit."
  (:require [harja.tiedot.urakka :as u]
            [harja.tiedot.kanavat.urakka.kanavaurakka :as ku]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [harja.ui.kentat :refer [tee-otsikollinen-kentta]]
            [harja.ui.yleiset :refer [livi-pudotusvalikko]]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.navigaatio :as nav]
            [reagent.core :as r]
            [cljs-time.core :as t]
            [harja.ui.komponentti :as komp]))

(defn tienumero
  ([tienumero-atom] (tienumero tienumero-atom nil))
  ([tienumero-atom toiminta-f]
   [tee-otsikollinen-kentta
    {:otsikko "Tienumero"
     :kentta-params {:tyyppi :numero :placeholder "Rajaa tienumerolla" :kokonaisluku? true :toiminta-f toiminta-f}
     :arvo-atom tienumero-atom
     :luokka "label-ja-kentta-puolikas"}
    "Tienumero"]))

(defn yllapitokohteen-kohdenumero
  ([kohdenumero-atom] (yllapitokohteen-kohdenumero kohdenumero-atom nil))
  ([kohdenumero-atom toiminta-f]
   [tee-otsikollinen-kentta
    {:otsikko "Kohdenumero"
     :kentta-params {:tyyppi :string :placeholder "Rajaa kohdenumerolla" :toiminta-f toiminta-f}
     :arvo-atom kohdenumero-atom
     :luokka "label-ja-kentta-puolikas"}]))

(defn urakan-sopimus [ur]
  (valinnat/urakan-sopimus ur u/valittu-sopimusnumero u/valitse-sopimusnumero!))

(defn urakan-hoitokausi [ur]
  (valinnat/urakan-hoitokausi ur u/valitun-urakan-hoitokaudet u/valittu-hoitokausi u/valitse-hoitokausi!))

(defn hoitokauden-kuukausi []
  [valinnat/hoitokauden-kuukausi
   (pvm/aikavalin-kuukausivalit @u/valittu-hoitokausi)
   u/valittu-hoitokauden-kuukausi
   u/valitse-hoitokauden-kuukausi!])

(defn urakan-vuosi
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
  (let [kuukaudet (vec (concat [nil] (pvm/aikavalin-kuukausivalit @u/valittu-hoitokausi)))]
    [valinnat/urakan-valinnat urakka {:hoitokausi {:hoitokaudet u/valitun-urakan-hoitokaudet
                                                   :valittu-hoitokausi-atom u/valittu-hoitokausi
                                                   :valitse-hoitokausi-fn u/valitse-hoitokausi!}
                                      :kuukausi {:hoitokauden-kuukaudet kuukaudet
                                                 :valittu-kuukausi-atom u/valittu-hoitokauden-kuukausi
                                                 :valitse-kuukausi-fn u/valitse-hoitokauden-kuukausi!}}]))

(defn aikavali []
  [valinnat/aikavali u/valittu-aikavali])

(def aikavali-valinnat [["Edellinen viikko" #(pvm/aikavali-nyt-miinus 7)]
                        ["Edelliset 2 viikkoa" #(pvm/aikavali-nyt-miinus 14)]
                        ["Edelliset 3 viikkoa" #(pvm/aikavali-nyt-miinus 21)]
                        ["Valittu aikaväli" nil]])

(defn aikavali-nykypvm-taakse
  "Näyttää aikavalinnan tästä hetkestä taaksepäin, jos urakka on käynnissä.
  Jos urakka ei ole käynnissä, näyttää hoitokausi ja kuukausi valinnat."
  ([urakka valittu-aikavali] (aikavali-nykypvm-taakse urakka valittu-aikavali nil))
  ([urakka valittu-aikavali {:keys [otsikko vaihda-filtteri-urakan-paattyessa?
                                    aikavalin-rajoitus] :as optiot}]
   (let [[valittu-aikavali-alku valittu-aikavali-loppu
          :as valittu-aikavali-nyt] @valittu-aikavali
         vaihda-filtteri-urakan-paattyessa? (if (some? vaihda-filtteri-urakan-paattyessa?)
                                              vaihda-filtteri-urakan-paattyessa?
                                              true)
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
         (when (not (u/urakka-kaynnissa? urakka))
           (reset! valittu-aikavali
                   (or @u/valittu-hoitokauden-kuukausi
                       @u/valittu-hoitokausi)))
         (if-not (u/urakka-kaynnissa? urakka)
           (if vaihda-filtteri-urakan-paattyessa?
             [urakan-hoitokausi-ja-kuukausi urakka]
             [valinnat/aikavali valittu-aikavali {:otsikko (or otsikko "Aikaväli")}])
           [:span.aikavali-nykypvm-taakse
            [:div.label-ja-alasveto
             [:span.alasvedon-otsikko (or otsikko "Aikaväli")]
             [livi-pudotusvalikko {:valinta @valinta
                                   :format-fn first
                                   :valitse-fn (partial valitse urakka)}
              aikavali-valinnat]]
            (when @vapaa-aikavali?
              [valinnat/aikavali valittu-aikavali {:aikavalin-rajoitus aikavalin-rajoitus}])]))))))

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

;; Tämä on neljännen tason toimenpiteille, jotka ovat muutoshintaisia
(defn urakan-tehtava+kaikki []
  (valinnat/urakan-tehtava
    (r/wrap (vec (concat [{:t4_nimi "Kaikki"}]
                         @u/urakan-tehtavat))
            identity)
    u/valittu-tehtava u/valitse-tehtava!))


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

(defn kanavaurakan-kohde+kaikki []
  (valinnat/kanavaurakan-kohde
    (r/wrap (vec (concat [{:harja.domain.kanavat.kohde/nimi "Kaikki"}]
                         @ku/kanavakohteet-mukaanlukien-poistetut))
            identity)
    ku/valittu-kohde ku/valitse-kohde!))


;; Komponentit, jotka käyttävät hoitokautta, joutuvat resetoimaan valitun aikavälin eksplisiittisesti
;; Muuten valituksi aikaväliksi voi jäädä jokin muu arvo kuin valittu hoitokausi, joka on hämmentävä
;; tilanne käyttäjälle. Erityisesti tämä näkyy kun käydään tarkastusten näkymässä, jossa käytetään
;; komponenttia, jolla aikavälin voi asettaa esim "viikon taaksepäin".

(def ^{:private true}
valintaoptiot {:sopimus {:valittu-sopimusnumero-atom u/valittu-sopimusnumero
                         :valitse-sopimus-fn u/valitse-sopimusnumero!}
               :hoitokausi {:hoitokaudet u/valitun-urakan-hoitokaudet
                            :valittu-hoitokausi-atom u/valittu-hoitokausi
                            :valitse-hoitokausi-fn u/valitse-hoitokausi!}
               :aikavali-optiot {:valittu-aikavali-atom u/valittu-aikavali}
               :toimenpide {:urakan-toimenpideinstassit-atom u/urakan-toimenpideinstanssit
                            :valittu-toimenpideinstanssi-atom u/valittu-toimenpideinstanssi
                            :valitse-toimenpide-fn u/valitse-toimenpideinstanssi!}})

(defn urakan-sopimus-ja-hoitokausi [ur]
  (komp/luo
    {:component-will-mount
     (fn [& args] (u/valitse-hoitokausi! @u/valittu-hoitokausi))}
    (fn [ur]
      (valinnat/urakan-valinnat ur (select-keys valintaoptiot [:sopimus :hoitokausi])))))

(defn urakan-sopimus-ja-toimenpide [ur]
  (valinnat/urakan-valinnat ur (select-keys valintaoptiot [:sopimus :toimenpide])))

(defn urakan-sopimus-ja-hoitokausi-ja-toimenpide [ur]
  (valinnat/urakan-valinnat ur (select-keys valintaoptiot [:sopimus :hoitokausi :toimenpide])))

(defn urakan-sopimus-ja-hoitokausi-ja-toimenpide+kaikki [ur]
  (let [sopimus-ja-hoitokausi-ja-toimenpide (select-keys valintaoptiot [:sopimus :hoitokausi :toimenpide])
        sopimus-ja-hoitokausi-ja-toimenpide+kaikki (update-in sopimus-ja-hoitokausi-ja-toimenpide [:toimenpide :urakan-toimenpideinstassit-atom]
                                                              (fn [urakan-toimenpideinstanssit]
                                                                (r/wrap (vec (concat @urakan-toimenpideinstanssit [{:tpi_nimi "Kaikki"}])) identity)))]
    (valinnat/urakan-valinnat ur sopimus-ja-hoitokausi-ja-toimenpide+kaikki)))

(defn urakan-sopimus-ja-hoitokausi-ja-toimenpide+muut [ur]
  (fn [ur]
    (let [sopimus-ja-hoitokausi-ja-toimenpide (select-keys valintaoptiot [:sopimus :hoitokausi :toimenpide])
          sopimus-ja-hoitokausi-ja-toimenpide+muut (update-in sopimus-ja-hoitokausi-ja-toimenpide [:toimenpide :urakan-toimenpideinstassit-atom]
                                                              (fn [urakan-toimenpideinstanssit]
                                                                (r/wrap (vec (concat @urakan-toimenpideinstanssit [{:tpi_nimi "Muut"}])) identity)))]
      (valinnat/urakan-valinnat ur sopimus-ja-hoitokausi-ja-toimenpide+muut))))

(defn urakan-hoitokausi-ja-toimenpide [ur]
  (fn [ur]
    (valinnat/urakan-valinnat ur (select-keys valintaoptiot [:hoitokausi :toimenpide]))))

(defn urakan-hoitokausi-ja-aikavali [ur]
  (fn [ur]
    (valinnat/urakan-valinnat ur (select-keys valintaoptiot [:hoitokausi :aikavali-optiot]))))

(defn urakan-sopimus-ja-hoitokausi-ja-aikavali-ja-toimenpide [ur]
  (fn [ur]
    (valinnat/urakan-valinnat ur (select-keys valintaoptiot [:sopimus :hoitokausi :aikavali-optiot :toimenpide]))))

(defn urakan-sopimus-ja-hoitokausi-ja-aikavali
  ([ur] (urakan-sopimus-ja-hoitokausi-ja-aikavali ur {}))
  ([ur optiot]
   (fn [ur]
     (valinnat/urakan-valinnat
       ur
       (merge-with merge
                   (select-keys valintaoptiot [:sopimus :hoitokausi :aikavali-optiot])
                   optiot)))))



