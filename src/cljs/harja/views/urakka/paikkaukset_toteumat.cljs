(ns harja.views.urakka.paikkaukset-toteumat
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.tiedot.urakka.paikkaukset-toteumat :as tiedot]
            [harja.views.kartta :as kartta]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as yleiset]))

(defn hakuehdot [e! {:keys [urakan-paikkauskohteet] :as app}]
  (let [tr-atomi (partial tiedot/valinta-wrap e! app)
        aikavali-atomi (partial tiedot/valinta-wrap e! app)]
    [:span
     [kentat/tee-otsikollinen-kentta
      {:otsikko "Tierekisteriosoite"
       :kentta-params {:tyyppi :tierekisteriosoite
                       :pakollinen? true
                       #_#_:validoi [(fn [osoite {sijainti :sijainti}]
                                       (when (and (tr-osoite-taytetty? osoite)
                                                  (nil? sijainti))
                                         "Tarkista tierekisteriosoite"))]}
       :arvo-atom (tr-atomi :tr)
       :tyylit {:width "fit-content"}}]
     [valinnat/aikavali (aikavali-atomi :aikavali)]
     [:span.label-ja-kentta
      [:span.kentan-otsikko "Paikkauskohteet"]
      [:div.kentta
       [yleiset/livi-pudotusvalikko
        {:naytettava-arvo (let [valittujen-paikkauskohteiden-maara (count (filter :valittu? urakan-paikkauskohteet))]
                            (str valittujen-paikkauskohteiden-maara (if (= 1 valittujen-paikkauskohteiden-maara)
                                                              " paikkauskohde valittu"
                                                              " paikkauskohdetta valittu")))
         :itemit-komponentteja? true}
        (mapv (fn [paikkauskohde]
                [:label {:on-click #(.stopPropagation %)}
                 (:nimi paikkauskohde)
                 [:input {:style {:display "inline-block"
                                  :float "right"}
                          :type "checkbox"
                          :checked (:valittu? paikkauskohde)
                          :on-change #(let [valittu? (-> % .-target .-checked)]
                                        (e! (tiedot/->UrakkaValittu paikkauskohde valittu?)))}]])
              urakan-paikkauskohteet)]]]]))
;:alkuetaisyys
;:alkuosa
;:loppuetaisyys
;:loppuosa
;:numero

(defn paikkaukset [e! app]
  (let [tierekisteriosoite-sarakkeet [{:nimi :nimi :pituus-max 30}
                                      {:nimi :tr-numero}
                                      {:nimi :tr-ajorata}
                                      {:nimi :tr-kaista}
                                      {:nimi :tr-alkuosa}
                                      {:nimi :tr-alkuetaisyys}
                                      {:nimi :tr-loppuosa}
                                      {:nimi :tr-loppuetaisyys}]
        skeema (into []
                     (concat
                       (yllapitokohteet/tierekisteriosoite-sarakkeet 8 tierekisteriosoite-sarakkeet)
                       [{:otsikko "Alku\u00ADaika"
                         :leveys 3
                         ;:nimi ::lt/kohde
                         ;:fmt kohde/fmt-kohteen-nimi
                         }
                        {:otsikko "Loppu\u00ADaika"
                         :leveys 3
                         ;:nimi :toimenpide
                         ;:hae tiedot/toimenpide->str
                         }
                        {:otsikko "Työ\u00ADmene\u00ADtelmä"
                         :leveys 1
                         ;:nimi :sillan-avaus?
                         ;:hae tiedot/silta-avattu?
                         ;:fmt totuus-ikoni
                         }
                        {:otsikko "Pal\u00ADvelu\u00ADmuoto"
                         :leveys 3
                         ;:nimi :palvelumuoto-ja-lkm
                         ;:hae tiedot/palvelumuoto->str
                         }
                        {:otsikko "Massa\u00ADtyyp\u00ADpi"
                         :leveys 2
                         ;:nimi ::lt-alus/suunta
                         ;:fmt lt/suunta->str
                         }
                        {:otsikko "Leveys"
                         :leveys 3
                         ;:nimi ::lt-alus/nimi
                         }
                        {:otsikko "Massa\u00ADmenek\u00ADki"
                         :leveys 2
                         ;:nimi ::lt-alus/laji
                         ;:fmt lt-alus/aluslaji->laji-str
                         }
                        {:otsikko "Raekoko"
                         :leveys 1
                         ;:nimi ::lt-alus/lkm
                         }
                        {:otsikko "Kuula\u00ADmylly"
                         :leveys 1
                         ;:nimi ::lt-alus/matkustajalkm
                         }
                        {:otsikko "Paik\u00ADkaus\u00ADkoh\u00ADteen nimi"
                         :leveys 1
                         ;:nimi ::lt-alus/nippulkm
                         }]))]
    (fn [e! {:keys [paikkauksien-haku-kaynnissa? paikkaukset]}]
      [:div
       [grid/grid
        {:otsikko (if paikkauksien-haku-kaynnissa?
                    [yleiset/ajax-loader-pieni "Päivitetään listaa.."]
                    "Paikkauksien toteumat")
         :sivuta grid/vakiosivutus
         :tyhja (if paikkauksien-haku-kaynnissa?
                  [yleiset/ajax-loader "Haku käynnissä"]
                  "Ei paikkauksia")}
        skeema
        paikkaukset]])))

(defn toteumat* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymaan))
                      #(e! (tiedot/->NakymastaPois)))
    (fn [e! app]
      [:span
       [kartta/kartan-paikka]
       [:div
        [debug/debug app]
        [hakuehdot e! app]
        [paikkaukset e! app]]])))

(defn toteumat []
  [tuck/tuck tiedot/app toteumat*])
