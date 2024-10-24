(ns harja.views.tilannekuva.tienakyma
  "Tienäkymä tilannekuvaan.

  Tienäkymä (aka 'supernäkymä') mahdollistaa tietyn tieosan
  ja aikavälin valinnan ja hakee kaiken mitä kyseisellä tiellä
  on tapahtunut kyseisellä aikavälillä.

  Tämä on tärkeä käyttäjille kun tutkitaan vaikka onnettomuutta.
  Silloin halutaan saada tietyltä ajalta kaikki mitä kyseisellä
  tieosuudella on tehty näkyviin (auraukset, tarkastukset, ilmoitukset jne).

  Tienäkymässä on oma lomakkeensa hakuparametrien syöttämistä varten
  (valinnat)."

  (:require [harja.tiedot.tilannekuva.tienakyma :as tiedot]
            [harja.ui.lomake :as lomake]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset]
            [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.loki :refer [log]]
            [harja.ui.komponentti :as komp]
            [harja.views.kartta.infopaneeli :as infopaneeli]
            [harja.views.tilannekuva.tilannekuva-jaettu :as jaettu]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.views.kartta.tasot :as tasot]
            [harja.views.kartta :as kartta]))

(def tr-osoite-taytetty? (every-pred :numero :alkuosa :alkuetaisyys :loppuosa :loppuetaisyys))

(defn- valinnat
  "Valintalomake tienäkymälle."
  [e! {:keys [valinnat haku-kaynnissa? tulokset] :as app}]
  [lomake/lomake
   {:otsikko "Tarkastele tien tietoja"
    :muokkaa! #(e! (tiedot/->PaivitaValinnat %))
    :footer-fn (fn [data]
                 [:span
                  [napit/yleinen-toissijainen
                   "Hae"
                   #(e! (tiedot/->Hae))
                   {:ikoni (ikonit/livicon-search)
                    :disabled (or (nil? (:sijainti valinnat))
                                  (not (lomake/validi? data)))}]
                  (when haku-kaynnissa?
                    [yleiset/ajax-loader "Haetaan tietoja..." {:luokka "inline-block"}])])
    :ei-borderia? true}
   [{:nimi :tierekisteriosoite :tyyppi :tierekisteriosoite
     :tyyli :rivitetty
     :pakollinen? true
     :sijainti (r/wrap (:sijainti valinnat)
                       #(e! (tiedot/->PaivitaSijainti %)))
     :otsikko "Tieosoite"
     :palstoja 3
     :validoi [(fn [osoite {sijainti :sijainti}]
                 (when (and (tr-osoite-taytetty? osoite)
                            (nil? sijainti))
                   "Tarkista tierekisteriosoite"))]}

    {:nimi :alku :tyyppi :pvm-aika :pakota-suunta :ylos-vasen
     :pakollinen? true
     :otsikko "Alkaen" :palstoja 3}
    {:nimi :loppu :tyyppi :pvm-aika :pakota-suunta :ylos-vasen
     :pakollinen? true
     :otsikko "Loppuen" :palstoja 3}]
   valinnat])



(defn tienakyman-infopaneelin-linkit [e!]
  {:toteuma
   [{:teksti "Siirry toteumanäkymään"
     :ikoni [ikonit/livicon-eye]
     :toiminto #(e! (tiedot/->TarkasteleToteumaa %))}
    {:teksti-fn #(if (tiedot/toteuman-reittipisteet-naytetty? %)
                   "Piilota reittipisteet"
                   "Näytä reittipisteet kartalla")
     :ikoni-fn #(if (tiedot/toteuman-reittipisteet-naytetty? %)
                  [ikonit/livicon-delete]
                  [ikonit/livicon-info-circle])
     :toiminto #(e! (tiedot/->HaeToteumanReittipisteet %))}]

   :varustetoteuma
   [{:teksti "Toteumanäkymään"
     :tooltip "Siirry urakan varustetoteumiin"
     :ikoni [ikonit/livicon-eye]
     :toiminto #(e! (tiedot/->TarkasteleToteumaa %))}]
   :ilmoitus
   {:toiminto #(jaettu/nayta-kuittausten-tiedot (:kuittaukset %))
    :teksti "Näytä kuittaukset"}})

(defn- nayta-tulospaneeli! [e! tulokset avatut-tulokset]
  ;; Poistetaan TR-valinnan katkoviiva häiritsemästä
  (tasot/poista-geometria! :tr-valittu-osoite)
  
  (kartta-tiedot/piilota-infopaneeli!)

  (kartta-tiedot/nayta-kartan-kontrollit!
   :tienakyma-tulokset
   ^{:class "kartan-infopaneeli"}
   [infopaneeli/infopaneeli-komponentti
    {:avatut-asiat (comp avatut-tulokset :idx :data)
     :toggle-asia! #(e! (tiedot/->AvaaTaiSuljeTulos (:idx (:data %))))
     :piilota-fn! #(e! (tiedot/->SuljeInfopaneeli))
     :linkkifunktiot (tienakyman-infopaneelin-linkit e!)}
    tulokset]))

(defn- tulospaneeli [e! tulokset avatut-tulokset]
  (komp/luo
   (komp/sisaan-ulos #(do (reset! kartta-tiedot/ikonien-selitykset-sijainti :vasen)
                          (nayta-tulospaneeli! e! tulokset avatut-tulokset))
                     #(do (reset! kartta-tiedot/ikonien-selitykset-sijainti :oikea)
                          (kartta-tiedot/poista-kartan-kontrollit! :tienakyma-tulokset)))
   (komp/kun-muuttuu nayta-tulospaneeli!)
   (fn [_ _ _]
     [:span.tienakyma-tulokset])))

(defn- tienakyma* [e! app]
  (komp/luo
   (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa true))
                          ;; Tilannekuvan "emonäkymään" tultaessa näytetään kartalla
                          ;; päivitysspinneri, koska nykytilanteessa ja historiakuvassa
                          ;; tehdään automaattisesti hakuja näkymään tultaessa. Tienäkymän
                          ;; karttaa ei lähdetä päivittämään automaattisesti, eikä päivitysspinneria
                          ;; käytetä tienäkymässä muuten, joten poistetaan spinneri.
                          (kartta/aseta-paivitetaan-karttaa-tila! false))
                     #(do (e! (tiedot/->Nakymassa false))))
   (komp/ulos (kartta-tiedot/aseta-klik-kasittelija!
               (fn [{t :geometria}]
                 (when-let [idx (:idx t)]
                   (e! (tiedot/->AvaaTaiSuljeTulos (:idx t)))))))
   (fn [e! {:keys [tulokset avatut-tulokset] :as app}]
     [:span
      [valinnat e! app]
      (when tulokset
        [tulospaneeli e! tulokset avatut-tulokset])])))

(defn tienakyma
  []
  [tuck/tuck tiedot/tienakyma tienakyma*])
