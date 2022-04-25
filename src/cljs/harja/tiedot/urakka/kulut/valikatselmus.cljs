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
            [taoensso.timbre :as log]
            [harja.tiedot.navigaatio :as nav]))

;; Oikaisut
(defrecord TallennaOikaisu [oikaisu id])
(defrecord TallennaOikaisuOnnistui [vastaus id])
(defrecord TallennaOikaisuEpaonnistui [vastaus])
(defrecord PoistaOikaisu [oikaisu id])
(defrecord PoistaOikaisuOnnistui [vastaus id])
(defrecord PoistaOikaisuEpaonnistui [vastaus])
(defrecord PaivitaTavoitehinnanOikaisut [hoitokauden-alkuvuosi uusi])

;; Kattohinnan oikaisut
(defrecord KattohinnanOikaisuaMuokattu [kattohinta])
(defrecord TallennaKattohinnanOikaisu [])
(defrecord TallennaKattohinnanOikaisuOnnistui [vastaus id])
(defrecord TallennaKattohinnanOikaisuEpaonnistui [vastaus])
(defrecord PoistaKattohinnanOikaisu [])
(defrecord PoistaKattohinnanOikaisuOnnistui [vastaus id])
(defrecord PoistaKattohinnanOikaisuEpaonnistui [vastaus])
(defrecord KattohinnanMuokkaaPainettu [kattohinta])

;; Päätökset
(defrecord NollaaPaatoksetJosUrakkaVaihtui [])
(defrecord PaivitaPaatosLomake [tiedot paatos])
(defrecord TallennaPaatos [paatos])
(defrecord TallennaPaatosOnnistui [vastaus tyyppi uusi?])
(defrecord TallennaPaatosEpaonnistui [vastaus])
(defrecord PoistaPaatos [id tyyppi])
(defrecord PoistaPaatosOnnistui [vastaus tyyppi])
(defrecord PoistaPaatosEpaonnistui [vastaus])
(defrecord MuokkaaPaatosta [lomake-avain])
(defrecord AlustaPaatosLomakkeet [paatokset hoitokauden-alkuvuosi])
(defrecord HaeUrakanPaatokset [urakka])
(defrecord HaeUrakanPaatoksetOnnistui [vastaus])
(defrecord HaeUrakanPaatoksetEpaonnistui [vastaus])
(defrecord PaivitaMaksunTyyppi [tyyppi])
(defrecord PoistaLupausPaatos [id])
(defrecord PoistaLupausPaatosOnnistui [vastaus])
(defrecord PoistaLupausPaatosEpaonnistui [vastaus])


(def tyyppi->lomake
  {::valikatselmus/kattohinnan-ylitys :kattohinnan-ylitys-lomake
   ::valikatselmus/lupaus-bonus :lupaus-bonus-lomake
   ::valikatselmus/lupaus-sanktio :lupaus-sanktio-lomake})

(defn nollaa-paatokset [app]
  (assoc app :urakan-paatokset nil
             ;; Nollataan kattohinnan ylitys-lomake
             :kattohinnan-ylitys-lomake nil))

(defn filtteroi-paatos [hoitokauden-alkuvuosi tyyppi paatokset]
  (first (filter #(and
                    (= (name tyyppi) (::valikatselmus/tyyppi %))
                    (= hoitokauden-alkuvuosi (::valikatselmus/hoitokauden-alkuvuosi %)))
           paatokset)))

