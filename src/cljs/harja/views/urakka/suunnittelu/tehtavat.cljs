(ns harja.views.urakka.suunnittelu.tehtavat
  (:require [reagent.core :as r]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.debug :as debug]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.mhu-tehtavat :as t]
            [harja.ui.taulukko.taulukko :as taulukko]
            [harja.ui.taulukko.jana :as jana]
            [harja.ui.taulukko.osa :as osa]
            [harja.ui.taulukko.tyokalut :as tyokalu]
            [harja.ui.taulukko.protokollat :as p]
            [harja.loki :refer [log]]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.pvm :as pvm]
            [harja.loki :as loki])
  (:require-macros [harja.ui.taulukko.tyokalut :refer [muodosta-taulukko]]))

(defn sarakkeiden-leveys [sarake]
  (case sarake
    :tehtava "col-xs-12 col-sm-8 col-md-8 col-lg-8"
    :maara "col-xs-12 col-sm-8 col-md-8 col-lg-8"
    :maara-input "col-xs-12 col-sm-2 col-md-2 col-lg-2"
    :maara-yksikko "col-xs-12 col-sm-2 col-md-2 col-lg-2"))


(defn osien-paivitys-fn [tehtava maara yksikko]
  (fn [osat]
    (mapv
      (fn [osa]
        (case (p/osan-id osa)
          "Tehtävä" (tehtava osa)
          "Määrä" (maara osa)
          "Yksikkö" (yksikko osa)))
      osat)))

;; [{:id "1" :nimi "1.0 TALVIHOITO" :tehtavaryhmatyyppi "otsikko" :piillotettu? false}
;; {:id "2" :tehtava-id 4548 :nimi "Ise 2-ajorat." :tehtavaryhmatyyppi "tehtava" :maara 50 :vanhempi "1" :piillotettu? false}
;; {:id "3" :nimi "2.1 LIIKENNEYMPÄRISTÖN HOITO" :tehtavaryhmatyyppi "otsikko" :piillotettu? false}
;; {:id "4" :tehtava-id 4565 :nimi "Liikennemerkkien ja opasteiden kunnossapito (oikominen, pesu yms.)" :tehtavaryhmatyyppi "tehtava" :maara 50 :vanhempi "3" :piillotettu? false}
;; {:id "5" :tehtava-id 4621  :nimi "Opastustaulun/-viitan uusiminen" :tehtavaryhmatyyppi "tehtava" :maara 50 :vanhempi "3" :piillotettu? false}]

;; TODO: Muodosta palautettavat tiedot. Vrt. println tulostukset.

