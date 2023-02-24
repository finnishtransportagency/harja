(ns harja.tiedot.urakka.paallystys
  "Päällystyksen tiedot"
  (:require
    [reagent.core :refer [atom] :as r]
    [tuck.core :refer [process-event] :as tuck]
    [cognitect.transit :as transit]
    [harja.tyokalut.tuck :as tuck-apurit]
    [harja.loki :refer [log tarkkaile!]]
    [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
    [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paallystysilmoitukset :as paikkausten-paallystysilmoitukset]
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
    [harja.ui.lomakkeen-muokkaus :as lomakkeen-muokkaus]
    [harja.tyokalut.vkm :as vkm]
    [clojure.string :as str]
    [harja.pvm :as pvm]
    [harja.domain.yllapitokohde :as yllapitokohteet-domain]
    [taoensso.timbre :as log]
    [harja.domain.tierekisteri :as tr])

  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<! reaction-writable]]))

(defn- pvm-valintojen-joukossa?
  [valinnat pvm]
  ((into #{}
         (map :pvm valinnat))
   pvm))

(defn takuupvm-valinnat
  [nykyinen-takuupvm]
  (let [nykyinen-vuosi (pvm/vuosi (pvm/nyt))
        valinnat (for [vuotta (range 1 4)]
                   {:pvm (pvm/vuoden-viim-pvm (+ vuotta nykyinen-vuosi))
                    :fmt (str vuotta (if (= 1 vuotta)
                                       " vuosi"
                                       " vuotta"))})]
    (if (or
          (nil? nykyinen-takuupvm)
          ;; vanhoissa pot-lomakkeissa voi olla takuupvm:iä jotka eivät ole vuoden
          ;; viimeisiä päiviä. Siksi taaksepäin yhteensopivuus
          (pvm-valintojen-joukossa? valinnat nykyinen-takuupvm))
      valinnat
      (conj valinnat {:pvm nykyinen-takuupvm
                      :fmt (pvm/pvm nykyinen-takuupvm)}))))

(def oletus-takuupvm
  (pvm/vuoden-viim-pvm (+ 3 (pvm/vuosi (pvm/nyt)))))

(def kohdeluettelossa? (atom false))
(def paallystysilmoitukset-tai-kohteet-nakymassa? (atom false))
(def validointivirheet-modal (atom nil))
;; Tämä tila on tuckia varten
(defonce tila (atom {:pot-jarjestys :tila}))
;; Tämän alla on joitain kursoreita tilaan, jotta vanhat jutut toimisi.
;; Näitä ei pitäisi tarvita refaktoroinnin päätteeksi
(defonce yllapitokohde-id (r/cursor tila [:paallystysilmoitus-lomakedata :yllapitokohde-id]))

(def perustiedot-avaimet
  #{:aloituspvm :asiatarkastus :tila :kohdenumero :tunnus :kohdenimi
    :tr-ajorata :tr-kaista :tr-numero :tr-alkuosa :tr-alkuetaisyys
    :tr-loppuosa :tr-loppuetaisyys :kommentit :tekninen-osa
    :valmispvm-kohde :takuupvm :valmispvm-paallystys :versio
    :yha-tr-osoite :muokattu
    :kokonaishinta-ilman-maaramuutoksia :maaramuutokset
    ;; Poikkeukset paikkauskohteen tietojen täydentämiseksi
    :paikkauskohde-toteutunut-hinta :paikkauskohde-nimi
    :paikkauskohde-id :paallystys-alku :paallystys-loppu :takuuaika})

(def lahetyksen-tila-avaimet
  #{:velho-lahetyksen-aika :velho-lahetyksen-vastaus :velho-lahetyksen-tila
    :lahetysaika :lahetetty :lahetys-onnistunut :lahetysvirhe})

(def tr-osoite-avaimet
  #{:tr-numero :tr-alkuosa :tr-alkuetaisyys
    :tr-loppuosa :tr-loppuetaisyys :tr-ajorata :tr-kaista})

