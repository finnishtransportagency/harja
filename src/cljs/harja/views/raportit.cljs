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
            [harja.views.urakka.valinnat :as valinnat]
            [harja.domain.roolit :as roolit]
            [harja.ui.raportti :as raportti])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def valittu-raporttityyppi (atom nil))


;; Tähän asetetaan suoritetun raportin elementit, jotka renderöidään
(defonce suoritettu-raportti (atom nil))

(def raportti-valmis-naytettavaksi?
  (reaction (let [valittu-raporttityyppi @valittu-raporttityyppi
                  konteksti (:konteksti valittu-raporttityyppi)
                  v-ur @nav/valittu-urakka
                  v-hal @nav/valittu-hallintayksikko
                  v-aikavali @u/valittu-aikavali]
              (when valittu-raporttityyppi
                (if (= :testiraportti (:nimi valittu-raporttityyppi))
                  true
                  (if (= konteksti #{:urakka}) ; Pelkkä urakka -konteksti
                    (and v-ur
                         v-hal)
                    (if (or (contains? (:parametrit valittu-raporttityyppi) :valitun-hallintayksikon-aikavali)
                            (contains? (:parametrit valittu-raporttityyppi) :koko-maan-aikavali))
                      (and (not (nil? v-aikavali)) (not (empty? (keep identity v-aikavali))))
                      false)))))))
(def nakymassa? (atom nil))

(defonce yksikkohintaiset-toteumat
         (reaction<! [urakka-id (:id @nav/valittu-urakka)
                      alkupvm (first @u/valittu-hoitokauden-kuukausi)
                      loppupvm (second @u/valittu-hoitokauden-kuukausi)
                      nakymassa? @nakymassa?
                      tama-raportti-valittu? (= :yks-hint-raportti (:nimi @valittu-raporttityyppi))]
                     (when (and urakka-id alkupvm loppupvm nakymassa? tama-raportti-valittu?)
                       (log "[RAPORTTI] Haetaan yks. hint. kuukausiraportti parametreilla: " urakka-id alkupvm loppupvm)
                       (raportit/hae-yksikkohintaisten-toiden-kuukausiraportti urakka-id alkupvm loppupvm))))
(defonce yksikkohintaiset-toteumat-kaikkine-tietoineen
         (reaction
           (when
             @nav/valittu-urakka
             @yksikkohintaiset-toteumat
             (let [tehtavat (map
                              (fn [tasot] (let [kolmostaso (nth tasot 2)
                                                nelostaso (nth tasot 3)]
                                            (assoc nelostaso :t3_koodi (:koodi kolmostaso))))
                              @u/urakan-toimenpiteet-ja-tehtavat)
                   toteumat-tehtavatietoineen (mapv
                                                (fn [toteuma]
                                                  (let [tehtavan-tiedot (first
                                                                          (filter
                                                                            (fn [tehtava]
                                                                              (= (:id tehtava) (:toimenpidekoodi_id toteuma)))
                                                                            tehtavat))
                                                        yksikkohinta-hoitokaudella (or (:yksikkohinta
                                                                                         (first
                                                                                           (filter
                                                                                             (fn [tyo] (and (= (:tehtava tyo) (:toimenpidekoodi_id toteuma))
                                                                                                            (pvm/sama-pvm? (:alkupvm tyo) (first @u/valittu-hoitokausi))))
                                                                                             @u/urakan-yks-hint-tyot))) nil)
                                                        suunniteltu-maara-hoitokaudella (or (:maara
                                                                                              (first
                                                                                                (filter
                                                                                                  (fn [tyo] (and (= (:tehtava tyo) (:toimenpidekoodi_id toteuma))
                                                                                                                 (pvm/sama-pvm? (:alkupvm tyo) (first @u/valittu-hoitokausi))))
                                                                                                  @u/urakan-yks-hint-tyot))) nil)]
                                                    (-> toteuma
                                                        (merge (dissoc tehtavan-tiedot :id))
                                                        (assoc :yksikkohinta yksikkohinta-hoitokaudella)
                                                        (assoc :suunniteltu-maara-hoitokaudella suunniteltu-maara-hoitokaudella))))
                                                @yksikkohintaiset-toteumat)]
               toteumat-tehtavatietoineen))))
(defn muodosta-materiaalisarakkeet
  "Käy läpi materiaalitoteumat ja muodostaa toteumissa esiintyvistä materiaaleista yhden sarakkeen kustakin."
  [materiaalitoteumat]
  (let [materiaalit (distinct (mapv (fn [materiaali]
                                      (select-keys materiaali [:materiaali_nimi :materiaali_nimi_lyhenne]))
                                    materiaalitoteumat))]
    (mapv (fn [materiaali]
            {:otsikko     (:materiaali_nimi materiaali)
             :nimi        (keyword (:materiaali_nimi materiaali))
             :muokattava? (constantly false)
             :tyyppi
                          :string
             :leveys      "33%"})
          materiaalit)))

(defn muodosta-materiaaliraportin-rivit
  "Yhdistää saman urakan materiaalitoteumat yhdeksi grid-komponentin riviksi."
  [materiaalitoteumat]
  (let [materiaali-nimet (distinct (mapv :materiaali_nimi materiaalitoteumat))
        urakka-nimet (distinct (mapv :urakka_nimi materiaalitoteumat))
        urakkarivit (vec (map-indexed (fn [index urakka]
                                        (reduce ; Lisää urakkaan liittyvien materiaalien kokonaismäärät avain-arvo pareina tälle riville
                                          (fn [eka toka]
                                            (assoc eka (keyword (:materiaali_nimi toka)) (:kokonaismaara toka)))
                                          (reduce (fn [eka toka] ; Lähtöarvona rivi, jossa urakan nimi ja kaikki materiaalit nollana
                                                    (assoc eka (keyword toka) 0))
                                                  {:id index :urakka_nimi urakka}
                                                  materiaali-nimet)
                                          (filter
                                            #(= (:urakka_nimi %) urakka)
                                            materiaalitoteumat)))
                                      urakka-nimet))]
    urakkarivit))
(defn muodosta-materiaaliraportin-yhteensa-rivi
  "Palauttaa rivin, jossa eri materiaalien määrät on summattu yhteen"
  [materiaalitoteumat]
  (let [materiaalinimet (distinct (mapv :materiaali_nimi materiaalitoteumat))]
    (reduce (fn [eka toka]
              (assoc eka (keyword toka) (reduce + (mapv
                                                    :kokonaismaara
                                                    (filter
                                                      #(= (:materiaali_nimi %) toka)
                                                      materiaalitoteumat)))))
            {:id -1 :urakka_nimi "Yhteensä" :yhteenveto true}
            materiaalinimet)))
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
  [{:nimi       :yks-hint-raportti
    :otsikko    "Yksikköhintaisten töiden raportti"
    :konteksti  #{:urakka}
    :parametrit #{:valitun-urakan-hoitokaudet :valitun-aikavalin-kuukaudet :valitun-urakan-toimenpiteet+kaikki}
    :render     (fn []
                  (let [filtteroidyt-tehtavat (case (:tpi_nimi @u/valittu-toimenpideinstanssi)
                                                "Kaikki" @yksikkohintaiset-toteumat-kaikkine-tietoineen
                                                (filter (fn [tehtava]
                                                          (or (:yhteenveto tehtava)
                                                              (= (:t3_koodi tehtava) (:t3_koodi @u/valittu-toimenpideinstanssi))))
                                                        @yksikkohintaiset-toteumat-kaikkine-tietoineen))
                        yhteensa {:id                                      -1
                                  :nimi                                    "Yhteensä"
                                  :yhteenveto                              true
                                  :toteutuneet-kustannukset                (reduce + (mapv
                                                                                       (fn [rivi]
                                                                                         (* (:yksikkohinta rivi) (:toteutunut_maara rivi)))
                                                                                       filtteroidyt-tehtavat))
                                  :suunnitellut-kustannukset-hoitokaudella (reduce + (mapv
                                                                                       (fn [rivi]
                                                                                         (* (:yksikkohinta rivi) (:suunniteltu-maara-hoitokaudella rivi)))
                                                                                       filtteroidyt-tehtavat))}
                        naytettavat-rivit (if (empty? filtteroidyt-tehtavat)
                                            []
                                            (conj filtteroidyt-tehtavat yhteensa))]
                    [grid/grid
                     {:otsikko "Yksikköhintaisten töiden raportti"
                      :tyhja   (if (empty? naytettavat-rivit) "Ei raportoitavia tehtäviä.")}
                     [{:otsikko "Päivämäärä" :nimi :alkanut :muokattava? (constantly false) :tyyppi :pvm :fmt pvm/pvm-opt :leveys "20%"}
                      {:otsikko "Tehtävä" :nimi :nimi :muokattava? (constantly false) :tyyppi :numero :leveys "30%"}
                      {:otsikko "Yksikkö" :nimi :yksikko :muokattava? (constantly false) :tyyppi :numero :leveys "10%"}
                      {:otsikko "Yksikköhinta" :nimi :yksikkohinta :muokattava? (constantly false) :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt}
                      {:otsikko "Suunniteltu määrä hoitokaudella" :nimi :suunniteltu-maara-hoitokaudella :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
                      {:otsikko "Toteutunut määrä" :nimi :toteutunut_maara :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
                      {:otsikko     "Suunnitellut kustannukset hoitokaudella" :nimi :suunnitellut-kustannukset-hoitokaudella :fmt fmt/euro-opt
                       :hae         (fn [rivi]
                                      (or (:suunnitellut-kustannukset-hoitokaudella rivi)
                                          (let [yksikkohinta (:yksikkohinta rivi)
                                                suunniteltu-maara-hoitokaudella (:suunniteltu-maara-hoitokaudella rivi)]
                                            (when (and yksikkohinta suunniteltu-maara-hoitokaudella)
                                              (* yksikkohinta suunniteltu-maara-hoitokaudella)))))
                       :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
                      {:otsikko     "Toteutuneet kustannukset" :nimi :toteutuneet-kustannukset :fmt fmt/euro-opt
                       :hae         (fn [rivi]
                                      (or (:toteutuneet-kustannukset rivi)
                                          (* (:yksikkohinta rivi) (:toteutunut_maara rivi))))
                       :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}]
                     (sort
                       (fn [eka toka] (pvm/ennen? (:alkanut eka) (:alkanut toka)))
                       naytettavat-rivit)]))}
   {:nimi       :materiaaliraportti
    :otsikko    "Materiaaliraportti"
    :konteksti  #{:urakka :hallintayksikko :koko-maa}
    :parametrit #{:valitun-urakan-hoitokaudet :koko-maan-aikavali :valitun-hallintayksikon-aikavali}
    :render     (fn []
                  (let [v-ur @nav/valittu-urakka
                        v-hal @nav/valittu-hallintayksikko
                        grid-otsikko (if v-ur
                                       (str (:nimi v-ur) " - Materiaaliraportti "
                                            (pvm/pvm (first @u/valittu-hoitokausi))
                                            " - "
                                            (pvm/pvm (second @u/valittu-hoitokausi)))
                                       (if v-hal
                                         (str (:nimi v-hal) " - Materiaaliraportti "
                                              (pvm/pvm (first @u/valittu-aikavali))
                                              " - "
                                              (pvm/pvm (second @u/valittu-aikavali)))
                                         (str "Koko maan materiaaliraportti "
                                              (pvm/pvm (first @u/valittu-aikavali))
                                              " - "
                                              (pvm/pvm (second @u/valittu-aikavali)))))
                        perussarakkeet [{:otsikko "Urakka" :nimi :urakka_nimi :muokattava? (constantly false) :tyyppi :string :leveys "33%"}]
                        materiaalisarakkeet (muodosta-materiaalisarakkeet @materiaalitoteumat)
                        lopulliset-sarakkeet (reduce conj perussarakkeet materiaalisarakkeet)
                        _ (log "[RAPORTTI] Materiaaliraportin sarakkeet: " (pr-str lopulliset-sarakkeet))
                        urakkarivit (muodosta-materiaaliraportin-rivit @materiaalitoteumat)
                        yhteensa-rivi (muodosta-materiaaliraportin-yhteensa-rivi @materiaalitoteumat)
                        lopulliset-rivit (conj urakkarivit yhteensa-rivi)
                        _ (log "[RAPORTTI] Materiaaliraportin rivit: " (pr-str lopulliset-rivit))]
                    [grid/grid
                     {:otsikko grid-otsikko
                      :tyhja   (if (empty? @materiaalitoteumat) "Ei raportoitavia materiaaleja.")}
                     lopulliset-sarakkeet
                     (if (> (count urakkarivit) 0)
                       lopulliset-rivit
                       [])]))}

   {:nimi       :testiraportti
    :otsikko    "Testiraportti"
    :konteksti  #{:urakka :hallintayksikko :koko-maa}
    :parametrit []
    :render     (fn []
                  [:raportti {:nimi    "Testiraportti"
                              :tietoja [["Urakka" "Rymättylän päällystys"]
                                        ["Aika" "15.7.2015 \u2014 30.9.2015"]]}
                   [:otsikko "Tämä on hieno raportti"]
                   [:teksti "Tässäpä on sitten kappale tekstiä, joka raportissa tulee. Tämähän voisi olla mitä vain, kuten vaikka lorem ipsum dolor sit amet."]
                   [:taulukko {:otsikko "Testidataa"}
                    [{:otsikko "Nimi" :leveys "50%"}
                               {:otsikko "Kappaleita" :leveys "15%"}
                               {:otsikko "Hinta" :leveys "15%"}
                               {:otsikko "Yhteensä" :leveys "20%"}]

                    [["Fine leather jacket" 2 199 (* 2 199)]
                     ["Log from blammo" 1 39 39]
                     ["Suristin" 10 25 250]]]

                   [:otsikko "Tähän taas väliotsikko"]
                   [:pylvaat {:otsikko "Kvartaalien luvut"}
                    [["Q1" 123]
                     ["Q2" 1500]
                     ["Q3" 1000]
                     ["Q4" 777]]]
                   [:yhteenveto [["PDF-generointi" "toimii"]
                                 ["XSL-FO" "hyvin"]]]])}

   ])

(defn raporttinakyma [tyyppi]
  (if (= :testiraportti (:nimi tyyppi))
    (raportti/muodosta-html ((:render tyyppi)))
    ((:render tyyppi))))

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
            ; Jos kontekstissa ainoastaan urakka, pakota valitsemaan hallintayksikkö ja urakka (jos ei ole jo valittu)
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
            (when
              (and (or (contains? (:parametrit @valittu-raporttityyppi) :valitun-hallintayksikon-aikavali)
                       (contains? (:parametrit @valittu-raporttityyppi) :koko-maan-aikavali))
                   (nil? v-ur))
              [valinnat/aikavali])
            (when
              (and (contains? (:parametrit @valittu-raporttityyppi) :valitun-urakan-toimenpiteet+kaikki) v-ur)
              [valinnat/urakan-toimenpide+kaikki])])]))))

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
       [raporttinakyma @valittu-raporttityyppi])]))

(defn raportit []
  (komp/luo
    (komp/lippu nakymassa?)
    (fn []
      (if (roolit/roolissa? roolit/tilaajan-kayttaja)
        [:span
         [kartta/kartan-paikka]
         (raporttivalinnat-ja-raportti)]
        [:span "Sinulla ei ole oikeutta tarkastella raportteja."]))))
