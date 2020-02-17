(ns harja.tiedot.urakka.paallystys
  "Päällystyksen tiedot"
  (:require
    [reagent.core :refer [atom] :as r]
    [tuck.core :refer [process-event] :as tuck]
    [harja.tyokalut.tuck :as tuck-apurit]
    [harja.loki :refer [log tarkkaile!]]
    [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
    [harja.tiedot.urakka.paallystys-muut-kustannukset :as muut-kustannukset]
    [cljs.core.async :refer [<! put!]]
    [clojure.string :as clj-str]
    [cljs-time.core :as t]
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
    [harja.ui.viesti :as viesti]
    [harja.ui.modal :as modal]
    [harja.ui.grid.gridin-muokkaus :as gridin-muokkaus]
    [harja.ui.lomakkeen-muokkaus :as lomakkeen-muokkaus])

  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<! reaction-writable]]))

(def kohdeluettelossa? (atom false))
(def paallystysilmoitukset-tai-kohteet-nakymassa? (atom false))
(def validointivirheet-modal (atom nil))
;; Tämä tila on tuckia varten
(defonce tila (atom {:pot-jarjestys :tila}))
;; Tämän alla on joitain kursoreita tilaan, jotta vanhat jutut toimisi.
;; Näitä ei pitäisi tarvita refaktoroinnin päätteeksi
(defonce yllapitokohde-id (r/cursor tila [:paallystysilmoitus-lomakedata :yllapitokohde-id]))

(defn hae-paallystysilmoitukset [urakka-id sopimus-id vuosi]
  (k/post! :urakan-paallystysilmoitukset {:urakka-id urakka-id
                                          :sopimus-id sopimus-id
                                          :vuosi vuosi}))

;; Nämä reactionit ovat tässä vielä, koska paallystyskohteet ns käyttää näitä.
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

(def tr-osien-tiedot (atom nil))

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
                         yllapitokohde-id @yllapitokohde-id]
                     (when (and taso paallystyskohteet)
                       (yllapitokohteet/yllapitokohteet-kartalle
                         paallystyskohteet
                         {:yllapitokohde-id yllapitokohde-id})))))

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

