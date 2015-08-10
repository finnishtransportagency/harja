(ns harja.views.urakka.kohdeluettelo.paikkausilmoitukset
  "Urakan kohdeluettelon paikkausilmoitukset"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]

            [harja.tiedot.navigaatio :as nav]
            [harja.fmt :as fmt]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<!]]
            [harja.tiedot.urakka :as u]
            [harja.ui.lomake :refer [lomake]]
            [harja.tiedot.urakka.kohdeluettelo.paikkaus :as paikkaus]
            [harja.domain.roolit :as roolit]
            [harja.ui.kommentit :as kommentit]
            [harja.ui.yleiset :as yleiset])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(def lomakedata (atom nil)) ; Vastaa rakenteeltaan paikkausilmoitus-taulun sisältöä

(defonce toteumarivit (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
                                   [valittu-sopimus-id _] @u/valittu-sopimusnumero
                                   nakymassa? @paikkaus/paikkausilmoitukset-nakymassa?]
                                  (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                                    (log "PÄÄ Haetaan paikkausilmoitukset")
                                    []
                                    ; TODO
                                    #_(paallystys/hae-paallystystoteumat valittu-urakka-id valittu-sopimus-id))))

(defn ilmoitusluettelo
  []
  (let []

    (komp/luo
      (fn []
        [:div
         [grid/grid
          {:otsikko  "Paikkausilmoitukset"
           :tyhja    (if (nil? @toteumarivit) [ajax-loader "Haetaan ilmoituksia..."] "Ei ilmoituksia")
           :tunniste :kohdenumero}
          [{:otsikko "#" :nimi :kohdenumero :muokattava? (constantly false) :tyyppi :numero :leveys "10%"}
           {:otsikko "Nimi" :nimi :nimi :muokattava? (constantly false) :tyyppi :string :leveys "50%"}
           {:otsikko "Paikkausilmoitus" :nimi :paikkausilmoitus :muokattava? (constantly false) :leveys "25%" :tyyppi :komponentti
            :komponentti (fn [rivi] (if (:tila rivi) [:button.nappi-toissijainen.nappi-grid {:on-click #(go
                                                                                                         ; TODO
                                                                                                         #_(let [urakka-id (:id @nav/valittu-urakka)
                                                                                                               [sopimus-id _] @u/valittu-sopimusnumero
                                                                                                               vastaus (<! (paallystys/hae-paallystysilmoitus-paallystyskohteella urakka-id sopimus-id (:paallystyskohde_id rivi)))]
                                                                                                           (log "PÄÄ Rivi: " (pr-str rivi))
                                                                                                           (log "PÄÄ Vastaus: " (pr-str vastaus))
                                                                                                           (if-not (k/virhe? vastaus)
                                                                                                             (reset! lomakedata (-> (assoc vastaus :paallystyskohde-id (:paallystyskohde_id rivi))
                                                                                                                                    (assoc :kokonaishinta (+ (:sopimuksen_mukaiset_tyot rivi)
                                                                                                                                                             (:arvonvahennykset rivi)
                                                                                                                                                             (:bitumi_indeksi rivi)
                                                                                                                                                             (:kaasuindeksi rivi))))))))}
                                                      [:span (ikonit/eye-open) " Paikkausilmoitus"]]
                                                     [:button.nappi-toissijainen.nappi-grid {:on-click #(reset! lomakedata {:kohdenumero        (:kohdenumero rivi)
                                                                                                                            :kohdenimi          (:nimi rivi)
                                                                                                                            :paikkauskohde-id   (:paikkauskohde_id rivi)})}
                                                      [:span " Tee paikkausilmoitus"]]))}]
          (sort-by
            (fn [toteuma] (case (:tila toteuma)
                            :lukittu 0
                            :valmis 1
                            :aloitettu 3
                            4))
            @toteumarivit)]]))))

(defn paikkausilmoitukset []
  (komp/luo
    (komp/lippu paikkaus/paikkausilmoitukset-nakymassa?)

    (fn []
      (if @lomakedata
        ;;[paallystysilmoituslomake] TODO To be implemented...
        [ilmoitusluettelo]))))