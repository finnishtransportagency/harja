(ns harja.views.urakka.paallystyksen-maksuerat
  "Päällystysurakan maksuerät"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.loki :refer [log tarkkaile!]]
            [cljs.core.async :refer [<!]]
            [harja.tiedot.urakka.paikkaus :as paikkaus]
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
            [harja.domain.yllapitokohteet :as yllapitokohteet-domain]
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
            ;; TODO OIKEUSTARKISTUS, ROOLIT EXCELIIN KUN TASKI VALMIS JA OTA TÄMÄ SITTEN KÄYTTÖÖN
            voi-muokata? true ; Käytä (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-maksuerat urakka-id
            voi-tayttaa-maksuerat? true ; Käytä (oikeudet/on-muu-oikeus? "maksuerat" oikeudet/urakat-kohdeluettelo-maksuerat urakka-id)
            voi-tayttaa-maksueratunnuksen? true ; Käytä (oikeudet/on-muu-oikeus? "TM-takaraja" oikeudet/urakat-kohdeluettelo-maksuerat urakka-id)
            ]
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
           :tallenna (if voi-muokata?
                       #(go (e! (tiedot/->TallennaMaksuerat
                                  (merge valinnat
                                         {:yllapitokohteet (mapv tiedot/maksuerarivi-tallennusmuotoon %)})))
                            (<! (tapahtumat/odota! :paallystyksen-maksuerat-tallennettu)))
                       :ei-mahdollinen)
           :tunniste :yllapitokohde-id
           ;; TODO OIKEUSTARKISTUS, ROOLIT EXCELIIN KUN TASKI VALMIS JA OTA TÄMÄ SITTEN KÄYTTÖÖN
           ;:tallennus-ei-mahdollinen-tooltip
           #_(oikeudet/oikeuden-puute-kuvaus :kirjoitus oikeudet/urakat-kohdeluettelo-maksuerat)}

          [{:otsikko "Kohdenumero" :leveys 5 :nimi :kohdenumero
            :tyyppi :string :muokattava? (constantly false)}
           {:otsikko "Kohteen nimi" :leveys 10 :nimi :nimi
            :tyyppi :string :muokattava? (constantly false)}
           {:otsikko "Kokonaishinta" :leveys 5 :nimi :kokonaishinta
            :tyyppi :numero :fmt fmt/euro-opt :muokattava? (constantly false)}
           ;; TODO Hae kokonaishinta, yhdistä frontin ja API:n kokonaishinnan lasku yhdeksi funktioksi,
           ;; jolla voidaan laskea kokonaishinta helposti (palvelu voi laskea valmiiksi, koska readonly
           ;; eikä muutu tässä näkymässä)
           {:otsikko "1. maksuerä" :leveys 10 :nimi :maksuera1 :tyyppi :string :pituus-max 512
            :fmt :sisalto :muokattava? (constantly voi-tayttaa-maksuerat?)}
           {:otsikko "2. maksuerä" :leveys 10 :nimi :maksuera2 :tyyppi :string :pituus-max 512
            :fmt :sisalto :muokattava? (constantly voi-tayttaa-maksuerat?)}
           {:otsikko "3. maksuerä" :leveys 10 :nimi :maksuera3 :tyyppi :string :pituus-max 512
            :fmt :sisalto :muokattava? (constantly voi-tayttaa-maksuerat?)}
           {:otsikko "4. maksuerä" :leveys 10 :nimi :maksuera4 :tyyppi :string :pituus-max 512
            :fmt :sisalto :muokattava? (constantly voi-tayttaa-maksuerat?)}
           {:otsikko "5. maksuerä" :leveys 10 :nimi :maksuera5 :tyyppi :string :pituus-max 512
            :fmt :sisalto :muokattava? (constantly voi-tayttaa-maksuerat?)}
           {:otsikko "Laskutuksen maksuerätunnus" :leveys 10 :nimi :maksueratunnus
            :tyyppi :string :pituus-max 512 :muokattava? (constantly voi-tayttaa-maksueratunnuksen?)}]
          (-> maksuerat
              (yllapitokohteet/suodata-yllapitokohteet {:tienumero (:tienumero valinnat)
                                                        :kohdenumero (:kohdenumero valinnat)})
              (yllapitokohteet-domain/jarjesta-yllapitokohteet))]]))))

(defn maksuerat []
  (komp/luo
    (komp/lippu paikkaus/paikkausilmoitukset-nakymassa?)

    (fn []
      [tuck tiedot/tila maksuerat*])))
