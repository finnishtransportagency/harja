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
            [harja.tiedot.urakka.paallystys-muut-kustannukset :as muut-kustannukset]
            [clojure.string :as string]
            [harja.ui.modal :as modal])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))



(defn tr-virheilmoitus [tr-virheet]
  [:div.tr-virheet
   (for [virhe (into #{} (vals @tr-virheet))]
     ^{:key (hash virhe)}
     [:div.tr-virhe (ikonit/livicon-warning-sign)
      virhe])])

;; Ylläpitokohteiden sarakkeiden leveydet
(def haitari-leveys 5)
(def id-leveys 6)
(def yhaid-leveys 6)
(def tunnus-leveys 6)
(def yotyo-leveys 6)
(def kvl-leveys 5)
(def pk-luokka-leveys-aikataulu 3)
(def pk-luokka-leveys 7)
(def tr-leveys 8)
(def tr-leveys-aikataulu 2)
(def kohde-leveys (* tr-leveys 2))
(def kohde-leveys-aikataulu (* tr-leveys-aikataulu 4))
(def tarjoushinta-leveys 10)
(def maaramuutokset-leveys 10)
(def toteutunut-hinta-leveys 20)
(def arvonvahennykset-leveys 10)
(def muut-leveys 10)
(def bitumi-indeksi-leveys 10)
(def kaasuindeksi-leveys 10)
(def maku-paallysteet-leveys 10)
(def yhteensa-leveys 10)

;; Ylläpitokohdeosien sarakkeiden leveydet
(def nimi-leveys 20)
(def paallyste-leveys 10)
(def raekoko-leveys 5)
(def tyomenetelma-leveys 10)
(def massamaara-leveys 7)
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
              {:otsikko "Ajo\u00ADrata" :alasveto-luokka "kavenna-jos-kapea"
               :nimi (:nimi ajorata)
               :muokattava? (or (:muokattava? ajorata) true-fn)
               :kentta-arity-3? (:arity-3? ajorata)
               :tyyppi :valinta
               :valinta-arvo :koodi
               :fmt ajorata-fmt-fn
               :valinta-nayta ajorata-valinta-nayta-fn
               :valinnat pot/+ajoradat-numerona+
               :leveys perusleveys
               :validoi (:validoi ajorata)})
            (when kaista
              {:otsikko "Kais\u00ADta" :alasveto-luokka "kavenna-jos-kapea"
               :muokattava? (or (:muokattava? kaista) true-fn)
               :kentta-arity-3? (:arity-3? kaista)
               :nimi (:nimi kaista)
               :tyyppi :valinta
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
              {:otsikko "Pit. (m)" :nimi :pituus :leveys perusleveys :tyyppi :kokonaisluku :tasaa :oikea
               :muokattava? false-fn}
              pituus)]))))

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
  ([{:keys [muokattava-tie? muokattava-ajorata-ja-kaista? validoi
            vain-nama-validoinnit? hae-fn tr-sarake-fn valinta-arity-3? dissoc-cols aikataulu?]}
    [tayta-alas?-fn tayta-paallyste-fn tayta-paallyste-toistuvasti-fn paallyste-valinta-nayta-fn tayta-raekoko-fn
     tayta-raekoko-toistuvasti-fn tayta-tyomenetelma-fn tayta-tyomenetelma-toistuvasti-fn tyomenetelma-valinta-nayta-fn
     tayta-massamaara-fn tayta-massamaara-toistuvasti-fn tayta-toimenpide-fn tayta-toimenpide-toistuvasti-fn]]
   (let [tr-sarakkeet-asetukset [{:nimi :nimi :pituus-max 30 :leveys (if aikataulu? kohde-leveys-aikataulu kohde-leveys)}
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
                        (if aikataulu? tr-leveys-aikataulu tr-leveys)
                        tr-sarakkeet-asetukset
                        vain-nama-validoinnit?
                        tr-sarake-fn)
                      (tierekisteriosoite-sarakkeet
                        (if aikataulu? tr-leveys-aikataulu tr-leveys)
                        tr-sarakkeet-asetukset
                        vain-nama-validoinnit?))
                    [(assoc paallystys-tiedot/pk-lk-skeema
                       :fokus-klikin-jalkeen? true
                       :tasaa (when aikataulu? :oikea)
                       :leveys (if aikataulu?
                                 pk-luokka-leveys-aikataulu
                                 pk-luokka-leveys)
                       :tayta-alas? tayta-alas?-fn
                       :tayta-sijainti :ylos
                       :tayta-tooltip "Kopioi sama pk-lk alla oleville riveille"
                       :kentta-arity-3? valinta-arity-3?)
                     (assoc paallystys-tiedot/paallyste-grid-skeema
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
                     (when-not (contains? dissoc-cols :raekoko)
                       (assoc paallystys-tiedot/raekoko-grid-skeema
                         :leveys raekoko-leveys
                         :tayta-alas? tayta-alas?-fn
                         :tayta-fn tayta-raekoko-fn
                         :tayta-sijainti :ylos
                         :tayta-tooltip "Kopioi sama raekoko alla oleville riveille"
                         :tayta-alas-toistuvasti? tayta-alas?-fn
                         :tayta-toistuvasti-fn tayta-raekoko-toistuvasti-fn))
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
                     (when-not (contains? dissoc-cols :massamaara)
                       {:otsikko "Massa\u00ADmenekki (kg/m²)" :nimi :massamaara
                        :tyyppi :positiivinen-numero :tasaa :oikea :leveys massamaara-leveys
                        :tayta-alas? tayta-alas?-fn
                        :tayta-fn tayta-massamaara-fn
                        :tayta-sijainti :ylos
                        :tayta-tooltip "Kopioi sama massamäärä alla oleville riveille"
                        :tayta-alas-toistuvasti? tayta-alas?-fn
                        :tayta-toistuvasti-fn tayta-massamaara-toistuvasti-fn})
                     (when-not (contains? dissoc-cols :toimenpide)
                       {:otsikko "Toimenpiteen selitys" :nimi :toimenpide :tyyppi :string
                        :leveys toimenpide-leveys
                        :tayta-alas? tayta-alas?-fn
                        :tayta-fn tayta-toimenpide-fn
                        :tayta-sijainti :ylos
                        :tayta-tooltip "Kopioi sama selitys alla oleville riveille"
                        :tayta-alas-toistuvasti? tayta-alas?-fn
                        :tayta-toistuvasti-fn tayta-toimenpide-toistuvasti-fn})]))))))

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
                  (yllapitokohteet-domain/indeksoi-kohdeosat (yllapitokohteet-domain/jarjesta-yllapitokohteet yllapitokohteet))))
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
                                                       (tiedot/pilko-paallystekohdeosa vanhat-kohdeosat 1 yllapitokohde)))))]
                           (fn [voi-muokata?]
                             (if (nil? @kohdeosat)
                               [ajax-loader "Haetaan kohdeosia..."]
                               [:div
                                [:div {:style {:display "inline-block"}} "Ei kohdeosia"]
                                (when (and kirjoitusoikeus? voi-muokata?)
                                  [:div.toiminnot {:style {:display "inline-block"
                                                           :float "right"}}
                                   [napit/nappi-hover-vihjeella {:tyyppi :lisaa
                                                                 :toiminto voi-muokata?-fn
                                                                 :hover-txt tiedot/hint-lisaa-osa}]])]))))
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
                                                                           (tiedot/pilko-paallystekohdeosa vanhat-kohdeosat (inc index) {}))))
                                      poista-osa-fn (fn [index]
                                                      (kohdeosat-muokkaa! (fn [vanhat-kohdeosat]
                                                                            (tiedot/poista-kohdeosa vanhat-kohdeosat (inc index)))))]
                                  (fn [rivi {:keys [index]} voi-muokata?]
                                    (let [yllapitokohde (-> @paallystys-tiedot/tila :paallystysilmoitus-lomakedata :perustiedot (select-keys [:tr-numero :tr-kaista :tr-ajorata :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys]))]
                                      [:div.toiminnot
                                       [napit/nappi-hover-vihjeella {:tyyppi :pilko
                                                                     :toiminto lisaa-osa-fn
                                                                     :toiminto-args [index]
                                                                     :hover-txt tiedot/hint-pilko-osoitevali
                                                                     :disabled? (or (not kirjoitusoikeus?)
                                                                                    (not voi-muokata?)
                                                                                    (= (:yllapitokohdetyyppi yllapitokohde) :sora))
                                                                     :data-attributes {:data-cy "lisaa-osa-nappi"}}]
                                       [napit/nappi-hover-vihjeella {:tyyppi :poista
                                                                     :disabled? (or (not kirjoitusoikeus?)
                                                                                    (not voi-muokata?)
                                                                                    (= (:yllapitokohdetyyppi yllapitokohde) :sora))
                                                                     :hover-txt tiedot/hint-poista-rivi
                                                                     :toiminto poista-osa-fn
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
               {:otsikko "Toimin\u00ADnot" :nimi :tr-muokkaus :tyyppi :reagent-komponentti :leveys 8
                :tasaa :keskita :komponentti-args [voi-muokata?]
                :komponentti toiminnot-komponentti})
         kohdeosat]))))

