(ns harja.views.ilmoituksen-tiedot
  (:require [harja.ui.yleiset :as yleiset]
            [harja.pvm :as pvm]
            [harja.ui.bootstrap :as bs]
            [clojure.string :refer [capitalize]]
            [harja.tiedot.ilmoitukset.tieliikenneilmoitukset :as tiedot]
            [harja.domain.tieliikenneilmoitukset
             :refer [+ilmoitustyypit+ ilmoitustyypin-nimi ilmoitustyypin-lyhenne-ja-nimi
                     +ilmoitustilat+ nayta-henkilo parsi-puhelinnumero
                     +ilmoitusten-selitteet+ parsi-selitteet kuittaustyypit
                     kuittaustyypin-selite]
             :as ilmoitukset]
            [harja.views.ilmoituskuittaukset :as kuittaukset]
            [harja.ui.ikonit :as ikonit]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.tiedot.ilmoitukset.viestit :as v]
            [harja.loki :refer [log]]
            [reagent.core :refer [atom] :as r]))

(defn selitelista [{:keys [selitteet] :as ilmoitus}]
  (let [virka-apu? (ilmoitukset/virka-apupyynto? ilmoitus)]
    [:div.selitelista.inline-block
     (when virka-apu?
       [:div.selite-virkaapu
        [ikonit/livicon-warning-sign] "Virka-apupyyntö"])
     (parsi-selitteet (filter #(not= % :virkaApupyynto) selitteet))]))

(defn ilmoitus [e! ilmoitus]
  (let [nayta-valitykset? (atom false)]
    (fn [e! ilmoitus]
      [:div
       [bs/panel {}
        (ilmoitustyypin-nimi (:ilmoitustyyppi ilmoitus))
        [:span
         [yleiset/tietoja {}
          "Urakka: " (:urakkanimi ilmoitus)
          "Id: " (:ilmoitusid ilmoitus)
          "Tunniste: " (:tunniste ilmoitus)
          "Ilmoitettu: " (pvm/pvm-aika-sek (:ilmoitettu ilmoitus))
          "Tiedotettu HARJAan: " (pvm/pvm-aika-sek (:ilmoitettu ilmoitus)) ;;TODO Maarit
          "Tiedotettu urakkaan: " (pvm/pvm-aika-sek (:ilmoitettu ilmoitus)) ;;TODO Maarit
          "Yhteydenottopyyntö:" (if (:yhteydenottopyynto ilmoitus) "Kyllä" "Ei")
          "Sijainti: " (tr-domain/tierekisteriosoite-tekstina (:tr ilmoitus))
          "Otsikko: " (:otsikko ilmoitus)
          "Paikan kuvaus: " (:paikankuvaus ilmoitus)
          "Lisatieto:  " (when (:lisatieto ilmoitus)
                           [yleiset/pitka-teksti (:lisatieto ilmoitus)])
          "Selitteet: " [selitelista ilmoitus]
          "Toimenpiteet aloitettu: " (when (:toimenpiteet-aloitettu ilmoitus)
                                       (pvm/pvm-aika-sek (:toimenpiteet-aloitettu ilmoitus)))
          "Aiheutti toimenpiteitä:" (if (:aiheutti-toimenpiteita ilmoitus) "Kyllä" "Ei")]
         [:br]
         [yleiset/tietoja {}
          "Ilmoittaja:" (let [henkilo (nayta-henkilo (:ilmoittaja ilmoitus))
                              tyyppi (capitalize (name (get-in ilmoitus [:ilmoittaja :tyyppi])))]
                          (if (and henkilo tyyppi)
                            (str henkilo ", " tyyppi)
                            (str (or henkilo tyyppi))))
          "Puhelinnumero: " (parsi-puhelinnumero (:ilmoittaja ilmoitus))
          "Sähköposti: " (get-in ilmoitus [:ilmoittaja :sahkoposti])]

         [:br]
         [yleiset/tietoja {}
          "Lähettäjä:" (nayta-henkilo (:lahettaja ilmoitus))
          "Puhelinnumero: " (parsi-puhelinnumero (:lahettaja ilmoitus))
          "Sähköposti: " (get-in ilmoitus [:lahettaja :sahkoposti])]

         [:br]
         (when (and
                 (:ilmoitusid ilmoitus)
                 (oikeudet/voi-kirjoittaa?
                   oikeudet/ilmoitukset-ilmoitukset
                   (:id @nav/valittu-urakka)))
           ;; todo: tämä kirjaus ei ole sallittu, jos lopetuskuittaus on jo tehty
           (if (:toimenpiteet-aloitettu ilmoitus)
             [:button.nappi-kielteinen
              {:on-click #(e! (v/->PeruutaToimenpiteidenAloitus (:id ilmoitus)))}
              "Peruuta toimenpiteiden aloitus"]
             [:button.nappi-ensisijainen
              {:on-click #(e! (v/->TallennaToimenpiteidenAloitus (:id ilmoitus)))}
              "Toimenpiteet aloitettu"]))]]
       [:div.kuittaukset
        [:h3 "Kuittaukset"]
        [:div
         ;; Tilannekuvanäkymässä ei voi tehdä kuittauksia, mutta tätä komponenttia käytetään
         ;; näyttämään ilmoituksen tarkempia tietoja. Tällöin e! on nil
         (when e!
           (if-let [uusi-kuittaus (:uusi-kuittaus ilmoitus)]
             [kuittaukset/uusi-kuittaus e! uusi-kuittaus]
             (when (oikeudet/voi-kirjoittaa? oikeudet/ilmoitukset-ilmoitukset
                                             (:id @nav/valittu-urakka))

               (if (:ilmoitusid ilmoitus)
                 [:button.nappi-ensisijainen
                  {:class "uusi-kuittaus-nappi"
                   :on-click #(e! (v/->AvaaUusiKuittaus))}
                  (ikonit/livicon-plus) " Uusi kuittaus"]
                 [yleiset/vihje tiedot/vihje-liito]))))

         [harja.ui.kentat/tee-kentta {:tyyppi :checkbox
                                      :teksti "Näytä välitysviestit"
                                      :nayta-rivina? true} nayta-valitykset?]

         (when-not (empty? (:kuittaukset ilmoitus))
           [:div
            (for [kuittaus (cond->> (sort-by :kuitattu pvm/jalkeen? (:kuittaukset ilmoitus))
                                    (not @nayta-valitykset?) (remove ilmoitukset/valitysviesti?))]
              (kuittaukset/kuittauksen-tiedot kuittaus))])]]])))
