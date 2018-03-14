(ns harja.views.urakka.yllapitokohteet
  "Ylläpitokohteet"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko vihje]]
            [harja.ui.komponentti :as komp]
            [harja.fmt :as fmt]
            [harja.loki :refer [log logt tarkkaile!]]
            [clojure.string :as str]
            [cljs.core.async :refer [<!]]
            [harja.tyokalut.vkm :as vkm]
            [harja.domain.tierekisteri :as tr]
            [harja.domain.paallystysilmoitus :as pot]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [harja.tiedot.urakka.yhatuonti :as yha]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.yllapitokohteet :as tiedot]
            [harja.tiedot.urakka :as u]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka :as urakka]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.validointi :as validointi]
            [harja.atom :refer [wrap-vain-luku]]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.tiedot.urakka.paallystys :as paallystys-tiedot]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.kentat :as kentat])
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

(defn alkuosa-ei-lopun-jalkeen [aosa {losa :tr-loppuosa}]
  (when (and aosa losa (> aosa losa))
    "Al\u00ADku\u00ADo\u00ADsa ei voi olla lop\u00ADpu\u00ADo\u00ADsan jäl\u00ADkeen"))

(defn alkuetaisyys-ei-lopun-jalkeen [alkuet {aosa :tr-alkuosa
                                             losa :tr-loppuosa
                                             loppuet :tr-loppuetaisyys}]
  (when (and aosa losa alkuet loppuet
             (= aosa losa)
             (> alkuet loppuet))
    "Alku\u00ADe\u00ADtäi\u00ADsyys ei voi olla lop\u00ADpu\u00ADe\u00ADtäi\u00ADsyy\u00ADden jäl\u00ADkeen"))

(defn loppuosa-ei-alkua-ennen [losa {aosa :tr-alkuosa}]
  (when (and aosa losa (< losa aosa))
    "Lop\u00ADpu\u00ADosa ei voi olla al\u00ADku\u00ADo\u00ADsaa ennen"))

(defn loppuetaisyys-ei-alkua-ennen [loppuet {aosa :tr-alkuosa
                                             losa :tr-loppuosa
                                             alkuet :tr-alkuetaisyys}]
  (when (and aosa losa alkuet loppuet
             (= aosa losa)
             (< loppuet alkuet))
    "Lop\u00ADpu\u00ADe\u00ADtäi\u00ADsyys ei voi olla enn\u00ADen al\u00ADku\u00ADe\u00ADtäi\u00ADsyyt\u00ADtä"))

