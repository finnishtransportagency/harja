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
(defrecord AlustaPaatosLomakkeet [paatokset hoitokauden-alkuvuosi])
(defrecord HaeUrakanPaatokset [urakka])
(defrecord HaeUrakanPaatoksetOnnistui [vastaus])
(defrecord HaeUrakanPaatoksetEpaonnistui [vastaus])
(defrecord PaivitaTavoitepalkkionTyyppi [tyyppi])
(defrecord PaivitaMaksunTyyppi [tyyppi])

(def tyyppi->lomake
  {::valikatselmus/tavoitehinnan-ylitys :tavoitehinnan-ylitys-lomake
   ::valikatselmus/tavoitehinnan-alitus :tavoitehinnan-alitus-lomake
   ::valikatselmus/kattohinnan-ylitys :kattohinnan-ylitys-lomake})

(defn alusta-paatos-lomakkeet [paatokset hoitokauden-alkuvuosi]
  (let [filtteroi-paatos (fn [tyyppi]
                           (first (filter
                                    #(and (= (name tyyppi)
                                             (::valikatselmus/tyyppi %))
                                          (= hoitokauden-alkuvuosi (::valikatselmus/hoitokauden-alkuvuosi %))) paatokset)))
        tavoitehinnan-ylitys (filtteroi-paatos ::valikatselmus/tavoitehinnan-ylitys)
        alitus (filtteroi-paatos ::valikatselmus/tavoitehinnan-alitus)]
    {:tavoitehinnan-ylitys-lomake (when (not (nil? tavoitehinnan-ylitys))
                                    {::valikatselmus/paatoksen-id (::valikatselmus/paatoksen-id tavoitehinnan-ylitys)
                                     :euro-vai-prosentti :euro
                                     :maksu (::valikatselmus/urakoitsijan-maksu tavoitehinnan-ylitys)})
     :tavoitehinnan-alitus-lomake (when (not (nil? alitus))
                                    {::valikatselmus/paatoksen-id (::valikatselmus/paatoksen-id alitus)
                                     :euro-vai-prosentti :euro
                                     :maksu (::valikatselmus/urakoitsijan-maksu alitus)
                                     :tavoitepalkkion-tyyppi (cond
                                                               (and (= 0 (::valikatselmus/siirto alitus))
                                                                    (neg? (::valikatselmus/urakoitsijan-maksu alitus)))
                                                               :maksu

                                                               (and (pos? (::valikatselmus/siirto alitus))
                                                                    (= 0 (::valikatselmus/urakoitsijan-maksu alitus)))
                                                               :siirto

                                                               :else :osa)})}))

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
    (when (map? vastaus)
      ;; Uusi oikaisu luotu
      (let [oikaisut-atom (:tavoitehinnan-oikaisut-atom app)
            vanha (get @oikaisut-atom id)
            vastaus (merge vanha vastaus)]
        (swap! oikaisut-atom assoc id vastaus)))
    (viesti/nayta-toast! "Oikaisu tallennettu")
    app)

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
                       {:onnistui ->HaeUrakanPaatoksetOnnistui
                        :epaonnistui ->HaeUrakanPaatoksetEpaonnistui})
    app)

  HaeUrakanPaatoksetOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [{tavoitehinnan-ylitys-lomake :tavoitehinnan-ylitys-lomake
           tavoitehinnan-alitus-lomake :tavoitehinnan-alitus-lomake
           kattohinnan-ylitys-lomake :kattohinnan-ylitys-lomake} (alusta-paatos-lomakkeet vastaus (:hoitokauden-alkuvuosi app))]
      (cond-> app
              tavoitehinnan-ylitys-lomake (assoc :tavoitehinnan-ylitys-lomake tavoitehinnan-ylitys-lomake)
              tavoitehinnan-alitus-lomake (assoc :tavoitehinnan-alitus-lomake tavoitehinnan-alitus-lomake)
              kattohinnan-ylitys-lomake (assoc :kattohinnan-ylitys-lomake kattohinnan-ylitys-lomake)
              :aina (assoc :urakan-paatokset vastaus))))

  HaeUrakanPaatoksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "HaeUrakanPaatoksetEpaonnistui" vastaus)
    (viesti/nayta-toast! "Urakan päätösten haku epäonnistui!" :varoitus)
    app)

  AlustaPaatosLomakkeet
  (process-event [{paatokset :paatokset hoitokauden-alkuvuosi :hoitokauden-alkuvuosi} app]
    (let [{tavoitehinnan-ylitys-lomake :tavoitehinnan-ylitys-lomake
           tavoitehinnan-alitus-lomake :tavoitehinnan-alitus-lomake
           kattohinnan-ylitys-lomake :kattohinnan-ylitys-lomake} (alusta-paatos-lomakkeet paatokset hoitokauden-alkuvuosi)]
      (cond-> app
              tavoitehinnan-ylitys-lomake (assoc :tavoitehinnan-ylitys-lomake tavoitehinnan-ylitys-lomake)
              tavoitehinnan-alitus-lomake (assoc :tavoitehinnan-alitus-lomake tavoitehinnan-alitus-lomake)
              kattohinnan-ylitys-lomake (assoc :kattohinnan-ylitys-lomake kattohinnan-ylitys-lomake))))

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
    (assoc-in app [lomake-avain :muokataan?] true))

  PaivitaTavoitepalkkionTyyppi
  (process-event [{tyyppi :tyyppi} app]
    (assoc-in app [:tavoitehinnan-alitus-lomake :tavoitepalkkion-tyyppi] tyyppi))

  PaivitaMaksunTyyppi
  (process-event [{tyyppi :tyyppi} app]
    (assoc-in app [:kattohinnan-ylitys-lomake :maksun-tyyppi] tyyppi)))
