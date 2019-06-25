(ns harja.views.vesivaylat.hallinta.hankkeiden-luonti
  (:require [harja.ui.komponentti :as komp]
            [tuck.core :refer [tuck]]
            [harja.tiedot.vesivaylat.hallinta.hankkeiden-luonti :as tiedot]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni]]
            [harja.ui.grid :as grid]
            [harja.domain.hanke :as h]
            [harja.domain.urakka :as u]
            [harja.pvm :as pvm]
            [harja.ui.lomake :as lomake]
            [harja.ui.ikonit :as ikonit]
            [harja.domain.oikeudet :as oikeudet]))

(defn luontilomake [e! {:keys [valittu-hanke tallennus-kaynnissa?] :as app}]
  [:div
   [napit/takaisin "Takaisin luetteloon" #(e! (tiedot/->ValitseHanke nil))
    {:disabled tallennus-kaynnissa?}]
   [lomake/lomake
    {:otsikko (if (::h/id valittu-hanke)
                "Muokkaa hanketta"
                "Luo uusi hanke")
     :muokkaa! #(e! (tiedot/->HankettaMuokattu (lomake/ilman-lomaketietoja %)))
     :voi-muokata? (oikeudet/hallinta-vesivaylat)
     :footer-fn (fn [hanke]
                  [napit/tallenna
                   "Tallenna hanke"
                   #(e! (tiedot/->TallennaHanke (lomake/ilman-lomaketietoja hanke)))
                   {:ikoni (ikonit/tallenna)
                    :disabled (or tallennus-kaynnissa?
                                  (not (lomake/voi-tallentaa? hanke))
                                  (not (oikeudet/hallinta-vesivaylat)))
                    :tallennus-kaynnissa? tallennus-kaynnissa?}])}
    [{:otsikko "Nimi" :nimi ::h/nimi :tyyppi :string :pakollinen? true}
     (lomake/rivi
       {:otsikko "Alku" :nimi ::h/alkupvm :tyyppi :pvm :pakollinen? true}
       {:otsikko "Loppu" :nimi ::h/loppupvm :tyyppi :pvm :pakollinen? true ;; TODO Validointi ei herjaa!?
        :validoi [[:pvm-kentan-jalkeen :alkupvm "Loppu ei voi olla ennen alkua"]]})
     (when (get-in valittu-hanke [:urakka :nimi])
       {:otsikko "Urakka" :nimi :urakan-nimi :tyyppi :string :muokattava? (constantly false)
        :hae #(get-in % [::h/urakka ::u/nimi])})]
    valittu-hanke]])

(defn hankegrid [e! app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeHankkeet)))
    (fn [e! {:keys [haetut-hankkeet hankkeiden-haku-kaynnissa?] :as app}]
      [:div
       [napit/uusi "Lisää hanke" #(e! (tiedot/->UusiHanke))
        {:disabled (or (nil? haetut-hankkeet)
                       (not (oikeudet/hallinta-vesivaylat)))}]
       [grid/grid
        {:otsikko (if (and (some? haetut-hankkeet) hankkeiden-haku-kaynnissa?)
                    [ajax-loader-pieni "Päivitetään listaa"] ;; Listassa on jo jotain, mutta sitä päivitetään
                    "Harjaan perustetut vesiväylähankkeet")
         :tunniste ::h/id
         :tyhja (if (nil? haetut-hankkeet)
                  [ajax-loader "Haetaan hankkeita"]
                  "Hankkeita ei löytynyt")
         :rivi-klikattu #(e! (tiedot/->ValitseHanke %))}
        [{:otsikko "Nimi" :nimi ::h/nimi}
         {:otsikko "Alku" :nimi ::h/alkupvm :tyyppi :pvm :fmt pvm/pvm-opt}
         {:otsikko "Loppu" :nimi ::h/loppupvm :tyyppi :pvm :fmt pvm/pvm-opt}
         {:otsikko "Liitetty urakkaan" :nimi :liitetty-urakkaan :tyyppi :string
          :hae #(get-in % [::h/urakka ::u/nimi])}]
        haetut-hankkeet]])))

(defn vesivaylahankkeiden-luonti* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                      #(e! (tiedot/->Nakymassa? false)))

    (fn [e! {valittu-hanke :valittu-hanke :as app}]
      (if valittu-hanke
        [luontilomake e! app]
        [hankegrid e! app]))))

(defn vesivaylahankkeiden-luonti []
  [tuck tiedot/tila vesivaylahankkeiden-luonti*])