(defn jarjesta-paallystysilmoitukset [paallystysilmoitukset jarjestys]
  (when paallystysilmoitukset
    (case jarjestys
      :kohdenumero
      (sort-by #(yllapitokohde-domain/kohdenumero-str->kohdenumero-vec (:kohdenumero %)) paallystysilmoitukset)

      :muokkausaika
      ;; Muokkausajalliset ylimmäksi, ei-muokatut sen jälkeen kohdenumeron mukaan
      (concat (sort-by :muokattu t/after? (filter #(some? (:muokattu %)) paallystysilmoitukset))
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

(defn virheviestit-komponentti [virheet]
  (for [[virhe-otsikko virheviestit] virheet]
    ^{:key virhe-otsikko}
    [:div
     [:p (str (when virhe-otsikko
                (clj-str/capitalize (clj-str/replace (name virhe-otsikko)
                                                     "-" " "))) ": ")]
     (into [:ul] (map-indexed (fn [i virheviesti]
                                ^{:key i}
                                [:li virheviesti])
                              virheviestit))]))

(defn virhe-modal [vastaus otsikko]
  (let [virhe (:virhe vastaus)]
    (modal/nayta!
      {:otsikko otsikko
       :otsikko-tyyli :virhe}
      (when virhe
        (concat
          ;; gensym on tässä vain poistamassa virheilmoituksen. Se ei estä remounttailua.
          (interpose '(^{:key (str (gensym))} [:p "------------"])
                     (map virheviestit-komponentti virhe)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pikkuhiljaa tätä muutetaan tuckin yhden atomin maalimaan

(defrecord AvaaPaallystysilmoituksenLukitus [])
(defrecord AvaaPaallystysilmoituksenLukitusOnnistui [vastaus paallystyskohde-id])
(defrecord AvaaPaallystysilmoituksenLukitusEpaonnistui [vastaus])
(defrecord AvaaPaallystysilmoitus [paallystyskohde-id])
(defrecord HaePaallystysilmoitukset [])
(defrecord HaePaallystysilmoituksetOnnnistui [vastaus])
(defrecord HaePaallystysilmoituksetEpaonnisuti [vastaus])
(defrecord HaePaallystysilmoitusPaallystyskohteellaOnnnistui [vastaus])
(defrecord HaePaallystysilmoitusPaallystyskohteellaEpaonnisuti [vastaus])
(defrecord HaeTrOsienPituudet [tr-numero tr-alkuosa tr-loppuosa])
(defrecord HaeTrOsienPituudetOnnistui [vastaus tr-numero])
(defrecord HaeTrOsienPituudetEpaonnistui [vastaus])
(defrecord HaeTrOsienTiedot [tr-numero tr-alkuosa tr-loppuosa])
(defrecord HaeTrOsienTiedotOnnistui [vastaus tr-numero])
(defrecord HaeTrOsienTiedotEpaonnistui [vastaus])
(defrecord HoidaCtrl+Z [])
(defrecord JarjestaYllapitokohteet [jarjestys])
(defrecord KumoaHistoria [])
(defrecord MuutaTila [polku arvo])
(defrecord PaivitaTila [polku f])
(defrecord SuodataYllapitokohteet [])
(defrecord TallennaHistoria [polku])
(defrecord TallennaPaallystysilmoitus [])
(defrecord TallennaPaallystysilmoitusOnnistui [vastaus])
(defrecord TallennaPaallystysilmoitusEpaonnistui [vastaus])
(defrecord TallennaPaallystysilmoitustenTakuuPaivamaarat [paallystysilmoitus-rivit takuupvm-tallennus-kaynnissa-kanava])
(defrecord TallennaPaallystysilmoitustenTakuuPaivamaaratOnnistui [vastaus takuupvm-tallennus-kaynnissa-kanava])
(defrecord TallennaPaallystysilmoitustenTakuuPaivamaaratEpaonnistui [vastaus takuupvm-tallennus-kaynnissa-kanava])
(defrecord YHAVientiOnnistui [paallystysilmoitukset])
(defrecord YHAVientiEpaonnistui [vastaus])

(extend-protocol tuck/Event
  AvaaPaallystysilmoituksenLukitus
  (process-event [_ {{urakka-id :id} :urakka
                     {:keys [paallystyskohde-id]} :paallystysilmoitus-lomakedata :as app}]
    (let [parametrit {::urakka-domain/id urakka-id
                      ::pot/paallystyskohde-id paallystyskohde-id
                      ::pot/tila :valmis}]
      (tuck-apurit/post! app
                         :aseta-paallystysilmoituksen-tila
                         parametrit
                         {:onnistui ->AvaaPaallystysilmoituksenLukitusOnnistui
                          :onnistui-parametrit [paallystyskohde-id]
                          :epaonnistui ->AvaaPaallystysilmoituksenLukitusEpaonnistui})))
  AvaaPaallystysilmoituksenLukitusOnnistui
  (process-event [{:keys [vastaus paallystyskohde-id]} app]
    ;; TODO Tämä on tässä vielä tukemassa vanhaa koodia
    (harja.atom/paivita! paallystysilmoitukset)
    (let [{:keys [tila]} vastaus
          paivitys-fn (fn [paallystysilmoitukset]
                        (mapv #(if (= (:paallystyskohde-id %) paallystyskohde-id)
                                 (assoc % :tila tila)
                                 %)
                              paallystysilmoitukset))]
      (-> app
          (assoc-in [:paallystysilmoitus-lomakedata :perustiedot :tila] tila)
          (assoc-in [:paallystysilmoitus-lomakedata :validoi-lomake?] true)
          (update :paallystysilmoitukset paivitys-fn)
          (update :kaikki-paallystysilmoitukset paivitys-fn))))
  AvaaPaallystysilmoituksenLukitusEpaonnistui
  (process-event [vastaus app]
    (viesti/nayta! "Lukituksen avaus epäonnistui" :warning)
    app)
  MuutaTila
  (process-event [{:keys [polku arvo]} app]
    (assoc-in app polku arvo))
  PaivitaTila
  (process-event [{:keys [polku f]} app]
    (update-in app polku f))
  SuodataYllapitokohteet
  (process-event [_ {paallystysilmoitukset :paallystysilmoitukset
                     kaikki-paallystysilmoitukset :kaikki-paallystysilmoitukset
                     {:keys [tienumero kohdenumero]} :yllapito-tila :as app}]
    (if paallystysilmoitukset
      (assoc app :paallystysilmoitukset
             (yllapitokohteet/suodata-yllapitokohteet kaikki-paallystysilmoitukset {:tienumero tienumero
                                                                             :kohdenumero kohdenumero}))
      app))
  HaePaallystysilmoitukset
  (process-event [_ {{urakka-id :id} :urakka
                     {:keys [valittu-sopimusnumero valittu-urakan-vuosi]} :urakka-tila
                     :as app}]
    (let [parametrit {:urakka-id urakka-id
                      :sopimus-id (first valittu-sopimusnumero)
                      :vuosi valittu-urakan-vuosi}]
      (-> app
          (tuck-apurit/post! :urakan-paallystysilmoitukset
                             parametrit
                             {:onnistui ->HaePaallystysilmoituksetOnnnistui
                              :epaonnistui ->HaePaallystysilmoituksetEpaonnisuti})
          (assoc :kiintioiden-haku-kaynnissa? true)
          (dissoc :paallystysilmoitukset))))
  HaePaallystysilmoituksetOnnnistui
  (process-event [{vastaus :vastaus} {pot-jarjestys :pot-jarjestys :as app}]
    (let [paallystysilmoitukset (jarjesta-paallystysilmoitukset vastaus pot-jarjestys)]
      ;; :paallystysilmoitukset ja :kaikki-paallystysilmoitukset ero on siinä, että :paallystysilmoitukset
      ;; sisältää vain ne päällystysilmoitukset, joita käyttäjä ei ole filtteröinyt pois. Pidetään kummiskin
      ;; kaikki päällystysilmoitukset :kaikki-paallystysilmoitukset avaimen sisällä, jottei tarvitse aina
      ;; filtteröinnin yhteydessä tehdä kantakyselyä
      (assoc app :paallystysilmoitukset paallystysilmoitukset
             :kaikki-paallystysilmoitukset paallystysilmoitukset)))
  HaePaallystysilmoituksetEpaonnisuti
  (process-event [{vastaus :vastaus} app]
    app)
  HaePaallystysilmoitusPaallystyskohteellaOnnnistui
  (process-event [{vastaus :vastaus} {urakka :urakka :as app}]
    (let [;; Leivotaan jokaiselle kannan JSON-rakenteesta nostetulle alustatoimelle id järjestämistä varten
          vastaus (-> vastaus
                      (update-in [:ilmoitustiedot :osoitteet]
                                 (fn [osoitteet]
                                   (let [osoitteet-jarjestyksessa (tr-domain/jarjesta-tiet osoitteet)]
                                     (into {}
                                           (map #(identity [%1 %2])
                                                (iterate inc 1) osoitteet-jarjestyksessa)))))
                      (update-in [:ilmoitustiedot :alustatoimet]
                                 (fn [alustatoimet]
                                   (let [alustatoimet-jarjestyksessa (tr-domain/jarjesta-tiet alustatoimet)]
                                     (into {}
                                           (map #(identity [%1 (assoc %2 :id %1)])
                                                (iterate inc 1) alustatoimet-jarjestyksessa))))))
          perustiedot-avaimet #{:aloituspvm :asiatarkastus :tila :kohdenumero :tunnus :kohdenimi
                                :tr-ajorata :tr-kaista :tr-numero :tr-alkuosa :tr-alkuetaisyys
                                :tr-loppuosa :tr-loppuetaisyys :kommentit :tekninen-osa
                                :valmispvm-kohde :takuupvm :valmispvm-paallystys
                                }
          perustiedot (select-keys vastaus perustiedot-avaimet)
          muut-tiedot (apply dissoc vastaus perustiedot-avaimet)]
      (-> app
          (assoc-in [:paallystysilmoitus-lomakedata :kirjoitusoikeus?]
                    (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                              (:id urakka)))
          (assoc-in [:paallystysilmoitus-lomakedata :perustiedot]
                    perustiedot)
          ;; TODO tätä logikkaa voisi refaktoroida. Nyt kohteen tr-osoitetta säliytetään yhtäaikaa kahdessa
          ;; eri paikassa. Yksi on :perustiedot avaimen alla, jota oikeasti käytetään aikalaila kaikeen muuhun
          ;; paitsi validointiin. Validointi hoidetaan [:perustiedot :tr-osoite] polun alta.
          (assoc-in [:paallystysilmoitus-lomakedata :perustiedot :tr-osoite]
                    (select-keys perustiedot
                                 #{:tr-numero :tr-alkuosa :tr-alkuetaisyys
                                   :tr-loppuosa :tr-loppuetaisyys :tr-ajorata :tr-kaista}))
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
      (tuck-apurit/post! app :hae-tr-pituudet
                         parametrit
                         {:onnistui ->HaeTrOsienPituudetOnnistui
                          :onnistui-parametrit [tr-numero]
                          :epaonnistui ->HaeTrOsienPituudetEpaonnistui})))
  HaeTrOsienPituudetOnnistui
  (process-event [{:keys [vastaus tr-numero]} app]
    (let [pituudet (reduce-kv (fn [m osa v]
                                (assoc m osa (assoc v :pituus (+ (get v 0 0)
                                                                 (max (get v 1 0)
                                                                      (get v 2 0))))))
                              {} vastaus)]
      (update-in app [:paallystysilmoitus-lomakedata :tr-osien-pituudet] (fn [vanhat]
                                                                           (update vanhat tr-numero
                                                                                   (fn [vanhat-osuudet]
                                                                                     (merge vanhat-osuudet pituudet)))))))
  HaeTrOsienPituudetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    app)
  HaeTrOsienTiedot
  (process-event [{:keys [tr-numero tr-alkuosa tr-loppuosa]} app]
    (let [parametrit {:tr-numero tr-numero :tr-alkuosa tr-alkuosa :tr-loppuosa tr-loppuosa}]
      (tuck-apurit/post! app :hae-tr-tiedot
                         parametrit
                         {:onnistui ->HaeTrOsienTiedotOnnistui
                          :onnistui-parametrit [(:tr-numero parametrit)]
                          :epaonnistui ->HaeTrOsienTiedotEpaonnistui})))
  HaeTrOsienTiedotOnnistui
  (process-event [{:keys [vastaus tr-numero]} app]
    (update app :paallystysilmoitus-lomakedata (fn [vanhat]
                                                 (-> vanhat
                                                     (assoc-in [:tr-osien-tiedot tr-numero] vastaus)
                                                     (assoc :validoi-lomake? true)))))
  HaeTrOsienTiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    ;;TODO tähän joku järkevä handlaus
    app)
  HoidaCtrl+Z
  (process-event [_ {{historia :historia} :paallystysilmoitus-lomakedata :as app}]
    (process-event (->KumoaHistoria) app))
  KumoaHistoria
  (process-event [_ {{historia :historia} :paallystysilmoitus-lomakedata :as app}]
    (if-not (empty? historia)
      (let [[polku eroavat-arvot] (first historia)]
        (-> app
            (assoc-in polku eroavat-arvot)
            (update-in [:paallystysilmoitus-lomakedata :historia]
                       (fn [vanha-historia]
                         (rest vanha-historia)))))
      app))
  JarjestaYllapitokohteet
  (process-event [{jarjestys :jarjestys} {paallystysilmoitukset :paallystysilmoitukset :as app}]
    (assoc app :paallystysilmoitukset (jarjesta-paallystysilmoitukset paallystysilmoitukset jarjestys)
           :pot-jarjestys jarjestys))
  AvaaPaallystysilmoitus
  (process-event [{paallystyskohde-id :paallystyskohde-id} {urakka :urakka :as app}]
    (let [parametrit {:urakka-id (:id urakka)
                      :paallystyskohde-id paallystyskohde-id}]
      (tuck-apurit/post! app
                         :urakan-paallystysilmoitus-paallystyskohteella
                         parametrit
                         {:onnistui ->HaePaallystysilmoitusPaallystyskohteellaOnnnistui
                          :epaonnistui ->HaePaallystysilmoitusPaallystyskohteellaEpaonnisuti})))
  TallennaHistoria
  (process-event [{polku :polku} app]
    (let [vanha-arvo (get-in app polku)]
      (update-in app [:paallystysilmoitus-lomakedata :historia] (fn [vanha-historia]
                                                                  (cons [polku vanha-arvo] vanha-historia)))))
  TallennaPaallystysilmoitus
  (process-event [_ {{urakka-id :id :as urakka} :urakka {:keys [valittu-sopimusnumero valittu-urakan-vuosi]} :urakka-tila paallystysilmoitus-lomakedata :paallystysilmoitus-lomakedata :as app}]
    (let [lahetettava-data (-> paallystysilmoitus-lomakedata
                               ;; Otetaan vain backin tarvitsema data
                               (select-keys #{:perustiedot :ilmoitustiedot :paallystyskohde-id})
                               (update :ilmoitustiedot dissoc :virheet)
                               (update :perustiedot lomakkeen-muokkaus/ilman-lomaketietoja)
                               (update-in [:perustiedot :asiatarkastus] lomakkeen-muokkaus/ilman-lomaketietoja)
                               (update-in [:perustiedot :tekninen-osa] lomakkeen-muokkaus/ilman-lomaketietoja)
                               ;; Poistetaan pituus
                               (update-in [:ilmoitustiedot :osoitteet] #(into (sorted-map)
                                                                              (map (fn [[id rivi]]
                                                                                     [id (dissoc rivi :pituus)])
                                                                                   %)))
                               (update-in [:ilmoitustiedot :alustatoimet] #(into (sorted-map)
                                                                                 (map (fn [[id rivi]]
                                                                                        [id (dissoc rivi :pituus)])
                                                                                      %)))
                               ;; Filteröidään uudet poistetut
                               (update-in [:ilmoitustiedot :osoitteet] #(gridin-muokkaus/filteroi-uudet-poistetut
                                                                          (into (sorted-map)
                                                                                %)))
                               (update-in [:ilmoitustiedot :alustatoimet] #(gridin-muokkaus/filteroi-uudet-poistetut
                                                                             (into (sorted-map)
                                                                                   %)))
                               ;; POT-lomake tallentuu kantaan JSONina, eikä se tarvitse id-tietoja.
                               (gridin-muokkaus/poista-idt [:ilmoitustiedot :osoitteet])
                               (gridin-muokkaus/poista-idt [:ilmoitustiedot :alustatoimet])
                               ;; Poistetaan poistetut elementit
                               (gridin-muokkaus/poista-poistetut [:ilmoitustiedot :osoitteet])
                               (gridin-muokkaus/poista-poistetut [:ilmoitustiedot :alustatoimet]))]
      (log "[PÄÄLLYSTYS] Lomake-data: " (pr-str paallystysilmoitus-lomakedata))
      (log "[PÄÄLLYSTYS] Lähetetään data " (pr-str lahetettava-data))
      (tuck-apurit/post! app :tallenna-paallystysilmoitus
                         {:urakka-id urakka-id
                          :sopimus-id (first valittu-sopimusnumero)
                          :vuosi valittu-urakan-vuosi
                          :paallystysilmoitus lahetettava-data}
                         {:onnistui ->TallennaPaallystysilmoitusOnnistui
                          :epaonnistui ->TallennaPaallystysilmoitusEpaonnistui
                          :paasta-virhe-lapi? true})))
  TallennaPaallystysilmoitusOnnistui
  (process-event [{vastaus :vastaus} {{urakka-id :id :as urakka} :urakka jarjestys :pot-jarjestys :as app}]
    (log "[PÄÄLLYSTYS] Lomake tallennettu onnistuneesti, vastaus: " (pr-str vastaus))
    (let [jarjestetyt-ilmoitukset (jarjesta-paallystysilmoitukset (:paallystysilmoitukset vastaus) jarjestys)]
      (urakka/lukitse-urakan-yha-sidonta! urakka-id)
      ;; TODO Nämä pois, kun refaktorointi valmis
      (reset! paallystysilmoitukset jarjestetyt-ilmoitukset)
      (reset! yllapitokohteet (:yllapitokohteet vastaus))
      (assoc app :paallystysilmoitus-lomakedata nil
             :kaikki-paallystysilmoitukset jarjestetyt-ilmoitukset
             :paallystysilmoitukset jarjestetyt-ilmoitukset)))
  TallennaPaallystysilmoitusEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (log "[PÄÄLLYSTYS] Lomakkeen tallennus epäonnistui, vastaus: " (pr-str vastaus))
    (virhe-modal {:virhe [(reduce-kv (fn [m k v]
                                       (assoc m k (distinct
                                                    (flatten
                                                      (vals (if (map? v)
                                                              v
                                                              (map (fn [kohde] (second kohde)) v)))))))
                                     {} (:virhe vastaus))]}
                 "Päällystysilmoituksen tallennus epäonnistui!")
    app)
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
  (process-event [{vastaus :vastaus} app]
    (virhe-modal [{:virhe-kohteen-lahetyksessa [vastaus]}]
                 "Vienti YHA:an epäonnistui!")
    (assoc app :paallystysilmoitukset (:paallystysilmoitukset vastaus))))