(defn paakohteen-validointi
  [_ rivi _]
  (let [{:keys [vuodet tr-osien-tiedot]} (:paallystysilmoitus-lomakedata @tila)
        paakohde (select-keys (:tr-osoite rivi) #{:tr-numero :tr-ajorata :tr-kaista :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys})
        vuosi (first vuodet)
        ;; Kohteiden päällekkyys keskenään validoidaan taulukko tasolla, jotta rivin päivittämine oikeaksi korjaa
        ;; myös toisilla riveillä olevat validoinnit.
        validoitu (yllapitokohde-domain/validoi-kohde paakohde (get tr-osien-tiedot (get-in rivi [:tr-osoite :tr-numero])) {:vuosi vuosi})]
    (vec (flatten (vals (yllapitokohde-domain/validoitu-kohde-tekstit validoitu true))))))

(def perustietojen-validointi
  {:tr-osoite [{:fn paakohteen-validointi}]})

(defn perustietojen-huomautukset [tekninen-osa valmispvm-kohde]
  {:perustiedot {:tekninen-osa {:kasittelyaika [[:ei-tyhja "Anna käsittelypvm"]
                                                [:pvm-toisen-pvmn-jalkeen valmispvm-kohde
                                                 "Käsittely ei voi olla ennen valmistumista"]]
                                :paatos [[:ei-tyhja "Anna päätös"]]
                                :perustelu [[:ei-tyhja "Anna päätöksen selitys"]]}
                 :asiatarkastus {:tarkastusaika [[:ei-tyhja "Anna tarkastuspäivämäärä"]
                                                 [:pvm-toisen-pvmn-jalkeen valmispvm-kohde
                                                  "Tarkastus ei voi olla ennen valmistumista"]]
                                 :tarkastaja [[:ei-tyhja "Anna tarkastaja"]]}}})

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

(defn paivita-yllapitokohteet! []
  (harja.atom/paivita! yllapitokohteet))

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

(def muut-kuin-yha-kohteet
  (reaction-writable
    (let [kohteet @yllapitokohteet-suodatettu
          muut-kuin-yha-kohteet (when kohteet
                                   (yllapitokohteet/suodata-yllapitokohteet
                                     kohteet
                                     {:yha-kohde? false}))]
      (tr-domain/jarjesta-kohteiden-kohdeosat muut-kuin-yha-kohteet))))

(def kaikki-kohteet
  (reaction (concat @yhan-paallystyskohteet @muut-kuin-yha-kohteet (when muut-kustannukset/kohteet
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
(def pk-lk-skeema
  {:otsikko "PK-lk" :alasveto-luokka "kavenna-jos-kapea"
   :nimi :yllapitoluokka :tyyppi :valinta
   :valinta-arvo :numero
   :valinnat yllapitokohteet-domain/paallysteen-korjausluokat
   :valinta-nayta #(cond
                     (map? %)
                     (:lyhyt-nimi %)

                     (number? %)
                     yllapitokohteet-domain/yllapitoluokkanumero->lyhyt-nimi

                     :else "-")})

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
                                [:li (if (map? virheviesti)
                                       (str virheviesti)
                                       virheviesti)])
                              virheviestit))]))

(defn virhe-modal [vastaus otsikko]
  (let [virhe (:virhe vastaus)]
    (modal/nayta!
      {:otsikko otsikko
       :otsikko-tyyli :virhe}
      (cond
        (map? virhe)
        (concat
          ;; gensym on tässä vain poistamassa virheilmoituksen. Se ei estä remounttailua.
          (interpose '(^{:key (str (gensym))} [:p "------------"])
            (map virheviestit-komponentti virhe)))
        :default
        [:div
         [:p (str virhe)]]))))

(defn- osoitteet-mapiksi [rivit]
  (update-in rivit [:ilmoitustiedot :osoitteet]
             (fn [osoitteet]
               (let [osoitteet-jarjestyksessa (tr-domain/jarjesta-tiet osoitteet)]
                 (into {}
                       (map #(identity [%1 %2])
                            (iterate inc 1) osoitteet-jarjestyksessa))))))

