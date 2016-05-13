(ns harja.views.urakka.yllapitokohteet
  "Ylläpitokohteet"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.fmt :as fmt]
            [harja.loki :refer [log logt tarkkaile!]]
            [clojure.string :as str]
            [cljs.core.async :refer [<!]]
            [harja.tyokalut.vkm :as vkm]
            [harja.views.kartta :as kartta]
            [harja.geo :as geo]
            [harja.ui.tierekisteri :as tierekisteri]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.tiedot.urakka :as u])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn laske-sarakkeen-summa [sarake kohderivit]
  (reduce + (mapv
              (fn [rivi] (sarake rivi))
              kohderivit)))

(defn yllapitokohdeosa-virheet [tr-virheet]
  [:div.tr-virheet
   (for [virhe (into #{} (vals @tr-virheet))]
     ^{:key (hash virhe)}
     [:div.tr-virhe (ikonit/livicon-warning-sign)
      virhe])])

;; Ylläpitokohteiden sarakkeiden leveydet
(def haitari-leveys 5)
(def id-leveys 10)
(def kohde-leveys 15)
(def kvl-leveys 5)
(def nykyinen-paallyste-leveys 8)
(def tr-leveys 8)
(def tarjoushinta-leveys 10)
(def muutoshinta-leveys 10)
(def toteutunut-hinta-leveys 10)
(def arvonvahennykset-leveys 10)
(def bitumi-indeksi-leveys 10)
(def kaasuindeksi-leveys 10)
(def yhteensa-leveys 10)

;; Ylläpitokohdeosien sarakkeiden leveydet
(def nimi-leveys 20)
(def toimenpide-leveys 20)

(defn yllapitokohdeosat [_ yllapitokohde-atom]
  (let [tr-osoite (fn [rivi]
                    (let [arvot (map rivi [:tr_numero :tr_alkuosa :tr_alkuetaisyys :tr_loppuosa :tr_loppuetaisyys])]
                      (when (every? #(not (str/blank? %)) arvot)
                        ;; Tierekisteriosoite on täytetty (ei tyhjiä kenttiä)
                        (zipmap [:numero :alkuosa :alkuetaisyys :loppuosa :loppuetaisyys]
                                arvot))))

        ;; onnistuneesti haetut TR-sijainnit
        tr-sijainnit (atom {})

        ;; virheelliset TR sijainnit 
        tr-virheet (atom {})

        resetoi-tr-tiedot (fn []
                            (reset! tr-sijainnit {})
                            (reset! tr-virheet {}))]
    (komp/luo
      (komp/ulos #(kartta/poista-popup!))
      (fn [{:keys [kohdeosat id] :as rivi} yllapitokohde-atom]
        (log "[PAAL] Renderöi alikohteet")
        [:div
         [grid/grid
          {:otsikko "Tierekisterikohteet"
           :tyhja (if (empty? kohdeosat) "Tierekisterikohteita ei löydy")
           :rivi-klikattu (fn [rivi]
                            (log "KLIKKASIT: " (pr-str rivi))
                            (when-let [viiva (some-> rivi :sijainti)]
                              (nav/vaihda-kartan-koko! :L)
                              (kartta/keskita-kartta-alueeseen! (geo/extent viiva))))
           ; FIXME Varmista, että alikohde on parentin sisällä
           :tallenna #(go (let [urakka-id (:id @nav/valittu-urakka)
                                [sopimus-id _] @u/valittu-sopimusnumero
                                sijainnit @tr-sijainnit
                                osat (into []
                                           (map (fn [osa]
                                                  (assoc osa :sijainti (sijainnit (tr-osoite osa)))))
                                           %)
                                vastaus (<! (yllapitokohteet/tallenna-yllapitokohdeosat! urakka-id sopimus-id (:id rivi) osat))]
                            (log "[PAAL] ylläpitokohdeosat tallennettu: " (pr-str vastaus))
                            (resetoi-tr-tiedot)
                            (yllapitokohteet/paivita-yllapitokohde! yllapitokohde-atom id assoc :kohdeosat vastaus)))
           :luokat ["yllapitokohdeosat-haitari"]
           :peruuta #(resetoi-tr-tiedot)
           :muutos (fn [g]
                     (log "VIRHEET:" (pr-str (grid/hae-virheet g)))
                     (let [haetut (into #{} (keys @tr-sijainnit))]
                       ;; jos on tullut uusi TR osoite, haetaan sille sijainti
                       (doseq [[id rivi] (grid/hae-muokkaustila g)]
                         (if (:poistettu rivi)
                           (swap! tr-virheet dissoc id)
                           (let [osoite (tr-osoite rivi)]
                             (when (and osoite (not (haetut osoite)))
                               (go
                                 (log "Haetaan TR osoitteen sijainti: " (pr-str osoite))
                                 (let [sijainti (<! (vkm/tieosoite->viiva osoite))]
                                   (when (= (get (grid/hae-muokkaustila g) id) rivi) ;; ettei rivi ole uudestaan muuttunut
                                     (if-let [virhe (when-not (vkm/loytyi? sijainti)
                                                      "Virheellinen TR-osoite")]
                                       (do (swap! tr-virheet assoc id virhe)
                                           (doseq [kentta [:tr_numero :tr_alkuosa :tr_alkuetaisyys :tr_loppuosa :tr_loppuetaisyys]]
                                             (grid/aseta-virhe! g id kentta "Tarkista tie")))
                                       (do (swap! tr-virheet dissoc id)
                                           (doseq [kentta [:tr_numero :tr_alkuosa :tr_alkuetaisyys :tr_loppuosa :tr_loppuetaisyys]]
                                             (grid/poista-virhe! g id kentta))
                                           (log "sain sijainnin " (clj->js sijainti))
                                           (swap! tr-sijainnit assoc osoite sijainti))))))))))))}
          [{:otsikko "Nimi" :nimi :nimi :tyyppi :string :leveys nimi-leveys :validoi [[:ei-tyhja "Anna nimi"]]}
           {:otsikko "Tienumero" :nimi :tr_numero :tyyppi :positiivinen-numero
            :leveys tr-leveys :validoi [[:ei-tyhja "Anna tienumero"]]}
           {:otsikko "Aosa" :nimi :tr_alkuosa :tyyppi :positiivinen-numero
            :leveys tr-leveys :validoi [[:ei-tyhja "Anna alkuosa"]]}
           {:otsikko "Aet" :nimi :tr_alkuetaisyys :tyyppi :positiivinen-numero
            :leveys tr-leveys :validoi [[:ei-tyhja "Anna alkuetäisyys"]]}
           {:otsikko "Losa" :nimi :tr_loppuosa :tyyppi :positiivinen-numero
            :leveys tr-leveys :validoi [[:ei-tyhja "Anna loppuosa"]]}
           {:otsikko "Let" :nimi :tr_loppuetaisyys :tyyppi :positiivinen-numero
            :leveys tr-leveys :validoi [[:ei-tyhja "Anna loppuetäisyys"]]}
           {:otsikko "Pit" :nimi :pit :muokattava? (constantly false) :tyyppi :string
            :hae (fn [rivi]
                   (str (tierekisteri/laske-tien-pituus {:aet (:tr_alkuetaisyys rivi)
                                                         :let (:tr_loppuetaisyys rivi)})))
            :leveys tr-leveys}
           {:otsikko "Toimenpide" :nimi :toimenpide :tyyppi :string :leveys toimenpide-leveys :validoi [[:ei-tyhja "Anna toimenpide"]]}]
          kohdeosat]
         [yllapitokohdeosa-virheet tr-virheet]]))))

(defn yllapitokohteet [kohteet-atom optiot]
  [grid/grid
   {:otsikko (:otsikko optiot)
    :tyhja (if (nil? @kohteet-atom) [ajax-loader "Haetaan kohteita..."] "Ei kohteita")
    :vetolaatikot (into {} (map (juxt :id
                                      (fn [rivi]
                                        [yllapitokohdeosat rivi kohteet-atom]))
                                @kohteet-atom))
    :tallenna (:tallenna optiot)
    :voi-lisata? (not (:yha-sidottu? optiot))
    :voi-poistaa? (constantly (not (:yha-sidottu? optiot)))
    :esta-poistaminen? (fn [rivi] (or (not (nil? (:paallystysilmoitus_id rivi)))
                                      (not (nil? (:paikkausilmoitus_id rivi)))))
    :esta-poistaminen-tooltip (fn [_] "Kohteelle on kirjattu ilmoitus, kohdetta ei voi poistaa.")}
   [{:tyyppi :vetolaatikon-tila :leveys haitari-leveys}
    {:otsikko "Kohde\u00ADnu\u00ADme\u00ADro" :nimi :kohdenumero :tyyppi :string :leveys id-leveys
     :validoi [[:uniikki "Sama kohdenumero voi esiintyä vain kerran."]]}
    {:otsikko "Kohteen nimi" :nimi :nimi
     :tyyppi :string :leveys kohde-leveys}
    {:otsikko "Tie\u00ADnu\u00ADme\u00ADro" :nimi :tr_numero :muokattava? (constantly (not (:yha-sidottu? optiot)))
     :tyyppi :positiivinen-numero :leveys tr-leveys}
    {:otsikko "Aosa" :nimi :tr_alkuosa :muokattava? (constantly (not (:yha-sidottu? optiot)))
     :tyyppi :positiivinen-numero :leveys tr-leveys}
    {:otsikko "Aet" :nimi :tr_alkuetaisyys :muokattava? (constantly (not (:yha-sidottu? optiot)))
     :tyyppi :positiivinen-numero :leveys tr-leveys}
    {:otsikko "Losa" :nimi :tr_loppuosa :muokattava? (constantly (not (:yha-sidottu? optiot)))
     :tyyppi :positiivinen-numero :leveys tr-leveys}
    {:otsikko "Let" :nimi :tr_loppuetaisyys :muokattava? (constantly (not (:yha-sidottu? optiot)))
     :tyyppi :positiivinen-numero :leveys tr-leveys}
    {:otsikko "Pit" :nimi :pit :muokattava? (constantly false) :tyyppi :string
     :hae (fn [rivi]
            (str (tierekisteri/laske-tien-pituus {:aet (:tr_alkuetaisyys rivi)
                                                  :let (:tr_loppuetaisyys rivi)})))
     :leveys tr-leveys}
    {:otsikko "KVL"
     :nimi :keskimaarainen_vuorokausiliikenne :tyyppi :numero :leveys kvl-leveys
     :muokattava? (constantly (not (:yha-sidottu? optiot)))}
    {:otsikko "Ny\u00ADkyi\u00ADnen pääl\u00ADlys\u00ADte"
     :nimi :nykyinen_paallyste
     :fmt #(paallystys-ja-paikkaus/hae-paallyste-koodilla %)
     :tyyppi :valinta
     :valinta-arvo :koodi
     :valinnat paallystys-ja-paikkaus/+paallystetyypit+
     :valinta-nayta :nimi
     :leveys nykyinen-paallyste-leveys
     :muokattava? (constantly (not (:yha-sidottu? optiot)))}
    (when (:paallystysnakyma? optiot)
      {:otsikko "Tarjous\u00ADhinta" :nimi :sopimuksen_mukaiset_tyot
       :fmt fmt/euro-opt :tyyppi :numero :leveys tarjoushinta-leveys :validoi [[:ei-tyhja "Anna arvo"]]})
    (when (:paallystysnakyma? optiot)
      {:otsikko "Muutok\u00ADset" :nimi :muutoshinta :muokattava? (constantly false)
       :fmt fmt/euro-opt :tyyppi :numero :leveys muutoshinta-leveys})
    (when (:paikkausnakyma? optiot)
      {:otsikko "Toteutunut hinta" :nimi :toteutunut_hinta :muokattava? (constantly false)
       :fmt fmt/euro-opt :tyyppi :numero :leveys toteutunut-hinta-leveys})
    {:otsikko "Arvon\u00ADväh." :nimi :arvonvahennykset :fmt fmt/euro-opt
     :tyyppi :numero :leveys arvonvahennykset-leveys :validoi [[:ei-tyhja "Anna arvo"]]}
    {:otsikko "Bitumi-in\u00ADdek\u00ADsi" :nimi :bitumi_indeksi :fmt fmt/euro-opt
     :tyyppi :numero :leveys bitumi-indeksi-leveys :validoi [[:ei-tyhja "Anna arvo"]]}
    {:otsikko "Kaasu\u00ADindeksi" :nimi :kaasuindeksi :fmt fmt/euro-opt
     :tyyppi :numero :leveys kaasuindeksi-leveys :validoi [[:ei-tyhja "Anna arvo"]]}
    {:otsikko "Kokonais\u00ADhinta (ind\u00ADek\u00ADsit mu\u00ADka\u00ADna)" :muokattava? (constantly false)
     :nimi :kokonaishinta :fmt fmt/euro-opt :tyyppi :numero :leveys yhteensa-leveys
     :hae (fn [rivi] (+ (:sopimuksen_mukaiset_tyot rivi)
                        (:muutoshinta rivi)
                        (:toteutunut_hinta rivi)
                        (:arvonvahennykset rivi)
                        (:bitumi_indeksi rivi)
                        (:kaasuindeksi rivi)))}]
   @kohteet-atom])

(defn yllapitokohteet-yhteensa [kohteet-atom optiot]
  (let [yhteensa (reaction (let [kohteet @kohteet-atom
                                 sopimuksen-mukaiset-tyot-yhteensa (laske-sarakkeen-summa :sopimuksen_mukaiset_tyot kohteet)
                                 toteutunut-hinta-yhteensa (laske-sarakkeen-summa :toteutunut_hinta kohteet)
                                 muutoshinta-yhteensa (laske-sarakkeen-summa :muutoshinta kohteet)
                                 arvonvahennykset-yhteensa (laske-sarakkeen-summa :arvonvahennykset kohteet)
                                 bitumi-indeksi-yhteensa (laske-sarakkeen-summa :bitumi_indeksi kohteet)
                                 kaasuindeksi-yhteensa (laske-sarakkeen-summa :kaasuindeksi kohteet)
                                 kokonaishinta (+ sopimuksen-mukaiset-tyot-yhteensa
                                                  toteutunut-hinta-yhteensa
                                                  muutoshinta-yhteensa
                                                  arvonvahennykset-yhteensa
                                                  bitumi-indeksi-yhteensa
                                                  kaasuindeksi-yhteensa)]
                             [{:id 0
                               :sopimuksen_mukaiset_tyot sopimuksen-mukaiset-tyot-yhteensa
                               :muutoshinta muutoshinta-yhteensa
                               :toteutunut_hinta toteutunut-hinta-yhteensa
                               :arvonvahennykset arvonvahennykset-yhteensa
                               :bitumi_indeksi bitumi-indeksi-yhteensa
                               :kaasuindeksi kaasuindeksi-yhteensa
                               :kokonaishinta kokonaishinta}]))]
    [grid/grid
     {:otsikko "Yhteensä"
      :tyhja (if (nil? {}) [ajax-loader "Lasketaan..."] "")}
     [{:otsikko "" :nimi :tyhja :tyyppi :string :leveys haitari-leveys}
      {:otsikko "" :nimi :kohdenumero :tyyppi :string :leveys id-leveys}
      {:otsikko "" :nimi :nimi :tyyppi :string :leveys kohde-leveys}
      {:otsikko "" :nimi :tr_numero :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :tr_alkuosa :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :tr_alkuetaisyys :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :tr_loppuosa :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :tr_loppuetaisyys :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :pit :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :nimi :tyyppi :string :leveys kohde-leveys}
      {:otsikko "" :nimi :keskimaarainen_vuorokausiliikenne :tyyppi :string :leveys kvl-leveys}
      {:otsikko "" :nimi :nykyinen_paallyste :tyyppi :string :leveys nykyinen-paallyste-leveys}
      (when (:paallystysnakyma? optiot)
        {:otsikko "Tarjous\u00ADhinta" :nimi :sopimuksen_mukaiset_tyot :fmt fmt/euro-opt :tyyppi :numero
         :leveys tarjoushinta-leveys})
      (when (:paallystysnakyma? optiot)
        {:otsikko "Muutok\u00ADset" :nimi :muutoshinta :fmt fmt/euro-opt :tyyppi :numero
         :leveys muutoshinta-leveys})
      (when (:paikkausnakyma? optiot)
        {:otsikko "Toteutunut hinta" :nimi :toteutunut_hinta :fmt fmt/euro-opt :tyyppi :numero
         :leveys toteutunut-hinta-leveys})
      {:otsikko "Arvon\u00ADväh." :nimi :arvonvahennykset :fmt fmt/euro-opt :tyyppi :numero
       :leveys arvonvahennykset-leveys}
      {:otsikko "Bitumi-indeksi" :nimi :bitumi_indeksi :fmt fmt/euro-opt :tyyppi :numero
       :leveys bitumi-indeksi-leveys}
      {:otsikko "Kaasu\u00ADindeksi" :nimi :kaasuindeksi :fmt fmt/euro-opt :tyyppi :numero
       :leveys kaasuindeksi-leveys}
      {:otsikko "Kokonais\u00ADhinta (indeksit mukana)" :nimi :kokonaishinta :fmt fmt/euro-opt
       :tyyppi :numero :leveys yhteensa-leveys}
      {:otsikko "" :nimi :muokkaustoiminnot-tyhja :tyyppi :string :leveys 3}]
     @yhteensa]))