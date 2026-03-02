(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-apurit
  (:require [reagent.core :as r]
            [harja.domain.roolit :as roolit]
            [harja.domain.paikkaus :as paikkaus]
            [harja.ui.kentat :as kentat]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.modal :as modal]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as t-paikkauskohteet]))

(defn nayta-pot-valinta?
  "Tilaajalle näytetään kolmen työmenetelmän kohdalla erillinen pot/toteuma radiobutton valinta.
  Mikäli tilaaja valitsee pot vaihtoehdon, toteumia ei kirjata normaaliprossin mukaan, vaan pot-lomakkeelta
  Kolme työmenetelmää ovat: AB-paikkaus levittäjällä, PAB-paikkaus levittäjällä, SMA-paikkaus levittäjällä"
  [lomake tyomenetelmat]
  (let [nayta? (and (roolit/kayttaja-on-laajasti-ottaen-tilaaja?
                      (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id))
                      @istunto/kayttaja)
                 (= "ehdotettu" (:paikkauskohteen-tila lomake))
                 (paikkaus/levittimella-tehty? lomake tyomenetelmat))]
    nayta?))

(defn tilaa-paikkauskohteet-modal! [e! {:keys [valitut-tilattavat-kohteet vahvista-tilaus-modal-auki? paikkauskohteet] :as app}]
  (let [valitut-kohteet (filter #(contains? valitut-tilattavat-kohteet (:id %)) paikkauskohteet)]
    [modal/modal
     {:nakyvissa? vahvista-tilaus-modal-auki?
      :otsikko (if (= 1 (count valitut-kohteet))
                 (str "Tilataanko kohde \"" (:nimi (first valitut-kohteet)) "\"?")
                 (str "Tilataanko " (count valitut-kohteet) " kpl kohteita?"))
      :footer [:div
               [:div.pull-left
                [napit/yleinen-ensisijainen (if (= 1 (count valitut-kohteet)) "Tilaa kohde" "Tilaa kohteet")
                 #(e! (t-paikkauskohteet/->TilaaValitutPaikkauskohteet))
                 {:paksu? true}]]
               [:div.pull-right
                [napit/yleinen-toissijainen "Kumoa" #(e! (t-paikkauskohteet/->TilaaModalToggle)) {:paksu? true}]]]}
     [:div.tilaus-vahvistus-modal
      [:p "Urakoitsija saa sähköpostiin ilmoituksen kohteen tilauksesta."]]]))

(defn raportointi-modal!
  "Näyttää modalin, jossa käyttäjä voi vahvistaa valittujen paikkauskohteiden raportointitavan."
  [e! app]
  (let [valitut-kohteet (:valitut-tilattavat-kohteet app)
        kaikki-kohteet (:paikkauskohteet app)
        valitut-kohteet (filter #(contains? valitut-kohteet (:id %)) kaikki-kohteet)
        tyomenetelmat (get-in app [:valinnat :tyomenetelmat])]
    [modal/modal
     {:nakyvissa? (:raportointimodal-aktiivinen? app)
      :otsikko "Vahvista raportointitapa seuraaville kohteille"
      :footer [:div
               [:div.pull-left
                [napit/yleinen-ensisijainen "Vahvista"
                 #(e! (t-paikkauskohteet/->VahvistaRaportointitavatModalissa))
                 {:paksu? true}]]
               [:div.pull-right
                [napit/yleinen-toissijainen "Kumoa" #(e! (t-paikkauskohteet/->RaportointitapaModalToggle)) {:paksu? true}]]]}
     [:div.tilaus-vahvistus-modal
      [:p (str "Osa kohteista (" (count valitut-kohteet) ") vaatii raportointitavan vahvistamisen ennen tilaamista.")]
      [grid/grid
       {:otsikko "Valitut kohteet"
        :tunniste :id
        :tyhja "Ei valittuja kohteita"
        :voi-muokata? false}
       [{:otsikko "Nro"
         :nimi :id
         :tyyppi :string
         :leveys 1}
        {:otsikko "Nimi"
         :nimi :nimi
         :tyyppi :string
         :leveys 3}
        {:otsikko "Raportointitapa"
         :leveys 3
         :tyyppi :komponentti
         :komponentti (fn [rivi]
                        (if (nayta-pot-valinta? rivi tyomenetelmat)
                          [kentat/tee-kentta {:tyyppi :radio-group
                                              :nimi :toteumatyyppi
                                              :otsikko ""
                                              :vaihtoehdot [:normaali :pot]
                                              :nayta-rivina? true
                                              :vayla-tyyli? true
                                              :vaihtoehto-nayta {:pot "POT-lomake"
                                                                 :normaali "Toteumat"}
                                              :valitse-fn #(e! (t-paikkauskohteet/->AsetaToteumatyyppiKohteelle rivi %))}
                           (r/atom (:toteumatyyppi rivi))]
                          "Toteumat"))}]
       valitut-kohteet]]]))