(defn yllapitokohdeosat [{:keys [urakka virheet-atom validoinnit voi-muokata?
                                 virhe-viesti rivinumerot? dissoc-cols aikataulu?]}]
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
                 (tr/laske-tien-pituus (tien-osat-riville rivi) rivi))
        pituus (fn [osan-pituus tieosa]
                 (tr/laske-tien-pituus osan-pituus tieosa))]
    (fn [{:keys [yllapitokohde otsikko kohdeosat-atom tallenna-fn tallennettu-fn
                 muokattava-tie? muokattava-ajorata-ja-kaista? jarjesta-avaimen-mukaan
                 jarjesta-kun-kasketaan esta-ainoan-osan-poisto? rivi-validointi taulukko-validointi]}]
      (let [skeema (yllapitokohdeosat-sarakkeet {:muokattava-ajorata-ja-kaista? muokattava-ajorata-ja-kaista?
                                                 :muokattava-tie? muokattava-tie?
                                                 :vain-nama-validoinnit? true
                                                 :hae-fn hae-fn
                                                 :dissoc-cols dissoc-cols
                                                 :aikataulu? aikataulu?})
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
                                               [:div.toiminnot {:style {:display "inline-block"
                                                                        :float "right"}}
                                                [napit/nappi-hover-vihjeella {:tyyppi :lisaa
                                                                              :toiminto #(reset! kohdeosat-atom (tiedot/pilko-paallystekohdeosa @kohdeosat-atom 1 yllapitokohde))
                                                                              :hover-txt tiedot/hint-lisaa-osa
                                                                              :disabled? (or (not kirjoitusoikeus?)
                                                                                             (not voi-muokata?)
                                                                                             (= (:yllapitokohdetyyppi yllapitokohde) :sora))}]])])
          :taulukko-validointi taulukko-validointi
          :ohjaus g
          :rivi-validointi rivi-validointi
          :rivinumerot? rivinumerot?
          :voi-muokata? (and kirjoitusoikeus? voi-muokata?)
          :virhe-viesti virhe-viesti
          :muutos (fn [grid]
                    (paallystys-tiedot/hae-osan-pituudet (grid/hae-muokkaustila grid) osan-pituudet-teille))
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
                              (paallystys-tiedot/hae-osan-pituudet grid-state osan-pituudet-teille))
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
                            [:span
                             [:span#kohdeosien-pituus-yht
                              "Tierekisterikohteiden pituus yhteensä: "
                              (if (not (empty? @osan-pituudet-teille))
                                (fmt/pituus (reduce + 0 (keep (fn [kohdeosa]
                                                                (pituus (get @osan-pituudet-teille (:tr-numero kohdeosa)) kohdeosa))
                                                              (vals (grid/hae-muokkaustila g)))))
                                "-")
                              (when (= (:yllapitokohdetyyppi yllapitokohde) :sora)
                                [:p (ikonit/ikoni-ja-teksti (ikonit/livicon-info-sign) " Soratiekohteilla voi olla vain yksi alikohde")])]
                             [:span " Varsinaiset päällysteen toteumatiedot löytyvät päällystysilmoituksesta."]])}
         (if (contains? dissoc-cols :tr-muokkaus)
           skeema
           (conj skeema
                 {:otsikko "Toimin\u00ADnot" :nimi :tr-muokkaus :tyyppi :komponentti :leveys 8
                  :tasaa :keskita
                  :komponentti (fn [rivi {:keys [index]}]
                                 [:div.toiminnot
                                  [napit/nappi-hover-vihjeella {:tyyppi :pilko
                                                                :toiminto #(do
                                                                             (muokkaa-kohdeosat! (tiedot/pilko-paallystekohdeosa @kohdeosat-atom (inc index) {}))
                                                                             (grid/validoi-grid g))
                                                                :hover-txt tiedot/hint-pilko-osoitevali
                                                                :disabled? (or (not kirjoitusoikeus?)
                                                                               (not voi-muokata?)
                                                                               (= (:yllapitokohdetyyppi yllapitokohde) :sora))
                                                                :data-attributes {:data-cy (str "lisaa-osa-" otsikko)}}]
                                  [napit/nappi-hover-vihjeella {:tyyppi :poista
                                                                :disabled? (or (not kirjoitusoikeus?)
                                                                               (not voi-muokata?)
                                                                               (and esta-ainoan-osan-poisto?
                                                                                    (= (count @kohdeosat-atom) 1))
                                                                               (= (:yllapitokohdetyyppi yllapitokohde) :sora))
                                                                :hover-txt tiedot/hint-poista-rivi
                                                                :toiminto #(do
                                                                             (muokkaa-kohdeosat! (tiedot/poista-kohdeosa @kohdeosat-atom (inc index)))
                                                                             (grid/validoi-grid g))}]])}))
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

