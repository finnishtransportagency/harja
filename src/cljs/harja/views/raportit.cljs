(ns harja.views.raportit
  "Harjan raporttien pääsivu."
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.komponentti :as komp]
            [harja.ui.valinnat :as valinnat]
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
            [harja.tiedot.urakka.suunnittelu :as s])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce valittu-raporttityyppi (atom nil))
(defonce valitun-raportin-sisalto (atom nil))
(tarkkaile! "[RAPORTTI] Valitun raportin sisältö: " valitun-raportin-sisalto)

(def +raporttityypit+
  ; FIXME: Hardcoodattu testidata, tämän on tarkoitus tulla myöhemmin serveriltä(?)
  [{:nimi      :yk-hint-kuukausiraportti
    :otsikko   "Yks.hint. töiden toteumat -raportti"
    :konteksti #{:urakka}
    :parametrit
               [{:otsikko  "Hoitokausi"
                 :nimi     :hoitokausi
                 :tyyppi   :valinta
                 :validoi  [[:ei-tyhja "Anna arvo"]]
                 :valinnat :valitun-urakan-hoitokaudet}
                {:otsikko  "Kuukausi"
                 :nimi     :kuukausi
                 :tyyppi   :valinta
                 :validoi  [[:ei-tyhja "Anna arvo"]]
                 :valinnat :valitun-aikavalin-kuukaudet}]
    :render   (fn []
                [grid/grid
                 {:otsikko      "Yksikköhintaisten töiden kuukausiraportti"
                  :tyhja        (if (empty? @valitun-raportin-sisalto) "Ei raportoitavaa tietoa.")
                  :voi-muokata? false}
                 [{:otsikko "Päivämäärä" :nimi :alkanut :muokattava? (constantly false) :tyyppi :pvm :fmt pvm/pvm-aika :leveys "20%"}
                  {:otsikko "Tehtävä" :nimi :nimi :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
                  {:otsikko "Yksikkö" :nimi :yksikko :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
                  {:otsikko "Yksikköhinta" :nimi :yksikkohinta :muokattava? (constantly false) :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt}
                  {:otsikko "Suunniteltu määrä hoitokaudella" :nimi :suunniteltu-maara-hoitokaudella :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
                  {:otsikko "Toteutunut määrä" :nimi :toteutunut_maara :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
                  {:otsikko "Suunnitellut kustannukset hoitokaudella" :nimi :suunnitellut-kustannukset-hoitokaudella :fmt fmt/euro-opt :hae (fn [rivi] (* (:yksikkohinta rivi) (:suunniteltu-maara-hoitokaudella rivi))) :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
                  {:otsikko "Toteutuneet kustannukset" :nimi :toteutuneet-kustannukset :fmt fmt/euro-opt :hae (fn [rivi] (* (:yksikkohinta rivi) (:toteutunut_maara rivi))) :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
                  {:otsikko "Lisätieto" :nimi :lisatieto :muokattava? (constantly false) :hae (fn [rivi] (let [max-pituus 80
                                                                                                               lisatieto (if (vector? (:lisatieto rivi))
                                                                                                                           (clojure.string/join "\n\n" (:lisatieto rivi))
                                                                                                                           (:lisatieto rivi))]
                                                                                                           (if (> (count lisatieto) max-pituus)
                                                                                                             (str (subs lisatieto 0 max-pituus) "...")
                                                                                                             lisatieto))) :tyyppi :string :leveys "20%"}]
                 @valitun-raportin-sisalto])}])

(defn tee-lomakekentta [kentta lomakkeen-tiedot]
  (if (= :valinta (:tyyppi kentta))
    (case (:valinnat kentta)
      :valitun-urakan-hoitokaudet
      (assoc kentta :valinnat @u/valitun-urakan-hoitokaudet
                    :valinta-nayta #(if % (fmt/pvm-vali-opt %) "- Valitse hoitokausi -"))
      :valitun-aikavalin-kuukaudet
      (assoc kentta :valinnat (if-let [hk (:hoitokausi lomakkeen-tiedot)] ; FIXME Valintojen pitäisi päivittyä jos hoitokausi vaihtuu.
                                (pvm/hoitokauden-kuukausivalit hk)
                                [])
                    :valinta-nayta #(if % (str (t/month (first %)) "/" (t/year (first %))) "- Valitse kuukausi -")))
    kentta))

(def raporttivalinnat-tiedot (atom nil))
(tarkkaile! "[RAPORTTI] Raporttivalinnat-tiedot: " raporttivalinnat-tiedot)
(def raporttivalinnat-virheet (atom nil))
(def raportti-valmis-naytettavaksi?
  (reaction (let [valittu-raporttityyppi @valittu-raporttityyppi
                  lomake-virheet @raporttivalinnat-virheet]
              (and valittu-raporttityyppi
                   (not (nil? lomake-virheet))
                   (empty? lomake-virheet)))))

(defn yk-hint-kuukausiraportti []
  (let [urakka-id (:id @nav/valittu-urakka)
        alkupvm (first (:kuukausi @raporttivalinnat-tiedot)) ; FIXME Näiden pitäisi päivittyä kun putodotusvalikon item vaihtuu.
        loppupvm (second (:kuukausi @raporttivalinnat-tiedot))
        tehtavat (map
                   (fn [tasot] (nth tasot 3))
                   @u/urakan-toimenpiteet-ja-tehtavat)]
    (go
      (log "[RAPORTTI] Haetaan yks. hint. kuukausiraportti parametreilla: " urakka-id alkupvm loppupvm)
      (let [toteumat (<! (raportit/hae-yksikkohintaisten-toiden-kuukausiraportti urakka-id alkupvm loppupvm))
            toteumat-kaikkine-tietoineen (mapv
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
                                           toteumat)]
        (reset! valitun-raportin-sisalto toteumat-kaikkine-tietoineen)))
    (fn []
      (:render @valittu-raporttityyppi))))

(defn raporttinakyma []
  (case (:nimi @valittu-raporttityyppi)
    :yk-hint-kuukausiraportti (yk-hint-kuukausiraportti)))

(defn raporttivalinnat
  []
  (komp/luo
    (fn []
      [:div.raportit
       [:div.label-ja-alasveto
        [:span.alasvedon-otsikko "Valitse raportti"]
        [livi-pudotusvalikko {:valinta    @valittu-raporttityyppi
                              ;;\u2014 on väliviivan unikoodi
                              :format-fn  #(if % (:otsikko %) "Valitse")
                              :valitse-fn #(reset! valittu-raporttityyppi %)
                              :class      "valitse-raportti-alasveto"}
         +raporttityypit+]]

       (when @valittu-raporttityyppi
         [lomake/lomake
          {:luokka   :horizontal
           :virheet  raporttivalinnat-virheet
           :muokkaa! (fn [uusi]
                       (reset! raporttivalinnat-tiedot uusi))}
          (let [lomake-tiedot @raporttivalinnat-tiedot
                kentat (into []
                             (concat
                               [{:otsikko "Kohde" :nimi :kohteen-nimi :hae #(:nimi @nav/valittu-urakka) :muokattava? (constantly false)}]
                               (map
                                 (fn [kentta]
                                   (tee-lomakekentta kentta lomake-tiedot))
                                 (:parametrit @valittu-raporttityyppi))))]
            kentat)

          @raporttivalinnat-tiedot])])))

(defn raporttivalinnat-ja-raportti []
  (go (reset! u/urakan-yks-hint-tyot ; FIXME Tämä on pöllitty suoraan views.urakka-namespacesta. Voisiko jotenkin yhdistää yhdeksi funktioksi?
              (s/prosessoi-tyorivit @nav/valittu-urakka
                                    (<! (yks-hint-tyot/hae-urakan-yksikkohintaiset-tyot (:id @nav/valittu-urakka))))))
  [:span
   [raporttivalinnat]
   (when @raportti-valmis-naytettavaksi?
     [raporttinakyma])])

(defn raportit []
  (komp/luo
    (fn []
      (or
        (urakat/valitse-hallintayksikko-ja-urakka) ; FIXME Voi olla tarve luoda raportti hallintayksikön alueesta tai koko Suomesta.
        (raporttivalinnat-ja-raportti)))))
