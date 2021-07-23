(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paallystysilmoitukset
  (:require
    [reagent.core :as r]
    [tuck.core :as tuck]
    [cljs-time.core :as t]
    [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
    [harja.tiedot.urakka :as u]
    [harja.tiedot.urakka.paallystys :as t-ur-paallystys]
    [harja.tiedot.urakka.urakka :as tila]
    [harja.tiedot.urakka.yllapito :as t-yllapito]
    [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paallystysilmoitukset :as t-paallystysilmoitukset]
    [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as t-paikkauskohteet]
    [harja.tiedot.hallintayksikot :as hal]
    [harja.tiedot.istunto :as istunto]
    [harja.ui.yleiset :as yleiset]
    [harja.ui.debug :as debug]
    [harja.ui.komponentti :as komp]
    [harja.ui.valinnat :as valinnat]
    [harja.ui.napit :as napit]
    [harja.views.urakka.paallystysilmoitukset :as paallystys]
    [harja.views.urakka.pot2.materiaalikirjasto :as massat-view]
    [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as v-paikkauskohteet]
    [harja.views.kartta :as kartta]
    [harja.views.kartta.tasot :as kartta-tasot]))

;; Refaktoroidaan tarkkailijat pois, sillä, että tehdään filtterit itse uusiksi.
(defn- lisaa-tarkkailija! [e! tarkkailijan-avain polku toinen]
  (e! (t-ur-paallystys/->MuutaTila polku @toinen))
  (add-watch
    toinen
    tarkkailijan-avain
    #(e! (t-ur-paallystys/->MuutaTila polku %4))))

;; Lisätään tarkkailijat ilmoitusluetteloa varten. Kuuntelija tallentaa halutun arvon tilaan, jotta
;; ilmoitusluettelo toimii oikein ilman isompia kikkailuita.
;; kuuntelijan avaimen prefiksi pkp tarkoittaa paikkauskohteiden päällystystä.
(defn- lisaa-tarkkailijat! [e!]
  (do
    (lisaa-tarkkailija! e! :pkp-urakan-vuosi [:urakka-tila :valittu-urakan-vuosi] u/valittu-urakan-vuosi)
    (lisaa-tarkkailija! e! :pkp-sopimusnro [:urakka-tila :valittu-sopimusnumero] u/valittu-sopimusnumero)
    (lisaa-tarkkailija! e! :pkp-tienumero [:yllapito-tila :tienumero] t-yllapito/tienumero)
    (lisaa-tarkkailija! e! :pkp-kohdenumero [:yllapito-tila :kohdenumero] t-yllapito/kohdenumero)))

(defn- poista-tarkkailijat! []
  (remove-watch u/valittu-urakan-vuosi :pkp-urakan-vuosi)
  (remove-watch u/valittu-sopimusnumero :pkp-sopimusnro)
  (remove-watch t-yllapito/tienumero :pkp-tienumero)
  (remove-watch t-yllapito/kohdenumero :pkp-kohdenumero))

(defn- tilan-formatointi 
  [t]
  (if (= :kaikki t)
    "Kaikki"
    (paallystys-ja-paikkaus/kuvaile-ilmoituksen-tila t)))

(defn filtterit [e! app] 
  (let [vuodet (v-paikkauskohteet/urakan-vuodet (:alkupvm (-> @tila/tila :yleiset :urakka)) (:loppupvm (-> @tila/tila :yleiset :urakka)))
        valittu-vuosi (or @u/valittu-urakan-vuosi (get-in app [:urakka-tila :valittu-urakan-vuosi]))
        valitut-elyt (get-in app [:valitut-elyt])
        valitut-tilat (get-in app [:valitut-tilat])
        valittavat-elyt (conj
                         (map (fn [h]
                                (-> h
                                    (dissoc h :alue :type :liikennemuoto)
                                    (assoc :valittu? (or (some #(= (:id h) %) valitut-elyt) ;; Onko kyseinen ely valittu
                                                         false))))
                              @hal/vaylamuodon-hallintayksikot)
                         {:id 0 :nimi "Kaikki" :elynumero 0 :valittu? (some #(= 0 %) valitut-elyt)})
        valittavat-tilat 
        (map (fn [t]
               {:nimi t
                :valittu? (or (some #(= t %) valitut-tilat) ;; Onko kyseinen tila valittu
                              false)})
             #{:aloitettu :valmis :lukittu :aloittamatta :kaikki})]
    [:div.flex-row.filtterit.paallystysilmoitukset.alkuun.valistys16.padding16.tasaa-alas
     ;;TODO: Ely valinta on varmaan näistä vähiten tärkeä
     [:div.basis256
      [:label.alasvedon-otsikko-vayla "ELY"]
      [valinnat/checkbox-pudotusvalikko
       valittavat-elyt
       (fn [ely valittu?]
         (e! (t-paikkauskohteet/->FiltteriValitseEly ely valittu?)))
       [" ELY valittu" " ELYä valittu"]
       {:vayla-tyyli? true}]]
     ;; Hox! Kehittäessa ja auto reloadin kanssa touhutessa kuuntelijat menevät rikki. Jos vuosi ei vaihdu lokaalisti
     ;; niin ei syytä huoleen. Käy eri välilehdellä ja kaikki palaa toimintakuntoon
     [:div.basis128
      [:label.alasvedon-otsikko-vayla "Vuosi"]
      [yleiset/livi-pudotusvalikko
       {:valinta valittu-vuosi
        :vayla-tyyli? true
        :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}
        :valitse-fn #(u/valitse-urakan-vuosi! %)}
       vuodet]]
     [:div.basis256
      [:label.alasvedon-otsikko-vayla "Tila"]
      [valinnat/checkbox-pudotusvalikko
       valittavat-tilat
       (fn [tila valittu?]
         (e! (t-paallystysilmoitukset/->FiltteriValitseTila tila valittu?)))
       [" Tila valittu" " Tilaa valittu"]
       {:vayla-tyyli? true
        :fmt tilan-formatointi}]]
     [:div.basis128
      [napit/yleinen-ensisijainen "Hae" #(e! (t-ur-paallystys/->HaePaallystysilmoitukset)) {:luokka "nappi-korkeus-36"}]]
     [:div.basis128.oikealle
      [kartta/piilota-tai-nayta-kartta-nappula]]]))

(defn paallystysilmoitukset* [e! _]
  (komp/luo
    (komp/sisaan-ulos #(do
                         (e! (t-ur-paallystys/->MuutaTila [:valitut-tilat] #{:kaikki}))
                         (e! (t-ur-paallystys/->MuutaTila [:urakka] (:urakka @tila/yleiset)))
                         (kartta-tasot/taso-pois! :paikkaukset-toteumat)
                         (kartta-tasot/taso-pois! :paikkaukset-paikkauskohteet)
                         (kartta-tasot/taso-paalle! :paikkaukset-paallystysilmoitukset)
                         (lisaa-tarkkailijat! e!))
                      #(do
                         (e! (t-ur-paallystys/->MuutaTila [:valitut-tilat] #{"Kaikki"}))
                         (kartta-tasot/taso-pois! :paikkaukset-paallystysilmoitukset)
                         (poista-tarkkailijat!)))
    (fn [e! app]
      (let [app (assoc app :kayttaja @istunto/kayttaja)]
        [:div
         [:h1 "Paikkauskohteiden päällystysilmoitukset"]
         [debug/debug app]
         ;;TODO: Kartalle pitää piirtää kaikkien päällystysilmoitusten paikat, mutta se on vielä tekemättä
         [kartta/kartan-paikka]
         ;; Jostain syystä urakkaa ei aina keretä ladata kokonaan sovelluksen tilaan, mikä hajoittaa valinnat-komponetin.
         ;; Odotetaan siis, että urakalta löytyy varmasti alkupvm ennen kuin rendataan mitään.
         (when-not (nil? (:alkupvm (:urakka app)))
           ;; Kun pot lomake on auki, niin mitään listauksia ei tarvitse näyttää
           [:div
            (when (nil? (:paallystysilmoitus-lomakedata app))
              [:div
               ;; Filtterit on niin erilaiset näissä näkymissä, että yritetään ensin tehdä käsin täysin omanlaisenna
               [filtterit e! app]
               ;; Listataan päällystysilmoitukset ja paikkauskohteet jotka eivät ole vielä päällystysilmoituksia
               [paallystys/ilmoitusluettelo e! app]])
            [:div
             ;; Renderöidään päällystysilmoitusten tärkeimmät toiminnot
             [paallystys/paallystysilmoitukset e! app]]])
         [massat-view/materiaalikirjasto-modal e! app]]))))

(defn paallystysilmoitukset [e! app-state]
  [paallystysilmoitukset* e! app-state])
