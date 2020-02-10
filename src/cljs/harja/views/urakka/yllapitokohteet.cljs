(ns harja.views.urakka.yllapitokohteet
  "Ylläpitokohteet"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.ui.viesti :as viesti]
            [harja.ui.validointi :as validointi]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko vihje] :as yleiset]
            [harja.fmt :as fmt]
            [harja.loki :refer [log logt tarkkaile!]]
            [clojure.string :as str]
            [clojure.set :as clj-set]
            [cljs.core.async :refer [<!]]
            [harja.tyokalut.vkm :as vkm]
            [harja.domain.tierekisteri :as tr]
            [harja.domain.paallystysilmoitus :as pot]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [harja.tiedot.urakka.yhatuonti :as yha]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.yllapitokohteet :as tiedot]
            [harja.tiedot.urakka :as u]
            [harja.asiakas.kommunikaatio :as k]
            [harja.domain.oikeudet :as oikeudet]
            [harja.atom :refer [wrap-vain-luku]]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.tiedot.urakka.paallystys :as paallystys-tiedot]
            [clojure.string :as string]
            [harja.ui.modal :as modal])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defn laske-sarakkeen-summa [sarake kohderivit]
  (reduce + 0 (keep
                (fn [rivi] (sarake rivi))
                kohderivit)))

(defn tr-virheilmoitus [tr-virheet]
  [:div.tr-virheet
   (for [virhe (into #{} (vals @tr-virheet))]
     ^{:key (hash virhe)}
     [:div.tr-virhe (ikonit/livicon-warning-sign)
      virhe])])

;; Ylläpitokohteiden sarakkeiden leveydet
(def haitari-leveys 5)
(def id-leveys 6)
(def tunnus-leveys 6)
(def kvl-leveys 5)
(def yllapitoluokka-leveys 7)
(def tr-leveys 8)
(def kohde-leveys (* tr-leveys 2))
(def tarjoushinta-leveys 10)
(def maaramuutokset-leveys 10)
(def toteutunut-hinta-leveys 20)
(def arvonvahennykset-leveys 10)
(def muut-leveys 10)
(def bitumi-indeksi-leveys 10)
(def kaasuindeksi-leveys 10)
(def yhteensa-leveys 10)

;; Ylläpitokohdeosien sarakkeiden leveydet
(def nimi-leveys 20)
(def paallyste-leveys 10)
(def raekoko-leveys 5)
(def tyomenetelma-leveys 10)
(def massamaara-leveys 5)
(def toimenpide-leveys 10)

(defn tierekisteriosoite-sarakkeet
  "Perusleveys on leveys, jota kentille käytetään, ellei määritetä kenttäkohtaista leveyttä.
   Nimi-kenttä on kaksi kertaa perusleveys, ellei määritetä kenttäkohtaista leveyttä."
  ([perusleveys skeemaosat] (tierekisteriosoite-sarakkeet perusleveys skeemaosat false))
  ([perusleveys skeemaosat vain-nama-validoinnit?] (tierekisteriosoite-sarakkeet perusleveys skeemaosat vain-nama-validoinnit?
                                                                                 [(constantly true) (constantly false) #(pot/arvo-koodilla pot/+ajoradat-numerona+ %)
                                                                                  #(pot/arvo-koodilla pot/+kaistat+ %) (fn [arvo muokattava?]
                                                                                                                         (if arvo (:nimi arvo) (if muokattava?
                                                                                                                                                 "- Ajorata -"
                                                                                                                                                 "")))
                                                                                  (fn [arvo muokattava?]
                                                                                    (if arvo (:nimi arvo) (if muokattava?
                                                                                                            "- Kaista -"
                                                                                                            "")))]))
  ([perusleveys
    [nimi tie ajorata kaista aosa aet losa let pituus]
    vain-nama-validoinnit?
    [true-fn false-fn ajorata-fmt-fn kaista-fmt-fn ajorata-valinta-nayta-fn kaista-valinta-nayta-fn]]
   (into []
         (remove
           nil?
           [(when nimi
              {:otsikko "Nimi" :nimi (:nimi nimi) :tyyppi :string
               :leveys (or (:leveys nimi) (* perusleveys 2))
               :pituus-max 30
               :muokattava? (or (:muokattava? nimi) true-fn)})
            {:otsikko "Tie\u00ADnu\u00ADme\u00ADro" :nimi (:nimi tie)
             :tyyppi :positiivinen-numero :leveys perusleveys :tasaa :oikea
             :validoi (if vain-nama-validoinnit?
                        (:validoi tie)
                        (into [[:ei-tyhja "Anna tienumero"]] (:validoi tie)))
             :kokonaisluku? true
             :muokattava? (or (:muokattava? tie) true-fn)}
            (when ajorata
              {:otsikko "Ajo\u00ADrata"
               :nimi (:nimi ajorata)
               :muokattava? (or (:muokattava? ajorata) true-fn)
               :kentta-arity-3? (:arity-3? ajorata)
               :tyyppi :valinta
               :tasaa :oikea
               :valinta-arvo :koodi
               :fmt ajorata-fmt-fn
               :valinta-nayta ajorata-valinta-nayta-fn
               :valinnat pot/+ajoradat-numerona+
               :leveys perusleveys
               :validoi (:validoi ajorata)})
            (when kaista
              {:otsikko "Kais\u00ADta"
               :muokattava? (or (:muokattava? kaista) true-fn)
               :kentta-arity-3? (:arity-3? kaista)
               :nimi (:nimi kaista)
               :tyyppi :valinta
               :tasaa :oikea
               :valinta-arvo :koodi
               :fmt kaista-fmt-fn
               :valinta-nayta kaista-valinta-nayta-fn
               :valinnat pot/+kaistat+
               :leveys perusleveys
               :validoi (:validoi kaista)})
            {:otsikko "Aosa" :nimi (:nimi aosa) :leveys perusleveys :tyyppi :positiivinen-numero
             :tasaa :oikea :kokonaisluku? true
             :validoi (if vain-nama-validoinnit?
                        (:validoi aosa)
                        (into [[:ei-tyhja "An\u00ADna al\u00ADku\u00ADo\u00ADsa"]
                               (fn [tr-alkuosa rivi]
                                 (when-not (yllapitokohteet-domain/losa>aosa? (assoc rivi :tr-alkuosa tr-alkuosa))
                                   (-> yllapitokohteet-domain/muoto-virhetekstit :tr-alkuosa :vaarin-pain)))]
                              (:validoi aosa)))
             :salli-muokkaus-rivin-ollessa-disabloituna? (:salli-muokkaus-rivin-ollessa-disabloituna? aosa)
             :muokattava? (or (:muokattava? aosa) true-fn)}
            {:otsikko "Aet" :nimi (:nimi aet) :leveys perusleveys :tyyppi :positiivinen-numero
             :tasaa :oikea :kokonaisluku? true
             :validoi (if vain-nama-validoinnit?
                        (:validoi aet)
                        (into [[:ei-tyhja "An\u00ADna al\u00ADku\u00ADe\u00ADtäi\u00ADsyys"]
                               (fn [tr-alkuetaisyys rivi]
                                 (when-not (and (or (yllapitokohteet-domain/let>aet? (assoc rivi :tr-alkuetaisyys tr-alkuetaisyys))
                                                    (yllapitokohteet-domain/let=aet? (assoc rivi :tr-alkuetaisyys tr-alkuetaisyys)))
                                                (yllapitokohteet-domain/losa=aosa? rivi))
                                   (-> yllapitokohteet-domain/muoto-virhetekstit :tr-alkuetaisyys :vaarin-pain)))]
                              (:validoi aet)))
             :salli-muokkaus-rivin-ollessa-disabloituna? (:salli-muokkaus-rivin-ollessa-disabloituna? aet)
             :muokattava? (or (:muokattava? aet) true-fn)}
            {:otsikko "Losa" :nimi (:nimi losa) :leveys perusleveys :tyyppi :positiivinen-numero
             :tasaa :oikea :kokonaisluku? true
             :validoi (if vain-nama-validoinnit?
                        (:validoi losa)
                        (into [[:ei-tyhja "An\u00ADna lop\u00ADpu\u00ADo\u00ADsa"]
                               (fn [tr-loppuosa rivi]
                                 (when-not (yllapitokohteet-domain/losa>aosa? (assoc rivi :tr-loppuosa tr-loppuosa))
                                   (-> yllapitokohteet-domain/muoto-virhetekstit :tr-loppuosa :vaarin-pain)))]
                              (:validoi losa)))
             :salli-muokkaus-rivin-ollessa-disabloituna? (:salli-muokkaus-rivin-ollessa-disabloituna? losa)
             :muokattava? (or (:muokattava? losa) true-fn)}
            {:otsikko "Let" :nimi (:nimi let) :leveys perusleveys :tyyppi :positiivinen-numero
             :tasaa :oikea :kokonaisluku? true
             :validoi (if vain-nama-validoinnit?
                        (:validoi let)
                        (into [[:ei-tyhja "An\u00ADna lop\u00ADpu\u00ADe\u00ADtäi\u00ADsyys"]
                               (fn [tr-loppuetaisyys rivi]
                                 (when-not (and (or (yllapitokohteet-domain/let>aet? (assoc rivi :tr-loppuetaisyys tr-loppuetaisyys))
                                                    (yllapitokohteet-domain/let=aet? (assoc rivi :tr-loppuetaisyys tr-loppuetaisyys)))
                                                (yllapitokohteet-domain/losa=aosa? rivi))
                                   (-> yllapitokohteet-domain/muoto-virhetekstit :tr-loppuetaisyys :vaarin-pain)))]
                              (:validoi let)))
             :salli-muokkaus-rivin-ollessa-disabloituna? (:salli-muokkaus-rivin-ollessa-disabloituna? let)
             :muokattava? (or (:muokattava? let) true-fn)}
            (merge
              {:otsikko "Pit. (m)" :nimi :pituus :leveys perusleveys :tyyppi :numero :tasaa :oikea
               :muokattava? false-fn}
              pituus)]))))

(defn tr-osoite [rivi]
  (let [arvot (map rivi [:tr-numero :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys])]
    (when (every? #(not (str/blank? %)) arvot)
      ;; Tierekisteriosoite on täytetty (ei tyhjiä kenttiä)
      (zipmap [:numero :alkuosa :alkuetaisyys :loppuosa :loppuetaisyys]
              arvot))))

(defn varmista-alku-ja-loppu [kohdeosat {tie :tr-numero aosa :tr-alkuosa losa :tr-loppuosa
                                         alkuet :tr-alkuetaisyys loppuet :tr-loppuetaisyys
                                         :as kohde}]
  (let [avaimet (sort (keys kohdeosat))
        ensimmainen (first avaimet)
        viimeinen (last avaimet)]
    (as-> kohdeosat ko
          (if (not= (tr/alku kohde) (tr/alku (get kohdeosat ensimmainen)))
            (update-in ko [ensimmainen] merge {:tr-alkuosa aosa :tr-alkuetaisyys alkuet})
            ko)
          (if (not= (tr/loppu kohde) (tr/loppu (get kohdeosat viimeinen)))
            (update-in ko [viimeinen] merge {:tr-loppuosa losa :tr-loppuetaisyys loppuet})
            ko))))

(defn lisaa-virhe-riville
  [{:keys [virheet-atom virhe]}]
  (let [paivitettavat-kentat #{:tr-numero :tr-ajorata :tr-kaista :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys}
        virherivin-paivitys (fn [rivin-virheet]
                              (apply assoc rivin-virheet (mapcat #(identity
                                                                    [% (conj (get rivin-virheet %)
                                                                             (:viesti virhe))])
                                                                 paivitettavat-kentat)))]
    (swap! virheet-atom update (:rivi virhe) virherivin-paivitys)))

(defn gridin-tila [kohdeosat-tila]
  (keep (fn [[indeksi kohdeosa]]
          (when (and (:tr-alkuosa kohdeosa) (:tr-alkuetaisyys kohdeosa)
                     (:tr-loppuosa kohdeosa) (:tr-loppuetaisyys kohdeosa))
            [indeksi (assoc kohdeosa :valiaikainen-id indeksi)]))
        kohdeosat-tila))

(defn gridin-paallekkaiset-osat [gridin-tila paallekkaiset-osat]
  (flatten (keep (fn [[avain rivi]]
                   (let [rivin-paallekkaiset-osat
                         (keep #(let [{id-1 :valiaikainen-id nimi-1 :nimi} (-> % :kohteet first)
                                      {id-2 :valiaikainen-id nimi-2 :nimi} (-> % :kohteet second)
                                      rivin-id (:valiaikainen-id rivi)]
                                  (when (or (= id-1 rivin-id) (= id-2 rivin-id))
                                    {:rivi avain
                                     :viesti (str "Kohteenosa on päällekkäin "
                                                  (cond
                                                    (= rivin-id id-1) (if (empty? nimi-2) "toisen osan" (str "osan " nimi-2))
                                                    (= rivin-id id-2) (if (empty? nimi-1) "toisen osan" (str "osan " nimi-1))
                                                    :else nil)
                                                  " kanssa")}))
                               paallekkaiset-osat)]
                     (if (empty? rivin-paallekkaiset-osat)
                       nil rivin-paallekkaiset-osat)))
                 gridin-tila)))

