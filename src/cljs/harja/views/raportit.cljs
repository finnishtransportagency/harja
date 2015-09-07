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
            [cljs-time.core :as t])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce valittu-raporttityyppi (atom nil))
(defonce valitun-raportin-sisalto (atom nil))
(tarkkaile! "[RAPORTTI] Valitun raportin sisältö: " valitun-raportin-sisalto)

(def +raporttityypit+
  ; FIXME: Hardcoodattu testidata, tämän on tarkoitus tulla myöhemmin serveriltä(?)
  [{:nimi      :laskutusyhteenveto
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
                  {:otsikko "Yksikköhinta" :nimi :yksikkohinta :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
                  {:otsikko "Suunniteltu määrä" :nimi :suunniteltu-maara :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
                  {:otsikko "Toteutunut määrä" :nimi :toteutunut_maara :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
                  {:otsikko "Suunnitellut kustannukset" :nimi :suunnitellut-kustannukset :fmt fmt/euro-opt :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
                  {:otsikko "Toteutuneet kustannukset" :nimi :toteutuneet-kustannukset :fmt fmt/euro-opt :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}]
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
(def raporttivalinnat-virheet (atom nil))

(tarkkaile! "[RAPORTTI] Raporttivalinnat-tiedot: " raporttivalinnat-tiedot)

(defn raporttinakyma []
  (let [urakka-id (:id @nav/valittu-urakka)
        alkupvm (t/minus (t/now) (t/years 30)) ; FIXME Käytä valittua kuukautta
        loppupvm (t/plus alkupvm (t/years 30))
        tehtavat (map
                   (fn [tasot] (nth tasot 3))
                   @u/urakan-toimenpiteet-ja-tehtavat)]

    ; FIXME Haku raporttityypin mukaan
    (go (let [toteumat (<! (raportit/hae-yksikkohintaisten-toiden-kuukausiraportti urakka-id alkupvm loppupvm))
                toteumat-kaikkine-tietoineen (-> (mapv ; Tehtävän tiedot (mm. yksikkö)
                                                   (fn [toteuma]
                                                     (let [tehtavan-tiedot (first (filter (fn [tehtava]
                                                                                            (= (:id tehtava) (:toimenpidekoodi_id toteuma)))
                                                                                          tehtavat))]
                                                       (merge toteuma (dissoc tehtavan-tiedot :id)))) toteumat))]
          (reset! valitun-raportin-sisalto toteumat-kaikkine-tietoineen)))
    (fn []
      (:render @valittu-raporttityyppi))))

(def raportti-valmis-naytettavaksi?
  (reaction (let [valittu-raporttityyppi @valittu-raporttityyppi
                  lomake-virheet @raporttivalinnat-virheet]
              (and valittu-raporttityyppi
                         (not (nil? lomake-virheet))
                         (empty? lomake-virheet)))))

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
