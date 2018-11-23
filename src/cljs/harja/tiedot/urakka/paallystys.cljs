(ns harja.tiedot.urakka.paallystys
  "Päällystyksen tiedot"
  (:require
    [reagent.core :refer [atom] :as r]
    [tuck.core :refer [process-event] :as tuck]
    [harja.tyokalut.tuck :as tuck-apurit]
    [harja.tiedot.muokkauslukko :as lukko]
    [harja.loki :refer [log tarkkaile!]]
    [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
    [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
    [harja.tiedot.urakka.paallystys-muut-kustannukset :as muut-kustannukset]
    [cljs.core.async :refer [<! put!]]
    [harja.atom :refer [paivita!]]
    [harja.asiakas.kommunikaatio :as k]
    [harja.tiedot.navigaatio :as nav]
    [harja.tiedot.urakka :as urakka]
    [harja.domain.tierekisteri :as tr-domain]
    [harja.domain.oikeudet :as oikeudet]
    [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
    [harja.domain.paallystysilmoitus :as pot]
    [harja.domain.urakka :as urakka-domain]
    [harja.tiedot.urakka.yllapito :as yllapito-tiedot]
    [harja.domain.yllapitokohde :as yllapitokohde-domain]
    [harja.ui.viesti :as viesti])

  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<! reaction-writable]]))

(def kohdeluettelossa? (atom false))
(def paallystysilmoitukset-tai-kohteet-nakymassa? (atom false))
(def validointivirheet-modal (atom nil))
;; Tämä tila on tuckia varten
(defonce tila (atom nil))
;; Tämän alla on joitain kursoreita tilaan, jotta vanhat jutut toimisi.
;; Näitä ei pitäisi tarvita refaktoroinnin päätteeksi
(defonce paallystysilmoitus-lomakedata (r/cursor tila [:paallystysilmoitus-lomakedata]))

(defn hae-paallystysilmoitukset [urakka-id sopimus-id vuosi]
  (k/post! :urakan-paallystysilmoitukset {:urakka-id urakka-id
                                          :sopimus-id sopimus-id
                                          :vuosi vuosi}))

(defn tallenna-paallystysilmoitus! [{:keys [urakka-id sopimus-id vuosi lomakedata]}]
  (k/post! :tallenna-paallystysilmoitus {:urakka-id urakka-id
                                         :sopimus-id sopimus-id
                                         :vuosi vuosi
                                         :paallystysilmoitus lomakedata}))

(def paallystysilmoitukset
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               vuosi @urakka/valittu-urakan-vuosi
               [valittu-sopimus-id _] @urakka/valittu-sopimusnumero
               nakymassa? @paallystysilmoitukset-tai-kohteet-nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                (hae-paallystysilmoitukset valittu-urakka-id valittu-sopimus-id vuosi))))

(defonce karttataso-paallystyskohteet (atom false))

(def yllapitokohteet
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               vuosi @urakka/valittu-urakan-vuosi
               [valittu-sopimus-id _] @urakka/valittu-sopimusnumero
               nakymassa? @kohdeluettelossa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                (yllapitokohteet/hae-yllapitokohteet valittu-urakka-id valittu-sopimus-id vuosi))))

(def yllapitokohteet-suodatettu
  (reaction (let [tienumero @yllapito-tiedot/tienumero
                  yllapitokohteet @yllapitokohteet
                  kohdenumero @yllapito-tiedot/kohdenumero
                  kohteet (when yllapitokohteet
                            (yllapitokohteet/suodata-yllapitokohteet yllapitokohteet {:tienumero tienumero
                                                                                      :kohdenumero kohdenumero}))]
              kohteet)))

(def yhan-paallystyskohteet
  (reaction-writable
    (let [kohteet @yllapitokohteet-suodatettu
          yhan-paallystyskohteet (when kohteet
                                   (yllapitokohteet/suodata-yllapitokohteet
                                     kohteet
                                     {:yha-kohde? true :yllapitokohdetyotyyppi :paallystys}))]
      (tr-domain/jarjesta-kohteiden-kohdeosat yhan-paallystyskohteet))))

(def harjan-paikkauskohteet
  (reaction-writable
    (let [kohteet @yllapitokohteet-suodatettu
          harjan-paikkauskohteet (when kohteet
                                   (yllapitokohteet/suodata-yllapitokohteet
                                     kohteet
                                     {:yha-kohde? false :yllapitokohdetyotyyppi :paikkaus}))]
      (tr-domain/jarjesta-kohteiden-kohdeosat harjan-paikkauskohteet))))

