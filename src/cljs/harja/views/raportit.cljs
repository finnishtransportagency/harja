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

(defonce valittu-raporttityyppi (atom nil))

(def nakymassa? (atom nil))

(defonce yksikkohintaiset-toteumat
         (reaction<! [urakka-id (:id @nav/valittu-urakka)
                      alkupvm (first @u/valittu-hoitokauden-kuukausi)
                      loppupvm (second @u/valittu-hoitokauden-kuukausi)
                      nakymassa? @nakymassa?]
                     (when (and urakka-id alkupvm loppupvm nakymassa?)
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
(tarkkaile! "[RAPORTTI] Valitun raportin sisältö: " yksikkohintaiset-toteumat-kaikkine-tietoineen)

(def raportti-valmis-naytettavaksi?
  (reaction (let [valittu-raporttityyppi @valittu-raporttityyppi
                  konteksti (:konteksti valittu-raporttityyppi)
                  v-ur @nav/valittu-urakka
                  v-hal @nav/valittu-hallintayksikko]
              (if (contains? konteksti :urakka)
                (and v-ur v-hal (not (nil? valittu-raporttityyppi)))
                (not (nil? valittu-raporttityyppi))))))

(def +raporttityypit+
  [{:nimi       :yks-hint-kuukausiraportti
    :otsikko    "Yks. hint. töiden kuukausiraportti"
    :konteksti  #{:urakka}
    :parametrit #{:valitun-urakan-hoitokaudet :valitun-aikavalin-kuukaudet}
    :render     (fn []
                  [grid/grid
                   {:otsikko      "Yksikköhintaisten töiden kuukausiraportti"
                    :tyhja        (if (empty? @yksikkohintaiset-toteumat-kaikkine-tietoineen) "Ei raportoitavia tehtäviä.")
                    :voi-muokata? false}
                   [{:otsikko "Päivämäärä" :nimi :alkanut :muokattava? (constantly false) :tyyppi :pvm :fmt pvm/pvm-aika-opt :leveys "20%"}
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
                   @yksikkohintaiset-toteumat-kaikkine-tietoineen])}])

(defn raporttinakyma []
  (fn []
    (:render @valittu-raporttityyppi)))

(defn raporttivalinnat
  []
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
         [:div.raportin-asetukset
            (when (contains? (:konteksti @valittu-raporttityyppi) :urakka)
            (urakat/valitse-hallintayksikko-ja-urakka))
          (when (and v-ur v-hal (contains? (:parametrit @valittu-raporttityyppi) :valitun-urakan-hoitokaudet))
            [valinnat/urakan-hoitokausi @nav/valittu-urakka])
          (when (and v-ur v-hal (contains? (:parametrit @valittu-raporttityyppi) :valitun-aikavalin-kuukaudet))
            [valinnat/hoitokauden-kuukausi])]]))))

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
