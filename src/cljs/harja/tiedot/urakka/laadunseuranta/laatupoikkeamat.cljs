(ns harja.tiedot.urakka.laadunseuranta.laatupoikkeamat
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tt]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.roolit :as roolit]
            [harja.domain.laadunseuranta.laatupoikkeama :as laatupoikkeamat]
            [harja.pvm :as pvm]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka :as urakka]
            [harja.ui.viesti :as viesti]
            [harja.loki :as log])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def kuvaile-tekija laatupoikkeamat/kuvaile-tekija)
(def kuvaile-kasittelytapa laatupoikkeamat/kuvaile-kasittelytapa)
(def kuvaile-paatostyyppi laatupoikkeamat/kuvaile-paatostyyppi)
(def kuvaile-paatos laatupoikkeamat/kuvaile-paatos)

(defonce voi-kirjata?
  (reaction
   (let [kayttaja @istunto/kayttaja
         urakka @nav/valittu-urakka]
     (and kayttaja
          urakka
          (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-laatupoikkeamat
                                    (:id urakka))))))

(defn hae-laatupoikkeaman-tiedot
  "Hakee urakan laatupoikkeaman tiedot urakan id:n ja laatupoikkeaman id:n
  perusteella. Palauttaa kaiken tiedon mitä laatupoikkeaman muokkausnäkymään
  tarvitaan."
  [urakka-id laatupoikkeama-id]
  (k/post! :hae-laatupoikkeaman-tiedot {:urakka-id urakka-id
                                  :laatupoikkeama-id laatupoikkeama-id}))

(defn tallenna-laatupoikkeama [laatupoikkeama]
  (k/post! :tallenna-laatupoikkeama laatupoikkeama))

(defonce listaus (atom :kaikki))
(defonce laatupoikkeamat-kartalla (atom []))
(defonce valittu-laatupoikkeama-id (atom nil))

(defn uusi-laatupoikkeama []
  {:tekija (roolit/osapuoli @istunto/kayttaja)
   :aika (pvm/nyt)})

(defn- sanktiotaulukon-rivit [laatupoikkeama]
  (vals (:sanktiot laatupoikkeama)))

(defonce valittu-laatupoikkeama
         (reaction<! [id @valittu-laatupoikkeama-id]
                     {:nil-kun-haku-kaynnissa? true}
                     (when id
                       (go (let [laatupoikkeama (if (= :uusi id)
                                                  (uusi-laatupoikkeama)
                                                  (<! (hae-laatupoikkeaman-tiedot (:id @nav/valittu-urakka) id)))]
                             (-> laatupoikkeama

                                 ;; Tarvitsemme urakan liitteen linkitystä varten

                                 (assoc :urakka (:id @nav/valittu-urakka))
                                 (assoc :sanktiot (into {}
                                                        (map (juxt :id identity) (:sanktiot laatupoikkeama))))))))))

(defn paivita-yllapitokohteen-tr-tiedot
  [tiedot yllapitokohteet]
  (when yllapitokohteet
    (let [ypk-id (if (integer? (:yllapitokohde tiedot))
                   (:yllapitokohde tiedot)
                   (get-in tiedot [:yllapitokohde :id]))
          k (first (filter #(= (:id %) ypk-id)
                           yllapitokohteet))
          [tie aosa aet losa let] [(:tr-numero k)
                                   (:tr-alkuosa k)
                                   (:tr-alkuetaisyys k)
                                   (:tr-loppuosa k)
                                   (:tr-loppuetaisyys k)]]
      (if (and k tie aosa aet)
        (assoc tiedot
          :tr {:numero        tie
               :alkuosa       aosa
               :alkuetaisyys  aet
               :loppuosa      losa
               :loppuetaisyys let})
        tiedot))))

(defonce aikavali-atom (atom nil))

(defrecord HoitokausiVaihdettu [urakka-id hoitokausi])
(defrecord HaeLaatupoikkeamat [urakka-id tyyppi aikavali])
(defrecord HaeLaatupoikkeamatOnnistui [vastaus])
(defrecord HaeLaatupoikkeamatEpaonnistui [vastaus])
(defrecord PaivitaAikavali [aikavali tyyppi urakka-id])
(defrecord PaivitaListausTyyppi [tyyppi aikavali urakka-id])

(defrecord TallennaLaatuPoikkeama [laatupoikkeama nakyma])
(defrecord TallennaLaatuPoikkeamaOnnistui [vastaus laatupoikkeama])
(defrecord TallennaLaatuPoikkeamaEpaonnistui [vastaus])

