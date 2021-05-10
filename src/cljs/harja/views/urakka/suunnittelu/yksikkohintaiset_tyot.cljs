(ns harja.views.urakka.suunnittelu.yksikkohintaiset-tyot
  "Urakan 'Yksikkohintaiset työt' välilehti:"
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :as yleiset :refer [ajax-loader linkki raksiboksi
                                                  alasveto-ei-loydoksia livi-pudotusvalikko vihje]]
            [harja.visualisointi :as vis]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.suunnittelu.yksikkohintaiset-tyot :as yks-hint-tyot]
            [harja.tiedot.urakka.urakan-toimenpiteet :as urakan-toimenpiteet]

            [harja.loki :refer [log logt tarkkaile!]]
            [harja.fmt :as fmt]
            [cljs.core.async :refer [<!]]

            [harja.views.urakka.valinnat :as u-valinnat]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.urakka :as urakka-domain]
            [clojure.string :as str]
            [harja.ui.valinnat :as valinnat]
            [harja.domain.urakka :as u-domain])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))


(def tuleville? (atom false))

(defn tallenna-tyot [ur sopimusnumero valittu-hoitokausi tyot uudet-tyot]
  (go (let [muuttuneet
            (into []
                  (if @tuleville?
                    (u/rivit-tulevillekin-kausille ur uudet-tyot valittu-hoitokausi)
                    uudet-tyot))
            res (<! (yks-hint-tyot/tallenna-urakan-yksikkohintaiset-tyot ur sopimusnumero muuttuneet))
            prosessoidut-tyorivit (s/prosessoi-tyorivit ur res)]
        (reset! tyot prosessoidut-tyorivit)
        (reset! tuleville? false)
        true)))

(defn luo-tyhja-tyo [tp ur hk]
  {:tehtava (:id tp), :tehtavan_nimi (:nimi tp) :yksikko (:yksikko tp) :urakka (:id ur)
   :alkupvm (first hk) :loppupvm (second hk)})

(defn ryhmittele-hinnoitellut
  "Ryhmittelee ja väliotsikoi hinnoitellut ja ei-hinnoitellut tehtävät"
  [tyorivit]
  (let [otsikko (fn [{:keys [yksikkohinta]}]
                  (if-not yksikkohinta
                    :ei-hintaa
                    :hinta))
        otsikon-mukaan (group-by otsikko tyorivit)]
    (concat [(grid/otsikko "Hinnoiteltu urakassa")]
            (get otsikon-mukaan :hinta)
            [(grid/otsikko "Hinta merkitsemättä tai tehtävä ei kuulu urakkaan")]
            (get otsikon-mukaan :ei-hintaa))))

(defmulti urakkatyypin-sarakkeet (fn [tyyppi] tyyppi))

(defmethod urakkatyypin-sarakkeet :hoito [_]
  [{:otsikko "Tehtävä" :nimi :tehtavan_nimi :tyyppi :string :muokattava? (constantly false) :leveys "30%"}
   {:otsikko (str "Määrä 10-12/" (.getYear (first @u/valittu-hoitokausi))) :nimi :maara-kkt-10-12 :tyyppi :positiivinen-numero :leveys "10%" :tasaa :oikea}
   {:otsikko (str "Yhteen\u00ADsä " (.getYear (first @u/valittu-hoitokausi))) :nimi :yhteensa-kkt-10-12
    :tasaa :oikea :tyyppi :string :muokattava? (constantly false) :leveys "10%" :fmt fmt/euro-opt}
   {:otsikko (str "Määrä 1-9/" (.getYear (second @u/valittu-hoitokausi))) :nimi :maara-kkt-1-9 :tyyppi :positiivinen-numero :leveys "10%" :tasaa :oikea}
   {:otsikko (str "Yhteen\u00ADsä " (.getYear (second @u/valittu-hoitokausi))) :nimi :yhteensa-kkt-1-9
    :tasaa :oikea :tyyppi :string :muokattava? (constantly false) :leveys "10%" :fmt fmt/euro-opt}
   {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :muokattava? (constantly false) :leveys "7%"}
   {:otsikko (str "Yksikkö\u00ADhinta") :nimi :yksikkohinta :tasaa :oikea :tyyppi :positiivinen-numero :fmt fmt/euro-opt :leveys "10%"}
   {:otsikko "Kausi yhteensä" :nimi :yhteensa :tasaa :oikea :tyyppi :string :muokattava? (constantly false) :leveys "13%" :fmt fmt/euro-opt}])

