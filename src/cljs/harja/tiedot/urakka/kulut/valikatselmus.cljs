(ns harja.tiedot.urakka.kulut.valikatselmus
  (:require [tuck.core :refer [process-event] :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.domain.urakka :as urakka]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.tiedot.urakka.lupaus-tiedot :as lupaus-tiedot]
            [harja.tiedot.urakka.kulut.mhu-kustannusten-seuranta :as kustannusten-seuranta-tiedot]
            [harja.tiedot.urakka.kulut.yhteiset :as t-yhteiset]
            [taoensso.timbre :as log]))

;; Oikaisut
(defrecord TallennaOikaisu [oikaisu id])
(defrecord TallennaOikaisuOnnistui [vastaus id])
(defrecord TallennaOikaisuEpaonnistui [vastaus])
(defrecord PoistaOikaisu [oikaisu id])
(defrecord PoistaOikaisuOnnistui [vastaus id])
(defrecord PoistaOikaisuEpaonnistui [vastaus])

;; Kattohinnan oikaisut
(defrecord KattohinnanOikaisuaMuokattu [kattohinta])
(defrecord TallennaKattohinnanOikaisu [])
(defrecord TallennaKattohinnanOikaisuOnnistui [vastaus id])
(defrecord TallennaKattohinnanOikaisuEpaonnistui [vastaus])
(defrecord PoistaKattohinnanOikaisu [oikaisu id])
(defrecord PoistaKattohinnanOikaisuOnnistui [vastaus id])
(defrecord PoistaKattohinnanOikaisuEpaonnistui [vastaus])

;; Päätökset
(defrecord PaivitaPaatosLomake [tiedot paatos])
(defrecord TallennaPaatos [paatos])
(defrecord TallennaPaatosOnnistui [vastaus tyyppi uusi?])
(defrecord TallennaPaatosEpaonnistui [vastaus])
(defrecord MuokkaaPaatosta [lomake-avain])
(defrecord AlustaPaatosLomakkeet [paatokset hoitokauden-alkuvuosi])
(defrecord HaeUrakanPaatokset [urakka])
(defrecord HaeUrakanPaatoksetOnnistui [vastaus])
(defrecord HaeUrakanPaatoksetEpaonnistui [vastaus])
(defrecord PaivitaTavoitepalkkionTyyppi [tyyppi])
(defrecord PaivitaMaksunTyyppi [tyyppi])
(defrecord PoistaLupausPaatos [id])
(defrecord PoistaLupausPaatosOnnistui [vastaus])
(defrecord PoistaLupausPaatosEpaonnistui [vastaus])


(def tyyppi->lomake
  {::valikatselmus/tavoitehinnan-ylitys :tavoitehinnan-ylitys-lomake
   ::valikatselmus/tavoitehinnan-alitus :tavoitehinnan-alitus-lomake
   ::valikatselmus/kattohinnan-ylitys :kattohinnan-ylitys-lomake
   ::valikatselmus/lupaus-bonus :lupaus-bonus-lomake
   ::valikatselmus/lupaus-sanktio :lupaus-sanktio-lomake})

(defn nollaa-paatokset [app]
  (let [hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        oikaisujen-summa (t-yhteiset/oikaisujen-summa (:tavoitehinnan-oikaisut app) hoitokauden-alkuvuosi)
        hoitokausi-nro (urakka-tiedot/hoitokauden-jarjestysnumero hoitokauden-alkuvuosi (-> @tila/yleiset :urakka :loppupvm))
        tavoitehinta (or (kustannusten-seuranta-tiedot/hoitokauden-tavoitehinta hoitokausi-nro app) 0)
        oikaistu-tavoitehinta (+ oikaisujen-summa tavoitehinta)
        toteuma (or (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa]) 0)
        alituksen-maara (- oikaistu-tavoitehinta toteuma)
        tavoitepalkkio (* valikatselmus/+tavoitepalkkio-kerroin+ alituksen-maara)
        maksimi-tavoitepalkkio (min tavoitepalkkio (* valikatselmus/+maksimi-tavoitepalkkio-prosentti+ oikaistu-tavoitehinta))
        maksimi-tavoitepalkkio-prosenttina (* 100 (/ maksimi-tavoitepalkkio tavoitepalkkio))]
    (assoc app :urakan-paatokset nil
               ;; Palautetaan lomakkeiden numerokenttien oletusarvot näin, koska numerokentän
               ;; oletusarvo asetetaan vain kun komponentti piirretään ensimmäisen kerran.
               :tavoitehinnan-ylitys-lomake {:maksu 30}
               :tavoitehinnan-alitus-lomake {:maksu (/ maksimi-tavoitepalkkio-prosenttina 2)}
               :kattohinnan-ylitys-lomake nil)))

