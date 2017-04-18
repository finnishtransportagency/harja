(ns harja.views.vesivaylat.sopimuksien-luonti
  (:require [harja.ui.komponentti :as komp]
            [tuck.core :refer [tuck]]
            [harja.tiedot.vesivaylat.sopimuksien-luonti :as tiedot]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni]]
            [harja.ui.grid :as grid]
            [harja.pvm :as pvm]
            [harja.ui.lomake :as lomake]
            [harja.ui.ikonit :as ikonit]
            [harja.domain.oikeudet :as oikeudet]))

(defn luontilomake [e! {:keys [valittu-sopimus tallennus-kaynnissa? haetut-sopimukset] :as app}]
  (let [urakat (:urakat valittu-sopimus)]
    [:div
    [napit/takaisin "Takaisin luetteloon"
     #(e! (tiedot/->ValitseSopimus nil))
     {:disabled tallennus-kaynnissa?}]
    [harja.ui.debug/debug valittu-sopimus]
    [lomake/lomake
     {:otsikko (if (:id valittu-sopimus)
                 "Muokkaa sopimusta"
                 "Luo uusi sopimus")
      :muokkaa! #(e! (tiedot/->SopimustaMuokattu (lomake/ilman-lomaketietoja %)))
      :voi-muokata? #(oikeudet/hallinta-vesivaylat)
      :footer-fn (fn [hanke]
                   [napit/tallenna
                    "Tallenna sopimus"
                    #(e! (tiedot/->TallennaSopimus (lomake/ilman-lomaketietoja hanke)))
                    {:ikoni (ikonit/tallenna)
                     :disabled (or tallennus-kaynnissa?
                                   (not (lomake/voi-tallentaa? hanke))
                                   (not (oikeudet/hallinta-vesivaylat)))
                     :tallennus-kaynnissa? tallennus-kaynnissa?}])}
     [{:otsikko "Nimi" :nimi :nimi :tyyppi :string :pakollinen? true}
      {:otsikko "Alku" :nimi :alkupvm :tyyppi :pvm :pakollinen? true}
      {:otsikko "Loppu" :nimi :loppupvm :tyyppi :pvm :pakollinen? true}
      {:otsikko "Pääsopimus"
       :muokattava? (constantly false)
       :nimi :paasopimus
       :tyyppi :string
       :hae (fn [s] (or (:name (first (filter #(= (:paasopimus s) (:id %)) haetut-sopimukset)))
                        "Sopimus on pääsopimus"))}
      (if (:id valittu-sopimus)
        {:otsikko "Urakka"
         :muokattava? (constantly false)
         :tyyppi :string
         :nimi :urakan-nimi
         :hae #(get-in % [:urakka :nimi])})]
     valittu-sopimus]]))

(defn sopimusgrid [e! app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeSopimukset)))
    (fn [e! {:keys [haetut-sopimukset sopimuksien-haku-kaynnissa?] :as app}]
      [:div
       [napit/uusi "Lisää sopimus" ;; TODO Oikeustarkistuksen mukaan disabloi tarvittaessa
        #(e! (tiedot/->UusiSopimus))
        {:disabled (or (not (oikeudet/hallinta-vesivaylat))
                       (nil? haetut-sopimukset))}]
       [grid/grid
        {:otsikko (if (and (some? haetut-sopimukset) sopimuksien-haku-kaynnissa?)
                    [ajax-loader-pieni "Päivitetään listaa"] ;; Listassa on jo jotain, mutta sitä päivitetään
                    "Harjaan perustetut vesiväyläsopimukset")
         :tunniste :id
         :tyhja (if (nil? haetut-sopimukset)
                  [ajax-loader "Haetaan sopimuksia"]
                  "Sopimuksia ei löytynyt")
         :rivi-klikattu #(e! (tiedot/->ValitseSopimus %))}
        [{:otsikko "Nimi" :nimi :nimi}
         {:otsikko "Alku" :nimi :alkupvm :tyyppi :pvm :fmt pvm/pvm-opt}
         {:otsikko "Loppu" :nimi :loppupvm :tyyppi :pvm :fmt pvm/pvm-opt}
         {:otsikko "Pääsopimus" :nimi :paasopimus
          :hae (fn [s] (:nimi (first (filter #(= (:paasopimus s) (:id %)) haetut-sopimukset))))}
         {:otsikko "Urakka" :nimi :urakan-nimi :hae #(get-in % [:urakka :nimi])}]
        haetut-sopimukset]])))

(defn vesivaylasopimuksien-luonti* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                      #(e! (tiedot/->Nakymassa? false)))

    (fn [e! {valittu-sopimus :valittu-sopimus :as app}]
      (if valittu-sopimus
        [luontilomake e! app]
        [sopimusgrid e! app]))))

(defn vesivaylasopimuksien-luonti []
  [tuck tiedot/tila vesivaylasopimuksien-luonti*])