(def kaikki-kohteet
  (reaction (concat @yhan-paallystyskohteet @harjan-paikkauskohteet (when muut-kustannukset/kohteet
                                                                      @muut-kustannukset/kohteet))))

(defonce paallystyskohteet-kartalla
  (reaction (let [taso @karttataso-paallystyskohteet
                  paallystyskohteet @yhan-paallystyskohteet
                  lomakedata @paallystysilmoitus-lomakedata]
              (when (and taso paallystyskohteet)
                (yllapitokohteet/yllapitokohteet-kartalle
                  paallystyskohteet
                  lomakedata)))))

;; Yhteiset UI-asiat

(def paallyste-grid-skeema
  {:otsikko "Päällyste"
   :nimi :paallystetyyppi
   :tyyppi :valinta
   :valinta-arvo :koodi
   :valinta-nayta (fn [rivi]
                    (if (:koodi rivi)
                      (str (:lyhenne rivi) " - " (:nimi rivi))
                      (:nimi rivi)))
   :valinnat paallystys-ja-paikkaus/+paallystetyypit-ja-nil+})

(def raekoko-grid-skeema
  {:otsikko "Rae\u00ADkoko" :nimi :raekoko :tyyppi :numero :desimaalien-maara 0
   :tasaa :oikea
   :validoi [[:rajattu-numero 0 99]]})

(def tyomenetelma-grid-skeema
  {:otsikko "Pääll. työ\u00ADmenetelmä"
   :nimi :tyomenetelma
   :tyyppi :valinta
   :valinta-arvo :koodi
   :valinta-nayta (fn [rivi]
                    (if (:koodi rivi)
                      (str (:lyhenne rivi) " - " (:nimi rivi))
                      (:nimi rivi)))
   :valinnat pot/+tyomenetelmat-ja-nil+})

(defn avaa-paallystysilmoituksen-lukitus!
  [{:keys [urakka-id kohde-id tila]}]
  (k/post! :aseta-paallystysilmoituksen-tila {::urakka-domain/id urakka-id
                                              ::pot/paallystyskohde-id kohde-id
                                              ::pot/tila tila}))


