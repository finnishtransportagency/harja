(ns harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-reikapaikkaukset
  "Reikäpaikkaukset- tiedot"
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.tierekisteri :as tr]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(defonce tila (atom {:rivit nil
                     :rivi-maara nil
                     :kustannukset nil
                     :valittu-rivi nil
                     :tyomenetelmat nil
                     :excel-virheet nil
                     :muokataan false
                     :haku-kaynnissa? false
                     :nayta-virhe-modal false
                     :valinnat {:tr nil
                                :aikavali (pvm/kuukauden-aikavali (pvm/nyt))}}))

(def nakymassa? (atom false))
(def paivita-kartta? (atom false))
(def tr-atom (atom nil))
(def aikavali-atom (atom (pvm/kuukauden-aikavali (pvm/nyt))))
;; Tekohetkellä samat kun paikkauskohteiden-yksikot, mutta käytetään reikäpaikkauksille omaa muuttujaa
(def reikapaikkausten-yksikot #{"m2" "t" "kpl" "jm"}) 

;; Kartta jutskat
(defonce valittu-reikapaikkaus (atom nil))
(def karttataso-reikapaikkaukset (atom false))

(defn hae-urakan-reikapaikkaukset-kartalle
  "Kartalle piirron hakufunktio"
  [urakka-id]
  (reset! paivita-kartta? false)
  (k/post! :hae-reikapaikkaukset {:tr @tr-atom
                                  :aikavali @aikavali-atom
                                  :urakka-id urakka-id}))

;; Tulokset reaction
(defonce haetut-reikapaikkaukset
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               nakymassa? @nakymassa?
               paivita-kartta? @paivita-kartta?]
    {:nil-kun-haku-kaynnissa? true}
    (when (or nakymassa? paivita-kartta?)
      (hae-urakan-reikapaikkaukset-kartalle urakka-id)))) 