(defn virheet-ilman-paallekkaisyysvirheita [virheet]
  (apply merge
         (map (fn [rivi-id]
                (let [kenttien-virheet (get virheet rivi-id)
                      kenttien-avaimet (keys kenttien-virheet)
                      paallekkaisyysvirhe? #(not (string/includes? % "Kohteenosa on päällekkäin"))]
                  {rivi-id (apply merge (map (fn [kentan-avain]
                                               {kentan-avain (filter paallekkaisyysvirhe? (get kenttien-virheet kentan-avain))})
                                             kenttien-avaimet))}))
              (keys virheet))))

(defn validoi-kohdeosien-paallekkyys [kohdeosat-tila virheet-atom]
  (let [gridin-tila (gridin-tila kohdeosat-tila)
        gridin-virheet @virheet-atom
        paallekkaiset-osat (tr/kohdeosat-keskenaan-paallekkain (map second gridin-tila) :valiaikainen-id)
        gridin-paallekkaiset-osat (gridin-paallekkaiset-osat gridin-tila paallekkaiset-osat)
        virheet (virheet-ilman-paallekkaisyysvirheita gridin-virheet)]
    (reset! virheet-atom virheet)
    (doseq [paallekkainen-osa gridin-paallekkaiset-osat]
      (lisaa-virhe-riville {:virheet-atom virheet-atom :virhe paallekkainen-osa}))))

(defn validoi-tr-osoite [grid tr-sijainnit-atom tr-virheet-atom]
  (let [haetut (into #{} (keys @tr-sijainnit-atom))]
    ;; jos on tullut uusi TR osoite, haetaan sille sijainti
    (doseq [[id rivi] (grid/hae-muokkaustila grid)]
      (if (:poistettu rivi)
        (swap! tr-virheet-atom dissoc id)
        (let [osoite (tr-osoite rivi)
              virheet (grid/hae-virheet grid)]
          (when (and osoite (not (haetut osoite))
                     (empty? (get virheet id)))
            (go
              (log "Haetaan TR osoitteen sijainti: " (pr-str osoite))
              (let [sijainti (<! (vkm/tieosoite->viiva osoite))]
                (when (= (get (grid/hae-muokkaustila grid) id) rivi) ;; ettei rivi ole uudestaan muuttunut
                  (if-let [virhe (when-not (vkm/loytyi? sijainti)
                                   "Virheellinen TR-osoite")]
                    (do (swap! tr-virheet-atom assoc id virhe)
                        (doseq [kentta [:tr-numero :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys]]
                          (grid/aseta-virhe! grid id kentta "Tarkista tie")))
                    (do (swap! tr-virheet-atom dissoc id)
                        (doseq [kentta [:tr-numero :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys]]
                          (grid/poista-virhe! grid id kentta))
                        (log "sain sijainnin " (clj->js sijainti))
                        (swap! tr-sijainnit-atom assoc osoite sijainti))))))))))))

(defn yllapitokohdeosat-sarakkeet
  ([asetukset] (yllapitokohdeosat-sarakkeet asetukset
                                            [(fn tayta-alas?-fn [jotain]
                                               (not (nil? jotain)))
                                             (fn tayta-paallyste-fn [lahtorivi tama-rivi]
                                               (assoc tama-rivi :paallystetyyppi (:paallystetyyppi lahtorivi)))
                                             (fn tayta-paallyste-toistuvasti-fn [toistettava-rivi tama-rivi]
                                               (assoc tama-rivi :paallystetyyppi (:paallystetyyppi toistettava-rivi)))
                                             (fn paallyste-valinta-nayta-fn [rivi]
                                               (if (:koodi rivi)
                                                 (str (:lyhenne rivi) " - " (:nimi rivi))
                                                 (:nimi rivi)))
                                             (fn tayta-raekoko-fn [lahtorivi tama-rivi]
                                               (assoc tama-rivi :raekoko (:raekoko lahtorivi)))
                                             (fn tayta-raekoko-toistuvasti-fn [toistettava-rivi tama-rivi]
                                               (assoc tama-rivi :raekoko (:raekoko toistettava-rivi)))
                                             (fn tayta-tyomenetelma-fn [lahtorivi tama-rivi]
                                               (assoc tama-rivi :tyomenetelma (:tyomenetelma lahtorivi)))
                                             (fn tayta-tyomenetelma-toistuvasti-fn [toistettava-rivi tama-rivi]
                                               (assoc tama-rivi :tyomenetelma (:tyomenetelma toistettava-rivi)))
                                             (fn tyomenetelma-valinta-nayta-fn [rivi]
                                               (if (:koodi rivi)
                                                 (str (:lyhenne rivi) " - " (:nimi rivi))
                                                 (:nimi rivi)))
                                             (fn tayta-massamaara-fn [lahtorivi tama-rivi]
                                               (assoc tama-rivi :massamaara (:massamaara lahtorivi)))
                                             (fn tayta-massamaara-toistuvasti-fn [toistettava-rivi tama-rivi]
                                               (assoc tama-rivi :massamaara (:massamaara toistettava-rivi)))
                                             (fn tayta-toimenpide-fn [lahtorivi tama-rivi]
                                               (assoc tama-rivi :toimenpide (:toimenpide lahtorivi)))
                                             (fn tayta-toimenpide-toistuvasti-fn [toistettava-rivi tama-rivi]
                                               (assoc tama-rivi :toimenpide (:toimenpide toistettava-rivi)))]))
  ([{:keys [muokattava-tie? muokattava-ajorata-ja-kaista? validoi vain-nama-validoinnit? hae-fn tr-sarake-fn valinta-arity-3?]}
    [tayta-alas?-fn tayta-paallyste-fn tayta-paallyste-toistuvasti-fn paallyste-valinta-nayta-fn tayta-raekoko-fn
     tayta-raekoko-toistuvasti-fn tayta-tyomenetelma-fn tayta-tyomenetelma-toistuvasti-fn tyomenetelma-valinta-nayta-fn
     tayta-massamaara-fn tayta-massamaara-toistuvasti-fn tayta-toimenpide-fn tayta-toimenpide-toistuvasti-fn]]
   (let [tr-sarakkeet-asetukset [{:nimi :nimi :pituus-max 30 :leveys kohde-leveys}
                                 {:nimi :tr-numero :muokattava? muokattava-tie?
                                  :validoi (:tr-numero validoi)}
                                 {:nimi :tr-ajorata :muokattava? muokattava-ajorata-ja-kaista?
                                  :validoi (:tr-ajorata validoi) :arity-3? valinta-arity-3?}
                                 {:nimi :tr-kaista :muokattava? muokattava-ajorata-ja-kaista?
                                  :validoi (:tr-kaista validoi) :arity-3? valinta-arity-3?}
                                 {:nimi :tr-alkuosa :validoi (:tr-alkuosa validoi)}
                                 {:nimi :tr-alkuetaisyys :validoi (:tr-alkuetaisyys validoi)}
                                 {:nimi :tr-loppuosa :validoi (:tr-loppuosa validoi)}
                                 {:nimi :tr-loppuetaisyys :validoi (:tr-loppuetaisyys validoi)}
                                 {:hae hae-fn}]]
     (vec (remove nil?
                  (concat
                    (if tr-sarake-fn
                      (tierekisteriosoite-sarakkeet
                        tr-leveys
                        tr-sarakkeet-asetukset
                        vain-nama-validoinnit?
                        tr-sarake-fn)
                      (tierekisteriosoite-sarakkeet
                        tr-leveys
                        tr-sarakkeet-asetukset
                        vain-nama-validoinnit?))
                    [(assoc paallystys-tiedot/paallyste-grid-skeema
                            :fokus-klikin-jalkeen? true
                            :leveys paallyste-leveys
                            :tayta-alas? tayta-alas?-fn
                            :tayta-fn tayta-paallyste-fn
                            :tayta-sijainti :ylos
                            :tayta-tooltip "Kopioi sama päällystetyyppi alla oleville riveille"
                            :tayta-alas-toistuvasti? tayta-alas?-fn
                            :tayta-toistuvasti-fn tayta-paallyste-toistuvasti-fn
                            :valinta-nayta paallyste-valinta-nayta-fn
                            :kentta-arity-3? valinta-arity-3?)
                     (assoc paallystys-tiedot/raekoko-grid-skeema
                            :leveys raekoko-leveys
                            :tayta-alas? tayta-alas?-fn
                            :tayta-fn tayta-raekoko-fn
                            :tayta-sijainti :ylos
                            :tayta-tooltip "Kopioi sama raekoko alla oleville riveille"
                            :tayta-alas-toistuvasti? tayta-alas?-fn
                            :tayta-toistuvasti-fn tayta-raekoko-toistuvasti-fn)
                     (assoc paallystys-tiedot/tyomenetelma-grid-skeema
                            :fokus-klikin-jalkeen? true
                            :leveys tyomenetelma-leveys
                            :tayta-alas? tayta-alas?-fn
                            :tayta-fn tayta-tyomenetelma-fn
                            :tayta-sijainti :ylos
                            :tayta-tooltip "Kopioi sama työmenetelmä alla oleville riveille"
                            :tayta-alas-toistuvasti? tayta-alas?-fn
                            :tayta-toistuvasti-fn tayta-tyomenetelma-toistuvasti-fn
                            :valinta-nayta tyomenetelma-valinta-nayta-fn
                            :kentta-arity-3? valinta-arity-3?)
                     {:otsikko "Massa\u00ADmäärä (kg/m²)" :nimi :massamaara
                      :tyyppi :positiivinen-numero :tasaa :oikea :leveys massamaara-leveys
                      :tayta-alas? tayta-alas?-fn
                      :tayta-fn tayta-massamaara-fn
                      :tayta-sijainti :ylos
                      :tayta-tooltip "Kopioi sama massamäärä alla oleville riveille"
                      :tayta-alas-toistuvasti? tayta-alas?-fn
                      :tayta-toistuvasti-fn tayta-massamaara-toistuvasti-fn}
                     {:otsikko "Toimenpiteen selitys" :nimi :toimenpide :tyyppi :string
                      :leveys toimenpide-leveys
                      :tayta-alas? tayta-alas?-fn
                      :tayta-fn tayta-toimenpide-fn
                      :tayta-sijainti :ylos
                      :tayta-tooltip "Kopioi sama selitys alla oleville riveille"
                      :tayta-alas-toistuvasti? tayta-alas?-fn
                      :tayta-toistuvasti-fn tayta-toimenpide-toistuvasti-fn}]))))))

(defn hae-osan-pituudet [grid-state osan-pituudet-teille-atom]
  (let [tiet (into #{} (map (comp :tr-numero second)) grid-state)]
    (doseq [tie tiet :when (not (contains? @osan-pituudet-teille-atom tie))]
      (go
        (let [pituudet (<! (vkm/tieosien-pituudet tie))]
          (log "Haettu osat tielle " tie ", vastaus: " (pr-str pituudet))
          (swap! osan-pituudet-teille-atom assoc tie pituudet))))))


(defn indeksoi-kohdeosat [kohdeosat]
  (into (sorted-map) (map (fn [[avain kohdeosa]] [avain kohdeosa]) (zipmap (iterate inc 1) kohdeosat))))

(defn kohdeosien-tallennusvirheet [virheet]
  [:div
   [:p "Virheet kohdeosien tiedoissa:"]
   (into [:ul] (mapv (fn [virhe]
                       [:li virhe])
                     virheet))])

(defn kohdeosat-tallennettu-onnistuneesti [kohdeosat-atom tallennettu-fn vastaus]
  (let [yllapitokohteet (vals @kohdeosat-atom)
        virheet (:validointivirheet vastaus)]
    (if (empty? virheet)
      (do (viesti/nayta! "Kohdeosat tallennettu!" :success)
          (tallennettu-fn vastaus)
          (reset! kohdeosat-atom
                  (indeksoi-kohdeosat (yllapitokohteet-domain/jarjesta-yllapitokohteet yllapitokohteet))))
      (do (viesti/nayta! "Kohdeosien tallennuksessa virheitä!" :danger)
          (modal/nayta!
            {:otsikko "Kohdeosien tallennus epäonnistui!"
             :otsikko-tyyli :virhe}
            (kohdeosien-tallennusvirheet virheet))))))

(defn yllapitokohdeosat-tuck [_ urakka
                              {:keys [ohjauskahvan-asetus hae-tr-osien-pituudet kohdeosat jarjesta-kun-kasketaan] :as asetukset}]
  (let [g (grid/grid-ohjaus)
        tiet-joilla-ei-pituutta (fn [tr-osien-pituudet grid-state]
                                  (let [tiet (into #{}
                                                   (map (comp :tr-numero second))
                                                   grid-state)]
                                    (filter #(not (contains? tr-osien-pituudet %))
                                            tiet)))
        luomisen-jalkeen-fn (fn [grid-state]
                              (let [tr-osien-pituudet (-> @paallystys-tiedot/tila :paallystysilmoitus-lomakedata :tr-osien-pituudet)]
                                (doseq [tie (tiet-joilla-ei-pituutta tr-osien-pituudet grid-state)]
                                  (hae-tr-osien-pituudet tie))))
        kohdeosat-muokkaa! (fn [uudet-kohdeosat-fn]
                             (let [vanhat-kohdeosat @kohdeosat
                                   uudet-kohdeosat (uudet-kohdeosat-fn vanhat-kohdeosat)
                                   uudet-kohdeosat (if jarjesta-kun-kasketaan
                                                     (vary-meta uudet-kohdeosat assoc :jarjesta-gridissa true)
                                                     uudet-kohdeosat)]
                               (swap! kohdeosat (fn [_]
                                                  uudet-kohdeosat))
                               (grid/validoi-grid g)))
        muutos-fn (fn [grid]
                    (let [tr-osien-pituudet (-> @paallystys-tiedot/tila :paallystysilmoitus-lomakedata :tr-osien-pituudet)]
                      (doseq [tie (tiet-joilla-ei-pituutta tr-osien-pituudet (grid/hae-muokkaustila grid))]
                        (hae-tr-osien-pituudet tie))))
        uusi-rivi-fn (fn [rivi]
                       (let [yllapitokohde (-> @paallystys-tiedot/tila :paallystysilmoitus-lomakedata :perustiedot (select-keys [:tr-numero :tr-kaista :tr-ajorata :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys]))]
                         ;; Otetaan pääkohteen tie, ajorata ja kaista, jos on
                         (assoc rivi
                                :tr-numero (:tr-numero yllapitokohde)
                                :tr-ajorata (:tr-ajorata yllapitokohde)
                                :tr-kaista (:tr-kaista yllapitokohde))))
        hae-fn (fn [rivi]
                 (let [tr-osien-pituudet (-> @paallystys-tiedot/tila :paallystysilmoitus-lomakedata :tr-osien-pituudet)]
                   (tr/laske-tien-pituus (into {}
                                               (map (juxt key (comp :pituus val)))
                                               (get tr-osien-pituudet (:tr-numero rivi)))
                                         rivi)))
        pituus (fn [osan-pituus tieosa]
                 (tr/laske-tien-pituus (into {}
                                             (map (juxt key (comp :pituus val)))
                                             osan-pituus)
                                       tieosa))
        kirjoitusoikeus?
        (case (:tyyppi urakka)
          :paallystys
          (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystyskohteet (:id urakka))
          :paikkaus
          (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paikkauskohteet (:id urakka))
          false)
        tyhja-kohdeosa (fn [voi-muokata?]
                         (let [voi-muokata?-fn (fn [_]
                                                 (let [yllapitokohde (-> @paallystys-tiedot/tila :paallystysilmoitus-lomakedata :perustiedot (select-keys [:tr-numero :tr-kaista :tr-ajorata :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys]))]
                                                   (kohdeosat-muokkaa!
                                                     (fn [vanhat-kohdeosat]
                                                       (tiedot/lisaa-uusi-kohdeosa vanhat-kohdeosat 1 yllapitokohde)))))]
                           (fn [voi-muokata?]
                             (if (nil? @kohdeosat)
                               [ajax-loader "Haetaan kohdeosia..."]
                               [:div
                                [:div {:style {:display "inline-block"}} "Ei kohdeosia"]
                                (when (and kirjoitusoikeus? voi-muokata?)
                                  [:div {:style {:display "inline-block"
                                                 :float "right"}}
                                   [napit/yleinen-ensisijainen "Lisää osa"
                                    voi-muokata?-fn
                                    {:ikoni (ikonit/livicon-arrow-down)
                                     :luokka "btn-xs"}]])]))))
        muokkaa-footer (fn [g]
                         (let [{{:keys [perustiedot tr-osien-pituudet]} :paallystysilmoitus-lomakedata} @paallystys-tiedot/tila
                               yllapitokohde (select-keys perustiedot [:tr-numero :tr-kaista :tr-ajorata :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys])]
                           [:span#kohdeosien-pituus-yht
                            "Tierekisterikohteiden pituus yhteensä: "
                            (if (not (empty? tr-osien-pituudet))
                              (fmt/pituus (reduce + 0 (keep (fn [kohdeosa]
                                                              (pituus (get tr-osien-pituudet (:tr-numero kohdeosa)) kohdeosa))
                                                            (vals (grid/hae-muokkaustila g)))))
                              "-")
                            (when (= (:yllapitokohdetyyppi yllapitokohde) :sora)
                              [:p (ikonit/ikoni-ja-teksti (ikonit/livicon-info-sign) " Soratiekohteilla voi olla vain yksi alikohde")])]))
        toiminnot-komponentti (fn [rivi osa voi-muokata?]
                                (let [lisaa-osa-fn (fn [index]
                                                      (kohdeosat-muokkaa! (fn [vanhat-kohdeosat]
                                                                            (tiedot/lisaa-uusi-kohdeosa vanhat-kohdeosat (inc index) {}))))
                                      poista-osa-fn (fn [index]
                                                      (kohdeosat-muokkaa! (fn [vanhat-kohdeosat]
                                                                            (tiedot/poista-kohdeosa vanhat-kohdeosat (inc index)))))]
                                  (fn [rivi {:keys [index]} voi-muokata?]
                                    (let [yllapitokohde (-> @paallystys-tiedot/tila :paallystysilmoitus-lomakedata :perustiedot (select-keys [:tr-numero :tr-kaista :tr-ajorata :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys]))]
                                      [:div.tasaa-oikealle
                                       [napit/yleinen-ensisijainen "Lisää osa"
                                        lisaa-osa-fn
                                        {:ikoni (ikonit/livicon-arrow-down)
                                         :disabled (or (not kirjoitusoikeus?)
                                                       (not voi-muokata?)
                                                       (= (:yllapitokohdetyyppi yllapitokohde) :sora))
                                         :luokka "btn-xs"
                                         :toiminto-args [index]}]
                                       [napit/kielteinen "Poista"
                                        poista-osa-fn
                                        {:ikoni (ikonit/livicon-trash)
                                         :disabled (or (not kirjoitusoikeus?)
                                                       (not voi-muokata?)
                                                       (= (:yllapitokohdetyyppi yllapitokohde) :sora))
                                         :luokka "btn-xs"
                                         :toiminto-args [index]}]]))))
        ;; Nämä funktiot annetaan skeemaan. Skeemassa itsessään ei kannata määritellä funktiota, koska se aiheuttaa komponentin uudelleen renderöitymisen, kun jokin
        ;; muu osa muuttuu.
        [true-fn false-fn ajorata-fmt-fn kaista-fmt-fn ajorata-valinta-nayta-fn kaista-valinta-nayta-fn] [(constantly true) (constantly false) #(pot/arvo-koodilla pot/+ajoradat-numerona+ %)
                                                                                                          #(pot/arvo-koodilla pot/+kaistat+ %) (fn [arvo muokattava?]
                                                                                                                                                 (if arvo (:nimi arvo) (if muokattava?
                                                                                                                                                                         "- Ajorata -"
                                                                                                                                                                         "")))
                                                                                                          (fn [arvo muokattava?]
                                                                                                            (if arvo (:nimi arvo) (if muokattava?
                                                                                                                                    "- Kaista -"
                                                                                                                                    "")))]
        [tayta-alas?-fn tayta-paallyste-fn tayta-paallyste-toistuvasti-fn paallyste-valinta-nayta-fn tayta-raekoko-fn
         tayta-raekoko-toistuvasti-fn tayta-tyomenetelma-fn tayta-tyomenetelma-toistuvasti-fn tyomenetelma-valinta-nayta-fn
         tayta-massamaara-fn tayta-massamaara-toistuvasti-fn tayta-toimenpide-fn tayta-toimenpide-toistuvasti-fn] [(fn tayta-alas?-fn [jotain]
                                                                                                                     (not (nil? jotain)))
                                                                                                                   (fn tayta-paallyste-fn [lahtorivi tama-rivi]
                                                                                                                     (assoc tama-rivi :paallystetyyppi (:paallystetyyppi lahtorivi)))
                                                                                                                   (fn tayta-paallyste-toistuvasti-fn [toistettava-rivi tama-rivi]
                                                                                                                     (assoc tama-rivi :paallystetyyppi (:paallystetyyppi toistettava-rivi)))
                                                                                                                   (fn paallyste-valinta-nayta-fn [rivi]
                                                                                                                     (if (:koodi rivi)
                                                                                                                       (str (:lyhenne rivi) " - " (:nimi rivi))
                                                                                                                       (:nimi rivi)))
                                                                                                                   (fn tayta-raekoko-fn [lahtorivi tama-rivi]
                                                                                                                     (assoc tama-rivi :raekoko (:raekoko lahtorivi)))
                                                                                                                   (fn tayta-raekoko-toistuvasti-fn [toistettava-rivi tama-rivi]
                                                                                                                     (assoc tama-rivi :raekoko (:raekoko toistettava-rivi)))
                                                                                                                   (fn tayta-tyomenetelma-fn [lahtorivi tama-rivi]
                                                                                                                     (assoc tama-rivi :tyomenetelma (:tyomenetelma lahtorivi)))
                                                                                                                   (fn tayta-tyomenetelma-toistuvasti-fn [toistettava-rivi tama-rivi]
                                                                                                                     (assoc tama-rivi :tyomenetelma (:tyomenetelma toistettava-rivi)))
                                                                                                                   (fn tyomenetelma-valinta-nayta-fn [rivi]
                                                                                                                     (if (:koodi rivi)
                                                                                                                       (str (:lyhenne rivi) " - " (:nimi rivi))
                                                                                                                       (:nimi rivi)))
                                                                                                                   (fn tayta-massamaara-fn [lahtorivi tama-rivi]
                                                                                                                     (assoc tama-rivi :massamaara (:massamaara lahtorivi)))
                                                                                                                   (fn tayta-massamaara-toistuvasti-fn [toistettava-rivi tama-rivi]
                                                                                                                     (assoc tama-rivi :massamaara (:massamaara toistettava-rivi)))
                                                                                                                   (fn tayta-toimenpide-fn [lahtorivi tama-rivi]
                                                                                                                     (assoc tama-rivi :toimenpide (:toimenpide lahtorivi)))
                                                                                                                   (fn tayta-toimenpide-toistuvasti-fn [toistettava-rivi tama-rivi]
                                                                                                                     (assoc tama-rivi :toimenpide (:toimenpide toistettava-rivi)))]]
    (when ohjauskahvan-asetus
      (ohjauskahvan-asetus g))
    (fn [{{:keys [:tr-numero :tr-kaista :tr-ajorata :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys] :as perustiedot} :perustiedot
          tr-osien-pituudet :tr-osien-pituudet}
         urakka
         {:keys [rivinumerot? voi-muokata? validoinnit vain-nama-validoinnit? hae-tr-osien-pituudet muokattava-ajorata-ja-kaista?
                 muokattava-tie? kohdeosat kohdeosat-virheet virheviesti otsikko jarjesta-avaimen-mukaan jarjesta-kun-kasketaan]}]
      (let [rivinumerot? (if (some? rivinumerot?) rivinumerot? false)
            voi-muokata? (if (some? voi-muokata?) voi-muokata? true)
            skeema (yllapitokohdeosat-sarakkeet {:muokattava-ajorata-ja-kaista? muokattava-ajorata-ja-kaista?
                                                 :muokattava-tie? muokattava-tie?
                                                 :validoi (:tr-osoitteet validoinnit)
                                                 :vain-nama-validoinnit? vain-nama-validoinnit?
                                                 :hae-fn hae-fn
                                                 :valinta-arity-3? true
                                                 :tr-sarake-fn [true-fn false-fn ajorata-fmt-fn kaista-fmt-fn ajorata-valinta-nayta-fn kaista-valinta-nayta-fn]}
                                                [tayta-alas?-fn tayta-paallyste-fn tayta-paallyste-toistuvasti-fn paallyste-valinta-nayta-fn tayta-raekoko-fn
                                                 tayta-raekoko-toistuvasti-fn tayta-tyomenetelma-fn tayta-tyomenetelma-toistuvasti-fn tyomenetelma-valinta-nayta-fn
                                                 tayta-massamaara-fn tayta-massamaara-toistuvasti-fn tayta-toimenpide-fn tayta-toimenpide-toistuvasti-fn])]
        [grid/muokkaus-grid
         {:tyhja tyhja-kohdeosa
          :tyhja-args [voi-muokata?]
          :validoi-alussa? true
          :tyhja-komponentti? true
          :ohjaus g
          :taulukko-validointi (:taulukko validoinnit)
          :rivi-validointi (:rivi validoinnit)
          :rivinumerot? rivinumerot?
          :voi-muokata? (and kirjoitusoikeus? voi-muokata?)
          :virhe-viesti virheviesti
          :muutos muutos-fn
          :otsikko otsikko
          :id "yllapitokohdeosat"
          :data-cy (str "yllapitokohdeosat-" otsikko)
          :virheet kohdeosat-virheet
          :voi-lisata? false
          :jarjesta-avaimen-mukaan (when jarjesta-avaimen-mukaan jarjesta-avaimen-mukaan)
          :jarjesta-kun-kasketaan (when jarjesta-kun-kasketaan jarjesta-kun-kasketaan)
          :piilota-toiminnot? true
          :voi-kumota? false
          :uusi-rivi uusi-rivi-fn
          :luomisen-jalkeen luomisen-jalkeen-fn
          :muokkaa-footer muokkaa-footer}
         (conj skeema
               {:otsikko "Toiminnot" :nimi :tr-muokkaus :tyyppi :reagent-komponentti :leveys 20
                :tasaa :keskita :komponentti-args [voi-muokata?]
                :komponentti toiminnot-komponentti})
         kohdeosat]))))

(defn yllapitokohdeosat [{:keys [urakka virheet-atom validoinnit voi-muokata? virhe-viesti rivinumerot?]}]
  (let [g (grid/grid-ohjaus)
        rivinumerot? (if (some? rivinumerot?) rivinumerot? false)
        virheet (or virheet-atom (atom nil))
        voi-muokata? (if (some? voi-muokata?) voi-muokata? true)
        osan-pituudet-teille (atom nil)
        tien-osat-riville (fn [rivi]
                            (get @osan-pituudet-teille (:tr-numero rivi)))
        kirjoitusoikeus?
        (case (:tyyppi urakka)
          :paallystys
          (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystyskohteet (:id urakka))
          :paikkaus
          (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paikkauskohteet (:id urakka))
          false)
        hae-fn (fn [rivi]
                 ;; TODO vaihda tämä käyttämään paallystys-tiedot/tr-osien-tiedot dataa
                 (tr/laske-tien-pituus (tien-osat-riville rivi) rivi))
        pituus (fn [osan-pituus tieosa]
                 (tr/laske-tien-pituus osan-pituus tieosa))]
    (fn [{:keys [yllapitokohde otsikko kohdeosat-atom tallenna-fn tallennettu-fn
                 muokattava-tie? muokattava-ajorata-ja-kaista? jarjesta-avaimen-mukaan
                 jarjesta-kun-kasketaan esta-ainoan-osan-poisto? rivi-validointi taulukko-validointi]}]
      (let [skeema (yllapitokohdeosat-sarakkeet {:muokattava-ajorata-ja-kaista? muokattava-ajorata-ja-kaista?
                                                 :muokattava-tie? muokattava-tie?
                                                 :vain-nama-validoinnit? true
                                                 :hae-fn hae-fn})
            muokkaa-kohdeosat! (fn [uudet-osat]
                                 (let [uudet-kohdeosat (if jarjesta-kun-kasketaan
                                                         (vary-meta uudet-osat assoc :jarjesta-gridissa true)
                                                         uudet-osat)
                                       uudet-virheet (into {}
                                                           (keep (fn [[id rivi]]
                                                                   (let [rivin-virheet (validointi/validoi-rivin-kentat
                                                                                         uudet-kohdeosat rivi skeema)]
                                                                     (when-not (empty? rivin-virheet)
                                                                       [id rivin-virheet])))
                                                                 uudet-kohdeosat))]
                                   (reset! kohdeosat-atom uudet-kohdeosat)
                                   (reset! virheet uudet-virheet)))]
        [grid/muokkaus-grid
         {:tyhja (if (nil? @kohdeosat-atom) [ajax-loader "Haetaan kohdeosia..."]
                                            [:div
                                             [:div {:style {:display "inline-block"}} "Ei kohdeosia"]
                                             (when (and kirjoitusoikeus? voi-muokata?)
                                               [:div {:style {:display "inline-block"
                                                              :float "right"}}
                                                [napit/yleinen-ensisijainen "Lisää osa"
                                                 #(reset! kohdeosat-atom (tiedot/lisaa-uusi-kohdeosa @kohdeosat-atom 1 yllapitokohde))
                                                 {:ikoni (ikonit/livicon-arrow-down)
                                                  :luokka "btn-xs"}]])])
          :taulukko-validointi taulukko-validointi
          :ohjaus g
          :rivi-validointi rivi-validointi
          :rivinumerot? rivinumerot?
          :voi-muokata? (and kirjoitusoikeus? voi-muokata?)
          :virhe-viesti virhe-viesti
          :muutos (fn [grid]
                    (hae-osan-pituudet (grid/hae-muokkaustila grid) osan-pituudet-teille))
          :otsikko otsikko
          :id "yllapitokohdeosat"
          :data-cy (str "yllapitokohdeosat-" otsikko)
          :virheet virheet
          :voi-lisata? false
          :jarjesta-avaimen-mukaan (when jarjesta-avaimen-mukaan jarjesta-avaimen-mukaan)
          :jarjesta-kun-kasketaan (when jarjesta-kun-kasketaan jarjesta-kun-kasketaan)
          :piilota-toiminnot? true
          :voi-kumota? false
          :uusi-rivi (fn [rivi]
                       ;; Otetaan pääkohteen tie, ajorata ja kaista, jos on
                       (assoc rivi
                              :tr-numero (:tr-numero yllapitokohde)
                              :tr-ajorata (:tr-ajorata yllapitokohde)
                              :tr-kaista (:tr-kaista yllapitokohde)))
          :luomisen-jalkeen (fn [grid-state]
                              (hae-osan-pituudet grid-state osan-pituudet-teille))
          :paneelikomponentit
          [(fn []
             (when tallenna-fn
               [napit/palvelinkutsu-nappi
                [ikonit/ikoni-ja-teksti (ikonit/tallenna) "Tallenna"]
                #(tallenna-fn (vals @kohdeosat-atom))
                {:disabled (or
                             (not (empty? (apply concat (mapcat vals (vals @virheet)))))
                             (not (every? #(and (:tr-numero %)
                                                (:tr-alkuosa %)
                                                (:tr-alkuetaisyys %)
                                                (:tr-loppuosa %)
                                                (:tr-loppuetaisyys %))
                                          (vals @kohdeosat-atom)))
                             (not kirjoitusoikeus?)
                             (not voi-muokata?))
                 :luokka "nappi-myonteinen grid-tallenna"
                 :virheviesti "Tallentaminen epäonnistui."
                 :kun-onnistuu (partial kohdeosat-tallennettu-onnistuneesti kohdeosat-atom tallennettu-fn)}]))]
          :muokkaa-footer (fn [g]
                            [:span#kohdeosien-pituus-yht
                             "Tierekisterikohteiden pituus yhteensä: "
                             (if (not (empty? @osan-pituudet-teille))
                               (fmt/pituus (reduce + 0 (keep (fn [kohdeosa]
                                                               (pituus (get @osan-pituudet-teille (:tr-numero kohdeosa)) kohdeosa))
                                                             (vals (grid/hae-muokkaustila g)))))
                               "-")
                             (when (= (:yllapitokohdetyyppi yllapitokohde) :sora)
                               [:p (ikonit/ikoni-ja-teksti (ikonit/livicon-info-sign) " Soratiekohteilla voi olla vain yksi alikohde")])])}
         (conj skeema
               {:otsikko "Toiminnot" :nimi :tr-muokkaus :tyyppi :komponentti :leveys 20
                :tasaa :keskita
                :komponentti (fn [rivi {:keys [index]}]
                               [:div.tasaa-oikealle
                                [napit/yleinen-ensisijainen "Lisää osa"
                                 #(do
                                    (muokkaa-kohdeosat! (tiedot/lisaa-uusi-kohdeosa @kohdeosat-atom (inc index) {}))
                                    (grid/validoi-grid g))
                                 {:ikoni (ikonit/livicon-arrow-down)
                                  :disabled (or (not kirjoitusoikeus?)
                                                (not voi-muokata?)
                                                (= (:yllapitokohdetyyppi yllapitokohde) :sora))
                                  :data-attributes {:data-cy (str "lisaa-osa-" otsikko)}
                                  :luokka "btn-xs"}]
                                [napit/kielteinen "Poista"
                                 #(do
                                    (muokkaa-kohdeosat! (tiedot/poista-kohdeosa @kohdeosat-atom (inc index)))
                                    (grid/validoi-grid g))
                                 {:ikoni (ikonit/livicon-trash)
                                  :disabled (or (not kirjoitusoikeus?)
                                                (not voi-muokata?)
                                                (and esta-ainoan-osan-poisto?
                                                     (= (count @kohdeosat-atom) 1))
                                                (= (:yllapitokohdetyyppi yllapitokohde) :sora))
                                  :luokka "btn-xs"}]])})
         kohdeosat-atom]))))

(defn maaramuutokset [{:keys [yllapitokohde-id urakka-id yllapitokohteet-atom] :as tiedot}]
  (let [sopimus-id (first @u/valittu-sopimusnumero)
        vuosi @u/valittu-urakan-vuosi
        maaramuutokset (atom nil)
        voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystyskohteet urakka-id)
        hae-maara-muutokset! (fn [urakka-id yllapitokohde-id]
                               (go (let [vastaus (<! (tiedot/hae-maaramuutokset urakka-id yllapitokohde-id))]
                                     (if (k/virhe? vastaus)
                                       (viesti/nayta! "Määrämuutoksien haku epäonnistui"
                                                      :warning
                                                      viesti/viestin-nayttoaika-keskipitka)
                                       (reset! maaramuutokset vastaus)))))]
    (hae-maara-muutokset! urakka-id yllapitokohde-id)
    (fn [{:keys [yllapitokohde-id urakka-id] :as tiedot}]
      [:div
       [grid/grid
        {:otsikko "Määrämuutokset"
         :tyhja (if (nil? @maaramuutokset) [ajax-loader "Haetaan määrämuutoksia..."] "Ei määrämuutoksia")
         :tallenna (when voi-muokata?
                     #(go (let [vastaus (<! (tiedot/tallenna-maaramuutokset!
                                              {:urakka-id urakka-id
                                               :sopimus-id sopimus-id
                                               :vuosi vuosi
                                               :yllapitokohde-id yllapitokohde-id
                                               :maaramuutokset (filterv (comp not :jarjestelman-lisaama) %)}))]
                            (if (k/virhe? vastaus)
                              (viesti/nayta! "Määrämuutoksien tallennus epäonnistui"
                                             :warning
                                             viesti/viestin-nayttoaika-keskipitka)
                              (do
                                (reset! maaramuutokset (:maaramuutokset vastaus))
                                (reset! yllapitokohteet-atom (:yllapitokohteet vastaus)))))))
         :voi-muokata? voi-muokata?
         :esta-poistaminen? #(:jarjestelman-lisaama %)
         :esta-poistaminen-tooltip (fn [_] "Järjestelmän lisäämää kohdetta ei voi poistaa.")
         :voi-muokata-rivia? #(not (:jarjestelman-lisaama %))}
        [{:otsikko "Päällyste\u00ADtyön tyyppi"
          :nimi :tyyppi
          :tyyppi :valinta
          :valinta-arvo :koodi
          :fmt pot/paallystystyon-tyypin-nimi-koodilla
          :valinta-nayta #(if % (:nimi %) "- Valitse työ -")
          :valinnat pot/+paallystystyon-tyypit+
          :leveys "30%" :validoi [[:ei-tyhja "Valitse tyyppi"]]}
         {:otsikko "Työ" :nimi :tyo :tyyppi :string :leveys "30%" :pituus-max 256
          :validoi [[:ei-tyhja "Anna työ"]]}
         {:otsikko "Yks." :nimi :yksikko :tyyppi :string :leveys "10%" :pituus-max 20
          :validoi [[:ei-tyhja "Anna yksikkö"]]}
         {:otsikko "Tilattu määrä" :nimi :tilattu-maara :tyyppi :positiivinen-numero :tasaa :oikea
          :kokonaisosan-maara 6 :leveys "15%" :validoi [[:ei-tyhja "Anna tilattu määrä"]]}
         {:otsikko "Ennustettu määrä" :nimi :ennustettu-maara :tyyppi :positiivinen-numero :tasaa :oikea
          :kokonaisosan-maara 6 :leveys "15%"
          :validoi [[:ainakin-toinen-annettu [:ennustettu-maara :toteutunut-maara]
                     "Anna ennustettu tai toteutunut määrä"]]}
         {:otsikko "Toteu\u00ADtunut määrä" :nimi :toteutunut-maara :leveys "15%" :tasaa :oikea
          :tyyppi :positiivinen-numero
          :validoi [[:ainakin-toinen-annettu [:ennustettu-maara :toteutunut-maara]
                     "Anna ennustettu tai toteutunut määrä"]]}
         {:otsikko "Ero" :nimi :ero :leveys "15%" :tyyppi :numero :muokattava? (constantly false)
          :hae (fn [rivi] (fmt/desimaaliluku-opt (- (:toteutunut-maara rivi) (:tilattu-maara rivi))))}
         {:otsikko "Yks.\u00ADhinta" :nimi :yksikkohinta :leveys "10%" :tasaa :oikea :fmt fmt/euro-opt
          :tyyppi :positiivinen-numero :kokonaisosan-maara 6 :validoi [[:ei-tyhja "Anna yksikköhinta"]]}
         {:otsikko "Muutos hintaan" :nimi :muutos-hintaan :leveys "15%" :tasaa :oikea
          :muokattava? (constantly false) :tyyppi :komponentti
          :komponentti (fn [rivi]
                         (let [maaramuutoksen-lasku (paallystys-ja-paikkaus/summaa-maaramuutokset [rivi])]
                           [:span {:class (when (:ennustettu? maaramuutoksen-lasku)
                                            "grid-solu-ennustettu")}
                            (fmt/euro-opt (:tulos maaramuutoksen-lasku))]))}]
        @maaramuutokset]
       (when (some :jarjestelman-lisaama @maaramuutokset)
         [vihje "Ulkoisen järjestelmän kirjaamia määrämuutoksia ei voi muokata Harjassa."])])))