(defn- otsikkorivi
  [rivi]
  (-> rivi
      (p/aseta-arvo :id :tehtava
                    :class #{"table-default" "table-default-header"})
      (p/paivita-arvo :lapset
                      (osien-paivitys-fn #(p/aseta-arvo %
                                                        :id :tehtava-nimi
                                                        :arvo "Tehtävä"
                                                        :class #{(sarakkeiden-leveys :maara)})
                                         #(p/aseta-arvo %
                                                        :id :tehtava-maara
                                                        :arvo "Määrä"
                                                        :class #{(sarakkeiden-leveys :maara-input)})
                                         #(p/aseta-arvo %
                                                        :id :tehtava-yksikko
                                                        :arvo "Yksikkö"
                                                        :class #{(sarakkeiden-leveys :maara-yksikko)})))))

(defn- validi?
  [arvo tyyppi]
  (let [validius (case tyyppi
                   :numero (re-matches #"\d+(?:\.?,?\d+)?" (str arvo)))]
    (not (nil? validius))))

(defn- luo-syottorivit
  [e! rivi tnt]
  (let [luku (atom {})
        pura-rivit (map (fn [[_ rivi]] rivi))
        lajittele-jarjestyksen-mukaan #(fn [rf]
                                         (let [s (volatile! [])]
                                           (fn
                                             ([]
                                              (rf))
                                             ([kaikki]
                                              (let [ss @s]
                                                (vreset! s [])
                                                (reduce rf kaikki (sort (fn [{a :jarjestys} {b :jarjestys}]
                                                                          (compare a b)) ss))))
                                             ([kaikki syote]
                                              (let [syote syote]
                                                (vswap! s conj syote)
                                                kaikki)))))
        tee-rivit (map
                    (fn [{:keys [nimi maarat id vanhempi yksikko jarjestys]}]
                      (swap! luku update vanhempi inc)
                      (-> rivi
                          (p/aseta-arvo :id (keyword (str vanhempi "/" id))
                                        :class #{(str "table-default-" (if (= 0 (rem (get @luku vanhempi) 2)) "even" "odd"))}
                                        :piillotettu? false)
                          (p/paivita-arvo :lapset
                                          (osien-paivitys-fn #(p/aseta-arvo %
                                                                            :id :tehtava-nimi
                                                                            :arvo (str nimi)
                                                                            :class #{(sarakkeiden-leveys :maara)})
                                                             #(p/aseta-arvo %
                                                                            :id (keyword (str vanhempi "/" id "-maara"))
                                                                            :arvo (->> @tila/tila :yleiset :urakka :alkupvm pvm/vuosi str keyword (get maarat))
                                                                            :class #{(sarakkeiden-leveys :maara-input) "input-default"}
                                                                            :on-blur (fn [arvo]
                                                                                       (let [arvo (-> arvo (.. -target -value))]
                                                                                         (when (validi? arvo :numero)
                                                                                           (e! (t/->TallennaTehtavamaara
                                                                                                 {:urakka-id  (-> @tila/tila :yleiset :urakka :id)
                                                                                                  :tehtava-id id
                                                                                                  :maara      arvo})))))
                                                                            :on-change (fn [arvo]
                                                                                         (e!
                                                                                           (t/->PaivitaMaara osa/*this*
                                                                                                             (-> arvo (.. -target -value))
                                                                                                             #{(sarakkeiden-leveys :maara-input) "input-default" (if (validi? (-> arvo (.. -target -value)) :numero) "" "ei-validi")}))))
                                                             #(p/aseta-arvo %
                                                                            :id :tehtava-yksikko
                                                                            :arvo (or yksikko "")
                                                                            :class #{(sarakkeiden-leveys :maara-yksikko)}))))))
        ota-vain-neljas-taso (filter (fn [[_ t]]
                                       (= 4 (:taso t))))
        xform-fn (comp ota-vain-neljas-taso
                       pura-rivit
                       (lajittele-jarjestyksen-mukaan)
                       tee-rivit)]
    (into [] xform-fn tnt)))


(defn luo-tehtava-taulukko
  [e! tehtavat-ja-toimenpiteet]
  (let [polku-taulukkoon [:tehtavat-taulukko]
        taulukon-paivitys-fn! (fn [paivitetty-taulukko app]
                                (assoc-in app polku-taulukkoon paivitetty-taulukko))
        syottorivi (fn [rivi]
                     (luo-syottorivit e! rivi tehtavat-ja-toimenpiteet))]
    (muodosta-taulukko :tehtavat
                       {:teksti {:janan-tyyppi jana/Rivi
                                 :osat         [osa/Teksti osa/Teksti osa/Teksti]}
                        :syotto {:janan-tyyppi jana/Rivi
                                 :osat         [osa/Teksti osa/Syote osa/Teksti]}}
                       ["Tehtävä" "Määrä" "Yksikkö"]
                       [:teksti otsikkorivi
                        :syotto syottorivi]
                       {:class                 #{}
                        :taulukon-paivitys-fn! taulukon-paivitys-fn!})))

(defn noudetaan-taulukko
  []
  (let [datarivi (fn [rivi] (-> rivi
                                (p/aseta-arvo :id :dummy-rivi
                                              :class #{"table-default-odd"})
                                (p/paivita-arvo :lapset
                                                (osien-paivitys-fn #(p/aseta-arvo %
                                                                                  :id :tehtava-nimi
                                                                                  :class #{(sarakkeiden-leveys :maara)})
                                                                   #(-> % (p/aseta-arvo
                                                                            :id :tehtava-maara
                                                                            :class #{(sarakkeiden-leveys :maara)})
                                                                        (assoc :komponentti (fn [_ {:keys [teksti]} _] (yleiset/ajax-loader teksti {:luokka "col-xs-12 keskita"}))
                                                                               :komponentin-argumentit {:teksti "Haetaan tehtäviä"}))
                                                                   #(p/aseta-arvo %
                                                                                  :id :tehtava-yksikko
                                                                                  :class #{(sarakkeiden-leveys :maara)})))))]
    (muodosta-taulukko :noudetaan-tehtavat
                       {:teksti    {:janan-tyyppi jana/Rivi
                                    :osat         [osa/Teksti osa/Teksti osa/Teksti]}
                        :datarivit {:janan-tyyppi jana/Rivi
                                    :osat         [osa/Teksti osa/Komponentti osa/Teksti]}}
                       ["Tehtävä" "Määrä" "Yksikkö"]
                       [:teksti otsikkorivi
                        :datarivit datarivi]
                       {:class                 #{}
                        :taulukon-paivitys-fn! identity})))

(defn valitaso-filtteri
  [_ app]
  (let [{:keys [alkupvm]} (-> @tila/tila :yleiset :urakka)]
    (fn [e! {:keys [tehtavat-ja-toimenpiteet valinnat] :as app}]
      (let [vuosi (pvm/vuosi alkupvm)
            toimenpide-xform (comp (map
                                     (fn [[_ data]] data))
                                   (filter
                                     (fn [data]
                                       (= 3 (:taso data)))))
            toimenpiteet (sort-by :nimi (into [] toimenpide-xform tehtavat-ja-toimenpiteet))
            hoitokaudet (into [] (range vuosi (+ 5 vuosi)))
            disabloitu-alasveto? (fn [koll]
                                   (= 0 (count koll)))]

        [:div
         [:div.label-ja-alasveto
          [:span.alasvedon-otsikko "Toimenpide"]
          [yleiset/livi-pudotusvalikko {:valinta    (:toimenpide valinnat)
                                        :valitse-fn #(e! (t/->ValitseTaso % :toimenpide))
                                        :format-fn  #(:nimi %)
                                        :disabled   (disabloitu-alasveto? toimenpiteet)}
           toimenpiteet]]
         [:div.label-ja-alasveto
          [:span.alasvedon-otsikko "Hoitokausi"]
          [yleiset/livi-pudotusvalikko {:valinta    (:hoitokausi valinnat)
                                        :valitse-fn #(e! (t/->HaeMaarat {:hoitokausi        %
                                                                         :prosessori        (partial luo-tehtava-taulukko e!)
                                                                         :tilan-paivitys-fn (fn [tila] (assoc-in tila [:valinnat :hoitokausi] %))}))
                                        :format-fn  #(str "1.10." % "-30.9." (inc %))
                                        :disabled   (disabloitu-alasveto? hoitokaudet)}
           hoitokaudet]]
         [:label.kopioi-tuleville-vuosille
          [:input {:type      "checkbox"
                   :checked   (:samat-kaikille valinnat)
                   :on-change #(e! (t/->SamatKaikilleMoodi (not (:samat-kaikille valinnat))))
                   :disabled  (:noudetaan valinnat)}]
          "Samat suunnitellut määrät kaikille hoitokausille"]]))))

(defn tehtavat*
  [e! app]
  (komp/luo
    (komp/piirretty (fn [this]
                      (e! (t/->HaeTehtavatJaMaarat
                            {:hoitokausi         (-> @tila/tila :yleiset :urakka :alkupvm pvm/vuosi)
                             :tehtavat->taulukko (partial luo-tehtava-taulukko e!)}))))
    (fn [e! app]
      (let [{taulukon-tehtavat :tehtavat-taulukko} app
            {:keys [nimi]} (-> @tila/tila :yleiset :urakka)]
        [:div
         ;[debug/debug app]
         [:h1 "Tehtävät ja määrät" [:span.pull-right nimi]]
         [:div "Tehtävät ja määrät suunnitellaan urakan alussa, ja tarkennetaan jokaisen hoitovuoden alussa. " [:a {:href "#"} "Toteuma"] "-puolelle kirjataan ja kirjautuu kalustosta toteutuneet määrät."]
         [valitaso-filtteri e! app]
         (if taulukon-tehtavat
           [p/piirra-taulukko taulukon-tehtavat]
           [p/piirra-taulukko (noudetaan-taulukko)])]))))

(defn tehtavat []
  (tuck/tuck tila/suunnittelu-tehtavat tehtavat*))