(defn alusta-paatos-lomakkeet [paatokset hoitokauden-alkuvuosi]
  (let [filtteroi-paatos (fn [tyyppi]
                           (filtteroi-paatos hoitokauden-alkuvuosi tyyppi paatokset))
        kattohinnan-ylitys (filtteroi-paatos ::valikatselmus/kattohinnan-ylitys)
        lupausbonus (filtteroi-paatos ::valikatselmus/lupaus-bonus)
        lupaussanktio (filtteroi-paatos ::valikatselmus/lupaus-sanktio)]
    {:kattohinnan-ylitys-lomake (when (some? kattohinnan-ylitys)
                                  {::valikatselmus/paatoksen-id (::valikatselmus/paatoksen-id kattohinnan-ylitys)
                                   :maksun-tyyppi (cond (and
                                                          (pos? (::valikatselmus/urakoitsijan-maksu kattohinnan-ylitys))
                                                          (pos? (::valikatselmus/siirto kattohinnan-ylitys))) :osa
                                                        (pos? (::valikatselmus/siirto kattohinnan-ylitys)) :siirto
                                                        :else :maksu)
                                   :siirto (when (pos? (::valikatselmus/siirto kattohinnan-ylitys)) (::valikatselmus/siirto kattohinnan-ylitys))})
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

(defn poista-kattohinnan-oikaisu [app]
  (tuck-apurit/post! app :poista-kattohinnan-oikaisu
    {::urakka/id (-> @tila/yleiset :urakka :id)
     :harja.domain.kulut.valikatselmus/hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)}
    {:onnistui ->PoistaKattohinnanOikaisuOnnistui
     :epaonnistui ->PoistaKattohinnanOikaisuEpaonnistui
     :paasta-virhe-lapi? true}))

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
      (tuck/action! (fn [e!]
                      (e! (kustannusten-seuranta-tiedot/->HaeBudjettitavoite))))
      (cond->
        app
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
      (tuck/action! (fn [e!]
                      (e! (kustannusten-seuranta-tiedot/->HaeBudjettitavoite))))
      (-> app
          (assoc-in [:tavoitehinnan-oikaisut (:hoitokauden-alkuvuosi app) id :poistettu] true)
          (nollaa-paatokset))))

  PoistaOikaisuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "PoistaOikaisuEpaonnistui" vastaus)
    (viesti/nayta-toast! "Oikaisun poistamisessa tapahtui virhe" :varoitus)
    app)

  PaivitaTavoitehinnanOikaisut
  (process-event [{hoitokauden-alkuvuosi :hoitokauden-alkuvuosi uusi :uusi} app]
    (assoc-in app [:tavoitehinnan-oikaisut hoitokauden-alkuvuosi] uusi))

  ;; Kattohinnan oikaisut

  KattohinnanOikaisuaMuokattu
  (process-event [{kattohinta :kattohinta} app]
    (log/debug "KattohinnanOikaisuaMuokattu" kattohinta)
    (assoc-in app [:kattohinnan-oikaisu :uusi-kattohinta] kattohinta))

  TallennaKattohinnanOikaisu
  (process-event [_ {{uusi-kattohinta :uusi-kattohinta} :kattohinnan-oikaisu :as app}]
    (if uusi-kattohinta
      (let [oikaisu {::urakka/id (-> @tila/yleiset :urakka :id)
                     :harja.domain.kulut.valikatselmus/hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
                     ::valikatselmus/uusi-kattohinta uusi-kattohinta}]
        (log/debug "TallennaKattohinnanOikaisu" oikaisu)
        (tuck-apurit/post! :tallenna-kattohinnan-oikaisu
          oikaisu
          {:onnistui ->TallennaKattohinnanOikaisuOnnistui
           :epaonnistui ->TallennaKattohinnanOikaisuEpaonnistui
           :paasta-virhe-lapi? true}))
      ;; Jos kattohinta-kenttä on tyhjä, poista kattohinnan oikaisu
      (poista-kattohinnan-oikaisu app))
    app)

  TallennaKattohinnanOikaisuOnnistui
  (process-event [{vastaus :vastaus} {:keys [hoitokauden-alkuvuosi] :as app}]
    (viesti/nayta-toast! "Kattohinnan oikaisu tallennettu")
    ;; Päivitetään sekä välikatselmuksen, että kustannusseurannan tiedot
    (hae-lupaustiedot app)
    (kustannusten-seuranta-tiedot/hae-kustannukset (-> @tila/yleiset :urakka :id) hoitokauden-alkuvuosi nil nil)
    (tuck/action! (fn [e!]
                    (e! (kustannusten-seuranta-tiedot/->HaeBudjettitavoite))))
    (->
      app
      (assoc-in [:kattohintojen-oikaisut hoitokauden-alkuvuosi] vastaus)
      (dissoc :kattohinnan-oikaisu)
      (nollaa-paatokset)))

  TallennaKattohinnanOikaisuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "TallennaKattohinnanOikaisuEpaonnistui" vastaus)
    (viesti/nayta-toast! "Kattohinnan oikaisun tallennuksessa tapahtui virhe" :varoitus)
    app)

  PoistaKattohinnanOikaisu
  (process-event [_ app]
    (poista-kattohinnan-oikaisu app))

  PoistaKattohinnanOikaisuOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Kattohinnan oikaisu poistettu")
      (hae-lupaustiedot app)
      (kustannusten-seuranta-tiedot/hae-kustannukset (-> @tila/yleiset :urakka :id) (:hoitokauden-alkuvuosi app) nil nil)
      (tuck/action! (fn [e!]
                      (e! (kustannusten-seuranta-tiedot/->HaeBudjettitavoite))))
      (->
        app
        (update :kattohintojen-oikaisut dissoc (:hoitokauden-alkuvuosi app))
        (dissoc app :kattohinnan-oikaisu)
        (nollaa-paatokset))))

  PoistaKattohinnanOikaisuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "PoistaKattohinnanOikaisuEpaonnistui" vastaus)
    (viesti/nayta-toast! "Kattohinnan oikaisun poistamisessa tapahtui virhe" :varoitus)
    app)

  KattohinnanMuokkaaPainettu
  (process-event [{kattohinta :kattohinta} app]
    (log/debug "KattohinnanMuokkaaPainettu" kattohinta)
    (-> app
      (assoc-in [:kattohinnan-oikaisu :muokkaa-painettu?] true)
      (assoc-in [:kattohinnan-oikaisu :uusi-kattohinta] kattohinta)))

  ;; Päätökset

  HaeUrakanPaatokset
  (process-event [{urakka :urakka} app]
    (do
      (hae-urakan-paatokset app urakka)
      app))

  HaeUrakanPaatoksetOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [{kattohinnan-ylitys-lomake :kattohinnan-ylitys-lomake
           lupaus-bonus-lomake :lupaus-bonus-lomake
           lupaus-sanktio-lomake :lupaus-sanktio-lomake} (alusta-paatos-lomakkeet vastaus (:hoitokauden-alkuvuosi app))]
      (cond-> app
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
          {kattohinnan-ylitys-lomake :kattohinnan-ylitys-lomake
           lupaus-bonus-lomake :lupaus-bonus-lomake
           lupaus-sanktio-lomake :lupaus-sanktio-lomake} (alusta-paatos-lomakkeet paatokset hoitokauden-alkuvuosi)]
      (cond-> app
              kattohinnan-ylitys-lomake (assoc :kattohinnan-ylitys-lomake kattohinnan-ylitys-lomake)
              lupaus-bonus-lomake (assoc :lupaus-bonus-lomake lupaus-bonus-lomake)
              lupaus-sanktio-lomake (assoc :lupaus-sanktio-lomake lupaus-sanktio-lomake))))

  NollaaPaatoksetJosUrakkaVaihtui
  (process-event [_ app]
    (if (not= (:valittu-urakka app) @nav/valittu-urakka-id)
      (-> app
        (nollaa-paatokset)
        (assoc :valittu-urakka @nav/valittu-urakka-id))
      app))

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

  PoistaPaatos
  (process-event [{id :id tyyppi :tyyppi} app]
    (tuck-apurit/post! app :poista-paatos
      {::valikatselmus/paatoksen-id id}
      {:onnistui ->PoistaPaatosOnnistui
       :onnistui-parametrit [tyyppi]
       :epaonnistui ->PoistaPaatosEpaonnistui}) )

  PoistaPaatosOnnistui
  (process-event [{tyyppi :tyyppi} app]
    (hae-urakan-paatokset app (-> @tila/yleiset :urakka :id))
    (update app (tyyppi->lomake tyyppi) dissoc ::valikatselmus/paatoksen-id))

  PoistaPaatosEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "PoistaPaatosEpaonnistui" vastaus)
    (viesti/nayta-toast! "Päätöksen kumoamisessa tapahtui virhe" :varoitus)
    app)

  MuokkaaPaatosta
  (process-event [{lomake-avain :lomake-avain} app]
    (assoc-in app [lomake-avain :muokataan?] true))

  PaivitaMaksunTyyppi
  (process-event [{tyyppi :tyyppi} app]
    (assoc-in app [:kattohinnan-ylitys-lomake :maksun-tyyppi] tyyppi))

  PoistaLupausPaatos
  (process-event [{id :id} app]
    (do
      (tuck-apurit/post! :poista-paatos
                         {::valikatselmus/paatoksen-id id}
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