(defn- alustatoimet-mapiksi [rivit]
  (update-in rivit [:ilmoitustiedot :alustatoimet]
             (fn [alustatoimet]
               (let [alustatoimet-jarjestyksessa (tr-domain/jarjesta-tiet alustatoimet)]
                 (into {}
                       (map #(identity [%1 (assoc %2 :id %1)])
                            (iterate inc 1) alustatoimet-jarjestyksessa))))))

(defn muotoile-osoitteet-ja-alustatoimet [rivit]
  (-> rivit
      (osoitteet-mapiksi)
      (alustatoimet-mapiksi)))

(defn hae-osan-pituudet [grid-state osan-pituudet-teille-atom]
  (let [tiet (into #{} (map (comp :tr-numero second)) grid-state)]
    (doseq [tie tiet :when (not (contains? @osan-pituudet-teille-atom tie))]
      (go
        (let [pituudet (<! (vkm/tieosien-pituudet tie))]
          (log "Haettu osat tielle " tie ", vastaus: " (pr-str pituudet))
          (swap! osan-pituudet-teille-atom assoc tie pituudet))))))

(defn tien-osat-riville
  [rivi osan-pituudet-teille]
  ;; osa toteutuksista nojaa vielä atomiin, osa taas käyttää jo Tuckin app statea
  ;; tuetaan parametriä sekä atomina että paljaana arvona
  (let [pituudet (if (instance? reagent.ratom/RAtom osan-pituudet-teille)
                   @osan-pituudet-teille
                   osan-pituudet-teille)]
    (get pituudet (:tr-numero rivi))))

(defn rivin-kohteen-pituus
  [osien-pituudet rivi]
  (tr-domain/laske-tien-pituus osien-pituudet rivi))

(defn rivita-virheet
  "Rivittää sisäkkäisessä rakenteessa olevat virheet ihmisen luettavaan muotoon, esim. modaliin"
  [virhe]
  (let [luettu-json (transit/read (transit/reader :json) virhe)]
    (cond
      (or
        (not (coll? luettu-json))
        (str/includes? virhe "missing-required-key"))
      luettu-json

      :else
      [(reduce-kv (fn [m k v]
                    (assoc m k (distinct
                                 (flatten
                                   (cond
                                     (map? v)
                                     v

                                     (string? v)
                                     (list v)

                                     :else
                                     (map (fn [kohde]
                                            (cond
                                              (empty? kohde) nil

                                              :else
                                              (vals kohde)))
                                       v))))))
         {} luettu-json)])))

(defn paivita-paallystysilmoituksen-lahetys-tila [paallystysilmoitukset {:keys [kohde-id] :as uusi-tila}]
  (let [avaimet [:lahetys-onnistunut :lahetysaika :lahetetty :lahetysvirhe
                 :velho-lahetyksen-aika :velho-lahetyksen-tila :velho-lahetyksen-vastaus]
        uusi-tila (select-keys uusi-tila avaimet)]
    (map #(if (= kohde-id (:paallystyskohde-id %))
            (merge % uusi-tila)
            %)
         paallystysilmoitukset)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pikkuhiljaa tätä muutetaan tuckin yhden atomin maalimaan

(defrecord AsetaKasiteltavaksi [arvo])
(defrecord AvaaPaallystysilmoituksenLukitus [])
(defrecord AvaaPaallystysilmoituksenLukitusOnnistui [vastaus paallystyskohde-id])
(defrecord AvaaPaallystysilmoituksenLukitusEpaonnistui [vastaus])
(defrecord AvaaPaallystysilmoitus [paallystyskohde-id])
(defrecord SuljePaallystysilmoitus [])

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

;; Digiroad kaistojen haku tr-osoite & ajorata-kombinaatiolle
(defrecord HaeKaistat [tr-osoite ajorata])
(defrecord HaeKaistatOnnistui [vastaus tr-osoite ajorata])
(defrecord HaeKaistatEpaonnistui [vastaus])

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
(defrecord YHAVelhoVientiOnnistui [vastaus])
(defrecord YHAVelhoVientiEpaonnistui [vastaus])


(extend-protocol tuck/Event
  AsetaKasiteltavaksi
  (process-event [{arvo :arvo} app]
    (assoc-in app [:paallystysilmoitus-lomakedata :perustiedot :valmis-kasiteltavaksi] arvo))

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
                     :keys [valitut-tilat valitut-elyt] :as app}]
    (let [valittu-sopimusnumero (if (nil? valittu-sopimusnumero)
                                  @urakka/valittu-sopimusnumero
                                  valittu-sopimusnumero)
          ;; Samalla kutsulla voidaan hakea paikkausilmoitusten lisäksi myös paikkauskohteet
          ;; joista ei ole vielä tehty paikkausilmiotusta.
          parametrit (if (:paikkauskohteet? app)
                       {:urakka-id urakka-id
                        :sopimus-id (first valittu-sopimusnumero)
                        :vuosi valittu-urakan-vuosi
                        :paikkauskohteet? true
                        :tilat valitut-tilat 
                        :elyt valitut-elyt
                        ;; Tänne myös elyt ja muut sellaset hakuhommat, mitä paikkauskohteiden puolella käytetään
                        }
                       {:urakka-id urakka-id
                        :sopimus-id (first valittu-sopimusnumero)
                        :vuosi valittu-urakan-vuosi})]
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
      (cond-> app  ;; mikäli paikkauspuolelta triggeröity haku, niin päivitetään karttaa
        (some? (:paikkauskohteet? app)) (do (paikkausten-paallystysilmoitukset/paivita-karttatiedot paallystysilmoitukset app) app)
        true (assoc :paallystysilmoitukset paallystysilmoitukset
                    :kaikki-paallystysilmoitukset paallystysilmoitukset))))
  HaePaallystysilmoituksetEpaonnisuti
  (process-event [{vastaus :vastaus} app]
    app)
  HaePaallystysilmoitusPaallystyskohteellaOnnnistui
  (process-event [{vastaus :vastaus} {urakka :urakka :as app}]
    (let [;; Leivotaan jokaiselle kannan JSON-rakenteesta nostetulle alustatoimelle id järjestämistä varten
          vastaus (muotoile-osoitteet-ja-alustatoimet vastaus)
          perustiedot (select-keys vastaus perustiedot-avaimet)
          lahetyksen-tila (select-keys vastaus lahetyksen-tila-avaimet)
          muut-tiedot (apply dissoc vastaus perustiedot-avaimet)]
      (-> app
          (assoc-in [:paallystysilmoitus-lomakedata :kirjoitusoikeus?]
                    (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                              (:id urakka)))
          (assoc-in [:paallystysilmoitus-lomakedata :perustiedot]
                    perustiedot)
          (assoc-in [:paallystysilmoitus-lomakedata :lahetyksen-tila]
                    lahetyksen-tila)
          ;; TODO tätä logikkaa voisi refaktoroida. Nyt kohteen tr-osoitetta säliytetään yhtäaikaa kahdessa
          ;; eri paikassa. Yksi on :perustiedot avaimen alla, jota oikeasti käytetään aikalaila kaikeen muuhun
          ;; paitsi validointiin. Validointi hoidetaan [:perustiedot :tr-osoite] polun alta.
          (assoc-in [:paallystysilmoitus-lomakedata :perustiedot :tr-osoite]
                    (select-keys perustiedot tr-osoite-avaimet))
          (assoc-in [:paallystysilmoitus-lomakedata :perustiedot :takuupvm]
                    (or (:takuupvm perustiedot) oletus-takuupvm))
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

  HaeKaistat
  (process-event [{:keys [tr-osoite ajorata]} app]
    ;; Haetaan kaistat vain jos kaikki vaadittavat tiedot on syötetty lomakkeella
    (if (and (tr/validi-osoite? tr-osoite) (tr/on-alku-ja-loppu? tr-osoite) (integer? ajorata))
      (do
        (log/info "HaeKaistat: " [tr-osoite ajorata])
        (let [parametrit {:tr-osoite tr-osoite
                          :ajorata ajorata}]
          (tuck-apurit/post! app
            :hae-kaistat-digiroadista
            parametrit
            {:onnistui ->HaeKaistatOnnistui
             :onnistui-parametrit [(:tr-osoite parametrit) (:ajorata parametrit)]
             :epaonnistui ->HaeKaistatEpaonnistui
             :paasta-virhe-lapi? true})))
      app))

  HaeKaistatOnnistui
  (process-event [{:keys [vastaus tr-osoite ajorata ]} app]
    (log/info "HaeKaistatOnnistui: " vastaus)

    (-> app
      (update :paallystysilmoitus-lomakedata (fn [lomake]
                                               (-> lomake
                                                 (assoc-in [:kaistat tr-osoite ajorata] vastaus)
                                                 (assoc :validoi-lomake? true))))))

  HaeKaistatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (log/info "HaeKaistatEpaonnistui: " vastaus)

    (let [status (get-in vastaus [:status])
          viesti (get-in vastaus [:response :virhe])]
      ;; Näytetään toast vain jos status on 500 (esim. ongelma Digiroadin puolella tai Harjan integraatiossa)
      ;; TODO: Tällä hetkellä Digiroadin kaistahaku niputtaa statuksen 400 alle kaikki seuraavat virhetyypit:
      ;;       Hakuparametrit ovat vääriä, tieosoitetta ei ole olemassa, tai on yritetty hakea tulosta liian monta kertaa
      ;;       Digiroadin puolella ja uudelleenyritysten maksimimäärä on saavutettu.
      ;;       Ideaalitapauksessa, ei näytetä virheitä käyttäjälle mikäli hakuparametrit ovat vääriä.
      ;;       Mutta, virhe pitäisi näyttää jos Digiroadista tai Harjasta tulee jokin tuntematon virhe (kuten nyt status 500).
      ;;       Ratkaisematta on vielä tilanne, että mitä tehdään kun haun maksimiyritysten määrä on ylittynyt.
      ;;          -> Yrittäisikö Harjan Digiroad integraatio hakua itsenäisesti uudelleen vai pitääkö käyttäjän muokata
      ;;             lomakkeella jotakin riviä, jotta kaistojen hakua yritetään uudelleen?
      (when (= 500 status)
        (viesti/nayta-toast! viesti :varoitus viesti/viestin-nayttoaika-aareton)))
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
  SuljePaallystysilmoitus
  (process-event [_ app]
    (assoc app :paallystysilmoitus-lomakedata nil))
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
                               (assoc :versio 1)
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
      (reset! paallystysilmoitukset jarjestetyt-ilmoitukset)
      (reset! yllapitokohteet (:yllapitokohteet vastaus))
      (assoc app :paallystysilmoitus-lomakedata nil
                 :kaikki-paallystysilmoitukset jarjestetyt-ilmoitukset
                 :paallystysilmoitukset jarjestetyt-ilmoitukset)))
  TallennaPaallystysilmoitusEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (let [vastaus-virhe (cond
                          (get-in vastaus [:parse-error :original-text])
                          [(get-in vastaus [:parse-error :original-text])]

                          (get-in vastaus [:response :virhe])
                          (get-in vastaus [:response :virhe])

                          :else
                          vastaus)]
      (virhe-modal {:virhe (if (vector? vastaus-virhe)
                             (last vastaus-virhe)
                             (rivita-virheet vastaus-virhe))} "Päällystysilmoituksen tallennus epäonnistui!"))
    (assoc-in app [:paallystysilmoitus-lomakedata :tallennus-kaynnissa?] false))

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


  YHAVelhoVientiOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :paallystysilmoitukset (paivita-paallystysilmoituksen-lahetys-tila
                                        (:paallystysilmoitukset app) vastaus)))

  YHAVelhoVientiEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :paallystysilmoitukset (paivita-paallystysilmoituksen-lahetys-tila
                                        (:paallystysilmoitukset app) vastaus))))
