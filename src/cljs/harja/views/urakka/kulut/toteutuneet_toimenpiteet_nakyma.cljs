(ns harja.views.urakka.kulut.toteutuneet-toimenpiteet-nakyma
  "Vesiväylä-hoitourakan toteutuneet toimenpiteet ja kustannukset."
  (:require [harja.views.urakka.valinnat :as urakka-valinnat]
            [tuck.core :as tuck]
            [reagent.core :as r]
            [clojure.string :as str]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]

            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.valinnat :as ui-valinnat]
            [harja.ui.komponentti :as komp]

            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.tiedot.urakka.kulut.toteutuneet-toimenpiteet-tiedot :as tiedot]))

(defn- urakkavuoden-aikavali [vuosi]
  (pvm/vuodesta-hoitokausi vuosi))

(defn- urakkavuodet [urakka]
  (->> (urakka-tiedot/hoito-tai-sopimuskaudet urakka)
       (map (comp pvm/vuosi first))
       distinct
       vec))

(defn- kuukaudet [vuosi]
  (into ["Kaikki"]
        (pvm/aikavalin-kuukausivalit (urakkavuoden-aikavali vuosi))))

(defn- maaramuoto
  "Hieman monimutkainen määrän käsittely, mutta tarpeellinen nyt, kun kaikkia VHU featureita ei ole vielä mergetty."
  [arvo]
  (if (some? arvo)
    (fmt/desimaaliluku arvo 2)
    "-"))

(defn- euromuoto
  "Hieman monimutkainen euron käsittely, mutta tarpeellinen nyt, kun kaikkia VHU featureita ei ole vielä mergetty."
  [arvo]
  (if (some? arvo)
    (fmt/euro arvo)
    "-"))

(defn toimenpiteet-valiotsikoilla [toimenpiteet]
  (->> toimenpiteet
       (partition-by (juxt :tehtavaryhmaotsikko_id :tehtavaryhma_id))
       (mapcat (fn [ryhma]
                 (let [{:keys [tehtavaryhmaotsikko tehtavaryhma_nimi]} (first ryhma)]
                   (cons (grid/otsikko (str tehtavaryhmaotsikko " / " tehtavaryhma_nimi))
                         ryhma))))
       vec))

