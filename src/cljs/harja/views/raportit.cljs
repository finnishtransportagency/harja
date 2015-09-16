(ns harja.views.raportit
  "Harjan raporttien pääsivu."
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.komponentti :as komp]
            [harja.ui.lomake :as lomake]
            [harja.views.urakat :as urakat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.pvm :as pvm]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.yleiset :refer [livi-pudotusvalikko]]
            [harja.fmt :as fmt]
            [harja.tiedot.raportit :as raportit]
            [harja.ui.grid :as grid]
            [cljs.core.async :refer [<! >! chan]]
            [harja.views.kartta :as kartta]
            [cljs-time.core :as t]
            [harja.tiedot.urakka.yksikkohintaiset-tyot :as yks-hint-tyot]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.kokonaishintaiset-tyot :as kok-hint-tyot]
            [harja.views.urakka.valinnat :as valinnat])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def valittu-raporttityyppi (atom nil))
(def raportti-valmis-naytettavaksi?
  (reaction (let [valittu-raporttityyppi @valittu-raporttityyppi
                  konteksti (:konteksti valittu-raporttityyppi)
                  v-ur @nav/valittu-urakka
                  v-hal @nav/valittu-hallintayksikko
                  v-aikavali @u/valittu-aikavali]
              (when valittu-raporttityyppi
                (if (= konteksti #{:urakka}) ; Pelkkä urakka -konteksti
                  (and v-ur
                       v-hal)
                  (if (or (contains? (:parametrit valittu-raporttityyppi) :valitun-hallintayksikon-hoitokaudet)
                          (contains? (:parametrit valittu-raporttityyppi) :koko-maan-hoitokaudet))
                    (and (not (nil? v-aikavali)) (not (empty? (keep identity v-aikavali))))
                    false))))))
(def nakymassa? (atom nil))

(defonce yksikkohintaiset-toteumat
         (reaction<! [urakka-id (:id @nav/valittu-urakka)
                      alkupvm (first @u/valittu-hoitokauden-kuukausi)
                      loppupvm (second @u/valittu-hoitokauden-kuukausi)
                      nakymassa? @nakymassa?
                      tama-raportti-valittu? (= :yks-hint-kuukausiraportti (:nimi @valittu-raporttityyppi))]
                     (when (and urakka-id alkupvm loppupvm nakymassa? tama-raportti-valittu?)
                       (log "[RAPORTTI] Haetaan yks. hint. kuukausiraportti parametreilla: " urakka-id alkupvm loppupvm)
                       (raportit/hae-yksikkohintaisten-toiden-kuukausiraportti urakka-id alkupvm loppupvm))))
(defonce yksikkohintaiset-toteumat-kaikkine-tietoineen
         (reaction
           (when
             @nav/valittu-urakka
             @yksikkohintaiset-toteumat
             (let [tehtavat (map
                              (fn [tasot] (nth tasot 3))
                              @u/urakan-toimenpiteet-ja-tehtavat)
                   toteumat-tehtavatietoineen (mapv
                                                  (fn [toteuma]
                                                    (let [tehtavan-tiedot
                                                          (first (filter (fn [tehtava]
                                                                           (= (:id tehtava) (:toimenpidekoodi_id toteuma)))
                                                                         tehtavat))
                                                          yksikkohinta-hoitokaudella (or (:yksikkohinta (first (filter
                                                                                                                 (fn [tyo] (and (= (:tehtava tyo) (:toimenpidekoodi_id toteuma))
                                                                                                                                (pvm/sama-pvm? (:alkupvm tyo) (first @u/valittu-hoitokausi))))
                                                                                                                 @u/urakan-yks-hint-tyot))) nil)
                                                          suunniteltu-maara-hoitokaudella (or (:maara (first (filter
                                                                                                               (fn [tyo] (and (= (:tehtava tyo) (:toimenpidekoodi_id toteuma))
                                                                                                                              (pvm/sama-pvm? (:alkupvm tyo) (first @u/valittu-hoitokausi))))
                                                                                                               @u/urakan-yks-hint-tyot))) nil)]
                                                      (-> toteuma
                                                          (merge (dissoc tehtavan-tiedot :id))
                                                          (assoc :yksikkohinta yksikkohinta-hoitokaudella)
                                                          (assoc :suunniteltu-maara-hoitokaudella suunniteltu-maara-hoitokaudella))))
                                                  @yksikkohintaiset-toteumat)
                   yhteensa {:id -1
                             :nimi       "Yhteensä"
                             :yhteenveto true
                             :toteutuneet-kustannukset (reduce + (mapv
                                                                   (fn [rivi]
                                                                     (* (:yksikkohinta rivi) (:toteutunut_maara rivi)))
                                                                   toteumat-tehtavatietoineen))}]
               (if (empty? toteumat-tehtavatietoineen)
                 toteumat-tehtavatietoineen
                 (conj toteumat-tehtavatietoineen yhteensa))))))

(defn muodosta-materiaalisarakkeet [rivit]
  (mapv (fn [materiaali]
          {:otsikko materiaali :nimi (keyword materiaali) :muokattava? (constantly false) :tyyppi :string :leveys "33%"})
        (distinct (mapv :materiaali_nimi rivit))))
(defonce materiaalitoteumat
         (reaction<! [urakka-id (:id @nav/valittu-urakka)
                      hallintayksikko-id (:id @nav/valittu-hallintayksikko)
                      hoitokauden-alkupvm (first @u/valittu-hoitokausi)
                      hoitokauden-loppupvm (second @u/valittu-hoitokausi)
                      aikavali-alkupvm (first @u/valittu-aikavali)
                      aikavali-loppupvm (second @u/valittu-aikavali)
                      nakymassa? @nakymassa?
                      tama-raportti-valittu? (= :materiaaliraportti (:nimi @valittu-raporttityyppi))]
                     (when (and nakymassa? tama-raportti-valittu?)
                       (if (and urakka-id hoitokauden-alkupvm hoitokauden-loppupvm)
                         (raportit/hae-materiaaliraportti-urakalle urakka-id hoitokauden-alkupvm hoitokauden-loppupvm)
                         (if hallintayksikko-id
                           (raportit/hae-materiaaliraportti-hallintayksikolle hallintayksikko-id aikavali-alkupvm aikavali-loppupvm)
                           (raportit/hae-materiaaliraportti-koko-maalle aikavali-alkupvm aikavali-loppupvm))))))

(def +raporttityypit+
  [{:nimi       :yks-hint-kuukausiraportti
    :otsikko    "Yks. hint. töiden kuukausiraportti"
    :konteksti  #{:urakka}
    :parametrit #{:valitun-urakan-hoitokaudet :valitun-aikavalin-kuukaudet}
    :render     (fn []
                  [grid/grid
                   {:otsikko      "Yksikköhintaisten töiden kuukausiraportti"
                    :tyhja        (if (empty? @yksikkohintaiset-toteumat-kaikkine-tietoineen) "Ei raportoitavia tehtäviä.")
                    :tunniste :materiaali_id}
                   [{:otsikko "Päivämäärä" :nimi :alkanut :muokattava? (constantly false) :tyyppi :pvm :fmt pvm/pvm-opt :leveys "20%"}
                    {:otsikko "Tehtävä" :nimi :nimi :muokattava? (constantly false) :tyyppi :numero :leveys "30%"}
                    {:otsikko "Yksikkö" :nimi :yksikko :muokattava? (constantly false) :tyyppi :numero :leveys "10%"}
                    {:otsikko "Yksikköhinta" :nimi :yksikkohinta :muokattava? (constantly false) :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt}
                    {:otsikko "Suunniteltu määrä hoitokaudella" :nimi :suunniteltu-maara-hoitokaudella :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
                    {:otsikko "Toteutunut määrä" :nimi :toteutunut_maara :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
                    {:otsikko "Suunnitellut kustannukset hoitokaudella" :nimi :suunnitellut-kustannukset-hoitokaudella :fmt fmt/euro-opt :hae (fn [rivi]
                                                                                                                                                (let [yksikkohinta (:yksikkohinta rivi)
                                                                                                                                                      suunniteltu-maara-hoitokaudella (:suunniteltu-maara-hoitokaudella rivi)]
                                                                                                                                                  (when (and yksikkohinta suunniteltu-maara-hoitokaudella)
                                                                                                                                                    (* yksikkohinta suunniteltu-maara-hoitokaudella)))) :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
                    {:otsikko "Toteutuneet kustannukset" :nimi :toteutuneet-kustannukset :fmt fmt/euro-opt :hae (fn [rivi] (or (:toteutuneet-kustannukset rivi)
                                                                                                                               (* (:yksikkohinta rivi) (:toteutunut_maara rivi)))) :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}]
                   @yksikkohintaiset-toteumat-kaikkine-tietoineen])}
   {:nimi       :materiaaliraportti
    :otsikko    "Materiaaliraportti"
    :konteksti  #{:urakka :hallintayksikko :koko-maa}
    :parametrit #{:valitun-urakan-hoitokaudet :koko-maan-hoitokaudet :valitun-hallintayksikon-hoitokaudet}
    :render     (fn []
                  (let [v-ur @nav/valittu-urakka
                        v-hal @nav/valittu-hallintayksikko
                        grid-otsikko (if v-ur
                                       "Urakan materiaaliraportti"
                                       (if v-hal
                                         "Hallintayksikön materiaaliraportti"
                                         "Koko maan materiaaliraportti"))
                        perussarakkeet [{:otsikko "Urakka" :nimi :urakka_nimi :muokattava? (constantly false) :tyyppi :string :leveys "33%"}]
                        toteumasarakkeet (muodosta-materiaalisarakkeet @materiaalitoteumat)
                        _ (log "[RAPORTIT] Toteumasarakkeet " (pr-str toteumasarakkeet))
                        lopulliset-sarakkeet (reduce conj perussarakkeet toteumasarakkeet)]
                    [grid/grid
                     {:otsikko grid-otsikko
                      :tyhja   (if (empty? @materiaalitoteumat) "Ei raportoitavia materiaaleja.")}
                     lopulliset-sarakkeet
                     [:asd 1]]))}]) ; FIXME Muodosta rivit

(defn raporttinakyma []
  (komp/luo
    (fn []
      (let [valittu-raporttityyppi @valittu-raporttityyppi] ; FIXME renderiä ei ajeta uudestaan vaikka tämä päivittyy?
        (:render valittu-raporttityyppi)))))

(defn raporttivalinnat []
  (komp/luo
    (fn []
      (let [v-ur @nav/valittu-urakka
            v-hal @nav/valittu-hallintayksikko]
        [:div.raporttivalinnat
         [:div.raportin-tyyppi
          [:div.label-ja-alasveto
           [:span.alasvedon-otsikko "Valitse raportti"]
           [livi-pudotusvalikko {:valinta    @valittu-raporttityyppi
                                 ;;\u2014 on väliviivan unikoodi
                                 :format-fn  #(if % (:otsikko %) "Valitse")
                                 :valitse-fn #(reset! valittu-raporttityyppi %)
                                 :class      "valitse-raportti-alasveto"}
            +raporttityypit+]]]
         (when @valittu-raporttityyppi
           [:div.raportin-asetukset
            ; Aina pelkkä urakka-konteksti, pakota valitsemaan hallintayksikkö ja urakka (jos ei ole jo valittu
            (when (= (:konteksti @valittu-raporttityyppi) #{:urakka})
              (urakat/valitse-hallintayksikko-ja-urakka))
            (when (and (contains? (:parametrit @valittu-raporttityyppi) :valitun-urakan-hoitokaudet)
                       v-ur
                       v-hal)
              [valinnat/urakan-hoitokausi @nav/valittu-urakka])
            (when (and (contains? (:parametrit @valittu-raporttityyppi) :valitun-aikavalin-kuukaudet)
                       v-ur
                       v-hal)
              [valinnat/hoitokauden-kuukausi])
            (when ; TODO Ilmeisesti ei ole mielekästä tapaa näyttää koko maan tai hallintayksikon hoitokausia,
                  ; koska koko maan tai hallintayksikön alueella on eri pituisia hoito/-sopimuskausia sisältäviä urakoita.
                  ; --> Näytä yleinen aikavälikomponentti, josta hoitokauden voi valita itse.
              (and (or (contains? (:parametrit @valittu-raporttityyppi) :valitun-hallintayksikon-hoitokaudet)
                       (contains? (:parametrit @valittu-raporttityyppi) :koko-maan-hoitokaudet))
                   (nil? v-ur))
              [valinnat/aikavali])])]))))

(defn raporttivalinnat-ja-raportti []
  (let [v-ur @nav/valittu-urakka
        hae-urakan-tyot (fn [ur]
                          (log "[RAPORTTI] Haetaan urakan yks. hint. ja kok. hint. työt")
                          (go (reset! u/urakan-kok-hint-tyot (<! (kok-hint-tyot/hae-urakan-kokonaishintaiset-tyot ur))))
                          (go (reset! u/urakan-yks-hint-tyot
                                      (s/prosessoi-tyorivit ur
                                                            (<! (yks-hint-tyot/hae-urakan-yksikkohintaiset-tyot (:id ur)))))))]

    (when v-ur (hae-urakan-tyot @nav/valittu-urakka)) ; FIXME Tämä on kopioitu suoraan views.urakka-namespacesta.
                                                      ; Yritin siirtää urakka-namespaceen yhteyseksi, mutta tuli circular dependency. :(
                                                      ; Toimisko paremmin jos urakan yks. hint. ja kok. hint. työt käyttäisi
                                                      ; reactionia(?) --> ajettaisiin aina kun urakka vaihtuu
    [:span
     [raporttivalinnat]
     (when @raportti-valmis-naytettavaksi?
       [raporttinakyma])]))

(defn raportit []
  (komp/luo
    (komp/lippu nakymassa?)
    (fn []
      (raporttivalinnat-ja-raportti))))