(defn alusta-paatos-lomakkeet [paatokset hoitokauden-alkuvuosi]
  (let [filtteroi-paatos (fn [tyyppi]
                           (first (filter
                                    #(and (= (name tyyppi) (::valikatselmus/tyyppi %))
                                          (= hoitokauden-alkuvuosi (::valikatselmus/hoitokauden-alkuvuosi %)))
                                    paatokset)))
        tavoitehinnan-ylitys (filtteroi-paatos ::valikatselmus/tavoitehinnan-ylitys)
        alitus (filtteroi-paatos ::valikatselmus/tavoitehinnan-alitus)
        kattohinnan-ylitys (filtteroi-paatos ::valikatselmus/kattohinnan-ylitys)
        lupausbonus (filtteroi-paatos ::valikatselmus/lupaus-bonus)
        lupaussanktio (filtteroi-paatos ::valikatselmus/lupaus-sanktio)]
    {:tavoitehinnan-ylitys-lomake (when (not (nil? tavoitehinnan-ylitys))
                                    {::valikatselmus/paatoksen-id (::valikatselmus/paatoksen-id tavoitehinnan-ylitys)
                                     :euro-vai-prosentti :euro
                                     :maksu (::valikatselmus/urakoitsijan-maksu tavoitehinnan-ylitys)})
     :kattohinnan-ylitys-lomake (when (not (nil? kattohinnan-ylitys))
                                  {::valikatselmus/paatoksen-id (::valikatselmus/paatoksen-id kattohinnan-ylitys)
                                   :maksun-tyyppi (cond (and (pos? (::valikatselmus/urakoitsijan-maksu kattohinnan-ylitys))
                                                             (pos? (::valikatselmus/siirto kattohinnan-ylitys))) :osa
                                                        (pos? (::valikatselmus/siirto kattohinnan-ylitys)) :siirto
                                                        :else :maksu)
                                   :siirto (if (pos? (::valikatselmus/siirto kattohinnan-ylitys)) (::valikatselmus/siirto kattohinnan-ylitys) nil)})
     :tavoitehinnan-alitus-lomake (when (not (nil? alitus))
                                    {::valikatselmus/paatoksen-id (::valikatselmus/paatoksen-id alitus)
                                     :euro-vai-prosentti :euro
                                     ;; Näytetään maksu positiivisena lomakkeella
                                     :maksu (- (::valikatselmus/urakoitsijan-maksu alitus))
                                     :tavoitepalkkion-tyyppi (cond
                                                               (and (= 0 (::valikatselmus/siirto alitus))
                                                                    (neg? (::valikatselmus/urakoitsijan-maksu alitus)))
                                                               :maksu

                                                               (and (pos? (::valikatselmus/siirto alitus))
                                                                    (= 0 (::valikatselmus/urakoitsijan-maksu alitus)))
                                                               :siirto

                                                               :else :osa)})
     :lupaus-bonus-lomake (when (not (nil? lupausbonus))
                            {::valikatselmus/paatoksen-id (::valikatselmus/paatoksen-id lupausbonus)})
     :lupaus-sanktio-lomake (when (not (nil? lupaussanktio))
                            {::valikatselmus/paatoksen-id (::valikatselmus/paatoksen-id lupaussanktio)})}))

(defn hae-lupaustiedot [app]
  (lupaus-tiedot/hae-urakan-lupaustiedot app (:urakka @tila/yleiset)))

(defn hae-urakan-paatokset [app urakka-id]
  (tuck-apurit/post! app :hae-urakan-paatokset
                     {::urakka/id urakka-id}
                     {:onnistui ->HaeUrakanPaatoksetOnnistui
                      :epaonnistui ->HaeUrakanPaatoksetEpaonnistui}))