(defn tierekisteriosoite-sarakkeet
  "Perusleveys on leveys, jota kentille käytetään, ellei määritetä kenttäkohtaista leveyttä.
   Nimi-kenttä on kaksi kertaa perusleveys, ellei määritetä kenttäkohtaista leveyttä."
  [perusleveys
   [nimi tie ajorata kaista aosa aet losa let pituus]]
  (into []
        (remove
          nil?
          [(when nimi {:otsikko "Nimi" :nimi (:nimi nimi) :tyyppi :string
                       :leveys (or (:leveys nimi) (* perusleveys 2))
                       :pituus-max 30
                       :sisalto-kun-rivi-disabloitu (:sisalto-kun-rivi-disabloitu nimi)
                       :muokattava? (or (:muokattava? nimi) (constantly true))})
           {:otsikko "Tie\u00ADnu\u00ADme\u00ADro" :nimi (:nimi tie)
            :tyyppi :positiivinen-numero :leveys perusleveys :tasaa :oikea
            :validoi [[:ei-tyhja "Anna tienumero"]]
            :kokonaisluku? true
            :muokattava? (or (:muokattava? tie) (constantly true))}
           (when ajorata
             {:otsikko "Ajo\u00ADrata"
              :nimi (:nimi ajorata)
              :muokattava? (or (:muokattava? ajorata) (constantly true))
              :tyyppi :valinta
              :tasaa :oikea
              :valinta-arvo :koodi
              :fmt #(pot/arvo-koodilla pot/+ajoradat-numerona+ %)
              :valinta-nayta (fn [arvo muokattava?]
                               (if arvo (:nimi arvo) (if muokattava?
                                                       "- Ajorata -"
                                                       "")))
              :valinnat pot/+ajoradat-numerona+
              :leveys perusleveys})
           (when kaista
             {:otsikko "Kais\u00ADta"
              :muokattava? (or (:muokattava? kaista) (constantly true))
              :nimi (:nimi kaista)
              :tyyppi :valinta
              :tasaa :oikea
              :valinta-arvo :koodi
              :fmt #(pot/arvo-koodilla pot/+kaistat+ %)
              :valinta-nayta (fn [arvo muokattava?]
                               (if arvo (:nimi arvo) (if muokattava?
                                                       "- Kaista -"
                                                       "")))
              :valinnat pot/+kaistat+
              :leveys perusleveys})
           {:otsikko "Aosa" :nimi (:nimi aosa) :leveys perusleveys :tyyppi :positiivinen-numero
            :tasaa :oikea :kokonaisluku? true
            :validoi (into [[:ei-tyhja "An\u00ADna al\u00ADku\u00ADo\u00ADsa"]
                            alkuosa-ei-lopun-jalkeen]
                           (:validoi aosa))
            :salli-muokkaus-rivin-ollessa-disabloituna? (:salli-muokkaus-rivin-ollessa-disabloituna? aosa)
            :muokattava? (or (:muokattava? aosa) (constantly true))}
           {:otsikko "Aet" :nimi (:nimi aet) :leveys perusleveys :tyyppi :positiivinen-numero
            :tasaa :oikea :kokonaisluku? true
            :validoi (into [[:ei-tyhja "An\u00ADna al\u00ADku\u00ADe\u00ADtäi\u00ADsyys"]
                            alkuetaisyys-ei-lopun-jalkeen]
                           (:validoi aet))
            :salli-muokkaus-rivin-ollessa-disabloituna? (:salli-muokkaus-rivin-ollessa-disabloituna? aet)
            :muokattava? (or (:muokattava? aet) (constantly true))}
           {:otsikko "Losa" :nimi (:nimi losa) :leveys perusleveys :tyyppi :positiivinen-numero
            :tasaa :oikea :kokonaisluku? true
            :validoi (into [[:ei-tyhja "An\u00ADna lop\u00ADpu\u00ADo\u00ADsa"]
                            loppuosa-ei-alkua-ennen]
                           (:validoi losa))
            :salli-muokkaus-rivin-ollessa-disabloituna? (:salli-muokkaus-rivin-ollessa-disabloituna? losa)
            :muokattava? (or (:muokattava? losa) (constantly true))}
           {:otsikko "Let" :nimi (:nimi let) :leveys perusleveys :tyyppi :positiivinen-numero
            :tasaa :oikea :kokonaisluku? true
            :validoi (into [[:ei-tyhja "An\u00ADna lop\u00ADpu\u00ADe\u00ADtäi\u00ADsyys"]
                            loppuetaisyys-ei-alkua-ennen]
                           (:validoi let))
            :salli-muokkaus-rivin-ollessa-disabloituna? (:salli-muokkaus-rivin-ollessa-disabloituna? let)
            :muokattava? (or (:muokattava? let) (constantly true))}
           (merge
             {:otsikko "Pit. (m)" :nimi :pituus :leveys perusleveys :tyyppi :numero :tasaa :oikea
              :muokattava? (constantly false)}
             pituus)])))

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

(defn- validoi-osan-maksimipituus [osan-pituus key pituus rivi]
  (when (integer? pituus)
    (let [osa (get rivi key)]
      (when-let [pit (get osan-pituus osa)]
        (when (> pituus pit)
          (str "Osan " osa " maksimietäisyys on " pit))))))

(defn validoi-yllapitokohteen-osoite
  [osan-pituudet-teille kentta _ {:keys [tr-numero tr-alkuosa tr-alkuetaisyys
                                         tr-loppuosa tr-loppuetaisyys] :as kohde}]
  (when osan-pituudet-teille
    (let [osan-pituudet (osan-pituudet-teille tr-numero)]
      (or
        (cond
          (and (= kentta :tr-alkuosa) (not (contains? osan-pituudet tr-alkuosa)))
          (str "Tiellä " tr-numero " ei ole osaa " tr-alkuosa)

          (and (= kentta :tr-loppuosa) (not (contains? osan-pituudet tr-loppuosa)))
          (str "Tiellä " tr-numero " ei ole osaa " tr-loppuosa))

        (when (= kentta :tr-alkuetaisyys)
          (validoi-osan-maksimipituus osan-pituudet :tr-alkuosa tr-alkuetaisyys kohde))

        (when (= kentta :tr-loppuetaisyys)
          (validoi-osan-maksimipituus osan-pituudet :tr-loppuosa tr-loppuetaisyys kohde))))))


(defn- aseta-uudet-kohdeosat [kohteet id kohdeosat]
  (let [kohteet (vec kohteet)
        rivi (some #(when (= (:id (nth kohteet %))
                             id)
                      %)
                   (range 0 (count kohteet)))]
    (if rivi
      (assoc-in kohteet [rivi :kohdeosat] kohdeosat)
      kohteet)))