(defn kohteen-vetolaatikko [{:keys [urakka sopimus-id kohteet-atom rivi
                                    kohteen-rivi-validointi kohteen-taulukko-validointi
                                    muut-kohteen-rivi-validointi muut-kohteen-taulukko-validointi]}]
  (let [tallenna-fn (fn [osatyyppi]
                      (fn [rivit]
                        (tiedot/tallenna-yllapitokohdeosat!
                          {:urakka-id (:id urakka)
                           :sopimus-id sopimus-id
                           :vuosi @u/valittu-urakan-vuosi
                           :yllapitokohde-id (:id rivi)
                           :osat rivit
                           :osatyyppi osatyyppi})))
        tallennettu-fn (fn [vastaus]
                         (let [yllapitokohde-uusilla-kohdeosilla (assoc rivi :kohdeosat vastaus)]
                           (reset! kohteet-atom
                                   (map (fn [kohde]
                                          (if (= (:id kohde) (:id rivi))
                                            yllapitokohde-uusilla-kohdeosilla
                                            kohde))
                                        @kohteet-atom))))
        kohdeosat (:kohdeosat rivi)
        kohteen-osat (atom (indeksoi-kohdeosat (yllapitokohteet-domain/jarjesta-yllapitokohteet
                                                 (filter #(= (:tr-numero rivi) (:tr-numero %)) kohdeosat))))
        muut-osat (atom (indeksoi-kohdeosat (yllapitokohteet-domain/jarjesta-yllapitokohteet
                                              (filter #(not= (:tr-numero rivi) (:tr-numero %)) kohdeosat))))
        osa-kohteen-ulkopuolella (fn [_ kohteen-osan-rivi _]
                                   (when (= (:tr-numero rivi) (:tr-numero kohteen-osan-rivi))
                                     (str "Muilla kohteilla ei saa olla sama tienumero pääkohteen kanssa")))
        osa-kohteen-sisalla (fn [_ kohteen-osan-rivi _]
                              (when (and (:tr-alkuosa kohteen-osan-rivi) (:tr-alkuetaisyys kohteen-osan-rivi)
                                         (:tr-loppuosa kohteen-osan-rivi) (:tr-loppuetaisyys kohteen-osan-rivi))
                                (when-not (tr/tr-vali-paakohteen-sisalla? rivi kohteen-osan-rivi)
                                  (str "Osoite ei ole pääkohteen sisällä."))))
        voi-muokata? (not= (some #(when (= (:kohdenumero %) (:kohdenumero rivi)) (:tila %))
                                 @paallystys-tiedot/paallystysilmoitukset)
                           :lukittu)
        virheet (atom nil)]
    (komp/luo
      (komp/kun-muuttuu
        (fn [{:keys [rivi]}]
          ;; Jos pääkohde päivittyy, palvelin saattaa tehdä automaattisia korjauksia kohdeosiin.
          ;; Täten kohteen osat -atomi tulee resetoida vastaamaan päivitettyjä osia.
          (reset! kohteen-osat (indeksoi-kohdeosat (yllapitokohteet-domain/jarjesta-yllapitokohteet
                                                     (filter #(= (:tr-numero rivi) (:tr-numero %)) (:kohdeosat rivi)))))))

      (fn [{:keys [urakka kohteet-atom rivi kohdetyyppi]}]
        (let [kohteella-ajorata-ja-kaista? (boolean (and (:tr-ajorata rivi)
                                                         (:tr-kaista rivi)))]
          [:div
           [yllapitokohdeosat
            {:otsikko "Kohteen tierekisteriosoitteet"
             :rivi-validointi kohteen-rivi-validointi
             :taulukko-validointi kohteen-taulukko-validointi
             :urakka urakka
             :muokattava-tie? (constantly false)
             :muokattava-ajorata-ja-kaista? (constantly (not kohteella-ajorata-ja-kaista?))
             :kohdeosat-atom kohteen-osat
             :esta-ainoan-osan-poisto? true
             :yllapitokohde rivi
             :tallenna-fn (tallenna-fn :kohteen-omat-kohdeosat)
             :tallennettu-fn tallennettu-fn
             :jarjesta-avaimen-mukaan identity
             :validoinnit {:tr-numero [osa-kohteen-sisalla]
                           :tr-ajorata [osa-kohteen-sisalla]
                           :tr-kaista [osa-kohteen-sisalla]
                           :tr-alkuosa [osa-kohteen-sisalla]
                           :tr-alkuetaisyys [osa-kohteen-sisalla]
                           :tr-loppuosa [osa-kohteen-sisalla]
                           :tr-loppuetaisyys [osa-kohteen-sisalla]}
             :virheet-atom virheet
             :voi-muokata? voi-muokata?
             :virhe-viesti (when-not voi-muokata? "Kohdetta ei voi muokata, sillä sen päällystysilmoitus on hyväksytty.")
             :kohdetyyppi kohdetyyppi}]
           [yllapitokohdeosat
            {:otsikko "Muut tierekisteriosoitteet"
             :rivi-validointi muut-kohteen-rivi-validointi
             :taulukko-validointi muut-kohteen-taulukko-validointi
             :urakka urakka
             :muokattava-tie? (constantly true)
             :muokattava-ajorata-ja-kaista? (constantly true)
             :kohdeosat-atom muut-osat
             :esta-ainoan-osan-poisto? false
             :tallenna-fn (tallenna-fn :kohteen-muut-kohdeosat)
             :tallennettu-fn tallennettu-fn
             :jarjesta-avaimen-mukaan identity
             :validoinnit {:tr-numero [osa-kohteen-ulkopuolella]
                           :tr-ajorata [osa-kohteen-ulkopuolella]
                           :tr-kaista [osa-kohteen-ulkopuolella]
                           :tr-alkuosa [osa-kohteen-ulkopuolella]
                           :tr-alkuetaisyys [osa-kohteen-ulkopuolella]
                           :tr-loppuosa [osa-kohteen-ulkopuolella]
                           :tr-loppuetaisyys [osa-kohteen-ulkopuolella]}
             :voi-muokata? voi-muokata?
             :virhe-viesti (when-not voi-muokata? "Kohdetta ei voi muokata, sillä sen päällystysilmoitus on hyväksytty.")}]
           (when (= kohdetyyppi :paallystys)
             [maaramuutokset {:yllapitokohde-id (:id rivi)
                              :urakka-id (:id urakka)
                              :yllapitokohteet-atom kohteet-atom}])])))))


(defn vasta-muokatut-lihavoitu []
  [yleiset/vihje "Viikon sisällä muokatut lihavoitu" "inline-block bold pull-right"])

(defn yllapitokohteet
  "Ottaa urakan, kohteet atomin ja optiot ja luo taulukon, jossa on listattu kohteen tiedot.

  Optiot on map, jossa avaimet:
  otsikko             Taulukon otsikko
  kohdetyyppi         Minkä tyyppisiä kohteita tässä taulukossa näytetään (:paallystys tai :paikkaus)
  yha-sidottu?        Onko taulukon kohteet tulleet kaikki YHA:sta (vaikuttaa mm. kohteiden muokkaamiseen)
  piilota-tallennus?  True jos tallennusnappi piilotetaan
  tallenna            Funktio tallennusnapille
  kun-onnistuu        Funktio tallennuksen onnistumiselle"
  [urakka kohteet-atom {:keys [yha-sidottu? piilota-tallennus?] :as optiot}]
  (let [tr-sijainnit (atom {}) ;; onnistuneesti haetut TR-sijainnit
        tr-virheet (atom {}) ;; virheelliset TR sijainnit
        g (grid/grid-ohjaus)
        tallenna (reaction
                   (if (and @yha/yha-kohteiden-paivittaminen-kaynnissa? yha-sidottu?)
                     :ei-mahdollinen
                     (:tallenna optiot)))
        osan-pituudet-teille (atom nil)
        toiset-kohteet-fn (fn [rivi taulukko]
                            (keep (fn [[indeksi kohdeosa]]
                                    (when (and (:tr-alkuosa kohdeosa) (:tr-alkuetaisyys kohdeosa)
                                               (:tr-loppuosa kohdeosa) (:tr-loppuetaisyys kohdeosa)
                                               (not= kohdeosa rivi)
                                               (= (:tr-numero rivi) (:tr-numero kohdeosa)))
                                      kohdeosa))
                                  taulukko))
        paakohteen-validointi (fn [rivi taulukko]
                                (let [vuosi @u/valittu-urakan-vuosi
                                      paakohde (select-keys rivi tr/paaluvali-avaimet)
                                      ;; Kohteiden päällekkyys keskenään validoidaan taulukko tasolla, jotta rivin päivittämine oikeaksi korjaa
                                      ;; myös toisilla riveillä olevat validoinnit.
                                      validoitu (yllapitokohteet-domain/validoi-kohde paakohde (get @paallystys-tiedot/tr-osien-tiedot (:tr-numero rivi)) {:vuosi vuosi})]
                                  (yllapitokohteet-domain/validoitu-kohde-tekstit validoitu true)))
        alikohteen-validointi (fn [paakohde rivi taulukko]
                                (let [toiset-kohteet (toiset-kohteet-fn rivi taulukko)
                                      vuosi @u/valittu-urakan-vuosi
                                      validoitu (yllapitokohteet-domain/validoi-alikohde paakohde rivi [] (get @paallystys-tiedot/tr-osien-tiedot (:tr-numero rivi)) vuosi)]
                                  (yllapitokohteet-domain/validoitu-kohde-tekstit (dissoc validoitu :alikohde-paallekkyys) false)))
        muukohteen-validointi (fn [paakohde rivi taulukko]
                                (let [vuosi @u/valittu-urakan-vuosi
                                      ;; Kohteiden päällekkyys keskenään validoidaan taulukko tasolla, jotta rivin päivittämine oikeaksi korjaa
                                      ;; myös toisilla riveillä olevat validoinnit.
                                      validoitu (yllapitokohteet-domain/validoi-muukohde paakohde rivi [] (get @paallystys-tiedot/tr-osien-tiedot (:tr-numero rivi)) vuosi)]
                                  (yllapitokohteet-domain/validoitu-kohde-tekstit (dissoc validoitu :muukohde-paallekkyys) false)))
        kohde-toisten-kanssa-paallekkain-validointi (fn [alikohde? _ rivi taulukko]
                                                      (let [toiset-kohteet (toiset-kohteet-fn rivi taulukko)
                                                            paallekkyydet (filter #(yllapitokohteet-domain/tr-valit-paallekkain? rivi %)
                                                                                  toiset-kohteet)]
                                                        (yllapitokohteet-domain/validoitu-kohde-tekstit {(if alikohde?
                                                                                                           :alikohde-paallekkyys
                                                                                                           :paallekkyys)
                                                                                                         paallekkyydet}
                                                                                                        (not alikohde?))))
        muukohde-toisten-kanssa-paallekkain-validointi (fn [_ rivi taulukko]
                                                         (let [toiset-kohteet (toiset-kohteet-fn rivi taulukko)
                                                               paallekkyydet (filter #(yllapitokohteet-domain/tr-valit-paallekkain? rivi %)
                                                                                     toiset-kohteet)]
                                                           (yllapitokohteet-domain/validoitu-kohde-tekstit {:muukohde-paallekkyys
                                                                                                            paallekkyydet}
                                                                                                           false)))
        hae-osien-tiedot (fn [grid-tila]
                           (let [rivit (vals grid-tila)
                                 jo-haetut-tiedot (into #{}
                                                        (map (juxt :tr-numero :tr-osa) (apply concat (vals @paallystys-tiedot/tr-osien-tiedot))))
                                 tarvittavat-tiedot (clj-set/union
                                                     (into #{}
                                                           (map (juxt :tr-numero :tr-alkuosa) rivit))
                                                     (into #{}
                                                          (map (juxt :tr-numero :tr-loppuosa) rivit)))
                                 puuttuvat-tiedot (clj-set/difference tarvittavat-tiedot jo-haetut-tiedot)
                                 mahdollisesti-haettavat-tiedot (reduce (fn [haettavat-tiedot [tr-numero tr-osa]]
                                                                        (if (some #(= tr-numero (:tr-numero %)) haettavat-tiedot)
                                                                          (map #(if (= tr-numero (:tr-numero %))
                                                                                  (-> %
                                                                                      (update :tr-alkuosa (fn [vanha-tr-alkuosa]
                                                                                                            (min vanha-tr-alkuosa tr-osa)))
                                                                                      (update :tr-loppuosa (fn [vanha-tr-loppuosa]
                                                                                                             (max vanha-tr-loppuosa tr-osa))))
                                                                                  %)
                                                                               haettavat-tiedot)
                                                                          (conj haettavat-tiedot {:tr-numero tr-numero
                                                                                                  :tr-alkuosa tr-osa
                                                                                                  :tr-loppuosa tr-osa})))
                                                                        [] (filter (fn [[tr-numero _]]
                                                                                     (some #(= tr-numero (first %))
                                                                                            puuttuvat-tiedot))
                                                                                   (concat jo-haetut-tiedot tarvittavat-tiedot)))]
                             (when-not (empty? mahdollisesti-haettavat-tiedot)
                               (go (doseq [haettava-tieto mahdollisesti-haettavat-tiedot]
                                     (let [tr-tieto (<! (k/post! :hae-tr-tiedot haettava-tieto))]
                                       (swap! paallystys-tiedot/tr-osien-tiedot
                                              assoc (:tr-numero haettava-tieto) tr-tieto)))
                                   (grid/validoi-grid g)))))]
    (komp/luo
      (komp/piirretty (fn [] (grid/validoi-grid g)))
      (fn [urakka kohteet-atom {:keys [yha-sidottu? piilota-tallennus?] :as optiot}]
        (let [nayta-ajorata-ja-kaista? (or (not yha-sidottu?)
                                           ;; YHA-kohteille näytetään ajorata ja kaista vain siinä tapauksessa, että
                                           ;; ainakin yhdellä kohteella ne on annettu
                                           ;; Näitä ei voi muokata itse, joten turha näyttää aina tyhjiä sarakkeita.
                                           (and yha-sidottu?
                                                (some #(or (:tr-ajorata %) (:tr-kaista %)) @kohteet-atom)))
              paallystysilmoitus-lukittu? #(not= :lukittu (:paallystysilmoitus-tila %))
              validointi {:paakohde {:tr-osoite [{:fn paakohteen-validointi
                                                  :sarakkeet {:tr-numero :tr-numero
                                                              :tr-ajorata :tr-ajorata
                                                              :tr-kaista :tr-kaista
                                                              :tr-alkuosa :tr-alkuosa
                                                              :tr-alkuetaisyys :tr-alkuetaisyys
                                                              :tr-loppuosa :tr-loppuosa
                                                              :tr-loppuetaisyys :tr-loppuetaisyys}}]}}
              varoitukset {:paakohde {:taulukko [{:fn (r/partial kohde-toisten-kanssa-paallekkain-validointi false)
                                                  :sarakkeet {:tr-numero :tr-numero
                                                              :tr-alkuosa :tr-alkuosa
                                                              :tr-alkuetaisyys :tr-alkuetaisyys
                                                              :tr-loppuosa :tr-loppuosa
                                                              :tr-loppuetaisyys :tr-loppuetaisyys}}]}}]
          [:div.yllapitokohteet
           [grid/grid
            {:otsikko [:span (:otsikko optiot)
                       [vasta-muokatut-lihavoitu]]
             :ohjaus g
             :tyhja (if (nil? @kohteet-atom) [ajax-loader "Haetaan kohteita..."] "Ei kohteita")
             :vetolaatikot (into {}
                                 (map (juxt
                                        :id
                                        (fn [rivi]
                                          [kohteen-vetolaatikko {:urakka urakka
                                                                 :sopimus-id (first @u/valittu-sopimusnumero)
                                                                 :kohteet-atom kohteet-atom
                                                                 :rivi rivi
                                                                 :kohdetyyppi (:kohdetyyppi optiot)
                                                                 :kohteen-rivi-validointi [{:fn (r/partial alikohteen-validointi (select-keys rivi tr/paaluvali-avaimet))
                                                                                            :sarakkeet tr/alikohteen-tr-sarakkeet-map}]
                                                                 :kohteen-taulukko-validointi [{:fn (r/partial kohde-toisten-kanssa-paallekkain-validointi true)
                                                                                                :sarakkeet tr/alikohteen-tr-sarakkeet-map}]
                                                                 :muut-kohteen-rivi-validointi [{:fn (r/partial muukohteen-validointi (select-keys rivi tr/paaluvali-avaimet))
                                                                                                 :sarakkeet tr/alikohteen-tr-sarakkeet-map}]
                                                                 :muut-kohteen-taulukko-validointi [{:fn (r/partial muukohde-toisten-kanssa-paallekkain-validointi)
                                                                                                     :sarakkeet tr/alikohteen-tr-sarakkeet-map}]}])))
                                 @kohteet-atom)
             :rivi-validointi (-> validointi :paakohde :tr-osoite)
             :taulukko-varoitus (-> varoitukset :paakohde :taulukko)
             :tallenna (when-not piilota-tallennus? @tallenna)
             :nollaa-muokkaustiedot-tallennuksen-jalkeen? (fn [vastaus]
                                                            (#{:ok :yha-virhe} (:status vastaus)))
             :muutos (fn [grid]
                       (hae-osien-tiedot (grid/hae-muokkaustila grid)))
             :voi-lisata? (not yha-sidottu?)
             :voi-muokata-rivia? paallystysilmoitus-lukittu?
             :esta-poistaminen? (fn [rivi] (not (:yllapitokohteen-voi-poistaa? rivi)))
             :esta-poistaminen-tooltip (fn [_]
                                         (if yha-sidottu?
                                           "Kohdetta on muokattu tai sille on tehty kirjauksia."
                                           "Kohteelle on tehty kirjauksia."))}
            (into []
                  (concat
                    [{:tyyppi :vetolaatikon-tila :leveys haitari-leveys}
                     {:otsikko "Koh\u00ADde\u00ADnu\u00ADme\u00ADro" :nimi :kohdenumero
                      :tyyppi :string :leveys id-leveys}
                     {:otsikko "Tunnus" :nimi :tunnus
                      :tyyppi :string :leveys tunnus-leveys :pituus-max 1}]
                    (tierekisteriosoite-sarakkeet
                      tr-leveys
                      [{:otsikko "Nimi" :nimi :nimi
                        :tyyppi :string :leveys (if nayta-ajorata-ja-kaista? kohde-leveys (* tr-leveys 4))
                        :pituus-max 30}
                       {:nimi :tr-numero :muokattava? (constantly (not yha-sidottu?))}
                       (when nayta-ajorata-ja-kaista?
                         {:nimi :tr-ajorata :muokattava? (constantly (not yha-sidottu?))})
                       (when nayta-ajorata-ja-kaista?
                         {:nimi :tr-kaista :muokattava? (constantly (not yha-sidottu?))})
                       {:nimi :tr-alkuosa}
                       {:nimi :tr-alkuetaisyys}
                       {:nimi :tr-loppuosa}
                       {:nimi :tr-loppuetaisyys}]
                      true)
                    [{:otsikko "KVL"
                      :nimi :keskimaarainen-vuorokausiliikenne :tyyppi :numero :leveys kvl-leveys
                      :muokattava? (constantly (not yha-sidottu?))}
                     {:otsikko "YP-lk"
                      :nimi :yllapitoluokka :leveys yllapitoluokka-leveys :tyyppi :valinta
                      :valinnat yllapitokohteet-domain/nykyiset-yllapitoluokat
                      :valinta-nayta #(if % (:lyhyt-nimi %) "-")
                      :fmt :lyhyt-nimi
                      :muokattava? (constantly (not yha-sidottu?))}

                     (when (= (:kohdetyyppi optiot) :paallystys)
                       {:otsikko "Tar\u00ADjous\u00ADhinta" :nimi :sopimuksen-mukaiset-tyot
                        :fmt fmt/euro-opt :tyyppi :numero :leveys tarjoushinta-leveys :tasaa :oikea})
                     (when (= (:kohdetyyppi optiot) :paallystys)
                       {:otsikko "Mää\u00ADrä\u00ADmuu\u00ADtok\u00ADset"
                        :nimi :maaramuutokset :muokattava? (constantly false)
                        :tyyppi :komponentti :leveys maaramuutokset-leveys :tasaa :oikea
                        :komponentti (fn [rivi]
                                       [:span {:class (when (:maaramuutokset-ennustettu? rivi)
                                                        "grid-solu-ennustettu")}
                                        (fmt/euro-opt (:maaramuutokset rivi))])})
                     (when (= (:kohdetyyppi optiot) :paikkaus)
                       {:otsikko "Toteutunut hinta" :nimi :toteutunut-hinta
                        :fmt fmt/euro-opt :tyyppi :numero :leveys toteutunut-hinta-leveys
                        :tasaa :oikea})
                     {:otsikko "Ar\u00ADvon muu\u00ADtok\u00ADset" :nimi :arvonvahennykset :fmt fmt/euro-opt
                      :tyyppi :numero :leveys arvonvahennykset-leveys :tasaa :oikea}
                     {:otsikko "Sak\u00ADko/bo\u00ADnus" :nimi :sakot-ja-bonukset :fmt fmt/euro-opt
                      :tyyppi :numero :leveys arvonvahennykset-leveys :tasaa :oikea
                      :muokattava? (constantly false)}
                     {:otsikko "Bi\u00ADtumi-in\u00ADdek\u00ADsi" :nimi :bitumi-indeksi
                      :fmt fmt/euro-opt
                      :tyyppi :numero :leveys bitumi-indeksi-leveys :tasaa :oikea}
                     {:otsikko "Kaa\u00ADsu\u00ADindeksi" :nimi :kaasuindeksi :fmt fmt/euro-opt
                      :tyyppi :numero :leveys kaasuindeksi-leveys :tasaa :oikea}
                     {:otsikko (str "Ko\u00ADko\u00ADnais\u00ADhinta"
                                    " (ind\u00ADek\u00ADsit mu\u00ADka\u00ADna)")
                      :muokattava? (constantly false)
                      :nimi :kokonaishinta :fmt fmt/euro-opt :tyyppi :komponentti :leveys yhteensa-leveys
                      :tasaa :oikea
                      :komponentti (fn [rivi]
                                     [:span {:class (when (:maaramuutokset-ennustettu? rivi)
                                                      "grid-solu-ennustettu")}
                                      (fmt/euro-opt (yllapitokohteet-domain/yllapitokohteen-kokonaishinta rivi))])}]))
            (yllapitokohteet-domain/lihavoi-vasta-muokatut
              (yllapitokohteet-domain/jarjesta-yllapitokohteet @kohteet-atom))]
           [tr-virheilmoitus tr-virheet]])))))

(defn yllapitokohteet-yhteensa [kohteet-atom optiot]
  (let [yhteensa
        (reaction
          (let [kohteet @kohteet-atom
                sopimuksen-mukaiset-tyot-yhteensa (laske-sarakkeen-summa :sopimuksen-mukaiset-tyot kohteet)
                toteutunut-hinta-yhteensa (laske-sarakkeen-summa :toteutunut-hinta kohteet)
                maaramuutokset-yhteensa (laske-sarakkeen-summa :maaramuutokset kohteet)
                arvonvahennykset-yhteensa (laske-sarakkeen-summa :arvonvahennykset kohteet)
                sakot-ja-bonukset-yhteensa (laske-sarakkeen-summa :sakot-ja-bonukset kohteet)
                bitumi-indeksi-yhteensa (laske-sarakkeen-summa :bitumi-indeksi kohteet)
                kaasuindeksi-yhteensa (laske-sarakkeen-summa :kaasuindeksi kohteet)
                muut-yhteensa (laske-sarakkeen-summa :muut-hinta kohteet)
                kokonaishinta (+ (yllapitokohteet-domain/yllapitokohteen-kokonaishinta
                                   {:sopimuksen-mukaiset-tyot sopimuksen-mukaiset-tyot-yhteensa
                                    :toteutunut-hinta toteutunut-hinta-yhteensa
                                    :maaramuutokset maaramuutokset-yhteensa
                                    :arvonvahennykset arvonvahennykset-yhteensa
                                    :sakot-ja-bonukset sakot-ja-bonukset-yhteensa
                                    :bitumi-indeksi bitumi-indeksi-yhteensa
                                    :kaasuindeksi kaasuindeksi-yhteensa})
                                 (or muut-yhteensa 0))]
            [{:id 0
              :sopimuksen-mukaiset-tyot sopimuksen-mukaiset-tyot-yhteensa
              :maaramuutokset maaramuutokset-yhteensa
              :toteutunut-hinta toteutunut-hinta-yhteensa
              :arvonvahennykset arvonvahennykset-yhteensa
              :sakot-ja-bonukset sakot-ja-bonukset-yhteensa
              :bitumi-indeksi bitumi-indeksi-yhteensa
              :kaasuindeksi kaasuindeksi-yhteensa
              :muut-hinta muut-yhteensa
              :kokonaishinta kokonaishinta}]))]

    [grid/grid
     {:nayta-toimintosarake? true
      :otsikko "Yhteensä"
      :tyhja (if (nil? {}) [ajax-loader "Lasketaan..."] "")}
     [{:otsikko "" :nimi :tyhja :tyyppi :string :leveys haitari-leveys}
      {:otsikko "" :nimi :kohdenumero :tyyppi :string :leveys id-leveys}
      {:otsikko "" :nimi :tunnus :tyyppi :string :leveys tunnus-leveys}
      {:otsikko "" :nimi :nimi :tyyppi :string :leveys kohde-leveys}
      {:otsikko "" :nimi :tr-numero :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :tr-ajorata :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :tr-kaista :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :tr-alkuosa :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :tr-alkuetaisyys :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :tr-loppuosa :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :tr-loppuetaisyys :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :pit :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :keskimaarainen-vuorokausiliikenne :tyyppi :string :leveys kvl-leveys}
      {:otsikko "" :nimi :yllapitoluokka :tyyppi :string :leveys yllapitoluokka-leveys}
      {:otsikko "" :nimi :tyhja :tyyppi :string :leveys toteutunut-hinta-leveys}
      (when (= (:kohdetyyppi optiot) :paallystys)
        {:otsikko "Tarjous\u00ADhinta" :nimi :sopimuksen-mukaiset-tyot
         :fmt fmt/euro-opt :tyyppi :numero
         :leveys tarjoushinta-leveys :tasaa :oikea})
      (when (= (:kohdetyyppi optiot) :paallystys)
        {:otsikko "Muutok\u00ADset" :nimi :maaramuutokset :fmt fmt/euro-opt :tyyppi :numero
         :leveys maaramuutokset-leveys :tasaa :oikea})
      (when (= (:kohdetyyppi optiot) :paikkaus)
        {:otsikko "Toteutunut hinta" :nimi :toteutunut-hinta :fmt fmt/euro-opt :tyyppi :numero
         :leveys toteutunut-hinta-leveys :tasaa :oikea})
      {:otsikko "Muut kustan\u00ADnukset" :nimi :muut-hinta :fmt fmt/euro-opt :tyyppi :numero
       :leveys muut-leveys :tasaa :oikea}
      {:otsikko "Arvon\u00ADväh." :nimi :arvonvahennykset :fmt fmt/euro-opt :tyyppi :numero
       :leveys arvonvahennykset-leveys :tasaa :oikea}
      {:otsikko "Sak\u00ADko/bo\u00ADnus" :nimi :sakot-ja-bonukset :fmt fmt/euro-opt
       :tyyppi :numero :leveys arvonvahennykset-leveys :tasaa :oikea
       :muokattava? (constantly false)}
      {:otsikko "Bitumi-indeksi" :nimi :bitumi-indeksi :fmt fmt/euro-opt :tyyppi :numero
       :leveys bitumi-indeksi-leveys :tasaa :oikea}
      {:otsikko "Kaasu\u00ADindeksi" :nimi :kaasuindeksi :fmt fmt/euro-opt :tyyppi :numero
       :leveys kaasuindeksi-leveys :tasaa :oikea}
      {:otsikko "Kokonais\u00ADhinta (indeksit mukana)" :nimi :kokonaishinta
       :tyyppi :komponentti :leveys yhteensa-leveys :tasaa :oikea
       :komponentti
       (fn [rivi]
         [:span {:class (when (some :maaramuutokset-ennustettu? @kohteet-atom)
                          "grid-solu-ennustettu")}
          (fmt/euro-opt (:kokonaishinta rivi))])}]
     @yhteensa]))