(defmethod urakkatyypin-sarakkeet :vesivayla-hoito [_]
  [{:otsikko "Tehtävä" :nimi :tehtavan_nimi :tyyppi :string :muokattava? (constantly false) :leveys 4}
   {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :muokattava? (constantly false) :leveys 1}
   {:otsikko (str "Yksikkö\u00ADhinta") :nimi :yksikkohinta :tasaa :oikea :tyyppi :numero
    :fmt fmt/euro-opt :leveys 1}])

(defmethod urakkatyypin-sarakkeet :vesivayla-kanavien-hoito [_]
  [{:otsikko "Tehtävä" :nimi :tehtavan_nimi :tyyppi :string :muokattava? (constantly false) :leveys 4}
   {:otsikko (str "Yksikkö\u00ADhinta") :nimi :yksikkohinta :tasaa :oikea :tyyppi :numero
    :fmt fmt/euro-opt :leveys 1}
   {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :muokattava? (constantly false) :leveys 1}
   {:otsikko "Arvioitu kustan\u00ADnus/v" :nimi :arvioitu_kustannus :tasaa :oikea :tyyppi :numero
    :fmt fmt/euro-opt :leveys 1}])

;; Ylläpidon sarakkeet
(defmethod urakkatyypin-sarakkeet :default [_]
  [{:otsikko "Tehtävä" :nimi :tehtavan_nimi :tyyppi :string :muokattava? (constantly false) :leveys "40%"}
   {:otsikko "Määrä" :nimi :maara :tyyppi :numero :leveys "15%" :tasaa :oikea}
   {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :muokattava? (constantly false) :leveys "15%"}
   {:otsikko (str "Yksikkö\u00ADhinta") :nimi :yksikkohinta :tasaa :oikea :tyyppi :numero
    :fmt fmt/euro-opt :leveys "15%"}
   {:otsikko "Yhteensä" :nimi :yhteensa :tasaa :oikea :tyyppi :string :muokattava? (constantly false)
    :leveys "15%" :fmt fmt/euro-opt}])

(defn paivita-hoitorivin-summat [{:keys [maara-kkt-10-12 maara-kkt-1-9 yksikkohinta] :as rivi}]
  (let [yht-10-12 (and yksikkohinta maara-kkt-10-12
                       (* yksikkohinta maara-kkt-10-12))
        yht-1-9 (and yksikkohinta maara-kkt-1-9
                     (* yksikkohinta maara-kkt-1-9))]
    (assoc rivi
      :yhteensa-kkt-10-12 yht-10-12
      :yhteensa-kkt-1-9 yht-1-9
      :yhteensa (and yht-10-12 yht-1-9 (+ yht-10-12 yht-1-9)))))

(defn paivita-yllapitorivin-summat [{:keys [maara yksikkohinta] :as rivi}]
  (assoc rivi
      :yhteensa (and maara yksikkohinta (* maara yksikkohinta))))

(defn etuliitteen-mukaan-valiotsikoilla [tyorivit]
  (->> tyorivit
       (map #(let [nimi (:tehtavan_nimi %)
                   kaksoispiste (.indexOf nimi ":")
                   valiotsikko (if (neg? kaksoispiste)
                                 ""
                                 (str/trim (subs nimi 0 kaksoispiste)))
                   nimi (if (neg? kaksoispiste)
                          nimi
                          (str/trim (subs nimi (inc kaksoispiste))))]
               (assoc %
                      :tehtavan_nimi nimi
                      :valiotsikko valiotsikko)))
       (group-by :valiotsikko)
       (sort-by first)
       (mapcat (fn [[otsikko tehtavat]]
                 (into (if (str/blank? otsikko)
                         []
                         [(grid/otsikko otsikko)])
                       tehtavat)))))

(defn- nayta-kustannukset? [ur]
  (and (not= (:tyyppi ur) :vesivayla-hoito)
       (not= (:tyyppi ur) :vesivayla-kanavien-hoito)))

(defn- suodattimet [urakka]
  [valinnat/urakkavalinnat {:urakka urakka}
   (with-meta
     (case (urakka-domain/urakkatyyppi urakka)
       :kanava [u-valinnat/urakan-sopimus-ja-hoitokausi-ja-toimenpide urakka]
       :vv [u-valinnat/urakan-sopimus-ja-hoitokausi urakka]
       [u-valinnat/urakan-sopimus-ja-hoitokausi-ja-toimenpide+muut urakka])
     {:key "valinnat"})])