(defn- suodattimet [e! {:keys [:haku-kaynnissa? :kuukausi] :as app}]
  (let [hoitokauden-kuukaudet (kuukaudet (pvm/vuosi (first @urakka-tiedot/valittu-aikavali)))]
    [:div.toteutuneet-toimenpiteet-suodattimet
     {:data-cy "toteutuneet-toimenpiteet-suodattimet"}
     [:div.rajausvalinta
      [kentat/tee-kentta
       {:tyyppi :radio-group
        :vaihtoehdot [:urakkavuoden-mukaan :valittu-aikavali]
        :vaihtoehto-nayta {:urakkavuoden-mukaan "Urakkavuoden mukaan"
                           :valittu-aikavali "Valittu aikaväli"}
        :vayla-tyyli? true
        :nayta-rivina? false}
       (r/wrap (:rajauksen-tyyppi app)
         #(e! (tiedot/->ValitseRajauksenTyyppi %)))]]
     (if (= :urakkavuoden-mukaan (:rajauksen-tyyppi app))
       [:div.urakkavuosi-ja-kuukausi
        [:div.filtteri
         [urakka-valinnat/paivittava-urakkavuosi-tuck
          @urakka-tiedot/valittu-aikavali
          #(e! (tiedot/->HaeToteutuneetToimenpiteet)) haku-kaynnissa? false]]
        [:div.filtteri.kuukausi.label-ja-alasveto
         [:label.alasvedon-otsikko {:for "kuukausi"} "Kuukausi"]
         [yleiset/livi-pudotusvalikko {:valinta kuukausi
                                       :vayla-tyyli? true
                                       :elementin-id "kuukausi"
                                       :valitse-fn #(e! (tiedot/->ValitseKuukausi %))
                                       :format-fn #(if %
                                                     (if (= "Kaikki" %)
                                                       "Kaikki"
                                                       (let [[alkupvm _] %
                                                             kk-teksti (pvm/kuukauden-nimi (pvm/kuukausi alkupvm))]
                                                         (str (str/capitalize kk-teksti) " " (pvm/vuosi alkupvm))))
                                                     "Kaikki")
                                       :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
          hoitokauden-kuukaudet]]]
       [ui-valinnat/aikavali
        (r/wrap (:aikavali app)
          #(e! (tiedot/->ValitseAikavali %)))
        {:otsikko "Aikaväli"
         :vayla-tyyli? true}])]))

(defn- toimenpiteet-grid [app]
  (let [koko-hoitokausi? (and (= :urakkavuoden-mukaan (:rajauksen-tyyppi app))
                              (= "Kaikki" (:kuukausi app)))
        toimenpiteet (:toimenpiteet app)]
    [grid/grid
     {:otsikko "Toimenpiteet ja laskutus"
      :tunniste :tehtava_id
      :data-cy "toteutuneet-toimenpiteet-grid"
      :tyhja (if (:haku-kaynnissa? app)
               [yleiset/ajax-loader-pieni "Haetaan tietoja..."]
               "Toimenpiteitä ei löytynyt valitulta aikaväliltä.")
      :voi-kumota? false
      :piilota-toiminnot? true}
     [{:otsikko "Tehtävä" :nimi :tehtava_nimi :tyyppi :string :leveys 4}
      {:otsikko "Tavoitemäärä / urakkavuosi" :nimi :tavoitemaara :tyyppi :string :leveys 2 :tasaa :oikea
       :hae #(maaramuoto (:tavoitemaara %))}
      {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :leveys 1}
      {:otsikko "Yksikköhinnan keskiarvo (€)" :nimi :yksikkohinnan_keskiarvo :tyyppi :string :leveys 2 :tasaa :oikea
       :hae (constantly "-")}
      {:otsikko "Toteutunut määrä" :nimi :toteutunut_maara :tyyppi :string :leveys 2 :tasaa :oikea
       :hae (constantly "-")}
      {:otsikko "Toteutunut kustannus (€)" :nimi :toteutunut_kustannus :tyyppi :string :leveys 2 :tasaa :oikea
       :hae #(euromuoto (:toteutunut_kustannus %))}
      {:otsikko "Määrä %" :nimi :maaramuutos_prosentteina :tyyppi :string :leveys 1 :tasaa :oikea
       :hae (constantly "-")}
      {:otsikko "Määrämuutos (+/-)" :nimi :maaramuutos :tyyppi :string :leveys 1 :tasaa :oikea
       :hae (constantly "-")}
      {:otsikko "Vaikutus tavoitehintaan (€)" :nimi :vaikutus_tavoitehintaan :tyyppi :string :leveys 2 :tasaa :oikea
       :hae #(if koko-hoitokausi? (euromuoto (:vaikutus_tavoitehintaan %)) "-")}]
     (toimenpiteet-valiotsikoilla toimenpiteet)]))

(defn- toteutuneet-toimenpiteet* [e! app]
  (komp/luo
    (komp/piirretty
      (fn [_]
        (let [urakka (-> @tila/yleiset :urakka)
              vuodet (urakkavuodet urakka)
              urakkavuosi (if (some #{(:urakkavuosi app)} vuodet)
                            (:urakkavuosi app)
                            (last vuodet))]
          (if (and urakkavuosi (not= urakkavuosi (:urakkavuosi app)))
            (e! (tiedot/->ValitseUrakkavuosi urakkavuosi))
            (e! (tiedot/->HaeToteutuneetToimenpiteet))))))
    (fn [_ app]
      [:div.toteutuneet-toimenpiteet
       [:div.header
        [:h1 "Toteutuneiden toimenpiteiden kustannukset"]
        [:p.urakka (:nimi (-> @tila/yleiset :urakka))]]
       [suodattimet e! app]
       [toimenpiteet-grid app]])))

(defn toteutuneet-toimenpiteet []
  (tuck/tuck tila/toteutuneet-toimenpiteet toteutuneet-toimenpiteet*))
