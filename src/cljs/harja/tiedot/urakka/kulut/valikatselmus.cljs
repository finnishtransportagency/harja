(ns harja.tiedot.urakka.kulut.valikatselmus
  (:require [tuck.core :refer [process-event] :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.domain.urakka :as urakka]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka.urakka :as tila]))

(defrecord TallennaOikaisu [oikaisu id])
(defrecord TallennaOikaisuOnnistui [vastaus id])
(defrecord TallennaOikaisuEpaonnistui [vastaus])
(defrecord PoistaOikaisu [oikaisu muokkaa!])
(defrecord PoistaOikaisuOnnistui [vastaus muokkaa!])
(defrecord PoistaOikaisuEpaonnistui [vastaus])
(defrecord PaivitaPaatosLomake [tiedot paatos])
(defrecord TallennaPaatos [paatos])
(defrecord TallennaPaatosOnnistui [vastaus tyyppi])
(defrecord TallennaPaatosEpaonnistui [vastaus])
(defrecord MuokkaaPaatosta [lomake-avain])
(defrecord AlustaPaatosLomakkeet [vastaus])
(defrecord HaeUrakanPaatokset [urakka])
(defrecord HaeUrakanPaatoksetOnnistui [vastaus])
(defrecord HaeUrakanPaatoksetEpaonnistui [vastaus])

(def tyyppi->lomake
  {::valikatselmus/tavoitehinnan-ylitys :tavoitehinnan-ylitys-lomake
   ::valikatselmus/tavoitehinnan-alitus :tavoitehinnan-alitus-lomake
   ::valikatselmus/kattohinnan-ylitys :kattohinnan-ylitys-lomake})

(extend-protocol tuck/Event
  TallennaOikaisu
  (process-event [{oikaisu :oikaisu id :id} app]
    (let [oikaisu (merge {::urakka/id (-> @tila/yleiset :urakka :id)
                          :harja.domain.kulut.valikatselmus/hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)}
                         oikaisu)]
      (tuck-apurit/post! :tallenna-tavoitehinnan-oikaisu
                         oikaisu
                         {:onnistui ->TallennaOikaisuOnnistui
                          :onnistui-parametrit [id]
                          :epaonnistui ->TallennaOikaisuEpaonnistui
                          :paasta-virhe-lapi? true}))
    app)

  TallennaOikaisuOnnistui
  (process-event [{vastaus :vastaus id :id} app]
    (let [oikaisut-atom (:tavoitehinnan-oikaisut-atom app)]
      (when (map? vastaus)
        ;; Uusi oikaisu luotu
        (swap! oikaisut-atom assoc id vastaus))
      (viesti/nayta-toast! "Oikaisu tallennettu")
      app))

  TallennaOikaisuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "TallennaOikaisuEpaonnistui" vastaus)
    (viesti/nayta-toast! "Oikaisun tallennuksessa tapahtui virhe" :varoitus)
    app)

  PoistaOikaisu
  (process-event [{oikaisu :oikaisu muokkaa! :muokkaa!} app]
    (if (not (::valikatselmus/oikaisun-id oikaisu))
      (muokkaa! assoc :poistettu true)
      (tuck-apurit/post! :poista-tavoitehinnan-oikaisu
                         oikaisu
                         {:onnistui ->PoistaOikaisuOnnistui
                          :epaonnistui ->PoistaOikaisuEpaonnistui
                          :onnistui-parametrit [muokkaa!]
                          :paasta-virhe-lapi? true}))
    app)

  PoistaOikaisuOnnistui
  (process-event [{vastaus :vastaus muokkaa! :muokkaa!} app]
    (do
      (muokkaa! assoc :poistettu true)
      (viesti/nayta-toast! "Oikaisu poistettu")
      app))

  PoistaOikaisuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "PoistaOikaisuEpaonnistui" vastaus)
    (viesti/nayta-toast! "Oikaisun poistamisessa tapahtui virhe" :varoitus)
    app)

  HaeUrakanPaatokset
  (process-event [{urakka :urakka} app]
    (tuck-apurit/post! :hae-urakan-paatokset
                       {::urakka/id urakka}
                       {:onnistui ->AlustaPaatosLomakkeet
                        :epaonnistui ->HaeUrakanPaatoksetEpaonnistui})
    app)

  HaeUrakanPaatoksetOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :urakan-paatokset vastaus))

  HaeUrakanPaatoksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "HaeUrakanPaatoksetEpaonnistui" vastaus)
    (viesti/nayta-toast! "Urakan päätösten haku epäonnistui!" :varoitus)
    app)

  AlustaPaatosLomakkeet
  (process-event [{vastaus :vastaus} app]
    (let [ylitys (first (filter
                          #(and (= (name ::valikatselmus/tavoitehinnan-ylitys)
                                   (::valikatselmus/tyyppi %))
                                (= (:hoitokauden-alkuvuosi app) (::valikatselmus/hoitokauden-alkuvuosi %))) vastaus))]
      (as-> app app
            (assoc app :urakan-paatokset vastaus)
            (if ylitys (assoc-in app [:tavoitehinnan-ylitys-lomake]
                                 {::valikatselmus/paatoksen-id (::valikatselmus/paatoksen-id ylitys)
                                  :euro-vai-prosentti :euro
                                  :maksu (::valikatselmus/urakoitsijan-maksu ylitys)})
                       app))))

  PaivitaPaatosLomake
  (process-event [{tiedot :tiedot paatos :paatos} app]
    (assoc app paatos tiedot))

  TallennaPaatos
  (process-event [{paatos :paatos} app]
    (tuck-apurit/post! :tallenna-urakan-paatos
                       paatos
                       {:onnistui ->TallennaPaatosOnnistui
                        :onnistui-parametrit [(::valikatselmus/tyyppi paatos)]
                        :epaonnistui ->TallennaPaatosEpaonnistui})
    app)

  TallennaPaatosOnnistui
  (process-event [{tyyppi :tyyppi vastaus :vastaus} app]
    (viesti/nayta-toast! "Päätöksen tallennus onnistui")
    (-> app
        (assoc-in [(tyyppi tyyppi->lomake) ::valikatselmus/paatoksen-id] (::valikatselmus/paatoksen-id vastaus))
        (assoc-in [(tyyppi tyyppi->lomake) :muokataan?] false)
        (assoc-in [(tyyppi tyyppi->lomake) :tallennettu?] true)))

  TallennaPaatosEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "TallennaPaatosEpaonnistui" vastaus)
    (viesti/nayta-toast! "Päätöksen tallennuksessa tapahtui virhe" :varoitus)
    app)

  MuokkaaPaatosta
  (process-event [{lomake-avain :lomake-avain} app]
    (assoc-in app [lomake-avain :muokataan?] true)))