;; Karttataso
(defonce reikapaikkaukset-kartalla
  (reaction
    (let [valittu-id @valittu-reikapaikkaus]
      (when @karttataso-reikapaikkaukset
        (kartalla-esitettavaan-muotoon
          @haetut-reikapaikkaukset
          ;; Korostaa pinni-ikonin (jos olemassa), ikonin määrittely: asia-kartalle :reikapaikkaus -> :img 
          #(= valittu-id (:id %))
          ;; Piirretään nämä toteumat kartalle
          ;; Jos valittuna yksittäinen paikkaus, näytetään pelkästään se
          (comp
            (keep #(do
                     (when (and
                             (:sijainti %)
                             (or
                               (= (:id %) @valittu-reikapaikkaus)
                               (nil? @valittu-reikapaikkaus)))
                       %)))
            (map #(assoc % :tyyppi-kartalla :reikapaikkaus))))))))

;; Tuck 
(defrecord PaivitaValinnat [uudet])
(defrecord HaeTiedot [paivita-kartalle?])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])
(defrecord HaeTyomenetelmat [])
(defrecord HaeTyomenetelmatOnnistui [vastaus])
(defrecord HaeTyomenetelmatEpaonnistui [vastaus])
(defrecord AvaaMuokkausModal [rivi])
(defrecord MuokkaaRivia [rivi])
(defrecord AsetaToteumanPvm [aika])
(defrecord SuljeMuokkaus [])
(defrecord SuljeVirheModal [])
(defrecord TiedostoLadattu [vastaus])
(defrecord TallennaReikapaikkaus [rivi])
(defrecord TallennaReikapaikkausOnnistui [vastaus])
(defrecord TallennaReikapaikkausEpaonnistui [vastaus])
(defrecord PoistaReikapaikkaus [rivi])
(defrecord PoistaReikapaikkausOnnistui [vastaus])
(defrecord PoistaReikapaikkausEpaonnistui [vastaus])


(defn hae-reikapaikkaukset 
  "Hakee reikäpaikkaukset näkymään"
  [{:keys [valinnat] :as app}]
  (tuck-apurit/post! app :hae-reikapaikkaukset
    {:tr (:tr valinnat)
     :aikavali (:aikavali valinnat)
     :urakka-id (:id @nav/valittu-urakka)}
    {:onnistui ->HaeTiedotOnnistui
     :epaonnistui ->HaeTiedotEpaonnistui}))


(defn- paivita-lista-ja-kartta
  "Hakee päivitetyt tulokset näkymään ja päivittää kartan toteumien piirrokset"
  [app]
  ;; Hae päivtetty lista näkymään
  (hae-reikapaikkaukset app)
  ;; Päivitä uudet reitit kartalle 
  (reset! paivita-kartta? true))


(defn voi-tallentaa?
  "Validoi toteuman muokkauslomakkeen"
  [{:keys [paikkaus_maara kustannus alkuaika tyomenetelma yksikko] :as valittu-reikapaikkaus}
   {:keys [tyomenetelmat]}]
  (let [yksikko-validi (some? yksikko)
        pvm-validi? (pvm/pvm? alkuaika)
        ;; Sallitaan myös 0, muttei nil
        kustannus-validi? (some? kustannus)
        paikkaus-maara-validi? (some? paikkaus_maara)
        ;; Onko valittua työmenetelmä id:tä olemassa tietokannassa? (HaeTyomenetelmat)
        tyomenetelma-validi? (boolean (some #(= tyomenetelma (:id %)) tyomenetelmat))
        ;; Onko syötetty tr-osoite validi? 
        tr-validi? (boolean (tr/validi-osoite? (select-keys valittu-reikapaikkaus [:tie :aosa :aet :losa :let])))]
    (and
      tr-validi?
      pvm-validi?
      yksikko-validi
      kustannus-validi?
      tyomenetelma-validi?
      paikkaus-maara-validi?)))


(defn- valitse-reikapaikkaus-kartalle
  "Piirtää kartalle valitun paikkauksen sijainnin kunhan sijainti on olemassa
   Jos passataan nil, näytetän kaikki toteumat"
  [id paivita?]
  (reset! valittu-reikapaikkaus id)
  (when paivita?
    (reset! paivita-kartta? true)))


(extend-protocol tuck/Event
  HaeTiedot
  (process-event [{paivita-kartalle? :paivita-kartalle?} app]
    (if paivita-kartalle?
      (paivita-lista-ja-kartta app)
      (hae-reikapaikkaukset app))
    (assoc app :haku-kaynnissa? true))

  HaeTiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [kustannukset (reduce + (map :kustannus vastaus))
          rivi-maara (count vastaus)]
      (assoc app
        :rivit vastaus
        :rivi-maara rivi-maara
        :kustannukset kustannukset
        :haku-kaynnissa? false)))

  HaeTiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "Tietojen haku epäonnistui: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

  HaeTyomenetelmat
  (process-event [_ app]
    (tuck-apurit/post! app :hae-tyomenetelmat
      {}
      {:onnistui ->HaeTyomenetelmatOnnistui
       :epaonnistui ->HaeTyomenetelmatEpaonnistui})
    app)

  HaeTyomenetelmatOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :tyomenetelmat vastaus))

  HaeTyomenetelmatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "Työmenetelmät haku epäonnistui: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Työmenetelmät haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

  AsetaToteumanPvm
  (process-event [{aika :aika} app]
    (-> app 
      (assoc-in [:valittu-rivi :alkuaika] aika)
      (assoc-in [:valittu-rivi :loppuaika] aika)))

  AvaaMuokkausModal
  (process-event [{rivi :rivi} app]
    (valitse-reikapaikkaus-kartalle (:id rivi) true)
    (-> app
      (assoc :muokataan true)
      (assoc :valittu-rivi rivi)))

  MuokkaaRivia
  (process-event [{rivi :rivi} app]
    (update app :valittu-rivi merge rivi))

  SuljeMuokkaus
  (process-event [_ app]
    (valitse-reikapaikkaus-kartalle nil false)
    (assoc app :muokataan false))

  PoistaReikapaikkaus
  (process-event [{rivi :rivi} app]
    (tuck-apurit/post! app :poista-reikapaikkaus
      {:kayttaja-id (:id @istunto/kayttaja)
       :urakka-id (:id @nav/valittu-urakka)
       :ulkoinen-id (:tunniste rivi)}
      {:onnistui ->PoistaReikapaikkausOnnistui
       :epaonnistui ->PoistaReikapaikkausEpaonnistui})
    app)

  PoistaReikapaikkausOnnistui
  (process-event [_ app]
    (viesti/nayta-toast! "Toteuma poistettu onnistuneesti" :onnistui viesti/viestin-nayttoaika-keskipitka)
    (paivita-lista-ja-kartta app)
    (assoc app
      :valittu-rivi nil
      :muokataan false))

  PoistaReikapaikkausEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "Poisto epaonnistui, vastaus: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Poisto epaonnistui, vastaus: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

  TallennaReikapaikkaus
  (process-event [{rivi :rivi} app]
    (let [;; Uncaught: nth not supported on this type cljs.core/PersistentHashMap
          ;; Voiko nämä jotenkin destruktoida?
          ;; [tunniste tie aosa aet losa] rivi <> ...
          ulkoinen-id (:tunniste rivi)
          tie (:tie rivi)
          aosa (:aosa rivi)
          luotu (:luotu rivi)
          aet (:aet rivi)
          losa (:losa rivi)
          let (:let rivi)
          yksikko (:yksikko rivi)
          menetelma (:tyomenetelma rivi)
          alkuaika (:alkuaika rivi)
          loppuaika (:loppuaika rivi) ;; Nämä on samoja, ei tietoa tarvitaanko loppuaikaa oikeasti reikäpaikkauksissa. Ks. AsetaToteumanPvm
          maara (:paikkaus_maara rivi)
          kustannus (:kustannus rivi)]
      ;; Yksikön muokkausta käyttöliittymästä ei ole näköjään speksattu, Excel-tuonti kuitenkin yliajaa ne 
      (tuck-apurit/post! app :tallenna-reikapaikkaus
        {:luoja-id (:id @istunto/kayttaja)
         :urakka-id (:id @nav/valittu-urakka)
         :ulkoinen-id ulkoinen-id
         :tie tie
         :aosa aosa
         :aet aet
         :losa losa
         :luotu luotu
         :alkuaika alkuaika
         :loppuaika loppuaika
         :let let
         :yksikko yksikko
         :menetelma menetelma
         :paikkaus_maara maara
         :kustannus kustannus}
        {:onnistui ->TallennaReikapaikkausOnnistui
         :epaonnistui ->TallennaReikapaikkausEpaonnistui})
      app))

  TallennaReikapaikkausOnnistui
  (process-event [_ {:keys [valittu-rivi rivit] :as app}]
    (let [valittu-id (:id valittu-rivi)
          valittu-rivi (some #(when (= (:id %) valittu-id) %) rivit)]
      (viesti/nayta-toast! "Toteuma tallennettu onnistuneesti" :onnistui viesti/viestin-nayttoaika-keskipitka)
      (paivita-lista-ja-kartta app)
      ;; Päivitetään vielä valittu rivi, että esim yksikkö näkyy inputissa oikein 
      (assoc app :valitu-rivi valittu-rivi)))

  TallennaReikapaikkausEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "Tallennus epäonnistui, vastaus: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Tallennus epäonnistui, vastaus: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

  SuljeVirheModal
  (process-event [_ app]
    (assoc app :nayta-virhe-modal false))

  TiedostoLadattu
  (process-event [{vastaus :vastaus} app]
    (let [status (:status vastaus)
          response (:response vastaus)
          virheet (cond
                    (= status 500) ["Sisäinen käsittelyvirhe"]
                    :else response)]
      (cond
        ;; Virheitä Excel tuonnissa
        (and
          (not (nil? status))
          (not= 200 status))
        (do
          (println "Status: " status)
          (viesti/nayta-toast! "Ladatun tiedoston käsittelyssä virhe" :varoitus viesti/viestin-nayttoaika-keskipitka)
          ;; Lisää virheet app stateen jotka näytetään modalissa
          (-> app
            (assoc :nayta-virhe-modal true)
            (assoc :excel-virheet virheet)))
        ;; Ei virheitä
        :else
        (do
          (paivita-lista-ja-kartta app)
          (viesti/nayta-toast! "Reikäpaikkaukset ladattu onnistuneesti" :onnistui viesti/viestin-nayttoaika-keskipitka)
          (-> app
            (assoc :excel-virheet nil)
            (assoc :haku-kaynnissa? true)
            (assoc :nayta-virhe-modal false))))))

  PaivitaValinnat
  (process-event [{uudet :uudet} app]
    (let [uudet-valinnat (merge (:valinnat app) uudet)]
      (assoc app :valinnat uudet-valinnat))))
