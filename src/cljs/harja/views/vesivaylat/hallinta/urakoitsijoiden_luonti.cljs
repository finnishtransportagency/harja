(ns harja.views.vesivaylat.hallinta.urakoitsijoiden-luonti
  (:require [harja.ui.komponentti :as komp]
            [tuck.core :refer [tuck]]
            [harja.tiedot.vesivaylat.hallinta.urakoitsijoiden-luonti :as tiedot]
            [harja.ui.napit :as napit]
            [harja.loki :refer [log]]
            [harja.domain.organisaatio :as o]
            [harja.domain.urakka :as u]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja]]
            [harja.ui.grid :as grid]
            [harja.ui.lomake :as lomake]
            [harja.ui.ikonit :as ikonit]
            [harja.domain.oikeudet :as oikeudet]
            [harja.fmt :as fmt]))

(defn urakan-nimi-ja-pvm [urakat]
  [apply
   tietoja
   {:otsikot-omalla-rivilla? true}
   (interleave
     (map ::u/nimi urakat)
     (map tiedot/urakan-aikavali-str urakat))])

(defn luontilomake [e! app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeLomakevaihtoehdot)))
    (fn [e! {:keys [valittu-urakoitsija tallennus-kaynnissa? haetut-ytunnukset] :as app}]
      (let [urakat (tiedot/urakoitsijan-urakat valittu-urakoitsija)]
       [:div
        [napit/takaisin "Takaisin luetteloon"
         #(e! (tiedot/->ValitseUrakoitsija nil))
         {:disabled tallennus-kaynnissa?}]
        [lomake/lomake
         {:otsikko (if (::o/id valittu-urakoitsija)
                     "Muokkaa urakoitsijaa"
                     "Luo uusi urakoitsija")
          :muokkaa! #(e! (tiedot/->UrakoitsijaaMuokattu %))
          :voi-muokata? (and (oikeudet/hallinta-vesivaylat)
                              (::o/harjassa-luotu? valittu-urakoitsija))
          :footer-fn (fn [urakoitsija]
                       [napit/tallenna
                        "Tallenna urakoitsija"
                        #(e! (tiedot/->TallennaUrakoitsija (lomake/ilman-lomaketietoja urakoitsija)))
                        {:ikoni (ikonit/tallenna)
                         :disabled (or tallennus-kaynnissa?
                                       (not (oikeudet/hallinta-vesivaylat))
                                       (not (lomake/voi-tallentaa? urakoitsija)))
                         :tallennus-kaynnissa? tallennus-kaynnissa?}])}
         [{:otsikko "Nimi" :nimi ::o/nimi :tyyppi :string :pakollinen? true}
          {:otsikko "Y-tunnus" :nimi ::o/ytunnus :tyyppi :string :pakollinen? true :pituus-min 9 :pituus-max 9
           :validoi [(fn [tunnus] (when-let [urakoitsija (haetut-ytunnukset tunnus)]
                                    (str "Tunnus " tunnus " on käytössa urakoitsijalla " urakoitsija)))]}
          {:otsikko "Katuosoite" :nimi ::o/katuosoite :tyyppi :string}
          {:otsikko "Postinumero" :nimi ::o/postinumero :tyyppi :string :pituus-max 5}
          (when (some not-empty (vals urakat))
            (lomake/ryhma
              {:otsikko "Urakat"}
              (when (not-empty (:alkava urakat))
                {:otsikko "Alkavat urakat"
                 :nimi :alkavat-urakat
                 :tyyppi :komponentti
                 :palstoja 2
                 :komponentti (fn [_] [urakan-nimi-ja-pvm (:alkava urakat)])})
              (when (not-empty (:kaynnissa urakat))
                {:otsikko "Käynnissä olevat urakat"
                 :nimi :kaynnissa-urakat
                 :tyyppi :komponentti
                 :palstoja 2
                 :komponentti (fn [_] [urakan-nimi-ja-pvm (:kaynnissa urakat)])})
              (when (not-empty (:paattynyt urakat))
                {:otsikko "Päättyneet urakat"
                 :nimi :paattyneet-urakat
                 :tyyppi :komponentti
                 :palstoja 2
                 :komponentti (fn [_] [urakan-nimi-ja-pvm (:paattynyt urakat)])})))]
         valittu-urakoitsija]]))))

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
         :tunniste ::o/id
         :tyhja (if (nil? haetut-urakoitsijat)
                  [ajax-loader "Haetaan urakoitsijoita"]
                  "Urakoitsijoita ei löytynyt")
         :rivi-klikattu #(e! (tiedot/->ValitseUrakoitsija %))}
        [{:otsikko "Nimi" :nimi ::o/nimi :tyyppi :string }
         {:otsikko "Y-tunnus" :nimi ::o/ytunnus :tyyppi :string :pituus-max 9}
         {:otsikko "Katuosoite" :nimi ::o/katuosoite :tyyppi :string}
         {:otsikko "Postinumero" :nimi ::o/postinumero :pituus-max 5 :tyyppi :string}
         {:otsikko "Urakoita (Alk. / Käyn. / Päät. )" :nimi :urakoita :tyyppi :string
          :hae tiedot/urakoitsijan-urakoiden-lukumaarat-str}
         {:otsikko "Luotu Harjassa?" :nimi ::o/harjassa-luotu? :fmt fmt/totuus}]
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