(defn- maaramuutosten-2023-muutoksesta-vinkki []
  [:span
   [:h6 "Määrämuutokset"]
   [yleiset/vihje "Vuodesta 2023 alkaen määrämuutoksista ei syötetä Harjaan enää muuta kuin kohdekohtaiset summat. Summan voi syöttää suoraan yläpuolella olevalle kohderiville tai tuoda kustannukset Excel-tiedoston avulla."]])

(defn kohteen-vetolaatikko [{:keys [urakka sopimus-id kohteet-atom rivi dissoc-cols
                                    kohteen-rivi-validointi kohteen-taulukko-validointi
                                    muut-kohteen-rivi-validointi muut-kohteen-taulukko-validointi
                                    paallystyksen-tarkka-aikataulu piilota-kohdeosat? aikataulu?]}]
  (let [vuosi @u/valittu-urakan-vuosi
        tallenna-fn (fn [osatyyppi]
                      (fn [rivit]
                        (tiedot/tallenna-yllapitokohdeosat!
                          {:urakka-id (:id urakka)
                           :sopimus-id sopimus-id
                           :vuosi vuosi
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
        kohteen-osat (atom (yllapitokohteet-domain/indeksoi-kohdeosat (yllapitokohteet-domain/jarjesta-yllapitokohteet
                                                 (filter #(= (:tr-numero rivi) (:tr-numero %)) kohdeosat))))
        muut-osat (atom (yllapitokohteet-domain/indeksoi-kohdeosat (yllapitokohteet-domain/jarjesta-yllapitokohteet
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
          (reset! kohteen-osat (yllapitokohteet-domain/indeksoi-kohdeosat (yllapitokohteet-domain/jarjesta-yllapitokohteet
                                                     (filter #(= (:tr-numero rivi) (:tr-numero %)) (:kohdeosat rivi)))))))

      (fn [{:keys [urakka kohteet-atom rivi kohdetyyppi]}]
        (let [kohteella-ajorata-ja-kaista? (boolean (and (:tr-ajorata rivi)
                                                         (:tr-kaista rivi)))]
          [:div
           (when-not piilota-kohdeosat?
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
              :kohdetyyppi kohdetyyppi
              :dissoc-cols dissoc-cols
              :aikataulu? aikataulu?}])
           (when-not piilota-kohdeosat?
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
              :virhe-viesti (when-not voi-muokata? "Kohdetta ei voi muokata, sillä sen päällystysilmoitus on hyväksytty.")
              :dissoc-cols dissoc-cols
              :aikataulu? aikataulu?}])
           (when paallystyksen-tarkka-aikataulu
             [(:fn paallystyksen-tarkka-aikataulu)
              urakka sopimus-id rivi vuosi
              (:nakyma paallystyksen-tarkka-aikataulu)
              (:voi-muokata-paallystys? paallystyksen-tarkka-aikataulu)
              (:voi-muokata-tiemerkinta? paallystyksen-tarkka-aikataulu)])
           (if (and (= kohdetyyppi :paallystys)
                    (yllapitokohteet-domain/eritellyt-maaramuutokset-kaytossa? vuosi))
             [maaramuutokset {:yllapitokohde-id (:id rivi)
                              :urakka-id (:id urakka)
                              :yllapitokohteet-atom kohteet-atom}]
             [maaramuutosten-2023-muutoksesta-vinkki])])))))


(defn vasta-muokatut-vinkki []
  [:div.tuoreusvihje
   [:div.kelloikoni [ikonit/harja-icon-action-set-time]]
   [:div.tarkentava-teksti "Kello kohdenumeron perässä tarkoittaa, että kohdetta on muokattu viimeisen viikon sisään."]])

(defn- paakohteen-validointi
  [rivi taulukko]
  (let [vuosi @u/valittu-urakan-vuosi
        paakohde (select-keys rivi tr/paaluvali-avaimet)
        ;; Kohteiden päällekkyys keskenään validoidaan taulukko tasolla, jotta rivin päivittämine oikeaksi korjaa
        ;; myös toisilla riveillä olevat validoinnit.
        validoitu (yllapitokohteet-domain/validoi-kohde paakohde (get @paallystys-tiedot/tr-osien-tiedot (:tr-numero rivi)) {:vuosi vuosi})]
    (yllapitokohteet-domain/validoitu-kohde-tekstit validoitu true)))


(defn- toiset-kohteet-fn
  [rivi taulukko]
  (keep (fn [[indeksi kohdeosa]]
          (when (and (:tr-alkuosa kohdeosa) (:tr-alkuetaisyys kohdeosa)
                     (:tr-loppuosa kohdeosa) (:tr-loppuetaisyys kohdeosa)
                     (not= kohdeosa rivi)
                     (= (:tr-numero rivi) (:tr-numero kohdeosa)))
            kohdeosa))
        taulukko))

(defn kohde-toisten-kanssa-paallekkain-validointi
  [alikohde? _ rivi taulukko]
  (let [toiset-kohteet (toiset-kohteet-fn rivi taulukko)
        paallekkyydet (filter #(yllapitokohteet-domain/tr-valit-paallekkain? rivi %)
                              toiset-kohteet)]
    (yllapitokohteet-domain/validoitu-kohde-tekstit {(if alikohde?
                                                       :alikohde-paallekkyys
                                                       :paallekkyys)
                                                     paallekkyydet}
                                                    (not alikohde?))))
(defn alikohteen-validointi
  [paakohde rivi taulukko]
  (let [toiset-kohteet (toiset-kohteet-fn rivi taulukko)
        vuosi @u/valittu-urakan-vuosi
        validoitu (yllapitokohteet-domain/validoi-alikohde paakohde rivi [] (get @paallystys-tiedot/tr-osien-tiedot (:tr-numero rivi)) vuosi)]
    (yllapitokohteet-domain/validoitu-kohde-tekstit (dissoc validoitu :alikohde-paallekkyys) false)))

(defn muukohteen-validointi
  [paakohde rivi taulukko]
  (let [vuosi @u/valittu-urakan-vuosi
        ;; Kohteiden päällekkyys keskenään validoidaan taulukko tasolla, jotta rivin päivittämine oikeaksi korjaa
        ;; myös toisilla riveillä olevat validoinnit.
        validoitu (yllapitokohteet-domain/validoi-muukohde paakohde rivi [] (get @paallystys-tiedot/tr-osien-tiedot (:tr-numero rivi)) vuosi)]
    (yllapitokohteet-domain/validoitu-kohde-tekstit (dissoc validoitu :muukohde-paallekkyys) false)))

(defn muukohde-toisten-kanssa-paallekkain-validointi
  [_ rivi taulukko]
  (let [toiset-kohteet (toiset-kohteet-fn rivi taulukko)
        paallekkyydet (filter #(yllapitokohteet-domain/tr-valit-paallekkain? rivi %)
                              toiset-kohteet)]
    (yllapitokohteet-domain/validoitu-kohde-tekstit {:muukohde-paallekkyys
                                                     paallekkyydet}
                                                    false)))

(defn alikohteiden-vetolaatikot
  [urakka sopimus-id kohteet-atom kohdetyyppi piilota-kohdeosat? dissoc-cols paallystyksen-tarkka-aikataulu aikataulu?]
  (into {}
        (map (juxt
               :id
               (fn [rivi]
                 [kohteen-vetolaatikko {:urakka urakka :sopimus-id sopimus-id :kohteet-atom kohteet-atom :rivi rivi
                                        :kohdetyyppi kohdetyyppi
                                        :piilota-kohdeosat? piilota-kohdeosat?
                                        :kohteen-rivi-validointi [{:fn (r/partial alikohteen-validointi (select-keys rivi tr/paaluvali-avaimet))
                                                                   :sarakkeet tr/alikohteen-tr-sarakkeet-map}]
                                        :kohteen-taulukko-validointi [{:fn (r/partial kohde-toisten-kanssa-paallekkain-validointi true)
                                                                       :sarakkeet tr/alikohteen-tr-sarakkeet-map}]
                                        :muut-kohteen-rivi-validointi [{:fn (r/partial muukohteen-validointi (select-keys rivi tr/paaluvali-avaimet))
                                                                        :sarakkeet tr/alikohteen-tr-sarakkeet-map}]
                                        :muut-kohteen-taulukko-validointi [{:fn (r/partial muukohde-toisten-kanssa-paallekkain-validointi)
                                                                            :sarakkeet tr/alikohteen-tr-sarakkeet-map}]
                                        :dissoc-cols dissoc-cols
                                        :paallystyksen-tarkka-aikataulu paallystyksen-tarkka-aikataulu
                                        :aikataulu? aikataulu?}])))
        @kohteet-atom))

(defn rivin-kohdenumero-ja-kello
  "Luo Reagent-komponentin gridin sarakkeeseen, kohdenumero ja alle viikon vanhoille riveille kelloindikaattori"
  [rivi]
  [:div.tuoreusindikaattori
   [:span (str (:kohdenumero rivi))]
   (when (yllapitokohteet-domain/muokattu-viikon-aikana? rivi)
     [yleiset/wrap-if true
      [yleiset/tooltip {} :% "Kohdetta muokattu viimeisen viikon sisään"]
      [:span.harja-icon-action-set-time]])])



(defn taulukon-ryhmittely-header
  "Ryhmittelee sarakkeet siten, että hintamuutokset eriteltyjä."
  [listaus]
  ;; Tässä ei oikein päästä kovakoodauksesta eroon, ainakaan kovin helposti
  ;; Joten ei muuta kuin speksi käteen ja tarkkana, jos muutat näitä
  [{:teksti "" :sarakkeita (case listaus
                             :yha-kohteet
                             15

                             :muut-kohteet 14

                             :yhteensa 16)
    :luokka "paallystys-tausta"}
   {:teksti "Hintamuutokset" :sarakkeita 3 :luokka "paallystys-tausta-tumma"}
   {:teksti "" :sarakkeita 2 :luokka "paallystys-tausta"}])

(defn yllapitokohteet
  "Ottaa urakan, kohteet atomin ja optiot ja luo taulukon, jossa on listattu kohteen tiedot.

  Optiot on map, jossa avaimet:
  otsikko             Taulukon otsikko
  kohdetyyppi         Minkä tyyppisiä kohteita tässä taulukossa näytetään (:paallystys tai :paikkaus. :paikkaus tarkoittaa tässä Harjassa luotuja kohteita, eikä YHA:ssa, ja niiltä puuttuu yhaid.)
  yha-sidottu?        Onko taulukon kohteet tulleet kaikki YHA:sta (vaikuttaa mm. kohteiden muokkaamiseen)
  piilota-tallennus?  True jos tallennusnappi piilotetaan
  tallenna            Funktio tallennusnapille
  kun-onnistuu        Funktio tallennuksen onnistumiselle"
  [urakka kohteet-atom {:keys [yha-sidottu? piilota-tallennus? valittu-vuosi] :as optiot}]
  (let [tr-sijainnit (atom {}) ;; onnistuneesti haetut TR-sijainnit
        tr-virheet (atom {}) ;; virheelliset TR sijainnit
        g (grid/grid-ohjaus)
        tallenna (reaction
                   (if (and @yha/yha-kohteiden-paivittaminen-kaynnissa? yha-sidottu?)
                     :ei-mahdollinen
                     (:tallenna optiot)))
        osan-pituudet-teille (atom nil)
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
      (fn [urakka kohteet-atom {:keys [yha-sidottu? piilota-tallennus? valittu-vuosi] :as optiot}]
        (let [paallystys? (= (:kohdetyyppi optiot) :paallystys)
              nayta-ajorata-ja-kaista? false ;; tehty uudistus, että pääkohteiden osalta ajorataa ja kaistaa ei enää anneta
              paallystysilmoitusta-ei-ole-lukittu? #(not= :lukittu (:paallystysilmoitus-tila %))
              validointi {:paakohde {:tr-osoite [{:fn paakohteen-validointi
                                                  :sarakkeet {:tr-numero :tr-numero
                                                              :tr-ajorata :tr-ajorata
                                                              :tr-kaista :tr-kaista
                                                              :tr-alkuosa :tr-alkuosa
                                                              :tr-alkuetaisyys :tr-alkuetaisyys
                                                              :tr-loppuosa :tr-loppuosa
                                                              :tr-loppuetaisyys :tr-loppuetaisyys}}]}}
              jarjestetyt-kohteet (yllapitokohteet-domain/jarjesta-yllapitokohteet @kohteet-atom)
              jarjestetyt-kohteet (if (and paallystys? (not-empty jarjestetyt-kohteet))
                                    (conj jarjestetyt-kohteet
                                          (yllapitokohteet-domain/kohteiden-summarivi jarjestetyt-kohteet))
                                    jarjestetyt-kohteet)]
          [:div.yllapitokohteet
           [grid/grid
            {:otsikko [:span (:otsikko optiot)
                       [vasta-muokatut-vinkki]]
             :rivi-ennen (taulukon-ryhmittely-header (if paallystys?
                                                       :yha-kohteet
                                                       :muut-kohteet))
             :ohjaus g
             :tyhja (if (nil? @kohteet-atom) [ajax-loader "Haetaan kohteita..."] "Ei kohteita")
             :vetolaatikot (alikohteiden-vetolaatikot urakka
                                                      (first @u/valittu-sopimusnumero)
                                                      kohteet-atom
                                                      (:kohdetyyppi optiot)
                                                      false
                                                      #{}
                                                      nil
                                                      false)
             :rivi-validointi (-> validointi :paakohde :tr-osoite)
             :taulukko-varoitus [] 
             :tallenna (when-not piilota-tallennus? @tallenna)
             :nollaa-muokkaustiedot-tallennuksen-jalkeen? (fn [vastaus]
                                                            (#{:ok :yha-virhe} (:status vastaus)))
             :muutos (fn [grid]
                       (hae-osien-tiedot (grid/hae-muokkaustila grid)))
             :voi-lisata? (not yha-sidottu?)
             :esta-poistaminen? (fn [rivi] (not (:yllapitokohteen-voi-poistaa? rivi)))
             :esta-poistaminen-tooltip (fn [_]
                                         (if yha-sidottu?
                                           "Kohdetta on muokattu tai sille on tehty kirjauksia."
                                           "Kohteelle on tehty kirjauksia."))}
            (into []
                  (concat
                    [{:tyyppi :vetolaatikon-tila :leveys haitari-leveys}
                     {:otsikko "Koh\u00ADde\u00ADnro" :nimi :kohdenumero
                      :tyyppi :komponentti :leveys id-leveys :muokattava? paallystysilmoitusta-ei-ole-lukittu?
                      :komponentti rivin-kohdenumero-ja-kello}
                     {:otsikko "Tun\u00ADnus" :nimi :tunnus
                      :tyyppi :string :leveys tunnus-leveys :pituus-max 1 :muokattava? paallystysilmoitusta-ei-ole-lukittu?}
                     {:otsikko "Nimi" :nimi :nimi
                      :tyyppi :string :leveys kohde-leveys
                      :pituus-max 30 :muokattava? paallystysilmoitusta-ei-ole-lukittu?}
                     {:otsikko (if paallystys? "YHA-id" "HARJA-id")
                      :nimi (if paallystys? :yhaid :id)
                      :tasaa :oikea
                      :tyyppi :string :leveys yhaid-leveys
                      :pituus-max 30 :muokattava? (constantly false)}
                     {:otsikko "Yö\u00ADtyö" :nimi :yotyo :tasaa :oikea :leveys yotyo-leveys
                      :tyyppi :checkbox :vayla-tyyli? true}]
                    (tierekisteriosoite-sarakkeet
                      tr-leveys
                      [nil ;; kohteen nimi siirretty pois tästä, jotta yha-id saadaan väliin
                       {:nimi :tr-numero :muokattava? (constantly (not yha-sidottu?))}
                       (when nayta-ajorata-ja-kaista?
                         {:nimi :tr-ajorata :muokattava? (constantly (not yha-sidottu?))})
                       (when nayta-ajorata-ja-kaista?
                         {:nimi :tr-kaista :muokattava? (constantly (not yha-sidottu?))})
                       {:nimi :tr-alkuosa :muokattava? paallystysilmoitusta-ei-ole-lukittu?}
                       {:nimi :tr-alkuetaisyys :muokattava? paallystysilmoitusta-ei-ole-lukittu?}
                       {:nimi :tr-loppuosa :muokattava? paallystysilmoitusta-ei-ole-lukittu?}
                       {:nimi :tr-loppuetaisyys :muokattava? paallystysilmoitusta-ei-ole-lukittu?}]
                      true)
                    [{:otsikko "KVL"
                      :nimi :keskimaarainen-vuorokausiliikenne :tyyppi :numero :leveys kvl-leveys
                      :muokattava? (constantly (not yha-sidottu?))}
                     (when paallystys?
                       {:otsikko "Tar\u00ADjous\u00ADhinta" :nimi :sopimuksen-mukaiset-tyot
                        :fmt fmt/euro-opt :tyyppi :numero :leveys tarjoushinta-leveys :tasaa :oikea})
                     (when paallystys?
                       (if (yllapitokohteet-domain/eritellyt-maaramuutokset-kaytossa? valittu-vuosi)
                         {:otsikko "Mää\u00ADrä\u00ADmuu\u00ADtok\u00ADset"
                          :nimi :maaramuutokset :muokattava? (constantly false)
                          :tyyppi :komponentti :leveys maaramuutokset-leveys :tasaa :oikea
                          :komponentti (fn [rivi]
                                         [:span {:class (when (:maaramuutokset-ennustettu? rivi)
                                                          "grid-solu-ennustettu")}
                                          (fmt/euro-opt (:maaramuutokset rivi))])}
                         ;; 1.1.2023 alkaen määrämuutoksia käsitellään yhtenä numerona eikä eriteltynä
                         {:otsikko "Mää\u00ADrä\u00ADmuu\u00ADtok\u00ADset" :nimi :maaramuutokset
                          :fmt fmt/euro-opt :tyyppi :numero :leveys maaramuutokset-leveys
                          :tasaa :oikea}))
                     (when (= (:kohdetyyppi optiot) :paikkaus)
                       {:otsikko "Toteutunut hinta" :nimi :toteutunut-hinta
                        :fmt fmt/euro-opt :tyyppi :numero :leveys toteutunut-hinta-leveys
                        :tasaa :oikea})
                     ;; Arvonmuutokset ja sanktiot halutaan piilottaa 2022 eteenpäin
                     (when-not (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? valittu-vuosi)
                       {:otsikko "Ar\u00ADvon muu\u00ADtok\u00ADset" :nimi :arvonvahennykset :fmt fmt/euro-opt
                        :tyyppi :numero :leveys arvonvahennykset-leveys :tasaa :oikea})
                     (when-not (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? valittu-vuosi)
                       {:otsikko "Sak\u00ADko/bo\u00ADnus" :nimi :sakot-ja-bonukset :fmt fmt/euro-opt
                        :tyyppi :numero :leveys arvonvahennykset-leveys :tasaa :oikea
                        :muokattava? (constantly false)})
                     {:otsikko "Side\u00ADaineet" :nimi :bitumi-indeksi
                      :fmt fmt/euro-opt :otsikkorivi-luokka "paallystys-tausta-tumma"
                      :tyyppi :numero :leveys bitumi-indeksi-leveys :tasaa :oikea}
                     {:otsikko "Neste\u00ADkaasu ja kevyt poltto\u00ADöljy" :nimi :kaasuindeksi :fmt fmt/euro-opt :otsikkorivi-luokka "paallystys-tausta-tumma"
                      :tyyppi :numero :leveys kaasuindeksi-leveys :tasaa :oikea}
                     {:otsikko "MAKU-päällysteet" :nimi :maku-paallysteet :fmt fmt/euro-opt
                      :otsikkorivi-luokka "paallystys-tausta-tumma"
                      :tyyppi :numero :leveys kaasuindeksi-leveys :tasaa :oikea}
                     {:otsikko "Kokonais\u00ADhinta"
                      :muokattava? (constantly false)
                      :nimi :kokonaishinta :fmt fmt/euro-opt :tyyppi :komponentti :leveys yhteensa-leveys
                      :tasaa :oikea
                      :komponentti (fn [rivi]
                                     [:span {:class (when (:maaramuutokset-ennustettu? rivi)
                                                      "grid-solu-ennustettu")}
                                      (fmt/euro-opt (yllapitokohteet-domain/yllapitokohteen-kokonaishinta rivi valittu-vuosi))])}]))
            jarjestetyt-kohteet]
           [tr-virheilmoitus tr-virheet]])))))

(defn yllapitokohteet-yhteensa [kohteet-atom optiot]
  (let [yhteensa
        (reaction
          (let [kohteet @kohteet-atom
                sopimuksen-mukaiset-tyot-yhteensa (yllapitokohteet-domain/laske-sarakkeen-summa :sopimuksen-mukaiset-tyot kohteet)
                toteutunut-hinta-yhteensa (yllapitokohteet-domain/laske-sarakkeen-summa :toteutunut-hinta kohteet)
                maaramuutokset-yhteensa (yllapitokohteet-domain/laske-sarakkeen-summa :maaramuutokset kohteet)
                arvonvahennykset-yhteensa (yllapitokohteet-domain/laske-sarakkeen-summa :arvonvahennykset kohteet)
                sakot-ja-bonukset-yhteensa (yllapitokohteet-domain/laske-sarakkeen-summa :sakot-ja-bonukset kohteet)
                bitumi-indeksi-yhteensa (yllapitokohteet-domain/laske-sarakkeen-summa :bitumi-indeksi kohteet)
                kaasuindeksi-yhteensa (yllapitokohteet-domain/laske-sarakkeen-summa :kaasuindeksi kohteet)
                maku-paallysteet-yhteensa (yllapitokohteet-domain/laske-sarakkeen-summa :maku-paallysteet kohteet)
                ;; käännetään kohdistamattomat sanktiot miinusmerkkiseksi jotta summaus toimii oikein
                kohdistamattomat-sanktiot-yhteensa (let [arvo (yllapitokohteet-domain/laske-sarakkeen-summa :summa @muut-kustannukset/kohdistamattomien-sanktioiden-tiedot)]
                                                     (if (> (Math/abs arvo) 0)
                                                       (- arvo)
                                                       arvo))
                muut-yhteensa (yllapitokohteet-domain/laske-sarakkeen-summa :hinta @muut-kustannukset/muiden-kustannusten-tiedot)
                kokonaishinta (+ (yllapitokohteet-domain/yllapitokohteen-kokonaishinta
                                   {:sopimuksen-mukaiset-tyot sopimuksen-mukaiset-tyot-yhteensa
                                    :toteutunut-hinta toteutunut-hinta-yhteensa
                                    :maaramuutokset maaramuutokset-yhteensa
                                    :arvonvahennykset arvonvahennykset-yhteensa
                                    :sakot-ja-bonukset sakot-ja-bonukset-yhteensa
                                    :bitumi-indeksi bitumi-indeksi-yhteensa
                                    :maku-paallysteet maku-paallysteet-yhteensa
                                    :kaasuindeksi kaasuindeksi-yhteensa}
                                   (:valittu-vuosi optiot))
                                 (or muut-yhteensa 0)
                                 (or kohdistamattomat-sanktiot-yhteensa 0))]
            [{:id 0
              :sopimuksen-mukaiset-tyot sopimuksen-mukaiset-tyot-yhteensa
              :maaramuutokset maaramuutokset-yhteensa
              :toteutunut-hinta toteutunut-hinta-yhteensa
              :arvonvahennykset arvonvahennykset-yhteensa
              :sakot-ja-bonukset sakot-ja-bonukset-yhteensa
              :bitumi-indeksi bitumi-indeksi-yhteensa
              :kaasuindeksi kaasuindeksi-yhteensa
              :maku-paallysteet maku-paallysteet-yhteensa
              :muut-hinta muut-yhteensa
              :kokonaishinta kokonaishinta
              :kohdistamattomat-sanktiot kohdistamattomat-sanktiot-yhteensa}]))]

    [grid/grid
     {:nayta-toimintosarake? true
      :otsikko "Yhteensä"
      :rivi-ennen (taulukon-ryhmittely-header :yhteensa)
      :tyhja (if (nil? {}) [ajax-loader "Lasketaan..."] "")}
     [{:otsikko "" :nimi :tyhja :tyyppi :string :leveys haitari-leveys}
      {:otsikko "" :nimi :kohdenumero :tyyppi :string :leveys id-leveys}
      {:otsikko "" :nimi :tunnus :tyyppi :string :leveys tunnus-leveys}
      {:otsikko "" :nimi :nimi :tyyppi :string :leveys kohde-leveys}
      {:otsikko "" :nimi :tr-numero :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :tr-alkuosa :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :tr-alkuetaisyys :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :tr-loppuosa :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :tr-loppuetaisyys :tyyppi :string :leveys tr-leveys}
      {:otsikko "" :nimi :keskimaarainen-vuorokausiliikenne :tyyppi :string :leveys kvl-leveys}
      {:otsikko "" :nimi :yllapitoluokka :tyyppi :string :leveys pk-luokka-leveys}
      {:otsikko "Toteu\u00ADtunut hinta (muut kohteet)" :nimi :toteutunut-hinta
       :fmt fmt/euro-opt :tyyppi :numero :leveys toteutunut-hinta-leveys :tasaa :oikea}
      {:otsikko (str
                  "Sakot ja bonukset"
                  (when-not (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? (:valittu-vuosi optiot))
                    " (muut kuin kohteisiin liittyvät)")) :nimi :kohdistamattomat-sanktiot :tyyppi :numero :leveys toteutunut-hinta-leveys
       :fmt fmt/euro-opt :tasaa :oikea}
      {:otsikko "Muut kustan\u00ADnukset" :nimi :muut-hinta :fmt fmt/euro-opt :tyyppi :numero
       :leveys muut-leveys :tasaa :oikea}
      {:otsikko "Tarjous\u00ADhinta" :nimi :sopimuksen-mukaiset-tyot
       :fmt fmt/euro-opt :tyyppi :numero
       :leveys tarjoushinta-leveys :tasaa :oikea}
      {:otsikko "Määrä\u00ADmuutok\u00ADset" :nimi :maaramuutokset :fmt fmt/euro-opt :tyyppi :numero
       :leveys maaramuutokset-leveys :tasaa :oikea}
      (when-not (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? (:valittu-vuosi optiot))
        {:otsikko "Arvon\u00ADmuutokset." :nimi :arvonvahennykset :fmt fmt/euro-opt :tyyppi :numero
         :leveys arvonvahennykset-leveys :tasaa :oikea})
      (when-not (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? (:valittu-vuosi optiot))
        {:otsikko "Sak\u00ADko/bo\u00ADnus" :nimi :sakot-ja-bonukset :fmt fmt/euro-opt
         :tyyppi :numero :leveys arvonvahennykset-leveys :tasaa :oikea
         :muokattava? (constantly false)})
      {:otsikko "Side\u00ADaineet" :nimi :bitumi-indeksi :fmt fmt/euro-opt :tyyppi :numero
       :leveys bitumi-indeksi-leveys :tasaa :oikea :otsikkorivi-luokka "paallystys-tausta-tumma"}
      {:otsikko "Neste\u00ADkaasu ja kevyt poltto\u00ADöljy" :nimi :kaasuindeksi :fmt fmt/euro-opt :tyyppi :numero
       :leveys kaasuindeksi-leveys :tasaa :oikea :otsikkorivi-luokka "paallystys-tausta-tumma"}
      {:otsikko "MAKU-päällys\u00ADteet" :nimi :maku-paallysteet :fmt fmt/euro-opt :tyyppi :numero
       :leveys maku-paallysteet-leveys :tasaa :oikea :otsikkorivi-luokka "paallystys-tausta-tumma"}
      {:otsikko "Kokonais\u00ADhinta" :nimi :kokonaishinta
       :tyyppi :komponentti :leveys yhteensa-leveys :tasaa :oikea
       :komponentti
       (fn [rivi]
         [:span {:class (when (some :maaramuutokset-ennustettu? @kohteet-atom)
                          "grid-solu-ennustettu")}
          (fmt/euro-opt (:kokonaishinta rivi))])}]
     @yhteensa]))
