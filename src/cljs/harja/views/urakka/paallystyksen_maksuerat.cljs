(ns harja.views.urakka.paallystyksen-maksuerat
  "Päällystysurakan maksuerät"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.loki :refer [log tarkkaile!]]
            [cljs.core.async :refer [<!]]
            [harja.tiedot.urakka.paallystyksen-maksuerat :as tiedot]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.navigaatio :as nav]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.tiedot.urakka.yllapito :as yllapito-tiedot]
            [harja.fmt :as fmt]
            [harja.ui.yleiset :as y]
            [harja.ui.grid :as grid]
            [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.domain.yllapitokohde :as yllapitokohde-domain]
            [harja.tiedot.istunto :as istunto]
            [harja.asiakas.tapahtumat :as tapahtumat])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn- maksuerat* [e! tila]
  (e! (tiedot/->PaivitaValinnat @tiedot/valinnat))

  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->PaivitaValinnat uusi))))

    (fn [e! {:keys [maksuerat valinnat] :as tila}]
      (let [valittu-urakka @nav/valittu-urakka
            urakka-id (:id valittu-urakka)
            voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-maksuerat urakka-id)
            voi-tayttaa-maksuerat?
            (oikeudet/on-muu-oikeus? "maksuerät" oikeudet/urakat-kohdeluettelo-maksuerat urakka-id)
            voi-tayttaa-maksueratunnuksen?
            (oikeudet/on-muu-oikeus? "maksuerätunnus" oikeudet/urakat-kohdeluettelo-maksuerat urakka-id)]
        [:div.paallystyksen-maksuerat
         [valinnat/urakan-vuosi valittu-urakka]
         [valinnat/yllapitokohteen-kohdenumero yllapito-tiedot/kohdenumero]
         [valinnat/tienumero yllapito-tiedot/tienumero]
         [grid/grid
          {:otsikko "Maksuerät"
           :tyhja (if (nil? maksuerat)
                    [y/ajax-loader "Haetaan maksueriä..."]
                    "Ei maksueriä")
           :voi-lisata? false
           :voi-poistaa? (constantly false)
           :piilota-toiminnot? true
           :tallenna (if voi-muokata?
                       #(go (e! (tiedot/->TallennaMaksuerat
                                  (merge valinnat
                                         {:yllapitokohteet (mapv tiedot/maksuerarivi-tallennusmuotoon %)})))
                            (<! (tapahtumat/odota! :paallystyksen-maksuerat-tallennettu)))
                       :ei-mahdollinen)
           :tallennus-ei-mahdollinen-tooltip
           (oikeudet/oikeuden-puute-kuvaus :kirjoitus oikeudet/urakat-kohdeluettelo-maksuerat)}

          [{:otsikko "Kohde\u00ADnumero" :leveys 5 :nimi :kohdenumero
            :tyyppi :string :muokattava? (constantly false)}
           {:otsikko "Tunnus" :leveys 10 :nimi :tunnus :tyyppi :string :muokattava? (constantly false)}
           {:otsikko "Nimi" :leveys 10 :nimi :nimi
            :tyyppi :string :muokattava? (constantly false)}
           {:otsikko "Kokonais\u00ADhinta" :leveys 5 :nimi :kokonaishinta
            :tyyppi :numero :fmt fmt/euro-opt :muokattava? (constantly false)}
           {:otsikko "1. maksu\u00ADerä" :leveys 10 :nimi :maksuera1 :tyyppi :string :pituus-max 512
            :muokattava? (constantly voi-tayttaa-maksuerat?)}
           {:otsikko "2. maksu\u00ADerä" :leveys 10 :nimi :maksuera2 :tyyppi :string :pituus-max 512
            :muokattava? (constantly voi-tayttaa-maksuerat?)}
           {:otsikko "3. maksu\u00ADerä" :leveys 10 :nimi :maksuera3 :tyyppi :string :pituus-max 512
            :muokattava? (constantly voi-tayttaa-maksuerat?)}
           {:otsikko "4. maksu\u00ADerä" :leveys 10 :nimi :maksuera4 :tyyppi :string :pituus-max 512
            :muokattava? (constantly voi-tayttaa-maksuerat?)}
           {:otsikko "5. maksu\u00ADerä" :leveys 10 :nimi :maksuera5 :tyyppi :string :pituus-max 512
            :muokattava? (constantly voi-tayttaa-maksuerat?)}
           {:otsikko "Lasku\u00ADtuksen maksuerä\u00ADtunnus" :leveys 10 :nimi :maksueratunnus
            :tyyppi :string :pituus-max 512 :muokattava? (constantly voi-tayttaa-maksueratunnuksen?)}]
          (-> maksuerat
              (yllapitokohteet/suodata-yllapitokohteet {:tienumero (:tienumero valinnat)
                                                        :kohdenumero (:kohdenumero valinnat)})
              (yllapitokohde-domain/jarjesta-yllapitokohteet))]]))))

(defn maksuerat []
  (komp/luo
    (fn []
      [tuck tiedot/tila maksuerat*])))