(defn hae-laatupoikkeamat [tyyppi urakka-id aikavali]
  (tt/post! :hae-urakan-laatupoikkeamat
    {:listaus tyyppi
     :urakka-id urakka-id
     :alku (first aikavali)
     :loppu (second aikavali)}
    {:onnistui ->HaeLaatupoikkeamatOnnistui
     :epaonnistui ->HaeLaatupoikkeamatEpaonnistui}))

(extend-protocol tuck/Event

  HoitokausiVaihdettu
  (process-event [{urakka-id :urakka-id hoitokausi :hoitokausi} app]
    (do
      (hae-laatupoikkeamat (:listaus-tyyppi app) urakka-id hoitokausi)
      (-> app
        (assoc :valittu-aikavali hoitokausi)
        (assoc :valittu-hoitokausi hoitokausi))))

  PaivitaAikavali
  (process-event [{aikavali :aikavali tyyppi :tyyppi urakka-id :urakka-id} app]
    (do
      (hae-laatupoikkeamat tyyppi urakka-id aikavali)
      (assoc app :valittu-aikavali aikavali)))

  PaivitaListausTyyppi
  (process-event [{tyyppi :tyyppi aikavali :aikavali urakka-id :urakka-id} app]
    (do
      (hae-laatupoikkeamat tyyppi urakka-id aikavali)
      (assoc app :listaus-tyyppi tyyppi)))

  HaeLaatupoikkeamat
  (process-event [{urakka-id :urakka-id tyyppi :tyyppi aikavali :aikavali} app]
    (do
      (hae-laatupoikkeamat tyyppi urakka-id aikavali)
      app))

  HaeLaatupoikkeamatOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (reset! laatupoikkeamat-kartalla vastaus)
      (assoc app :laatupoikkeamat vastaus)))

  HaeLaatupoikkeamatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (reset! laatupoikkeamat-kartalla [])
      (js/console.log "HaeLaatupoikkeamatEpaonnistui :: vastaus" (pr-str vastaus))
      app))

  TallennaLaatuPoikkeama
  (process-event [{:keys [laatupoikkeama nakyma]} app]
    (let [laatupoikkeama (as-> laatupoikkeama lp
                           (assoc lp :sanktiot (sanktiotaulukon-rivit lp))
                           ;; Varmistetaan, että tietyssä näkymäkontekstissa tallennetaan vain näkymän
                           ;; sisältämät asiat (esim. on mahdollista vaihtaa koko valittu urakka päällystyksestä
                           ;; hoitoon, ja emme halua että hoidon lomakkeessa tallentuu myös ylläpitokohde)
                           (if (some #(= nakyma %) [:paallystys :paikkaus :tiemerkinta])
                             (dissoc lp :kohde)
                             (dissoc lp :yllapitokohde))
                           (if (integer? (:yllapitokohde lp))
                             lp
                             (assoc lp :yllapitokohde (get-in lp [:yllapitokohde :id]))))]
      (tt/post! app :tallenna-laatupoikkeama
        laatupoikkeama
        {:onnistui ->TallennaLaatuPoikkeamaOnnistui
         :onnistui-parametrit [laatupoikkeama]
         :epaonnistui ->TallennaLaatuPoikkeamaEpaonnistui})))

  TallennaLaatuPoikkeamaOnnistui
  (process-event [{:keys [vastaus laatupoikkeama]} {:keys [laatupoikkeamat] :as app}]
    (let [uusi-laatupoikkeama vastaus
          aika (:aika uusi-laatupoikkeama)
          [alku loppu] @urakka/valittu-aikavali]
      (reset! valittu-laatupoikkeama-id nil)
      (if (and (pvm/sama-tai-jalkeen? aika alku)
              (pvm/sama-tai-ennen? aika loppu))
        ;; Kuuluu aikavälille, lisätään tai päivitetään
        (if (:id laatupoikkeama)
          ;; Päivitetty olemassaolevaa
          (assoc app :laatupoikkeamat
            (mapv (fn [lp]
                    (if (= (:id lp) (:id uusi-laatupoikkeama))
                      uusi-laatupoikkeama
                      lp)) laatupoikkeamat))
          ;; Luotu uusi
          (update app :laatupoikkeamat conj uusi-laatupoikkeama))
        app)))

  TallennaLaatuPoikkeamaEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (log/error "Laatupoikkeaman tallennuksessa virhe!" vastaus)
    (viesti/nayta-toast! "Oikaisun tallennuksessa tapahtui virhe" :varoitus)
    app))
