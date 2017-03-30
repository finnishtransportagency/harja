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
            [harja.ui.grid :as grid])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn- maksuerat* [e! tila]
  (e! (tiedot/->PaivitaValinnat @tiedot/valinnat))

  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->PaivitaValinnat uusi))))

    (fn [e! {:keys [maksuerat] :as tila}]
      (let [valittu-urakka @nav/valittu-urakka
            voi-muokata? true] ;; TODO OIKEUSTARKISTUS
        [:div.paallystyksen-maksuerat
         [valinnat/urakan-vuosi valittu-urakka]
         [valinnat/yllapitokohteen-kohdenumero yllapito-tiedot/kohdenumero]
         [valinnat/tienumero yllapito-tiedot/tienumero]
         [grid/grid
          {:otsikko "Maksuerät"
           :tyhja (if (nil? maksuerat)
                    [y/ajax-loader "Haetaan maksueriä..."]
                    "Ei maksueriä")
           :tallenna (if voi-muokata?
                       (log "Painoit nappia")
                       #_(go (let [vastaus (<! (tiedot/tallenna-valitavoitteet! (:id urakka) %))]
                              (if (k/virhe? vastaus)
                                (viesti/nayta! "Tallentaminen epäonnistui"
                                               :warning viesti/viestin-nayttoaika-lyhyt)
                                (reset! kaikki-valitavoitteet-atom vastaus))))
                       :ei-mahdollinen)
           ;; TODO Oikeuscheck
           ;:tallennus-ei-mahdollinen-tooltip
           #_(oikeudet/oikeuden-puute-kuvaus :kirjoitus oikeudet/urakat-valitavoitteet)}

          [{:otsikko "Kohdenumero" :leveys 5 :nimi :nimi :tyyppi :string}
           {:otsikko "Kohteen nimi" :leveys 10 :nimi :nimi :tyyppi :string}
           {:otsikko "Kokonaishinta" :leveys 5 :nimi :nimi :tyyppi :numero :fmt fmt/euro-opt}
           {:otsikko "1. maksuerä" :leveys 10 :nimi :nimi :tyyppi :string :pituus-max 512}
           {:otsikko "2. maksuerä" :leveys 10 :nimi :nimi :tyyppi :string :pituus-max 512}
           {:otsikko "3. maksuerä" :leveys 10 :nimi :nimi :tyyppi :string :pituus-max 512}
           {:otsikko "4. maksuerä" :leveys 10 :nimi :nimi :tyyppi :string :pituus-max 512}
           {:otsikko "5. maksuerä" :leveys 10 :nimi :nimi :tyyppi :string :pituus-max 512}
           {:otsikko "Laskutuksen maksuerätunnus" :leveys 10 :nimi :nimi :tyyppi :string :pituus-max 512}]
          maksuerat]]))))

(defn maksuerat []
  (komp/luo
    (komp/lippu paikkaus/paikkausilmoitukset-nakymassa?)

    (fn []
      [tuck tiedot/tila maksuerat*])))