(defn yllapitokohdeosat-kohteelle [yllapitokohde]
  (let [kohdeosat (:kohdeosat yllapitokohde)]
    [grid/grid
     {:otsikko "Tierekisterikohteet"
      :tallenna #(log "TALLENNA!")}
     (remove nil? (concat
                    (tierekisteriosoite-sarakkeet
                      tr-leveys
                      [{:nimi :nimi :pituus-max 30 :leveys kohde-leveys}
                       {:nimi :tr-numero}
                       {:nimi :tr-ajorata}
                       {:nimi :tr-kaista}
                       {:nimi :tr-alkuosa}
                       {:nimi :tr-alkuetaisyys}
                       {:nimi :tr-loppuosa}
                       {:nimi :tr-loppuetaisyys}])
                    [(assoc paallystys-tiedot/paallyste-grid-skeema
                       :leveys paallyste-leveys
                       :tayta-alas? #(not (nil? %))
                       :tayta-fn (fn [lahtorivi tama-rivi]
                                   (assoc tama-rivi :paallystetyyppi (:paallystetyyppi lahtorivi)))
                       :tayta-sijainti :ylos
                       :tayta-tooltip "Kopioi sama päällystetyyppi alla oleville riveille"
                       :tayta-alas-toistuvasti? #(not (nil? %))
                       :tayta-toistuvasti-fn
                       (fn [toistettava-rivi tama-rivi]
                         (assoc tama-rivi :paallystetyyppi (:paallystetyyppi toistettava-rivi))))
                     (assoc paallystys-tiedot/raekoko-grid-skeema
                       :leveys raekoko-leveys
                       :tayta-alas? #(not (nil? %))
                       :tayta-fn (fn [lahtorivi tama-rivi]
                                   (assoc tama-rivi :raekoko (:raekoko lahtorivi)))
                       :tayta-sijainti :ylos
                       :tayta-tooltip "Kopioi sama raekoko alla oleville riveille"
                       :tayta-alas-toistuvasti? #(not (nil? %))
                       :tayta-toistuvasti-fn
                       (fn [toistettava-rivi tama-rivi]
                         (assoc tama-rivi :raekoko (:raekoko toistettava-rivi))))
                     (assoc paallystys-tiedot/tyomenetelma-grid-skeema
                       :leveys tyomenetelma-leveys
                       :tayta-alas? #(not (nil? %))
                       :tayta-fn (fn [lahtorivi tama-rivi]
                                   (assoc tama-rivi :tyomenetelma (:tyomenetelma lahtorivi)))
                       :tayta-sijainti :ylos
                       :tayta-tooltip "Kopioi sama työmenetelmä alla oleville riveille"
                       :tayta-alas-toistuvasti? #(not (nil? %))
                       :tayta-toistuvasti-fn
                       (fn [toistettava-rivi tama-rivi]
                         (assoc tama-rivi :tyomenetelma (:tyomenetelma toistettava-rivi))))
                     {:otsikko "Massa\u00ADmäärä (kg/m²)" :nimi :massamaara
                      :tyyppi :positiivinen-numero :tasaa :oikea :leveys massamaara-leveys
                      :tayta-alas? #(not (nil? %))
                      :tayta-fn (fn [lahtorivi tama-rivi]
                                  (assoc tama-rivi :massamaara (:massamaara lahtorivi)))
                      :tayta-sijainti :ylos
                      :tayta-tooltip "Kopioi sama massamäärä alla oleville riveille"
                      :tayta-alas-toistuvasti? #(not (nil? %))
                      :tayta-toistuvasti-fn
                      (fn [toistettava-rivi tama-rivi]
                        (assoc tama-rivi :massamaara (:massamaara toistettava-rivi)))}
                     {:otsikko "Toimenpiteen selitys" :nimi :toimenpide :tyyppi :string
                      :leveys toimenpide-leveys
                      :tayta-alas? #(not (nil? %))
                      :tayta-fn (fn [lahtorivi tama-rivi]
                                  (assoc tama-rivi :toimenpide (:toimenpide lahtorivi)))
                      :tayta-sijainti :ylos
                      :tayta-tooltip "Kopioi sama selitys alla oleville riveille"
                      :tayta-alas-toistuvasti? #(not (nil? %))
                      :tayta-toistuvasti-fn
                      (fn [toistettava-rivi tama-rivi]
                        (assoc tama-rivi :toimenpide (:toimenpide toistettava-rivi)))}]))
     kohdeosat]))

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

(defn kohteen-vetolaatikko [urakka kohteet-atom rivi kohdetyyppi]
  [:div
   [yllapitokohdeosat-kohteelle rivi]
   (when (= kohdetyyppi :paallystys)
     [maaramuutokset {:yllapitokohde-id (:id rivi)
                      :urakka-id (:id urakka)
                      :yllapitokohteet-atom kohteet-atom}])])

(defn hae-osan-pituudet [grid osan-pituudet-teille-atom]
  (let [tiet (into #{} (map (comp :tr-numero second)) (grid/hae-muokkaustila grid))]
    (doseq [tie tiet :when (not (contains? @osan-pituudet-teille-atom tie))]
      (go
        (let [pituudet (<! (vkm/tieosien-pituudet tie))]
          (log "Haettu osat tielle " tie ", vastaus: " (pr-str pituudet))
          (swap! osan-pituudet-teille-atom assoc tie pituudet))))))


(defn- vasta-muokatut-lihavoitu []
  [yleiset/vihje "Viikon sisällä muokatut lihavoitu" "inline-block bold pull-right"])

(defn yllapitokohteet
  "Ottaa urakan, kohteet atomin ja optiot ja luo taulukon, jossa on listattu kohteen tiedot.

  Optiot on map, jossa avaimet:
  otsikko           Taulukon otsikko
  kohdetyyppi       Minkä tyyppisiä kohteita tässä taulukossa näytetään (:paallystys tai :paikkaus)
  yha-sidottu?      Onko taulukon kohteet tulleet kaikki YHA:sta (vaikuttaa mm. kohteiden muokkaamiseen)
  tallenna          Funktio tallennusnapille
  kun-onnistuu      Funktio tallennuksen onnistumiselle"
  [urakka kohteet-atom {:keys [yha-sidottu?] :as optiot}]
  (let [tr-sijainnit (atom {}) ;; onnistuneesti haetut TR-sijainnit
        tr-virheet (atom {}) ;; virheelliset TR sijainnit
        tallenna (reaction
                   (if (and @yha/yha-kohteiden-paivittaminen-kaynnissa? yha-sidottu?)
                     :ei-mahdollinen
                     (:tallenna optiot)))
        osan-pituudet-teille (atom nil)
        validoi-kohteen-osoite (fn [kentta arvo rivi]
                                 (validoi-yllapitokohteen-osoite @osan-pituudet-teille kentta arvo rivi))]
    (komp/luo
      (fn [urakka kohteet-atom {:keys [yha-sidottu?] :as optiot}]
        (let [nayta-ajorata-ja-kaista? (or (not yha-sidottu?)
                                           ;; YHA-kohteille näytetään ajorata ja kaista vain siinä tapauksessa, että
                                           ;; ainakin yhdellä kohteella ne on annettu
                                           ;; Näitä ei voi muokata itse, joten turha näyttää aina tyhjiä sarakkeita.
                                           (and yha-sidottu?
                                                (some #(or (:tr-ajorata %) (:tr-kaista %)) @kohteet-atom)))]
          [:div.yllapitokohteet
           [grid/grid
            {:otsikko [:span (:otsikko optiot)
                       [vasta-muokatut-lihavoitu]]
             :tyhja (if (nil? @kohteet-atom) [ajax-loader "Haetaan kohteita..."] "Ei kohteita")
             :vetolaatikot
             (into {}
                   (map (juxt
                          :id
                          (fn [rivi]
                            [kohteen-vetolaatikko urakka kohteet-atom rivi (:kohdetyyppi optiot)])))
                   @kohteet-atom)
             :tallenna @tallenna
             :nollaa-muokkaustiedot-tallennuksen-jalkeen? (fn [vastaus]
                                                            (= (:status vastaus) :ok))
             :muutos (fn [grid]
                       (hae-osan-pituudet grid osan-pituudet-teille)
                       (validoi-tr-osoite grid tr-sijainnit tr-virheet))
             :voi-lisata? (not yha-sidottu?)
             :esta-poistaminen? (fn [rivi] (not (:yllapitokohteen-voi-poistaa? rivi)))
             :esta-poistaminen-tooltip
             (fn [_]
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
                       {:nimi :tr-alkuosa :validoi [(partial validoi-kohteen-osoite :tr-alkuosa)]}
                       {:nimi :tr-alkuetaisyys :validoi [(partial validoi-kohteen-osoite :tr-alkuetaisyys)]}
                       {:nimi :tr-loppuosa :validoi [(partial validoi-kohteen-osoite :tr-loppuosa)]}
                       {:nimi :tr-loppuetaisyys :validoi [(partial validoi-kohteen-osoite :tr-loppuetaisyys)]}])
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