(extend-protocol tuck/Event
  ;; Tavoitehinnan oikaisut
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
  (process-event [{vastaus :vastaus id :id} {:keys [hoitokauden-alkuvuosi tavoitehinnan-oikaisut] :as app}]
    (let [vanha (get-in tavoitehinnan-oikaisut [hoitokauden-alkuvuosi id])
          uusi (if (map? vastaus)
                 vastaus
                 (select-keys vanha [::valikatselmus/oikaisun-id
                                     ::valikatselmus/hoitokauden-alkuvuosi
                                     ::valikatselmus/otsikko
                                     ::valikatselmus/selite
                                     :lisays-tai-vahennys
                                     ::valikatselmus/summa]))]
      (viesti/nayta-toast! "Oikaisu tallennettu")
      ;; Päivitetään sekä välikatselmuksen, että kustannusseurannan tiedot
      (hae-lupaustiedot app)
      (kustannusten-seuranta-tiedot/hae-kustannukset (-> @tila/yleiset :urakka :id) hoitokauden-alkuvuosi nil nil)
      (cond-> app
              uusi (assoc-in [:tavoitehinnan-oikaisut hoitokauden-alkuvuosi id] uusi)
              :aina (nollaa-paatokset))))

  TallennaOikaisuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "TallennaOikaisuEpaonnistui" vastaus)
    (viesti/nayta-toast! "Oikaisun tallennuksessa tapahtui virhe" :varoitus)
    app)

  PoistaOikaisu
  (process-event [{oikaisu :oikaisu id :id} app]
    (if (not (::valikatselmus/oikaisun-id oikaisu))
      (assoc-in app [:tavoitehinnan-oikaisut (:hoitokauden-alkuvuosi app) id :poistettu] true)
      (tuck-apurit/post! app :poista-tavoitehinnan-oikaisu
                         oikaisu
                         {:onnistui ->PoistaOikaisuOnnistui
                          :epaonnistui ->PoistaOikaisuEpaonnistui
                          :onnistui-parametrit [id]
                          :paasta-virhe-lapi? true})))

  PoistaOikaisuOnnistui
  (process-event [{vastaus :vastaus id :id} app]
    (do
      (viesti/nayta-toast! "Oikaisu poistettu")
      (hae-lupaustiedot app)
      (kustannusten-seuranta-tiedot/hae-kustannukset (-> @tila/yleiset :urakka :id) (:hoitokauden-alkuvuosi app) nil nil)
      (-> app
          (assoc-in [:tavoitehinnan-oikaisut (:hoitokauden-alkuvuosi app) id :poistettu] true)
          (nollaa-paatokset))))

  PoistaOikaisuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "PoistaOikaisuEpaonnistui" vastaus)
    (viesti/nayta-toast! "Oikaisun poistamisessa tapahtui virhe" :varoitus)
    app)

  ;; Kattohinnan oikaisut

  KattohinnanOikaisuaMuokattu
  (process-event [{kattohinta :kattohinta} app]
    (log/debug "KattohinnanOikaisuaMuokattu" kattohinta)
    (assoc-in app [:kattohinnan-oikaisu :uusi-kattohinta] kattohinta))

  TallennaKattohinnanOikaisu
  (process-event [_ app]
    (let [oikaisu {::urakka/id (-> @tila/yleiset :urakka :id)
                   :harja.domain.kulut.valikatselmus/hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
                   ::valikatselmus/uusi-kattohinta (get-in app [:kattohinnan-oikaisu :uusi-kattohinta])
                   }]
      (log/debug "TallennaKattohinnanOikaisu" oikaisu)
      (tuck-apurit/post! :tallenna-kattohinnan-oikaisu
        oikaisu
        {:onnistui ->TallennaOikaisuOnnistui
         ;:onnistui-parametrit [id] ;; FIXME
         :epaonnistui ->TallennaOikaisuEpaonnistui
         :paasta-virhe-lapi? true}))
    app)

  ;; TODO
  TallennaKattohinnanOikaisuOnnistui
  (process-event [{vastaus :vastaus id :id} {:keys [hoitokauden-alkuvuosi kattohinnan-oikaisut] :as app}]
    (let [vanha (get-in kattohinnan-oikaisut [hoitokauden-alkuvuosi id])
          uusi (if (map? vastaus)
                 vastaus
                 (select-keys vanha [::valikatselmus/oikaisun-id
                                     ::valikatselmus/hoitokauden-alkuvuosi
                                     ::valikatselmus/otsikko
                                     ::valikatselmus/selite
                                     :lisays-tai-vahennys
                                     ::valikatselmus/summa]))]
      (viesti/nayta-toast! "KattohinnanOikaisu tallennettu")
      ;; Päivitetään sekä välikatselmuksen, että kustannusseurannan tiedot
      (hae-lupaustiedot app)
      (kustannusten-seuranta-tiedot/hae-kustannukset (-> @tila/yleiset :urakka :id) hoitokauden-alkuvuosi nil nil)
      (cond-> app
        uusi (assoc-in [:kattohinnan-oikaisut hoitokauden-alkuvuosi id] uusi)
        :aina (nollaa-paatokset))))

  ;; TODO
  TallennaKattohinnanOikaisuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "TallennaKattohinnanOikaisuEpaonnistui" vastaus)
    (viesti/nayta-toast! "KattohinnanOikaisun tallennuksessa tapahtui virhe" :varoitus)
    app)

  ;; TODO
  PoistaKattohinnanOikaisu
  (process-event [{oikaisu :oikaisu id :id} app]
    (if (not (::valikatselmus/oikaisun-id oikaisu))
      (assoc-in app [:kattohinnan-oikaisut (:hoitokauden-alkuvuosi app) id :poistettu] true)
      (tuck-apurit/post! app :poista-kattohinnan-oikaisu
        oikaisu
        {:onnistui ->PoistaKattohinnanOikaisuOnnistui
         :epaonnistui ->PoistaKattohinnanOikaisuEpaonnistui
         :onnistui-parametrit [id]
         :paasta-virhe-lapi? true})))

  ;; TODO
  PoistaKattohinnanOikaisuOnnistui
  (process-event [{vastaus :vastaus id :id} app]
    (do
      (viesti/nayta-toast! "KattohinnanOikaisu poistettu")
      (hae-lupaustiedot app)
      (kustannusten-seuranta-tiedot/hae-kustannukset (-> @tila/yleiset :urakka :id) (:hoitokauden-alkuvuosi app) nil nil)
      (-> app
        (assoc-in [:kattohinnan-oikaisut (:hoitokauden-alkuvuosi app) id :poistettu] true)
        (nollaa-paatokset))))

  ;; TODO
  PoistaKattohinnanOikaisuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "PoistaOikaisuEpaonnistui" vastaus)
    (viesti/nayta-toast! "Oikaisun poistamisessa tapahtui virhe" :varoitus)
    app)

  ;; Päätökset

  HaeUrakanPaatokset
  (process-event [{urakka :urakka} app]
    (do
      (hae-urakan-paatokset app urakka)
      app))

  HaeUrakanPaatoksetOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [;; Tyhjennetään vanhat lomakkeet
          app (dissoc app :tavoitehinnan-ylitys-lomake :tavoitehinnan-alitus-lomake :kattohinnan-ylitys-lomake :lupaus-bonus-lomake :lupaus-sanktio-lomake)
          {tavoitehinnan-ylitys-lomake :tavoitehinnan-ylitys-lomake
           tavoitehinnan-alitus-lomake :tavoitehinnan-alitus-lomake
           kattohinnan-ylitys-lomake :kattohinnan-ylitys-lomake
           lupaus-bonus-lomake :lupaus-bonus-lomake
           lupaus-sanktio-lomake :lupaus-sanktio-lomake} (alusta-paatos-lomakkeet vastaus (:hoitokauden-alkuvuosi app))]
      (cond-> app
              tavoitehinnan-ylitys-lomake (assoc :tavoitehinnan-ylitys-lomake tavoitehinnan-ylitys-lomake)
              tavoitehinnan-alitus-lomake (assoc :tavoitehinnan-alitus-lomake tavoitehinnan-alitus-lomake)
              kattohinnan-ylitys-lomake (assoc :kattohinnan-ylitys-lomake kattohinnan-ylitys-lomake)
              lupaus-bonus-lomake (assoc :lupaus-bonus-lomake lupaus-bonus-lomake)
              lupaus-sanktio-lomake (assoc :lupaus-sanktio-lomake lupaus-sanktio-lomake)
              :aina (assoc :urakan-paatokset vastaus))))

  HaeUrakanPaatoksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "HaeUrakanPaatoksetEpaonnistui" vastaus)
    (viesti/nayta-toast! "Urakan päätösten haku epäonnistui!" :varoitus)
    app)

  AlustaPaatosLomakkeet
  (process-event [{paatokset :paatokset hoitokauden-alkuvuosi :hoitokauden-alkuvuosi} app]
    (let [;; Tyhjennetään vanhat lomakkeet
          app (dissoc app :tavoitehinnan-ylitys-lomake :tavoitehinnan-alitus-lomake :kattohinnan-ylitys-lomake :lupaus-bonus-lomake :lupaus-sanktio-lomake)
          {tavoitehinnan-ylitys-lomake :tavoitehinnan-ylitys-lomake
           tavoitehinnan-alitus-lomake :tavoitehinnan-alitus-lomake
           kattohinnan-ylitys-lomake :kattohinnan-ylitys-lomake
           lupaus-bonus-lomake :lupaus-bonus-lomake
           lupaus-sanktio-lomake :lupaus-sanktio-lomake} (alusta-paatos-lomakkeet paatokset hoitokauden-alkuvuosi)]
      (cond-> app
              tavoitehinnan-ylitys-lomake (assoc :tavoitehinnan-ylitys-lomake tavoitehinnan-ylitys-lomake)
              tavoitehinnan-alitus-lomake (assoc :tavoitehinnan-alitus-lomake tavoitehinnan-alitus-lomake)
              kattohinnan-ylitys-lomake (assoc :kattohinnan-ylitys-lomake kattohinnan-ylitys-lomake)
              lupaus-bonus-lomake (assoc :lupaus-bonus-lomake lupaus-bonus-lomake)
              lupaus-sanktio-lomake (assoc :lupaus-sanktio-lomake lupaus-sanktio-lomake))))

  PaivitaPaatosLomake
  (process-event [{tiedot :tiedot paatos :paatos} app]
    (assoc app paatos tiedot))

  TallennaPaatos
  (process-event [{paatos :paatos} app]
    (tuck-apurit/post! app :tallenna-urakan-paatos
                       paatos
                       {:onnistui ->TallennaPaatosOnnistui
                        :onnistui-parametrit [(::valikatselmus/tyyppi paatos)
                                              (nil? (::valikatselmus/paatoksen-id paatos))]
                        :epaonnistui ->TallennaPaatosEpaonnistui}))

  TallennaPaatosOnnistui
  (process-event [{tyyppi :tyyppi vastaus :vastaus uusi? :uusi?} {:keys [urakan-paatokset] :as app}]
    (viesti/nayta-toast! "Päätöksen tallennus onnistui")
    (let [paivitetyt-paatokset (map #(if (= (select-keys % [::valikatselmus/tyyppi ::valikatselmus/hoitokauden-alkuvuosi])
                                            (select-keys vastaus [::valikatselmus/tyyppi ::valikatselmus/hoitokauden-alkuvuosi]))
                                       vastaus
                                       %)
                                    urakan-paatokset)
          paivitetyt-paatokset (if uusi? (conj paivitetyt-paatokset vastaus)
                                         paivitetyt-paatokset)]
      (do
        ;; Jos tallennettiin lupauspäätös, niin joudutaan hakemaan lupaukset uusiksi.
        (kustannusten-seuranta-tiedot/hae-kustannukset (-> @tila/yleiset :urakka :id) (:hoitokauden-alkuvuosi app) nil nil)
        (hae-lupaustiedot app)
        (-> app
            (assoc :urakan-paatokset paivitetyt-paatokset)
            (assoc-in [(tyyppi tyyppi->lomake) ::valikatselmus/paatoksen-id] (::valikatselmus/paatoksen-id vastaus))
            (assoc-in [(tyyppi tyyppi->lomake) :muokataan?] false)))))

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
    (assoc-in app [:kattohinnan-ylitys-lomake :maksun-tyyppi] tyyppi))

  PoistaLupausPaatos
  (process-event [{id :id} app]
    (do
      (tuck-apurit/post! :poista-lupaus-paatos
                         {:paatos-id id}
                         {:onnistui ->PoistaLupausPaatosOnnistui
                          :epaonnistui ->PoistaLupausPaatosEpaonnistui})
      app))

  PoistaLupausPaatosOnnistui
  (process-event [{vastaus :vastaus} app]
    (hae-lupaustiedot app)
    (hae-urakan-paatokset app (-> @tila/yleiset :urakka :id))
    (kustannusten-seuranta-tiedot/hae-kustannukset (-> @tila/yleiset :urakka :id) (:hoitokauden-alkuvuosi app) nil nil)
    (hae-lupaustiedot app)
    (viesti/nayta-toast! "Päätöksen poisto onnistui!")
    app)

  PoistaLupausPaatosEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "PoistaLupausPaatosEpaonnistui" vastaus)
    (viesti/nayta-toast! "Päätöksen poistossa tapahtui virhe" :varoitus)
    app)
  )
