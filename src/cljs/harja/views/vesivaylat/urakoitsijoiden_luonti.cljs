(ns harja.views.vesivaylat.urakoitsijoiden-luonti
  (:require [harja.ui.komponentti :as komp]
            [tuck.core :refer [tuck]]
            [harja.tiedot.vesivaylat.urakoitsijoiden-luonti :as tiedot]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni]]
            [harja.ui.grid :as grid]
            [harja.pvm :as pvm]
            [harja.ui.lomake :as lomake]
            [harja.ui.ikonit :as ikonit]
            [harja.domain.oikeudet :as oikeudet]))

(defn luontilomake [e! {:keys [valittu-urakoitsija tallennus-kaynnissa?] :as app}]
  [:div
   [napit/takaisin "Takaisin luetteloon"
    #(e! (tiedot/->ValitseUrakoitsija nil))
    {:disabled (or (not (oikeudet/hallinta-vesivaylat)) tallennus-kaynnissa?)}]
   [lomake/lomake
    {:otsikko (if (:id valittu-urakoitsija)
                "Muokkaa urakoitsijaa"
                "Luo uusi urakoitsija")
     :muokkaa! #(e! (tiedot/->UrakoitsijaaMuokattu (lomake/ilman-lomaketietoja %)))
     :voi-muokata? #(oikeudet/hallinta-vesivaylat)
     :footer-fn (fn [urakoitsija]
                  [napit/tallenna
                   "Tallenna urakoitsija"
                   #(e! (tiedot/->TallennaUrakoitsija (lomake/ilman-lomaketietoja urakoitsija)))
                   {:ikoni (ikonit/tallenna)
                    :disabled (or tallennus-kaynnissa?
                                  (not (oikeudet/hallinta-vesivaylat))
                                  (not (lomake/voi-tallentaa? urakoitsija)))
                    :tallennus-kaynnissa? tallennus-kaynnissa?}])}
    [{:otsikko "Nimi" :nimi :nimi :tyyppi :string :pakollinen? true}
     {:otsikko "Y-tunnus" :nimi :ytunnus :tyyppi :string :pakollinen? true}
     {:otsikko "Katuosoite" :nimi :katuosoite :tyyppi :string}
     {:otsikko "Postinumero" :nimi :postinumero :tyyppi :string}]
    valittu-urakoitsija]])

(defn urakoitsijagrid [e! app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeUrakoitsijat)))
    (fn [e! {:keys [haetut-urakoitsijat urakoitsijoiden-haku-kaynnissa?] :as app}]
      [:div
       [napit/uusi "Lisää urakoitsija"
        #(e! (tiedot/->UusiUrakoitsija))
        {:disabled (or (not (oikeudet/hallinta-vesivaylat))
                       (nil? haetut-urakoitsijat))}]
       [grid/grid
        {:otsikko (if (and (some? haetut-urakoitsijat) urakoitsijoiden-haku-kaynnissa?)
                    [ajax-loader-pieni "Päivitetään listaa"] ;; Listassa on jo jotain, mutta sitä päivitetään
                    "Harjaan perustetut vesiväyläurakoitsijat")
         :tunniste :id
         :tyhja (if (nil? haetut-urakoitsijat)
                  [ajax-loader "Haetaan urakoitsijoita"]
                  "Urakoitsijoita ei löytynyt")
         :rivi-klikattu #(e! (tiedot/->ValitseUrakoitsija %))}
        [{:otsikko "Nimi" :nimi :nimi}
         {:otsikko "Y-tunnus" :nimi :ytunnus}
         {:otsikko "Katuosoite" :nimi :katuosoite}
         {:otsikko "Postinumero" :nimi :postinumero}]
        haetut-urakoitsijat]])))

(defn vesivaylaurakoitsijoiden-luonti* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                      #(e! (tiedot/->Nakymassa? false)))

    (fn [e! {valittu-urakoitsija :valittu-urakoitsija :as app}]
      (if valittu-urakoitsija
        [luontilomake e! app]
        [urakoitsijagrid e! app]))))

(defn vesivaylaurakoitsijoiden-luonti []
  [tuck tiedot/tila vesivaylaurakoitsijoiden-luonti*])