(ns harja.tiedot.urakka.laadunseuranta.laatupoikkeamat
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tt]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.domain.roolit :as roolit]
            [harja.domain.laadunseuranta.laatupoikkeama :as laatupoikkeamat]
            [harja.pvm :as pvm]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka :as urakka]
            [harja.ui.viesti :as viesti]
            [harja.loki :as log]
            [harja.domain.tierekisteri :as tierekisteri])
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

(defn- taydenna-kohteen-kuvaus [laatupoikkeama]
  (let [tr-osoite-teksti (tierekisteri/tierekisteriosoite-tekstina (:yllapitokohde laatupoikkeama) {:teksti-tie? false})
        tr-osoite-teksti (when-not (empty? tr-osoite-teksti)
                            (str "(" tr-osoite-teksti ")"))]
    (update laatupoikkeama :kohde
      #(str "Laatupoikkeama: " % " " tr-osoite-teksti))))

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

(defn- resetoi-ja-hae-poikkeamat [tyyppi urakka-id aikavali]
  (reset! valittu-laatupoikkeama nil)
  (reset! valittu-laatupoikkeama-id nil)
  (hae-laatupoikkeamat tyyppi urakka-id aikavali))

(extend-protocol tuck/Event

  HoitokausiVaihdettu
  (process-event [{urakka-id :urakka-id hoitokausi :hoitokausi} app]
    (do
      (hae-laatupoikkeamat (:listaus-tyyppi app) urakka-id hoitokausi)
      (-> app
        ;; Resetoi laatupoikkeamat jotta ajax loader tulee näkyviin 
        (assoc :laatupoikkeamat nil)
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
    (let [;; Listaa urakan hoitokaudet ja hae kuluva hoitokausi
          urakan-hoitokaudet (u/hoito-tai-sopimuskaudet (-> @tila/yleiset :urakka))
          kuluva-hoitokausi (first (filter #(pvm/valissa? (pvm/nyt) (first %) (second %)) urakan-hoitokaudet))
          ;; Fallback 
          kuluva-hoitokausi (if (nil? kuluva-hoitokausi)
                              (first urakan-hoitokaudet)
                              kuluva-hoitokausi)]
      (do
        ;; Kun urakkaa vaihdetaan, resetoidaan ja päivitetään valitut tiedot 
        ;; Valituksi ajaksi ja hoitokaudeksi valitaan defaulttina aina kuluva hoitokausi
        (resetoi-ja-hae-poikkeamat tyyppi urakka-id aikavali)
        (-> app
          (assoc :laatupoikkeamat nil)
          (assoc :valittu-hoitokausi kuluva-hoitokausi)
          (assoc :valittu-aikavali kuluva-hoitokausi)))))

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
  (process-event [_ {:keys [listaus-tyyppi valittu-aikavali] :as app}]
    ;; Kun poikkeama tallennetaan, päivitetään kaikki tiedot jotta karttaan tulee uusikin reitti näkyviin
    (resetoi-ja-hae-poikkeamat listaus-tyyppi (-> @tila/yleiset :urakka :id) valittu-aikavali)
    (assoc app :laatupoikkeamat nil))

  TallennaLaatuPoikkeamaEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (log/error "Laatupoikkeaman tallennuksessa virhe!" vastaus)
    (viesti/nayta-toast! "Oikaisun tallennuksessa tapahtui virhe" :varoitus)
    app))