(defn jarjesta-paallystysilmoitukset [paallystysilmoitukset jarjestys]
  (when paallystysilmoitukset
    (case jarjestys
      :kohdenumero
      (sort-by #(yllapitokohde-domain/kohdenumero-str->kohdenumero-vec (:kohdenumero %)) paallystysilmoitukset)

      :muokkausaika
      ;; Muokkausajalliset ylimmäksi, ei-muokatut sen jälkeen kohdenumeron mukaan
      (concat (sort-by :muokattu (filter #(some? (:muokattu %)) paallystysilmoitukset))
              (sort-by #(yllapitokohde-domain/kohdenumero-str->kohdenumero-vec (:kohdenumero %))
                       (filter #(nil? (:muokattu %)) paallystysilmoitukset)))

      :tila
      (sort-by
        (juxt (fn [toteuma] (case (:tila toteuma)
                              :lukittu 0
                              :valmis 1
                              :aloitettu 3
                              4))
              (fn [toteuma] (case (:paatos-tekninen-osa toteuma)
                              :hyvaksytty 0
                              :hylatty 1
                              3)))
        paallystysilmoitukset))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pikkuhiljaa tätä muutetaan tuckin yhden atomin maalimaan

(defrecord MuutaTila [polku arvo])
(defrecord PaivitaTila [polku f])
(defrecord SuodataYllapitokohteet [])
(defrecord HaePaallystysilmoitukset [])
(defrecord HaePaallystysilmoituksetOnnnistui [vastaus])
(defrecord HaePaallystysilmoituksetEpaonnisuti [vastaus])
(defrecord HaePaallystysilmoitusPaallystyskohteellaOnnnistui [vastaus])
(defrecord HaePaallystysilmoitusPaallystyskohteellaEpaonnisuti [vastaus])
(defrecord HaeTrOsienPituudet [tr-numero tr-alkuosa tr-loppuosa])
(defrecord HaeTrOsienPituudetOnnistui [vastaus])
(defrecord HaeTrOsienPituudetEpaonnistui [vastaus])
(defrecord AvaaPaallystysilmoitus [paallystyskohde-id])
(defrecord TallennaPaallystysilmoitustenTakuuPaivamaarat [paallystysilmoitus-rivit takuupvm-tallennus-kaynnissa-kanava])
(defrecord TallennaPaallystysilmoitustenTakuuPaivamaaratOnnistui [vastaus takuupvm-tallennus-kaynnissa-kanava])
(defrecord TallennaPaallystysilmoitustenTakuuPaivamaaratEpaonnistui [vastaus takuupvm-tallennus-kaynnissa-kanava])
(defrecord YHAVientiOnnistui [paallystysilmoitukset])
(defrecord YHAVientiEpaonnistui [paallystysilmoitukset])

(extend-protocol tuck/Event
  MuutaTila
  (process-event [{:keys [polku arvo]} tila]
    (assoc-in tila polku arvo))
  PaivitaTila
  (process-event [{:keys [polku f]} tila]
    (update-in tila polku f))
  SuodataYllapitokohteet
  (process-event [_ {paallystysilmoitukset :paallystysilmoitukset
                     {:keys [tienumero kohdenumero]} :yllapito-tila :as tila}]
    (when paallystysilmoitukset
      (yllapitokohteet/suodata-yllapitokohteet paallystysilmoitukset {:tienumero tienumero
                                                                      :kohdenumero kohdenumero}))
    tila)
  HaePaallystysilmoitukset
  (process-event [_ {{urakka-id :id} :urakka
                     {:keys [valittu-sopimusnumero valittu-urakan-vuosi]} :urakka-tila
                     :as app}]
    (let [parametrit {:urakka-id urakka-id
                      :sopimus-id (first valittu-sopimusnumero)
                      :vuosi valittu-urakan-vuosi}]
      (println "HAETAAN PARAMETRILLA " parametrit)
      (-> app
          (tuck-apurit/post! :urakan-paallystysilmoitukset
                             parametrit
                             {:onnistui ->HaePaallystysilmoituksetOnnnistui
                              :epaonnistui ->HaePaallystysilmoituksetEpaonnisuti})
          (assoc :kiintioiden-haku-kaynnissa? true))))
  HaePaallystysilmoituksetOnnnistui
  (process-event [{vastaus :vastaus} app]
    (println "VASTAUS ONNISTUI: " vastaus)
    (assoc app :paallystysilmoitukset (jarjesta-paallystysilmoitukset vastaus (get-in app [:yllapito-tila :kohdejarjestys]))))
  HaePaallystysilmoituksetEpaonnisuti
  (process-event [{vastaus :vastaus} app]
    (println "VASTAUS EPÄONNISTUI: " vastaus)
    app)
  HaePaallystysilmoitusPaallystyskohteellaOnnnistui
  (process-event [{vastaus :vastaus} {urakka :urakka :as app}]
    (let [;; Leivotaan jokaiselle kannan JSON-rakenteesta nostetulle alustatoimelle id järjestämistä varten
          vastaus (-> vastaus
                      (update-in [:ilmoitustiedot :osoitteet]
                                 (fn [osoitteet]
                                   (into {}
                                         (map #(identity [%1 %2])
                                              (iterate inc 1) osoitteet))))
                      (update-in [:ilmoitustiedot :alustatoimet]
                                 (fn [alustatoimet]
                                   (vec (map #(assoc %1 :id %2)
                                             alustatoimet (iterate inc 1))))))
          perustiedot-avaimet #{:aloituspvm :asiatarkastus :tila :kohdenumero :tunnus :kohdenimi
                        :tr-ajorata :tr-kaista :tr-numero :tr-alkuosa :tr-alkuetaisyys
                        :tr-loppuosa :tr-loppuetaisyys :kommentit :tekninen-osa
                        :valmispvm-kohde :takuupvm :valmispvm-paallystys
                                }
          perustiedot (select-keys vastaus perustiedot-avaimet)
          muut-tiedot (apply dissoc vastaus perustiedot-avaimet)]
      (println "---> PERUSTIEDOT: " perustiedot)
      (println "----> MUUT TIEDOT: " muut-tiedot)
      (-> app
          (assoc-in [:paallystysilmoitus-lomakedata :kirjoitusoikeus?]
                    (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                              (:id urakka)))
          (assoc-in [:paallystysilmoitus-lomakedata :perustiedot]
                    perustiedot)
          (update :paallystysilmoitus-lomakedata #(merge % muut-tiedot)))))
  HaePaallystysilmoitusPaallystyskohteellaEpaonnisuti
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Päällystysilmoituksen haku epäonnistui." :warning viesti/viestin-nayttoaika-lyhyt)
    app)
  HaeTrOsienPituudet
  (process-event [{:keys [tr-numero tr-alkuosa tr-loppuosa]} app]
    (let [parametrit {:tie tr-numero
                      :aosa tr-alkuosa
                      :losa tr-loppuosa}]
      (tuck-apurit/post! app :hae-tr-osien-pituudet
                         parametrit
                         {:onnistui ->HaeTrOsienPituudetOnnistui
                          :epaonnistui ->HaeTrOsienPituudetEpaonnistui})))
  HaeTrOsienPituudetOnnistui
  (process-event [{vastaus :vastaus} app]
    (update-in app [:paallystysilmoitus-lomakedata :tr-osien-pituudet] (fn [vanhat]
                                                                         (merge vanhat vastaus))))
  HaeTrOsienPituudetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (println "TR OSIEN PITUUDEN HAKU EPÄONNISTUI: " vastaus)
    app)
  AvaaPaallystysilmoitus
  (process-event [{paallystyskohde-id :paallystyskohde-id} {urakka :urakka :as app}]
    (let [parametrit {:urakka-id (:id urakka)
                      :paallystyskohde-id paallystyskohde-id}]
      (tuck-apurit/post! app
                         :urakan-paallystysilmoitus-paallystyskohteella
                         parametrit
                         {:onnistui ->HaePaallystysilmoitusPaallystyskohteellaOnnnistui
                          :epaonnistui ->HaePaallystysilmoitusPaallystyskohteellaEpaonnisuti})))
  TallennaPaallystysilmoitustenTakuuPaivamaarat
  (process-event [{paallystysilmoitus-rivit :paallystysilmoitus-rivit
                   takuupvm-tallennus-kaynnissa-kanava :takuupvm-tallennus-kaynnissa-kanava}
                  {{urakka-id :id} :urakka :as app}]
    (let [paallystysilmoitukset (mapv #(do
                                         {::pot/id (:id %)
                                          ::pot/paallystyskohde-id (:paallystyskohde-id %)
                                          ::pot/takuupvm (:takuupvm %)})
                                      paallystysilmoitus-rivit)
          ilmoitukset-joilla-jo-pot (keep #(when (:harja.domain.paallystysilmoitus/id %) %)
                                          paallystysilmoitukset)
          parametrit {::urakka-domain/id urakka-id
                      ::pot/tallennettavat-paallystysilmoitusten-takuupvmt ilmoitukset-joilla-jo-pot}]
      (-> app
          (tuck-apurit/post! :tallenna-paallystysilmoitusten-takuupvmt
                             parametrit
                             {:onnistui ->TallennaPaallystysilmoitustenTakuuPaivamaaratOnnistui
                              :onnistui-parametrit [takuupvm-tallennus-kaynnissa-kanava]
                              :epaonnistui ->TallennaPaallystysilmoitustenTakuuPaivamaaratEpaonnistui
                              :epaonnistui-parametrit [takuupvm-tallennus-kaynnissa-kanava]})
          (assoc :kiintioiden-haku-kaynnissa? true))))
  TallennaPaallystysilmoitustenTakuuPaivamaaratOnnistui
  (process-event [{:keys [takuupvm-tallennus-kaynnissa-kanava vastaus]} app]
    ;; Tämä rivi on vanhaa koodia ja voi ottaa pois, kun refaktorointi on tehty kokonaan
    (harja.atom/paivita! paallystysilmoitukset)
    ;; TODO katso, josko tämän koko prosessin saisi vähän järkevämmäksi. Nyt tehdään yhden kantakyselyn
    ;; jälkeen heti toinen.
    (tuck/action!
      (fn [e!]
        (e! (->HaePaallystysilmoitukset))))
    (put! takuupvm-tallennus-kaynnissa-kanava 0)
    app)
  TallennaPaallystysilmoitustenTakuuPaivamaaratEpaonnistui
  (process-event [{:keys [takuupvm-tallennus-kaynnissa-kanava vastaus]} app]
    (viesti/nayta! "Päällystysilmoitusten takuupäivämäärän tallennus epäonnistui"
                   :warning
                   viesti/viestin-nayttoaika-keskipitka)
    (put! takuupvm-tallennus-kaynnissa-kanava 1)
    app)
  YHAVientiOnnistui
  (process-event [{paallystysilmoitukset :paallystysilmoitukset} app]
    (viesti/nayta! "Kohteet lähetetty onnistuneesti." :success)
    (assoc app :paallystysilmoitukset paallystysilmoitukset))
  YHAVientiEpaonnistui
  (process-event [{paallystysilmoitukset :paallystysilmoitukset} app]
    (viesti/nayta! "Lähetys epäonnistui osalle kohteista. Tarkista kohteiden tiedot." :warning)
    (assoc app :paallystysilmoitukset paallystysilmoitukset)))