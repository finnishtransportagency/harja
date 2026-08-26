(ns harja.tiedot.urakka.valikatselmus.valikatselmus-tiedot
  (:require [clojure.string :as str]
            [tuck.core :as tuck]
            [harja.ui.dom :as dom]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.nakymasiirrin :as siirrin]
            [harja.domain.urakka :as urakka]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.pvm :as pvm]))

(def valikatselmus-nakymassa? (atom false))
(def tavoitehinnan-muutostallennus-max (atom 9999))
(def tavoitehinnan-muutostallennus-kpl (atom 0))

(defonce tavoitehinnan-muutokset (atom []))

(defn scrollaa-muutoksiin []
  ;; Kutsutaan kun käyttäjä tallentaa oikaisua 
  ;; Gridin elementit menee disabled muotoon, joka muuttaa sivun kokoa
  (siirrin/siirry-elementin-id "tavhinnan-muutokset" 450))

(defn karsitut-tavoitehinnan-muutokset [muutokset]
  (when-not (empty? muutokset)
    (sort-by :index
      (map-indexed
        (fn [indeksi muutos]
          (let [muutos (select-keys muutos [::valikatselmus/otsikko ::valikatselmus/hoitokauden-alkuvuosi ::valikatselmus/selite ::valikatselmus/summa ::valikatselmus/oikaisun-id])
                muutos (into (sorted-map) muutos)]
            muutos))
        muutokset))))

