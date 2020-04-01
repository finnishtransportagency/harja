(ns harja.views.urakka.paikkaukset-toteumat
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.tiedot.urakka.paikkaukset-toteumat :as tiedot]
            [harja.tiedot.urakka.paikkaukset-yhteinen :as yhteiset-tiedot]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [harja.views.kartta :as kartta]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.views.urakka.paikkaukset-yhteinen :as yhteinen-view]
            [harja.ui.debug :as debug]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as yleiset]
            [cljs.core.async :refer [<! timeout]]
            [taoensso.timbre :as log]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.ui.modal :as modal])
  (:require-macros [cljs.core.async.macros :refer [go]]))



(defn ilmoita-virheesta-modal
  "Modaali, jossa kerrotaan paikkaustoteumassa olevasta virheestä."
  [e! {:keys [kohde-id urakka-id kohde-nimi lomakedata nakyvissa?] :as kohde}]
  [modal/modal
   {:otsikko (str "Lähetä sähköposti")
    :luokka "ilmoita-virheesta-modal"
    :nakyvissa? true
    :sulje-fn #(e! (tiedot/->SuljeVirheModal))
    :footer [:div
             [napit/peruuta
              "Peruuta"
              #(e! (tiedot/->SuljeVirheModal))]

             [napit/palvelinkutsu-nappi
              "Ilmoita"
              #(tiedot/->LahetaIlmoitusVirheesta
                 {:kohde-id kohde-id
                  :kohde-nimi kohde-nimi
                  :kopio-itselle? (:kopio-itselle? lomakedata)
                  :saate (:saate lomakedata)
                  :urakka-id urakka-id})
              {:disabled false
               :luokka "nappi-myonteinen"
               :ikoni (ikonit/check)
               :kun-onnistuu (fn [vastaus]
                               (log "Homma rokkasi, kerrotaan jotenkin käyttäjälle.")
                               #(e! (tiedot/->SuljeVirheModal)))}]]}])


(defn paikkaukset-vetolaatikko
  [e! {tienkohdat ::paikkaus/tienkohdat materiaalit ::paikkaus/materiaalit id ::paikkaus/id :as rivi}
   app]
  (let [nayta-numerot #(apply str (interpose ", " %))
        tienkohdat-skeema [{:otsikko "Ajo\u00ADrata"
                            :leveys 1
                            :nimi ::paikkaus/ajorata}
                           {:otsikko "Reu\u00ADnat"
                            :leveys 1
                            :nimi ::paikkaus/reunat
                            :fmt nayta-numerot}
                           {:otsikko "Ajo\u00ADurat"
                            :leveys 1
                            :nimi ::paikkaus/ajourat
                            :fmt nayta-numerot}
                           {:otsikko "Ajoura\u00ADvälit"
                            :leveys 1
                            :nimi ::paikkaus/ajouravalit
                            :fmt nayta-numerot}
                           {:otsikko "Kes\u00ADkisau\u00ADmat"
                            :leveys 1
                            :nimi ::paikkaus/keskisaumat
                            :fmt nayta-numerot}]
        materiaalit-skeema [{:otsikko "Esiin\u00ADtymä"
                             :leveys 1
                             :nimi ::paikkaus/esiintyma}
                            {:otsikko "Kuu\u00ADlamyl\u00ADly\u00ADarvo"
                             :leveys 2
                             :nimi ::paikkaus/kuulamylly-arvo}
                            {:otsikko "Muoto\u00ADarvo"
                             :leveys 2
                             :nimi ::paikkaus/muotoarvo}
                            {:otsikko "Side\u00ADaine\u00ADtyyp\u00ADpi"
                             :leveys 2
                             :nimi ::paikkaus/sideainetyyppi}
                            {:otsikko "Pitoi\u00ADsuus"
                             :leveys 1
                             :nimi ::paikkaus/pitoisuus}
                            {:otsikko "Lisä\u00ADaineet"
                             :leveys 2
                             :nimi ::paikkaus/lisa-aineet}]]
    [:div
     [grid/grid
      {:otsikko "Tienkohdat"
       :tunniste ::paikkaus/tienkohta-id
       :sivuta grid/vakiosivutus
       :tyhja "Ei tietoja"}
      tienkohdat-skeema
      tienkohdat]
     [grid/grid
      {:otsikko "Materiaalit"
       :tunniste ::paikkaus/materiaali-id
       :sivuta grid/vakiosivutus
       :tyhja "Ei tietoja"}
      materiaalit-skeema
      materiaalit]]))

(defn paikkaukset [e! app]
  (let [tierekisteriosoite-sarakkeet [nil
                                      {:nimi ::tierekisteri/tie}
                                      nil nil
                                      {:nimi ::tierekisteri/aosa}
                                      {:nimi ::tierekisteri/aet}
                                      {:nimi ::tierekisteri/losa}
                                      {:nimi ::tierekisteri/let}
                                      {:nimi :suirun-pituus}]
        desimaalien-maara 2
        skeema (into []
                     (concat
                       [{:tyyppi :vetolaatikon-tila :leveys 1}]
                       (yllapitokohteet/tierekisteriosoite-sarakkeet 5 tierekisteriosoite-sarakkeet)
                       [{:otsikko "Alku\u00ADaika"
                         :leveys 10
                         :nimi ::paikkaus/alkuaika
                         :fmt yhteinen-view/aika-formatteri}
                        {:otsikko "Loppu\u00ADaika"
                         :leveys 10
                         :nimi ::paikkaus/loppuaika
                         :fmt yhteinen-view/aika-formatteri}
                        {:otsikko "Työ\u00ADmene\u00ADtelmä"
                         :leveys 10
                         :nimi ::paikkaus/tyomenetelma}
                        {:otsikko "Massa\u00ADtyyp\u00ADpi"
                         :leveys 10
                         :nimi ::paikkaus/massatyyppi}
                        {:otsikko "Leveys\u00AD (m)"
                         :leveys 5
                         :nimi ::paikkaus/leveys}
                        {:otsikko "Pinta-ala\u00AD (m\u00B2)"
                         :leveys 5
                         :fmt #(fmt/desimaaliluku-opt % desimaalien-maara)
                         :nimi :suirun-pinta-ala}
                        {:otsikko "Massa\u00ADmenek\u00ADki"
                         :leveys 5
                         :nimi ::paikkaus/massamenekki}
                        {:otsikko "Raekoko"
                         :leveys 5
                         :nimi ::paikkaus/raekoko}
                        {:otsikko "Kuula\u00ADmylly"
                         :leveys 5
                         :nimi ::paikkaus/kuulamylly}]))]
    (fn [e! {:keys [paikkauksien-haku-kaynnissa? paikkauksien-haku-tulee-olemaan-kaynnissa?
                    paikkaukset-grid paikkauket-vetolaatikko] :as app}]
      [:div
       [grid/grid
        {:otsikko (if (or paikkauksien-haku-kaynnissa? paikkauksien-haku-tulee-olemaan-kaynnissa?)
                    [yleiset/ajax-loader-pieni "Päivitetään listaa.."]
                    "Paikkauksien toteumat")
         :salli-valiotsikoiden-piilotus? true
         :valiotsikon-komponentti-flowhun? true
         :tunniste ::paikkaus/id
         :sivuta 100
         :tyhja (if paikkauksien-haku-kaynnissa?
                  [yleiset/ajax-loader "Haku käynnissä"]
                  "Ei paikkauksia")
         :vetolaatikot
         (into {}
               (map (juxt
                      ::paikkaus/id
                      (fn [rivi]
                        [paikkaukset-vetolaatikko e! rivi app])))
               paikkauket-vetolaatikko)}
        skeema
        paikkaukset-grid]])))

(def ohje-teksti-tarkistus-ja-yha
  "Tarkista toteumat ja valitse Merkitse tarkistetuksi, jolloin tiettyjen työmenetelmien tiedot lähtevät YHA:an. Jos tiedoissa on virheitä, valitse Ilmoita virhe.")

(defn otsikkokomponentti
  [e! paikkaukset]
  (let [paikkaus (first paikkaukset)
        paikkauskohde-id (get-in paikkaus
                                 [::paikkaus/paikkauskohde ::paikkaus/id])
        alkupvm (get paikkaus ::paikkaus/alkuaika)
        loppupvm (get paikkaus ::paikkaus/loppuaika)
        pinta-ala-sum (->> paikkaukset
                           (map :suirun-pinta-ala)
                           (reduce +))
        massamenekki-sum (->> paikkaukset
                              (map ::paikkaus/massamenekki)
                              (reduce +))
        ]
    [{:tyyli {}
      :sisalto
      (fn [_]
        [:<>
         [:tr
          [napit/yleinen-ensisijainen "Ilmoita virhe"
           #(tiedot/->AvaaVirheModal paikkaus)]]
         [:tr.valiotsikko

          [:td.tasaa-oikealle {:colSpan 2}
           [:span.bold (str "Yhteensä: " (count paikkaukset))]]
          [:td {:colSpan 5}]
          [:td {:colSpan 1}
           [:span.bold (when alkupvm
                         (clojure.string/replace (pvm/pvm-aika alkupvm) " " " klo "))]] ; replacella "klo" päivämäärän ja ajan väliin
          [:td {:colSpan 1}
           [:span.bold (when loppupvm
                         (clojure.string/replace (pvm/pvm-aika loppupvm) " " " klo "))]]
          [:td {:colSpan 3}]
          [:td
           [:span.bold (* 0.01 (Math/round (* 100 (float pinta-ala-sum))))]]
          [:td
           [:span.bold (* 0.01 (Math/round (* 100 (float massamenekki-sum))))]]
          [:td {:colSpan 2}
           [:div {:style {:float "right"}}
            [:a.livicon-arrow-right {:style {:float "right"}
                                     :href "#"
                                     :on-click #(do
                                                  (.preventDefault %)
                                                  (e! (tiedot/->SiirryKustannuksiin paikkauskohde-id)))}
             "Kustannukset"]]]]])}]))

(defn toteumat* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymaan))
                           (reset! tiedot/taso-nakyvissa? true))
                      #(do (e! (tiedot/->NakymastaPois))
                           (reset! tiedot/taso-nakyvissa? false)))
    ;; Syy, että miksi tämmöinen erillinen otsikoiden lisäysfunktio on tässä sen sijaan, että
    ;; tuo otsikko laitettaisiin samantein paikkallensa tiedot/kasittele-haettu-tulos funktiossa
    ;; niinkuin se tehdään kustannusten puolella on se, että harja.ui.grid ns:n requiraaminen
    ;; tiedot ns:ssa aiheuttaa circular dependencyn.
    (komp/kun-muuttuu (fn [e! {haettu-uudet-paikkaukset? :haettu-uudet-paikkaukset?}]
                        (when haettu-uudet-paikkaukset?
                          (e! (tiedot/->LisaaOtsikotGridiin (fn [[otsikko paikkaukset]]
                                                              (cons (grid/otsikko otsikko {:otsikkokomponentit (otsikkokomponentti e! paikkaukset)})
                                                                    (sort-by (juxt ::tierekisteri/tie ::tierekisteri/aosa ::tierekisteri/aet ::tierekisteri/losa ::tierekisteri/let)
                                                                             paikkaukset))))))))
    (fn [e! app]
      [:span
       [kartta/kartan-paikka]
       [:div
        [debug/debug app]
        [yhteinen-view/hakuehdot
         {:nakyma :toteumat
          :palvelukutsu-onnistui-fn #(e! (tiedot/->PaikkauksetHaettu %))}]
        (when (:modalin-paikkaus app)
          [ilmoita-virheesta-modal e! (:modalin-paikkaus app)])
        [paikkaukset e! app]]])))

(defn toteumat [ur]
  (komp/luo
    (komp/sisaan #(yhteiset-tiedot/nakyman-urakka ur))
    (fn [_]
      [tuck/tuck tiedot/app toteumat*])))