(defn yksikkohintaiset-tyot-view [ur valitun-hoitokauden-yks-hint-kustannukset]
  (let [urakan-yks-hint-tyot u/urakan-yks-hint-tyot
        toimenpiteet-ja-tehtavat (atom nil)
        urakka (atom nil)
        hae-urakan-tiedot (fn [ur]
                            (reset! urakka ur)
                            ;; Tehdään hoitokauden osien (10-12 / 1-9) yhdistäminen urakalle
                            (go (reset! toimenpiteet-ja-tehtavat
                                        (<! (urakan-toimenpiteet/hae-urakan-yksikkohintaiset-toimenpiteet-ja-tehtavat (:id ur))))))
        sopimuksen-tyot
        (reaction
          (into []
                (filter (fn [t]
                          (= (:sopimus t) (first @u/valittu-sopimusnumero))))
                @urakan-yks-hint-tyot))


        sopimuksen-tyot-hoitokausittain
        (reaction (u/ryhmittele-hoitokausittain @sopimuksen-tyot
                                                @u/valitun-urakan-hoitokaudet))

        varoita-ylikirjoituksesta?
        (reaction (let [kopioi? @tuleville?
                        varoita? (s/varoita-ylikirjoituksesta? @sopimuksen-tyot-hoitokausittain @u/valittu-hoitokausi)]
                    (if-not kopioi?
                      false
                      varoita?)))

        tyorivit
        (reaction (let [valittu-hoitokausi @u/valittu-hoitokausi
                        valittu-toimenpide @u/valittu-toimenpideinstanssi
                        alkupvm (first valittu-hoitokausi)
                        toimenpiteet-ja-tehtavat @toimenpiteet-ja-tehtavat
                        loppupvm (second valittu-hoitokausi)
                        tehtavien-rivit (get @sopimuksen-tyot-hoitokausittain [alkupvm loppupvm])
                        nelostason-tpt (map #(nth % 3) toimenpiteet-ja-tehtavat)
                        kirjatut-tehtavat (into #{} (map :tehtava tehtavien-rivit))
                        tyhjat-tyot (mapv #(luo-tyhja-tyo % ur valittu-hoitokausi)
                                         (filter (fn [tp]
                                                   (not (kirjatut-tehtavat (:id tp))))
                                                 nelostason-tpt))
                        toimenpiteen-tehtavat
                        (sort-by :tehtavan_nimi
                                 (filter (fn [{:keys [tehtava]}]
                                           (case (:tpi_nimi valittu-toimenpide)
                                             "Muut" (not-any? (fn [[t1 t2 t3 t4]]
                                                                (= (:id t4) tehtava))
                                                              toimenpiteet-ja-tehtavat)
                                             (some (fn [[t1 t2 t3 t4]] ; Näytetään valittu TPI
                                                     (and
                                                       (= (:id t4) tehtava)
                                                       (= (:koodi t2) (:t2_koodi valittu-toimenpide))))
                                                   toimenpiteet-ja-tehtavat)))
                                         (concat tehtavien-rivit tyhjat-tyot)))]
                    toimenpiteen-tehtavat))

        kaikkien-hoitokausien-kustannukset
        (reaction (transduce (comp (mapcat second)
                                   (map #(* (:maara %) (:yksikkohinta %))))
                             + 0
                             (seq @sopimuksen-tyot-hoitokausittain)))

        vesivaylaurakka? (reaction (urakka-domain/vesivaylaurakkatyyppi? (:tyyppi @urakka)))
        tyorivit-joilla-hinta (reaction
                               (let [tyorivit (filter :yksikkohinta @tyorivit)]
                                 (if @vesivaylaurakka?
                                   (etuliitteen-mukaan-valiotsikoilla tyorivit)
                                   tyorivit)))

        toimenpiteen-kustannukset (reaction (reduce + 0 (keep :yhteensa @tyorivit-joilla-hinta)))]

    (hae-urakan-tiedot ur)
    (komp/luo
      {:component-will-receive-props
       (fn [_ & [_ ur]]
         (log "UUSI URAKKA: " (pr-str (dissoc ur :alue)))
         (hae-urakan-tiedot ur))

       :component-will-unmount
       (fn [this]
         (reset! tuleville? false))}

      (fn [ur]
        [:div.yksikkohintaiset-tyot
         [suodattimet ur]
         [grid/grid
          {:otsikko "Urakkasopimuksen mukaiset yksikköhinnat"
           :tyhja (if (nil? @toimenpiteet-ja-tehtavat) [ajax-loader "Yksikköhintaisia töitä haetaan..."] "Ei yksikköhintaisia töitä")
           :tallenna (if (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-yksikkohintaisettyot (:id ur))
                       #(tallenna-tyot ur @u/valittu-sopimusnumero @u/valittu-hoitokausi urakan-yks-hint-tyot %)
                       :ei-mahdollinen)
           :tallenna-vain-muokatut false
           :tallennus-ei-mahdollinen-tooltip (oikeudet/oikeuden-puute-kuvaus
                                              :kirjoitus
                                              oikeudet/urakat-suunnittelu-yksikkohintaisettyot)
           :peruuta #(reset! tuleville? false)
           :tunniste :tehtava
           :voi-lisata? false
           :voi-poistaa? (constantly false)
           :aloita-muokkaus-fn (fn [_]
                                 (if @vesivaylaurakka?
                                   (etuliitteen-mukaan-valiotsikoilla @tyorivit)
                                   (ryhmittele-hinnoitellut @tyorivit)))
           :piilota-toiminnot? true
           :muokkaa-footer (fn [g]
                             [raksiboksi {:teksti (s/monista-tuleville-teksti (:tyyppi ur))
                                          :info-teksti [:div.raksiboksin-info (ikonit/livicon-warning-sign) "Tulevilla hoitokausilla eri tietoa, jonka tallennus ylikirjoittaa."]
                                          :nayta-infoteksti? @varoita-ylikirjoituksesta?}
                              tuleville?])
           :prosessoi-muutos (if (= :hoito (:tyyppi ur))
                               (fn [rivit]
                                 (let [rivit (seq rivit)]
                                   (zipmap (map first rivit)
                                           (map (comp paivita-hoitorivin-summat second) rivit))))
                               (fn [rivit]
                                 (let [rivit (seq rivit)]
                                   (zipmap (map first rivit)
                                           (map (comp paivita-yllapitorivin-summat second) rivit)))))}

          ;; sarakkeet
          (urakkatyypin-sarakkeet (:tyyppi ur))

          @tyorivit-joilla-hinta]
         [vihje yleiset/+tehtavien-hinta-vaihtoehtoinen+]

         (when (nayta-kustannukset? ur)
           [:div.hoitokauden-kustannukset
            [:div.summa.summa-toimenpiteen-hoitokausi
             "Yksikköhintaisten töiden toimenpiteen hoitokausi yhteensä "
             [:span (fmt/euro @toimenpiteen-kustannukset)]]
            [:div.summa.summa-hoitokausi
             "Yksikkohintaisten töiden hoitokausi yhteensä "
             [:span (fmt/euro @valitun-hoitokauden-yks-hint-kustannukset)]]
            [:div.summa.summa-hoitokaudet
             "Yksikköhintaisten töiden kaikki hoitokaudet yhteensä "
             [:span (fmt/euro @kaikkien-hoitokausien-kustannukset)]]
            [:div.piirakka-hoitokauden-kustannukset-per-kaikki.row
             [:div.col-xs-6.piirakka
              (let [valittu-kust @valitun-hoitokauden-yks-hint-kustannukset
                    kaikki-kust @kaikkien-hoitokausien-kustannukset]
                (when (or (not= 0 valittu-kust) (not= 0 kaikki-kust))
                  [:span.piirakka-wrapper
                   [:h5.piirakka-label "Tämän hoitokauden osuus kaikista hoitokausista"]
                   [vis/pie
                    {:width 230 :height 150 :radius 60 :show-text :percent :show-legend true}
                    {"Valittu hoitokausi" valittu-kust "Muut hoitokaudet" (- kaikki-kust valittu-kust)}]]))]
             [:div.col-xs-6.piirakka
              (let [yks-hint-yhteensa @valitun-hoitokauden-yks-hint-kustannukset
                    kok-hint-yhteensa @s/valitun-hoitokauden-kok-hint-kustannukset]
                (when (or (not= 0 yks-hint-yhteensa) (not= 0 kok-hint-yhteensa))
                  [:span.piirakka-wrapper
                   [:h5.piirakka-label "Hoitokauden yksikköhintaisten töiden osuus kaikista töistä"]
                   [vis/pie
                    {:width 230 :height 150 :radius 60 :show-text :percent :show-legend true}
                    {"Yksikköhintaiset" yks-hint-yhteensa "Kokonaishintaiset" kok-hint-yhteensa}]]))]]])]))))