(defn kasittele-throw-virhe [vastaus]
  (let [raaka-virhe (get-in vastaus [:parse-error :original-text])
        raaka-virhe (if (nil? raaka-virhe) "Virhe! Palvelin palautti virheen!" raaka-virhe)
        raaka-virhe (str/replace raaka-virhe #"\\" "")
        raaka-virhe (str/replace raaka-virhe #"\"" "")

        ;; Emme tarvitse ensimmäistä virhesanaa
        virheet (str/join " " (rest (str/split raaka-virhe #" ")))]
    virheet))

;; Oikaisut
(defrecord TallennaOikaisu [oikaisu id])
(defrecord TallennaOikaisut [oikaisut hoitokauden-alkuvuosi])
(defrecord TallennaOikaisuOnnistui [vastaus id])
(defrecord TallennaOikaisuEpaonnistui [vastaus])
(defrecord PoistaOikaisu [oikaisu id])
(defrecord PoistaOikaisuOnnistui [vastaus])
(defrecord PoistaOikaisuEpaonnistui [vastaus])
(defrecord PaivitaTavoitehinnanOikaisut [hoitokauden-alkuvuosi uusi])

;; Kattohinnan oikaisut
(defrecord KattohinnanOikaisuaMuokattu [kattohinta])
(defrecord TallennaKattohinnanOikaisu [uusi-kattohinta hoitokauden-alkuvuosi])
(defrecord TallennaKattohinnanOikaisuOnnistui [vastaus id])
(defrecord TallennaKattohinnanOikaisuEpaonnistui [vastaus])
(defrecord PoistaKattohinnanOikaisu [])
(defrecord PoistaKattohinnanOikaisuOnnistui [vastaus id])
(defrecord PoistaKattohinnanOikaisuEpaonnistui [vastaus])
(defrecord KattohinnanMuokkaaPainettu [kattohinta])

;; Päätökset
(defrecord TallennaLupausPaatos [paatos])
(defrecord PoistaLupausPaatos [paatos])
(defrecord PoistaLupausPaatosOnnistui [vastaus])
(defrecord PoistaLupausPaatosEpaonnistui [vastaus])
(defrecord TallennaTavoitehinnanMuutosPaatos [paatos])
(defrecord TallennaTavoitehinnanPysyvaMuutosPaatos [paatos])
(defrecord TallennaTavoitehinnanAlitusPaatos [paatos])
(defrecord TallennaTavoitehinnanYlitysPaatos [paatos])
(defrecord PoistaTavoitehinnanYlitysPaatos [paatos])
(defrecord TallennaKattohinnanYlitysPaatos [paatos])
(defrecord TallennaKattohinnanYlitysPaatosEpaonnistui [vastaus])
(defrecord PoistaKattohinnanYlitysPaatos [paatos])
(defrecord TallennaPoytakirjanRaporttiPaatos [paatos])
(defrecord PoistaPoytakirjanRaporttiPaatos [paatos])
(defrecord TallennaHoidonjohtopalkkionMuutospaatos [paatos])
(defrecord TallennaHoitokaudenlopunHintapaatos [paatos])
(defrecord TallennaHoitovuodenlopunIndeksikorjauspaatos [paatos])
(defrecord PoistaHoitovuodenlopunIndeksikorjauspaatos [paatos])

(defrecord HaeKetjutetustiKumoutuvatPaatokset [paatos peru-fn])
(defrecord HaeKumoutuvatOnnistui [vastaus])
(defrecord SuljePaatosModal [])
(defrecord PeruValikatselmusPaatos [paatos])


(defrecord PaivitaKattohinnanSiirtoCheckbox [uusi-arvo])
(defrecord PaivitaKattohinnanSiirtoMaara [uusi-arvo])


(defrecord ValitseHoitokausi [urakkaid vuosi])
(defrecord HaeValikatselmuksenTiedotOnnistui [vastaus])
(defrecord HaeValikatselmuksenTiedotEpaonnistui [vastaus])

;; Hae Välikatselmuksen tiedot
(defrecord HaeValikatselmuksenTiedot [urakkaid hoitovuosi])

(defrecord AvaaPaatos [avain])

(defn poista-kattohinnan-oikaisu [app]
  (tuck-apurit/post! app :poista-kattohinnan-oikaisu
    {::urakka/id (-> @tila/yleiset :urakka :id)
     ::valikatselmus/hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)}
    {:onnistui ->PoistaKattohinnanOikaisuOnnistui
     :epaonnistui ->PoistaKattohinnanOikaisuEpaonnistui
     :paasta-virhe-lapi? true}))

(defn hae-valikatselmuksen-tiedot [urakkaid hoitovuosi]
  (tuck-apurit/post! :hae-valikatselmuksen-tiedot-hoitovuodelle
    {:urakkaid urakkaid
     :hoitovuosi hoitovuosi}
    {:onnistui ->HaeValikatselmuksenTiedotOnnistui
     :epaonnistui ->HaeValikatselmuksenTiedotEpaonnistui
     :paasta-virhe-lapi? true}))

(defn kasittele-valikatselmuksen-vastaus [app vastaus]
  (let [hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi vastaus)
        vastaus-muutokset (vals (get-in (:tavoitehinnan-muutokset vastaus) [hoitokauden-alkuvuosi]))
        muutokset (karsitut-tavoitehinnan-muutokset vastaus-muutokset)]

    (reset! tavoitehinnan-muutokset muutokset)
    (-> app
      (assoc :paatokset (:paatokset vastaus))
      (assoc :tavoitehinnan-muutokset (:tavoitehinnan-muutokset vastaus))
      (assoc :yhteenveto (:yhteenveto vastaus))
      (assoc :urakan-parametrit (:urakan-parametrit vastaus))
      (assoc :haku-kaynnissa? false)
      (assoc :tallennus-kesken? false)
      (assoc :nayta-kumoa-modal? false)
      (assoc :tehdyt-kumoutuvat-paatokset nil))))

(extend-protocol tuck/Event

  HaeValikatselmuksenTiedot
  (process-event [{urakkaid :urakkaid hoitovuosi :hoitovuosi} app]
    (hae-valikatselmuksen-tiedot urakkaid hoitovuosi)
    (assoc app :haku-kaynnissa? true))

  HaeValikatselmuksenTiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (kasittele-valikatselmuksen-vastaus app vastaus))

  HaeValikatselmuksenTiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! (kasittele-throw-virhe vastaus) :varoitus)
    (-> app
      (assoc :tallennus-kesken? false)
      (assoc :haku-kaynnissa? false)))

  ;; Tavoitehinnan oikaisut
  TallennaOikaisu
  (process-event [{oikaisu :oikaisu id :id} app]
    (let [oikaisu (merge {::urakka/id (-> @tila/yleiset :urakka :id)}
                    oikaisu)
          ;; Lähetetään oikaisun tallennus serverille vain, jos kaikki tiedot on syötetty
          kaikki-tiedot? (and (::valikatselmus/otsikko oikaisu)
                           (::valikatselmus/selite oikaisu)
                           (::valikatselmus/summa oikaisu)
                           (::valikatselmus/hoitokauden-alkuvuosi oikaisu)
                           (::urakka/id oikaisu))]

      (when kaikki-tiedot?
        (tuck-apurit/post! :tallenna-tavoitehinnan-oikaisu
          oikaisu
          {:onnistui ->TallennaOikaisuOnnistui
           :onnistui-parametrit [id]
           :epaonnistui ->TallennaOikaisuEpaonnistui
           :paasta-virhe-lapi? true}))
      app))

  TallennaOikaisut
  (process-event [{oikaisut :oikaisut hoitokauden-alkuvuosi :hoitokauden-alkuvuosi} app]
    (let [urakka-id (-> @tila/yleiset :urakka :id)
          validit-oikaisut (->> oikaisut
                             (map #(merge % {::urakka/id urakka-id
                                             ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))
                             ;; Katso että kaikki tiedot syötetty 
                             (filter #(every? % [::valikatselmus/otsikko
                                                 ::valikatselmus/selite
                                                 ::valikatselmus/summa
                                                 ::valikatselmus/hoitokauden-alkuvuosi
                                                 ::urakka/id])))
          ;; Viimeisen oikaisun indeksi, näytetään viimeisenä toast viesti 
          viimeinen-idx (count validit-oikaisut)
          ;; Tallennetaan atomiin oikaisujen määrä
          _ (reset! tavoitehinnan-muutostallennus-max viimeinen-idx)
          ;; Resetoidaan alkutilanne
          _ (reset! tavoitehinnan-muutostallennus-kpl 0)]

      (doseq [oikaisu validit-oikaisut]
        (scrollaa-muutoksiin)
        (tuck-apurit/post! :tallenna-tavoitehinnan-oikaisu
          oikaisu
          {:onnistui ->TallennaOikaisuOnnistui
           :epaonnistui ->TallennaOikaisuEpaonnistui
           :paasta-virhe-lapi? true})))

    (assoc app :tallennus-kesken? true))

  TallennaOikaisuOnnistui
  (process-event [{:keys [vastaus _id]} {:keys [_hoitokauden-alkuvuosi _tavoitehinnan-oikaisut] :as app}]
    (swap! tavoitehinnan-muutostallennus-kpl inc)
    (when (= @tavoitehinnan-muutostallennus-kpl @tavoitehinnan-muutostallennus-max)
      (viesti/nayta-toast! "Oikaisu tallennettu"))
    (->
      (kasittele-valikatselmuksen-vastaus app vastaus)
      (assoc :tallennus-kesken? (if (= @tavoitehinnan-muutostallennus-kpl @tavoitehinnan-muutostallennus-max)
                                  false true))))

  TallennaOikaisuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "TallennaOikaisuEpaonnistui" vastaus)
    (viesti/nayta-toast! "Oikaisun tallennuksessa tapahtui virhe" :varoitus)
    app)

  PoistaOikaisu
  (process-event [{oikaisu :oikaisu id :id} app]
    (if (not (::valikatselmus/oikaisun-id oikaisu))
      (assoc-in app [:tavoitehinnan-muutokset (:hoitokauden-alkuvuosi app) id :poistettu] true)
      (do
        (tuck-apurit/post! app :poista-tavoitehinnan-oikaisu
          oikaisu
          {:onnistui ->PoistaOikaisuOnnistui
           :epaonnistui ->PoistaOikaisuEpaonnistui
           :paasta-virhe-lapi? true})
        (assoc app :tallennus-kesken? true))))

  PoistaOikaisuOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Oikaisu poistettu")
      (kasittele-valikatselmuksen-vastaus app vastaus)))

  PoistaOikaisuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "PoistaOikaisuEpaonnistui" (pr-str vastaus))
    (viesti/nayta-toast! "Oikaisun poistamisessa tapahtui virhe" :varoitus)
    (kasittele-valikatselmuksen-vastaus app vastaus))

  PaivitaTavoitehinnanOikaisut
  (process-event [{hoitokauden-alkuvuosi :hoitokauden-alkuvuosi uusi :uusi} app]
    (assoc-in app [:tavoitehinnan-muutokset hoitokauden-alkuvuosi] uusi))

  ;; Kattohinnan oikaisut

  KattohinnanOikaisuaMuokattu
  (process-event [{kattohinta :kattohinta} app]
    (assoc-in app [:kattohinnan-oikaisu :uusi-kattohinta] kattohinta))

  TallennaKattohinnanOikaisu
  (process-event [{uusi-kattohinta :uusi-kattohinta hoitokauden-alkuvuosi :hoitokauden-alkuvuosi} app]
    (when uusi-kattohinta
      (let [oikaisu {::urakka/id (-> @tila/yleiset :urakka :id)
                     ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                     ::valikatselmus/uusi-kattohinta uusi-kattohinta}]
        (tuck-apurit/post! :tallenna-kattohinnan-oikaisu
          oikaisu
          {:onnistui ->TallennaKattohinnanOikaisuOnnistui
           :epaonnistui ->TallennaKattohinnanOikaisuEpaonnistui
           :paasta-virhe-lapi? true})))
    (assoc app :tallennus-kesken? true))

  TallennaKattohinnanOikaisuOnnistui
  (process-event [{vastaus :vastaus} {:keys [hoitokauden-alkuvuosi] :as app}]
    (viesti/nayta-toast! "Kattohinnan oikaisu tallennettu")
    (kasittele-valikatselmuksen-vastaus app vastaus))

  TallennaKattohinnanOikaisuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "TallennaKattohinnanOikaisuEpaonnistui" vastaus)
    (viesti/nayta-toast!
      (if (str/includes? (str (get-in vastaus [:parse-error :original-text])) "Kattohinnan täytyy olla suurempi kuin tavoitehinta.")
        "Kattohinnan oikaisua ei voitu tallentaa. Kattohinnan tulee olla suurempi kuin tavoitehinta."
        "Kattohinnan oikaisun tallennuksessa tapahtui virhe.")
      :varoitus)
    (kasittele-valikatselmuksen-vastaus app vastaus))

  PoistaKattohinnanOikaisu
  (process-event [_ app]
    (poista-kattohinnan-oikaisu app)
    app)

  PoistaKattohinnanOikaisuOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Kattohinnan oikaisu poistettu")
      (hae-valikatselmuksen-tiedot (-> @tila/yleiset :urakka :id) (:hoitokauden-alkuvuosi app))
      (->
        app
        (update-in [:kattohintojen-oikaisut] dissoc (:hoitokauden-alkuvuosi app))
        (dissoc app :kattohinnan-oikaisu))))

  PoistaKattohinnanOikaisuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "PoistaKattohinnanOikaisuEpaonnistui" vastaus)
    (viesti/nayta-toast! "Kattohinnan oikaisun poistamisessa tapahtui virhe" :varoitus)
    app)

  KattohinnanMuokkaaPainettu
  (process-event [{kattohinta :kattohinta} app]
    (-> app
      (assoc-in [:kattohinnan-oikaisu :muokkaa-painettu?] true)
      (assoc-in [:kattohinnan-oikaisu :uusi-kattohinta] kattohinta)))

  TallennaLupausPaatos
  (process-event [{paatos :paatos} app]
    (tuck-apurit/post! :tee-lupauspaatos
      (assoc paatos :luoja (:id @istunto/kayttaja))
      {:onnistui ->HaeValikatselmuksenTiedotOnnistui
       :epaonnistui ->HaeValikatselmuksenTiedotEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :tallennus-kesken? true))

  PoistaLupausPaatos
  (process-event [{paatos :paatos} app]
    (tuck-apurit/post! :poista-lupauspaatos
      paatos
      {:onnistui ->PoistaLupausPaatosOnnistui
       :epaonnistui ->PoistaLupausPaatosEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :tallennus-kesken? true))

  PoistaLupausPaatosOnnistui
  (process-event [{vastaus :vastaus} app]
    (kasittele-valikatselmuksen-vastaus app vastaus))

  PoistaLupausPaatosEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Päätöksen poistossa tapahtui virhe" :varoitus)
    (-> app
      (assoc :haku-kaynnissa? false)
      (assoc :tallennus-kesken? false)))

  ValitseHoitokausi
  (process-event [{urakkaid :urakkaid vuosi :vuosi} app]
    (let [app (-> app
                (assoc :valittu-kuukausi nil)
                ;; Lupaukset on kiinteässä linkissä kustannusten seurannan kanssa joten tarvitaan hoitokaudellekin sama avain
                (assoc :valittu-hoitokausi [(pvm/hoitokauden-alkupvm vuosi)
                                            (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
                (assoc :nykyhetki (pvm/nyt))
                (assoc :haku-kaynnissa? true)
                (assoc :hoitokauden-alkuvuosi vuosi))]
      ;; Haetaan kaikki välikatselmuksessa tarvittavat tiedot
      (hae-valikatselmuksen-tiedot (-> @tila/yleiset :urakka :id) (:hoitokauden-alkuvuosi app))
      (assoc app :haku-kaynnissa? true)))

  ;; Monta paatosta voi olla avattuna kerrallaan
  AvaaPaatos
  (process-event [{avain :avain} app]
    (let [app (if (nil? (:avatut-paatokset app))
                (assoc app :avatut-paatokset #{})
                app)]
      (if (contains? (:avatut-paatokset app) avain)
        (assoc app :avatut-paatokset (disj (:avatut-paatokset app) avain))
        (assoc app :avatut-paatokset (merge (:avatut-paatokset app) avain)))))

  PaivitaKattohinnanSiirtoCheckbox
  (process-event [{uusi-arvo :uusi-arvo} app]
    (let [paatos (first (filter #(= (ffirst %) :kattohinnan-ylitys) (:paatokset app)))
          paatos (assoc-in paatos [:kattohinnan-ylitys :siirra?] uusi-arvo)]
      (update app :paatokset (fn [paatokset]
                               (map #(if (= (ffirst %) :kattohinnan-ylitys)
                                       paatos
                                       %)
                                 paatokset)))))

  PaivitaKattohinnanSiirtoMaara
  (process-event [{uusi-arvo :uusi-arvo} app]
    (let [paatos (first (filter #(= (ffirst %) :kattohinnan-ylitys) (:paatokset app)))
          kattohinnan-ylityksen-maara (get-in paatos [:kattohinnan-ylitys :ylityksen_maara])
          paatos (-> paatos
                   ;; Poistetaan mahdollinen virhe
                   (assoc-in [:kattohinnan-ylitys :virhe] nil)
                   ;; Merkitään saatu siirtomäärä
                   (assoc-in [:kattohinnan-ylitys :siirrettava_maara] uusi-arvo)
                   ;; Vähennetään kattohinnan ylityksen määrästä siirrettävä summa
                   (assoc-in [:kattohinnan-ylitys :urakoitsija_maksaa] (- kattohinnan-ylityksen-maara uusi-arvo)))]
      (update app :paatokset (fn [paatokset]
                               (map #(if (= (ffirst %) :kattohinnan-ylitys)
                                       paatos
                                       %)
                                 paatokset)))))

  TallennaTavoitehinnanMuutosPaatos
  (process-event [{paatos :paatos} app]
    (tuck-apurit/post! :tee-tavoitehinnan-muutospaatos
      (assoc paatos :luoja (:id @istunto/kayttaja))
      {:onnistui ->HaeValikatselmuksenTiedotOnnistui
       :epaonnistui ->HaeValikatselmuksenTiedotEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :tallennus-kesken? true))

  TallennaTavoitehinnanPysyvaMuutosPaatos
  (process-event [{paatos :paatos} app]
    (tuck-apurit/post! :tee-tavoitehinnan-pysyvamuutospaatos
      (assoc paatos :luoja (:id @istunto/kayttaja))
      {:onnistui ->HaeValikatselmuksenTiedotOnnistui
       :epaonnistui ->HaeValikatselmuksenTiedotEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :tallennus-kesken? true))

  TallennaTavoitehinnanAlitusPaatos
  (process-event [{paatos :paatos} app]
    (tuck-apurit/post! :tee-tavoitehinnan-alituspaatos
      (assoc paatos :luoja (:id @istunto/kayttaja))
      {:onnistui ->HaeValikatselmuksenTiedotOnnistui
       :epaonnistui ->HaeValikatselmuksenTiedotEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :tallennus-kesken? true))

  TallennaTavoitehinnanYlitysPaatos
  (process-event [{paatos :paatos} app]
    (tuck-apurit/post! :tee-tavoitehinnan-ylityspaatos
      (assoc paatos :luoja (:id @istunto/kayttaja))
      {:onnistui ->HaeValikatselmuksenTiedotOnnistui
       :epaonnistui ->HaeValikatselmuksenTiedotEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :tallennus-kesken? true))

  PoistaTavoitehinnanYlitysPaatos
  (process-event [{paatos :paatos} app]
    (tuck-apurit/post! :poista-tavoitehinnan-ylityspaatos
      (assoc paatos :luoja (:id @istunto/kayttaja))
      {:onnistui ->HaeValikatselmuksenTiedotOnnistui
       :epaonnistui ->HaeValikatselmuksenTiedotEpaonnistui})
    (assoc app :tallennus-kesken? true))

  TallennaKattohinnanYlitysPaatos
  (process-event [{paatos :paatos} app]
    (tuck-apurit/post! :tee-kattohinnan-ylityspaatos
      (assoc paatos :luoja (:id @istunto/kayttaja))
      {:onnistui ->HaeValikatselmuksenTiedotOnnistui
       :epaonnistui ->TallennaKattohinnanYlitysPaatosEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :tallennus-kesken? true))

  TallennaKattohinnanYlitysPaatosEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (let [virhe (kasittele-throw-virhe vastaus)
          paatos (first (filter #(= (ffirst %) :kattohinnan-ylitys) (:paatokset app)))
          paatos (assoc-in paatos [:kattohinnan-ylitys :virhe] virhe)
          app (update app :paatokset (fn [paatokset]
                                       (map #(if (= (ffirst %) :kattohinnan-ylitys)
                                               paatos
                                               %)
                                         paatokset)))]
      (viesti/nayta-toast! (if virhe virhe "Tapahtui virhe. Tarkista tilanne ja koeta hetken päästä uudelleen.") :varoitus)
      (-> app
        (assoc :tallennus-kesken? false)
        (assoc :haku-kaynnissa? false))))

  PoistaKattohinnanYlitysPaatos
  (process-event [{paatos :paatos} app]
    (tuck-apurit/post! :poista-kattohinnan-ylityspaatos
      (assoc paatos :luoja (:id @istunto/kayttaja))
      {:onnistui ->HaeValikatselmuksenTiedotOnnistui
       :epaonnistui ->HaeValikatselmuksenTiedotEpaonnistui})
    (assoc app :tallennus-kesken? true))

  TallennaPoytakirjanRaporttiPaatos
  (process-event [{paatos :paatos} app]
    (tuck-apurit/post! :tee-poytakirjan-raporttipaatos
      (assoc paatos :luoja (:id @istunto/kayttaja))
      {:onnistui ->HaeValikatselmuksenTiedotOnnistui
       :epaonnistui ->HaeValikatselmuksenTiedotEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :tallennus-kesken? true))

  PoistaPoytakirjanRaporttiPaatos
  (process-event [{paatos :paatos} app]
    (tuck-apurit/post! :poista-poytakirjan-raporttipaatos
      (assoc paatos :luoja (:id @istunto/kayttaja))
      {:onnistui ->HaeValikatselmuksenTiedotOnnistui
       :epaonnistui ->HaeValikatselmuksenTiedotEpaonnistui})
    (assoc app :tallennus-kesken? true))

  TallennaHoidonjohtopalkkionMuutospaatos
  (process-event [{paatos :paatos} app]
    (tuck-apurit/post! :tee-hoidonjohtopalkkion-muutospaatos
      (assoc paatos :luoja (:id @istunto/kayttaja))
      {:onnistui ->HaeValikatselmuksenTiedotOnnistui
       :epaonnistui ->HaeValikatselmuksenTiedotEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :tallennus-kesken? true))

  TallennaHoitokaudenlopunHintapaatos
  (process-event [{paatos :paatos} app]
    (tuck-apurit/post! :tee-hv-lopun-tavoite-ja-kattohintapaatos
      (assoc paatos :luoja (:id @istunto/kayttaja))
      {:onnistui ->HaeValikatselmuksenTiedotOnnistui
       :epaonnistui ->HaeValikatselmuksenTiedotEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :tallennus-kesken? true))

  TallennaHoitovuodenlopunIndeksikorjauspaatos
  (process-event [{paatos :paatos} app]
    (tuck-apurit/post! :tee-indeksikorjauspaatos
      (assoc paatos :luoja (:id @istunto/kayttaja))
      {:onnistui ->HaeValikatselmuksenTiedotOnnistui
       :epaonnistui ->HaeValikatselmuksenTiedotEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :tallennus-kesken? true))

  PoistaHoitovuodenlopunIndeksikorjauspaatos
  (process-event [{paatos :paatos} app]
    (tuck-apurit/post! :poista-indeksikorjauspaatos
      (assoc paatos :luoja (:id @istunto/kayttaja))
      {:onnistui ->HaeValikatselmuksenTiedotOnnistui
       :epaonnistui ->HaeValikatselmuksenTiedotEpaonnistui})
    (assoc app :tallennus-kesken? true))

  HaeKetjutetustiKumoutuvatPaatokset
  (process-event [{paatos :paatos peru-fn :peru-fn} app]
    (tuck-apurit/post!
      :hae-ketjutetusti-kumoutuvat-paatokset
      paatos
      {:onnistui ->HaeKumoutuvatOnnistui
       :epaonnistui ->HaeValikatselmuksenTiedotEpaonnistui})
    (assoc app
      :peru-fn peru-fn
      :tallennus-kesken? true
      :nayta-kumoa-modal? false
      :tehdyt-kumoutuvat-paatokset nil
      :kumottava-paatos-nimi (:nimi paatos)))

  HaeKumoutuvatOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app
      :tallennus-kesken? false
      :nayta-kumoa-modal? true
      :tehdyt-kumoutuvat-paatokset vastaus))

  PeruValikatselmusPaatos
  (process-event [{:keys [paatos]} app]
    (tuck-apurit/post!
      :poista-paatokset-ketjutetusti
      {:paatos (assoc paatos :luoja (:id @istunto/kayttaja))
       :tehdyt-kumoutuvat-paatokset (:tehdyt-kumoutuvat-paatokset app)}
      {:onnistui ->HaeValikatselmuksenTiedotOnnistui
       :epaonnistui ->HaeValikatselmuksenTiedotEpaonnistui})
    (assoc app
      :tallennus-kesken? true
      :nayta-kumoa-modal? false
      :tehdyt-kumoutuvat-paatokset nil
      :kumottava-paatos-nimi (:nimi paatos)))

  SuljePaatosModal
  (process-event [_ app]
    (assoc app
      :peru-fn nil
      :tallennus-kesken? false
      :nayta-kumoa-modal? false
      :kumottava-paatos-nimi nil
      :tehdyt-kumoutuvat-paatokset nil)))

(defn avaa-tai-sulje-haitari [event avain]
  (when (dom/enter-nappain? event)
    (tuck/action!
      (fn [e!]
        (e! (->AvaaPaatos avain))))))

(defn ota-paatos [paatokset avain]
  (first (vals (first (filter #(= (ffirst %) avain) paatokset)))))
